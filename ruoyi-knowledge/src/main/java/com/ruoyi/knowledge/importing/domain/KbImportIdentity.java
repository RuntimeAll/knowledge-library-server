package com.ruoyi.knowledge.importing.domain;

public class KbImportIdentity {
    private String clientKey;
    private String createBy;
    private Long questionId;
    private String requestFingerprint;

    public String getClientKey() { return clientKey; }
    public void setClientKey(String clientKey) { this.clientKey = clientKey; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String requestFingerprint) { this.requestFingerprint = requestFingerprint; }
}
