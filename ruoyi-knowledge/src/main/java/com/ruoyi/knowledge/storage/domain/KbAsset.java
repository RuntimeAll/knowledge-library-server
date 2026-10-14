package com.ruoyi.knowledge.storage.domain;

import com.ruoyi.common.core.domain.BaseEntity;

public class KbAsset extends BaseEntity {
    private String externalKey;
    public String getExternalKey() { return externalKey; }
    public void setExternalKey(String externalKey) { this.externalKey = externalKey; }
    private Long assetId;
    private String purpose;
    private String storageProvider;
    private String bucketName;
    private String objectKey;
    private String ossVersionId;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private String sha256;
    private String status;
    private String failureMessage;
    private String testRunId;

    @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getStorageProvider() { return storageProvider; }
    public void setStorageProvider(String storageProvider) { this.storageProvider = storageProvider; }
    public String getBucketName() { return bucketName; }
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getOssVersionId() { return ossVersionId; }
    public void setOssVersionId(String ossVersionId) { this.ossVersionId = ossVersionId; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }
    public String getTestRunId() { return testRunId; }
    public void setTestRunId(String testRunId) { this.testRunId = testRunId; }
}
