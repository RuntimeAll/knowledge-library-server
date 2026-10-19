package com.ruoyi.knowledge.layout;

import com.alibaba.fastjson2.*;
import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.domain.*;

import java.util.*;

public class KbLayoutAsset {
    private Long versionId;
    private Long jobId;
    private String resourceKey;
    private Long assetId;
    private String assetRole;
    private String sha256;
    private String mimeType;
    private String path;

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getVersionId() {
        return versionId;
    }

    public void setVersionId(Long value) {
        versionId = value;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long value) {
        jobId = value;
    }

    public String getResourceKey() {
        return resourceKey;
    }

    public void setResourceKey(String value) {
        resourceKey = value;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long value) {
        assetId = value;
    }

    public String getAssetRole() {
        return assetRole;
    }

    public void setAssetRole(String value) {
        assetRole = value;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String value) {
        sha256 = value;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String value) {
        mimeType = value;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String value) {
        path = value;
    }
}
