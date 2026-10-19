package com.ruoyi.knowledge.layout;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;

import org.junit.jupiter.api.Test;

import java.util.List;

class KbLayoutProtocolTest {
    static KbExperienceVersion template() {
        var v = new KbExperienceVersion();
        v.setId(1L);
        v.setSchemaVersion("kb-layout-template/v1");
        var title = new KbTemplateField();
        title.setKey("title");
        title.setLabel("标题");
        title.setType("TEXT");
        title.setRequired(true);
        v.setFieldSchema(List.of(title));
        v.setDefaultValues(new JSONObject());
        var d = new KbTemplateDefinition();
        d.setHtml("<h1 data-kb-field=\"title\"></h1><div data-kb-slot=\"sections\"></div>");
        d.setCss("h1 { font-size:20pt; line-height:1.2 }");
        v.setDefinition(d);
        v.setAssets(List.of());
        return v;
    }

    @Test
    void defaultA4AndDeclaredFields() {
        var v = template();
        KbLayoutProtocol.template(v);
        assertEquals("A4", v.getDefinition().getPage().getSize());
        assertEquals("KBBody", v.getDefinition().getPage().getFontFamily());
        assertEquals(
                "一元一次方程",
                KbLayoutProtocol.parameters(v, JSONObject.of("title", "一元一次方程"), true)
                        .getString("title"));
    }

    @Test
    void unknownAndMissingFieldsFail() {
        var v = template();
        KbLayoutProtocol.template(v);
        assertThrows(
                ServiceException.class,
                () -> KbLayoutProtocol.parameters(v, new JSONObject(), true));
        assertThrows(
                ServiceException.class,
                () ->
                        KbLayoutProtocol.parameters(
                                v, JSONObject.of("title", "练习", "undeclared", 1), true));
    }

    @Test
    void scriptsAndNestedSlotAreNotTemplates() {
        for (String html :
                List.of(
                        "<script>alert(1)</script><div data-kb-slot=sections></div>",
                        "<main><div data-kb-slot=sections></div></main>",
                        "<img src='https://example.com/a.png'><div data-kb-slot=sections></div>",
                        "<h1 onclick='x()'></h1><div data-kb-slot=sections></div>")) {
            var v = template();
            v.getDefinition().setHtml(html);
            assertThrows(ServiceException.class, () -> KbLayoutProtocol.template(v));
        }
    }

    @Test
    void externalOrCroppingCssFailsBeforePublishing() {
        for (String css :
                List.of(
                        "div{background:url(https://example.com/a)}",
                        "@import 'x'",
                        "div{height:10px}",
                        "div{max-height:10px}",
                        "div{overflow:hidden}",
                        "div{display:none}",
                        "div{color:transparent}",
                        "div{position:relative}",
                        "div{content:'x'}",
                        "div{font-size:0}")) {
            assertThrows(ServiceException.class, () -> KbLayoutProtocol.css(css), css);
        }
    }

    @Test
    void wrongFieldTypeAndRangeRejected() {
        var v = template();
        var n = new KbTemplateField();
        n.setKey("days");
        n.setLabel("天数");
        n.setType("NUMBER");
        n.setMin(1d);
        n.setMax(7d);
        v.setFieldSchema(List.of(v.getFieldSchema().get(0), n));
        KbLayoutProtocol.template(v);
        assertThrows(
                ServiceException.class,
                () ->
                        KbLayoutProtocol.parameters(
                                v, JSONObject.of("title", "打卡", "days", "3"), true));
        assertThrows(
                ServiceException.class,
                () ->
                        KbLayoutProtocol.parameters(
                                v, JSONObject.of("title", "打卡", "days", 8), true));
    }

    @Test
    void canonicalHashDoesNotDependOnJsonKeyOrder() {
        assertEquals(
                KbLayoutProtocol.hash(JSONObject.of("a", 1, "b", 2)),
                KbLayoutProtocol.hash(JSONObject.of("b", 2, "a", 1)));
    }

    @Test
    void pageRangeAndPinnedFontRejected() {
        var v = template();
        KbLayoutProtocol.template(v);
        v.getDefinition().getPage().setMarginTopMm(2d);
        assertThrows(ServiceException.class, () -> KbLayoutProtocol.template(v));
        v.getDefinition().getPage().setMarginTopMm(15d);
        v.getDefinition().getPage().setFontFamily("Arial");
        assertThrows(ServiceException.class, () -> KbLayoutProtocol.template(v));
    }
}
