package com.ruoyi.knowledge.dto;

import java.util.List;

/** 冻结成品创建时的专项与题目版本，后续元数据编辑不得重算。 */
public record KbProductSnapshot(
        String materialId, String rowVersion, String title, List<QuestionVersion> questions) {
    public record QuestionVersion(String questionId, String rowVersion) {}
}
