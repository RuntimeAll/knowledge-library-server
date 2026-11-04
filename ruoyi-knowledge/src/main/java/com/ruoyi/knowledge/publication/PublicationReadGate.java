package com.ruoyi.knowledge.publication;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.knowledge.publication.PublicationModels.CheckResult;
import com.ruoyi.knowledge.publication.PublicationModels.Input;
import com.ruoyi.knowledge.publication.PublicationModels.Issue;
import com.ruoyi.knowledge.publication.PublicationModels.OperationResult;
import com.ruoyi.knowledge.publication.PublicationModels.QuestionCheck;
import com.ruoyi.knowledge.publication.PublicationModels.QuestionResult;
import com.ruoyi.knowledge.publication.PublicationModels.Snapshot;
import com.ruoyi.knowledge.publication.PublicationRows.Publication;
import com.ruoyi.knowledge.publication.PublicationRows.PublicationOperation;
import com.ruoyi.knowledge.review.KbReviewLedger;
import com.ruoyi.knowledge.review.KbPublicationReviewBridge;
import com.ruoyi.knowledge.review.KbReviewService;
import com.ruoyi.knowledge.review.ReviewModels;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Rechecks live eligibility in bounded batches and binds it to this sale's immutable approval receipts. */
@Service
public class PublicationReadGate {
    private final PublicationService service;
    private final PublicationCheckService checks;
    private final KbReviewService reviews;
    private final KbPublicationReviewBridge bridge;
    public PublicationReadGate(PublicationService service, PublicationCheckService checks, KbReviewService reviews, KbPublicationReviewBridge bridge) {
        this.service = service; this.checks = checks; this.reviews = reviews; this.bridge = bridge;
    }
    public CheckResult evaluate(Publication row, PublicationOperation sale) {
        Snapshot snapshot = service.support.decode(sale.snapshotJson, Snapshot.class);
        OperationResult receipt = service.support.decode(sale.resultJson, OperationResult.class);
        if (snapshot == null || snapshot.check() == null || receipt == null || receipt.questionResults == null) {
            throw PublicationSupport.fail(503, "DEPENDENCY_UNAVAILABLE", "发售证据暂不可核对");
        }
        Input input = service.graph.read(row); CheckResult result = new CheckResult();
        result.items = snapshot.check().items;
        result.issues.addAll(checks.materialIssues(input));
        List<Long> ids = input.products.stream().flatMap(p -> p.questions.stream()).map(q -> q.questionId).distinct().sorted().toList();
        Set<Long> frozen = result.items.stream().map(q -> q.questionId).collect(Collectors.toSet());
        if (!frozen.equals(Set.copyOf(ids))) { result.issues.add(issue("QUESTION_LIST_CHANGED", null)); }
        // unavailableIds checks the current SQL facts against the verified review ledger; no per-question SQL here.
        for (int start = 0; start < ids.size(); start += 500) {
            List<Long> unavailable = reviews.unavailableIds(ids.subList(start, Math.min(start + 500, ids.size())));
            for (Long id : unavailable) { result.issues.add(issue("QUESTION_NOT_ELIGIBLE", id)); }
        }
        Map<Long, ReviewModels.ApprovalEvidence> approvals = ids.isEmpty() ? Map.of() : reviews.freezeApprovals(ids);
        Map<String, QuestionResult> committed = receipt.questionResults.stream().collect(Collectors.toMap(QuestionResult::questionId, q -> q));
        if (committed.size() != ids.size()) { result.issues.add(issue("SALE_RECEIPT_CHANGED", null)); }
        for (QuestionCheck question : result.items) {
            ReviewModels.ApprovalEvidence evidence = approvals.get(question.questionId);
            if (!matches(question, evidence, committed.get(question.questionId.toString()))) {
                result.issues.add(issue("APPROVAL_CHANGED", question.questionId));
            }
            if (evidence != null && (evidence.facts().stream().anyMatch(f -> "PARENT".equals(f.kind()))
                    || (evidence.evidence() != null && evidence.evidence().stream().anyMatch(e -> "BINDING".equals(e.kind()))))
                    && !lineageMatches(question)) {
                result.issues.add(issue("FROZEN_LINEAGE_CHANGED", question.questionId));
            }
        }
        result.questionCount = ids.size(); result.checkedCount = ids.size(); result.blockingCount = result.issues.size();
        result.status = result.blockingCount == 0 ? "PASSED" : "BLOCKED";
        result.dependencyHash = checks.dependency(row.id, input, result.items);
        return result;
    }
    private boolean lineageMatches(QuestionCheck frozen) {
        try {
            // Parent facts include ancestry provenance beyond the child's SQL projection. Preserve that stricter gate.
            ReviewModels.PublicationLineageEvidence lineage = bridge.lineageEvidence(frozen.questionId);
            if (lineage == null) { return false; }
            var expected = frozen.sourceEvidence.stream().filter(e -> "FROZEN_LINEAGE".equals(e.kind)).toList();
            return expected.stream().allMatch(e -> Objects.equals(lineage.bindingId(), e.lineageEvidenceId))
                    && expected.stream().map(e -> e.sourceId.toString()).collect(Collectors.toSet()).equals(Set.copyOf(lineage.sourceIds()));
        } catch (com.ruoyi.common.exception.ServiceException e) {
            if (Integer.valueOf(400).equals(e.getCode()) || Integer.valueOf(404).equals(e.getCode())
                    || Integer.valueOf(409).equals(e.getCode())) { return false; }
            throw e;
        }
    }
    private static boolean matches(QuestionCheck frozen, ReviewModels.ApprovalEvidence current, QuestionResult committed) {
        if (current == null || committed == null) { return false; }
        String basis = KbReviewLedger.hash(JSON.toJSONBytes(List.of(current.contentHash(), current.facts())));
        return Objects.equals(current.operationId(), committed.approvalOperationId())
                && Objects.equals(current.rowVersion(), committed.afterRowVersion())
                && Objects.equals(current.policyVersion(), committed.policyVersion())
                && Objects.equals(basis, committed.contentHash()) && Objects.equals(basis, frozen.contentHash);
    }
    private static Issue issue(String code, Long id) {
        return new Issue(code, null, null, PublicationSupport.id(id), "发售后的题目资格或冻结依据已变化");
    }
}
