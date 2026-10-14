package com.ruoyi.knowledge.domain;

import com.ruoyi.common.core.domain.BaseEntity;

import java.util.List;

/** 可复用考点及嵌套、组合关系。 */
public class KbKp extends BaseEntity {
    private Long id;
    private Long rowVersion;
    private String name;
    private String description;
    private String gradeHint;
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

    public String getGradeHint() {
        return gradeHint;
    }

    public void setGradeHint(String gradeHint) {
        this.gradeHint = gradeHint;
    }

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
