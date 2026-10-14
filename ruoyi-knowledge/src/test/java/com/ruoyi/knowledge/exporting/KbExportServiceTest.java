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
        IKbAssetService assets = mock(IKbAssetService.class);
        KbImportMapper imports = mock(KbImportMapper.class);
        when(imports.selectInstanceId()).thenReturn("instance-1");
        KbQuestion question = new KbQuestion();
        question.setId(9L);
        question.setRowVersion(3L);
        question.setTitle("带图公式题");
        question.setStemHtml("<p>\\(x^2\\)</p><img src='kb-asset:4' data-asset-id='4'>");
        question.setAnswerHtml("<p>答案为4</p>");
        question.setAnalysisHtml("<p>过程</p>");
        question.setKpIds(List.of());
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
        KbExportServiceImpl service = new KbExportServiceImpl(questions, mock(IKbMaterialService.class), mock(IKbKpService.class),
                mock(IKbSourceService.class), assets, imports);
        List<Entry> entries = unzip(service.export(new KbExportRequest(List.of(9L), null, true)));
        String index = text(entries, "index.html");
        String single = text(entries, "questions/9.html");
        assertTrue(index.contains("data-row-version=\"3\""));
        assertTrue(index.contains("data-intent=\"update\""));
        assertTrue(index.contains("kb-origin"));
        assertTrue(index.contains("color:#c00000"));
        assertTrue(index.contains("src=\"assets/4.png\""));
        assertTrue(single.contains("src=\"../assets/4.png\""));
        assertArrayEquals(image, entries.stream().filter(e -> e.path().equals("assets/4.png")).findFirst().orElseThrow().bytes());
        assertTrue(entries.stream().anyMatch(e -> e.path().equals("vendor/katex/katex.min.js")));
        assertTrue(entries.stream().anyMatch(e -> e.path().endsWith(".woff2")));
        assertTrue(text(entries, "manifest.json").contains("knowledgePoints"));
        assertTrue(text(entries, "manifest.json").contains("directSources"));
    }

    @Test
    void requiresExactlyOneSelectionAndRejectsDuplicates() {
        KbExportServiceImpl service = new KbExportServiceImpl(mock(IKbQuestionService.class), mock(IKbMaterialService.class),
                mock(IKbKpService.class), mock(IKbSourceService.class), mock(IKbAssetService.class), mock(KbImportMapper.class));
        assertThrows(ServiceException.class, () -> service.export(new KbExportRequest(List.of(1L), 2L, true)));
        assertThrows(ServiceException.class, () -> service.export(new KbExportRequest(List.of(1L, 1L), null, true)));
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
