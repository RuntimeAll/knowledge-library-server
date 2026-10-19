package com.ruoyi.knowledge.catalog;

import java.util.List;

public class KbCatalogQuery {
    private String title;
    private String subject;
    private String resourceType;
    private String catalogStatus;
    private String sourceKind;
    private String extractionStatus;
    private String authenticityStatus;
    private String chapterMatch;
    private String textbookVersion;
    private String grade;
    private String term;
    private String yearField;
    private String regionRole;
    private String regionCode;
    private String coverageMode;
    private String examPurpose;
    private String organizerLevel;
    private String paperNature;
    private String audienceRole;
    private String provenanceStatus;
    private String historyMode;
    private Long outlineId;
    private Long outlineNodeId;
    private Long kpId;
    private Long rootProductId;
    private Long materialId;
    private Integer yearFrom;
    private Integer yearTo;
    private Integer examYear;
    private Integer academicYearStart;
    private Boolean regionDescendants;
    private Boolean includeNationwide;
    private List<Long> chapterTargetIds;
    private List<Long> chapterAncestorIds;
    private List<String> regionCodes;

    public String getTitle() {
        return title;
    }

    public void setTitle(String value) {
        title = value;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String value) {
        subject = value;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String value) {
        resourceType = value;
    }

    public String getCatalogStatus() {
        return catalogStatus;
    }

    public void setCatalogStatus(String value) {
        catalogStatus = value;
    }

    public String getSourceKind() {
        return sourceKind;
    }

    public void setSourceKind(String value) {
        sourceKind = value;
    }

    public String getExtractionStatus() {
        return extractionStatus;
    }

    public void setExtractionStatus(String value) {
        extractionStatus = value;
    }

    public String getAuthenticityStatus() {
        return authenticityStatus;
    }

    public void setAuthenticityStatus(String value) {
        authenticityStatus = value;
    }

    public String getChapterMatch() {
        return chapterMatch;
    }

    public void setChapterMatch(String value) {
        chapterMatch = value;
    }

    public String getTextbookVersion() {
        return textbookVersion;
    }

    public void setTextbookVersion(String value) {
        textbookVersion = value;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String value) {
        grade = value;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String value) {
        term = value;
    }

    public String getYearField() {
        return yearField;
    }

    public void setYearField(String value) {
        yearField = value;
    }

    public String getRegionRole() {
        return regionRole;
    }

    public void setRegionRole(String value) {
        regionRole = value;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String value) {
        regionCode = value;
    }

    public String getCoverageMode() {
        return coverageMode;
    }

    public void setCoverageMode(String value) {
        coverageMode = value;
    }

    public String getExamPurpose() {
        return examPurpose;
    }

    public void setExamPurpose(String value) {
        examPurpose = value;
    }

    public String getOrganizerLevel() {
        return organizerLevel;
    }

    public void setOrganizerLevel(String value) {
        organizerLevel = value;
    }

    public String getPaperNature() {
        return paperNature;
    }

    public void setPaperNature(String value) {
        paperNature = value;
    }

    public String getAudienceRole() {
        return audienceRole;
    }

    public void setAudienceRole(String value) {
        audienceRole = value;
    }

    public String getProvenanceStatus() {
        return provenanceStatus;
    }

    public void setProvenanceStatus(String value) {
        provenanceStatus = value;
    }

    public String getHistoryMode() {
        return historyMode;
    }

    public void setHistoryMode(String value) {
        historyMode = value;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getOutlineId() {
        return outlineId;
    }

    public void setOutlineId(Long value) {
        outlineId = value;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getOutlineNodeId() {
        return outlineNodeId;
    }

    public void setOutlineNodeId(Long value) {
        outlineNodeId = value;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getKpId() {
        return kpId;
    }

    public void setKpId(Long value) {
        kpId = value;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getRootProductId() {
        return rootProductId;
    }

    public void setRootProductId(Long value) {
        rootProductId = value;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long value) {
        materialId = value;
    }

    public Integer getYearFrom() {
        return yearFrom;
    }

    public void setYearFrom(Integer value) {
        yearFrom = value;
    }

    public Integer getYearTo() {
        return yearTo;
    }

    public void setYearTo(Integer value) {
        yearTo = value;
    }

    public Integer getExamYear() {
        return examYear;
    }

    public void setExamYear(Integer value) {
        examYear = value;
    }

    public Integer getAcademicYearStart() {
        return academicYearStart;
    }

    public void setAcademicYearStart(Integer value) {
        academicYearStart = value;
    }

    public Boolean getRegionDescendants() {
        return regionDescendants;
    }

    public void setRegionDescendants(Boolean value) {
        regionDescendants = value;
    }

    public Boolean getIncludeNationwide() {
        return includeNationwide;
    }

    public void setIncludeNationwide(Boolean value) {
        includeNationwide = value;
    }

    @tools.jackson.databind.annotation.JsonSerialize(
            contentUsing = tools.jackson.databind.ser.std.ToStringSerializer.class)
    public List<Long> getChapterTargetIds() {
        return chapterTargetIds;
    }

    public void setChapterTargetIds(List<Long> value) {
        chapterTargetIds = value;
    }

    @tools.jackson.databind.annotation.JsonSerialize(
            contentUsing = tools.jackson.databind.ser.std.ToStringSerializer.class)
    public List<Long> getChapterAncestorIds() {
        return chapterAncestorIds;
    }

    public void setChapterAncestorIds(List<Long> value) {
        chapterAncestorIds = value;
    }

    public List<String> getRegionCodes() {
        return regionCodes;
    }

    public void setRegionCodes(List<String> value) {
        regionCodes = value;
    }

    public void rejectUnknown(java.util.Set<String> names) {
        java.util.Set<String> allowed =
                java.util.Set.of(
                        "title",
                        "subject",
                        "resourceType",
                        "catalogStatus",
                        "sourceKind",
                        "extractionStatus",
                        "authenticityStatus",
                        "chapterMatch",
                        "textbookVersion",
                        "grade",
                        "term",
                        "yearField",
                        "regionRole",
                        "regionCode",
                        "coverageMode",
                        "examPurpose",
                        "organizerLevel",
                        "paperNature",
                        "audienceRole",
                        "provenanceStatus",
                        "historyMode",
                        "outlineId",
                        "outlineNodeId",
                        "kpId",
                        "rootProductId",
                        "materialId",
                        "yearFrom",
                        "yearTo",
                        "examYear",
                        "academicYearStart",
                        "regionDescendants",
                        "includeNationwide",
                        "pageNum",
                        "pageSize",
                        "orderByColumn",
                        "isAsc",
                        "reasonable");
        for (String name : names)
            if (!allowed.contains(name))
                throw new com.ruoyi.common.exception.ServiceException("不支持的资料筛选参数: " + name, 400);
    }
}
