package com.ruoyi.knowledge.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.html.HtmlMetadata;
import com.ruoyi.knowledge.html.KbHtmlCodec;
import com.ruoyi.knowledge.importing.KbRecordHtmlConverter;
import com.ruoyi.knowledge.importing.dto.KbRecordsRequest;
import com.ruoyi.knowledge.service.impl.KnowledgeDictionary;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/** Focused fresh-review reproductions; fixtures are confined to the pre-existing port guard. */
@EnabledIfEnvironmentVariable(named = "KB_REVIEW_TEST_JDBC_URL", matches = ".+")
class KbReviewFreshContextRegressionTest {
    @TempDir Path directory;

    @Test void ancestorSourceErrorMustNotPermitReapprovalOfGrandchildFromOldFrozenEvidence() throws Exception {
        var fixture = fixture();
        try {
            long root = seed(fixture), child = seed(fixture), grandchild = seed(fixture);
            fixture.service.act(root, "APPROVE", command("fresh-root", 0));
            derive(fixture, root, child, "fresh-child");
            fixture.service.act(child, "APPROVE", command("fresh-child-approved", 0));
            derive(fixture, child, grandchild, "fresh-grandchild");
            fixture.service.act(grandchild, "APPROVE", command("fresh-grandchild-approved", 0));
            fixture.service.act(root, "RETURN_TO_DRAFT", new ReviewModels.Command(
                    "fresh-root-source-error", 1L, "原来源错误", null, "SOURCE_ERROR"));
            assertEquals("DRAFT", fixture.questions.selectById(grandchild).getStatus());
            assertAll(
                    () -> assertFalse(fixture.proofs.derivedEvidence(full(fixture, grandchild)),
                            "a SOURCE_ERROR affecting the frozen parent must invalidate its old approval basis"),
                    () -> assertThrows(ServiceException.class, () -> fixture.service.act(grandchild, "APPROVE",
                            command("fresh-grandchild-again", 2))));
        } finally { fixture.close(); }
    }

    @Test void disabledReviewMustStillBlockMutationOverUnresolvedPreparedOperation() throws Exception {
        var fixture = fixture();
        try {
            long id = seed(fixture);
            KbQuestion question = full(fixture, id);
            var before = new ReviewModels.Snapshot(Long.toString(id), "0", "DRAFT",
                    KbQuestionFingerprint.content(question), fixture.mapper.facts(id));
            var after = new ReviewModels.Snapshot(Long.toString(id), "1", "READY", before.contentHash(), before.facts());
            fixture.ledger.prepare(new ReviewModels.Prepared("fresh-pending", "a".repeat(64),
                    "APPROVE", "teacher", "当前完整内容已审查", KbReviewService.POLICY, before, after, null));
            fixture.jdbc.update("UPDATE kb_question SET status='READY',row_version=1 WHERE id=?", id);
            fixture.service = new KbReviewService(fixture.ledger, fixture.gate, fixture.questions,
                    fixture.mapper, mock(KnowledgeDictionary.class), fixture.proofs, fixture.manager, false);
            var method = KbReviewMysqlIntegrationTest.class.getDeclaredMethod("context");
            method.setAccessible(true);
            try (var context = (org.springframework.context.annotation.AnnotationConfigApplicationContext) method.invoke(fixture)) {
                var source = new com.ruoyi.knowledge.domain.KbSource(); source.setId(id);
                var mutation = context.getBean(KbReviewMysqlIntegrationTest.TestMutation.class);
                try { mutation.sourceChange(source); }
                catch (ServiceException allowedFailClosed) { assertEquals(503, allowedFailClosed.getCode()); }
                assertTrue(fixture.ledger.terminal("fresh-pending") != null
                                || fixture.questions.selectById(id).getRowVersion() == 1,
                        "disabled review must reconcile or reject, never mutate past unresolved PREPARE");
            }
        } finally { fixture.close(); }
    }

