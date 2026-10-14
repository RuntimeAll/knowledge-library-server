package com.ruoyi.knowledge.importing.domain;

public class KbImportBatch {
    private Long batchId;
    private String idempotencyKey;
    private String requestFingerprint;
    private String status;
    private Integer totalCount;
    private Integer successCount;
    private Integer failureCount;
    private Long sourceId;
    private Long inputAssetId;
    private String testRunId;
    private String createBy;
    private java.util.Date createTime;
    private java.util.List<KbImportItem> items;
    private Boolean inputReady;
    private String claimToken;
    private java.util.Date leaseUntil;
    private String lastError;
    private java.util.List<KbImportResource> resources;

    @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String requestFingerprint) { this.requestFingerprint = requestFingerprint; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    public Integer getSuccessCount() { return successCount; }
    public void setSuccessCount(Integer successCount) { this.successCount = successCount; }
    public Integer getFailureCount() { return failureCount; }
    public void setFailureCount(Integer failureCount) { this.failureCount = failureCount; }
    @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getInputAssetId() { return inputAssetId; }
    public void setInputAssetId(Long inputAssetId) { this.inputAssetId = inputAssetId; }
    public String getTestRunId() { return testRunId; }
    public void setTestRunId(String testRunId) { this.testRunId = testRunId; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public java.util.Date getCreateTime() { return createTime; }
    public void setCreateTime(java.util.Date createTime) { this.createTime = createTime; }
    public java.util.List<KbImportItem> getItems() { return items; }
    public void setItems(java.util.List<KbImportItem> items) { this.items = items; }
    public Boolean getInputReady() { return inputReady; }
    public void setInputReady(Boolean inputReady) { this.inputReady = inputReady; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getClaimToken() { return claimToken; }
    public void setClaimToken(String claimToken) { this.claimToken = claimToken; }
    public java.util.Date getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(java.util.Date leaseUntil) { this.leaseUntil = leaseUntil; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public java.util.List<KbImportResource> getResources() { return resources; }
    public void setResources(java.util.List<KbImportResource> resources) { this.resources = resources; }
}
