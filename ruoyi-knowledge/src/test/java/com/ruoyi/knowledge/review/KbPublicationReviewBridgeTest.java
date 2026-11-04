package com.ruoyi.knowledge.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.html.KbHtmlCodec;
import com.ruoyi.knowledge.importing.KbRecordHtmlConverter;
import com.ruoyi.knowledge.mapper.KbQuestionMapper;
import com.ruoyi.knowledge.mapper.KbReviewMapper;
import com.ruoyi.knowledge.service.impl.KnowledgeDictionary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Real immutable ledger with isolated mapper/transaction doubles; no database connection. */
class KbPublicationReviewBridgeTest {
    @TempDir Path directory;
    KbReviewLedger ledger;
    KbReviewGate gate;
    KbQuestionMapper questions;
    KbReviewMapper mapper;
    KbReviewProofs proofs;
    KbReviewService reviews;
    KbPublicationReviewBridge bridge;
    PublicationReviewContextValidator validator;
    KbQuestion question;
    final ReviewModels.PublicationContext context = new ReviewModels.PublicationContext(8L, 2L);

    @BeforeEach void setup() {
        ledger = new KbReviewLedger(directory.toString());
        gate = new KbReviewGate();
        questions = mock(KbQuestionMapper.class);
        mapper = mock(KbReviewMapper.class);
        validator = mock(PublicationReviewContextValidator.class);
        var beans = new StaticListableBeanFactory();
        beans.addBean("publicationValidator", validator);
        var provider = beans.getBeanProvider(PublicationReviewContextValidator.class);
        proofs = new KbReviewProofs(ledger, gate, mapper, questions, mock(KbCandidateProxy.class),
                new KbRecordHtmlConverter(), new KbHtmlCodec());
        proofs.setPublicationValidators(provider);
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        reviews = new KbReviewService(ledger, gate, questions, mapper, mock(KnowledgeDictionary.class), proofs, transactions, true);
        bridge = new KbPublicationReviewBridge(reviews, ledger, gate, proofs, provider);
        question = new KbQuestion();
        question.setId(7L); question.setRowVersion(0L); question.setStatus("DRAFT");
        question.setSubject("MATH"); question.setContentKind("EXERCISE"); question.setQuestionType("CHOICE");
        question.setCreationKind("AUTHORED"); question.setStemHtml("<p>2+3是多少？</p>");
        question.setKpIds(List.of(3L)); question.setAssetIds(List.of());
        when(questions.selectById(7L)).thenReturn(question);
        when(questions.lockById(7L)).thenReturn(question);
        when(questions.selectKpIds(7L)).thenReturn(List.of(3L));
        when(questions.selectAssetIds(7L)).thenReturn(List.of());
        when(mapper.validOrigin(7L, "AUTHORED")).thenReturn(1);
        when(mapper.facts(7L)).thenReturn(List.of(new ReviewModels.Fact("SOURCE", "10", "real-source")));
        when(mapper.changeState(eq(7L), anyLong(), anyString(), anyString())).thenAnswer(call -> {
            Long version = call.getArgument(1);
            if (!Objects.equals(version, question.getRowVersion())) { return 0; }
            question.setRowVersion(version + 1); question.setStatus(call.getArgument(2)); return 1;
        });
        when(mapper.usableIds(anyList(), anyString())).thenAnswer(call -> {
            List<ReviewModels.Approval> approvals = JSON.parseArray(call.getArgument(1), ReviewModels.Approval.class);
            return "READY".equals(question.getStatus()) && approvals.stream().anyMatch(a ->
                    a.questionId().equals("7") && a.rowVersion().equals(question.getRowVersion().toString())
                    && a.contentHash().equals(KbQuestionFingerprint.content(question))) ? List.of(7L) : List.of();
        });
        login("teacher", Set.of("knowledge:publication:sale", "knowledge:publication:check", "knowledge:question:approve", "knowledge:question:granularityException"));
    }

    @AfterEach void close() throws Exception {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        SecurityContextHolder.clearContext(); ledger.close();
    }

