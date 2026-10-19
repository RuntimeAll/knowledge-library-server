package com.ruoyi.knowledge.catalog;


public class KbOccurrenceQuery {
    private Long questionId;
    private Long sourceId;
    private String collectionKind;
    private String matchStatus;

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long value) {
        questionId = value;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long value) {
        sourceId = value;
    }

    public String getCollectionKind() {
        return collectionKind;
    }

    public void setCollectionKind(String value) {
        collectionKind = value;
    }

    public String getMatchStatus() {
        return matchStatus;
    }

    public void setMatchStatus(String value) {
        matchStatus = value;
    }
}