    @Test void ancestorErrorMustInvalidateEvidenceThroughAnAlreadyStoppedParent() throws Exception {
        var fixture = fixture();
        try {
            long root = seed(fixture), child = seed(fixture), grandchild = seed(fixture);
            fixture.service.act(root, "APPROVE", command("stop-root", 0));
            derive(fixture, root, child, "stop-child");
            fixture.service.act(child, "APPROVE", command("stop-child-approved", 0));
            derive(fixture, child, grandchild, "stop-grandchild");
            fixture.service.act(grandchild, "APPROVE", command("stop-grandchild-approved", 0));
            fixture.service.act(child, "SOFT_DELETE", new ReviewModels.Command(
                    "stop-parent", 1L, "仅停止选用父题", null, "STOP_USE"));
            assertTrue(fixture.service.readiness(grandchild).eligible(), "STOP_USE alone keeps frozen descendants valid");
            fixture.service.act(root, "RETURN_TO_DRAFT", new ReviewModels.Command(
                    "stopped-ancestor-error", 1L, "祖先来源有误", null, "SOURCE_ERROR"));
            var error = fixture.ledger.prepared("stopped-ancestor-error");
            assertTrue(error.affectedAfter().stream().anyMatch(s -> s.questionId().equals(Long.toString(child))
                    && "DELETED".equals(s.status()) && "2".equals(s.rowVersion())),
                    "unchanged stopped intermediates must be durable error-impact evidence");
            assertAll(
                    () -> assertFalse(fixture.service.readiness(grandchild).canApprove(),
                            "a stopped intermediate parent is absent from affectedAfter but its frozen evidence still depends on the ancestor"),
                    () -> assertThrows(ServiceException.class, () -> fixture.service.act(grandchild, "APPROVE",
                            command("stopped-grandchild-again", 2))));
        } finally { fixture.close(); }
    }

    @Test void newFreezeAfterCorrectiveReviewIsNotRejectedByEarlierError() throws Exception {
        var fixture = fixture();
        try {
            long root = seed(fixture), child = seed(fixture);
            fixture.service.act(root, "APPROVE", command("corrected-before", 0));
            fixture.service.act(root, "RETURN_TO_DRAFT", new ReviewModels.Command(
                    "corrected-error", 1L, "来源待复核", null, "SOURCE_ERROR"));
            fixture.service.act(root, "APPROVE", command("corrected-reviewed", 2));
            derive(fixture, root, child, "corrected-child");
            fixture.service.act(child, "APPROVE", command("corrected-child-approved", 0));
            assertTrue(fixture.service.readiness(child).eligible());
        } finally { fixture.close(); }
    }

    @Test void sourceErrorInAuxiliaryParentMustInvalidateChildEvidence() throws Exception {
        var fixture = fixture();
        try {
            long primary = seed(fixture), auxiliary = seed(fixture), child = seed(fixture);
            fixture.service.act(primary, "APPROVE", command("aux-primary", 0));
            fixture.service.act(auxiliary, "APPROVE", command("aux-parent", 0));
            derive(fixture, primary, child, "aux-child", auxiliary);
            fixture.service.act(child, "APPROVE", command("aux-child-approved", 0));
            fixture.service.act(auxiliary, "RETURN_TO_DRAFT", new ReviewModels.Command(
                    "aux-source-error", 1L, "辅助来源错误", null, "SOURCE_ERROR"));
            assertEquals("DRAFT", fixture.questions.selectById(child).getStatus());
            assertAll(
                    () -> assertFalse(fixture.service.readiness(child).canApprove(),
                            "auxiliary ancestry was part of the saved derivation and cannot be ignored on reapproval"),
                    () -> assertThrows(ServiceException.class, () -> fixture.service.act(child, "APPROVE",
                            command("aux-child-again", 2))));
        } finally { fixture.close(); }
    }

