package com.ruoyi.knowledge.importing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicReference;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.knowledge.html.KbHtmlCodec;
import com.ruoyi.knowledge.importing.domain.KbImportBatch;
import com.ruoyi.knowledge.importing.domain.KbImportItem;
import com.ruoyi.knowledge.importing.domain.KbImportResource;
import com.ruoyi.knowledge.importing.mapper.KbImportMapper;
import com.ruoyi.knowledge.service.IKbSourceService;
import com.ruoyi.knowledge.storage.IKbAssetService;
import com.ruoyi.knowledge.storage.domain.KbAsset;
import com.ruoyi.knowledge.storage.KbAssetServiceImpl;
import com.ruoyi.knowledge.service.IKbQuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockMultipartFile;

class KbImportServiceTest {
    private KbImportMapper mapper;
    private KbHtmlAssetResolver resolver;
    private KbImportItemWorker worker;
    private IKbAssetService assets;
    private KbImportServiceImpl service;
    private KbImportLeaseManager leases;
    private final AtomicReference<KbImportBatch> stored = new AtomicReference<>();
    private final List<KbImportItem> items = new ArrayList<>();
    private final List<KbImportResource> resources = new ArrayList<>();
    private final Map<Long, byte[]> storedBytes = new HashMap<>();
    private final Map<Long, KbAsset> storedAssets = new HashMap<>();

    @BeforeEach
    void setUp() {
        mapper = mock(KbImportMapper.class);
        resolver = mock(KbHtmlAssetResolver.class);
        worker = mock(KbImportItemWorker.class);
        assets = mock(IKbAssetService.class);
        leases = mock(KbImportLeaseManager.class);
        when(leases.acquire(any())).thenAnswer(invocation -> {
            KbImportBatch batch = invocation.getArgument(0);
            batch.setClaimToken("lease-token");
            return mock(KbImportLeaseManager.Lease.class);
        });
        service = new KbImportServiceImpl(mapper, new KbHtmlCodec(), resolver, worker, assets, mock(IKbSourceService.class), leases);
        when(mapper.selectInstanceId()).thenReturn("current-instance");
        when(mapper.selectByKey(eq("agent"), anyString())).thenAnswer(i -> stored.get());
        doAnswer(i -> { KbImportBatch batch = i.getArgument(0); batch.setBatchId(1L); stored.set(batch); return 1; }).when(mapper).insertBatch(any());
        when(mapper.selectBatch(1L)).thenAnswer(i -> stored.get());
        when(mapper.selectItems(1L)).thenReturn(items);
        when(mapper.selectResources(1L)).thenReturn(resources);
        when(mapper.updateBatch(any())).thenReturn(1);
        doAnswer(i -> { items.add(i.getArgument(0)); return 1; }).when(mapper).insertItem(any());
        doAnswer(i -> { items.add(i.getArgument(1)); return null; }).when(worker).recordFailure(any(), any());
        doAnswer(i -> { resources.add(i.getArgument(1)); return null; }).when(worker).saveResource(any(), any());
        when(worker.write(any(), anyInt(), nullable(ResolvedArticle.class))).thenAnswer(i -> success(i.getArgument(1)));
        when(assets.uploadBytes(any(), anyString(), anyString(), anyString(), nullable(String.class), anyString())).thenAnswer(i -> {
            byte[] bytes = i.getArgument(0);
            KbAsset asset = new KbAsset();
            asset.setAssetId(10L + storedAssets.size());
            asset.setFileSize((long) bytes.length);
            asset.setSha256(KbAssetServiceImpl.sha256(bytes));
            storedAssets.put(asset.getAssetId(), asset);
            storedBytes.put(asset.getAssetId(), bytes);
            return asset;
        });
        when(assets.requireReady(anyLong())).thenAnswer(i -> storedAssets.get(i.getArgument(0)));
        when(assets.openContent(anyLong())).thenAnswer(i -> new ByteArrayInputStream(storedBytes.get(i.getArgument(0))));
    }

