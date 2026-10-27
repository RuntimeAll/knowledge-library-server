package com.ruoyi.knowledge.catalog;

import java.util.List;

public class KbCatalogDocument extends com.ruoyi.common.core.domain.BaseEntity implements com.ruoyi.knowledge.review.KbQualityImpact {
    private java.util.List<String> affectedQuestionIds = java.util.List.of();
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public java.util.List<String> getAffectedQuestionIds() { return affectedQuestionIds; }
    public void setAffectedQuestionIds(java.util.List<String> value) { affectedQuestionIds = value; }

    private KbCatalogProfile profile;
    private List<KbOutlineScope> outlineScopes;
    private List<Long> declaredKpIds;
    private List<Long> actualKpIds;
    private List<KbRegionLink> regions;
    private List<String> readinessReasons;
    private List<String> matchReasons;
    private KbExamDetail examDetail;
    private List<KbProductQuestionRef> questionRefs;
    private List<KbProductReferenceSource> referenceSources;

    public KbCatalogProfile getProfile() {
        return profile;
    }

    public void setProfile(KbCatalogProfile value) {
        profile = value;
    }

    public List<KbOutlineScope> getOutlineScopes() {
        return outlineScopes;
    }

    public void setOutlineScopes(List<KbOutlineScope> value) {
        outlineScopes = value;
    }

    @tools.jackson.databind.annotation.JsonSerialize(
            contentUsing = tools.jackson.databind.ser.std.ToStringSerializer.class)
    public List<Long> getDeclaredKpIds() {
        return declaredKpIds;
    }

    public void setDeclaredKpIds(List<Long> value) {
        declaredKpIds = value;
    }

    @tools.jackson.databind.annotation.JsonSerialize(
            contentUsing = tools.jackson.databind.ser.std.ToStringSerializer.class)
    public List<Long> getActualKpIds() {
        return actualKpIds;
    }

    public void setActualKpIds(List<Long> value) {
        actualKpIds = value;
    }

    public List<KbRegionLink> getRegions() {
        return regions;
    }

    public void setRegions(List<KbRegionLink> value) {
        regions = value;
    }

    public List<String> getReadinessReasons() {
        return readinessReasons;
    }

    public void setReadinessReasons(List<String> value) {
        readinessReasons = value;
    }

    public List<String> getMatchReasons() {
        return matchReasons;
    }

    public void setMatchReasons(List<String> value) {
        matchReasons = value;
    }

    public KbExamDetail getExamDetail() {
        return examDetail;
    }

    public void setExamDetail(KbExamDetail value) {
        examDetail = value;
    }

    public List<KbProductQuestionRef> getQuestionRefs() {
        return questionRefs;
    }

    public void setQuestionRefs(List<KbProductQuestionRef> value) {
        questionRefs = value;
    }

    public List<KbProductReferenceSource> getReferenceSources() {
        return referenceSources;
    }

    public void setReferenceSources(List<KbProductReferenceSource> value) {
        referenceSources = value;
    }

    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void unknown(String key, Object value) {
        throw new com.ruoyi.common.exception.ServiceException("未定义字段: " + key, 400);
    }
}
