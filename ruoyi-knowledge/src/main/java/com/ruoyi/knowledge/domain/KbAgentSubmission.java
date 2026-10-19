package com.ruoyi.knowledge.domain;

/** Committed together with the source/product; an unfinished claim cannot survive rollback. */
public class KbAgentSubmission {
    private String requestHash;
    private Long resourceId;
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String value) { requestHash = value; }
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long value) { resourceId = value; }
}
