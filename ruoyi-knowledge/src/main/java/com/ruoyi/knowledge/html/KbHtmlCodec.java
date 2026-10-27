package com.ruoyi.knowledge.html;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.storage.KbAssetServiceImpl;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/** Parses the documented exchange format; never fetches remote resources. */
@Component
public class KbHtmlCodec {
    public static final String SCHEMA = "kb-question-html/v1";
    private static final Safelist SAFE = new Safelist()
            .addTags("p", "br", "div", "span", "strong", "b", "em", "i", "u", "s", "sub", "sup",
                    "ul", "ol", "li", "table", "thead", "tbody", "tfoot", "tr", "td", "th", "caption",
                    "img", "h1", "h2", "h3", "h4", "blockquote", "pre", "code", "hr")
            .addAttributes("span", "class", "data-latex", "data-display")
            .addAttributes("div", "data-kb-role", "data-columns", "data-option-key", "data-item-key")
            .addAttributes("img", "src", "alt", "data-asset-id", "width", "height")
            .addAttributes("td", "colspan", "rowspan").addAttributes("th", "colspan", "rowspan")
            .addAttributes("ol", "start")
            .addProtocols("img", "src", "kb-asset");

    static {
        SAFE.addTags(KbSvgSafety.TAGS.toArray(String[]::new));
        for (String tag : KbSvgSafety.TAGS) {
            SAFE.addAttributes(tag, KbSvgSafety.ATTRIBUTES.toArray(String[]::new));
            SAFE.addAttributes(tag, "viewBox", "preserveAspectRatio");
        }
    }