    @Test void optionalAnswerApprovalIsUsableWithoutWeakeningOrdinaryApproval() {
        assertFalse(reviews.readiness(7L).canApprove());
        assertEquals(409, assertThrows(ServiceException.class,
                () -> reviews.act(7L, "APPROVE", command("ordinary", 0L))).getCode());
        assertTrue(bridge.inspect(context, 7L).canApprove());
        var result = bridge.approveOrReuse(context, "sale", 7L, command("child", 0L));
        assertEquals("APPROVED", result.mode());
        assertEquals("PRD012-QUALITY-1", result.evidence().policyVersion());
        assertEquals("1", result.evidence().rowVersion());
        assertTrue(reviews.readiness(7L).eligible());
        assertEquals("PRD012-QUALITY-1", reviews.readiness(7L).policyVersion());
        assertTrue(reviews.readiness(7L).checks().stream().filter(c -> c.code().equals("ANSWER")).allMatch(ReviewModels.Check::passed));
        assertTrue(reviews.unavailableIds(List.of(7L)).isEmpty());
        gate.enter();
        try { assertEquals("child", reviews.freezeParent(7L, 1L, null, proofs.basis(question)).approvalOperationId()); }
        finally { gate.leave(); }
        verify(mapper, times(1)).changeState(eq(7L), anyLong(), anyString(), anyString());
    }

    @Test void replayPreservesOriginalVersionAndLedgerBytesAndDifferentActorCannotContinue() throws Exception {
        var command = command("stable-child", 0L);
        bridge.approveOrReuse(context, "sale", 7L, command);
        Path prepared;
        try (var files = Files.list(directory)) { prepared = files.filter(p -> p.toString().endsWith(".prepare.json")).findFirst().orElseThrow(); }
        byte[] original = Files.readAllBytes(prepared);
        assertEquals("APPROVED", bridge.approveOrReuse(context, "sale", 7L, command).mode());
        assertArrayEquals(original, Files.readAllBytes(prepared));
        assertEquals("CONFIRMED", bridge.lookup("stable-child").terminal().outcome());
        login("another-teacher", Set.of("knowledge:publication:sale", "knowledge:question:approve"));
        assertEquals(409, assertThrows(ServiceException.class,
                () -> bridge.approveOrReuse(context, "sale", 7L, command)).getCode());
        verify(mapper, times(1)).changeState(eq(7L), anyLong(), anyString(), anyString());
    }

    @Test void oldReadyApprovalCanBeReusedWithSalePermissionAlone() {
        question.setAnswerHtml("<p>5</p>");
        reviews.act(7L, "APPROVE", command("old-approved", 0L));
        login("seller", Set.of("knowledge:publication:sale"));
        var result = bridge.approveOrReuse(context, "sale", 7L, command("unused-child", 1L));
        assertEquals("REUSED", result.mode());
        assertEquals(KbReviewService.POLICY, result.evidence().policyVersion());
        assertNull(ledger.prepared("unused-child"));
        verify(mapper, times(1)).changeState(eq(7L), anyLong(), anyString(), anyString());
    }

    @Test void draftNeedsBothPermissionsAndRealPublicationValidation() {
        login("seller", Set.of("knowledge:publication:sale"));
        assertEquals(403, assertThrows(ServiceException.class,
                () -> bridge.approveOrReuse(context, "sale", 7L, command("child", 0L))).getCode());
        login("teacher", Set.of("knowledge:publication:sale", "knowledge:question:approve"));
        doThrow(new ServiceException("不属于题单", 409)).when(validator).validateApproval(eq(context), eq("sale"), eq(7L), any());
        assertEquals(409, assertThrows(ServiceException.class,
                () -> bridge.approveOrReuse(context, "sale", 7L, command("child", 0L))).getCode());
        verify(mapper, never()).changeState(anyLong(), anyLong(), anyString(), anyString());
        assertTrue(ledger.operations().isEmpty());
    }

    @Test void absentValidatorAndOuterTransactionFailClosed() {
        var empty = new StaticListableBeanFactory().getBeanProvider(PublicationReviewContextValidator.class);
        var unavailable = new KbPublicationReviewBridge(reviews, ledger, gate, proofs, empty);
        assertEquals(503, assertThrows(ServiceException.class, () -> unavailable.inspect(context, 7L)).getCode());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThrows(IllegalStateException.class, () -> bridge.approveOrReuse(context, "sale", 7L, command("child", 0L)));
        assertTrue(ledger.operations().isEmpty());
    }

