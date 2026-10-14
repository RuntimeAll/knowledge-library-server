package com.ruoyi.knowledge.dto;

import java.util.List;

/** 一个训练考点对应的有效题目集合。 */
public class KbPointMatch {
    private final Long kpId;
    private final String kpName;
    private final List<Long> questionIds;

    public KbPointMatch(Long kpId, String kpName, List<Long> questionIds) {
        this.kpId = kpId;
        this.kpName = kpName;
        this.questionIds = questionIds;
    }

    public Long getKpId() {
        return kpId;
    }

    public String getKpName() {
        return kpName;
    }

    public List<Long> getQuestionIds() {
        return questionIds;
    }
}
