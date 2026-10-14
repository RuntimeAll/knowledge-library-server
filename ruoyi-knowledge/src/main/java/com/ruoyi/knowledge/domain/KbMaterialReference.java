package com.ruoyi.knowledge.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/** 专项采用的参考来源及采用理由。 */
public class KbMaterialReference extends BaseEntity {
    private Long sourceId;
    private String note;
    private String sourceTitle;
    private String extractionStatus;

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getSourceTitle() {
        return sourceTitle;
    }

    public void setSourceTitle(String sourceTitle) {
        this.sourceTitle = sourceTitle;
    }

    public String getExtractionStatus() {
        return extractionStatus;
    }

    public void setExtractionStatus(String extractionStatus) {
        this.extractionStatus = extractionStatus;
    }
}
