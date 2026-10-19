package com.ruoyi.knowledge.dto;

/** 教材归属来自大纲；nodeId 为空表示已挂教材、尚待核实章节。 */
public class KbKpOutlineBinding {
    private Long outlineId;
    private String outlineTitle;
    private String subject;
    private String textbookVersion;
    private String grade;
    private String term;
    private String edition;
    private Long nodeId;
    private String nodeTitle;

    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getOutlineId() { return outlineId; }
    public void setOutlineId(Long outlineId) { this.outlineId = outlineId; }
    public String getOutlineTitle() { return outlineTitle; }
    public void setOutlineTitle(String value) { this.outlineTitle = value; }
    public String getSubject() { return subject; }
    public void setSubject(String value) { this.subject = value; }
    public String getTextbookVersion() { return textbookVersion; }
    public void setTextbookVersion(String value) { this.textbookVersion = value; }
    public String getGrade() { return grade; }
    public void setGrade(String value) { this.grade = value; }
    public String getTerm() { return term; }
    public void setTerm(String value) { this.term = value; }
    public String getEdition() { return edition; }
    public void setEdition(String value) { this.edition = value; }
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getNodeId() { return nodeId; }
    public void setNodeId(Long value) { this.nodeId = value; }
    public String getNodeTitle() { return nodeTitle; }
    public void setNodeTitle(String value) { this.nodeTitle = value; }
}
