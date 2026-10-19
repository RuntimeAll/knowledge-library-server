package com.ruoyi.knowledge.layout;

import com.alibaba.fastjson2.*;
import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.domain.*;

import java.util.*;

public class KbRenderRequest {
    private String idempotencyKey;
    private Long templateVersionId;
    private JSONObject parameters;
    private List<KbOutlineScope> outlineScopes;
    private List<KbRenderSection> sections;
    private List<String> outputRoles;
    private Long materialId;
    private Long materialRowVersion;
    private String testRunId;
    private String resourceType;

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String value) {
        resourceType = value;
    }

    private List<KbPreviousOutput> previousOutputs;

    public List<KbPreviousOutput> getPreviousOutputs() {
        return previousOutputs;
    }

    public void setPreviousOutputs(List<KbPreviousOutput> value) {
        previousOutputs = value;
    }

    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new com.ruoyi.common.exception.ServiceException("未定义生成字段：" + name, 400);
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String value) {
        idempotencyKey = value;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getTemplateVersionId() {
        return templateVersionId;
    }

    public void setTemplateVersionId(Long value) {
        templateVersionId = value;
    }

    public JSONObject getParameters() {
        return parameters;
    }

    public void setParameters(JSONObject value) {
        parameters = value;
    }

    public List<KbOutlineScope> getOutlineScopes() {
        return outlineScopes;
    }

    public void setOutlineScopes(List<KbOutlineScope> value) {
        outlineScopes = value;
    }

    public List<KbRenderSection> getSections() {
        return sections;
    }

    public void setSections(List<KbRenderSection> value) {
        sections = value;
    }

    public List<String> getOutputRoles() {
        return outputRoles;
    }

    public void setOutputRoles(List<String> value) {
        outputRoles = value;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long value) {
        materialId = value;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getMaterialRowVersion() {
        return materialRowVersion;
    }

    public void setMaterialRowVersion(Long value) {
        materialRowVersion = value;
    }

    public String getTestRunId() {
        return testRunId;
    }

    public void setTestRunId(String value) {
        testRunId = value;
    }
}
