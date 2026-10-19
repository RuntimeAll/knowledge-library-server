package com.ruoyi.knowledge.catalog;


public class KbProductQuestionRef extends CatalogFields {
    private Integer seq;
    private Long questionId;
    private Long questionRowVersion;
    private String stemHash;
    private String itemLocator;
    private String questionTitle;

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer value) {
        seq = value;
        supplied("seq");
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long value) {
        questionId = value;
        supplied("questionId");
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getQuestionRowVersion() {
        return questionRowVersion;
    }

    public void setQuestionRowVersion(Long value) {
        questionRowVersion = value;
        supplied("questionRowVersion");
    }

    public String getStemHash() {
        return stemHash;
    }

    public void setStemHash(String value) {
        stemHash = value;
        supplied("stemHash");
    }

    public String getItemLocator() {
        return itemLocator;
    }

    public void setItemLocator(String value) {
        itemLocator = value;
        supplied("itemLocator");
    }

    public String getQuestionTitle() {
        return questionTitle;
    }

    public void setQuestionTitle(String value) {
        questionTitle = value;
        supplied("questionTitle");
    }
}
