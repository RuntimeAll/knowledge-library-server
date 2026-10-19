package com.ruoyi.knowledge.layout;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.mapper.KbLayoutMapper;
import com.ruoyi.knowledge.storage.IKbAssetService;
import com.ruoyi.knowledge.storage.domain.KbAsset;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Database leases fence archival; the Node child has no credentials and no network inputs. */
@Component
public class KbRenderWorker {
    private static final Logger log = LoggerFactory.getLogger(KbRenderWorker.class);
    private final KbLayoutMapper mapper;
    private final KbRenderServiceImpl service;
    private final IKbAssetService assets;
    private final KbRenderProperties properties;
    private final KbFrozenProductArchiver archiver;
    private final TransactionTemplate tx;
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        var t = new Thread(r, "kb-render-worker");
                        t.setDaemon(true);
                        return t;
                    });
    private final ScheduledExecutorService heartbeat =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        var t = new Thread(r, "kb-render-lease");
                        t.setDaemon(true);
                        return t;
                    });
    private volatile Process process;
    private volatile long lastWarning;
    private volatile boolean stopping;

    public KbRenderWorker(
            KbLayoutMapper mapper,
            KbRenderServiceImpl service,
            IKbAssetService assets,
            KbRenderProperties properties,
            KbFrozenProductArchiver archiver,
            PlatformTransactionManager transactions) {
        this.mapper = mapper;
        this.service = service;
        this.assets = assets;
        this.properties = properties;
        this.archiver = archiver;
        this.tx = new TransactionTemplate(transactions);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (properties.isEnabled())
            executor.scheduleWithFixedDelay(this::poll, 5, 5, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() {
        // Leave the claim recoverable. A normal application stop is not a failed document.
        stopping = true;
        executor.shutdownNow();
        heartbeat.shutdownNow();
        var p = process;
        if (p != null) kill(p);
    }

    private static void kill(Process process) {
        process.descendants()
                .forEach(
                        child -> {
                            try {
                                child.destroyForcibly();
                            } catch (RuntimeException ignored) {
                            }
                        });
        process.destroyForcibly();
    }

    private void poll() {
        if (stopping) return;
        try {
            Long id = mapper.nextJob();
            if (id == null) return;
            var claim = new KbRenderJob();
            claim.setId(id);
            claim.setLeaseToken(UUID.randomUUID().toString());
            if (mapper.claim(claim) != 1) return;
            execute(claim);
        } catch (Exception e) {
            if (System.currentTimeMillis() - lastWarning > 60000) {
                log.warn("Knowledge render queue unavailable: {}", e.getClass().getSimpleName());
                lastWarning = System.currentTimeMillis();
            }
        }
    }

    private void execute(KbRenderJob claim) {
        if (stopping) return;
        AtomicBoolean lost = new AtomicBoolean();
        var lease =
                heartbeat.scheduleAtFixedRate(
                        () -> {
                            try {
                                if (mapper.heartbeat(claim) != 1) lost.set(true);
                            } catch (Exception e) {
                                lost.set(true);
                            }
                        },
                        20,
                        20,
                        TimeUnit.SECONDS);
        try {
            var job = service.hydrate(Objects.requireNonNull(mapper.job(claim.getId())));
            job.setLeaseToken(claim.getLeaseToken());
            SysUser user = new SysUser();
            user.setUserId(job.getActorUserId());
            user.setUserName(job.getCreateBy());
            var login = new LoginUser(job.getActorUserId(), null, user, Set.of());
            SecurityContextHolder.getContext()
                    .setAuthentication(
                            new UsernamePasswordAuthenticationToken(login, null, List.of()));
            Path base =
                    Paths.get(properties.getWorkDirectory())
                            .toAbsolutePath()
                            .normalize()
                            .resolve("job-" + job.getId());
            Files.createDirectories(base);
            Path sealed = base.resolve("sealed");
            Path request = base.resolve("request.json");
            // A successful local render is sealed once and reused byte-for-byte after an upload
            // interruption.
            if (!Files.isRegularFile(sealed.resolve("manifest.json"))) {
                Path inputs = base.resolve("inputs");
                Files.createDirectories(inputs);
                List<KbLayoutAsset> inputsAssets = new ArrayList<>();
                for (var a : job.getAssets())
                    if (Set.of("TEMPLATE_IMAGE", "QUESTION_IMAGE").contains(a.getAssetRole())) {
                        check(lost);
                        String ext =
                                switch (a.getMimeType()) {
                                    case "image/svg+xml" -> ".svg";
                                    case "image/png" -> ".png";
                                    case "image/jpeg" -> ".jpg";
                                    case "image/webp" -> ".webp";
                                    case "image/gif" -> ".gif";
                                    default ->
                                            throw new RenderFailure(
                                                    "ASSET_TYPE", "无法渲染资产 " + a.getAssetId());
                                };
                        Path target = inputs.resolve(a.getAssetId() + ext);
                        if (!Files.isRegularFile(target)
                                || !KbLayoutProtocol.hash(Files.readAllBytes(target))
                                        .equals(a.getSha256())) {
                            try (InputStream stream = assets.openContent(a.getAssetId())) {
                                Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
                            }
                            if (!KbLayoutProtocol.hash(Files.readAllBytes(target))
                                    .equals(a.getSha256()))
                                throw new RenderFailure("ASSET_HASH", "资产哈希不一致：" + a.getAssetId());
                        }
                        a.setPath(target.toString());
                        inputsAssets.add(a);
                    }
                var input =
                        new KbRendererInput(
                                "kb-render-input/v1",
                                job.getId().toString(),
                                job.getTemplateSnapshot(),
                                job.getConfigSnapshot(),
                                job.getItems(),
                                inputsAssets);
                byte[] requestBytes = KbLayoutProtocol.json(input).getBytes(StandardCharsets.UTF_8);
                Files.write(request, requestBytes);
                Path attempt = base.resolve("attempt-" + claim.getLeaseToken());
                Files.createDirectories(attempt);
                Path script = Paths.get(properties.getScript()).toAbsolutePath().normalize();
                if (!Files.isRegularFile(script))
                    throw new RenderFailure("RENDERER_UNAVAILABLE", "PDF渲染脚本不存在");
                ProcessBuilder builder =
                        new ProcessBuilder(
                                        properties.getNodeExecutable(),
                                        script.toString(),
                                        "--input",
                                        request.toString(),
                                        "--output",
                                        attempt.toString())
                                .redirectOutput(attempt.resolve("stdout.log").toFile())
                                .redirectError(attempt.resolve("stderr.log").toFile());
                Set<String> allowedEnvironment =
                        Set.of(
                                "PATH",
                                "SYSTEMROOT",
                                "WINDIR",
                                "TEMP",
                                "TMP",
                                "USERPROFILE",
                                "HOME",
                                "LOCALAPPDATA",
                                "APPDATA",
                                "PLAYWRIGHT_BROWSERS_PATH");
                builder.environment()
                        .keySet()
                        .removeIf(
                                key -> !allowedEnvironment.contains(key.toUpperCase(Locale.ROOT)));
                process = builder.start();
                long deadline =
                        System.nanoTime()
                                + TimeUnit.SECONDS.toNanos(
                                        Math.max(30, properties.getTimeoutSeconds()));
                while (!process.waitFor(2, TimeUnit.SECONDS)) {
                    check(lost);
                    if (System.nanoTime() > deadline) {
                        kill(process);
                        throw new RenderFailure("RENDER_TIMEOUT", "PDF生成超时，可按原任务重试");
                    }
                }
                if (process.exitValue() != 0) {
                    String err = Files.readString(attempt.resolve("stderr.log"));
                    throw new RenderFailure("RENDER_FAILED", errorMessage(err));
                }
                var manifest = readManifest(attempt, requestBytes, job);
                verifyArtifacts(attempt, manifest);
                check(lost);
                // Keep manifest relative paths valid when atomically sealing this attempt.
                for (var o : manifest.getOutputs()) {
                    o.setPdfPath(relative(attempt, o.getPdfPath()));
                    o.setHtmlPath(relative(attempt, o.getHtmlPath()));
                    o.setPackagePath(relative(attempt, o.getPackagePath()));
                }
                Files.writeString(
                        attempt.resolve("manifest.json"),
                        KbLayoutProtocol.json(manifest),
                        StandardCharsets.UTF_8);
                tx.executeWithoutResult(
                        status -> {
                            fence(claim);
                            try {
                                if (!Files.exists(sealed))
                                    Files.move(attempt, sealed, StandardCopyOption.ATOMIC_MOVE);
                                else throw new RenderFailure("CACHE_CONFLICT", "已有不完整生成缓存，须核对后恢复");
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
            }
            var manifest = readManifest(sealed, Files.readAllBytes(request), job);
            verifyArtifacts(sealed, manifest);
            check(lost);
            claim.setRendererFingerprint(manifest.getRendererFingerprint());
            for (var output : manifest.getOutputs()) {
                check(lost);
                KbAsset pdf =
                        upload(
                                job,
                                claim,
                                sealed,
                                output.getPdfPath(),
                                output.getSha256(),
                                output.getOutputKey(),
                                "pdf",
                                "application/pdf",
                                lost);
                KbAsset pack =
                        upload(
                                job,
                                claim,
                                sealed,
                                output.getPackagePath(),
                                output.getPackageSha256(),
                                output.getOutputKey(),
                                "package",
                                "application/zip",
                                lost);
                check(lost);
                tx.executeWithoutResult(
                        status -> {
                            fence(claim);
                            archiver.archive(claim, output.getOutputKey(), pdf.getAssetId());
                        });
            }
            claim.setStatus("SUCCEEDED");
            tx.executeWithoutResult(
                    status -> {
                        fence(claim);
                        if (mapper.finish(claim) != 1)
                            throw new RenderFailure("LEASE_LOST", "任务已转交其他执行器");
                    });
        } catch (Exception failure) {
            var p = process;
            if (p != null && p.isAlive()) kill(p);
            if (!lost.get() && !stopping)
                try {
                    claim.setStatus("FAILED");
                    claim.setErrorCode(
                            failure instanceof RenderFailure r
                                    ? r.code
                                    : failure instanceof ServiceException
                                            ? "STORAGE_OR_ARCHIVE_FAILED"
                                            : "RENDER_INTERNAL");
                    String message =
                            failure instanceof RenderFailure || failure instanceof ServiceException
                                    ? failure.getMessage()
                                    : "生成失败，请检查任务目录日志后重试（"
                                            + failure.getClass().getSimpleName()
                                            + "）";
                    claim.setErrorMessage(
                            message == null
                                    ? "生成失败"
                                    : message.substring(0, Math.min(message.length(), 1900)));
                    mapper.finish(claim);
                } catch (Exception ignored) {
                    log.warn("Render job {} will recover after its lease expires", claim.getId());
                }
        } finally {
            lease.cancel(false);
            process = null;
            SecurityContextHolder.clearContext();
        }
    }

    private KbAsset upload(
            KbRenderJob job,
            KbRenderJob claim,
            Path directory,
            String path,
            String hash,
            String role,
            String kind,
            String contentType,
            AtomicBoolean lost)
            throws IOException {
        String key = "output:" + role + ":" + kind;
        for (var existing : mapper.jobAssets(job.getId()))
            if (existing.getResourceKey().equals(key)) {
                if (!hash.equals(existing.getSha256()))
                    throw new RenderFailure("OUTPUT_HASH_CONFLICT", "已归档输出与生成缓存不一致");
                return assets.requireReady(existing.getAssetId());
            }
        byte[] bytes = Files.readAllBytes(resolve(directory, path));
        check(lost);
        KbAsset stored =
                assets.uploadBytes(
                        bytes,
                        "render-"
                                + job.getId()
                                + "-"
                                + role
                                + (kind.equals("pdf") ? ".pdf" : ".zip"),
                        contentType,
                        "PRODUCT_ORIGINAL",
                        job.getConfigSnapshot().getTestRunId(),
                        "render:" + job.getId() + ":" + role + ":" + kind);
        check(lost);
        tx.executeWithoutResult(
                status -> {
                    fence(claim);
                    if (!stored.getSha256().equals(hash))
                        throw new RenderFailure("OUTPUT_HASH", "归档资产哈希不一致");
                    KbLayoutAsset a = new KbLayoutAsset();
                    a.setJobId(job.getId());
                    a.setResourceKey(key);
                    a.setAssetId(stored.getAssetId());
                    a.setAssetRole(kind.equals("pdf") ? "OUTPUT_PDF" : "OUTPUT_PACKAGE");
                    a.setSha256(hash);
                    mapper.insertJobAsset(a);
                });
        return stored;
    }

    private void fence(KbRenderJob claim) {
        if (mapper.lockOwnedJob(claim.getId(), claim.getLeaseToken()) == null)
            throw new RenderFailure("LEASE_LOST", "任务执行租约已失效");
    }

    private void check(AtomicBoolean lost) {
        if (stopping || lost.get() || Thread.currentThread().isInterrupted())
            throw new RenderFailure("LEASE_LOST", "任务执行租约已失效");
    }

    private KbRendererManifest readManifest(Path dir, byte[] request, KbRenderJob job)
            throws IOException {
        var m =
                JSON.parseObject(
                        Files.readString(dir.resolve("manifest.json")), KbRendererManifest.class);
        if (!"kb-render-output/v1".equals(m.getSchemaVersion())
                || !KbLayoutProtocol.hash(request).equals(m.getInputHash())
                || m.getRendererFingerprint() == null
                || m.getRendererFingerprint().length() > 200)
            throw new RenderFailure("OUTPUT_MANIFEST", "渲染输出协议或输入哈希不一致");
        if (m.getOutputs() == null
                || m.getOutputs().size() != job.getConfigSnapshot().getOutputRoles().size())
            throw new RenderFailure("OUTPUT_ROLES", "输出角色数量不一致");
        Set<String> roles = new HashSet<>();
        for (var o : m.getOutputs())
            if (!roles.add(o.getOutputKey())
                    || !Objects.equals(o.getOutputKey(), o.getRole())
                    || !job.getConfigSnapshot().getOutputRoles().contains(o.getRole())
                    || o.getPageCount() == null
                    || o.getPageCount() < 1) throw new RenderFailure("OUTPUT_ROLES", "输出角色或页数不合法");
        return m;
    }

    private void verifyArtifacts(Path dir, KbRendererManifest manifest) throws IOException {
        for (var o : manifest.getOutputs()) {
            byte[] pdf = Files.readAllBytes(resolve(dir, o.getPdfPath()));
            if (pdf.length < 5
                    || !new String(pdf, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")
                    || !KbLayoutProtocol.hash(pdf).equals(o.getSha256()))
                throw new RenderFailure("OUTPUT_HASH", "PDF哈希校验失败");
            if (!KbLayoutProtocol.hash(Files.readAllBytes(resolve(dir, o.getPackagePath())))
                    .equals(o.getPackageSha256()))
                throw new RenderFailure("OUTPUT_HASH", "离线包哈希校验失败");
            resolve(dir, o.getHtmlPath());
        }
    }

    static Path resolve(Path directory, String file) throws IOException {
        if (file == null) throw new RenderFailure("OUTPUT_PATH", "缺少输出路径");
        Path root = directory.toRealPath();
        Path path = Paths.get(file);
        if (!path.isAbsolute()) path = root.resolve(path);
        path = path.toRealPath();
        if (!path.startsWith(root) || !Files.isRegularFile(path))
            throw new RenderFailure("OUTPUT_PATH", "输出文件越出任务目录");
        return path;
    }

    private String relative(Path dir, String file) throws IOException {
        return dir.toRealPath().relativize(resolve(dir, file)).toString();
    }

    static String errorMessage(String raw) {
        if (raw.length() > 8000) raw = raw.substring(raw.length() - 8000);
        for (String line : raw.lines().toList())
            try {
                var error = JSON.parseObject(line);
                String message = error.getString("message");
                if (message != null) {
                    // Browser exceptions append an internal JavaScript stack to message. Keep the
                    // actionable first line for the UI; stderr.log retains the complete diagnostic.
                    String first =
                            message.lines()
                                    .map(String::strip)
                                    .filter(s -> !s.isEmpty())
                                    .findFirst()
                                    .orElse("");
                    first =
                            first.replaceFirst(
                                            "^(?:page\\.evaluate(?:Handle)?|page\\.pdf|page\\.goto|browserType\\.launch|browser\\.newContext):\\s*",
                                            "")
                                    .replaceFirst("^Error:\\s*", "");
                    if (!first.isBlank()) return first.substring(0, Math.min(first.length(), 1800));
                }
            } catch (Exception ignored) {
            }
        return "PDF渲染失败，请查看任务stderr.log；缺图、公式或分页错误必须修正后重试";
    }

    static class RenderFailure extends RuntimeException {
        final String code;

        RenderFailure(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}
