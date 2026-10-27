package com.ruoyi.knowledge.review;

import com.ruoyi.knowledge.mapper.KbReviewMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.mapper.KbQuestionMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Corrupt/legacy immutable evidence is checked without any database read before paging. */
class KbReviewLineageTest {
    @Test void validFrozenChainUsesOnlyLedgerEvidence() {
        var graph = graph(4,false,false);
        assertTrue(graph.lineage.evidenceValid(graph.approvals.get("approve-1")));
        verifyNoInteractions(graph.mapper,graph.questions);
    }

    @Test void cycleInFrozenApprovalGraphFailsClosedWithoutRecursingForever() {
        var graph = graph(2,true,false);
        assertFalse(graph.lineage.evidenceValid(graph.approvals.get("approve-1")));
        verifyNoInteractions(graph.mapper,graph.questions);
    }

    @Test void excessiveDepthFailsClosedAtDocumentedBoundary() {
        var boundary = graph(65,false,false);
        assertTrue(boundary.lineage.evidenceValid(boundary.approvals.get("approve-1")));
        var tooDeep = graph(66,false,false);
        assertFalse(tooDeep.lineage.evidenceValid(tooDeep.approvals.get("approve-1")));
        verifyNoInteractions(tooDeep.mapper,tooDeep.questions);
    }

    @Test void legacySingleParentBindingRemainsReadableWithoutRebinding() {
        var graph = graph(3,false,true);
        assertTrue(graph.lineage.evidenceValid(graph.approvals.get("approve-1")));
        verifyNoInteractions(graph.mapper,graph.questions);
    }

    @Test void legacyMultiParentBindingCannotInventTheMissingAuxiliaryApproval() {
        var graph = graph(3,false,true);
        var old = graph.bindings.get(0);
        var facts = new ArrayList<>(old.facts());
        facts.add(new ReviewModels.Fact("PARENT","3","auxiliary-edge"));
        var incomplete = new ReviewModels.ImportBinding(old.bindingId(),old.clientKey(),old.createBy(),old.requestFingerprint(),
                old.questionId(),old.rowVersion(),old.contentHash(),facts,null,old.parentFreeze());
        graph.bindings.set(0,incomplete);
        var previous = graph.approvals.get("approve-1");
        var after = new ReviewModels.Snapshot("1","1","READY",previous.after().contentHash(),facts);
        graph.approvals.put("approve-1",new ReviewModels.Prepared(previous.operationId(),previous.requestHash(),previous.action(),
                previous.actor(),previous.reason(),previous.policyVersion(),previous.before(),after,null,null,List.of(),List.of(),
                List.of(new ReviewModels.Evidence("BINDING",incomplete.bindingId(),KbReviewLedger.hash(JSON.toJSONBytes(incomplete))))));
        assertFalse(graph.lineage.evidenceValid(graph.approvals.get("approve-1")));
        verifyNoInteractions(graph.mapper,graph.questions);
    }

    private static Graph graph(int count, boolean cycle, boolean legacy) {
        var ledger = mock(KbReviewLedger.class);
        var mapper = mock(KbReviewMapper.class);
        var questions = mock(KbQuestionMapper.class);
        Map<String,ReviewModels.Prepared> approvals = new HashMap<>();
        List<ReviewModels.ImportBinding> bindings = new ArrayList<>();
        List<ReviewModels.ParentFreeze> frozen = new ArrayList<>();
        for (int index=0;index<count;index++) {
            boolean hasParent = cycle || index+1<count;
            String id = Integer.toString(index+1);
            var question = new KbQuestion();question.setId((long)index+1);question.setRowVersion(1L);
            question.setCreationKind(hasParent?"DERIVED":"AUTHORED");
            var facts = hasParent ? List.of(new ReviewModels.Fact("PARENT",Integer.toString((index+1)%count+1),"frozen-edge"))
                    : List.<ReviewModels.Fact>of();
            String content = KbQuestionFingerprint.content(question);
            frozen.add(new ReviewModels.ParentFreeze(id,"1",KbReviewLedger.hash(JSON.toJSONBytes(List.of(content,facts))),
                    "approve-"+id,question,facts));
        }
        for (int index=0;index<count;index++) {
            var value = frozen.get(index);
            boolean hasParent = cycle || index+1<count;
            List<ReviewModels.Evidence> evidence = List.of();
            String content = KbQuestionFingerprint.content(value.question());
            if (hasParent) {
                var parent = frozen.get((index+1)%count);
                var binding = new ReviewModels.ImportBinding("binding-"+value.questionId(),"key-"+value.questionId(),"teacher","f".repeat(64),
                        value.questionId(),"0",content,value.facts(),null,parent,legacy?null:List.of(parent));
                bindings.add(binding);
                evidence = List.of(new ReviewModels.Evidence("BINDING",binding.bindingId(),KbReviewLedger.hash(JSON.toJSONBytes(binding))));
            }
            var before = new ReviewModels.Snapshot(value.questionId(),"0","DRAFT",content,value.facts());
            var after = new ReviewModels.Snapshot(value.questionId(),"1","READY",content,value.facts());
            approvals.put(value.approvalOperationId(),new ReviewModels.Prepared(value.approvalOperationId(),"a".repeat(64),"APPROVE",
                    "teacher","approved",KbReviewService.POLICY,before,after,null,null,List.of(),List.of(),evidence));
        }
        when(ledger.operations()).thenAnswer(call -> List.copyOf(approvals.values()));
        when(ledger.bindings()).thenReturn(bindings);
        when(ledger.prepared(anyString())).thenAnswer(call -> approvals.get(call.getArgument(0)));
        when(ledger.terminal(anyString())).thenAnswer(call -> new ReviewModels.Terminal(call.getArgument(0),"a".repeat(64),"CONFIRMED"));
        return new Graph(new KbReviewLineage(ledger,mapper,questions),mapper,questions,approvals,bindings);
    }

    private record Graph(KbReviewLineage lineage,KbReviewMapper mapper,KbQuestionMapper questions,
            Map<String,ReviewModels.Prepared> approvals,List<ReviewModels.ImportBinding> bindings) { }
}
