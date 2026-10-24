package com.ruoyi.knowledge.catalog;

import java.util.List;

/** Internal, typed row grouping for page-scoped catalog relationship reads. */
public class KbCatalogBatch extends KbCatalogDocument {
    private Long ownerId;
    private List<KbOccurrenceCollection> collections;
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long value) { ownerId = value; }
    public List<KbOccurrenceCollection> getCollections() { return collections; }
    public void setCollections(List<KbOccurrenceCollection> value) { collections = value; }
}
