package com.ruoyi.knowledge.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.KbOccurrence;
import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.domain.KbSource;
import com.ruoyi.knowledge.mapper.KbOccurrenceMapper;
import com.ruoyi.knowledge.mapper.KbQuestionMapper;
import com.ruoyi.knowledge.mapper.KbSourceMapper;
import com.ruoyi.knowledge.storage.IKbAssetService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class KbSourceServiceTest {
    private KbSourceMapper sources;
    private KbOccurrenceMapper occurrences;
    private KbQuestionMapper questions;
    private KbSourceServiceImpl service;

    @BeforeEach
    void setUp() {
        sources = mock(KbSourceMapper.class);
        occurrences = mock(KbOccurrenceMapper.class);
        questions = mock(KbQuestionMapper.class);
        service =
                new KbSourceServiceImpl(
                        sources,
                        occurrences,
                        questions,
                        mock(IKbAssetService.class),
                        mock(KnowledgeDictionary.class), mock(com.ruoyi.knowledge.service.IKbCatalogService.class));
    }

    @Test
    void nonExamCannotClaimVerifiedExamIdentity() {
        KbSource source = new KbSource();
        source.setTitle("讲义");
        source.setSourceKind("HANDOUT");
        source.setAuthenticityStatus("VERIFIED");
        assertThrows(ServiceException.class, () -> service.create(source));
        verify(sources, never()).insert(any());
    }

    @Test
    void changedStemInvalidatesPreviouslyVerifiedOccurrence() {
        KbOccurrence occurrence = new KbOccurrence();
        occurrence.setQuestionId(1L);
        occurrence.setSourceId(2L);
        occurrence.setMatchStatus("VERIFIED");
        occurrence.setMatchedStemHash("old");
        KbQuestion question = new KbQuestion();
        question.setStemHash("new");
        KbSource source = new KbSource();
        source.setId(2L);
        source.setSourceKind("EXAM");
        KbSource exam = new KbSource();
        exam.setAuthenticityStatus("VERIFIED");
        when(occurrences.selectList(any())).thenReturn(List.of(occurrence));
        when(questions.selectById(1L)).thenReturn(question);
        when(sources.selectById(2L)).thenReturn(source);
        when(sources.selectExam(2L)).thenReturn(exam);
        when(sources.selectAttachments(2L)).thenReturn(List.of());
        KbOccurrence result = service.occurrences(1L, null).get(0);
        assertEquals(false, result.getStemMatches());
        assertEquals(false, result.getIsRealExam());
    }

    @Test
    void callerCannotExcludeExistingPositionByForgingCreateId() {
        KbOccurrence input = new KbOccurrence();
        input.setId(99L);
        input.setSourceId(2L);
        input.setQuestionId(1L);
        input.setQuestionNo("1");
        input.setPageFrom(1);
        when(sources.lockById(2L)).thenReturn(new KbSource());
        when(questions.lockById(1L)).thenReturn(new KbQuestion());
        when(occurrences.countPosition(input))
                .thenAnswer(
                        invocation -> {
                            assertNull(input.getId());
                            return 1L;
                        });
        assertThrows(ServiceException.class, () -> service.createOccurrence(input));
        verify(occurrences, never()).insert(any());
    }
}
