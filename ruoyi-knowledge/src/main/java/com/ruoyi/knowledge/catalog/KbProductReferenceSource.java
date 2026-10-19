package com.ruoyi.knowledge.catalog;


public class KbProductReferenceSource extends CatalogFields {
    private Long sourceId;
    private String note;
    private String sourceTitle;

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long value) {
        sourceId = value;
        supplied("sourceId");
    }

    public String getNote() {
        return note;
    }

    public void setNote(String value) {
        note = value;
        supplied("note");
    }

    public String getSourceTitle() {
        return sourceTitle;
    }

    public void setSourceTitle(String value) {
        sourceTitle = value;
        supplied("sourceTitle");
    }
}
