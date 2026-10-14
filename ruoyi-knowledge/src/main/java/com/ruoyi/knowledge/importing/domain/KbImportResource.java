package com.ruoyi.knowledge.importing.domain;

/** Immutable original upload mapping; resources are restored without fetching remote URLs. */
public class KbImportResource {
    private Long batchId;
    private String resourceKey;
    private Long assetId;
    private String contentType;
    private String sha256;
    private Long fileSize;

    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getResourceKey() { return resourceKey; }
    public void setResourceKey(String resourceKey) { this.resourceKey = resourceKey; }
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
}
