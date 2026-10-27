package com.ruoyi.knowledge.review;

import com.ruoyi.knowledge.service.impl.QuestionContentRules;
import org.jsoup.Jsoup;

/** Shared completeness check for candidate proofs and saved-question approval. */
final class ReviewContentChecks {
    private ReviewContentChecks() { }

    static boolean completeAnswer(String stem, String answer, boolean grouped) {
        if (!QuestionContentRules.hasContent(answer)) { return false; }
        var stemItems = Jsoup.parseBodyFragment(stem == null ? "" : stem)
                .select("[data-kb-role=group] > [data-item-key]");
        if (stemItems.isEmpty()) { return !grouped; }
        var answerItems = Jsoup.parseBodyFragment(answer).select("[data-kb-role=group] > [data-item-key]");
        return stemItems.eachAttr("data-item-key").equals(answerItems.eachAttr("data-item-key"))
                && answerItems.stream().allMatch(item -> QuestionContentRules.hasContent(item.html()));
    }
}
