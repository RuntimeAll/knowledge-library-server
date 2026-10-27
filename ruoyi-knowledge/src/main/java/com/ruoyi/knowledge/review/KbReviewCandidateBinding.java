package com.ruoyi.knowledge.review;

import com.ruoyi.knowledge.mapper.KbReviewMapper;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.ruoyi.knowledge.domain.KbOccurrence;
import com.ruoyi.knowledge.domain.KbQuestion;
import java.util.List;
import java.util.Objects;

/** The only allowed post-import proof evolution is the same source's first exact verification. */
final class KbReviewCandidateBinding {
    private final KbReviewMapper mapper;

    KbReviewCandidateBinding(KbReviewMapper mapper) { this.mapper = mapper; }

    boolean factsMatch(ReviewModels.ImportBinding binding, ReviewModels.GranularityException proof,
            KbQuestion question, List<ReviewModels.Fact> current) {
        if (Objects.equals(binding.facts(), current)) { return true; }
        if (proof.sourceLocator() == null || proof.sourceId() == null
                || !stableFacts(binding.facts()).equals(stableFacts(current))) { return false; }
        List<KbOccurrence> occurrences = mapper.candidateOccurrences(question.getId(), proof.sourceId());
        if (occurrences.size() != 1) { return false; }
        KbOccurrence occurrence = occurrences.get(0);
        if (!"VERIFIED".equals(occurrence.getMatchStatus())
                || !Objects.equals(question.getStemHash(), occurrence.getMatchedStemHash())
                || !sameLocation(proof.sourceLocator(), occurrence)) { return false; }
        List<ReviewModels.Fact> before = byKind(binding.facts(), "OCCURRENCE");
        List<ReviewModels.Fact> after = byKind(current, "OCCURRENCE");
        if (before.size() != after.size()) { return false; }
        boolean completed = false;
        for (int i = 0; i < before.size(); i++) {
            ReviewModels.Fact old = before.get(i), next = after.get(i);
            if (old.equals(next)) { continue; }
            if (completed || !old.identity().equals(occurrence.getId().toString())
                    || !old.identity().equals(next.identity()) || !firstVerification(old, next, proof, question)) { return false; }
            completed = true;
        }
        return completed && evidenceUnchangedOrAttached(binding.facts(), current, occurrence);
    }

    private static boolean firstVerification(ReviewModels.Fact before, ReviewModels.Fact after,
            ReviewModels.GranularityException proof, KbQuestion question) {
        JSONArray old = JSON.parseArray(before.stamp()), next = JSON.parseArray(after.stamp());
        return old.size() == 4 && next.size() == 4 && "UNVERIFIED".equals(old.getString(2))
                && old.getString(3) == null && "VERIFIED".equals(next.getString(2))
                && proof.sourceId().equals(old.getString(1)) && proof.sourceId().equals(next.getString(1))
                && next.getLongValue(0) == old.getLongValue(0) + 1
                && Objects.equals(question.getStemHash(), next.getString(3));
    }

    private static boolean sameLocation(KbCandidateProxy.SourceLocator expected, KbOccurrence actual) {
        String page = blank(expected.printedPage()) ? expected.physicalPage() : expected.printedPage();
        if (!blank(page)) {
            try {
                if (!page.matches("[0-9]+") || !Objects.equals(Integer.valueOf(page), actual.getPageFrom())) { return false; }
            } catch (NumberFormatException invalidPage) { return false; }
        }
        return Objects.equals(blank(expected.questionNo()) ? null : expected.questionNo(),
                blank(actual.getQuestionNo()) ? null : actual.getQuestionNo());
    }

    private static boolean evidenceUnchangedOrAttached(List<ReviewModels.Fact> before,
            List<ReviewModels.Fact> after, KbOccurrence occurrence) {
        List<ReviewModels.Fact> oldEvidence = byKind(before, "EVIDENCE_ASSET");
        List<ReviewModels.Fact> newEvidence = byKind(after, "EVIDENCE_ASSET");
        if (oldEvidence.equals(newEvidence)) { return true; }
        if (!oldEvidence.isEmpty() || newEvidence.size() != 1 || occurrence.getEvidenceAssetId() == null) { return false; }
        ReviewModels.Fact added = newEvidence.get(0);
        if (!added.identity().equals(occurrence.getEvidenceAssetId().toString())) { return false; }
        JSONArray bytes = JSON.parseArray(added.stamp());
        return byKind(before, "SOURCE_ASSET").stream().anyMatch(source -> {
            JSONArray original = JSON.parseArray(source.stamp());
            return source.identity().equals(occurrence.getSourceId() + ":" + occurrence.getEvidenceAssetId())
                    && bytes.size() == 3 && original.size() == 4
                    && Objects.equals(original.get(0), bytes.get(0)) && "READY".equals(bytes.getString(0))
                    && Objects.equals(original.get(1), bytes.get(1)) && Objects.equals(original.get(2), bytes.get(2));
        });
    }

    private static List<ReviewModels.Fact> stableFacts(List<ReviewModels.Fact> facts) {
        return facts.stream().filter(f -> !List.of("OCCURRENCE", "EVIDENCE_ASSET").contains(f.kind())).toList();
    }
    private static List<ReviewModels.Fact> byKind(List<ReviewModels.Fact> facts, String kind) {
        return facts.stream().filter(f -> kind.equals(f.kind())).toList();
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
