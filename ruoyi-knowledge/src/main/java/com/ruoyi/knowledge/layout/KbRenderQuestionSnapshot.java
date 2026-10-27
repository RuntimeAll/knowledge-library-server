package com.ruoyi.knowledge.layout;

import com.alibaba.fastjson2.*;
import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.domain.*;

import java.util.*;

public class KbRenderQuestionSnapshot {
    private com.ruoyi.knowledge.review.ReviewModels.ApprovalEvidence approvalEvidence;
    public com.ruoyi.knowledge.review.ReviewModels.ApprovalEvidence getApprovalEvidence() { return approvalEvidence; }
    public void setApprovalEvidence(com.ruoyi.knowledge.review.ReviewModels.ApprovalEvidence value) { approvalEvidence=value; }
    private KbQuestion question;
    private List<KbOccurrence> occurrences;
    private List<KbKp> knowledgePoints;
    private List<KbSource> sources;

    public List<KbKp> getKnowledgePoints() {
        return knowledgePoints;
    }

    public void setKnowledgePoints(List<KbKp> value) {
        knowledgePoints = value;
    }

    public List<KbSource> getSources() {
        return sources;
    }

    public void setSources(List<KbSource> value) {
        sources = value;
    }

    public KbQuestion getQuestion() {
        return question;
    }

    public void setQuestion(KbQuestion value) {
        question = value;
    }

    public List<KbOccurrence> getOccurrences() {
        return occurrences;
    }

    public void setOccurrences(List<KbOccurrence> value) {
        occurrences = value;
    }
}
