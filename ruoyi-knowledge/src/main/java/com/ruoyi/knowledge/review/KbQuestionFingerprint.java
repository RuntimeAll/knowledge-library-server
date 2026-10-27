package com.ruoyi.knowledge.review;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.knowledge.domain.KbQuestion;
import java.util.Arrays;

/** Complete teaching-content identity; audit remarks and row versions are intentionally separate. */
public final class KbQuestionFingerprint {
    private KbQuestionFingerprint() { }
    public static String content(KbQuestion q) {
        return KbReviewLedger.hash(JSON.toJSONBytes(Arrays.asList(q.getTitle(), q.getStemHtml(),
                q.getAnswerHtml(), q.getAnalysisHtml(), q.getSubject(), q.getQuestionType(),
                q.getContentKind(), q.getCreationKind(), q.getDifficulty(), q.getSourceNote(),
                q.getLayoutMode(), q.getUnitKind(), q.getPaginationMode(), q.getLayoutNote(),
                q.getItemCount(), q.getKpIds(), q.getAssetIds())));
    }
}
