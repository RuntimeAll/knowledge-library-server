package com.ruoyi.knowledge.domain;

import com.ruoyi.common.core.domain.BaseEntity;

import java.math.BigDecimal;
import java.util.Date;

/** 题目在来源中的实际出现事实和核实证据。 */
public class KbOccurrence extends BaseEntity {
    private Long id;
    private Long rowVersion;
    private Long questionId;
    private Long sourceId;
    private String questionNo;
    private Integer pageFrom;
    private Integer pageTo;
    private BigDecimal score;
    private String matchStatus;
    private String matchedStemHash;
    private Long evidenceAssetId;
    private String verificationNote;
    private Date verifiedAt;
    private Boolean isRealExam;
    private Boolean stemMatches;
    private String sourceTitle;
    private String questionTitle;

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

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public String getQuestionNo() {
        return questionNo;
    }

    public void setQuestionNo(String questionNo) {
        this.questionNo = questionNo;
    }

    public Integer getPageFrom() {
        return pageFrom;
    }

    public void setPageFrom(Integer pageFrom) {
        this.pageFrom = pageFrom;
    }

    public Integer getPageTo() {
        return pageTo;
    }

    public void setPageTo(Integer pageTo) {
        this.pageTo = pageTo;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public String getMatchStatus() {
        return matchStatus;
    }

    public void setMatchStatus(String matchStatus) {
        this.matchStatus = matchStatus;
    }

    public String getMatchedStemHash() {
        return matchedStemHash;
    }

    public void setMatchedStemHash(String matchedStemHash) {
        this.matchedStemHash = matchedStemHash;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getEvidenceAssetId() {
        return evidenceAssetId;
    }

    public void setEvidenceAssetId(Long evidenceAssetId) {
        this.evidenceAssetId = evidenceAssetId;
    }

    public String getVerificationNote() {
        return verificationNote;
    }

    public void setVerificationNote(String verificationNote) {
        this.verificationNote = verificationNote;
    }

    public Date getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Date verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public Boolean getIsRealExam() {
        return isRealExam;
    }

    public void setIsRealExam(Boolean isRealExam) {
        this.isRealExam = isRealExam;
    }

    public Boolean getStemMatches() {
        return stemMatches;
    }

    public void setStemMatches(Boolean stemMatches) {
        this.stemMatches = stemMatches;
    }

    public String getSourceTitle() {
        return sourceTitle;
    }

    public void setSourceTitle(String sourceTitle) {
        this.sourceTitle = sourceTitle;
    }

    public String getQuestionTitle() {
        return questionTitle;
    }

    public void setQuestionTitle(String questionTitle) {
        this.questionTitle = questionTitle;
    }
}
