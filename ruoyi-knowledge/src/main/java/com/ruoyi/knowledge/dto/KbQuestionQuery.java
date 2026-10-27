package com.ruoyi.knowledge.dto;

import java.util.List;

/** 题目筛选条件与内部考点、参考范围约束。 */
public class KbQuestionQuery {
    private String purpose;
    private List<Long> ids;
    private String approvalJson;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    public String getPurpose() { return purpose; }
    public void setPurpose(String value) { purpose = value; }
    public List<Long> getIds() { return ids; }
    public void setIds(List<Long> value) { ids = value; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getApprovalJson() { return approvalJson; }
    public void setApprovalJson(String value) { approvalJson = value; }
    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer value) { pageNum = value; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer value) { pageSize = value; }
    public void validatePage() {
        if (pageNum == null || pageNum < 1 || pageSize == null || pageSize < 1 || pageSize > 100)
            { throw new com.ruoyi.common.exception.ServiceException("分页需pageNum>=1、pageSize为1至100", 400); }
        if (ids != null && (ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(id -> id == null || id < 1)
                || ids.stream().distinct().count() != ids.size()))
            { throw new com.ruoyi.common.exception.ServiceException("ids需1至100个不重复正整数ID", 400); }
    }
    private String title;
    private String query;
    private String matchMode;
    private Long kpId;
    private Boolean expand;
    private Boolean hasImage;
    private String status;
    private String subject;
    private String contentKind;
    private String layoutMode;
    private String unitKind;
    private String paginationMode;
    private Long outlineId;
    private Long outlineNodeId;
    private String questionType;
    private String difficulty;
    private String creationKind;
    private Long sourceId;
    private Boolean verifiedOnly;
    private List<Long> matchedIds;
    private List<Long> sourceIds;

    public String getLayoutMode() { return layoutMode; }
    public void setLayoutMode(String layoutMode) { this.layoutMode = layoutMode; }
    public String getUnitKind() { return unitKind; }
    public void setUnitKind(String unitKind) { this.unitKind = unitKind; }
    public String getPaginationMode() { return paginationMode; }
    public void setPaginationMode(String paginationMode) { this.paginationMode = paginationMode; }

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

    public String getContentKind() {
        return contentKind;
    }

    public void setContentKind(String contentKind) {
        this.contentKind = contentKind;
    }

    public Long getOutlineId() {
        return outlineId;
    }

    public void setOutlineId(Long outlineId) {
        this.outlineId = outlineId;
    }

    public Long getOutlineNodeId() {
        return outlineNodeId;
    }

    public void setOutlineNodeId(Long outlineNodeId) {
        this.outlineNodeId = outlineNodeId;
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
    private Boolean wrongOnly;
    public Boolean getWrongOnly() {return wrongOnly;}
    public void setWrongOnly(Boolean value) {wrongOnly=value;}
    private Long wrongSourceId;
    public Long getWrongSourceId() {return wrongSourceId;}
    public void setWrongSourceId(Long value) {wrongSourceId=value;}
    private Long examSourceId;
    public Long getExamSourceId() {return examSourceId;}
    public void setExamSourceId(Long value) {examSourceId=value;}
    private String examPurpose;
    public String getExamPurpose() {return examPurpose;}
    public void setExamPurpose(String value) {examPurpose=value;}
    private Integer examYear;
    public Integer getExamYear() {return examYear;}
    public void setExamYear(Integer value) {examYear=value;}
    private String examRegionCode;
    public String getExamRegionCode() {return examRegionCode;}
    public void setExamRegionCode(String value) {examRegionCode=value;}
    private Boolean examRegionDescendants;
    public Boolean getExamRegionDescendants() {return examRegionDescendants;}
    public void setExamRegionDescendants(Boolean value) {examRegionDescendants=value;}
    private List<String> examRegionCodes;
    public List<String> getExamRegionCodes() {return examRegionCodes;}
    public void setExamRegionCodes(List<String> value) {examRegionCodes=value;}
}
