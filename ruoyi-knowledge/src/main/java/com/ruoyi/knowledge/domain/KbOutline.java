package com.ruoyi.knowledge.domain;

import com.ruoyi.common.core.domain.BaseEntity;

import java.util.List;

/** 教学大纲及其章节明细。 */
public class KbOutline extends BaseEntity {
    private Long id;
    private Long rowVersion;
    private String title;
    private String description;
    private String catalogKind;
    private String subject;
    private String textbookVersion;
    private String grade;
    private String term;
    private String edition;
    private String catalogSource;
    private String status;
    private List<KbOutlineNode> nodes;

    public String getCatalogKind() { return catalogKind; }
    public void setCatalogKind(String value) { catalogKind = value; }
    public String getSubject() { return subject; }
    public void setSubject(String value) { subject = value; }
    public String getTextbookVersion() { return textbookVersion; }
    public void setTextbookVersion(String value) { textbookVersion = value; }
    public String getGrade() { return grade; }
    public void setGrade(String value) { grade = value; }
    public String getTerm() { return term; }
    public void setTerm(String value) { term = value; }
    public String getEdition() { return edition; }
    public void setEdition(String value) { edition = value; }
    public String getCatalogSource() { return catalogSource; }
    public void setCatalogSource(String value) { catalogSource = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }

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
