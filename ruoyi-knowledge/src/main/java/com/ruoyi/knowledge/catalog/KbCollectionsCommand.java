package com.ruoyi.knowledge.catalog;

import java.util.List;

public class KbCollectionsCommand extends CatalogFields {
    private Long rowVersion;
    private List<KbOccurrenceCollection> collections;

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Long value) {
        rowVersion = value;
        supplied("rowVersion");
    }

    public List<KbOccurrenceCollection> getCollections() {
        return collections;
    }

    public void setCollections(List<KbOccurrenceCollection> value) {
        collections = value;
        supplied("collections");
    }
}
