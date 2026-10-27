package com.ruoyi.knowledge.exporting;

/** Bounded export lineage projection, preserving the selected descendant identity. */
public class KbExportAncestor extends com.ruoyi.knowledge.domain.KbOccurrence {
    private Long descendantQuestionId;
    @com.fasterxml.jackson.annotation.JsonIgnore
    public Long getDescendantQuestionId() { return descendantQuestionId; }
    public void setDescendantQuestionId(Long value) { descendantQuestionId = value; }
}
