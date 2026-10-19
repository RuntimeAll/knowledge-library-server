package com.ruoyi.knowledge.layout;

import com.alibaba.fastjson2.*;
import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.domain.*;

import java.util.*;

public class KbExperienceVersion extends com.ruoyi.common.core.domain.BaseEntity {
    private Long id;
    private Long rowVersion;
    private Long experienceId;
    private String name;
    private Integer versionNo;
    private String status;
    private String schemaVersion;
    private String fieldSchemaJson;
    private String defaultValuesJson;
    private String definitionJson;
    private String definitionHash;
    private String changeNote;
    private String publishBy;
    private Date publishTime;
    private List<KbTemplateField> fieldSchema;
    private JSONObject defaultValues;
    private KbTemplateDefinition definition;
    private List<KbLayoutAsset> assets;

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getId() {
        return id;
    }

    public void setId(Long value) {
        id = value;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Long value) {
        rowVersion = value;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getExperienceId() {
        return experienceId;
    }

    public void setExperienceId(Long value) {
        experienceId = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String value) {
        name = value;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer value) {
        versionNo = value;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String value) {
        status = value;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String value) {
        schemaVersion = value;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    @com.alibaba.fastjson2.annotation.JSONField(serialize = false)
    public String getFieldSchemaJson() {
        return fieldSchemaJson;
    }

    public void setFieldSchemaJson(String value) {
        fieldSchemaJson = value;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    @com.alibaba.fastjson2.annotation.JSONField(serialize = false)
    public String getDefaultValuesJson() {
        return defaultValuesJson;
    }

    public void setDefaultValuesJson(String value) {
        defaultValuesJson = value;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    @com.alibaba.fastjson2.annotation.JSONField(serialize = false)
    public String getDefinitionJson() {
        return definitionJson;
    }

    public void setDefinitionJson(String value) {
        definitionJson = value;
    }

    public String getDefinitionHash() {
        return definitionHash;
    }

    public void setDefinitionHash(String value) {
        definitionHash = value;
    }

    public String getChangeNote() {
        return changeNote;
    }

    public void setChangeNote(String value) {
        changeNote = value;
    }

    public String getPublishBy() {
        return publishBy;
    }

    public void setPublishBy(String value) {
        publishBy = value;
    }

    public Date getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(Date value) {
        publishTime = value;
    }

    public List<KbTemplateField> getFieldSchema() {
        return fieldSchema;
    }

    public void setFieldSchema(List<KbTemplateField> value) {
        fieldSchema = value;
    }

    public JSONObject getDefaultValues() {
        return defaultValues;
    }

    public void setDefaultValues(JSONObject value) {
        defaultValues = value;
    }

    public KbTemplateDefinition getDefinition() {
        return definition;
    }

    public void setDefinition(KbTemplateDefinition value) {
        definition = value;
    }

    public List<KbLayoutAsset> getAssets() {
        return assets;
    }

    public void setAssets(List<KbLayoutAsset> value) {
        assets = value;
    }
}
