package com.ruoyi.knowledge.importing;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.knowledge.html.HtmlQuestionDocument;
import com.ruoyi.knowledge.html.KbHtmlCodec;
import com.ruoyi.knowledge.importing.domain.KbImportBatch;
import com.ruoyi.knowledge.importing.domain.KbImportItem;
import com.ruoyi.knowledge.importing.domain.KbImportResource;
import com.ruoyi.knowledge.importing.mapper.KbImportMapper;
import com.ruoyi.knowledge.service.IKbSourceService;
import com.ruoyi.knowledge.storage.IKbAssetService;
import com.ruoyi.knowledge.storage.KbAssetServiceImpl;
import com.ruoyi.knowledge.storage.domain.KbAsset;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Durable input intake followed by separately committed, fenced question transactions. */
@Service
public class KbImportServiceImpl implements IKbImportService {
    private final KbImportMapper mapper;
    private final KbHtmlCodec codec;
    private final KbHtmlAssetResolver resolver;
    private final KbImportItemWorker worker;
    private final IKbAssetService assets;
    private final IKbSourceService sources;
    private final KbImportLeaseManager leases;

    public KbImportServiceImpl(KbImportMapper mapper, KbHtmlCodec codec,
            KbHtmlAssetResolver resolver, KbImportItemWorker worker,
            IKbAssetService assets, IKbSourceService sources, KbImportLeaseManager leases) {
        this.mapper = mapper;
        this.codec = codec;
        this.resolver = resolver;
        this.worker = worker;
        this.assets = assets;
        this.sources = sources;
        this.leases = leases;
    }

    public Schema schema() {
        return new Schema(KbHtmlCodec.SCHEMA, mapper.selectInstanceId());
    }

    public KbImportBatch get(Long batchId) {
        KbImportBatch batch = mapper.selectBatch(batchId);
        if (batch == null || !SecurityUtils.getUsername().equals(batch.getCreateBy())) {
            throw new ServiceException("导入批次不存在或无权读取");
        }
        batch.setItems(mapper.selectItems(batchId));
        batch.setResources(mapper.selectResources(batchId));
        return batch;
    }

