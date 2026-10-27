package com.ruoyi.knowledge.service.impl;

import static com.ruoyi.knowledge.service.impl.KnowledgeRules.changed;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.found;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.text;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.unreferenced;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.version;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.domain.KbMaterial;
import com.ruoyi.knowledge.domain.KbProduct;
import com.ruoyi.knowledge.mapper.KbMaterialMapper;
import com.ruoyi.knowledge.mapper.KbProductMapper;
import com.ruoyi.knowledge.service.IKbCatalogService;
import com.ruoyi.knowledge.service.IKbMaterialService;
import com.ruoyi.knowledge.service.IKbProductService;
import com.ruoyi.knowledge.storage.IKbAssetService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** 成品文件不可变，修改版使用新的记录与资产。 */
@Service
public class KbProductServiceImpl implements IKbProductService {
    private final KbProductMapper mapper;
    private final KbMaterialMapper materials;
    private final IKbAssetService assets;
    private final IKbMaterialService materialService;
    private final IKbCatalogService catalog;
    private final com.ruoyi.knowledge.review.KbReviewService reviews;

    public KbProductServiceImpl(
            KbProductMapper mapper,
            KbMaterialMapper materials,
            IKbAssetService assets,
            IKbMaterialService materialService,
            IKbCatalogService catalog, com.ruoyi.knowledge.review.KbReviewService reviews) {
        this.mapper = mapper;
        this.materials = materials;
        this.assets = assets;
        this.materialService = materialService;
        this.catalog = catalog;
        this.reviews = reviews;
    }

    public void prepareQuery(KbCatalogQuery query) {
        catalog.prepareQuery("PRODUCT", query);
    }

    public List<KbProduct> list(KbCatalogQuery query) {
        List<KbProduct> page = mapper.selectList(query);
        if (page.isEmpty()) return page;
        Map<Long, KbProduct> targets = new LinkedHashMap<>();
        page.forEach(row -> targets.put(row.getId(), row));
        catalog.enrichBatch("PRODUCT", targets);
        for (KbProduct row : page) {
            applyRenderSnapshot(row);
            row.setMatchedProductId(row.getId());
            java.util.ArrayList<String> reasons = new java.util.ArrayList<>();
            if (query.getOutlineId() != null
                    || query.getGrade() != null
                    || query.getTerm() != null
                    || query.getTextbookVersion() != null) reasons.add("命中本成品版本声明的教材范围");
            if (query.getKpId() != null)
                reasons.add("ACTUAL".equals(query.getCoverageMode()) ? "命中本版本题单考点" : "命中声明考点");
            row.setMatchReasons(reasons);
        }
        return page;
    }

    public KbProduct get(Long id) {
        KbProduct data = found(mapper.selectById(id));
        catalog.enrich("PRODUCT", id, data);
        applyRenderSnapshot(data);
        data.setMatchedProductId(id);
        return data;
    }

    private void applyRenderSnapshot(KbProduct data) {
        if (data.getRenderJobId() != null && data.getSnapshotJson() != null) {
            KbProductCatalogSnapshot snapshot =
                    com.alibaba.fastjson2.JSON.parseObject(
                            data.getSnapshotJson(), KbProductCatalogSnapshot.class);
            data.setQuestionRefs(snapshot.questionRefs());
            data.setReferenceSources(snapshot.referenceSources());
            data.setActualKpIds(snapshot.declaredKpIds());
        }
    }

    public List<KbProduct> versions(Long id) {
        KbProduct data = get(id);
        KbCatalogQuery query = new KbCatalogQuery();
        query.setRootProductId(data.getRootProductId());
        return list(query);
    }

    @com.ruoyi.knowledge.review.KbQualityWrite
    @Transactional
    public KbProduct create(KbProduct data) {
        if (data.getRenderJobId() != null || data.getRenderOutputKey() != null)
            throw new ServiceException("生成依据只允许由后台任务归档", 403);
        data.setTitle(text(data.getTitle(), "成品标题", 300));
        if (data.getPreviousProductId() != null) {
            KbProduct previous = found(mapper.selectById(data.getPreviousProductId()));
            found(mapper.lockById(previous.getRootProductId()));
        }
        if (data.getExpectedMaterialRowVersion() != null && data.getMaterialId() == null)
            throw new ServiceException("expectedMaterialRowVersion须与materialId一起提交", 400);
        if (data.getMaterialId() != null) {
            KbMaterial locked = found(materials.lockById(data.getMaterialId()));
            if (data.getExpectedMaterialRowVersion() != null) {
                version(data.getExpectedMaterialRowVersion(), locked.getRowVersion());
                if (data.getQuestionRefs() == null)
                    throw new ServiceException("按专项版本归档须显式提交导出文件中的questionRefs", 400);
            }
        }
        KbMaterial material = null;
        if (data.getMaterialId() != null) {
            material = materialService.get(data.getMaterialId());
            if (data.getQuestionRefs() == null) {
                java.util.ArrayList<KbProductQuestionRef> refs = new java.util.ArrayList<>();
                int seq = 0;
                for (var q : material.getQuestions()) {
                    KbProductQuestionRef ref = new KbProductQuestionRef();
                    ref.setSeq(++seq);
                    ref.setQuestionId(q.getId());
                    ref.setQuestionRowVersion(q.getRowVersion());
                    ref.setStemHash(q.getStemHash());
                    refs.add(ref);
                }
                data.setQuestionRefs(refs);
            }
            if (data.getReferenceSources() == null) {
                java.util.ArrayList<KbProductReferenceSource> refs = new java.util.ArrayList<>();
                for (Long sourceId :
                        material.getReferenceSources().stream()
                                .map(com.ruoyi.knowledge.domain.KbMaterialReference::getSourceId)
                                .toList()) {
                    KbProductReferenceSource ref = new KbProductReferenceSource();
                    ref.setSourceId(sourceId);
                    refs.add(ref);
                }
                data.setReferenceSources(refs);
            }
        }
        catalog.prepareProduct(data, null);
        if (data.getQuestionRefs() != null && !data.getQuestionRefs().isEmpty())
            { reviews.assertUsable(data.getQuestionRefs().stream().map(KbProductQuestionRef::getQuestionId).distinct().toList()); }
        if (data.getPreviousProductId() != null) {
            KbProduct previous = get(data.getPreviousProductId());
            if (!java.util.Objects.equals(audienceRole(previous), audienceRole(data)))
                throw new ServiceException("不同使用角色必须分别建立成品版本链", 409);
        }
        validate(data);
        data.setSnapshotJson(productSnapshot(data, material));
        if (data.getPreviousProductId() != null) {
            KbProduct previous = found(mapper.selectById(data.getPreviousProductId()));
            data.setRootProductId(previous.getRootProductId());
            data.setVersionNo(mapper.nextVersion(previous.getRootProductId()));
        } else {
            data.setRootProductId(null);
            data.setVersionNo(1);
        }
        mapper.insert(data);
        if (data.getRootProductId() == null) mapper.initializeRoot(data.getId());
        catalog.persist("PRODUCT", data.getId(), data, data.getCreateBy(), true);
        return get(data.getId());
    }

