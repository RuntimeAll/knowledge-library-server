package com.ruoyi.knowledge.layout;

/** Optional explicit same-role version chain; never inferred from titles. */
public record KbPreviousOutput(String outputKey, Long productId, Long rowVersion) {}
