package com.ruoyi.knowledge.dto;

/** 章节考点关联的内部批量查询结果。 */
public class KbOutlineNodeKp {
    private Long nodeId;
    private Long kpId;

    public Long getNodeId() { return nodeId; }
    public void setNodeId(Long nodeId) { this.nodeId = nodeId; }
    public Long getKpId() { return kpId; }
    public void setKpId(Long kpId) { this.kpId = kpId; }
}
