package com.ruoyi.knowledge.html;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.ruoyi.common.exception.ServiceException;

/** Nullable fields mean omitted; explicit empty strings and lists mean clear. */
public record HtmlMetadata(String title, String subject, String questionType, String difficulty,
                           List<Long> kpIds, String sourceNote, List<Long> auxiliaryParentIds,
                           String operation, String changeNote, String preservedNote, String contentKind,
                           String layoutMode, String unitKind, String paginationMode, String layoutNote) {
    @JsonAnySetter
    public void unknown(String name, Object value) {
        throw new ServiceException("metadata包含未定义字段：" + name, 400);
    }

    public static HtmlMetadata empty() {
        return new HtmlMetadata(null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }
}
