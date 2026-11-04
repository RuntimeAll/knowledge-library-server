package com.ruoyi.knowledge.publication;

import com.ruoyi.knowledge.mapper.PublicationMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ruoyi.knowledge.mapper.KbAgentSubmissionMapper;
import com.ruoyi.knowledge.publication.PublicationModels.*;
import com.ruoyi.knowledge.publication.PublicationRows.*;
import com.ruoyi.knowledge.review.KbReviewGate;
import com.ruoyi.knowledge.storage.IKbAssetService;
import com.ruoyi.knowledge.storage.mapper.KbAssetMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PublicationPublicGateTest {
    @Test void temporaryDependencyFailureDoesNotPermanentlyDisableShare() {
        Fixture f = new Fixture(); when(f.checks.evaluate(eq(f.row), any(PublicationOperation.class))).thenThrow(new IllegalStateException("temporary"));
        PublicationException error = assertThrows(PublicationException.class, () -> f.publicApi.get(f.row.shareCode));
        assertEquals(503, error.getCode()); verify(f.mapper, never()).updatePublication(any());
    }
    @Test void provenDependencyChangeDisablesShareWithoutPublishingChangedContent() {
        Fixture f = new Fixture(); CheckResult changed = new CheckResult(); changed.status = "PASSED"; changed.dependencyHash = "changed";
        when(f.checks.evaluate(eq(f.row), any(PublicationOperation.class))).thenReturn(changed); when(f.mapper.lock(1L)).thenReturn(f.row);
        when(f.mapper.updatePublication(f.row)).thenReturn(1);
        assertEquals(404, assertThrows(PublicationException.class, () -> f.publicApi.get(f.row.shareCode)).getCode());
        assertEquals("DISABLED", f.row.shareState); verify(f.mapper).updatePublication(f.row);
        verifyNoInteractions(f.assets);
    }
    @Test void preparingShareCannotReadGraphEvenWithOldReadyAsset() {
        Fixture f = new Fixture(); f.row.status = "PREPARING";
        assertEquals(404, assertThrows(PublicationException.class, () -> f.publicApi.get(f.row.shareCode)).getCode());
        verifyNoInteractions(f.checks); verifyNoInteractions(f.assets);
    }
    @Test void publicHtmlKeepsOnlyScopedProxyImagesAndRemovesExecutableContent() {
        Fixture f = new Fixture();
        String html = f.publicApi.sanitize("<script>alert(1)</script><img data-asset-id='7' src='https://external.invalid/x' onerror='x()'><img data-asset-id='8' src='x'><a href='javascript:alert(1)'>label</a>", f.row, java.util.Set.of(7L));
        assertTrue(html.contains("/public/publications/" + f.row.shareCode + "/assets/7/content?contentVersion=1"));
        assertFalse(html.contains("script")); assertFalse(html.contains("onerror")); assertFalse(html.contains("external.invalid"));
        assertFalse(html.contains("assets/8")); assertFalse(html.contains("javascript:"));
    }
    static class Fixture {
        final PublicationMapper mapper = mock(PublicationMapper.class);
        final PublicationReadGate checks = mock(PublicationReadGate.class);
        final IKbAssetService assets = mock(IKbAssetService.class);
        final PublicationGraph graph = mock(PublicationGraph.class);
        final Publication row = new Publication();
        final PublicationPublicService publicApi;
        Fixture() {
            PublicationProperties properties = new PublicationProperties(); properties.setEnabled(true);
            PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
            when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
            PublicationSupport support = new PublicationSupport(manager, new KbReviewGate(), properties);
            PublicationService service = new PublicationService(mapper, support, graph, mock(KbAgentSubmissionMapper.class));
            row.id = 1L; row.rowVersion = 1L; row.contentVersion = 1L; row.sharedContentVersion = 1L;
            row.status = "ON_SALE"; row.shareState = "READY"; row.shareCode = "a".repeat(32); row.lastSaleOperationId = 3L;
            when(mapper.byCode(row.shareCode)).thenReturn(row);
            PublicationOperation op = new PublicationOperation(); op.id = 3L; op.action = "SALE"; op.status = "SUCCEEDED";
            CheckResult result = new CheckResult(); result.status = "PASSED"; result.dependencyHash = "original";
            op.snapshotJson = support.encode(new Snapshot(new Input(), result));
            when(mapper.getOperation(1L, 3L)).thenReturn(op);
            publicApi = new PublicationPublicService(service, checks, assets, mock(KbAssetMapper.class));
        }
    }
}
