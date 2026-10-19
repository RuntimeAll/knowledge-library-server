package com.ruoyi.knowledge.importing;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.html.HtmlMetadata;
import com.ruoyi.knowledge.html.KbHtmlCodec;
import com.ruoyi.knowledge.importing.dto.KbRecordsRequest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/** A transport adapter only: all persistence, assets and transactions use the HTML import pipeline. */
@Component
public class KbRecordHtmlConverter {
    public byte[] convert(KbRecordsRequest request) {
        if (!KbRecordsRequest.SCHEMA.equals(request.schemaVersion())) fail("schemaVersion必须为" + KbRecordsRequest.SCHEMA);
        if (request.records() == null || request.records().isEmpty() || request.records().size() > 200)
            fail("records必须包含1至200条内容");
        Map<String, Long> assets = new LinkedHashMap<>();
        for (var resource : request.resources() == null ? List.<KbRecordsRequest.Resource>of() : request.resources()) {
            if (resource == null || resource.key() == null || !resource.key().matches("[A-Za-z0-9._/-]{1,160}")
                    || resource.key().startsWith("/") || Arrays.asList(resource.key().split("/")).contains("..")
                    || resource.assetId() == null || resource.assetId() <= 0 || assets.putIfAbsent(resource.key(), resource.assetId()) != null)
                fail("resources必须使用唯一相对路径key和有效assetId");
        }
        var document = Jsoup.parse("<!doctype html><html lang=zh-CN><head><meta charset=utf-8></head><body></body></html>");
        document.outputSettings().prettyPrint(false);
        document.head().appendElement("meta").attr("name", "kb-schema").attr("content", KbHtmlCodec.SCHEMA);
        if (request.origin() != null) document.head().appendElement("meta").attr("name", "kb-origin").attr("content", request.origin());
        Set<String> keys = new HashSet<>();
        for (var record : request.records()) {
            if (record == null || record.clientKey() == null || record.clientKey().isBlank() || record.clientKey().length() > 160
                    || !keys.add(record.clientKey())) fail("clientKey必须为1至160字符且在批次内唯一");
            int before = document.body().childrenSize();
            try {
            String intent = record.intent() == null ? "create" : record.intent();
            if (!Set.of("create", "update", "derive").contains(intent)) fail("intent必须为create、update或derive");
            if (!"create".equals(intent) && (request.origin() == null || request.origin().isBlank())) fail("更新或变式必须携带origin");
            if ("update".equals(intent) && (record.questionId() == null || record.questionId() <= 0 || record.rowVersion() == null || record.rowVersion() < 0))
                fail("更新必须携带questionId和rowVersion");
            if ("derive".equals(intent) && (record.parentId() == null || record.parentId() <= 0)) fail("变式必须携带parentId");
            Element article = document.body().appendElement("article").attr("data-client-key", record.clientKey()).attr("data-intent", intent);
            if (record.questionId() != null) article.attr("data-question-id", record.questionId().toString());
            if (record.rowVersion() != null) article.attr("data-row-version", record.rowVersion().toString());
            if (record.parentId() != null) article.attr("data-parent-id", record.parentId().toString());
            HtmlMetadata metadata = record.metadata() == null ? HtmlMetadata.empty() : record.metadata();
            var items = record.items();
            if (items != null) {
                if (items.size() < 2 || items.size() > 200) fail("成组items须包含2至200项");
                if (metadata.unitKind() != null && !"GROUP".equals(metadata.unitKind())) fail("items要求unitKind为GROUP");
                metadata = new HtmlMetadata(metadata.title(), metadata.subject(), metadata.questionType(), metadata.difficulty(), metadata.kpIds(),
                        metadata.sourceNote(), metadata.auxiliaryParentIds(), metadata.operation(), metadata.changeNote(), metadata.preservedNote(),
                        metadata.contentKind(), metadata.layoutMode() == null ? "STRUCTURED" : metadata.layoutMode(), "GROUP", metadata.paginationMode(), metadata.layoutNote());
                Set<String> itemKeys = new HashSet<>();
                for (var item : items) if (item == null || item.key() == null || !item.key().matches("[A-Za-z0-9._-]{1,64}") || !itemKeys.add(item.key()))
                    fail("items.key须为唯一的1至64位字母、数字、点、短横线或下划线");
            } else if (record.columns() != null) fail("columns仅用于items成组录入");
            article.appendElement("script").attr("type", "application/json").attr("data-kb-metadata", "")
                    .appendChild(new DataNode(JSON.toJSONString(metadata).replace("<", "\\u003c")));
            appendPart(article, "stem", record.stem(), record.stemHtml(), items, record.columns());
            appendPart(article, "answer", record.answer(), record.answerHtml(), items, record.columns());
            appendPart(article, "analysis", record.analysis(), record.analysisHtml(), items, record.columns());
            if (record.options() != null) {
                if (items != null || record.options().size() < 2 || record.options().size() > 26) fail("独立选择题options须为2至26项");
                Element options = article.appendElement("section").attr("data-part", "options");
                Set<String> optionKeys = new HashSet<>();
                for (var option : record.options()) {
                    if (option == null || option.key() == null || !option.key().matches("[A-Z]") || !optionKeys.add(option.key())) fail("选项key须为唯一A至Z");
                    String html = fragment(option.text(), option.html());
                    if (html == null) fail("选项必须提供text或html");
                    options.appendElement("div").attr("data-option-key", option.key()).html(html);
                }
            }
            for (Element img : article.select("img[src]")) {
                Long assetId = assets.get(img.attr("src"));
                if (assetId != null) {
                    if (img.hasAttr("data-asset-id") && !assetId.toString().equals(img.attr("data-asset-id"))) fail("图片assetId与资源映射不一致");
                    img.attr("data-asset-id", assetId.toString()).attr("src", "kb-asset:" + assetId);
                }
            }
            } catch (ServiceException invalidItem) {
                // Keep a durable item receipt; a malformed sibling must not discard valid records.
                while (document.body().childrenSize() > before) document.body().children().last().remove();
                document.body().appendElement("article").attr("data-client-key", record.clientKey())
                        .attr("data-intent", "create").attr("data-kb-input-error", invalidItem.getMessage());
            }
        }
        byte[] bytes = document.outerHtml().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 2 * 1024 * 1024) fail("转换后的题目正文超过2MiB，请分批提交");
        return bytes;
    }

    private void appendPart(Element article, String part, String text, String html, List<KbRecordsRequest.Item> items, Integer columns) {
        String content = fragment(text, html);
        boolean required = "stem".equals(part);
        if (required && content == null) fail("每条内容必须提供stem或stemHtml，成组内容也需公共题干");
        boolean itemContent = items != null && items.stream().anyMatch(i -> itemPart(i, part) != null);
        if (content == null && !itemContent) return;
        Element section = article.appendElement("section").attr("data-part", part).html(content == null ? "" : content);
        if (items != null) {
            int width = columns == null ? 2 : columns;
            if (width < 2 || width > 4) fail("columns只能为2、3或4");
            Element group = section.appendElement("div").attr("data-kb-role", "group").attr("data-columns", Integer.toString(width));
            for (var item : items) {
                String itemHtml = itemPart(item, part);
                if (required && itemHtml == null) fail("每个items子项必须提供stem或stemHtml");
                group.appendElement("div").attr("data-item-key", item.key()).html(itemHtml == null ? "" : itemHtml);
            }
        }
    }

    private String itemPart(KbRecordsRequest.Item item, String part) {
        return switch (part) {
            case "stem" -> fragment(item.stem(), item.stemHtml());
            case "answer" -> fragment(item.answer(), item.answerHtml());
            default -> fragment(item.analysis(), item.analysisHtml());
        };
    }
    private String fragment(String text, String html) {
        if (text != null && html != null) fail("同一区域的纯文本和Html字段只能二选一");
        if (html != null || text == null) return html;
        Element paragraph = new Element("p");
        String[] lines = text.split("\\r\\n|\\r|\\n", -1);
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) paragraph.appendElement("br");
            paragraph.appendText(lines[index]);
        }
        return paragraph.outerHtml();
    }
    private static void fail(String message) { throw new ServiceException(message, 400); }
}
