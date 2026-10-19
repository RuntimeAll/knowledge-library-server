package com.ruoyi.knowledge.domain;

import com.ruoyi.common.core.domain.BaseEntity;

import java.util.List;

/** 来源资料身份、考试属性及原件关联。 */
public class KbSource extends com.ruoyi.knowledge.catalog.KbCatalogDocument {
    private Long id;
    private Long rowVersion;
    private String title;
    private String sourceKind;
    private String extractionStatus;
    private String originUrl;
    private String originNote;
    private String subject;
    private String grade;
    private String examType;
    private String schoolName;
    private String scopeNote;
    private String authenticityStatus;
    private String verificationNote;
    private Integer examYear;
    private List<Long> assetIds;
    private List<KbSourceAttachment> attachments;

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Long rowVersion) {
        this.rowVersion = rowVersion;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSourceKind() {
        return sourceKind;
    }

    public void setSourceKind(String sourceKind) {
        this.sourceKind = sourceKind;
    }

    public String getExtractionStatus() {
        return extractionStatus;
    }

    public void setExtractionStatus(String extractionStatus) {
        this.extractionStatus = extractionStatus;
    }

    public String getOriginUrl() {
        return originUrl;
    }

    public void setOriginUrl(String originUrl) {
        this.originUrl = originUrl;
    }

    public String getOriginNote() {
        return originNote;
    }

    public void setOriginNote(String originNote) {
        this.originNote = originNote;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public String getExamType() {
        return examType;
    }

    public void setExamType(String examType) {
        this.examType = examType;
    }

    public String getSchoolName() {
        return schoolName;
    }

    public void setSchoolName(String schoolName) {
        this.schoolName = schoolName;
    }

    public String getScopeNote() {
        return scopeNote;
    }

    public void setScopeNote(String scopeNote) {
        this.scopeNote = scopeNote;
    }

    public String getAuthenticityStatus() {
        return authenticityStatus;
    }

    public void setAuthenticityStatus(String authenticityStatus) {
        this.authenticityStatus = authenticityStatus;
    }

    public String getVerificationNote() {
        return verificationNote;
    }

    public void setVerificationNote(String verificationNote) {
        this.verificationNote = verificationNote;
    }

    public Integer getExamYear() {
        return examYear;
    }

    public void setExamYear(Integer examYear) {
        this.examYear = examYear;
    }

    @tools.jackson.databind.annotation.JsonSerialize(
            contentUsing = tools.jackson.databind.ser.std.ToStringSerializer.class)
    public List<Long> getAssetIds() {
        return assetIds;
    }

    public void setAssetIds(List<Long> assetIds) {
        this.assetIds = assetIds;
    }

    public List<KbSourceAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<KbSourceAttachment> attachments) {
        this.attachments = attachments;
    }
}
