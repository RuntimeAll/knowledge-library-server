package com.ruoyi.knowledge.catalog;

import java.util.List;

public class KbOutlineScope extends CatalogFields {
    private Long id;
    private Long outlineId;
    private String scopeKind;
    private List<Long> nodeIds;
    private String note;
    private String outlineTitle;
    private String subject;
    private String textbookVersion;
    private String grade;
    private String term;
    private String outlineStatus;

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getId() {
        return id;
    }

    public void setId(Long value) {
        id = value;
        supplied("id");
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getOutlineId() {
        return outlineId;
    }

    public void setOutlineId(Long value) {
        outlineId = value;
        supplied("outlineId");
    }

    public String getScopeKind() {
        return scopeKind;
    }

    public void setScopeKind(String value) {
        scopeKind = value;
        supplied("scopeKind");
    }

    @tools.jackson.databind.annotation.JsonSerialize(
            contentUsing = tools.jackson.databind.ser.std.ToStringSerializer.class)
    public List<Long> getNodeIds() {
        return nodeIds;
    }

    public void setNodeIds(List<Long> value) {
        nodeIds = value;
        supplied("nodeIds");
    }

    public String getNote() {
        return note;
    }

    public void setNote(String value) {
        note = value;
        supplied("note");
    }

    public String getOutlineTitle() {
        return outlineTitle;
    }

    public void setOutlineTitle(String value) {
        outlineTitle = value;
        supplied("outlineTitle");
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String value) {
        subject = value;
        supplied("subject");
    }

    public String getTextbookVersion() {
        return textbookVersion;
    }

    public void setTextbookVersion(String value) {
        textbookVersion = value;
        supplied("textbookVersion");
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String value) {
        grade = value;
        supplied("grade");
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String value) {
        term = value;
        supplied("term");
    }

    public String getOutlineStatus() {
        return outlineStatus;
    }

    public void setOutlineStatus(String value) {
        outlineStatus = value;
        supplied("outlineStatus");
    }
}
