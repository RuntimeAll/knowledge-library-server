package com.ruoyi.knowledge.catalog;


public class KbCatalogCommand extends KbCatalogDocument {
    private Long rowVersion;

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Long value) {
        rowVersion = value;
    }
}
