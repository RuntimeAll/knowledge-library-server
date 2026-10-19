package com.ruoyi.knowledge.exporting;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.KbKp;
import com.ruoyi.knowledge.domain.KbMaterial;
import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.dto.KbLineage;
import com.ruoyi.knowledge.html.HtmlMetadata;
import com.ruoyi.knowledge.html.KbHtmlCodec;
import com.ruoyi.knowledge.html.KbHtmlLayout;
import com.ruoyi.knowledge.importing.mapper.KbImportMapper;
import com.ruoyi.knowledge.importing.dto.KbRecordsRequest;
import com.ruoyi.knowledge.service.IKbKpService;
import com.ruoyi.knowledge.service.IKbMaterialService;
import com.ruoyi.knowledge.service.IKbQuestionService;
import com.ruoyi.knowledge.service.IKbSourceService;
import com.ruoyi.knowledge.storage.IKbAssetService;
import com.ruoyi.knowledge.storage.KbAssetServiceImpl;
import com.ruoyi.knowledge.storage.domain.KbAsset;
import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

@Service
public class KbExportServiceImpl implements IKbExportService {
    private static final long MAX_ASSET_BYTES = 200L * 1024 * 1024;
    private final IKbQuestionService questions;
    private final IKbMaterialService materials;
    private final IKbKpService knowledgePoints;
    private final IKbSourceService sources;
    private final IKbAssetService assets;
    private final KbImportMapper imports;

    public KbExportServiceImpl(IKbQuestionService questions, IKbMaterialService materials,
                           IKbKpService knowledgePoints, IKbSourceService sources,
                           IKbAssetService assets, KbImportMapper imports) {
        this.questions = questions;
        this.materials = materials;
        this.knowledgePoints = knowledgePoints;
        this.sources = sources;
        this.assets = assets;
        this.imports = imports;
    }

