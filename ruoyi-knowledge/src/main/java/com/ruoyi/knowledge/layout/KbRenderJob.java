package com.ruoyi.knowledge.layout;

import com.alibaba.fastjson2.*;
import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.domain.*;

import java.util.*;

public class KbRenderJob extends com.ruoyi.common.core.domain.BaseEntity {
    private Long id;
    private Long rowVersion;
    private Long templateVersionId;
    private Long actorUserId;
    private String idempotencyKey;
    private String requestHash;
    private Long materialId;
    private Long materialRowVersion;
    private String configSnapshotJson;
    private String templateSnapshotJson;
    private String rendererFingerprint;
    private String status;
    private String leaseToken;
    private Date leaseUntil;
    private Integer retryCount;
    private String errorCode;
    private String errorMessage;
    private KbRenderRequest configSnapshot;
    private KbExperienceVersion templateSnapshot;
    private List<KbRenderItem> items;
    private List<KbLayoutAsset> assets;
    private List<KbRenderOutput> outputs;

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
    public Long getTemplateVersionId() {
        return templateVersionId;
    }

    public void setTemplateVersionId(Long value) {
        templateVersionId = value;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(Long value) {
        actorUserId = value;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String value) {
        idempotencyKey = value;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String value) {
        requestHash = value;
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

    @com.fasterxml.jackson.annotation.JsonIgnore
    @com.alibaba.fastjson2.annotation.JSONField(serialize = false)
    public String getConfigSnapshotJson() {
        return configSnapshotJson;
    }

    public void setConfigSnapshotJson(String value) {
        configSnapshotJson = value;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    @com.alibaba.fastjson2.annotation.JSONField(serialize = false)
    public String getTemplateSnapshotJson() {
        return templateSnapshotJson;
    }

    public void setTemplateSnapshotJson(String value) {
        templateSnapshotJson = value;
    }

    public String getRendererFingerprint() {
        return rendererFingerprint;
    }

    public void setRendererFingerprint(String value) {
        rendererFingerprint = value;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String value) {
        status = value;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    @com.alibaba.fastjson2.annotation.JSONField(serialize = false)
    public String getLeaseToken() {
        return leaseToken;
    }

    public void setLeaseToken(String value) {
        leaseToken = value;
    }

    public Date getLeaseUntil() {
        return leaseUntil;
    }

    public void setLeaseUntil(Date value) {
        leaseUntil = value;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer value) {
        retryCount = value;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String value) {
        errorCode = value;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String value) {
        errorMessage = value;
    }

    public KbRenderRequest getConfigSnapshot() {
        return configSnapshot;
    }

    public void setConfigSnapshot(KbRenderRequest value) {
        configSnapshot = value;
    }

    public KbExperienceVersion getTemplateSnapshot() {
        return templateSnapshot;
    }

    public void setTemplateSnapshot(KbExperienceVersion value) {
        templateSnapshot = value;
    }

    public List<KbRenderItem> getItems() {
        return items;
    }

    public void setItems(List<KbRenderItem> value) {
        items = value;
    }

    public List<KbLayoutAsset> getAssets() {
        return assets;
    }

    public void setAssets(List<KbLayoutAsset> value) {
        assets = value;
    }

    public List<KbRenderOutput> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<KbRenderOutput> value) {
        outputs = value;
    }
}
