package com.ruoyi.knowledge.html;

import org.jsoup.nodes.Element;

/** Parsed article before asset resolution. */
public record HtmlQuestionDocument(String clientKey, String intent, Long questionId, Long rowVersion,
                                   Long parentId, HtmlMetadata metadata, Element stem,
                                   Element answer, Element analysis, String fingerprint,
                                   Long expectedParentRowVersion, String expectedParentStemHash,
                                   String granularityExceptionId, String candidateArticleHash, String expectedParentContentHash) {
    public HtmlQuestionDocument(String clientKey, String intent, Long questionId, Long rowVersion,
            Long parentId, HtmlMetadata metadata, Element stem, Element answer, Element analysis, String fingerprint) {
        this(clientKey,intent,questionId,rowVersion,parentId,metadata,stem,answer,analysis,fingerprint,null,null,null,null,null);
    }
}
