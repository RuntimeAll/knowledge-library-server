package com.ruoyi.knowledge.publication;

import static com.ruoyi.knowledge.publication.PublicationSupport.fail;
import static com.ruoyi.knowledge.publication.PublicationSupport.id;
import com.ruoyi.knowledge.publication.PublicationModels.Download;
import com.ruoyi.knowledge.publication.PublicationModels.Snapshot;
import com.ruoyi.knowledge.storage.IKbAssetService;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Authenticated issuance only. Issued private OSS URLs expire after 600 seconds, without a live proxy. */
@Service
public class PublicationDownloadService {
    private final PublicationService service;
    private final PublicationReadGate checks;
    private final IKbAssetService assets;
    public PublicationDownloadService(PublicationService service, PublicationReadGate checks, IKbAssetService assets) {
        this.service = service; this.checks = checks; this.assets = assets;
    }
    public Download issue(Long publicationId) {
        service.support.permission("query");
        try { return service.support.gated(() -> {
            var row = service.require(publicationId); service.support.own(row);
            if (!"OSS_DOWNLOAD".equals(service.support.deliveryMode()) || !"ON_SALE".equals(row.status)
                    || !"READY".equals(row.shareState) || row.shareAssetId == null
                    || !Objects.equals(row.contentVersion, row.sharedContentVersion)) {
                throw fail(409, "STATE_CONFLICT", "当前资料没有可下载的已发售包");
            }
            var sale = service.mapper.getOperation(row.id, row.lastSaleOperationId);
            if (sale == null || !"SALE".equals(sale.action) || !"SUCCEEDED".equals(sale.status)
                    || !Objects.equals(sale.contentVersion, row.contentVersion)) {
                throw fail(409, "STATE_CONFLICT", "尚无当前版本的成功发售记录");
            }
            var snapshot = service.support.decode(sale.snapshotJson, Snapshot.class);
            var current = checks.evaluate(row, sale);
            if (!"PASSED".equals(current.status) || snapshot == null || snapshot.check() == null
                    || !Objects.equals(current.dependencyHash, snapshot.check().dependencyHash)) {
                throw fail(409, "DEPENDENCY_CHANGED", "资料依据已变化，请撤回重检后重新发售");
            }
            var asset = assets.requireReady(row.shareAssetId);
            if (!"application/zip".equals(asset.getContentType()) || !"IMPORT_RESOURCE".equals(asset.getPurpose())) {
                throw fail(409, "STATE_CONFLICT", "当前产物不是发布下载包，请撤回后重新发售");
            }
            var signed = assets.publicationDownload(asset.getAssetId());
            if (signed.url() == null || !signed.url().startsWith("https://")) {
                throw fail(503, "DEPENDENCY_UNAVAILABLE", "OSS下载需配置HTTPS存储入口");
            }
            return new Download(signed.url(), signed.expiresInSeconds(), asset.getOriginalName(), id(asset.getAssetId()), asset.getSha256());
        });
        } catch (PublicationException e) { throw e; }
        catch (RuntimeException e) { throw fail(503, "DEPENDENCY_UNAVAILABLE", "资料或下载签名暂时无法核对，请稍后重试"); }
    }
}
