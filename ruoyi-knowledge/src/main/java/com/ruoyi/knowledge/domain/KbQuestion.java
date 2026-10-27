package com.ruoyi.knowledge.domain;

import com.ruoyi.common.core.domain.BaseEntity;

import java.util.List;

/** 公共题目与当前正文版本，业务主键独立于导入批次。 */
public class KbQuestion extends BaseEntity implements com.ruoyi.knowledge.review.KbQualityImpact {
    private java.util.List<String> affectedQuestionIds = java.util.List.of();
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public java.util.List<String> getAffectedQuestionIds() { return affectedQuestionIds; }
    public void setAffectedQuestionIds(java.util.List<String> value) { affectedQuestionIds = value; }

    private String contentHash;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public String getContentHash() { return contentHash; }
    public void setContentHash(String value) { contentHash = value; }
    private com.ruoyi.knowledge.review.ReviewModels.ParentFreeze reviewParentFreeze;
    private String expectedParentContentHash;
    @com.fasterxml.jackson.annotation.JsonIgnore
    public com.ruoyi.knowledge.review.ReviewModels.ParentFreeze getReviewParentFreeze() { return reviewParentFreeze; }
    public void setReviewParentFreeze(com.ruoyi.knowledge.review.ReviewModels.ParentFreeze value) { reviewParentFreeze = value; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getExpectedParentContentHash() { return expectedParentContentHash; }
    public void setExpectedParentContentHash(String value) { expectedParentContentHash = value; }
    private Long expectedParentRowVersion;
    private String expectedParentStemHash;
    private String granularityExceptionId;
    private String importClientKey;
    private String candidateArticleHash;
    private Long importSourceId;
    @com.fasterxml.jackson.annotation.JsonIgnore
    public Long getExpectedParentRowVersion() { return expectedParentRowVersion; }
    public void setExpectedParentRowVersion(Long value) { expectedParentRowVersion = value; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getExpectedParentStemHash() { return expectedParentStemHash; }
    public void setExpectedParentStemHash(String value) { expectedParentStemHash = value; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getGranularityExceptionId() { return granularityExceptionId; }
    public void setGranularityExceptionId(String value) { granularityExceptionId = value; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getImportClientKey() { return importClientKey; }
    public void setImportClientKey(String value) { importClientKey = value; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getCandidateArticleHash() { return candidateArticleHash; }
    public void setCandidateArticleHash(String value) { candidateArticleHash = value; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public Long getImportSourceId() { return importSourceId; }
    public void setImportSourceId(Long value) { importSourceId = value; }
    private Long id;
    private Long rowVersion;
    private String title;
    private String stemHtml;
    private String answerHtml;
    private String analysisHtml;
    private String stemText;
    private String stemHash;
    private String status;
    private String subject;
    private String contentKind;
    private String layoutMode;
    private String unitKind;
    private String paginationMode;
    private String layoutNote;
    private Integer itemCount;
    private String questionType;
    private String difficulty;
    private String creationKind;
    private String sourceNote;
    private Boolean hasImage;
    private Boolean isRealExam;
    private List<Long> kpIds;
    private List<Long> assetIds;
    private List<String> readinessReasons;

    public String getLayoutMode() { return layoutMode; }
    public void setLayoutMode(String layoutMode) { this.layoutMode = layoutMode; }
    public String getUnitKind() { return unitKind; }
    public void setUnitKind(String unitKind) { this.unitKind = unitKind; }
    public String getPaginationMode() { return paginationMode; }
    public void setPaginationMode(String paginationMode) { this.paginationMode = paginationMode; }
    public String getLayoutNote() { return layoutNote; }
    public void setLayoutNote(String layoutNote) { this.layoutNote = layoutNote; }
    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }

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

    public String getStemHtml() {
        return stemHtml;
    }

    public void setStemHtml(String stemHtml) {
        this.stemHtml = stemHtml;
    }

    public String getAnswerHtml() {
        return answerHtml;
    }

    public void setAnswerHtml(String answerHtml) {
        this.answerHtml = answerHtml;
    }

    public String getAnalysisHtml() {
        return analysisHtml;
    }

    public void setAnalysisHtml(String analysisHtml) {
        this.analysisHtml = analysisHtml;
    }

    public String getStemText() {
        return stemText;
    }

    public void setStemText(String stemText) {
        this.stemText = stemText;
    }

    public String getStemHash() {
        return stemHash;
    }

    public void setStemHash(String stemHash) {
        this.stemHash = stemHash;
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

    public String getSourceNote() {
        return sourceNote;
    }

    public void setSourceNote(String sourceNote) {
        this.sourceNote = sourceNote;
    }

    public Boolean getHasImage() {
        return hasImage;
    }

    public void setHasImage(Boolean hasImage) {
        this.hasImage = hasImage;
    }

    public Boolean getIsRealExam() {
        return isRealExam;
    }

    public void setIsRealExam(Boolean isRealExam) {
        this.isRealExam = isRealExam;
    }

    @tools.jackson.databind.annotation.JsonSerialize(
            contentUsing = tools.jackson.databind.ser.std.ToStringSerializer.class)
    public List<Long> getKpIds() {
        return kpIds;
    }

    public void setKpIds(List<Long> kpIds) {
        this.kpIds = kpIds;
    }

    @tools.jackson.databind.annotation.JsonSerialize(
            contentUsing = tools.jackson.databind.ser.std.ToStringSerializer.class)
    public List<Long> getAssetIds() {
        return assetIds;
    }

    public void setAssetIds(List<Long> assetIds) {
        this.assetIds = assetIds;
    }

    public List<String> getReadinessReasons() {
        return readinessReasons;
    }

    public void setReadinessReasons(List<String> readinessReasons) {
        this.readinessReasons = readinessReasons;
    }
    private java.util.List<KbOccurrence> matchedExamOccurrences;
    public java.util.List<KbOccurrence> getMatchedExamOccurrences(){return matchedExamOccurrences;}
    public void setMatchedExamOccurrences(java.util.List<KbOccurrence> value){matchedExamOccurrences=value;}
    private java.util.List<KbOccurrence> matchedWrongOccurrences;
    public java.util.List<KbOccurrence> getMatchedWrongOccurrences(){return matchedWrongOccurrences;}
    public void setMatchedWrongOccurrences(java.util.List<KbOccurrence> value){matchedWrongOccurrences=value;}
}
