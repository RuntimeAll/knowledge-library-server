package com.ruoyi.knowledge.exporting;

import java.util.List;

public record KbExportRequest(List<Long> questionIds, Long materialId, Boolean includeAnswers, String purpose) {
    public KbExportRequest(List<Long> questionIds, Long materialId, Boolean includeAnswers) { this(questionIds, materialId, includeAnswers, null); }
}
