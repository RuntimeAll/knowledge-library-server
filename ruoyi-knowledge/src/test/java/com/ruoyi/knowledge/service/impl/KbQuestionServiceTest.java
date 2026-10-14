package com.ruoyi.knowledge.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        persisted.setQuestionType("APPLICATION");
        persisted.setDifficulty("MEDIUM");
        persisted.setCreationKind("EXTRACTED");
        persisted.setStatus("READY");
        persisted.setSourceNote("已有线索");
        when(mapper.lockById(1L)).thenReturn(persisted);
        when(mapper.selectById(1L)).thenReturn(persisted);
        when(mapper.selectKpIds(1L)).thenReturn(List.of(10L));
        when(mapper.selectAssetIds(1L)).thenReturn(List.of(20L));
        when(points.lockById(10L)).thenReturn(new KbKp());
        when(mapper.update(any())).thenReturn(1);
    }

    @Test
    void referencedQuestionRejectsDeletionBeforeRelationsAreRemoved() {
        when(mapper.countReferences(1L)).thenReturn(1L);
        assertThrows(ServiceException.class, () -> service.delete(new Long[] {1L}));
        verify(mapper, never()).deleteIncomingDerivations(1L);
        verify(mapper, never()).deleteById(1L);
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
        assertEquals("已有线索", input.getSourceNote());
        assertEquals("EXTRACTED", input.getCreationKind());
        assertEquals(List.of(10L), input.getKpIds());
        assertEquals(List.of(20L), input.getAssetIds());
        assertEquals("new-hash", input.getStemHash());
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
}
