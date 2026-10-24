package com.ruoyi.knowledge.exporting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.domain.KbKp;
import com.ruoyi.knowledge.dto.KbKpOutlineBinding;
import com.ruoyi.knowledge.dto.KbLineage;
import com.ruoyi.knowledge.importing.mapper.KbImportMapper;
import com.ruoyi.knowledge.service.IKbKpService;
import com.ruoyi.knowledge.service.IKbMaterialService;
import com.ruoyi.knowledge.service.IKbQuestionService;
import com.ruoyi.knowledge.service.IKbSourceService;
import com.ruoyi.knowledge.storage.IKbAssetService;
import com.ruoyi.knowledge.storage.KbAssetServiceImpl;
import com.ruoyi.knowledge.storage.domain.KbAsset;
import org.junit.jupiter.api.Test;

class KbExportServiceTest {
    private record Entry(String path, byte[] bytes) { }

    @Test
    void producesOfflineRoundTrippablePackageWithExactImageAndRedAnswers() throws Exception {
        IKbQuestionService questions = mock(IKbQuestionService.class);
        IKbKpService points = mock(IKbKpService.class);
        IKbAssetService assets = mock(IKbAssetService.class);
        KbImportMapper imports = mock(KbImportMapper.class);
        when(imports.selectInstanceId()).thenReturn("instance-1");
        KbQuestion question = new KbQuestion();
        question.setId(9L);
        question.setRowVersion(3L);
        question.setTitle("带图公式题");
        question.setSubject("MATH");
        question.setContentKind("KNOWLEDGE");
        question.setQuestionType("KNOWLEDGE_EXPLANATION");
        question.setDifficulty("EASY");
        question.setStatus("DRAFT");
        question.setLayoutMode("STRUCTURED");
        question.setUnitKind("SINGLE");
        question.setPaginationMode("KEEP_TOGETHER");
        question.setLayoutNote("保留数学表达式与图的相对位置");
        question.setItemCount(1);
        question.setStemHtml("<p>\\(x^2\\)</p><img src='kb-asset:4' data-asset-id='4'>");
        question.setAnswerHtml("<p>答案为4</p>");
        question.setAnalysisHtml("<p>过程</p>");
        question.setKpIds(List.of(12L));
        KbKp point = new KbKp();
        point.setName("公式考点");
        point.setSubject("MATH");
        point.setOutlineIds(List.of(9007199254740993L));
        KbKpOutlineBinding binding = new KbKpOutlineBinding();
        binding.setOutlineId(9007199254740993L);
        binding.setOutlineTitle("浙教版七上数学");
        binding.setSubject("MATH");
        binding.setTextbookVersion("ZHEJIAO");
        binding.setGrade("G7");
        binding.setTerm("FIRST");
        binding.setEdition("核对版本");
        binding.setNodeId(9007199254740994L);
        binding.setNodeTitle("已核对章节");
        point.setOutlineBindings(List.of(binding));
        when(points.get(12L)).thenReturn(point);
        question.setAssetIds(List.of(4L));
        when(questions.get(9L)).thenReturn(question);
        KbLineage lineage = new KbLineage();
        lineage.setParents(List.of());
        lineage.setAncestorSources(List.of());
        when(questions.lineage(9L)).thenReturn(lineage);
        byte[] image = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0};
        KbAsset asset = new KbAsset();
        asset.setAssetId(4L);
        asset.setOriginalName("原题图.png");
        asset.setContentType("image/png");
        asset.setFileSize((long) image.length);
        asset.setSha256(KbAssetServiceImpl.sha256(image));
        when(assets.requireReady(4L)).thenReturn(asset);
        when(assets.openContent(4L)).thenAnswer(i -> new ByteArrayInputStream(image));
        KbExportServiceImpl service = new KbExportServiceImpl(questions, mock(IKbMaterialService.class), points,
                mock(IKbSourceService.class), assets, imports);
        List<Entry> entries = unzip(service.export(new KbExportRequest(List.of(9L), null, true)));
        String index = text(entries, "index.html");
        String single = text(entries, "questions/9.html");
        assertTrue(index.contains("data-row-version=\"3\""));
        assertTrue(index.contains("data-intent=\"update\""));
        assertTrue(index.contains("kb-origin"));
        assertTrue(index.contains("color:#c00000"));
        assertTrue(index.contains("data-pagination-mode=\"KEEP_TOGETHER\""));
        assertTrue(index.contains("break-inside:avoid"));
        assertFalse(index.contains("break-after:page"));
        assertTrue(index.contains("src=\"assets/4.png\""));
        assertTrue(single.contains("src=\"../assets/4.png\""));
        assertArrayEquals(image, entries.stream().filter(e -> e.path().equals("assets/4.png")).findFirst().orElseThrow().bytes());
        assertTrue(entries.stream().anyMatch(e -> e.path().equals("vendor/katex/katex.min.js")));
        assertTrue(entries.stream().anyMatch(e -> e.path().endsWith(".woff2")));
        assertTrue(text(entries, "manifest.json").contains("knowledgePoints"));
        assertTrue(text(entries, "manifest.json").contains("directSources"));
        var imported = new com.ruoyi.knowledge.html.KbHtmlCodec()
                .readArticle(org.jsoup.Jsoup.parse(index).selectFirst("article"));
        assertEquals("KNOWLEDGE", imported.metadata().contentKind());
        assertEquals("KNOWLEDGE_EXPLANATION", imported.metadata().questionType());
        assertEquals("STRUCTURED", imported.metadata().layoutMode());
        assertEquals("SINGLE", imported.metadata().unitKind());
        assertEquals("KEEP_TOGETHER", imported.metadata().paginationMode());
        assertEquals("保留数学表达式与图的相对位置", imported.metadata().layoutNote());
        var manifest = com.alibaba.fastjson2.JSON.parseObject(text(entries, "manifest.json"));
        var exported = manifest.getJSONArray("questions").getJSONObject(0);
        assertEquals("KNOWLEDGE", exported.getString("contentKind"));
        assertEquals("MATH", exported.getString("subject"));
        assertEquals("KNOWLEDGE_EXPLANATION", exported.getString("questionType"));
        assertEquals("EASY", exported.getString("difficulty"));
        assertEquals("DRAFT", exported.getString("status"));
        assertEquals("STRUCTURED", exported.getString("layoutMode"));
        assertEquals("SINGLE", exported.getString("unitKind"));
        assertEquals("KEEP_TOGETHER", exported.getString("paginationMode"));
        assertEquals(1, exported.getIntValue("itemCount"));
        var exportedPoint = exported.getJSONArray("knowledgePoints").getJSONObject(0);
        assertEquals("MATH", exportedPoint.getString("subject"));
        assertEquals("9007199254740993", exportedPoint.getJSONArray("outlineIds").get(0));
        var exportedBinding = exportedPoint.getJSONArray("outlineBindings").getJSONObject(0);
        assertEquals("9007199254740993", exportedBinding.get("outlineId"));
        assertEquals("9007199254740994", exportedBinding.get("nodeId"));
        assertEquals("ZHEJIAO", exportedBinding.getString("textbookVersion"));
        assertEquals("G7", exportedBinding.getString("grade"));
        assertEquals("FIRST", exportedBinding.getString("term"));
    }

    @Test
    void requiresExactlyOneSelectionAndRejectsDuplicates() {
        KbExportServiceImpl service = new KbExportServiceImpl(mock(IKbQuestionService.class), mock(IKbMaterialService.class),
                mock(IKbKpService.class), mock(IKbSourceService.class), mock(IKbAssetService.class), mock(KbImportMapper.class));
        assertThrows(ServiceException.class, () -> service.export(new KbExportRequest(List.of(1L), 2L, true)));
        assertThrows(ServiceException.class, () -> service.export(new KbExportRequest(List.of(1L, 1L), null, true)));
    }

    @Test void groupIsOneRoundTrippableQuestionWithStableItemKeysAndPortableLayout() throws Exception {
        IKbQuestionService questions = mock(IKbQuestionService.class);
        KbImportMapper imports = mock(KbImportMapper.class);
        when(imports.selectInstanceId()).thenReturn("instance-1");
        KbQuestion question = new KbQuestion();
        question.setId(15L);
        question.setRowVersion(2L);
        question.setStemHash("a".repeat(64));
        question.setTitle("十道口算");
        question.setQuestionType("CALCULATION");
        question.setContentKind("EXERCISE");
        question.setLayoutMode("STRUCTURED");
        question.setUnitKind("GROUP");
        question.setPaginationMode("ALLOW_SPLIT");
        question.setLayoutNote("两列，空答案保留占位");
        question.setItemCount(10);
        StringBuilder stem = new StringBuilder("<div data-kb-role='group' data-columns='2'>");
        StringBuilder answer = new StringBuilder("<div data-kb-role='group' data-columns='2'>");
        for (int n = 1; n <= 10; n++) {
            stem.append("<div data-item-key='").append(n).append("'>").append(n).append("+1=</div>");
            answer.append("<div data-item-key='").append(n).append("'>").append(n == 10 ? "" : String.valueOf(n + 1)).append("</div>");
        }
        question.setStemHtml(stem.append("</div>").toString());
        question.setAnswerHtml(answer.append("</div>").toString());
        question.setKpIds(List.of());
        when(questions.get(15L)).thenReturn(question);
        KbLineage lineage = new KbLineage();
        lineage.setParents(List.of());
        lineage.setAncestorSources(List.of());
        when(questions.lineage(15L)).thenReturn(lineage);
        IKbMaterialService materials = mock(IKbMaterialService.class);
        var material = new com.ruoyi.knowledge.domain.KbMaterial();
        material.setTitle("整组计算专项");
        material.setRowVersion(8L);
        material.setQuestionIds(List.of(15L));
        var declaredReference = new com.ruoyi.knowledge.domain.KbMaterialReference();
        declaredReference.setSourceId(77L);
        declaredReference.setNote("采用讲义的分层设计；该来源不是本题直接出处");
        material.setReferenceSources(List.of(declaredReference));
        when(materials.get(7L)).thenReturn(material);
        var service = new KbExportServiceImpl(questions, materials, mock(IKbKpService.class),
                mock(IKbSourceService.class), mock(IKbAssetService.class), imports);
        var entries = unzip(service.export(new KbExportRequest(null, 7L, true)));
        String html = text(entries, "index.html");
        var page = org.jsoup.Jsoup.parse(html);
        assertEquals(1, page.select("article").size());
        var codec = new com.ruoyi.knowledge.html.KbHtmlCodec();
        var parsed = codec.readArticle(page.selectFirst("article"));
        assertEquals("GROUP", parsed.metadata().unitKind());
        assertEquals("ALLOW_SPLIT", parsed.metadata().paginationMode());
        assertEquals(10, com.ruoyi.knowledge.html.KbHtmlLayout.validateParts(
                codec.sanitize(parsed.stem()), codec.sanitize(parsed.answer()), null).itemCount());
        assertEquals("10", parsed.answer().select("[data-item-key]").last().attr("data-item-key"));
        assertEquals("", parsed.answer().select("[data-item-key]").last().text());
        assertTrue(html.contains("grid-template-columns:repeat(2,minmax(0,1fr))"));
        var manifest = com.alibaba.fastjson2.JSON.parseObject(text(entries, "manifest.json"));
        assertEquals(1, manifest.getJSONArray("questions").size());
        assertEquals("8", manifest.getString("materialRowVersion"));
        var reference = manifest.getJSONArray("materialReferenceSources").getJSONObject(0);
        assertEquals("77", reference.getString("sourceId"));
        assertEquals("采用讲义的分层设计；该来源不是本题直接出处", reference.getString("note"));
        assertTrue(manifest.getJSONArray("questions").getJSONObject(0).getJSONArray("directSources").isEmpty());
        assertEquals(10, manifest.getJSONArray("questions").getJSONObject(0).getIntValue("itemCount"));
        assertEquals("a".repeat(64), manifest.getJSONArray("questions").getJSONObject(0).getString("stemHash"));
    }

    private List<Entry> unzip(byte[] bytes) throws Exception {
        List<Entry> entries = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) { entries.add(new Entry(entry.getName(), zip.readAllBytes())); }
        }
        return entries;
    }

    private String text(List<Entry> entries, String name) {
        return new String(entries.stream().filter(e -> e.path().equals(name)).findFirst().orElseThrow().bytes(), StandardCharsets.UTF_8);
    }
}
