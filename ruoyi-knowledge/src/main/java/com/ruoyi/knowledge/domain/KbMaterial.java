package com.ruoyi.knowledge.domain;

import com.ruoyi.common.core.domain.BaseEntity;

import java.util.List;

/** 专项选题顺序、参考范围及当前有效性。 */
public class KbMaterial extends BaseEntity {
    private Long id;
    private Long rowVersion;
    private String title;
    private String description;
    private Long kpId;
    private String selectionMode;
    private List<Long> questionIds;
    private List<KbMaterialReference> referenceSources;
    private List<KbQuestion> questions;
    private Boolean selectionValid;
    private Integer questionCount;
    private List<String> unavailableQuestionIds = List.of();
    public List<String> getUnavailableQuestionIds() { return unavailableQuestionIds; }
    public void setUnavailableQuestionIds(List<String> value) { unavailableQuestionIds = value; }

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

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getKpId() {
        return kpId;
    }

    public void setKpId(Long kpId) {
        this.kpId = kpId;
    }

    public String getSelectionMode() {
        return selectionMode;
    }

    public void setSelectionMode(String selectionMode) {
        this.selectionMode = selectionMode;
    }

    @tools.jackson.databind.annotation.JsonSerialize(
            contentUsing = tools.jackson.databind.ser.std.ToStringSerializer.class)
    public List<Long> getQuestionIds() {
        return questionIds;
    }

    public void setQuestionIds(List<Long> questionIds) {
        this.questionIds = questionIds;
    }

    public List<KbMaterialReference> getReferenceSources() {
        return referenceSources;
    }

    public void setReferenceSources(List<KbMaterialReference> referenceSources) {
        this.referenceSources = referenceSources;
    }

    public List<KbQuestion> getQuestions() {
        return questions;
    }

    public void setQuestions(List<KbQuestion> questions) {
        this.questions = questions;
    }

    public Boolean getSelectionValid() {
        return selectionValid;
    }

    public void setSelectionValid(Boolean selectionValid) {
        this.selectionValid = selectionValid;
    }

    public Integer getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(Integer questionCount) {
        this.questionCount = questionCount;
    }
}
