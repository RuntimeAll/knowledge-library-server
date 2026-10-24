package com.ruoyi.knowledge.dto;

import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.domain.KbProduct;

import java.util.List;

public record KbAgentProductRequest(
        String externalKey,
        KbProduct product,
        KbCatalogProfile profile,
        List<KbOutlineScope> outlineScopes,
        List<Long> declaredKpIds,
        List<KbRegionLink> regions,
        List<KbProductQuestionRef> questionRefs,
        List<KbProductReferenceSource> referenceSources,
        Long expectedMaterialRowVersion) {
    public KbAgentProductRequest(String externalKey, KbProduct product) {
        this(externalKey, product, null, null, null, null, null, null, null);
    }

    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void unknown(String name, Object value) {
        throw new com.ruoyi.common.exception.ServiceException("未定义字段: " + name, 400);
    }
}
