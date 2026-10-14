package com.ruoyi.knowledge.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/** 来源原件、摘录或证据文件关联。 */
public class KbSourceAttachment extends BaseEntity {
    private Long sourceId;
    private Long assetId;
    private String role;

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
