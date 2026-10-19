package com.ruoyi.knowledge.dto;

import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.domain.KbSource;

import java.util.List;

public record KbAgentSourceRequest(
        String externalKey,
        KbSource source,
        KbCatalogProfile profile,
        List<KbOutlineScope> outlineScopes,
        List<Long> declaredKpIds,
        List<KbRegionLink> regions,
        KbExamDetail examDetail) {
    public KbAgentSourceRequest(String externalKey, KbSource source) {
        this(externalKey, source, null, null, null, null, null);
    }

    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void unknown(String name, Object value) {
        throw new com.ruoyi.common.exception.ServiceException("未定义字段: " + name, 400);
    }
}
