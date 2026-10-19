package com.ruoyi.knowledge.domain;

import com.ruoyi.common.core.domain.BaseEntity;

import java.util.List;
import com.ruoyi.knowledge.dto.KbKpOutlineBinding;

/** 可复用考点及嵌套、组合关系。 */
public class KbKp extends BaseEntity {
    private Long id;
    private Long rowVersion;
    private String name;
    private String description;
    private String subject;
    private Long parentId;
    private Long rootId;
    private String rootName;
    private Integer depth;
    private List<Long> outlineIds;
    private List<KbKpOutlineBinding> outlineBindings;
    private Long outlineId;
    private Long outlineNodeId;
    private List<Long> nestedIds;
    private List<Long> componentIds;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getRootId() { return rootId; }
    public void setRootId(Long rootId) { this.rootId = rootId; }
    public String getRootName() { return rootName; }
    public void setRootName(String rootName) { this.rootName = rootName; }
    public Integer getDepth() { return depth; }
    public void setDepth(Integer depth) { this.depth = depth; }
    @tools.jackson.databind.annotation.JsonSerialize(contentUsing = tools.jackson.databind.ser.std.ToStringSerializer.class)
    public List<Long> getOutlineIds() { return outlineIds; }
    public void setOutlineIds(List<Long> outlineIds) { this.outlineIds = outlineIds; }
    public List<KbKpOutlineBinding> getOutlineBindings() { return outlineBindings; }
    public void setOutlineBindings(List<KbKpOutlineBinding> outlineBindings) { this.outlineBindings = outlineBindings; }
    public Long getOutlineId() { return outlineId; }
    public void setOutlineId(Long outlineId) { this.outlineId = outlineId; }
    public Long getOutlineNodeId() { return outlineNodeId; }
    public void setOutlineNodeId(Long outlineNodeId) { this.outlineNodeId = outlineNodeId; }

    @tools.jackson.databind.annotation.JsonSerialize(
            contentUsing = tools.jackson.databind.ser.std.ToStringSerializer.class)
    public List<Long> getNestedIds() {
        return nestedIds;
    }

    public void setNestedIds(List<Long> nestedIds) {
        this.nestedIds = nestedIds;
    }

    @tools.jackson.databind.annotation.JsonSerialize(
            contentUsing = tools.jackson.databind.ser.std.ToStringSerializer.class)
    public List<Long> getComponentIds() {
        return componentIds;
    }

    public void setComponentIds(List<Long> componentIds) {
        this.componentIds = componentIds;
    }
}
