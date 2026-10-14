package com.ruoyi.knowledge.exporting;

import java.util.List;

public record KbExportRequest(List<Long> questionIds, Long materialId, Boolean includeAnswers) { }
