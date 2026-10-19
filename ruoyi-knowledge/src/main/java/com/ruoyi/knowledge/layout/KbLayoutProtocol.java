package com.ruoyi.knowledge.layout;

import static com.ruoyi.knowledge.service.impl.KnowledgeRules.*;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.ruoyi.common.exception.ServiceException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Bounded, executable-free template language and schema-driven parameters. */
public final class KbLayoutProtocol {
    private KbLayoutProtocol() {}

    public static String json(Object value) {
        return JSON.toJSONString(
                value, JSONWriter.Feature.MapSortField, JSONWriter.Feature.WriteLongAsString);
    }

    public static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String hash(Object value) {
        return hash(json(value).getBytes(StandardCharsets.UTF_8));
    }

    public static <T> List<T> list(List<T> value) {
        return value == null ? List.of() : value;
    }

    public static void limit(String value, int length, String name) {
        if (value != null && value.length() > length) throw new ServiceException(name + "过长", 400);
    }

    public static String key(String value, int max) {
        if (value == null || value.length() > max || !value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]*"))
            throw new ServiceException("配置标识无效", 400);
        return value;
    }

    public static void template(KbExperienceVersion version) {
        if (!"kb-layout-template/v1".equals(version.getSchemaVersion()))
            throw new ServiceException("模板协议必须为kb-layout-template/v1", 400);
        var fields = list(version.getFieldSchema());
        if (fields.isEmpty() || fields.size() > 64) throw new ServiceException("模板字段须为1至64项", 400);
        Set<String> keys = new HashSet<>();
        for (var field : fields) {
            key(field.getKey(), 64);
            if (!keys.add(field.getKey())) throw new ServiceException("模板字段重复", 400);
            field.setLabel(text(field.getLabel(), "字段标签", 100));
            choice(field.getType(), null, "TEXT", "NUMBER", "BOOLEAN", "STRING_LIST");
            if (field.getMin() != null && field.getMax() != null && field.getMin() > field.getMax())
                throw new ServiceException("字段范围无效", 400);
            if (list(field.getOptions()).size() > 100) throw new ServiceException("字段选项过多", 400);
            if (field.getDefaultValue() != null) value(field, field.getDefaultValue());
        }
        if (!keys.contains("title")) throw new ServiceException("模板须声明title标题字段", 400);
        if (version.getDefaultValues() == null) version.setDefaultValues(new JSONObject());
        parameters(version, version.getDefaultValues(), false);
        KbTemplateDefinition d = version.getDefinition();
        if (d == null) throw new ServiceException("缺少模板definition", 400);
        d.setHtml(text(d.getHtml(), "模板HTML", 100000));
        if (d.getCss() == null) d.setCss("");
        limit(d.getCss(), 100000, "模板CSS");
        var body = Jsoup.parseBodyFragment(d.getHtml()).body();
        Set<String> tags =
                Set.of(
                        "div", "section", "header", "footer", "main", "article", "h1", "h2", "h3",
                        "h4", "p", "span", "strong", "b", "em", "i", "small", "br", "hr", "table",
                        "thead", "tbody", "tr", "th", "td", "ul", "ol", "li", "img");
        for (Element e : body.getAllElements()) {
            if (e == body) continue;
            if (!tags.contains(e.normalName()))
                throw new ServiceException("模板包含不允许的标签：" + e.normalName(), 400);
            for (var a : e.attributes()) {
                String n = a.getKey().toLowerCase(Locale.ROOT);
                if (!Set.of(
                                "class",
                                "style",
                                "data-kb-field",
                                "data-kb-slot",
                                "data-kb-resource",
                                "data-asset-id",
                                "alt",
                                "colspan",
                                "rowspan")
                        .contains(n)) throw new ServiceException("模板属性不允许：" + n, 400);
                if ("style".equals(n)) css(a.getValue());
            }
            if (e.hasAttr("data-kb-field") && !keys.contains(e.attr("data-kb-field")))
                throw new ServiceException("模板引用未声明字段", 400);
            if (e.hasAttr("data-kb-slot") && !"sections".equals(e.attr("data-kb-slot")))
                throw new ServiceException("只支持sections插槽", 400);
            if (e.normalName().equals("img")
                    && !e.hasAttr("data-kb-resource")
                    && !e.hasAttr("data-asset-id")) throw new ServiceException("模板图片须引用资产", 400);
        }
        var slots = body.select("[data-kb-slot=sections]");
        if (slots.size() != 1
                || slots.first().parent() != body
                || !slots.first().normalName().equals("div")
                || !slots.first().children().isEmpty()
                || !slots.first().text().isBlank())
            throw new ServiceException("模板必须有且仅有一个空div sections插槽", 400);
        css(d.getCss());
        KbTemplatePage p = d.getPage();
        if (p == null) {
            p = new KbTemplatePage();
            d.setPage(p);
        }
        p.setSize(choice(p.getSize(), "A4", "A4", "A5", "A3", "Letter"));
        p.setOrientation(choice(p.getOrientation(), "portrait", "portrait", "landscape"));
        p.setMarginTopMm(range(p.getMarginTopMm(), 15, 5, 40, "上边距"));
        p.setMarginRightMm(range(p.getMarginRightMm(), 15, 5, 40, "右边距"));
        p.setMarginBottomMm(range(p.getMarginBottomMm(), 15, 5, 40, "下边距"));
        p.setMarginLeftMm(range(p.getMarginLeftMm(), 15, 5, 40, "左边距"));
        p.setFontFamily(choice(p.getFontFamily(), "KBBody", "KBBody"));
        p.setFontSizePt(range(p.getFontSizePt(), 12, 8, 24, "字号"));
        p.setLineHeight(range(p.getLineHeight(), 1.5, 1, 3, "行距"));
        p.setColumns(
                (int)
                        range(
                                p.getColumns() == null ? null : p.getColumns().doubleValue(),
                                1,
                                1,
                                3,
                                "分栏"));
        p.setQuestionGapMm(range(p.getQuestionGapMm(), 5, 0, 30, "题间距"));
        p.setAnswerSpaceMm(range(p.getAnswerSpaceMm(), 16, 0, 150, "作答留白"));
        var o = d.getOrganization();
        if (o == null) {
            o = new KbTemplateOrganization();
            d.setOrganization(o);
        }
        o.setNumbering(choice(o.getNumbering(), "CONTINUOUS", "CONTINUOUS", "SECTION"));
        o.setSectionPageBreak(Boolean.TRUE.equals(o.getSectionPageBreak()));
        o.setDayPageBreak(Boolean.TRUE.equals(o.getDayPageBreak()));
        var c = d.getContent();
        if (c == null) {
            c = new KbTemplateContent();
            d.setContent(c);
        }
        c.setOptionColumns(
                (int)
                        range(
                                c.getOptionColumns() == null
                                        ? null
                                        : c.getOptionColumns().doubleValue(),
                                1,
                                1,
                                4,
                                "选项分栏"));
        c.setShowAnalysis(Boolean.TRUE.equals(c.getShowAnalysis()));
        c.setImageMaxWidthPercent(range(c.getImageMaxWidthPercent(), 100, 10, 100, "图片宽度"));
        limit(version.getChangeNote(), 10000, "变更说明");
    }

