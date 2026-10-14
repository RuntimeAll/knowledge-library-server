package com.ruoyi.knowledge.html;

import org.jsoup.nodes.Element;

/** Parsed article before asset resolution. */
public record HtmlQuestionDocument(String clientKey, String intent, Long questionId, Long rowVersion,
                                   Long parentId, HtmlMetadata metadata, Element stem,
                                   Element answer, Element analysis, String fingerprint) { }
