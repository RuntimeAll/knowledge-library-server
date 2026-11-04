package com.ruoyi.knowledge.publication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ruoyi.knowledge.mapper.KbAgentSubmissionMapper;
import com.ruoyi.knowledge.mapper.PublicationMapper;
import com.ruoyi.knowledge.publication.PublicationModels.*;
import com.ruoyi.knowledge.publication.PublicationRows.*;
import com.ruoyi.knowledge.review.KbReviewGate;
import com.ruoyi.knowledge.storage.IKbAssetService;
import com.ruoyi.knowledge.storage.KbAssetServiceImpl;
import com.ruoyi.knowledge.storage.domain.KbAsset;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PublicationDownloadTest {
    PublicationProperties properties;
    PublicationMapper mapper;
    IKbAssetService assets;
    PublicationService service;
    PublicationReadGate gate;
    Publication row;
    PublicationOperation sale;
    CheckResult passed;
    Input input;
    byte[] image = {1, 2, 3, 4};
    @BeforeEach void setup() {
        PublicationSaleRecoveryTest.login("owner", Set.of("knowledge:publication:query", "knowledge:publication:all", "knowledge:publication:shareRetry"));
        properties = new PublicationProperties(); properties.setEnabled(true); properties.setDeliveryMode(PublicationProperties.DeliveryMode.OSS_DOWNLOAD);
        mapper = mock(PublicationMapper.class); assets = mock(IKbAssetService.class); gate = mock(PublicationReadGate.class);
        var tx = mock(PlatformTransactionManager.class); when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new PublicationService(mapper, new PublicationSupport(tx, new KbReviewGate(), properties), mock(PublicationGraph.class), mock(KbAgentSubmissionMapper.class));
        row = new Publication(); row.id = 1L; row.rowVersion = 2L; row.contentVersion = 1L; row.sharedContentVersion = 1L;
        row.status = "ON_SALE"; row.shareState = "READY"; row.shareAssetId = 8L; row.lastSaleOperationId = 2L; row.createBy = "owner";
        when(mapper.get(1L)).thenReturn(row); when(mapper.lock(1L)).thenReturn(row); when(mapper.updatePublication(any())).thenReturn(1);
        when(mapper.updatePublicationOperation(any())).thenReturn(1);
        input = new Input(); input.title = "<script>alert(1)</script>"; input.postTitle = "标题完整"; input.postBody = "line1\nline2\n";
        input.listingDescription = "商品描述"; input.products = List.of(); input.referenceSources = List.of(); input.baiduShares = List.of();
        ImageInput img = new ImageInput(); img.assetId = 7L; img.sortOrder = 1; img.caption = "\"><img onerror=alert(1)>"; input.images = List.of(img);
        passed = new CheckResult(); passed.status = "PASSED"; passed.dependencyHash = "same";
        sale = new PublicationOperation(); sale.id = 2L; sale.contentVersion = 1L; sale.action = "SALE"; sale.status = "SUCCEEDED";
        sale.snapshotJson = service.support.encode(new Snapshot(input, passed)); when(mapper.getOperation(1L, 2L)).thenReturn(sale);
        when(gate.evaluate(row, sale)).thenReturn(passed);
        KbAsset png = asset(7L, "image/png", "IMPORT_RESOURCE", image); when(assets.requireReady(7L)).thenReturn(png);
        when(assets.openContent(7L)).thenAnswer(i -> new ByteArrayInputStream(image));
        when(mapper.relationRefs(any())).thenReturn(List.of(relation("PRODUCT", 20L), relation("QUESTION", 21L), relation("SOURCE", 22L), relation("KP", 23L), relation("OUTLINE", 24L)));
        KbAsset archive = asset(8L, "application/zip", "IMPORT_RESOURCE", new byte[]{8}); when(assets.requireReady(8L)).thenReturn(archive);
        when(assets.publicationDownload(8L)).thenReturn(new IKbAssetService.Preview("https://oss.invalid/private?signature=example", 600, "application/zip"));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void deterministicOfflinePackagePreservesBytesTextAndFiveRelationTypes() throws Exception {
        var bundles = new PublicationBundleService(service, assets); Snapshot snapshot = new Snapshot(input, passed);
        byte[] first = bundles.build(1L, 1L, snapshot), second = bundles.build(1L, 1L, snapshot); assertArrayEquals(first, second);
        Map<String, byte[]> entries = unzip(first); assertEquals(7, entries.size()); assertArrayEquals(image, entries.get("images/001.png"));
        assertEquals(input.postBody, new String(entries.get("正文.txt"), StandardCharsets.UTF_8));
        String html = new String(entries.get("index.html"), StandardCharsets.UTF_8);
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;")); assertFalse(html.contains("<img onerror="));
        assertFalse(html.contains("fetch(")); assertFalse(html.contains("/public/")); assertTrue(html.contains("src=\"images/001.png\""));
        assertTrue(html.contains("-webkit-line-clamp:4")); assertTrue(html.contains("id=body class=collapsed"));
        assertTrue(html.contains("id=expand class=expand aria-expanded=false hidden")); assertTrue(html.contains(".textContent"));
        String manifest = new String(entries.get("manifest.json"), StandardCharsets.UTF_8);
        for (String type : List.of("PRODUCT", "QUESTION", "SOURCE", "KP", "OUTLINE")) { assertTrue(manifest.contains("\"objectType\":\"" + type + "\"")); }
    }
    @Test void oversizeAndChangedImageFailBeforeUpload() {
        KbAsset png = assets.requireReady(7L); png.setFileSize((long) PublicationBundleService.MAX_BYTES + 1);
        var bundles = new PublicationBundleService(service, assets);
        assertEquals("BUNDLE_TOO_LARGE", assertThrows(PublicationException.class, () -> bundles.build(1L, 1L, new Snapshot(input, passed))).getDetail().errorCode());
        png.setFileSize((long) image.length); png.setSha256("different");
        assertEquals("DEPENDENCY_CHANGED", assertThrows(PublicationException.class, () -> bundles.build(1L, 1L, new Snapshot(input, passed))).getDetail().errorCode());
        verify(assets, never()).uploadBytes(any(), any(), any(), any(), any(), any());
    }
    @Test void downloadSignsCurrentBundleOnlyAndNeverCachesUrlInOperationResult() {
        var downloads = new PublicationDownloadService(service, gate, assets);
        Download result = downloads.issue(1L); assertEquals(600, result.expiresInSeconds()); assertEquals("8", result.assetId());
        assertNull(service.shareUrl(row)); assertEquals("OSS_DOWNLOAD", service.result(row).deliveryMode); assertNull(service.result(row).shareUrl);
        row.status = "PREPARING"; assertThrows(PublicationException.class, () -> downloads.issue(1L));
        row.status = "DELETED"; assertThrows(PublicationException.class, () -> downloads.issue(1L));
        row.status = "ON_SALE"; row.sharedContentVersion = 0L; assertThrows(PublicationException.class, () -> downloads.issue(1L));
        verify(assets, times(1)).publicationDownload(8L);
    }
    @Test void signingRejectsChangedDependenciesOtherOwnersWrongAssetsAndPublicPageMode() {
        var downloads = new PublicationDownloadService(service, gate, assets);
        CheckResult changed = new CheckResult(); changed.status = "PASSED"; changed.dependencyHash = "changed"; when(gate.evaluate(row, sale)).thenReturn(changed);
        assertEquals("DEPENDENCY_CHANGED", assertThrows(PublicationException.class, () -> downloads.issue(1L)).getDetail().errorCode());
        when(gate.evaluate(row, sale)).thenReturn(passed); assets.requireReady(8L).setContentType("text/html");
        assertThrows(PublicationException.class, () -> downloads.issue(1L));
        properties.setDeliveryMode(PublicationProperties.DeliveryMode.PUBLIC_PAGE); assertThrows(PublicationException.class, () -> downloads.issue(1L));
        properties.setDeliveryMode(PublicationProperties.DeliveryMode.OSS_DOWNLOAD);
        PublicationSaleRecoveryTest.login("other", Set.of("knowledge:publication:query")); assertThrows(PublicationException.class, () -> downloads.issue(1L));
        verify(assets, never()).publicationDownload(any());
    }
    @Test void noDomainOrTemplateGenerationAndRetryKeepSameZipBytesAndAsset() {
        var checks = mock(PublicationCheckService.class); when(checks.evaluate(row, false)).thenReturn(passed);
        var shares = new PublicationShareService(service, checks, assets, properties, new PublicationBundleService(service, assets));
        AtomicReference<PublicationOperation> operation = new AtomicReference<>(); when(mapper.operation(1L, "generate:2")).thenAnswer(i -> operation.get());
        doAnswer(i -> { PublicationOperation op = i.getArgument(0); op.id = 3L; operation.set(op); return 1; }).when(mapper).insertPublicationOperation(any());
        List<byte[]> uploads = new ArrayList<>(); when(assets.uploadBytes(any(), eq("publication-1-v1.zip"), eq("application/zip"), eq("IMPORT_RESOURCE"), isNull(), eq("publication-bundle-3")))
                .thenAnswer(i -> { uploads.add(i.getArgument(0)); if (uploads.size() == 1) throw new IllegalStateException("uncertain"); return assets.requireReady(8L); });
        assertEquals("FAILED", shares.generate(1L, 2L).status);
        PublicationSaleRecoveryTest.login("other", Set.of("knowledge:publication:all", "knowledge:publication:shareRetry"));
        assertEquals(403, assertThrows(PublicationException.class, () -> shares.generate(1L, 2L)).getCode());
        assertEquals(1, uploads.size());
        Command retry = new Command(); retry.operationKey = "generate:2"; retry.saleOperationId = 2L;
        assertEquals(403, assertThrows(PublicationException.class, () -> shares.retry(1L, retry)).getCode());
        PublicationSaleRecoveryTest.login("owner", Set.of("knowledge:publication:all", "knowledge:publication:shareRetry"));
        assertEquals("SUCCEEDED", shares.generate(1L, 2L).status);
        assertEquals("SUCCEEDED", shares.generate(1L, 2L).status); assertEquals(2, uploads.size()); assertArrayEquals(uploads.get(0), uploads.get(1));
        verify(mapper, times(1)).insertPublicationOperation(any()); assertEquals("READY", row.shareState);
        assertNull(properties.getPublicBaseUrl()); assertNull(properties.getPageTemplateAssetId());
    }
    @Test void temporaryLedgerFailureReturnsUnavailableWithoutSigning() {
        when(gate.evaluate(row, sale)).thenThrow(new IllegalStateException("private filesystem path"));
        PublicationException failure = assertThrows(PublicationException.class, () -> new PublicationDownloadService(service, gate, assets).issue(1L));
        assertEquals(503, failure.getCode()); assertFalse(failure.getMessage().contains("private filesystem"));
        verify(assets, never()).publicationDownload(any());
    }
    @Test void publicReadRejectsDownloadModeBeforeLookingUpCode() {
        var publicService = new PublicationPublicService(service, gate, assets, mock(com.ruoyi.knowledge.storage.mapper.KbAssetMapper.class));
        assertThrows(PublicationException.class, () -> publicService.get("0123456789abcdef0123456789abcdef"));
        verify(mapper, never()).byCode(any());
    }
    @Test void changingGlobalModeCannotServeExistingZipAsPublicPageOrJson() {
        properties.setDeliveryMode(PublicationProperties.DeliveryMode.PUBLIC_PAGE); row.shareCode = "a".repeat(32);
        when(mapper.byCode(row.shareCode)).thenReturn(row);
        var publicService = new PublicationPublicService(service, gate, assets, mock(com.ruoyi.knowledge.storage.mapper.KbAssetMapper.class));
        assertEquals(404, assertThrows(PublicationException.class, () -> publicService.get(row.shareCode)).getCode());
    }
    private static KbAsset asset(Long id, String type, String purpose, byte[] bytes) {
        KbAsset asset = new KbAsset(); asset.setAssetId(id); asset.setContentType(type); asset.setPurpose(purpose); asset.setStatus("READY");
        asset.setOriginalName("publication-1-v1.zip"); asset.setFileSize((long) bytes.length); asset.setSha256(KbAssetServiceImpl.sha256(bytes)); return asset;
    }
    private static Relation relation(String type, Long id) { Relation r = new Relation(); r.objectType = type; r.id = id; r.rowVersion = 1L; r.name = type; return r; }
    private static Map<String, byte[]> unzip(byte[] bytes) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) { entries.put(e.getName(), zip.readAllBytes()); }
        }
        return entries;
    }
}
