package com.ruoyi.knowledge.publication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.mapper.KbAgentSubmissionMapper;
import com.ruoyi.knowledge.mapper.PublicationMapper;
import com.ruoyi.knowledge.publication.PublicationModels.*;
import com.ruoyi.knowledge.publication.PublicationRows.*;
import com.ruoyi.knowledge.review.KbReviewGate;
import com.ruoyi.knowledge.review.KbPublicationReviewBridge;
import com.ruoyi.knowledge.review.KbReviewLedger;
import com.ruoyi.knowledge.review.KbReviewService;
import com.ruoyi.knowledge.review.ReviewModels;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

class PublicationReadGateTest {
    @Test void fileCoursewareWithoutQuestionsDoesNotReadOrInventQuestionApprovals() {
        Fixture f = new Fixture(0);
        CheckResult result = f.gate.evaluate(f.row, f.sale);
        assertEquals("PASSED", result.status); assertEquals(0, result.checkedCount);
        verifyNoInteractions(f.reviews, f.bridge);
    }
    @Test void allQuestionsAreVerifiedInBoundedBatchesWithoutPerQuestionReadinessSql() {
        Fixture f = new Fixture(501);
        CheckResult result = f.gate.evaluate(f.row, f.sale);
        assertEquals("PASSED", result.status); assertEquals(501, result.checkedCount);
        @SuppressWarnings("unchecked") ArgumentCaptor<List<Long>> batches = ArgumentCaptor.forClass(List.class);
        verify(f.reviews, times(2)).unavailableIds(batches.capture());
        assertEquals(List.of(500, 1), batches.getAllValues().stream().map(List::size).toList());
        verifyNoInteractions(f.bridge);
        verify(f.reviews, never()).readiness(any()); verify(f.reviews, times(1)).freezeApprovals(anyList());
    }
    @Test void reapprovalWithSameContentStillRequiresNewPublicationSale() {
        Fixture f = new Fixture(1); var original = f.approvals.get(1L);
        f.approvals.put(1L, new ReviewModels.ApprovalEvidence("different-approval", "1", "3", original.contentHash(),
                original.policyVersion(), original.actor(), original.reason(), null, original.facts(), original.evidence()));
        CheckResult result = f.gate.evaluate(f.row, f.sale);
        assertEquals("BLOCKED", result.status); assertTrue(result.issues.stream().anyMatch(i -> i.code().equals("APPROVAL_CHANGED")));
    }
    @Test void unavailableLiveQuestionCannotPassOnFrozenReceiptAlone() {
        Fixture f = new Fixture(1); when(f.reviews.unavailableIds(anyList())).thenReturn(List.of(1L));
        assertEquals("BLOCKED", f.gate.evaluate(f.row, f.sale).status);
    }
    @Test void ledgerUncertaintyPropagatesWithoutConvertingItIntoAProvenDependencyChange() {
        Fixture f = new Fixture(1); when(f.reviews.unavailableIds(anyList())).thenThrow(new ServiceException("uncertain", 503));
        assertEquals(503, assertThrows(ServiceException.class, () -> f.gate.evaluate(f.row, f.sale)).getCode());
    }
    @ParameterizedTest @ValueSource(ints = {400, 404, 409})
    void parentProvenanceChangesRemainBlockingEvenWhenChildProjectionIsEligible(int errorCode) throws Exception {
        Fixture f = new Fixture(1); var original = f.approvals.get(1L);
        var facts = List.of(new ReviewModels.Fact("PARENT", "77", "stamp"));
        var changed = new ReviewModels.ApprovalEvidence(original.operationId(), original.questionId(), original.rowVersion(),
                original.contentHash(), original.policyVersion(), original.actor(), original.reason(), null, facts, List.of());
        f.approvals.put(1L, changed);
        String basis = KbReviewLedger.hash(JSON.toJSONBytes(List.of(changed.contentHash(), facts)));
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        Snapshot snapshot = json.readValue(f.sale.snapshotJson, Snapshot.class); snapshot.check().items.get(0).contentHash = basis;
        f.sale.snapshotJson = json.writeValueAsString(snapshot);
        OperationResult committed = json.readValue(f.sale.resultJson, OperationResult.class);
        committed.questionResults = List.of(new QuestionResult("1", "APPROVED", "0", "1", basis, changed.operationId(), changed.policyVersion()));
        f.sale.resultJson = json.writeValueAsString(committed);
        when(f.bridge.lineageEvidence(1L)).thenThrow(new ServiceException("parent source changed", errorCode));
        CheckResult result = f.gate.evaluate(f.row, f.sale);
        assertEquals("BLOCKED", result.status);
        assertTrue(result.issues.stream().anyMatch(i -> "FROZEN_LINEAGE_CHANGED".equals(i.code())));
        assertFalse(result.issues.stream().anyMatch(i -> "APPROVAL_CHANGED".equals(i.code())));
        verify(f.bridge).lineageEvidence(1L);
    }
    static class Fixture {
        final KbReviewService reviews = mock(KbReviewService.class);
        final KbPublicationReviewBridge bridge = mock(KbPublicationReviewBridge.class);
        final Map<Long, ReviewModels.ApprovalEvidence> approvals = new HashMap<>();
        final Publication row = new Publication(); final PublicationOperation sale = new PublicationOperation();
        final PublicationReadGate gate;
        Fixture(int count) {
            var properties = new PublicationProperties(); properties.setEnabled(true);
            var support = new PublicationSupport(mock(PlatformTransactionManager.class), new KbReviewGate(), properties);
            var graph = mock(PublicationGraph.class); var checks = mock(PublicationCheckService.class);
            var service = new PublicationService(mock(PublicationMapper.class), support, graph, mock(KbAgentSubmissionMapper.class));
            gate = new PublicationReadGate(service, checks, reviews, bridge);
            row.id = 1L; row.contentVersion = 1L;
            Input input = new Input(); ProductInput product = new ProductInput(); product.questions = new ArrayList<>(); input.products = List.of(product);
            CheckResult frozen = new CheckResult(); frozen.status = "PASSED"; OperationResult receipt = new OperationResult();
            for (long id = 1; id <= count; id++) {
                var facts = List.of(new ReviewModels.Fact("CONTENT", Long.toString(id), "stamp"));
                var evidence = new ReviewModels.ApprovalEvidence("approval-" + id, Long.toString(id), "1", "a".repeat(64),
                        "PRD012-QUALITY-1", "reviewer", "verified", null, facts, List.of());
                approvals.put(id, evidence);
                String basis = KbReviewLedger.hash(JSON.toJSONBytes(List.of(evidence.contentHash(), evidence.facts())));
                QuestionInput ref = new QuestionInput(); ref.questionId = id; product.questions.add(ref);
                QuestionCheck checked = new QuestionCheck(); checked.questionId = id; checked.contentHash = basis; frozen.items.add(checked);
                receipt.questionResults.add(new QuestionResult(Long.toString(id), "APPROVED", "0", "1", basis, evidence.operationId(), evidence.policyVersion()));
            }
            sale.snapshotJson = support.encode(new Snapshot(input, frozen)); sale.resultJson = support.encode(receipt);
            when(graph.read(row)).thenReturn(input); when(checks.materialIssues(input)).thenReturn(List.of());
            when(checks.dependency(eq(row.id), eq(input), anyList())).thenReturn("unchanged");
            when(reviews.unavailableIds(anyList())).thenReturn(List.of()); when(reviews.freezeApprovals(anyList())).thenReturn(approvals);
        }
    }
}
