package com.ruoyi.knowledge.importing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.html.HtmlQuestionDocument;
import com.ruoyi.knowledge.html.KbHtmlCodec;
import com.ruoyi.knowledge.storage.IKbAssetService;
import com.ruoyi.knowledge.storage.domain.KbAsset;
import java.util.List;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class KbLayoutAssetPipelineTest {
    private final KbHtmlCodec codec = new KbHtmlCodec();
    private final IKbAssetService assets = mock(IKbAssetService.class);
    private final KbHtmlAssetResolver resolver = new KbHtmlAssetResolver(assets, codec);

    private HtmlQuestionDocument parse(String body) {
        return codec.readArticle(Jsoup.parse("<article data-client-key='layout' data-intent='create'>"
                + body + "</article>").selectFirst("article"));
    }

    @Test void separateChoiceImageIsResolvedAndMissingChoiceImageFails() {
        String body = "<section data-part='stem'>选择图形</section><section data-part='options'>"
                + "<div data-option-key='A'><img src='a.png'></div><div data-option-key='B'>无图</div></section>";
        assertThrows(ServiceException.class, () -> resolver.resolve(parse(body), List.of(), null));
        KbAsset asset = imageAsset(11L);
        when(assets.uploadBytes(any(byte[].class), eq("a.png"), eq("image/png"), eq("QUESTION_IMAGE"), isNull()))
                .thenReturn(asset);
        var result = resolver.resolve(parse(body), List.of(new ImportResource("a.png", "image/png", new byte[] {1, 2})), null);
        assertEquals(List.of(11L), result.assetIds());
        assertTrue(result.stemHtml().contains("data-option-key=\"A\""));
        assertTrue(result.stemHtml().contains("src=\"kb-asset:11\""));
        verify(assets).uploadBytes(any(byte[].class), eq("a.png"), eq("image/png"), eq("QUESTION_IMAGE"), isNull());
    }

    @Test void groupStemAndAnswerImagesUseTheSameRegisteredAssetPipeline() {
        when(assets.requireReady(11L)).thenReturn(imageAsset(11L));
        when(assets.requireReady(12L)).thenReturn(imageAsset(12L));
        var document = parse("""
                <section data-part="stem"><div data-kb-role="group" data-columns="2">
                <div data-item-key="1"><img data-asset-id="11"></div><div data-item-key="2">2+2</div></div></section>
                <section data-part="answer"><div data-kb-role="group" data-columns="2">
                <div data-item-key="1"><img data-asset-id="12"></div><div data-item-key="2"></div></div></section>
                """);
        var result = resolver.resolve(document, List.of(), null);
        assertEquals(List.of(11L, 12L), result.assetIds());
        assertTrue(result.stemHtml().contains("kb-asset:11"));
        assertTrue(result.answerHtml().contains("kb-asset:12"));
        assertTrue(result.answerHtml().contains("data-item-key=\"2\"></div>"));
    }

    @Test void optionsCannotSmuggleRemoteImagesOrNonQuestionAssets() {
        String before = "<section data-part='stem'>选择<div data-kb-role='options'><div data-option-key='A'>";
        String after = "</div><div data-option-key='B'>无图</div></div></section>";
        assertThrows(ServiceException.class, () -> resolver.resolve(parse(before + "<img src='https://example.com/a.png'>" + after), List.of(), null));
        KbAsset source = imageAsset(11L);
        source.setPurpose("SOURCE_ORIGINAL");
        when(assets.requireReady(11L)).thenReturn(source);
        assertThrows(ServiceException.class, () -> resolver.resolve(parse(before + "<img data-asset-id='11'>" + after), List.of(), null));
        verify(assets, never()).uploadBytes(any(), any(), any(), any(), any());
    }

    private KbAsset imageAsset(Long id) {
        KbAsset asset = new KbAsset();
        asset.setAssetId(id);
        asset.setPurpose("QUESTION_IMAGE");
        return asset;
    }
}
