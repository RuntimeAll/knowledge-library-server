package com.ruoyi.knowledge.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/** 公共题目与考点的直接关联。 */
public class KbQuestionKp extends BaseEntity {
    private Long questionId;
    private Long kpId;

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getKpId() {
        return kpId;
    }

    public void setKpId(Long kpId) {
        this.kpId = kpId;
    }
}
