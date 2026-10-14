package com.ruoyi.knowledge.domain;

import com.ruoyi.common.core.domain.BaseEntity;

import java.util.List;

/** 公共题目与当前正文版本，业务主键独立于导入批次。 */
public class KbQuestion extends BaseEntity {
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
    private String questionType;
    private String difficulty;
    private String creationKind;
    private String sourceNote;
    private Boolean hasImage;
    private Boolean isRealExam;
    private List<Long> kpIds;
    private List<Long> assetIds;
    private List<String> readinessReasons;

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
}