    public List<Element> readArticles(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > 2 * 1024 * 1024) {
            throw new ServiceException("标准HTML必须非空且不超过2MiB");
        }
        Document document = Jsoup.parse(new String(bytes, StandardCharsets.UTF_8));
        Element schema = document.selectFirst("meta[name=kb-schema]");
        if (schema == null || !SCHEMA.equals(schema.attr("content"))) {
            throw new ServiceException("HTML缺少kb-schema或版本不支持，要求" + SCHEMA);
        }
        List<Element> articles = new ArrayList<>(document.select("article[data-client-key]"));
        if (articles.isEmpty() || articles.size() > 200) { throw new ServiceException("每批必须包含1至200道标准题目"); }
        Set<String> keys = new HashSet<>();
        for (Element article : articles) {
            if (!keys.add(article.attr("data-client-key"))) { throw new ServiceException("同一批次clientKey不能重复"); }
            if (article.parents().stream().anyMatch(e -> e.tagName().equals("article"))) {
                throw new ServiceException("题目article不能嵌套");
            }
        }
        return articles;
    }

    public HtmlQuestionDocument readArticle(Element original) {
        if (original.hasAttr("data-kb-input-error")) {
            String message = original.attr("data-kb-input-error");
            throw new ServiceException("JSON录入项无效：" + message.substring(0, Math.min(message.length(), 300)), 400);
        }
        Element article = original.clone();
        String key = article.attr("data-client-key").trim();
        if (key.isEmpty() || key.length() > 160) { throw new ServiceException("clientKey长度必须为1至160字符"); }
        String intent = article.attr("data-intent");
        if (!Set.of("create", "update", "derive").contains(intent)) {
            throw new ServiceException("data-intent必须为create、update或derive");
        }
        Element stem = onePart(article, "stem", true);
        Element answer = onePart(article, "answer", false);
        Element analysis = onePart(article, "analysis", false);
        Element options = onePart(article, "options", false);
        if (options != null) {
            if (options.parent() != article) throw new ServiceException("独立options区域必须直属article");
            if (!stem.select("[data-kb-role=options]").isEmpty())
                throw new ServiceException("独立选项区域不能与题干内选项区域重复");
            Element normalized = new Element("div").attr("data-kb-role", "options");
            for (var node : new ArrayList<>(options.childNodes())) normalized.appendChild(node);
            stem.appendChild(normalized);
            options.remove();
        }
        HtmlMetadata metadata = HtmlMetadata.empty();
        List<Element> scripts = article.select("script[type=application/json][data-kb-metadata]");
        if (scripts.size() > 1) { throw new ServiceException("每题只能有一个metadata区域"); }
        if (!scripts.isEmpty()) {
            try { metadata = JSON.parseObject(scripts.get(0).data(), HtmlMetadata.class); }
            catch (RuntimeException e) { throw new ServiceException("metadata不是有效的标准JSON"); }
            if (metadata == null) { metadata = HtmlMetadata.empty(); }
        }
        Long id = number(article, "data-question-id");
        Long version = number(article, "data-row-version");
        Long parent = number(article, "data-parent-id");
        if ("update".equals(intent) && (id == null || version == null)) {
            throw new ServiceException("修改原题必须提供question-id和row-version");
        }
        if ("derive".equals(intent) && parent == null) { throw new ServiceException("变式必须提供parent-id"); }
        String fingerprint = KbAssetServiceImpl.sha256(original.outerHtml().getBytes(StandardCharsets.UTF_8));
        for (Element math : article.select("script[type^=math/tex]")) {
            String latex = math.data();
            Element span = new Element("span").attr("class", "math").attr("data-latex", latex)
                    .text("\\(" + latex + "\\)");
            if (math.attr("type").contains("mode=display")) { span.attr("data-display", "block"); }
            math.replaceWith(span);
        }
        article.select("script,iframe,object,embed,style,link,base").remove();
        for (Element semantic : article.select("[data-kb-role],[data-columns],[data-item-key],[data-option-key]")) {
            if (!semantic.parents().contains(stem) && !semantic.parents().contains(answer)
                    && !semantic.parents().contains(analysis))
                throw new ServiceException("排版语义必须位于stem、answer或analysis区域内部");
        }
        KbHtmlLayout.validateParts(stem, answer, analysis);
        Element candidate = original.clone();
        candidate.removeAttr("data-granularity-exception-id");
        String candidateHash = KbAssetServiceImpl.sha256(candidate.outerHtml().getBytes(StandardCharsets.UTF_8));
        return new HtmlQuestionDocument(key, intent, id, version, parent, metadata, stem, answer, analysis, fingerprint,
                number(original, "data-expected-parent-row-version"),
                original.hasAttr("data-expected-parent-stem-hash") ? original.attr("data-expected-parent-stem-hash") : null,
                original.hasAttr("data-granularity-exception-id") ? original.attr("data-granularity-exception-id") : null,
                candidateHash, original.hasAttr("data-expected-parent-content-hash") ? original.attr("data-expected-parent-content-hash") : null);
    }

    public String sanitize(Element part) {
        if (part == null) { return null; }
        Document dirty = Jsoup.parseBodyFragment(part.html());
        boolean auxiliary = Set.of("answer", "analysis").contains(part.attr("data-part"));
        KbHtmlLayout.inspect(dirty.body(), auxiliary);
        for (Element svg : dirty.select("svg")) { KbSvgSafety.validate(svg); }
        dirty.outputSettings().prettyPrint(false);
        Document safe = new Cleaner(SAFE).clean(dirty);
        safe.outputSettings().prettyPrint(false);
        // Keep a single semantic math class; no arbitrary user CSS or handlers are retained.
        for (Element span : safe.select("span[class]")) {
            if (span.hasAttr("data-latex")) { span.attr("class", "math"); }
            else { span.removeAttr("class"); }
        }
        for (Element math : safe.select("span[data-latex]")) {
            if (math.text().isBlank() && !math.attr("data-latex").isBlank()) {
                math.text("\\(" + math.attr("data-latex") + "\\)");
            }
        }
        // Validate again after dangerous tags were removed, so a script-only item cannot survive empty.
        KbHtmlLayout.inspect(safe.body(), auxiliary);
        return safe.body().html();
    }

    public String text(String html) { return Jsoup.parseBodyFragment(html == null ? "" : html).text(); }

    private Element onePart(Element article, String part, boolean required) {
        List<Element> values = article.select("[data-part=" + part + "]");
        if (values.size() > 1 || (required && values.size() != 1)) {
            throw new ServiceException("题目必须有唯一的" + part + "区域");
        }
        if (values.isEmpty()) return null;
        Element value = values.get(0);
        if (value.parents().stream().anyMatch(parent -> parent.hasAttr("data-part")))
            throw new ServiceException("题目区域不能嵌套，子项不能复用data-part");
        return value;
    }


    private Long number(Element element, String attribute) {
        if (!element.hasAttr(attribute) || element.attr(attribute).isBlank()) { return null; }
        try {
            long result = Long.parseLong(element.attr(attribute));
            if (result < 0) { throw new NumberFormatException(); }
            return result;
        } catch (NumberFormatException e) { throw new ServiceException(attribute + "必须为非负整数"); }
    }
}