    public KbImportBatch importHtml(MultipartFile file, List<MultipartFile> files,
            String idempotencyKey, Long sourceId, String testRunId) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new ServiceException("幂等键长度必须为1至128字符");
        }
        String runId = assets.validateTestRunId(testRunId);
        if (file == null || file.isEmpty() || file.getSize() > 2 * 1024 * 1024) {
            throw new ServiceException("请选择不超过2MiB的标准HTML");
        }
        byte[] html;
        List<ImportResource> resources;
        try {
            html = file.getBytes();
            resources = readResources(files);
        } catch (IOException error) {
            throw new ServiceException("读取HTML或题图失败");
        }
        List<Element> articles = codec.readArticles(html);
        if (sourceId != null) sources.get(sourceId);
        String fingerprint = batchFingerprint(html, resources, sourceId, runId);
        String actor = SecurityUtils.getUsername();
        KbImportBatch batch = mapper.selectByKey(actor, idempotencyKey);
        if (batch == null) {
            batch = new KbImportBatch();
            batch.setIdempotencyKey(idempotencyKey);
            batch.setRequestFingerprint(fingerprint);
            batch.setStatus("RECEIVING");
            batch.setTotalCount(articles.size());
            batch.setSuccessCount(0);
            batch.setFailureCount(0);
            batch.setSourceId(sourceId);
            batch.setTestRunId(runId);
            batch.setCreateBy(actor);
            batch.setInputReady(false);
            try {
                mapper.insertBatch(batch);
            } catch (DuplicateKeyException conflict) {
                batch = mapper.selectByKey(actor, idempotencyKey);
            }
        }
        if (batch == null || !fingerprint.equals(batch.getRequestFingerprint())) {
            throw new ServiceException("幂等键已用于其他导入内容");
        }
        if (terminal(batch)) return get(batch.getBatchId());
        KbImportLeaseManager.Lease lease;
        try {
            lease = leases.acquire(batch);
        } catch (ServiceException busy) {
            return get(batch.getBatchId());
        }
        try (lease) {
            try {
                if (!Boolean.TRUE.equals(batch.getInputReady())) {
                    stage(batch, html, file.getOriginalFilename(), resources, lease);
                }
                process(batch, html, resources, articles, lease);
            } catch (RuntimeException error) {
                rememberInterrupted(batch, error);
                throw error;
            }
        }
        return get(batch.getBatchId());
    }

    public KbImportBatch resume(Long batchId, String testRunId) {
        KbImportBatch batch = get(batchId);
        String runId = assets.validateTestRunId(testRunId);
        if (!Objects.equals(runId, batch.getTestRunId())) {
            throw new ServiceException("恢复请求必须保持原批次测试命名空间", 409);
        }
        if (terminal(batch)) return batch;
        if (!Boolean.TRUE.equals(batch.getInputReady())) {
            throw new ServiceException("原始文件尚未全部接收，请用相同幂等键和原文件重新提交以继续接收", 409);
        }
        try (KbImportLeaseManager.Lease lease = leases.acquire(batch)) {
            try {
                byte[] html = readStored(batch.getInputAssetId(), 2L * 1024 * 1024, null);
                List<ImportResource> resources = new ArrayList<>();
                long total = 0;
                for (KbImportResource mapping : mapper.selectResources(batchId)) {
                    total += mapping.getFileSize();
                    if (total > 100L * 1024 * 1024 || resources.size() >= 100) {
                        throw new ServiceException("持久资源清单超出导入限制");
                    }
                    byte[] bytes = readStored(mapping.getAssetId(), mapping.getFileSize(), mapping.getSha256());
                    resources.add(new ImportResource(mapping.getResourceKey(), mapping.getContentType(), bytes));
                    lease.check();
                }
                if (!batch.getRequestFingerprint().equals(batchFingerprint(html, resources, batch.getSourceId(), runId))) {
                    throw new ServiceException("持久输入与原请求指纹不一致，拒绝恢复", 409);
                }
                process(batch, html, resources, codec.readArticles(html), lease);
            } catch (RuntimeException error) {
                rememberInterrupted(batch, error);
                throw error;
            }
        }
        return get(batchId);
    }

    private boolean terminal(KbImportBatch batch) {
        return Set.of("SUCCESS", "PARTIAL", "FAILED").contains(batch.getStatus());
    }

    private List<ImportResource> readResources(List<MultipartFile> files) throws IOException {
        List<ImportResource> resources = new ArrayList<>();
        Set<String> names = new HashSet<>();
        long total = 0;
        for (MultipartFile resource : files == null ? List.<MultipartFile>of() : files) {
            String original = resource.getOriginalFilename();
            String name = original == null ? null : original.replace('\\', '/');
            if (name == null || name.isBlank() || name.length() > 255 || !names.add(name)
                    || name.startsWith("/") || name.contains(":") || List.of(name.split("/")).contains("..")) {
                throw new ServiceException("资源文件名必须唯一且为安全相对路径");
            }
            total += resource.getSize();
            if (total > 100L * 1024 * 1024 || resources.size() >= 100) {
                throw new ServiceException("题图总大小不能超过100MiB且最多100个");
            }
            String type = resource.getContentType() == null ? "application/octet-stream" : resource.getContentType();
            if (type.length() > 200) throw new ServiceException("资源Content-Type过长");
            resources.add(new ImportResource(name, type, resource.getBytes()));
        }
        return resources;
    }

    private void stage(KbImportBatch batch, byte[] html, String originalName,
            List<ImportResource> resources, KbImportLeaseManager.Lease lease) {
        if (batch.getInputAssetId() == null) {
            KbAsset input = assets.uploadBytes(html, originalName, "text/html", "IMPORT_HTML",
                    batch.getTestRunId(), "kb-import:" + batch.getBatchId() + ":html");
            batch.setInputAssetId(input.getAssetId());
            saveProgress(batch);
        }
        for (ImportResource resource : resources) {
            lease.check();
            String digest = KbAssetServiceImpl.sha256(resource.bytes());
            KbImportResource existing = mapper.selectResources(batch.getBatchId()).stream()
                    .filter(item -> item.getResourceKey().equals(resource.resourceKey())).findFirst().orElse(null);
            if (existing != null) {
                if (!digest.equals(existing.getSha256())) throw new ServiceException("已接收资源内容不匹配", 409);
                assets.requireReady(existing.getAssetId());
                continue;
            }
            String resourceIdentity = KbAssetServiceImpl.sha256(resource.resourceKey().getBytes(StandardCharsets.UTF_8));
            KbAsset asset = assets.uploadBytes(resource.bytes(), resource.resourceKey(), resource.contentType(),
                    "IMPORT_RESOURCE", batch.getTestRunId(), "kb-import:" + batch.getBatchId() + ":" + resourceIdentity);
            KbImportResource mapping = new KbImportResource();
            mapping.setBatchId(batch.getBatchId());
            mapping.setResourceKey(resource.resourceKey());
            mapping.setAssetId(asset.getAssetId());
            mapping.setContentType(resource.contentType());
            mapping.setSha256(digest);
            mapping.setFileSize((long) resource.bytes().length);
            worker.saveResource(batch, mapping);
        }
        lease.check();
        batch.setInputReady(true);
        batch.setStatus("PROCESSING");
        batch.setLastError(null);
        saveProgress(batch);
    }

    private byte[] readStored(Long assetId, long limit, String expectedHash) {
        KbAsset asset = assets.requireReady(assetId);
        if (asset.getFileSize() > limit) throw new ServiceException("持久文件大小与清单不符");
        try (InputStream stream = assets.openContent(assetId)) {
            byte[] bytes = stream.readNBytes(Math.toIntExact(asset.getFileSize()) + 1);
            String actual = KbAssetServiceImpl.sha256(bytes);
            if (bytes.length != asset.getFileSize() || !actual.equals(asset.getSha256())
                    || expectedHash != null && !expectedHash.equals(actual)) {
                throw new ServiceException("持久输入文件哈希不匹配，拒绝恢复", 409);
            }
            return bytes;
        } catch (IOException error) {
            throw new ServiceException("读取持久导入输入失败，请稍后恢复批次");
        }
    }

    private void process(KbImportBatch batch, byte[] html, List<ImportResource> resources,
            List<Element> articles, KbImportLeaseManager.Lease lease) {
        String origin = origin(html);
        String currentOrigin = mapper.selectInstanceId();
        Set<Integer> completed = new HashSet<>();
        mapper.selectItems(batch.getBatchId()).forEach(item -> completed.add(item.getItemOrder()));
        for (int index = 0; index < articles.size(); index++) {
            if (completed.contains(index)) continue;
            lease.check();
            Element article = articles.get(index);
            try {
                HtmlQuestionDocument parsed = codec.readArticle(article);
                validateOrigin(parsed, article, origin, currentOrigin);
                KbImportItem reused = "update".equals(parsed.intent()) ? null
                        : worker.reuse(batch, index, parsed, articleFingerprint(parsed, resources));
                if (reused == null) worker.write(batch, index, resolver.resolve(parsed, resources, batch.getTestRunId()));
            } catch (Exception error) {
                lease.check();
                recordFailure(batch, index, article, error);
            }
            refreshCounts(batch);
            saveProgress(batch);
        }
        refreshCounts(batch);
        if (batch.getSuccessCount() + batch.getFailureCount() != batch.getTotalCount()) {
            throw new ServiceException("导入回执尚未完整，请恢复批次", 409);
        }
        batch.setStatus(batch.getFailureCount() == 0 ? "SUCCESS" : batch.getSuccessCount() == 0 ? "FAILED" : "PARTIAL");
        batch.setLastError(null);
        saveProgress(batch);
    }

    private void validateOrigin(HtmlQuestionDocument parsed, Element article, String origin, String currentOrigin) {
        if (!"create".equals(parsed.intent()) && !currentOrigin.equals(origin)) {
            throw new ServiceException("HTML缺少当前知识库kb-origin或来自其他实例，请重新导出或显式新建");
        }
        if ("create".equals(parsed.intent()) && !origin.isBlank() && !currentOrigin.equals(origin)
                && (parsed.questionId() != null || parsed.parentId() != null
                || parsed.metadata().kpIds() != null && !parsed.metadata().kpIds().isEmpty()
                || !article.select("[data-asset-id]").isEmpty())) {
            throw new ServiceException("跨实例新建必须先映射考点、上传题图并移除旧ID，不能直接复用旧库编号");
        }
    }

    private void refreshCounts(KbImportBatch batch) {
        List<KbImportItem> items = mapper.selectItems(batch.getBatchId());
        batch.setSuccessCount((int) items.stream().filter(item -> !"FAILED".equals(item.getStatus())).count());
        batch.setFailureCount((int) items.stream().filter(item -> "FAILED".equals(item.getStatus())).count());
    }

    private void saveProgress(KbImportBatch batch) {
        if (mapper.updateBatch(batch) != 1) throw new ServiceException("批次执行权已变化，停止旧执行者", 409);
    }

    private void rememberInterrupted(KbImportBatch batch, RuntimeException error) {
        batch.setLastError(truncate(error instanceof ServiceException ? error.getMessage() : "导入执行中断，请查询并恢复批次", 500));
        mapper.updateBatch(batch);
    }

    private void recordFailure(KbImportBatch batch, int index, Element article, Exception error) {
        KbImportItem item = new KbImportItem();
        item.setBatchId(batch.getBatchId());
        item.setItemOrder(index);
        item.setClientKey(truncate(article.attr("data-client-key"), 160));
        item.setIntent(truncate(article.attr("data-intent"), 16));
        item.setStatus("FAILED");
        String message = error instanceof ServiceException ? error.getMessage()
                : error instanceof DuplicateKeyException ? "外部键已被并发请求使用，请查询原批次" : "导入失败，请检查资源、元数据及关联状态";
        item.setMessage(truncate(message, 500));
        worker.recordFailure(batch, item);
    }

    public static String articleFingerprint(HtmlQuestionDocument document, List<ImportResource> resources) {
        StringBuilder input = new StringBuilder(document.fingerprint());
        Set<String> referenced = new HashSet<>();
        for (Element part : new Element[]{document.stem(), document.answer(), document.analysis()}) {
            if (part != null) part.select("img:not([data-asset-id])")
                    .forEach(image -> referenced.add(image.attr("src").replace('\\', '/')));
        }
        resources.stream().filter(resource -> referenced.contains(resource.resourceKey()))
                .sorted(Comparator.comparing(ImportResource::resourceKey))
                .forEach(resource -> input.append('|').append(resource.resourceKey()).append(':')
                        .append(KbAssetServiceImpl.sha256(resource.bytes())));
        return KbAssetServiceImpl.sha256(input.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String batchFingerprint(byte[] html, List<ImportResource> resources, Long sourceId, String runId) {
        StringBuilder value = new StringBuilder(KbAssetServiceImpl.sha256(html)).append('|').append(sourceId).append('|').append(runId);
        resources.stream().sorted(Comparator.comparing(ImportResource::resourceKey))
                .forEach(resource -> value.append('|').append(resource.resourceKey()).append(':')
                        .append(KbAssetServiceImpl.sha256(resource.bytes())));
        return KbAssetServiceImpl.sha256(value.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String origin(byte[] html) {
        Element meta = Jsoup.parse(new String(html, StandardCharsets.UTF_8)).selectFirst("meta[name=kb-origin]");
        return meta == null ? "" : meta.attr("content");
    }

    private String truncate(String text, int limit) {
        if (text == null) return "导入失败";
        return text.substring(0, Math.min(limit, text.length()));
    }
}
