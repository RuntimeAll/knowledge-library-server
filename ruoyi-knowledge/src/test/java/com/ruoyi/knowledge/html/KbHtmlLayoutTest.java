package com.ruoyi.knowledge.html;

import static org.junit.jupiter.api.Assertions.*;
import com.ruoyi.common.exception.ServiceException;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KbHtmlLayoutTest {
    private final KbHtmlCodec codec = new KbHtmlCodec();

    private HtmlQuestionDocument parse(String parts) {
        return codec.readArticle(Jsoup.parse("<article data-client-key='layout' data-intent='create'>"
                + parts + "</article>").selectFirst("article"));
    }

    @Test void standaloneOptionsBecomeStemContentBeforeAssetResolution() {
        var doc = parse("""
                <section data-part="stem"><p>选择正确图形</p></section>
                <section data-part="options"><div data-option-key="A"><img src="a.png"></div>
                <div data-option-key="B"><span data-latex="x^2"></span></div></section>
                """);
        assertEquals("a.png", doc.stem().selectFirst("[data-option-key=A] img").attr("src"));
        String safe = codec.sanitize(doc.stem());
        assertTrue(safe.contains("data-kb-role=\"options\""));
        assertTrue(safe.contains("data-option-key=\"A\""));
        assertTrue(safe.contains("\\(x^2\\)"));
    }

    @Test void preservesGroupKeysAndAnswerPlaceholdersWhileDiscardingArbitraryPresentation() {
        var doc = parse("""
                <section data-part="stem"><div data-kb-role="group" data-columns="2" style="position:fixed" class="rogue">
                <div data-item-key="1">1+1</div><div data-item-key="2"><div data-kb-role="vertical">
                <table><tr><td style="color:red">12</td></tr><tr><td>+3</td></tr></table></div></div></div></section>
                <section data-part="answer"><div data-kb-role="group" data-columns="2"><div data-item-key="1">2</div><div data-item-key="2"></div></div></section>
                """);
        String stem = codec.sanitize(doc.stem());
        String answer = codec.sanitize(doc.answer());
        assertEquals(2, KbHtmlLayout.validateParts(stem, answer, null).itemCount());
        assertTrue(stem.contains("data-kb-role=\"vertical\""));
        assertTrue(stem.contains("data-columns=\"2\""));
        assertTrue(stem.contains("<table>"));
        assertFalse(stem.contains("style="));
        assertFalse(stem.contains("class="));
        assertTrue(answer.contains("data-item-key=\"2\"></div>"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "<div data-kb-role='options'><div data-option-key='A'>1</div><div data-option-key='A'>2</div></div>",
        "<div data-kb-role='options'><div data-option-key='A'>1</div></div>",
        "<div data-kb-role='options'><div data-option-key='A'>1<div data-kb-role='options'><div data-option-key='B'>2</div><div data-option-key='C'>3</div></div></div><div data-option-key='D'>4</div></div>",
        "<div data-option-key='A'>orphan</div>",
        "<div data-kb-role='group' data-columns='5'><div data-item-key='1'>1</div><div data-item-key='2'>2</div></div>",
        "<div data-kb-role='group' data-columns='2'><div data-item-key='1'>1</div><div data-item-key='1'>2</div></div>",
        "<div data-kb-role='group' data-columns='2'><div data-item-key='1'></div><div data-item-key='2'>2</div></div>",
        "<span data-kb-role='puzzle'>wrong element</span>",
        "<div data-columns='2'>orphan columns</div>"
    })
    void malformedSemanticStructuresAreRejected(String body) {
        assertThrows(ServiceException.class, () -> parse("<section data-part='stem'>" + body + "</section>"));
    }

    @Test void rejectsDuplicateSeparateOptionsNestedPartsAndDetachedSemanticContent() {
        String options = "<section data-part='options'><div data-option-key='A'>1</div><div data-option-key='B'>2</div></section>";
        assertThrows(ServiceException.class, () -> parse("<section data-part='stem'>题干</section>" + options + options));
        assertThrows(ServiceException.class, () -> parse("<section data-part='stem'>题干" + options + "</section>"));
        assertThrows(ServiceException.class, () -> parse("<section data-part='stem'>题干</section><div data-kb-role='puzzle'>漏放正文</div>"));
    }

    @Test void answerGroupsMustMatchStemKeysOrderAndColumns() {
        String stem = "<div data-kb-role='group' data-columns='2'><div data-item-key='1'>1+1</div><div data-item-key='2'>2+2</div></div>";
        String answer = "<div data-kb-role='group' data-columns='2'><div data-item-key='2'>4</div><div data-item-key='1'>2</div></div>";
        assertThrows(ServiceException.class, () -> KbHtmlLayout.validateParts(stem, answer, null));
        assertThrows(ServiceException.class, () -> KbHtmlLayout.validateParts("单题", answer, null));
        assertEquals(2, KbHtmlLayout.validateParts(stem, "<p>答案见解析</p>", null).itemCount());
    }

    @Test void malformedSemanticDataInsideBlockedElementsCannotBecomeVisibleLayout() {
        // readArticle removes the iframe before the item-content check.
        assertThrows(ServiceException.class, () -> parse("<section data-part='stem'><div data-kb-role='options'><div data-option-key='A'><iframe src='about:blank'></iframe></div><div data-option-key='B'>B</div></div></section>"));
    }

    @Test void jsonMetadataRejectsDerivedAndUnknownFieldsInsteadOfSilentlyDroppingThem() {
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        var metadata = mapper.readValue("{\"layoutMode\":\"STRUCTURED\",\"unitKind\":\"GROUP\",\"paginationMode\":\"KEEP_TOGETHER\"}", HtmlMetadata.class);
        assertEquals("GROUP", metadata.unitKind());
        for (String field : new String[] {"hasImage", "itemCount", "status", "layoutMdoe"})
            assertThrows(RuntimeException.class, () -> mapper.readValue("{\"" + field + "\":\"ignored\"}", HtmlMetadata.class));
    }
}
