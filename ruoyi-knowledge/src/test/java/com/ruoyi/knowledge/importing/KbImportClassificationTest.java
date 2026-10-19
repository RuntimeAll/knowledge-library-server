package com.ruoyi.knowledge.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.html.HtmlQuestionDocument;
import com.ruoyi.knowledge.html.KbHtmlCodec;
import com.ruoyi.knowledge.importing.domain.KbImportBatch;
import com.ruoyi.knowledge.importing.mapper.KbImportMapper;
import com.ruoyi.knowledge.service.IKbQuestionService;
import com.ruoyi.knowledge.service.IKbSourceService;
import java.util.List;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KbImportClassificationTest {
    private final KbHtmlCodec codec = new KbHtmlCodec();
    private final IKbQuestionService questions = mock(IKbQuestionService.class);
    private final KbImportMapper mapper = mock(KbImportMapper.class);
    private final KbImportBatch batch = new KbImportBatch();
    private final KbImportItemWorker worker = new KbImportItemWorker(
            questions, mock(IKbSourceService.class), mapper, codec);
    private KbQuestion saved;

    @BeforeEach
    void setUp() {
        batch.setBatchId(8L);
        batch.setCreateBy("agent");
        batch.setClaimToken("claim");
        when(mapper.lockOwnedBatch(8L, "claim")).thenReturn(batch);
        when(mapper.renewClaim(8L, "claim")).thenReturn(1);
        when(questions.createImportedQuestion(any())).thenAnswer(invocation -> {
            saved = invocation.getArgument(0);
            saved.setId(5L);
            return saved;
        });
        when(questions.updateImportedQuestion(any())).thenAnswer(invocation -> {
            saved = invocation.getArgument(0);
            return saved;
        });
    }

    @Test
    void explicitKnowledgeMetadataPassesFromHtmlToService() {
        HtmlQuestionDocument document = codec.readArticle(Jsoup.parse("""
                <article data-client-key="knowledge-review" data-intent="create">
                <script type="application/json" data-kb-metadata>
                {"contentKind":"KNOWLEDGE","questionType":"KNOWLEDGE_REVIEW","subject":"SCIENCE","kpIds":["36"],"layoutMode":"STRUCTURED","unitKind":"SINGLE","paginationMode":"ALLOW_SPLIT","layoutNote":"保留表格"}
                </script><section data-part="stem">生物分类知识点</section></article>
                """).selectFirst("article"));
        worker.write(batch, 0, resolved(document));
        assertEquals("KNOWLEDGE", saved.getContentKind());
        assertEquals("KNOWLEDGE_REVIEW", saved.getQuestionType());
        assertEquals("SCIENCE", saved.getSubject());
        assertEquals(List.of(36L), saved.getKpIds());
        assertEquals("STRUCTURED", saved.getLayoutMode());
        assertEquals("SINGLE", saved.getUnitKind());
        assertEquals("ALLOW_SPLIT", saved.getPaginationMode());
        assertEquals("保留表格", saved.getLayoutNote());
    }

    @Test
    void omittedMetadataPreservesKnowledgeClassificationDuringBodyUpdate() {
        KbQuestion current = new KbQuestion();
        current.setId(5L);
        current.setRowVersion(2L);
        current.setTitle("生物分类");
        current.setContentKind("KNOWLEDGE");
        current.setQuestionType("KNOWLEDGE_EXPLANATION");
        current.setSubject("SCIENCE");
        current.setKpIds(List.of(36L));
        current.setLayoutMode("STRUCTURED");
        current.setUnitKind("SINGLE");
        current.setPaginationMode("KEEP_TOGETHER");
        current.setLayoutNote("不要改变表格次序");
        when(questions.get(5L)).thenReturn(current);
        HtmlQuestionDocument document = codec.readArticle(Jsoup.parse("""
                <article data-client-key="question-5" data-intent="update" data-question-id="5" data-row-version="2">
                <section data-part="stem">修订知识点说明</section></article>
                """).selectFirst("article"));
        worker.write(batch, 0, resolved(document));
        assertEquals("KNOWLEDGE", saved.getContentKind());
        assertEquals("KNOWLEDGE_EXPLANATION", saved.getQuestionType());
        assertEquals("SCIENCE", saved.getSubject());
        assertEquals(List.of(36L), saved.getKpIds());
        assertEquals("STRUCTURED", saved.getLayoutMode());
        assertEquals("SINGLE", saved.getUnitKind());
        assertEquals("KEEP_TOGETHER", saved.getPaginationMode());
        assertEquals("不要改变表格次序", saved.getLayoutNote());
    }

    private ResolvedArticle resolved(HtmlQuestionDocument document) {
        return new ResolvedArticle(document, codec.sanitize(document.stem()), null, null,
                List.of(), document.fingerprint());
    }
}
