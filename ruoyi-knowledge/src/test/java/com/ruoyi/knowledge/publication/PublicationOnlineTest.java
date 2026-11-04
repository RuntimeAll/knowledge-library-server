package com.ruoyi.knowledge.publication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ruoyi.knowledge.publication.PublicationModels.*;
import com.ruoyi.knowledge.publication.PublicationRows.*;
import com.ruoyi.knowledge.review.KbReviewGate;
import com.ruoyi.knowledge.storage.IKbAssetService;
import com.ruoyi.knowledge.storage.domain.KbAsset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;

class PublicationOnlineTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void deploymentOriginSupportsOptInHttpAndMigratingToHttpsWithoutChangingCode() {
        var properties = new PublicationProperties();
        var support = new PublicationSupport(mock(PlatformTransactionManager.class), new KbReviewGate(), properties);
        properties.setPublicBaseUrl("http://192.168.1.20:18084/");
        assertThrows(PublicationException.class, () -> support.publicUrl("a".repeat(32)));
        properties.setAllowHttp(true);
        assertEquals("http://192.168.1.20:18084/p/" + "a".repeat(32), support.publicUrl("a".repeat(32)));
        properties.setPublicBaseUrl("https://materials.example.invalid/"); properties.setAllowHttp(false);
        assertEquals("https://materials.example.invalid/p/" + "a".repeat(32), support.publicUrl("a".repeat(32)));
        for (String invalid : List.of("https://user:pass@example.invalid", "https://example.invalid?x=1", "https://example.invalid/#frag",
                "https://example.invalid/subdir", "javascript:alert(1)", "//example.invalid", "https://example.invalid:0", "https://example.invalid:65536", "https://example.invalid:abc")) {
            properties.setPublicBaseUrl(invalid);
            assertThrows(PublicationException.class, () -> support.publicUrl("a".repeat(32)), invalid);
        }
    }
    @Test void baiduFormatUsesActualRemoteNamesAndNeverInventsAbsentCodes() {
        assertEquals("通过百度网盘分享的文件：学生卷.pdf、答案卷.pdf\n链接：https://pan.baidu.com/s/fixture\n提取码：ABCD",
                PublicationCopyText.baidu("https://pan.baidu.com/s/fixture", "ABCD", List.of("学生卷.pdf", "答案卷.pdf")));
        assertEquals("链接：https://pan.baidu.com/s/fixture", PublicationCopyText.baidu("https://pan.baidu.com/s/fixture", " ", List.of()));
    }
    @Test void onlyReportedFooterIsRemovedAndFrozenInputIsRetained() {
        String footer = "#七年级科学 #科学课件 #课堂互动 #反应时间 #制动距离 #初中科学 #教学资源 #玉米训练营";
        String input = "完整正文\n\n" + footer + "\n";
        assertEquals("完整正文", PublicationCopyText.body(input));
        assertTrue(input.contains(footer));
        assertEquals("文中 #反应时间\n#自选话题", PublicationCopyText.body("文中 #反应时间\n#自选话题"));
        assertNull(PublicationCopyText.body(null));
    }
    @Test void onlineReadAndCopyShareOneTextFormatAndRejectOldZipAssets() {
        var f = new PublicationPublicGateTest.Fixture();
        f.row.shareAssetId = 8L; f.row.postBody = "正文\n\n#七年级科学 #科学课件 #课堂互动 #反应时间 #制动距离 #初中科学 #教学资源 #玉米训练营";
        var ready = new CheckResult(); ready.status = "PASSED"; ready.dependencyHash = "original";
        when(f.checks.evaluate(eq(f.row), any(PublicationOperation.class))).thenReturn(ready);
        var asset = new KbAsset(); asset.setAssetId(8L); asset.setContentType("text/html");
        when(f.assets.requireReady(8L)).thenReturn(asset);
        Input input = new Input(); input.images = List.of(); input.products = List.of();
        ShareInput drive = new ShareInput(); drive.shareUrl = "https://pan.baidu.com/s/fixture"; drive.extractionCode = "ABCD";
        FileInput file = new FileInput(); file.productId = 7L; file.remoteFileName = "实际课件.html";
        drive.files = List.of(file); input.baiduShares = List.of(drive);
        when(f.graph.read(f.row)).thenReturn(input);
        assertEquals("正文", f.publicApi.get(f.row.shareCode).postBody());
        assertEquals("正文", f.publicApi.materials(f.row.shareCode, "BODY", null, null).text());
        assertEquals("通过百度网盘分享的文件：实际课件.html\n链接：https://pan.baidu.com/s/fixture\n提取码：ABCD",
                f.publicApi.materials(f.row.shareCode, "BAIDU", null, null).text());
        assertNotNull(f.publicApi.page(f.row.shareCode));
        asset.setContentType("application/zip");
        assertEquals(404, assertThrows(PublicationException.class, () -> f.publicApi.page(f.row.shareCode)).getCode());
        for (String state : List.of("PREPARING", "DELETED")) {
            f.row.status = state;
            assertEquals(404, assertThrows(PublicationException.class, () -> f.publicApi.page(f.row.shareCode)).getCode());
        }
    }
    @Test void managementNeverAdvertisesAZipAsAnOnlinePage() {
        PublicationSaleRecoveryTest.login("owner", Set.of("knowledge:publication:all", "knowledge:publication:list"));
        var mapper = mock(com.ruoyi.knowledge.mapper.PublicationMapper.class);
        var props = new PublicationProperties(); props.setEnabled(true); props.setPublicBaseUrl("https://online.example.invalid");
        var support = new PublicationSupport(mock(PlatformTransactionManager.class), new KbReviewGate(), props);
        var graph = mock(PublicationGraph.class);
        var service = new PublicationService(mapper, support, graph, mock(com.ruoyi.knowledge.mapper.KbAgentSubmissionMapper.class));
        var summary = new Summary(); summary.status = "ON_SALE"; summary.shareState = "READY"; summary.shareCode = "a".repeat(32);
        summary.shareContentType = "application/zip";
        when(mapper.list(any())).thenReturn(List.of(summary)); when(mapper.count(any())).thenReturn(1L);
        assertNull(service.list(new ListQuery()).rows().get(0).shareUrl);
        summary.shareContentType = "text/html";
        assertEquals("https://online.example.invalid/p/" + "a".repeat(32), service.list(new ListQuery()).rows().get(0).shareUrl);
        verifyNoInteractions(graph);
    }
    @Test void successfulZipGenerationIsNotReplayedAsSuccessfulOnlineGeneration() {
        PublicationSaleRecoveryTest.login("owner", Set.of("knowledge:publication:all", "knowledge:publication:shareRetry"));
        var f = new PublicationPublicGateTest.Fixture();
        var props = new PublicationProperties(); props.setEnabled(true); props.setPublicBaseUrl("https://online.example.invalid");
        var support = new PublicationSupport(mock(PlatformTransactionManager.class), new KbReviewGate(), props);
        var service = new PublicationService(f.mapper, support, mock(PublicationGraph.class), mock(com.ruoyi.knowledge.mapper.KbAgentSubmissionMapper.class));
        when(f.mapper.get(1L)).thenReturn(f.row);
        var sale = f.mapper.getOperation(1L, 3L); sale.contentVersion = 1L;
        var op = new PublicationOperation(); op.status = "SUCCEEDED"; op.action = "GENERATE_LINK";
        op.snapshotJson = support.encode(new Snapshot(new Input(), null, null, "OSS_DOWNLOAD"));
        when(f.mapper.operation(1L, "generate:3")).thenReturn(op);
        var share = new PublicationShareService(service, mock(PublicationCheckService.class), f.assets, props, new PublicationBundleService(service, f.assets));
        assertEquals(409, assertThrows(PublicationException.class, () -> share.generate(1L, 3L)).getCode());
        op.status = "FAILED"; op.attemptCount = 1;
        assertEquals(409, assertThrows(PublicationException.class, () -> share.generate(1L, 3L)).getCode());
        assertEquals("FAILED", op.status); assertEquals(1, op.attemptCount);
        verifyNoInteractions(f.assets);
        verify(f.mapper, never()).updatePublicationOperation(any());
    }
}
