package com.ruoyi.knowledge.html;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import com.ruoyi.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.jsoup.nodes.Element;

class KbHtmlCodecTest {
    private final KbHtmlCodec codec = new KbHtmlCodec();

    private HtmlQuestionDocument parse(String body) {
        byte[] input = ("<meta name=kb-schema content=kb-question-html/v1>" + body).getBytes(StandardCharsets.UTF_8);
        return codec.readArticle(codec.readArticles(input).get(0));
    }

    @Test
    void preservesMathTableAndAssetIdentityWhileRemovingExecutableContent() {
        HtmlQuestionDocument doc = parse("""
            <article data-client-key="x" data-intent="create">
              <script type="application/json" data-kb-metadata>{"title":"标题","kpIds":["12"]}</script>
              <section data-part="stem"><script>alert(1)</script><p onclick="alert(2)">题干</p>
                <span class="evil" data-latex="x^2">\\(x^2\\)</span>
                <table><tr><td colspan="2">数值</td></tr></table>
                <img data-asset-id="3" src="kb-asset:3" onerror="alert(3)">
                <iframe src="https://invalid/"></iframe>
              </section>
            </article>
            """);
        String html = codec.sanitize(doc.stem());
        assertTrue(html.contains("data-latex=\"x^2\""));
        assertTrue(html.contains("<table>"));
        assertTrue(html.contains("kb-asset:3"));
        assertFalse(html.contains("script"));
        assertFalse(html.contains("onclick"));
        assertFalse(html.contains("iframe"));
        assertEquals("标题", doc.metadata().title());
        assertEquals(12L, doc.metadata().kpIds().get(0));
        assertNull(doc.answer());
    }

    @Test
    void preservesStaticSvgAndRejectsActiveSvg() {
        HtmlQuestionDocument doc = parse("<article data-client-key='svg' data-intent='create'><section data-part='stem'><svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><circle cx='40' cy='40' r='20' fill='none' stroke='black'/></svg></section></article>");
        assertTrue(codec.sanitize(doc.stem()).contains("circle"));
        assertThrows(ServiceException.class, () -> KbSvgSafety.validateBytes("<svg><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8)));
        assertThrows(ServiceException.class, () -> KbSvgSafety.validateBytes("<svg><image href='https://example.com/a.png'/></svg>".getBytes(StandardCharsets.UTF_8)));
        assertThrows(ServiceException.class, () -> KbSvgSafety.validateBytes("<svg><path style='fill:url(https://example.com)'/></svg>".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void requiresExplicitVersionForUpdate() {
        assertThrows(ServiceException.class, () -> parse("<article data-client-key='x' data-intent='update' data-question-id='1'><section data-part='stem'>x</section></article>"));
    }

    @Test
    void rejectsDuplicateClientKeysAndUnknownSchema() {
        byte[] duplicate = ("<meta name=kb-schema content=kb-question-html/v1><article data-client-key=x></article><article data-client-key=x></article>").getBytes(StandardCharsets.UTF_8);
        assertThrows(ServiceException.class, () -> codec.readArticles(duplicate));
        assertThrows(ServiceException.class, () -> codec.readArticles("<article>plain</article>".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void distinguishesAbsentAndExplicitEmptyAnswer() {
        HtmlQuestionDocument doc = parse("<article data-client-key=x data-intent=create><section data-part=stem>内容</section><section data-part=answer></section></article>");
        assertEquals("", codec.sanitize(doc.answer()));
        assertNull(codec.sanitize(doc.analysis()));
    }

    @Test
    void normalizesMathOnlyMarkupWithoutKeepingExecutableScripts() {
        HtmlQuestionDocument doc = parse("<article data-client-key='math' data-intent='create'><section data-part='stem'><script type='math/tex'>x^2</script><span data-latex='y^2'></span></section></article>");
        String sanitized = codec.sanitize(doc.stem());
        assertTrue(sanitized.contains("\\(x^2\\)"));
        assertTrue(sanitized.contains("\\(y^2\\)"));
        assertFalse(sanitized.contains("<script"));
    }
}
