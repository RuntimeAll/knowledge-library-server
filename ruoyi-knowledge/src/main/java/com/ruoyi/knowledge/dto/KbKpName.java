package com.ruoyi.knowledge.dto;

/** 关联名称展示使用的最小考点投影。 */
public class KbKpName {
    private Long id;
    private String name;

    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
