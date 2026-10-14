package com.ruoyi.knowledge.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/** 考点嵌套或组合的有向边。 */
public class KbKpEdge extends BaseEntity {
    private Long fromKpId;
    private Long toKpId;
    private String kind;

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getFromKpId() {
        return fromKpId;
    }

    public void setFromKpId(Long fromKpId) {
        this.fromKpId = fromKpId;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getToKpId() {
        return toKpId;
    }

    public void setToKpId(Long toKpId) {
        this.toKpId = toKpId;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }
}
