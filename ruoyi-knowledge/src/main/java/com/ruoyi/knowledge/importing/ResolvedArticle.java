package com.ruoyi.knowledge.importing;

import java.util.List;
import com.ruoyi.knowledge.html.HtmlQuestionDocument;

public record ResolvedArticle(HtmlQuestionDocument document, String stemHtml, String answerHtml,
                              String analysisHtml, List<Long> assetIds, String fingerprint) { }
