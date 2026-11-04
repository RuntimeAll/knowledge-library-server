package com.ruoyi.knowledge.publication;

import com.ruoyi.knowledge.mapper.PublicationMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.mapper.KbAgentSubmissionMapper;
import com.ruoyi.knowledge.publication.PublicationModels.*;
import com.ruoyi.knowledge.publication.PublicationRows.*;
import com.ruoyi.knowledge.review.KbReviewGate;
import com.ruoyi.knowledge.storage.IKbAssetService;
import com.ruoyi.knowledge.storage.domain.KbAsset;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PublicationShareRecoveryTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void interruptedUploadUsesIdenticalCodeBytesAndOperationKeyOnRetry() {
        PublicationSaleRecoveryTest.login("owner", Set.of("knowledge:publication:all", "knowledge:publication:shareRetry"));
        var mapper = mock(PublicationMapper.class); var checks = mock(PublicationCheckService.class);
        var assets = mock(IKbAssetService.class); var graph = mock(PublicationGraph.class);
        var properties = new PublicationProperties(); properties.setEnabled(true);
        properties.setPublicBaseUrl("https://publication.invalid"); properties.setPageTemplateAssetId(99L);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        var support = new PublicationSupport(manager, new KbReviewGate(), properties);
        var service = new PublicationService(mapper, support, graph, mock(KbAgentSubmissionMapper.class));
        var share = new PublicationShareService(service, checks, assets, properties, new PublicationBundleService(service, assets));
        Publication row = new Publication(); row.id = 1L; row.rowVersion = 0L; row.contentVersion = 1L;
        row.status = "ON_SALE"; row.lastSaleOperationId = 2L; row.shareState = "NOT_CREATED";
        when(mapper.get(1L)).thenReturn(row); when(mapper.lock(1L)).thenReturn(row);
        when(mapper.updatePublication(any())).thenReturn(1); when(mapper.updatePublicationOperation(any())).thenReturn(1);
        CheckResult passed = new CheckResult(); passed.status = "PASSED"; passed.dependencyHash = "same";
        when(checks.evaluate(row, false)).thenReturn(passed);
        PublicationOperation sale = new PublicationOperation(); sale.id = 2L; sale.action = "SALE"; sale.status = "SUCCEEDED";
        sale.contentVersion = 1L; sale.snapshotJson = support.encode(new Snapshot(new Input(), passed));
        when(mapper.getOperation(1L, 2L)).thenReturn(sale);
        AtomicReference<PublicationOperation> generated = new AtomicReference<>();
        when(mapper.operation(1L, "generate:2")).thenAnswer(i -> generated.get());
        doAnswer(i -> { PublicationOperation op = i.getArgument(0); op.id = 3L; generated.set(op); return 1; }).when(mapper).insertPublicationOperation(any());
        byte[] template = "<html><head></head><body></body></html>".getBytes(StandardCharsets.UTF_8);
        KbAsset shell = new KbAsset(); shell.setAssetId(99L); shell.setFileSize((long) template.length);
        when(assets.requireReady(99L)).thenReturn(shell);
        when(assets.openContent(99L)).thenAnswer(i -> new ByteArrayInputStream(template));
        KbAsset output = new KbAsset(); output.setAssetId(100L);
        List<byte[]> attempts = new ArrayList<>();
        when(assets.uploadBytes(any(), eq("publication-1.html"), eq("text/html"), eq("IMPORT_HTML"), isNull(), eq("publication-page-3")))
                .thenAnswer(i -> { attempts.add(i.getArgument(0)); if (attempts.size() == 1) { throw new ServiceException("upload uncertain"); } return output; });
        PublicationOperation first = share.generate(1L, 2L);
        assertEquals("FAILED", first.status); assertEquals("ON_SALE", row.status);
        assertEquals("FAILED", row.shareState); assertNotNull(row.shareCode); String code = row.shareCode;
        Command command = new Command(); command.operationKey = "generate:2"; command.expectedRowVersion = 0L;
        command.contentVersion = 1L; command.saleOperationId = 2L;
        PublicationOperation retry = share.retry(1L, command);
        assertEquals("SUCCEEDED", retry.status); assertEquals(code, row.shareCode); assertEquals("READY", row.shareState);
        assertArrayEquals(attempts.get(0), attempts.get(1)); assertEquals(2, retry.attemptCount);
        verify(mapper, times(1)).insertPublicationOperation(any());
    }
}
