package com.ruoyi.knowledge.domain;

import com.ruoyi.common.core.domain.BaseEntity;

import java.util.List;

/** 教学大纲及其章节明细。 */
public class KbOutline extends BaseEntity {
    private Long id;
    private Long rowVersion;
    private String title;
    private String description;
    private List<KbOutlineNode> nodes;

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Long rowVersion) {
        this.rowVersion = rowVersion;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<KbOutlineNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<KbOutlineNode> nodes) {
        this.nodes = nodes;
    }
}
