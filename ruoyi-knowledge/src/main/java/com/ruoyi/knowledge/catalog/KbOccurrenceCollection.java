package com.ruoyi.knowledge.catalog;


public class KbOccurrenceCollection extends CatalogFields {
    private String collectionKind;
    private String note;
    private Long evidenceAssetId;
    private Long rowVersion;

    public String getCollectionKind() {
        return collectionKind;
    }

    public void setCollectionKind(String value) {
        collectionKind = value;
        supplied("collectionKind");
    }

    public String getNote() {
        return note;
    }

    public void setNote(String value) {
        note = value;
        supplied("note");
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getEvidenceAssetId() {
        return evidenceAssetId;
    }

    public void setEvidenceAssetId(Long value) {
        evidenceAssetId = value;
        supplied("evidenceAssetId");
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Long value) {
        rowVersion = value;
        supplied("rowVersion");
    }
}
