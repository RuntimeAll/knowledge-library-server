package com.ruoyi.knowledge.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.KbKp;
import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.mapper.KbKpMapper;
import com.ruoyi.knowledge.mapper.KbOccurrenceMapper;
import com.ruoyi.knowledge.mapper.KbQuestionMapper;
import com.ruoyi.knowledge.service.IKbKpService;
import com.ruoyi.knowledge.storage.IKbAssetService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

class KbQuestionServiceTest {
    private KbQuestionMapper mapper;
    private KbQuestionServiceImpl service;
    private KbQuestion persisted;
    private KbKp point;

    @BeforeEach
    void setUp() {
        mapper = mock(KbQuestionMapper.class);
        KbKpMapper points = mock(KbKpMapper.class);
        service =
                new KbQuestionServiceImpl(
                        mapper,
                        points,
                        mock(IKbKpService.class),
                        mock(IKbAssetService.class),
                        mock(KbOccurrenceMapper.class),
                        mock(KnowledgeDictionary.class));
        persisted = new KbQuestion();
        persisted.setId(1L);
        persisted.setRowVersion(3L);
        persisted.setTitle("原题");
        persisted.setStemHtml("<p>1+1</p>");
        persisted.setAnswerHtml("2");
        persisted.setAnalysisHtml("原解析");
        persisted.setStemHash("current-hash");
        persisted.setSubject("MATH");
        persisted.setContentKind("EXERCISE");
        persisted.setQuestionType("APPLICATION");
        persisted.setDifficulty("MEDIUM");
        persisted.setCreationKind("EXTRACTED");
        persisted.setStatus("READY");
        persisted.setSourceNote("已有线索");
        when(mapper.lockById(1L)).thenReturn(persisted);
        when(mapper.selectById(1L)).thenReturn(persisted);
        when(mapper.selectKpIds(1L)).thenReturn(List.of(10L));
        when(mapper.selectAssetIds(1L)).thenReturn(List.of(20L));
        point = new KbKp();
        point.setSubject("MATH");
        when(points.lockById(10L)).thenReturn(point);
        when(mapper.update(any())).thenReturn(1);
    }

    @Test
    void referencedQuestionRejectsDeletionBeforeRelationsAreRemoved() {
        when(mapper.countReferences(1L)).thenReturn(1L);
        assertThrows(ServiceException.class, () -> service.delete(new Long[] {1L}));
        verify(mapper, never()).deleteIncomingDerivations(1L);
        verify(mapper, never()).deleteById(1L);
    }

    @Test void imageFilterFlagIsDerivedFromStoredContentRatherThanClientMetadata() {
        KbQuestion input = new KbQuestion(); input.setId(1L); input.setRowVersion(3L); input.setHasImage(true);
        service.updateMetadata(input);
        assertEquals(false,persisted.getHasImage());
        persisted.setAnalysisHtml("<p>图示</p><svg><circle r='5'/></svg>");
        input.setHasImage(false);
        service.updateMetadata(input);
        assertEquals(true,persisted.getHasImage());
    }

    @Test
    void metadataEndpointCannotReplaceBodyOrAssets() {
        KbQuestion input = new KbQuestion();
        input.setId(1L);
        input.setRowVersion(3L);
        input.setTitle("新标题");
        input.setStemHtml("injected body");
        input.setAnswerHtml("injected answer");
        input.setAnalysisHtml("injected analysis");
        input.setAssetIds(List.of(999L));
        service.updateMetadata(input);
        ArgumentCaptor<KbQuestion> captor = ArgumentCaptor.forClass(KbQuestion.class);
        verify(mapper).update(captor.capture());
        assertEquals("<p>1+1</p>", captor.getValue().getStemHtml());
        assertEquals("原解析", captor.getValue().getAnalysisHtml());
        assertEquals(List.of(20L), captor.getValue().getAssetIds());
        assertEquals("新标题", captor.getValue().getTitle());
    }

    @Test
    void importWithoutMetadataRetainsMetadataAndRelations() {
        persisted.setContentKind("KNOWLEDGE");
        persisted.setQuestionType("KNOWLEDGE_EXPLANATION");
        persisted.setLayoutMode("STRUCTURED");
        persisted.setUnitKind("SINGLE");
        persisted.setPaginationMode("KEEP_TOGETHER");
        persisted.setLayoutNote("保留表格对齐");
        KbQuestion input = new KbQuestion();
        input.setId(1L);
        input.setRowVersion(3L);
        input.setStemHtml("<p>1+2</p>");
        input.setAnswerHtml("3");
        input.setAnalysisHtml("新解析");
        input.setStemHash("new-hash");
        service.updateImportedQuestion(input);
        assertEquals("原题", input.getTitle());
        assertEquals("MATH", input.getSubject());
        assertEquals("KNOWLEDGE", input.getContentKind());
        assertEquals("KNOWLEDGE_EXPLANATION", input.getQuestionType());
        assertEquals("已有线索", input.getSourceNote());
        assertEquals("EXTRACTED", input.getCreationKind());
        assertEquals(List.of(10L), input.getKpIds());
        assertEquals(List.of(20L), input.getAssetIds());
        assertEquals("new-hash", input.getStemHash());
        assertEquals("STRUCTURED", input.getLayoutMode());
        assertEquals("SINGLE", input.getUnitKind());
        assertEquals("KEEP_TOGETHER", input.getPaginationMode());
        assertEquals("保留表格对齐", input.getLayoutNote());
    }

    @Test
    void staleVersionRejectsBeforePersistence() {
        KbQuestion input = new KbQuestion();
        input.setId(1L);
        input.setRowVersion(2L);
        assertThrows(ServiceException.class, () -> service.updateMetadata(input));
        verify(mapper, never()).update(any());
    }

