package com.ruoyi.knowledge.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/** 母题到变式的溯源边，不传递真题身份。 */
public class KbDerivation extends BaseEntity {
    private Long parentQuestionId;
    private Long childQuestionId;
    private Boolean isPrimary;
    private String operation;
    private String changeNote;
    private String preservedNote;
    private String parentTitle;
    private String childTitle;

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getParentQuestionId() {
        return parentQuestionId;
    }

    public void setParentQuestionId(Long parentQuestionId) {
        this.parentQuestionId = parentQuestionId;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getChildQuestionId() {
        return childQuestionId;
    }

    public void setChildQuestionId(Long childQuestionId) {
        this.childQuestionId = childQuestionId;
    }

    public Boolean getIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(Boolean isPrimary) {
        this.isPrimary = isPrimary;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getChangeNote() {
        return changeNote;
    }

    public void setChangeNote(String changeNote) {
        this.changeNote = changeNote;
    }

    public String getPreservedNote() {
        return preservedNote;
    }

    public void setPreservedNote(String preservedNote) {
        this.preservedNote = preservedNote;
    }

    public String getParentTitle() {
        return parentTitle;
    }

    public void setParentTitle(String parentTitle) {
        this.parentTitle = parentTitle;
    }

    public String getChildTitle() {
        return childTitle;
    }

    public void setChildTitle(String childTitle) {
        this.childTitle = childTitle;
    }
}
