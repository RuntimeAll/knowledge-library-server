package com.ruoyi.knowledge.layout;

import com.alibaba.fastjson2.*;
import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.domain.*;

import java.util.*;

public class KbRenderItem {
    private Long jobId;
    private Integer seq;
    private String sectionKey;
    private String dayKey;
    private Long questionId;
    private Long questionRowVersion;
    private String contentHash;
    private String snapshotJson;
    private KbQuestion snapshot;
    private List<KbOccurrence> occurrences;
    private List<KbKp> knowledgePoints;
    private List<KbSource> sources;

    public List<KbKp> getKnowledgePoints() {
        return knowledgePoints;
    }

    public void setKnowledgePoints(List<KbKp> value) {
        knowledgePoints = value;
    }

    public List<KbSource> getSources() {
        return sources;
    }

    public void setSources(List<KbSource> value) {
        sources = value;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long value) {
        jobId = value;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer value) {
        seq = value;
    }

    public String getSectionKey() {
        return sectionKey;
    }

    public void setSectionKey(String value) {
        sectionKey = value;
    }

    public String getDayKey() {
        return dayKey;
    }

    public void setDayKey(String value) {
        dayKey = value;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long value) {
        questionId = value;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getQuestionRowVersion() {
        return questionRowVersion;
    }

    public void setQuestionRowVersion(Long value) {
        questionRowVersion = value;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String value) {
        contentHash = value;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    @com.alibaba.fastjson2.annotation.JSONField(serialize = false)
    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String value) {
        snapshotJson = value;
    }

    public KbQuestion getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(KbQuestion value) {
        snapshot = value;
    }

    public List<KbOccurrence> getOccurrences() {
        return occurrences;
    }

    public void setOccurrences(List<KbOccurrence> value) {
        occurrences = value;
    }
}