    @Test void initiallyDisabledWithoutLedgerStillInvalidatesDependenciesAndForbidsNormalUse() throws Exception {
        var fixture = fixture();
        try {
            long id = seed(fixture);
            fixture.jdbc.update("UPDATE kb_question SET status='READY' WHERE id=?", id);
            fixture.service = new KbReviewService(new KbReviewLedger(""), fixture.gate, fixture.questions,
                    fixture.mapper, mock(KnowledgeDictionary.class), fixture.proofs, fixture.manager, false);
            var method = KbReviewMysqlIntegrationTest.class.getDeclaredMethod("context"); method.setAccessible(true);
            try (var context = (org.springframework.context.annotation.AnnotationConfigApplicationContext) method.invoke(fixture)) {
                var source = new com.ruoyi.knowledge.domain.KbSource(); source.setId(id);
                context.getBean(KbReviewMysqlIntegrationTest.TestMutation.class).sourceChange(source);
                assertEquals("DRAFT", fixture.questions.selectById(id).getStatus());
                assertEquals(503, assertThrows(ServiceException.class, () -> fixture.service.approvalJson()).getCode());
            }
        } finally { fixture.close(); }
    }

    @Test void stoppedAuxiliaryParentKeepsExistingChildUsableButChangedEdgeCannotBeRebound() throws Exception {
        var fixture = fixture();
        try {
            long primary = seed(fixture), auxiliary = seed(fixture), child = seed(fixture);
            fixture.service.act(primary, "APPROVE", command("aux-stop-primary", 0));
            fixture.service.act(auxiliary, "APPROVE", command("aux-stop-source", 0));
            derive(fixture, primary, child, "aux-stop-child", auxiliary);
            fixture.service.act(child, "APPROVE", command("aux-stop-approved", 0));
            fixture.service.act(auxiliary, "SOFT_DELETE", new ReviewModels.Command(
                    "aux-stop", 1L, "仅停止使用", null, "STOP_USE"));
            assertTrue(fixture.service.readiness(child).eligible());
            fixture.jdbc.update("UPDATE kb_question_derivation SET change_note='different justification' WHERE parent_question_id=? AND child_question_id=?", auxiliary, child);
            assertFalse(fixture.proofs.derivedEvidence(full(fixture, child)));
            assertFalse(fixture.service.readiness(child).eligible());
        } finally { fixture.close(); }
    }

    @Test void sourceDependencyChangeInAncestorRejectsOldGrandchildEvidence() throws Exception {
        var fixture = fixture();
        try {
            long root = seed(fixture), parent = seed(fixture), child = seed(fixture);
            fixture.service.act(root, "APPROVE", command("dep-root", 0));
            derive(fixture, root, parent, "dep-parent");
            fixture.service.act(parent, "APPROVE", command("dep-parent-approve", 0));
            derive(fixture, parent, child, "dep-child");
            fixture.service.act(child, "APPROVE", command("dep-child-approve", 0));
            var method = KbReviewMysqlIntegrationTest.class.getDeclaredMethod("context"); method.setAccessible(true);
            try (var context = (org.springframework.context.annotation.AnnotationConfigApplicationContext) method.invoke(fixture)) {
                var source = new com.ruoyi.knowledge.domain.KbSource(); source.setId(root);
                context.getBean(KbReviewMysqlIntegrationTest.TestMutation.class).sourceChange(source);
            }
            assertEquals("DRAFT", fixture.questions.selectById(child).getStatus());
            assertFalse(fixture.service.readiness(child).canApprove());
        } finally { fixture.close(); }
    }

    @Test void twoItemCandidateCannotReceiveQuantityExceptionWithOneMissingAnswer() throws Exception {
        var fixture = fixture();
        try {
            var candidateProxy = mock(KbCandidateProxy.class);
            var proofService = new KbReviewProofs(fixture.ledger, fixture.gate, fixture.mapper, fixture.questions,
                    candidateProxy, new KbRecordHtmlConverter(), new KbHtmlCodec());
            var metadata = new HtmlMetadata("two computations", "MATH", "CALCULATION", "MEDIUM", List.of(),
                    null, null, null, null, null, "EXERCISE", "PLAIN", "GROUP", "FLOW", null);
            var record = new KbRecordsRequest.Record("fresh-small", "create", null, null, null, metadata,
                    "计算下面各题。", null, null, null, null, null, 1,
                    List.of(new KbRecordsRequest.Item("a", "1+1=", null, "2", null, null, null),
                            new KbRecordsRequest.Item("b", "2+2=", null, null, null, null, null)), null);
            var candidate = new KbCandidateProxy.Candidate("pipeline-candidate/v1", "fresh-run", "g", "original",
                    "teacher", "fixture", "fresh-candidate", 1, "fresh-small", "b".repeat(64), "{}", record, "1",
                    new KbCandidateProxy.SourceLocator("1", null, "1", null),
                    List.of(new KbCandidateProxy.ArtifactRef("fixture-original", "c".repeat(64), "application/pdf", 10L)),
                    "2026-10-27", "CODEX", true, null);
            when(candidateProxy.get("fresh-run", "fresh-candidate")).thenReturn(candidate);
            assertThrows(ServiceException.class, () -> proofService.grant(new ReviewModels.ExceptionCommand(
                    "fresh-small-exception", "真实原件只有两小题", null, null, "fresh-run", "fresh-candidate", "b".repeat(64))));
        } finally { fixture.close(); }
    }