    @Test void publicationQuantityExceptionAllowsAbsentAnswerButNotCandidateOrOrdinaryBypass() {
        question.setQuestionType("CALCULATION");
        var ordinary = new ReviewModels.ExceptionCommand("strict-proof", "真实一道计算题", 7L, 0L, null, null, null);
        assertEquals(409, assertThrows(ServiceException.class, () -> proofs.grant(ordinary)).getCode());
        var contextual = new ReviewModels.ExceptionCommand("publication-proof", "真实一道计算题", 7L, 0L, null, null, null, context);
        var proof = proofs.grant(contextual);
        assertEquals("PRD012-QUALITY-1", proof.policyVersion());
        assertEquals(proof, proofs.grant(contextual));
        verify(validator, atLeastOnce()).validateException(context, 7L);
        assertTrue(bridge.inspect(context, 7L).canApprove());
        bridge.approveOrReuse(context, "sale", 7L, command("small-child", 0L));
        assertTrue(reviews.readiness(7L).eligible());
        var candidate = new ReviewModels.ExceptionCommand("candidate", "candidate", null, null, "run", "candidate", "hash", context);
        assertEquals(400, assertThrows(ServiceException.class, () -> proofs.grant(candidate)).getCode());
    }

    @Test void legacyQuantityExceptionIdentityStillMatchesOldRequestSerialization() {
        question.setQuestionType("CALCULATION"); question.setAnswerHtml("<p>5</p>");
        var command = new ReviewModels.ExceptionCommand("old-key", "真实一道题", 7L, 0L, null, null, null);
        var oldShape = new OldExceptionCommand("old-key", "真实一道题", 7L, 0L, null, null, null);
        String expected = KbReviewLedger.hash(JSON.toJSONBytes(List.of("teacher", oldShape)));
        assertEquals(expected, proofs.grant(command).requestHash());
        assertEquals(expected, proofs.grant(command).requestHash());
    }

    @Test void unknownPolicyDoesNotCreateUsableProjection() {
        var before = new ReviewModels.Snapshot("7", "0", "DRAFT", KbQuestionFingerprint.content(question), mapper.facts(7L));
        var after = new ReviewModels.Snapshot("7", "1", "READY", before.contentHash(), before.facts());
        ledger.prepare(new ReviewModels.Prepared("unknown", "a".repeat(64), "APPROVE", "teacher", "test", "UNRECOGNIZED", before, after, null));
        ledger.finish("unknown", "CONFIRMED"); question.setStatus("READY"); question.setRowVersion(1L);
        assertEquals("[]", reviews.approvalJson());
        assertFalse(reviews.readiness(7L).eligible());
    }

    @Test void presentButIncompleteGroupedAnswerStillBlocksPublicationApproval() {
        question.setUnitKind("GROUP");
        question.setStemHtml("<div data-kb-role='group' data-columns='2'><div data-item-key='a'>2+3</div><div data-item-key='b'>3+4</div></div>");
        question.setAnswerHtml("<div data-kb-role='group' data-columns='2'><div data-item-key='a'>5</div><div data-item-key='b'></div></div>");
        assertFalse(bridge.inspect(context, 7L).canApprove());
        assertEquals(409, assertThrows(ServiceException.class,
                () -> bridge.approveOrReuse(context, "sale", 7L, command("incomplete", 0L))).getCode());
        verify(mapper, never()).changeState(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test void uncertainChildReceiptIsNotRetriedOrReportedAsSuccessful() {
        var before = new ReviewModels.Snapshot("7", "0", "DRAFT", KbQuestionFingerprint.content(question), mapper.facts(7L));
        var after = new ReviewModels.Snapshot("7", "1", "READY", before.contentHash(), before.facts());
        ledger.prepare(new ReviewModels.Prepared("uncertain", "a".repeat(64), "APPROVE", "teacher", "test", ReviewPolicies.PUBLICATION, before, after, null));
        question.setRowVersion(8L);
        assertEquals(503, assertThrows(ServiceException.class, () -> bridge.lookup("uncertain")).getCode());
        assertNull(ledger.terminal("uncertain"));
        verify(mapper, never()).changeState(anyLong(), anyLong(), anyString(), anyString());
    }

    private ReviewModels.Command command(String identity, Long version) {
        return new ReviewModels.Command(identity, version, "已核对当前完整内容与真实关联", true, null, proofs.basis(question));
    }

    private static void login(String username, Set<String> permissions) {
        SysUser user = new SysUser(); user.setUserId(42L); user.setUserName(username);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(42L, null, user, permissions), null, List.of()));
    }

    private record OldExceptionCommand(String operationId, String reason, Long questionId,
            Long expectedRowVersion, String runId, String candidateKey, String candidateHash) { }
}
