package com.ruoyi.knowledge.review;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Authoritative backend-only immutable journal. Never falls back to an empty cache. */
@Component
public class KbReviewLedger implements AutoCloseable {
    private final Path directory;
    private FileChannel owner;
    private FileLock lease;
    private boolean failed;
    private final java.util.Map<Path,Object> readCache = new java.util.HashMap<>();
    private final java.util.Map<String,List<?>> collectionCache = new java.util.HashMap<>();
    private java.nio.file.attribute.FileTime directoryStamp;
    private long generation;
    private java.util.Map<Path,String> inventory = java.util.Map.of();
    public synchronized long generation() {
        open();
        java.util.Map<Path,String> current = new java.util.HashMap<>();
        try (var paths=Files.list(directory)) {
            for (Path path : paths.filter(p->p.getFileName().toString().matches("[0-9a-f]{64}\\.(prepare|terminal|exception|binding)\\.json")).toList()) {
                if (Files.isSymbolicLink(path)) { throw unavailable(); }
                var attrs=Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                current.put(path,attrs.size()+":"+attrs.lastModifiedTime()+":"+attrs.fileKey());
                if (path.getFileName().toString().endsWith(".terminal.json") && !Files.exists(path.resolveSibling(path.getFileName().toString().replace(".terminal.json",".prepare.json")))) { throw unavailable(); }
            }
        } catch (IOException error) { failed=true; throw unavailable(); }
        if (!current.equals(inventory)) { invalidate(); inventory=java.util.Map.copyOf(current); }
        return generation;
    }
    private void invalidate() { readCache.clear(); collectionCache.clear(); generation++; }

    public KbReviewLedger(@Value("${knowledge.review.directory:}") String path) {
        directory = path == null || path.isBlank() ? null : Path.of(path).toAbsolutePath().normalize();
    }

    public boolean configured() {
        return directory != null;
    }

    private synchronized void open() {
        if (failed || directory == null) { throw unavailable(); }
        try {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) { throw unavailable(); }
            var stamp = Files.getLastModifiedTime(directory, LinkOption.NOFOLLOW_LINKS);
            if (!stamp.equals(directoryStamp)) { invalidate(); directoryStamp = stamp; }
        } catch (IOException exception) { failed = true; throw unavailable(); }
        if (lease != null && lease.isValid()) { return; }
        try {
            // The operator explicitly provisions this retained directory. Missing storage is not an empty ledger.
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) { throw unavailable(); }
            if (Files.isSymbolicLink(directory.resolve("owner.lock"))) { throw unavailable(); }
            owner = FileChannel.open(directory.resolve("owner.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            lease = owner.tryLock();
            if (lease == null) { throw unavailable(); }
        } catch (IOException | java.nio.channels.OverlappingFileLockException exception) {
            failed = true;
            throw unavailable();
        }
    }

    public synchronized void prepare(ReviewModels.Prepared value) {
        validatePrepared(value);
        write(file(value.operationId(), "prepare"), value);
    }

    public synchronized ReviewModels.Prepared prepared(String operationId) {
        open();
        Path path = file(operationId, "prepare");

        return Files.exists(path, LinkOption.NOFOLLOW_LINKS) ? read(path, ReviewModels.Prepared.class) : null;
    }

