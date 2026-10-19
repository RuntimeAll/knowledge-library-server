package com.ruoyi.knowledge.layout;

import static com.ruoyi.knowledge.service.impl.KnowledgeRules.*;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.knowledge.catalog.KbProductCatalogSnapshot;
import com.ruoyi.knowledge.domain.KbProduct;
import com.ruoyi.knowledge.mapper.KbLayoutMapper;
import com.ruoyi.knowledge.mapper.KbProductMapper;
import com.ruoyi.knowledge.service.IKbCatalogService;
import com.ruoyi.knowledge.storage.IKbAssetService;

import org.springframework.stereotype.Component;

/** Internal-only trusted path. The source of truth is a fenced DB snapshot, never client JSON. */
@Component
public class KbFrozenProductArchiver {
    private final KbLayoutMapper jobs;
    private final KbProductMapper products;
    private final IKbCatalogService catalog;
    private final IKbAssetService assets;

    public KbFrozenProductArchiver(
            KbLayoutMapper jobs,
            KbProductMapper products,
            IKbCatalogService catalog,
            IKbAssetService assets) {
        this.jobs = jobs;
        this.products = products;
        this.catalog = catalog;
        this.assets = assets;
    }

    KbProduct archive(KbRenderJob claim, String outputKey, Long assetId) {
        var job = found(jobs.lockOwnedJob(claim.getId(), claim.getLeaseToken()));
        for (var existing : jobs.outputs(job.getId()))
            if (existing.getOutputKey().equals(outputKey))
                return products.selectById(existing.getProductId());
        var frozen = JSON.parseObject(job.getConfigSnapshotJson(), KbRenderFrozenConfig.class);
        if (!frozen.getRequest().getOutputRoles().contains(outputKey))
            throw new IllegalStateException("Output outside frozen request");
        KbProduct data = frozen.getProductCatalog();
        assets.requireOriginal(assetId);
        data.setId(null);
        data.setAssetId(assetId);
        data.setTitle(
                text(
                        data.getTitle()
                                + " - "
                                + switch (outputKey) {
                                    case "STUDENT" -> "学生版";
                                    case "INLINE_ANSWER" -> "原位答案版";
                                    default -> "独立答案版";
                                },
                        "成品标题",
                        300));
        data.setRenderJobId(job.getId());
        data.setRenderOutputKey(outputKey);
        data.setCreateBy(job.getCreateBy());
        data.setVersionNo(1);
        data.setRootProductId(null);
        data.setPreviousProductId(null);
        for (var previous : KbLayoutProtocol.list(frozen.getRequest().getPreviousOutputs()))
            if (previous.outputKey().equals(outputKey)) {
                var prior = found(products.selectById(previous.productId()));
                found(products.lockById(prior.getRootProductId()));
                data.setPreviousProductId(prior.getId());
                data.setRootProductId(prior.getRootProductId());
                data.setVersionNo(products.nextVersion(prior.getRootProductId()));
            }
        data.getProfile().setAudienceRole(outputKey);
        data.getProfile().setCatalogStatus("READY");
        data.setSnapshotJson(
                KbLayoutProtocol.json(
                        new KbProductCatalogSnapshot(
                                "kb-product-snapshot/v2",
                                data.getMaterialId(),
                                job.getMaterialRowVersion(),
                                null,
                                data.getProfile(),
                                data.getOutlineScopes(),
                                data.getDeclaredKpIds(),
                                data.getRegions(),
                                data.getQuestionRefs(),
                                data.getReferenceSources())));
        products.insert(data);
        if (data.getRootProductId() == null) products.initializeRoot(data.getId());
        catalog.persist("PRODUCT", data.getId(), data, job.getCreateBy(), true);
        return data;
    }
}