    public byte[] export(KbExportRequest request) {
        if (request == null || (request.materialId() == null) == (request.questionIds() == null || request.questionIds().isEmpty())) {
            throw new ServiceException("请提供题目ID列表或专项ID，且只能提供一种");
        }
        List<Long> ids;
        String title = "知识库选题导出";
        if (request.materialId() != null) {
            materials.assertSelectionValid(request.materialId());
            KbMaterial material = materials.get(request.materialId());
            title = material.getTitle();
            ids = material.getQuestionIds();
        } else {
            ids = request.questionIds();
        }
        if (ids == null || ids.isEmpty() || ids.size() > 200 || ids.stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new ServiceException("导出请选择1至200个不重复的题目");
        }
        boolean answers = !Boolean.FALSE.equals(request.includeAnswers());
        String origin = imports.selectInstanceId();
        List<KbQuestion> selected = ids.stream().map(questions::get).toList();
        List<KbAsset> files = assetList(selected, answers);
        List<KbExportManifest.QuestionEntry> entries = new ArrayList<>();
        List<KbExportManifest.AssetEntry> fileEntries = new ArrayList<>();
        List<KbRecordsRequest.Record> jsonRecords = new ArrayList<>();
        Document index = document(title, origin, "");
        long fileBytes = 0;
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (KbQuestion question : selected) {
                jsonRecords.add(new KbRecordsRequest.Record("question-" + question.getId(), "update",
                        question.getId(), question.getRowVersion(), null, metadata(question), null, question.getStemHtml(),
                        null, answers ? question.getAnswerHtml() : null, null, answers ? question.getAnalysisHtml() : null,
                        null, null, null));
                Element article = article(question, "", answers);
                index.body().appendChild(article);
                Document single = document(question.getTitle(), origin, "../");
                single.body().appendChild(article(question, "../", answers));
                put(zip, "questions/" + question.getId() + ".html", single.outerHtml().getBytes(StandardCharsets.UTF_8));
                List<KbExportManifest.KnowledgePoint> points = new ArrayList<>();
                for (Long kpId : question.getKpIds() == null ? List.<Long>of() : question.getKpIds()) {
                    KbKp point = knowledgePoints.get(kpId);
                    List<String> outlineIds = point.getOutlineIds() == null ? List.of()
                            : point.getOutlineIds().stream().map(String::valueOf).toList();
                    List<KbExportManifest.OutlineBinding> bindings = point.getOutlineBindings() == null ? List.of()
                            : point.getOutlineBindings().stream().map(binding -> new KbExportManifest.OutlineBinding(
                                    binding.getOutlineId().toString(), binding.getOutlineTitle(), binding.getSubject(),
                                    binding.getTextbookVersion(), binding.getGrade(), binding.getTerm(), binding.getEdition(),
                                    binding.getNodeId() == null ? null : binding.getNodeId().toString(), binding.getNodeTitle())).toList();
                    points.add(new KbExportManifest.KnowledgePoint(kpId.toString(), point.getName(),
                            point.getSubject(), point.getParentId() == null ? null : point.getParentId().toString(),
                            point.getRootId() == null ? null : point.getRootId().toString(), point.getRootName(), point.getDepth(), outlineIds, bindings));
                }
                KbLineage lineage = questions.lineage(question.getId());
                entries.add(new KbExportManifest.QuestionEntry(question.getId().toString(), question.getRowVersion().toString(),
                        question.getTitle(), "questions/" + question.getId() + ".html", question.getCreationKind(), question.getSourceNote(),
                        question.getContentKind(), question.getSubject(), question.getQuestionType(), question.getDifficulty(), question.getStatus(),
                        question.getLayoutMode(), question.getUnitKind(), question.getPaginationMode(), question.getLayoutNote(), question.getItemCount(), question.getHasImage(),
                        points, sources.occurrences(question.getId(), null), lineage.getParents(), lineage.getAncestorSources()));
            }
            for (KbAsset file : files) {
                fileBytes += file.getFileSize();
                if (fileBytes > MAX_ASSET_BYTES) { throw new ServiceException("导出题图总量超过200MiB，请分批导出"); }
                byte[] bytes;
                try (InputStream stream = assets.openContent(file.getAssetId())) {
                    bytes = stream.readNBytes(Math.toIntExact(file.getFileSize()) + 1);
                }
                if (bytes.length != file.getFileSize() || !KbAssetServiceImpl.sha256(bytes).equals(file.getSha256())) {
                    throw new ServiceException("题图内容校验失败，请检查文件资产");
                }
                String path = assetPath(file);
                put(zip, path, bytes);
                fileEntries.add(new KbExportManifest.AssetEntry(file.getAssetId().toString(), path, file.getOriginalName(),
                        file.getContentType(), file.getFileSize(), file.getSha256()));
            }
            put(zip, "index.html", index.outerHtml().getBytes(StandardCharsets.UTF_8));
            KbExportManifest manifest = new KbExportManifest(KbHtmlCodec.SCHEMA, origin, OffsetDateTime.now().toString(), title,
                    request.materialId() == null ? null : request.materialId().toString(), answers, entries, fileEntries);
            put(zip, "manifest.json", JSON.toJSONString(manifest, JSONWriter.Feature.PrettyFormat).getBytes(StandardCharsets.UTF_8));
            KbRecordsRequest jsonRequest = new KbRecordsRequest(KbRecordsRequest.SCHEMA,
                    "export-" + java.util.UUID.randomUUID(), null, origin, jsonRecords, List.of());
            put(zip, "records.json", JSON.toJSONString(jsonRequest, JSONWriter.Feature.PrettyFormat,
                    JSONWriter.Feature.WriteLongAsString).getBytes(StandardCharsets.UTF_8));
            copyVendor(zip);
            zip.finish();
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new ServiceException("生成离线导出包失败");
        }
    }

    private List<KbAsset> assetList(List<KbQuestion> selected, boolean answers) {
        Set<Long> ids = new LinkedHashSet<>();
        for (KbQuestion question : selected) {
            collectImages(question.getStemHtml(), ids);
            if (answers) {
                collectImages(question.getAnswerHtml(), ids);
                collectImages(question.getAnalysisHtml(), ids);
            }
        }
        return ids.stream().map(assets::requireReady).toList();
    }

    private void collectImages(String html, Set<Long> ids) {
        for (Element image : Jsoup.parseBodyFragment(html == null ? "" : html).select("img")) {
            try { ids.add(Long.valueOf(image.attr("data-asset-id"))); }
            catch (NumberFormatException e) { throw new ServiceException("题图缺少有效资产登记，不能离线导出"); }
        }
    }

    private Document document(String title, String origin, String relativeRoot) {
        Document doc = Document.createShell("");
        doc.outputSettings().prettyPrint(false).charset(StandardCharsets.UTF_8);
        doc.prependChild(new org.jsoup.nodes.DocumentType("html", "", ""));
        doc.selectFirst("html").attr("lang", "zh-CN");
        doc.head().appendElement("meta").attr("charset", "utf-8");
        doc.head().appendElement("meta").attr("name", "kb-schema").attr("content", KbHtmlCodec.SCHEMA);
        doc.head().appendElement("meta").attr("name", "kb-origin").attr("content", origin);
        doc.head().appendElement("title").text(title == null ? "知识库题目" : title);
        doc.head().appendElement("link").attr("rel", "stylesheet").attr("href", relativeRoot + "vendor/katex/katex.min.css");
        doc.head().appendElement("style").appendChild(new DataNode("body{max-width:960px;margin:32px auto;padding:0 24px;font:18px/1.8 serif;color:#000;background:#fff}article{margin-bottom:32px}table{border-collapse:collapse;color:#000}td,th{border:1px solid #000;padding:4px 8px}img,svg{max-width:100%;height:auto}[data-part=answer],[data-part=analysis]{color:#c00000}[data-part=answer] *,[data-part=analysis] *{color:inherit}h2{font-size:20px}@media print{body{margin:0}}" + KbHtmlLayout.CSS));
        doc.head().appendElement("script").attr("defer", "").attr("src", relativeRoot + "vendor/katex/katex.min.js");
        doc.head().appendElement("script").attr("defer", "").attr("src", relativeRoot + "vendor/katex/contrib/auto-render.min.js");
        doc.head().appendElement("script").attr("defer", "").attr("src", relativeRoot + "vendor/render.js");
        return doc;
    }

    private Element article(KbQuestion question, String root, boolean answers) {
        Element article = new Element("article");
        article.attr("data-client-key", "question-" + question.getId());
        article.attr("data-intent", "update");
        article.attr("data-question-id", question.getId().toString());
        article.attr("data-row-version", question.getRowVersion().toString());
        article.attr("data-layout-mode", question.getLayoutMode() == null ? "PLAIN" : question.getLayoutMode());
        article.attr("data-unit-kind", question.getUnitKind() == null ? "SINGLE" : question.getUnitKind());
        article.attr("data-pagination-mode", question.getPaginationMode() == null ? "FLOW" : question.getPaginationMode());
        article.appendElement("h2").text(question.getTitle());
        String json = JSON.toJSONString(metadata(question)).replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026");
        article.appendElement("script").attr("type", "application/json").attr("data-kb-metadata", "")
                .appendChild(new DataNode(json));
        appendPart(article, "stem", question.getStemHtml(), root);
        if (answers) {
            appendPart(article, "answer", question.getAnswerHtml(), root);
            appendPart(article, "analysis", question.getAnalysisHtml(), root);
        }
        return article;
    }

    private HtmlMetadata metadata(KbQuestion question) {
        return new HtmlMetadata(question.getTitle(), question.getSubject(), question.getQuestionType(),
                question.getDifficulty(), question.getKpIds(), question.getSourceNote(), null, null, null, null, question.getContentKind(),
                question.getLayoutMode(), question.getUnitKind(), question.getPaginationMode(), question.getLayoutNote());
    }

    private void appendPart(Element article, String name, String html, String root) {
        Element part = article.appendElement("section").attr("data-part", name).html(html == null ? "" : html);
        for (Element image : part.select("img")) {
            if (!image.hasAttr("data-asset-id")) { throw new ServiceException("题图缺少资产登记，不能离线导出"); }
            Long id;
            try { id = Long.valueOf(image.attr("data-asset-id")); }
            catch (NumberFormatException e) { throw new ServiceException("题图资产编号无效"); }
            image.attr("src", root + assetPath(assets.requireReady(id)));
        }
    }

    private String assetPath(KbAsset asset) {
        String extension = switch (asset.getContentType()) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/svg+xml" -> "svg";
            default -> throw new ServiceException("题图格式不支持离线导出");
        };
        return "assets/" + asset.getAssetId() + "." + extension;
    }

    private void copyVendor(ZipOutputStream zip) throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath*:knowledge/vendor/**/*");
        boolean katex = false;
        Set<String> copied = new HashSet<>();
        for (Resource resource : resources) {
            String url = resource.getURL().toExternalForm();
            int start = url.indexOf("knowledge/vendor/");
            if (start < 0 || url.endsWith("/") || !resource.isReadable()) { continue; }
            String relative = url.substring(start + "knowledge/".length());
            if (!copied.add(relative)) { continue; }
            try (InputStream stream = resource.getInputStream()) { put(zip, relative, stream.readAllBytes()); }
            if (relative.equals("vendor/katex/katex.min.js")) { katex = true; }
        }
        if (!katex) { throw new ServiceException("离线公式资源缺失，请重新构建知识库模块"); }
    }

    private void put(ZipOutputStream zip, String path, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(bytes);
        zip.closeEntry();
    }
}
