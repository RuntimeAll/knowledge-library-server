package com.ruoyi.knowledge.dto;

/** Internal row for assembling a page of textbook bindings without per-point queries. */
public class KbKpOutlineRow {
    private Long kpId;
    private Long outlineId;
    private Long nodeId;
    private KbKpOutlineBinding binding;

    public Long getKpId() { return kpId; }
    public void setKpId(Long value) { kpId = value; }
    public Long getOutlineId() { return outlineId; }
    public void setOutlineId(Long value) { outlineId = value; }
    public Long getNodeId() { return nodeId; }
    public void setNodeId(Long value) { nodeId = value; }
    public KbKpOutlineBinding getBinding() { return binding; }
    public void setBinding(KbKpOutlineBinding value) { binding = value; }
}