    public static void css(String css) {
        String s = css.replaceAll("(?s)/\\*.*?\\*/", "").toLowerCase(Locale.ROOT);
        if (s.contains("\\")
                || s.matches(
                        "(?s).*(url\\s*\\(|@|expression\\s*\\(|javascript:|behavior\\s*:|position\\s*:|overflow(?:-x|-y)?\\s*:|clip(?:-path)?\\s*:|visibility\\s*:|display\\s*:\\s*none|opacity\\s*:|content\\s*:|<|>|font-size\\s*:\\s*0(?:\\D|$)|transform\\s*:|(?<![-a-z])(?:max-)?height\\s*:|color\\s*:\\s*(white|transparent|#fff\\b|#ffffff\\b)).*"))
            throw new ServiceException("模板CSS包含外部资源、隐藏、定位或裁切规则", 400);
    }

    private static double range(Double n, double fallback, double min, double max, String name) {
        double v = n == null ? fallback : n;
        if (!Double.isFinite(v) || v < min || v > max)
            throw new ServiceException(name + "超出允许范围", 400);
        return v;
    }

    public static JSONObject parameters(KbExperienceVersion v, JSONObject input, boolean required) {
        JSONObject result = new JSONObject();
        Set<String> fields = new HashSet<>();
        for (var f : list(v.getFieldSchema())) {
            fields.add(f.getKey());
            Object item =
                    input != null && input.containsKey(f.getKey())
                            ? input.get(f.getKey())
                            : v.getDefaultValues() != null
                                            && v.getDefaultValues().containsKey(f.getKey())
                                    ? v.getDefaultValues().get(f.getKey())
                                    : f.getDefaultValue();
            if (item == null || item instanceof String s && s.isBlank()) {
                if (required && Boolean.TRUE.equals(f.getRequired()))
                    throw new ServiceException("缺少必填模板字段：" + f.getKey(), 400);
                if (item != null) result.put(f.getKey(), item);
            } else {
                value(f, item);
                result.put(f.getKey(), item);
            }
        }
        if (input != null)
            for (String k : input.keySet())
                if (!fields.contains(k)) throw new ServiceException("未声明的模板字段：" + k, 400);
        return result;
    }

    private static void value(KbTemplateField f, Object value) {
        double size;
        switch (f.getType()) {
            case "TEXT" -> {
                if (!(value instanceof String s) || s.length() > 10000)
                    throw new ServiceException(f.getKey() + "须为有限长度文本", 400);
                size = ((String) value).length();
                if (!list(f.getOptions()).isEmpty() && !f.getOptions().contains(value))
                    throw new ServiceException(f.getKey() + "不在允许选项内", 400);
            }
            case "NUMBER" -> {
                if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue()))
                    throw new ServiceException(f.getKey() + "须为数字", 400);
                size = ((Number) value).doubleValue();
            }
            case "BOOLEAN" -> {
                if (!(value instanceof Boolean))
                    throw new ServiceException(f.getKey() + "须为布尔值", 400);
                return;
            }
            case "STRING_LIST" -> {
                if (!(value instanceof List<?> a)
                        || a.size() > 100
                        || a.stream().anyMatch(x -> !(x instanceof String s) || s.length() > 200))
                    throw new ServiceException(f.getKey() + "须为文本列表", 400);
                size = ((List<?>) value).size();
            }
            default -> throw new ServiceException("未知字段类型", 400);
        }
        if (f.getMin() != null && size < f.getMin() || f.getMax() != null && size > f.getMax())
            throw new ServiceException(f.getKey() + "超出允许范围", 400);
    }
}