    @Test
    void partialBatchPreservesSuccessfulItemAndReportsFailedItem() {
        String html = head() + "<article data-client-key='ok' data-intent='create'><section data-part='stem'>1+1</section></article>"
                + "<article data-client-key='bad' data-intent='unknown'><section data-part='stem'>x</section></article>";
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("agent");
            KbImportBatch result = service.importHtml(file(html), List.of(), "key-1", null, null);
            assertEquals("PARTIAL", result.getStatus());
            assertEquals(1, result.getSuccessCount());
            assertEquals(1, result.getFailureCount());
            verify(worker, times(1)).write(any(), eq(0), nullable(ResolvedArticle.class));
            assertEquals("FAILED", items.get(1).getStatus());
            assertTrue(items.get(1).getMessage().contains("data-intent"));
        }
    }

    @Test
    void retryReturnsReceiptWithoutUploadingAgainAndRejectsDifferentContent() {
        String html = head() + "<article data-client-key='q' data-intent='create'><section data-part='stem'>1+1</section></article>";
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("agent");
            KbImportBatch first = service.importHtml(file(html), List.of(), "key-1", null, null);
            KbImportBatch repeated = service.importHtml(file(html), List.of(), "key-1", null, null);
            assertEquals(first.getBatchId(), repeated.getBatchId());
            verify(assets, times(1)).uploadBytes(any(), anyString(), eq("text/html"), eq("IMPORT_HTML"), nullable(String.class), anyString());
            assertThrows(ServiceException.class, () -> service.importHtml(file(html.replace("1+1", "2+2")), List.of(), "key-1", null, null));
        }
    }

    @Test
    void rejectsCrossInstanceUpdateBeforeTouchingQuestion() {
        String html = head() + "<meta name='kb-origin' content='another-instance'><article data-client-key='q' data-intent='update' data-question-id='1' data-row-version='1'><section data-part='stem'>2+2</section></article>";
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("agent");
            KbImportBatch result = service.importHtml(file(html), List.of(), "key-1", null, null);
            assertEquals("FAILED", result.getStatus());
            verify(worker, never()).write(any(), anyInt(), any());
            verifyNoInteractions(resolver);
            assertTrue(items.get(0).getMessage().contains("kb-origin"));
        }
    }

    @Test
    void resumeReloadsInputsAndSkipsBothSuccessfulAndFailedReceipts() {
        String html = head() + question("first", "create") + question("invalid", "unknown") + question("last", "create");
        MockMultipartFile image = new MockMultipartFile("resources", "figure.png", "image/png", new byte[]{1, 2, 3});
        doThrow(new AssertionError("simulated process death")).when(worker).write(any(), eq(2), nullable(ResolvedArticle.class));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("agent");
            assertThrows(AssertionError.class, () -> service.importHtml(file(html), List.of(image), "interrupted", null, null));
            assertEquals("PROCESSING", stored.get().getStatus());
            assertTrue(stored.get().getInputReady());
            assertEquals(2, items.size());
            assertEquals(1, resources.size());
            doAnswer(i -> success(2)).when(worker).write(any(), eq(2), nullable(ResolvedArticle.class));
            clearInvocations(worker, resolver, assets);
            KbImportBatch result = service.resume(1L, null);
            assertEquals("PARTIAL", result.getStatus());
            assertEquals(2, result.getSuccessCount());
            assertEquals(1, result.getFailureCount());
            verify(worker, never()).write(any(), eq(0), any());
            verify(worker, never()).recordFailure(any(), any());
            verify(worker, times(1)).write(any(), eq(2), nullable(ResolvedArticle.class));
            verify(assets, never()).uploadBytes(any(), anyString(), anyString(), anyString(), nullable(String.class), anyString());
            verify(resolver).resolve(any(), argThat(values -> values.size() == 1
                    && values.get(0).resourceKey().equals("figure.png")
                    && java.util.Arrays.equals(values.get(0).bytes(), new byte[]{1, 2, 3})), isNull());
        }
    }

    @Test
    void activeLeaseRejectsResumeBeforeReadingAssets() {
        prepareInterruptedBatch();
        doThrow(new ServiceException("批次仍在执行", 409)).when(leases).acquire(any());
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("agent");
            assertEquals(409, assertThrows(ServiceException.class, () -> service.resume(1L, null)).getCode());
            verify(assets, never()).openContent(anyLong());
            verifyNoInteractions(worker);
        }
    }

    @Test
    void receivingBatchRequiresOriginalFilesRatherThanPretendingItCanResume() {
        prepareInterruptedBatch();
        stored.get().setInputReady(false);
        stored.get().setStatus("RECEIVING");
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("agent");
            assertTrue(assertThrows(ServiceException.class, () -> service.resume(1L, null)).getMessage().contains("原文件重新提交"));
            verifyNoInteractions(leases, worker);
        }
    }

    @Test
    void resumeRejectsChangedDurableBytesWithoutWritingAnyQuestion() {
        String html = head() + question("last", "create");
        doThrow(new AssertionError("process death")).when(worker).write(any(), eq(0), nullable(ResolvedArticle.class));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("agent");
            assertThrows(AssertionError.class, () -> service.importHtml(file(html), List.of(), "key", null, null));
            storedBytes.put(10L, "changed".getBytes(StandardCharsets.UTF_8));
            clearInvocations(worker);
            assertTrue(assertThrows(ServiceException.class, () -> service.resume(1L, null)).getMessage().contains("哈希"));
            verifyNoInteractions(worker);
            assertEquals("PROCESSING", stored.get().getStatus());
        }
    }

    @Test
    void staleWorkerCannotWriteQuestionOrFailureReceipt() {
        KbImportBatch batch = prepareInterruptedBatch();
        IKbQuestionService questions = mock(IKbQuestionService.class);
        KbImportItemWorker fenced = new KbImportItemWorker(questions, mock(IKbSourceService.class), mapper, new KbHtmlCodec());
        assertThrows(ServiceException.class, () -> fenced.write(batch, 0, null));
        assertThrows(ServiceException.class, () -> fenced.recordFailure(batch, new KbImportItem()));
        verifyNoInteractions(questions);
        verify(mapper, never()).insertItem(any());
    }

    @Test
    void receivingRetryPersistsAllResourcesBeforeFirstQuestionTransaction() {
        String html = head() + question("one", "create");
        MockMultipartFile first = new MockMultipartFile("resources", "first.png", "image/png", new byte[]{1});
        MockMultipartFile second = new MockMultipartFile("resources", "second.png", "image/png", new byte[]{2});
        doThrow(new ServiceException("simulated storage outage"))
                .when(assets).uploadBytes(any(), eq("second.png"), anyString(), anyString(), isNull(), anyString());
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("agent");
            assertThrows(ServiceException.class, () -> service.importHtml(file(html), List.of(first, second), "receive", null, null));
            assertEquals("RECEIVING", stored.get().getStatus());
            assertFalse(stored.get().getInputReady());
            assertEquals(1, resources.size());
            verify(worker, never()).write(any(), anyInt(), any());
            doAnswer(invocation -> {
                byte[] bytes = invocation.getArgument(0);
                KbAsset asset = new KbAsset();
                asset.setAssetId(12L);
                asset.setSha256(KbAssetServiceImpl.sha256(bytes));
                asset.setFileSize((long) bytes.length);
                return asset;
            }).when(assets).uploadBytes(any(), eq("second.png"), anyString(), anyString(), isNull(), anyString());
            doAnswer(invocation -> {
                assertTrue(stored.get().getInputReady());
                assertEquals(2, resources.size());
                assertEquals("PROCESSING", stored.get().getStatus());
                return success(0);
            }).when(worker).write(any(), eq(0), nullable(ResolvedArticle.class));
            assertEquals("SUCCESS", service.importHtml(file(html), List.of(first, second), "receive", null, null).getStatus());
            verify(assets, times(1)).uploadBytes(any(), eq("questions.html"), anyString(), anyString(), isNull(), anyString());
            verify(assets, times(1)).uploadBytes(any(), eq("first.png"), anyString(), anyString(), isNull(), anyString());
        }
    }

    @Test
    void leaseManagerRejectsFailedAtomicClaim() {
        KbImportBatch batch = prepareInterruptedBatch();
        KbImportLeaseManager manager = new KbImportLeaseManager(mapper);
        try {
            when(mapper.claim(eq(1L), anyString())).thenReturn(0);
            assertEquals(409, assertThrows(ServiceException.class, () -> manager.acquire(batch)).getCode());
            verify(mapper, never()).renewClaim(anyLong(), anyString());
        } finally {
            manager.close();
        }
    }

    private KbImportBatch prepareInterruptedBatch() {
        KbImportBatch batch = new KbImportBatch();
        batch.setBatchId(1L);
        batch.setCreateBy("agent");
        batch.setStatus("PROCESSING");
        batch.setInputReady(true);
        batch.setClaimToken("old-token");
        stored.set(batch);
        return batch;
    }

    private KbImportItem success(int order) {
        KbImportItem item = new KbImportItem();
        item.setBatchId(1L);
        item.setItemOrder(order);
        item.setStatus("SUCCESS");
        item.setQuestionId(100L + order);
        items.add(item);
        return item;
    }

    private String question(String key, String intent) {
        return "<article data-client-key='" + key + "' data-intent='" + intent + "'><section data-part='stem'>1+1</section></article>";
    }

    private String head() { return "<meta name='kb-schema' content='kb-question-html/v1'>"; }
    private MockMultipartFile file(String html) {
        return new MockMultipartFile("file", "questions.html", "text/html", html.getBytes(StandardCharsets.UTF_8));
    }
}
