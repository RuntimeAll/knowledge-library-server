package com.ruoyi.knowledge.publication;

import com.ruoyi.knowledge.mapper.PublicationMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.mapper.KbAgentSubmissionMapper;
import com.ruoyi.knowledge.publication.PublicationModels.*;
import com.ruoyi.knowledge.publication.PublicationRows.*;
import com.ruoyi.knowledge.review.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PublicationSaleRecoveryTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void checkedFileCoursewareSellsWithoutQuestionApprovalPermissionOrReceipts() {
        Fixture f = new Fixture(); f.checked.items.clear();
        reset(f.bridge, f.reviews);
        login("owner", Set.of("knowledge:publication:sale", "knowledge:publication:all"));
        SaleResult result = f.sales.sale(1L, f.command);
        assertEquals("ON_SALE", result.status());
        assertEquals("SUCCEEDED", result.sale().status);
        assertTrue(result.sale().result.questionResults.isEmpty());
        verifyNoInteractions(f.bridge, f.reviews);
        verify(f.shares).generate(1L, 30L);
    }
    @Test void partialApprovalRemainsPreparingAndResumesOnlyMissingChildrenWithSameKey() {
        Fixture f = new Fixture();
        when(f.bridge.approveOrReuse(any(), eq("sale-1"), eq(12L), any()))
                .thenThrow(new ServiceException("ledger unavailable", 503)).thenReturn(approved(12L));
        SaleResult first = f.sales.sale(1L, f.command);
        assertEquals("PREPARING", first.status()); assertNull(first.generation());
        assertEquals("UNCERTAIN", first.sale().status); assertEquals(1, first.sale().result.questionResults.size());
        verifyNoInteractions(f.shares);
        SaleResult recovered = f.sales.sale(1L, f.command);
        assertEquals("ON_SALE", recovered.status()); assertEquals("SUCCEEDED", recovered.sale().status);
        assertEquals(2, recovered.sale().result.questionResults.size());
        verify(f.bridge, times(1)).approveOrReuse(any(), eq("sale-1"), eq(11L), any());
        verify(f.bridge, times(2)).approveOrReuse(any(), eq("sale-1"), eq(12L), any());
        verify(f.shares).generate(1L, 30L);
    }
    @Test void differentActorCannotContinueOrReplacePendingSale() {
        Fixture f = new Fixture();
        when(f.bridge.approveOrReuse(any(), eq("sale-1"), eq(12L), any()))
                .thenThrow(new ServiceException("ledger unavailable", 503));
        f.sales.sale(1L, f.command); reset(f.bridge);
        login("other", Set.of("knowledge:publication:sale", "knowledge:publication:all"));
        assertEquals(403, assertThrows(PublicationException.class, () -> f.sales.sale(1L, f.command)).getCode());
        verifyNoInteractions(f.bridge, f.shares);
    }
    @Test void reusedUsernameDoesNotImpersonateTheOriginalAccount() {
        Fixture f = new Fixture();
        when(f.bridge.approveOrReuse(any(), eq("sale-1"), eq(12L), any())).thenThrow(new ServiceException("pending", 503));
        f.sales.sale(1L, f.command); reset(f.bridge);
        SysUser replacement = new SysUser(); replacement.setUserId(78L); replacement.setUserName("owner");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(78L, null, replacement, Set.of("knowledge:publication:sale", "knowledge:publication:all")), null, List.of()));
        assertEquals(403, assertThrows(PublicationException.class, () -> f.sales.sale(1L, f.command)).getCode());
        verifyNoInteractions(f.bridge, f.shares);
    }
    @Test void staleDependencyBlocksBeforeAnyChildApproval() {
        Fixture f = new Fixture(); f.checked.dependencyHash = "changed";
        assertEquals(409, assertThrows(PublicationException.class, () -> f.sales.sale(1L, f.command)).getCode());
        verifyNoInteractions(f.bridge, f.shares); verify(f.mapper, never()).insertPublicationOperation(any());
    }
    @Test void reconciliationRequiresItsOwnAuditReasonWithoutBorrowingSaleReason() {
        Fixture f = new Fixture();
        login("owner", Set.of("knowledge:publication:query", "knowledge:publication:sale", "knowledge:publication:all"));
        Command reconcile = new Command(); reconcile.operationKey = "reconcile-1";
        assertEquals(400, assertThrows(PublicationException.class, () -> f.sales.reconcileAndClose(1L, 30L, reconcile)).getCode());
        reconcile.reason = " ";
        assertEquals(400, assertThrows(PublicationException.class, () -> f.sales.reconcileAndClose(1L, 30L, reconcile)).getCode());
        verifyNoInteractions(f.bridge);
    }
    private static ReviewModels.PublicationApprovalResult approved(Long id) {
        var readiness = new ReviewModels.Readiness(id.toString(), "1", "READY", true, false,
                "PRD012-QUALITY-1", List.of(), null, null, "a".repeat(64), "basis");
        var evidence = new ReviewModels.ApprovalEvidence("approval-" + id, id.toString(), "1", "a".repeat(64),
                "PRD012-QUALITY-1", "owner", "reviewed", null, List.of(), List.of());
        return new ReviewModels.PublicationApprovalResult("APPROVED", readiness, evidence);
    }
    static void login(String name, Set<String> permissions) {
        SysUser user = new SysUser(); user.setUserId(77L); user.setUserName(name);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(77L, null, user, permissions), null, List.of()));
    }
    static class Fixture {
        final PublicationMapper mapper = mock(PublicationMapper.class);
        final PublicationCheckService checks = mock(PublicationCheckService.class);
        final KbPublicationReviewBridge bridge = mock(KbPublicationReviewBridge.class);
        final KbReviewService reviews = mock(KbReviewService.class);
        final PublicationShareService shares = mock(PublicationShareService.class);
        final PublicationSaleService sales;
        final Publication row = new Publication(); final Command command = new Command();
        final CheckResult checked = new CheckResult();
        Fixture() {
            login("owner", Set.of("knowledge:publication:sale", "knowledge:publication:all", "knowledge:question:approve"));
            PublicationProperties props = new PublicationProperties(); props.setEnabled(true);
            PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
            when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
            PublicationSupport support = new PublicationSupport(manager, new KbReviewGate(), props);
            PublicationGraph graph = mock(PublicationGraph.class);
            PublicationService service = new PublicationService(mapper, support, graph, mock(KbAgentSubmissionMapper.class));
            sales = new PublicationSaleService(service, checks, bridge, reviews, shares);
            row.id = 1L; row.createBy = "owner"; row.status = "PREPARING"; row.rowVersion = 0L; row.contentVersion = 1L; row.shareState = "NOT_CREATED";
            when(mapper.get(1L)).thenReturn(row); when(mapper.lock(1L)).thenReturn(row);
            when(mapper.updatePublication(any())).thenReturn(1); when(mapper.updatePublicationOperation(any())).thenReturn(1);
            AtomicReference<PublicationOperation> operation = new AtomicReference<>();
            when(mapper.operation(eq(1L), eq("sale-1"))).thenAnswer(i -> operation.get());
            doAnswer(i -> { PublicationOperation op = i.getArgument(0); op.id = 30L; operation.set(op); return 1; }).when(mapper).insertPublicationOperation(any());
            PublicationCheck stored = new PublicationCheck(); stored.id = 20L; stored.contentVersion = 1L; stored.status = "PASSED"; stored.dependencyHash = "original";
            when(mapper.getCheck(1L, 20L)).thenReturn(stored);
            checked.status = "PASSED"; checked.dependencyHash = "original";
            for (long id : List.of(11L, 12L)) {
                QuestionCheck q = new QuestionCheck(); q.questionId = id; q.observedRowVersion = 0L; q.contentHash = "a".repeat(64); checked.items.add(q);
                when(reviews.readiness(id)).thenReturn(new ReviewModels.Readiness(Long.toString(id), "0", "DRAFT", false, true, "PRD012-QUALITY-1", List.of(), null, null, q.contentHash, null));
                when(bridge.approveOrReuse(any(), eq("sale-1"), eq(id), any())).thenReturn(approved(id));
            }
            when(checks.evaluate(row, true)).thenReturn(checked); when(checks.evaluate(row, false)).thenReturn(checked);
            when(graph.read(row)).thenReturn(new Input());
            command.operationKey = "sale-1"; command.expectedRowVersion = 0L; command.contentVersion = 1L;
            command.checkId = 20L; command.reason = "核实完整题单后发售";
        }
    }
}
