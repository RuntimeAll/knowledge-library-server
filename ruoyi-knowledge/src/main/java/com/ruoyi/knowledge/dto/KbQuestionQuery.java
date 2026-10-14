package com.ruoyi.knowledge.dto;

import java.util.List;

/** 题目筛选条件与内部考点、参考范围约束。 */
public class KbQuestionQuery {
    private String title;
    private String query;
    private String matchMode;
    private Long kpId;
    private Boolean expand;
    private Boolean hasImage;
    private String status;
    private String subject;
    private String questionType;
    private String difficulty;
    private String creationKind;
    private Long sourceId;
    private Boolean verifiedOnly;
    private List<Long> matchedIds;
    private List<Long> sourceIds;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getEscapedQuery() {
        return query == null
                ? null
                : query.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    public String getMatchMode() {
        return matchMode;
    }

    public void setMatchMode(String matchMode) {
        this.matchMode = matchMode;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getKpId() {
        return kpId;
    }

    public void setKpId(Long kpId) {
        this.kpId = kpId;
    }

    public Boolean getExpand() {
        return expand;
    }

    public void setExpand(Boolean expand) {
        this.expand = expand;
    }

    public Boolean getHasImage() {
        return hasImage;
    }

    public void setHasImage(Boolean hasImage) {
        this.hasImage = hasImage;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getQuestionType() {
        return questionType;
    }

    public void setQuestionType(String questionType) {
        this.questionType = questionType;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getCreationKind() {
        return creationKind;
    }

    public void setCreationKind(String creationKind) {
        this.creationKind = creationKind;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public Boolean getVerifiedOnly() {
        return verifiedOnly;
    }

    public void setVerifiedOnly(Boolean verifiedOnly) {
        this.verifiedOnly = verifiedOnly;
    }

    @tools.jackson.databind.annotation.JsonSerialize(
            contentUsing = tools.jackson.databind.ser.std.ToStringSerializer.class)
    public List<Long> getMatchedIds() {
        return matchedIds;
    }

    public void setMatchedIds(List<Long> matchedIds) {
        this.matchedIds = matchedIds;
    }

    @tools.jackson.databind.annotation.JsonSerialize(
            contentUsing = tools.jackson.databind.ser.std.ToStringSerializer.class)
    public List<Long> getSourceIds() {
        return sourceIds;
    }

    public void setSourceIds(List<Long> sourceIds) {
        this.sourceIds = sourceIds;
    }
}