    private String productSnapshot(KbProduct data, KbMaterial material) {
        return                 com.alibaba.fastjson2.JSON.toJSONString(
                        new KbProductCatalogSnapshot(
                                "kb-product-snapshot/v2",
                                data.getMaterialId(),
                                material == null ? null : material.getRowVersion(),
                                material == null ? null : material.getTitle(),
                                data.getProfile(),
                                data.getOutlineScopes(),
                                data.getDeclaredKpIds(),
                                data.getRegions(),
                                data.getQuestionRefs(),
                                data.getReferenceSources()),
                        com.alibaba.fastjson2.JSONWriter.Feature.WriteLongAsString);
    }

    @com.ruoyi.knowledge.review.KbQualityWrite
    @Transactional
    public KbProduct update(KbProduct data) {
        found(mapper.lockById(data.getId()));
        KbProduct old = get(data.getId());
        if (data.getRenderJobId() != null && !data.getRenderJobId().equals(old.getRenderJobId())
                || data.getRenderOutputKey() != null
                        && !data.getRenderOutputKey().equals(old.getRenderOutputKey()))
            throw new ServiceException("生成依据不可修改", 409);
        data.setRenderJobId(old.getRenderJobId());
        data.setRenderOutputKey(old.getRenderOutputKey());
        version(data.getRowVersion(), old.getRowVersion());
        if (data.getAssetId() != null && !data.getAssetId().equals(old.getAssetId())
                || data.getMaterialId() != null && !data.getMaterialId().equals(old.getMaterialId())
                || data.getPreviousProductId() != null
                        && !data.getPreviousProductId().equals(old.getPreviousProductId()))
            throw new ServiceException("成品文件、生成专项及版本关系已冻结，请另存新版本", 409);
        if (data.getTitle() == null) data.setTitle(old.getTitle());
        if (data.getRemark() == null) data.setRemark(old.getRemark());
        data.setAssetId(old.getAssetId());
        data.setMaterialId(old.getMaterialId());
        data.setPreviousProductId(old.getPreviousProductId());
        data.setRootProductId(old.getRootProductId());
        data.setVersionNo(old.getVersionNo());
        data.setSnapshotJson(old.getSnapshotJson());
        catalog.prepareProduct(data, old);
        if (old.getRenderOutputKey() != null
                && !old.getRenderOutputKey().equals(audienceRole(data)))
            throw new ServiceException("生成成品的使用角色由冻结输出确定，不能修改", 409);
        if (!java.util.Objects.equals(audienceRole(old), audienceRole(data))
                && (audienceRole(old) != null
                        || old.getPreviousProductId() != null
                        || mapper.countNewerVersions(old.getId()) != 0))
            throw new ServiceException("成品使用角色已固定，请作为独立成品登记", 409);
        validate(data);
        changed(mapper.update(data));
        catalog.persist("PRODUCT", data.getId(), data, data.getUpdateBy(), false);
        return get(data.getId());
    }

    @com.ruoyi.knowledge.review.KbQualityWrite
    @Transactional
    public void delete(Long[] values) {
        for (Long id : values) {
            found(mapper.lockById(id));
            unreferenced(mapper.countNewerVersions(id), "该成品还有后续版本引用，请从最新版本处理");
            catalog.delete("PRODUCT", id);
            mapper.deleteById(id);
        }
    }

    private void validate(KbProduct data) {
        data.setTitle(text(data.getTitle(), "成品标题", 300));
        assets.requireOriginal(data.getAssetId());
        if (data.getMaterialId() != null) found(materials.lockById(data.getMaterialId()));
    }

    private static String audienceRole(KbProduct data) {
        return data.getProfile() == null ? null : data.getProfile().getAudienceRole();
    }

    @com.ruoyi.knowledge.review.KbQualityWrite
    @Transactional
    public KbProduct catalog(Long id, KbCatalogCommand command, String actor) {
        KbProduct data = new KbProduct();
        data.setId(id);
        data.setRowVersion(command.getRowVersion());
        data.setUpdateBy(actor);
        data.setProfile(command.getProfile());
        data.setOutlineScopes(command.getOutlineScopes());
        data.setDeclaredKpIds(command.getDeclaredKpIds());
        data.setRegions(command.getRegions());
        data.setQuestionRefs(command.getQuestionRefs());
        data.setReferenceSources(command.getReferenceSources());
        return update(data);
    }
}
