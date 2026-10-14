package com.ruoyi.knowledge.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/** 训练考点对照事实或来源覆盖说明。 */
public class KbComparisonRow extends BaseEntity {
    private Long sourceId;
    private String sourceTitle;
    private String extractionStatus;
    private String comparisonStatus;
    private String notice;
    private Long questionId;
    private String questionTitle;
    private Long kpId;
    private String kpName;
    private String questionNo;
    private Integer pageFrom;
    private Integer pageTo;

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public String getSourceTitle() {
        return sourceTitle;
    }

    public void setSourceTitle(String sourceTitle) {
        this.sourceTitle = sourceTitle;
    }

    public String getExtractionStatus() {
        return extractionStatus;
    }

    public void setExtractionStatus(String extractionStatus) {
        this.extractionStatus = extractionStatus;
    }

    public String getComparisonStatus() {
        return comparisonStatus;
    }

    public void setComparisonStatus(String comparisonStatus) {
        this.comparisonStatus = comparisonStatus;
    }

    public String getNotice() {
        return notice;
    }

    public void setNotice(String notice) {
        this.notice = notice;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public String getQuestionTitle() {
        return questionTitle;
    }

    public void setQuestionTitle(String questionTitle) {
        this.questionTitle = questionTitle;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getKpId() {
        return kpId;
    }

    public void setKpId(Long kpId) {
        this.kpId = kpId;
    }

    public String getKpName() {
        return kpName;
    }

    public void setKpName(String kpName) {
        this.kpName = kpName;
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
}
