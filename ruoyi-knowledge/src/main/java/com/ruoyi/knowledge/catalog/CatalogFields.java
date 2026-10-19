package com.ruoyi.knowledge.catalog;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ruoyi.common.exception.ServiceException;

import java.util.HashSet;
import java.util.Set;

/** Presence is transport metadata, allowing omitted values and explicit null to differ. */
public abstract class CatalogFields {
    private final Set<String> supplied = new HashSet<>();

    protected void supplied(String field) {
        supplied.add(field);
    }

    @JsonIgnore
    public boolean has(String field) {
        return supplied.contains(field);
    }

    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new ServiceException("未定义字段: " + name, 400);
    }
}
