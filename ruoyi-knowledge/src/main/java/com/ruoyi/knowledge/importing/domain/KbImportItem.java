package com.ruoyi.knowledge.importing.domain;

public class KbImportItem {
    private Long itemId;
    private Long batchId;
    private Integer itemOrder;
    private String clientKey;
    private String intent;
    private String status;
    private Long questionId;
    private String message;

    @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Integer getItemOrder() { return itemOrder; }
    public void setItemOrder(Integer itemOrder) { this.itemOrder = itemOrder; }
    public String getClientKey() { return clientKey; }
    public void setClientKey(String clientKey) { this.clientKey = clientKey; }
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