    private KbReviewMysqlIntegrationTest fixture() throws Exception {
        var fixture = new KbReviewMysqlIntegrationTest(); fixture.directory = directory; fixture.setup(); return fixture;
    }
    private static long seed(KbReviewMysqlIntegrationTest fixture) throws Exception {
        var method = KbReviewMysqlIntegrationTest.class.getDeclaredMethod("seed"); method.setAccessible(true);
        return (long) method.invoke(fixture);
    }
    private static KbQuestion full(KbReviewMysqlIntegrationTest fixture, long id) {
        KbQuestion value = fixture.questions.selectById(id); value.setKpIds(fixture.questions.selectKpIds(id));
        value.setAssetIds(fixture.questions.selectAssetIds(id)); return value;
    }
    private static ReviewModels.Command command(String key, long version) {
        return new ReviewModels.Command(key, version, "独立复核当前完整内容", true);
    }
    private static void derive(KbReviewMysqlIntegrationTest fixture, long parent, long child, String key) {
        derive(fixture, parent, child, key, null);
    }
    private static void derive(KbReviewMysqlIntegrationTest fixture, long parent, long child, String key, Long auxiliary) {
        var question = full(fixture, parent);
        var frozen = fixture.service.freezeParent(parent, question.getRowVersion(), question.getStemHash(),
                fixture.service.contentHash(question));
        fixture.jdbc.update("UPDATE kb_question SET creation_kind='DERIVED' WHERE id=?", child);
        fixture.jdbc.update("INSERT INTO kb_question_derivation(parent_question_id,child_question_id,is_primary,operation,change_note,preserved_note) VALUES(?,?,true,'CHANGE_VALUES','changed','preserved')", parent, child);
        if (auxiliary != null) {
            fixture.jdbc.update("INSERT INTO kb_question_derivation(parent_question_id,child_question_id,is_primary,operation,change_note,preserved_note) VALUES(?,?,false,'CHANGE_VALUES','changed','preserved')", auxiliary, child);
        }
        var saved = full(fixture, child); saved.setReviewParentFreeze(frozen);
        var input = new KbQuestion(); input.setImportClientKey(key);
        fixture.proofs.bindImport(saved, input, "teacher", "d".repeat(64));
        fixture.jdbc.update("INSERT INTO kb_import_identity VALUES('" + key + "','teacher',?,?)", child, "d".repeat(64));
        var binding = fixture.ledger.bindings().stream().filter(b -> b.questionId().equals(Long.toString(child))).findFirst().orElseThrow();
        assertEquals(frozen.contentHash(), binding.parentFreeze().contentHash());
        var approval = fixture.ledger.prepared(binding.parentFreeze().approvalOperationId());
        assertEquals(binding.parentFreeze().contentHash(), KbReviewLedger.hash(com.alibaba.fastjson2.JSON.toJSONBytes(
                List.of(approval.after().contentHash(), approval.after().facts()))));
        assertEquals(com.alibaba.fastjson2.JSON.toJSONString(binding.parentFreeze()),
                com.alibaba.fastjson2.JSON.toJSONString(binding.parentFreezes().stream().filter(p -> p.questionId().equals(Long.toString(parent))).findFirst().orElseThrow()));
    }
}
