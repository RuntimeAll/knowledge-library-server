package com.ruoyi.knowledge.publication;

import static com.ruoyi.knowledge.publication.PublicationSupport.fail;
import com.ruoyi.knowledge.publication.PublicationModels.Command;
import com.ruoyi.knowledge.publication.PublicationModels.Snapshot;
import com.ruoyi.knowledge.publication.PublicationRows.Publication;
import com.ruoyi.knowledge.publication.PublicationRows.PublicationOperation;
import com.ruoyi.knowledge.storage.IKbAssetService;
import com.ruoyi.knowledge.storage.domain.KbAsset;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** OSS side effects happen outside the quality gate and database transaction. */
@Service
public class PublicationShareService {
    private final java.util.concurrent.locks.ReentrantLock[] generationLocks = java.util.stream.IntStream.range(0, 64)
            .mapToObj(i -> new java.util.concurrent.locks.ReentrantLock()).toArray(java.util.concurrent.locks.ReentrantLock[]::new);
    private final PublicationService service;
    private final PublicationCheckService checks;
    private final IKbAssetService assets;
    private final PublicationProperties properties;
    private final PublicationBundleService bundles;
    public PublicationShareService(PublicationService service, PublicationCheckService checks,
            IKbAssetService assets, PublicationProperties properties, PublicationBundleService bundles) {
        this.service = service; this.checks = checks; this.assets = assets; this.properties = properties; this.bundles = bundles;
    }
    public PublicationOperation retry(Long id, Command command) {
        service.support.permission("shareRetry");
        Publication row = service.require(id); service.support.own(row);
        PublicationOperation op = service.mapper.operation(id, command.operationKey);
        if (op == null || !"GENERATE_LINK".equals(op.action) || !Objects.equals(op.parentOperationId, command.saleOperationId)) {
            throw fail(409, "STATE_CONFLICT", "只能重试原生成操作");
        }
        if (!service.support.sameActor(op)) { throw fail(403, "FORBIDDEN", "只有原生成者可重试交付；其他管理员可撤回后重新发售"); }
        service.same(op, command, "GENERATE_LINK");
        return generate(id, command.saleOperationId);
    }
    public PublicationOperation generate(Long id, Long saleId) {
        // One server process owns the review ledger. Serialize only this publication's slow OSS work;
        // the global quality gate is deliberately released while bytes are uploaded.
        var lock = generationLocks[Math.floorMod(id.hashCode(), generationLocks.length)];
        if (!lock.tryLock()) {
            PublicationOperation pending = service.mapper.operation(id, "generate:" + saleId);
            if (pending == null) { throw fail(409, "OPERATION_IN_PROGRESS", "链接生成正在启动，请查原发售回执"); }
            return pending;
        }
        try { return generateLocked(id, saleId); } finally { lock.unlock(); }
    }
    private PublicationOperation generateLocked(Long id, Long saleId) {
        PublicationOperation op = service.support.gated(() -> prepare(id, saleId));
        if ("SUCCEEDED".equals(op.status)) { return op; }
        try {
            String code = reserveCode(id, saleId, op.contentVersion);
            Snapshot generation = service.support.decode(op.snapshotJson, Snapshot.class);
            String mode = generation.deliveryMode() == null ? "PUBLIC_PAGE" : generation.deliveryMode();
            if (!Objects.equals(mode, service.support.deliveryMode())) {
                throw fail(409, "STATE_CONFLICT", "交付模式已变化，请恢复原模式重试或撤回后重新发售");
            }
            KbAsset page = "OSS_DOWNLOAD".equals(mode) ? bundles.create(id, op) : createPage(id, code, op);
            final String finalCode = code;
            service.support.gated(() -> {
                Publication observed = service.require(id); assertCurrent(observed, saleId, op.contentVersion);
                var fresh = checks.evaluate(observed, false);
                Snapshot snapshot = service.support.decode(op.snapshotJson, Snapshot.class);
                if (!"PASSED".equals(fresh.status) || !Objects.equals(fresh.dependencyHash, snapshot.check().dependencyHash)) {
                    throw fail(409, "DEPENDENCY_CHANGED", "生成期间关联依据已变化");
                }
                return service.support.transaction(() -> {
                Publication current = service.lock(id); assertCurrent(current, saleId, op.contentVersion);
                if (!Objects.equals(observed.rowVersion, current.rowVersion)) { throw fail(409, "VERSION_CONFLICT", "资料在生成期间发生变化"); }
                current.shareCode = finalCode; current.shareAssetId = page.getAssetId(); current.shareState = "READY";
                current.sharedContentVersion = current.contentVersion; service.support.updated(current);
                PublicationService.changed(service.mapper.updatePublication(current)); current.rowVersion++;
                op.status = "SUCCEEDED"; op.finishedAt = new Date(); op.errorCode = null; op.errorMessage = null;
                op.resultJson = service.support.encode(service.result(current));
                PublicationService.changed(service.mapper.updatePublicationOperation(op)); op.rowVersion++; return null;
                });
            });
        } catch (Exception e) {
            service.support.gated(() -> service.support.transaction(() -> {
                Publication current = service.lock(id);
                if ("ON_SALE".equals(current.status) && Objects.equals(current.lastSaleOperationId, saleId)) {
                    current.shareState = "FAILED"; service.support.updated(current); PublicationService.changed(service.mapper.updatePublication(current));
                }
                op.status = "FAILED"; op.errorCode = "GENERATION_FAILED";
                op.errorMessage = "交付生成未完成，请检查交付模式、文件大小、模板及存储后重试";
                op.finishedAt = new Date(); PublicationService.changed(service.mapper.updatePublicationOperation(op)); op.rowVersion++; return null;
            }));
        }
        return op;
    }
    private KbAsset createPage(Long id, String code, PublicationOperation op) throws java.io.IOException {
        service.support.publicUrl(code);
        if (properties.getPageTemplateAssetId() == null) { throw fail(503, "GENERATION_FAILED", "手机发布页模板尚未配置"); }
        KbAsset template = assets.requireReady(properties.getPageTemplateAssetId());
        if (template.getFileSize() > 524288) { throw fail(503, "GENERATION_FAILED", "手机页面模板超过限制"); }
        String html;
        try (InputStream input = assets.openContent(template.getAssetId())) { html = new String(input.readNBytes(524289), StandardCharsets.UTF_8); }
        if (!html.contains("</head>")) { throw fail(503, "GENERATION_FAILED", "手机页面模板格式无效"); }
        html = html.replace("</head>", "<meta name=\"publication-code\" content=\"" + code + "\"></head>");
        return assets.uploadBytes(html.getBytes(StandardCharsets.UTF_8), "publication-" + id + ".html",
                "text/html", "IMPORT_HTML", null, "publication-page-" + op.id);
    }
    private String reserveCode(Long id, Long saleId, Long version) {
        return service.support.gated(() -> service.support.transaction(() -> {
            Publication row = service.lock(id); assertCurrent(row, saleId, version);
            if (row.shareCode == null) {
                byte[] bytes = new byte[16]; new SecureRandom().nextBytes(bytes);
                row.shareCode = HexFormat.of().formatHex(bytes); service.support.updated(row);
                PublicationService.changed(service.mapper.updatePublication(row));
            }
            return row.shareCode;
        }));
    }
    private PublicationOperation prepare(Long id, Long saleId) {
        Publication row = service.require(id);
        PublicationOperation sale = service.mapper.getOperation(id, saleId);
        if (sale == null || !"SALE".equals(sale.action) || !"SUCCEEDED".equals(sale.status)) {
            throw fail(409, "STATE_CONFLICT", "尚无已完成发售操作");
        }
        assertCurrent(row, saleId, sale.contentVersion);
        PublicationOperation existing = service.mapper.operation(id, "generate:" + saleId);
        if (existing != null) {
            if (!"SUCCEEDED".equals(existing.status)) {
                if (!service.support.sameActor(existing)) { throw fail(403, "FORBIDDEN", "只有原生成者可继续交付生成"); }
                existing.attemptCount++; existing.status = "PENDING";
                service.support.transaction(() -> { PublicationService.changed(service.mapper.updatePublicationOperation(existing)); existing.rowVersion++; return null; });
            }
            return existing;
        }
        Snapshot snapshot = service.support.decode(sale.snapshotJson, Snapshot.class);
        var checked = checks.evaluate(row, false);
        if (!"PASSED".equals(checked.status) || !Objects.equals(checked.dependencyHash, snapshot.check().dependencyHash)) {
            throw fail(409, "DEPENDENCY_CHANGED", "已发售内容依据发生变化，停止生成");
        }
        return service.support.transaction(() -> {
            Publication locked = service.lock(id); assertCurrent(locked, saleId, sale.contentVersion);
            Command command = new Command(); command.operationKey = "generate:" + saleId;
            command.expectedRowVersion = row.rowVersion; command.contentVersion = row.contentVersion; command.saleOperationId = saleId;
            PublicationOperation op = service.newOperation(locked, command, "GENERATE_LINK", new Snapshot(snapshot.publication(), snapshot.check(), command, service.support.deliveryMode()));
            op.parentOperationId = saleId; op.attemptCount = 1;
            PublicationService.changed(service.mapper.updatePublicationOperation(op)); op.rowVersion++; return op;
        });
    }
    private static void assertCurrent(Publication row, Long saleId, Long version) {
        if (!"ON_SALE".equals(row.status) || !Objects.equals(row.lastSaleOperationId, saleId)
                || !Objects.equals(row.contentVersion, version)) { throw fail(409, "STATE_CONFLICT", "资料已撤回或版本已改变"); }
    }
}
