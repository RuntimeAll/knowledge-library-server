package com.ruoyi.knowledge.importing;

/** A named uploaded resource; file bytes are not fetched from arbitrary URLs. */
public record ImportResource(String resourceKey, String contentType, byte[] bytes) { }
