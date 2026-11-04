package com.ruoyi.knowledge.review;

import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.service.impl.QuestionContentRules;

/** Explicit policy compatibility; existing approvals retain their original policy and bytes. */
final class ReviewPolicies {
    static final String STANDARD = "PRD010-QUALITY-1";
    static final String PUBLICATION = "PRD012-QUALITY-1";

    private ReviewPolicies() { }

    static boolean supported(String policy) {
        return STANDARD.equals(policy) || PUBLICATION.equals(policy);
    }

    static boolean exceptionAnswerValid(KbQuestion question, String policy) {
        if (PUBLICATION.equals(policy) && !QuestionContentRules.hasContent(question.getAnswerHtml())) { return true; }
        return ReviewContentChecks.completeAnswer(question.getStemHtml(), question.getAnswerHtml(),
                "GROUP".equals(question.getUnitKind()));
    }

    static boolean answerValid(KbQuestion question, String policy) {
        if (!"EXERCISE".equals(question.getContentKind())) { return true; }
        if (PUBLICATION.equals(policy) && !QuestionContentRules.hasContent(question.getAnswerHtml())) { return true; }
        return ReviewContentChecks.completeAnswer(question.getStemHtml(), question.getAnswerHtml(),
                "GROUP".equals(question.getUnitKind()));
    }
}
