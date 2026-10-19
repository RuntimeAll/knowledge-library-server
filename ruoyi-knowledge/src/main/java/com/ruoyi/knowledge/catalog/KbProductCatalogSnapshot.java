package com.ruoyi.knowledge.catalog;

import java.util.List;

public record KbProductCatalogSnapshot(
        String schemaVersion,
        Long materialId,
        Long materialRowVersion,
        String materialTitle,
        KbCatalogProfile profile,
        List<KbOutlineScope> outlineScopes,
        List<Long> declaredKpIds,
        List<KbRegionLink> regions,
        List<KbProductQuestionRef> questionRefs,
        List<KbProductReferenceSource> referenceSources) {}
