package com.ruoyi.knowledge.publication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ruoyi.knowledge.mapper.KbAgentSubmissionMapper;
import com.ruoyi.knowledge.mapper.PublicationMapper;
import com.ruoyi.knowledge.publication.PublicationModels.*;
import com.ruoyi.knowledge.publication.PublicationRows.*;
import com.ruoyi.knowledge.review.KbReviewGate;
import com.ruoyi.knowledge.storage.IKbAssetService;
import com.ruoyi.knowledge.storage.domain.KbAsset;
import com.ruoyi.knowledge.storage.mapper.KbAssetMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class PublicationPreviewTest {
    @Test void detailSignsOnlyCurrentImageBatchAndKeepsOriginalBytesOnDemand() {
        Fixture f = new Fixture();
        ImageInput first = new ImageInput(); first.assetId = 10L; first.sortOrder = 1;
        ImageInput second = new ImageInput(); second.assetId = 11L; second.sortOrder = 2;
        f.input.images = List.of(first, second);
        when(f.assetMapper.selectByIds(List.of(10L, 11L))).thenReturn(List.of(image(10L), image(11L)));
        when(f.assets.publicationImagePreviews(List.of(10L, 11L))).thenReturn(Map.of(
                10L, new IKbAssetService.Preview("https://fixture.oss-cn-hangzhou.aliyuncs.com/one?processed", 600, "image/webp"),
                11L, new IKbAssetService.Preview("https://fixture.oss-cn-hangzhou.aliyuncs.com/two?processed", 600, "image/webp")));
        var view = f.api.get(f.row.shareCode);
        assertEquals(2, view.images().size()); assertEquals(600, view.images().get(0).previewExpiresInSeconds());
        assertTrue(view.images().get(0).previewUrl().endsWith("one?processed"));
        assertTrue(view.images().get(0).contentUrl().contains("/assets/10/content?contentVersion=1"));
        verify(f.assets).publicationImagePreviews(List.of(10L, 11L));
        verify(f.assets, never()).openContent(any());
        f.row.status = "PREPARING"; clearInvocations(f.assets);
        assertThrows(PublicationException.class, () -> f.api.get(f.row.shareCode));
        verifyNoInteractions(f.assets);
    }
    @Test void publicProductDeliveryCanBeDisabledWithoutDeletingArchivedBytes() {
        Fixture f = new Fixture(); f.properties.setProductDownloadsEnabled(false);
        var product = new PublicationProduct(); product.productId = 7L;
        when(f.mapper.products(1L)).thenReturn(List.of(product));
        var fact = new ProductFact(); fact.id = 7L; fact.assetId = 99L;
        when(f.mapper.productFacts(List.of(7L))).thenReturn(List.of(fact));
        ProductInput input = new ProductInput(); input.productId = 7L; f.input.products = List.of(input);
        when(f.mapper.allowedAssets(any())).thenReturn(List.of(10L, 99L));
        assertTrue(f.api.get(f.row.shareCode).products().isEmpty());
        assertEquals(404, assertThrows(PublicationException.class, () -> f.api.asset(f.row.shareCode, 99L, 1L)).getCode());
        verify(f.assets, never()).requireReady(99L);
        when(f.assets.requireReady(10L)).thenReturn(image(10L));
        assertNotNull(f.api.asset(f.row.shareCode, 10L, 1L));
        f.properties.setProductDownloadsEnabled(true); when(f.assets.requireReady(99L)).thenReturn(image(99L));
        assertNotNull(f.api.asset(f.row.shareCode, 99L, 1L));
        verify(f.assets, never()).delete(any());
    }
    private static KbAsset image(Long id) {
        KbAsset a = new KbAsset(); a.setAssetId(id); a.setStatus("READY"); a.setContentType("image/png"); a.setFileSize(1024L); return a;
    }
    static class Fixture {
        final PublicationMapper mapper = mock(PublicationMapper.class);
        final PublicationGraph graph = mock(PublicationGraph.class);
        final PublicationReadGate checks = mock(PublicationReadGate.class);
        final IKbAssetService assets = mock(IKbAssetService.class);
        final KbAssetMapper assetMapper = mock(KbAssetMapper.class);
        final PublicationProperties properties = new PublicationProperties();
        final Publication row = new Publication(); final Input input = new Input();
        final PublicationPublicService api;
        Fixture() {
            properties.setEnabled(true);
            var support = new PublicationSupport(mock(PlatformTransactionManager.class), new KbReviewGate(), properties);
            var service = new PublicationService(mapper, support, graph, mock(KbAgentSubmissionMapper.class));
            row.id = 1L; row.contentVersion = 1L; row.sharedContentVersion = 1L; row.lastSaleOperationId = 2L;
            row.shareAssetId = 8L; row.shareCode = "a".repeat(32); row.status = "ON_SALE"; row.shareState = "READY";
            input.images = List.of(); input.products = List.of(); input.baiduShares = List.of();
            var sale = new PublicationOperation(); sale.id = 2L; sale.action = "SALE"; sale.status = "SUCCEEDED";
            var check = new CheckResult(); check.status = "PASSED"; check.dependencyHash = "unchanged";
            sale.snapshotJson = support.encode(new Snapshot(input, check));
            when(mapper.byCode(row.shareCode)).thenReturn(row); when(mapper.getOperation(1L, 2L)).thenReturn(sale);
            when(checks.evaluate(row, sale)).thenReturn(check); when(graph.read(row)).thenReturn(input);
            var page = image(8L); page.setContentType("text/html"); when(assets.requireReady(8L)).thenReturn(page);
            api = new PublicationPublicService(service, checks, assets, assetMapper);
        }
    }
}