    public synchronized ReviewModels.Terminal terminal(String operationId) {
        open();
        Path path = file(operationId, "terminal");
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) { return null; }
        ReviewModels.Terminal terminal = read(path, ReviewModels.Terminal.class);
        ReviewModels.Prepared prepared = prepared(operationId);
        if (prepared == null || !operationId.equals(terminal.operationId())
                || !hash(JSON.toJSONBytes(prepared)).equals(terminal.prepareHash())
                || !List.of("CONFIRMED", "ABORTED").contains(terminal.outcome())) { throw unavailable(); }
        return terminal;
    }

    public synchronized void finish(String operationId, String outcome) {
        ReviewModels.Prepared prepared = prepared(operationId);
        if (prepared == null) { throw unavailable(); }
        write(file(operationId, "terminal"), new ReviewModels.Terminal(operationId,
                hash(JSON.toJSONBytes(prepared)), outcome));
    }

    public synchronized List<ReviewModels.Prepared> operations() {
        open();
        if (collectionCache.containsKey("operations"))
            { return collectionCache.get("operations").stream().map(ReviewModels.Prepared.class::cast).toList(); }
        List<ReviewModels.Prepared> result = new ArrayList<>();
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".prepare.json")).sorted().toList()) {
                ReviewModels.Prepared prepared = read(path, ReviewModels.Prepared.class);
                if (prepared == null || !path.equals(file(prepared.operationId(), "prepare"))) { throw unavailable(); }
                validatePrepared(prepared);
                terminal(prepared.operationId());
                result.add(prepared);
            }
        } catch (IOException exception) { throw unavailable(); }
        List<ReviewModels.Prepared> snapshot = List.copyOf(result);
        collectionCache.put("operations", snapshot);
        return snapshot;
    }

    public synchronized void saveException(ReviewModels.GranularityException value) {
        write(file(value.exceptionId(), "exception"), value);
    }

    public synchronized ReviewModels.GranularityException exception(String id) {
        open();
        Path path = file(id, "exception");
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS) ? read(path, ReviewModels.GranularityException.class) : null;
    }

    public synchronized List<ReviewModels.GranularityException> exceptions() {
        return records("exception", ReviewModels.GranularityException.class);
    }

    public synchronized void saveBinding(ReviewModels.ImportBinding value) {
        write(file(value.bindingId(), "binding"), value);
    }

    public synchronized List<ReviewModels.ImportBinding> bindings() {
        return records("binding", ReviewModels.ImportBinding.class);
    }

    private <T> List<T> records(String kind, Class<T> type) {
        open();
        if (collectionCache.containsKey(kind)) { return collectionCache.get(kind).stream().map(type::cast).toList(); }
        try (var paths = Files.list(directory)) {
            List<T> values = paths.filter(path -> path.getFileName().toString().endsWith("." + kind + ".json"))
                    .sorted().map(path -> read(path, type)).toList();
            collectionCache.put(kind, values);
            return values;
        } catch (IOException exception) { throw unavailable(); }
    }

    private Path file(String operationId, String kind) {
        if (operationId == null || operationId.isBlank() || operationId.length() > 128)
            { throw new ServiceException("operationId长度必须为1至128", 400); }
        if (directory == null) { throw unavailable(); }
        return directory.resolve(hash(operationId.getBytes(StandardCharsets.UTF_8)) + "." + kind + ".json");
    }

    private void write(Path target, Object value) {
        open();
        byte[] bytes = JSON.toJSONBytes(value);
        try {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(target) || !java.util.Arrays.equals(bytes, Files.readAllBytes(target)))
                    { throw new ServiceException("操作身份已对应其他内容", 409); }
                return;
            }
            Path temporary = Files.createTempFile(directory, ".pending-", ".json");
            try {
                try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) { channel.write(buffer); }
                    channel.force(true);
                }
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                invalidate();
                try (FileChannel parent = FileChannel.open(directory, StandardOpenOption.READ)) { parent.force(true); }
            } finally { Files.deleteIfExists(temporary); }
        } catch (IOException exception) { failed = true; throw unavailable(); }
    }

    private <T> T read(Path path, Class<T> type) {
        try {
            if (Files.isSymbolicLink(path)) { throw unavailable(); }
            if (readCache.containsKey(path)) { return type.cast(readCache.get(path)); }
            T value = JSON.parseObject(Files.readAllBytes(path), type);
            if (value == null) { throw unavailable(); }
            readCache.put(path, value);
            return value;
        } catch (IOException | com.alibaba.fastjson2.JSONException exception) { failed = true; throw unavailable(); }
    }

    private void validatePrepared(ReviewModels.Prepared value) {
        if (value == null || value.before()==null || value.after()==null || value.actor()==null || value.actor().isBlank()
                || value.requestHash()==null || !value.requestHash().matches("[0-9a-f]{64}") || value.reason()==null
                || (value.policyVersion()==null || value.policyVersion().isBlank()) || !List.of("APPROVE","RETURN_TO_DRAFT","SOFT_DELETE","RESTORE").contains(value.action())
                || !value.before().questionId().equals(value.after().questionId())
                || Long.parseLong(value.after().rowVersion()) != Long.parseLong(value.before().rowVersion()) + 1
                || value.before().facts()==null || value.after().facts()==null) { throw unavailable(); }
    }

    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private ServiceException unavailable() { return new ServiceException("审核账册不可用或未配置；禁止正常取用及审批，需恢复后重试", 503); }

    @Override
    @PreDestroy
    public synchronized void close() throws IOException {
        if (lease != null) { lease.release(); }
        if (owner != null) { owner.close(); }
        lease = null;
        owner = null;
    }
}
