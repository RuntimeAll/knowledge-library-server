package com.ruoyi.knowledge.html;

import java.util.List;

/** Nullable fields mean omitted; explicit empty strings and lists mean clear. */
public record HtmlMetadata(String title, String subject, String questionType, String difficulty,
                           List<Long> kpIds, String sourceNote, List<Long> auxiliaryParentIds,
                           String operation, String changeNote, String preservedNote) {
    public static HtmlMetadata empty() {
        return new HtmlMetadata(null, null, null, null, null, null, null, null, null, null);
    }
}
