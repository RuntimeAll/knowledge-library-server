package com.ruoyi.knowledge.review;

/** Read-only response surface for dependency invalidation in the same transaction. */
public interface KbQualityImpact {
    void setAffectedQuestionIds(java.util.List<String> ids);
}