    @Test
    void derivedIdentityCannotBeForgedByMetadataEdit() {
        KbQuestion input = new KbQuestion();
        input.setId(1L);
        input.setRowVersion(3L);
        input.setCreationKind("DERIVED");
        assertThrows(ServiceException.class, () -> service.updateMetadata(input));
        verify(mapper, never()).update(any());
    }

    @Test
    void readyContentRequiresSubjectAndQuestionTypeInAdditionToBodyAndKnowledgePoint() {
        KbQuestion input = metadataInput();
        input.setSubject("");
        input.setQuestionType("");
        ServiceException exception = assertThrows(ServiceException.class, () -> service.updateMetadata(input));
        assertTrue(exception.getMessage().contains("缺少学科"));
        assertTrue(exception.getMessage().contains("缺少题型"));
        verify(mapper, never()).update(any());
    }

    @Test
    void knowledgeAndExerciseTypesCannotBeMixedEvenForDrafts() {
        KbQuestion input = metadataInput();
        input.setStatus("DRAFT");
        input.setContentKind("KNOWLEDGE");
        input.setQuestionType("CALCULATION");
        assertThrows(ServiceException.class, () -> service.updateMetadata(input));
        input.setContentKind("EXERCISE");
        input.setQuestionType("KNOWLEDGE_REVIEW");
        assertThrows(ServiceException.class, () -> service.updateMetadata(input));
        verify(mapper, never()).update(any());
    }

    @Test
    void knowledgeReviewCanBeReadyAndMetadataEditPreservesItsBody() {
        KbQuestion input = metadataInput();
        input.setContentKind("KNOWLEDGE");
        input.setQuestionType("KNOWLEDGE_REVIEW");
        KbQuestion saved = service.updateMetadata(input);
        assertEquals("KNOWLEDGE", saved.getContentKind());
        assertEquals("READY", saved.getStatus());
        assertEquals("<p>1+1</p>", saved.getStemHtml());
    }

    @Test
    void questionCannotBeAttachedToAnotherSubjectsKnowledgePoint() {
        point.setSubject("SCIENCE");
        assertThrows(ServiceException.class, () -> service.updateMetadata(metadataInput()));
        verify(mapper, never()).update(any());
    }

    @Test
    void missingClassificationIsVisibleForLegacyDraftsAndLists() {
        persisted.setContentKind(null);
        persisted.setQuestionType(null);
        persisted.setStatus("DRAFT");
        KbQuestion detail = service.get(1L);
        assertEquals("EXERCISE", detail.getContentKind());
        assertTrue(detail.getReadinessReasons().contains("缺少题型"));
        when(mapper.selectList(any())).thenReturn(List.of(persisted));
        assertTrue(service.list(new com.ruoyi.knowledge.dto.KbQuestionQuery())
                .get(0).getReadinessReasons().contains("缺少题型"));
    }

    @Test
    void draftCanRetainAnExplicitlyUnclassifiedType() {
        KbQuestion input = metadataInput();
        input.setQuestionType("");
        input.setStatus("DRAFT");
        KbQuestion saved = service.updateMetadata(input);
        assertEquals("DRAFT", saved.getStatus());
        assertTrue(saved.getReadinessReasons().contains("缺少题型"));
    }

    private KbQuestion metadataInput() {
        KbQuestion input = new KbQuestion();
        input.setId(1L);
        input.setRowVersion(3L);
        return input;
    }

    @Test void groupedCountComesFromBodyAndMetadataCannotDetachItFromItsStructure() {
        persisted.setStemHtml("<div data-kb-role='group' data-columns='2'><div data-item-key='1'>1+1</div><div data-item-key='2'>2+2</div></div>");
        persisted.setUnitKind("GROUP");
        persisted.setLayoutMode("STRUCTURED");
        KbQuestion input = metadataInput();
        input.setItemCount(999);
        KbQuestion saved = service.updateMetadata(input);
        assertEquals(2, saved.getItemCount());
        assertEquals(false, saved.getHasImage());
        input.setUnitKind("SINGLE");
        assertThrows(ServiceException.class, () -> service.updateMetadata(input));
    }

    @Test void plainImageQuestionRemainsPlainAndOldTextChoiceNeedsNoNewOptions() {
        persisted.setStemHtml("<p>选择A或B</p><img src='kb-asset:20' data-asset-id='20'>");
        persisted.setQuestionType("CHOICE");
        KbQuestion saved = service.updateMetadata(metadataInput());
        assertEquals("PLAIN", saved.getLayoutMode());
        assertEquals("SINGLE", saved.getUnitKind());
        assertEquals("FLOW", saved.getPaginationMode());
        assertEquals(1, saved.getItemCount());
        assertEquals(true, saved.getHasImage());
    }

    @Test void controlledOptionsRequireChoiceTypeButNeverRewriteItAutomatically() {
        persisted.setStemHtml("<p>选择</p><div data-kb-role='options'><div data-option-key='A'>1</div><div data-option-key='B'>2</div></div>");
        assertThrows(ServiceException.class, () -> service.updateMetadata(metadataInput()));
        KbQuestion input = metadataInput();
        input.setQuestionType("CHOICE");
        assertEquals("CHOICE", service.updateMetadata(input).getQuestionType());
    }

    @Test void rejectsUnsupportedPaginationAndOversizedLayoutNoteBeforePersistence() {
        KbQuestion input = metadataInput();
        input.setPaginationMode("FORCE_FIXED_HEIGHT");
        assertThrows(ServiceException.class, () -> service.updateMetadata(input));
        input.setPaginationMode("FLOW");
        input.setLayoutNote("x".repeat(2001));
        assertThrows(ServiceException.class, () -> service.updateMetadata(input));
        verify(mapper, never()).update(any());
    }
}
