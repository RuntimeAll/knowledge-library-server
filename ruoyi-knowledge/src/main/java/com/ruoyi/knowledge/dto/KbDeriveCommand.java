package com.ruoyi.knowledge.dto;

import java.util.List;

/** 新建变式的主母题、辅助题和改动说明。 */
public class KbDeriveCommand {
    private Long parentId;
    private List<Long> auxiliaryParentIds;
    private String operation;
    private String changeNote;
    private String preservedNote;

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    @tools.jackson.databind.annotation.JsonSerialize(
            contentUsing = tools.jackson.databind.ser.std.ToStringSerializer.class)
    public List<Long> getAuxiliaryParentIds() {
        return auxiliaryParentIds;
    }

    public void setAuxiliaryParentIds(List<Long> auxiliaryParentIds) {
        this.auxiliaryParentIds = auxiliaryParentIds;
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
}
