package com.ruoyi.knowledge.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.KbMaterial;
import com.ruoyi.knowledge.dto.KbQuestionQuery;
import com.ruoyi.knowledge.mapper.KbMaterialMapper;
import com.ruoyi.knowledge.mapper.KbQuestionMapper;
import com.ruoyi.knowledge.mapper.KbSourceMapper;
import com.ruoyi.knowledge.service.IKbKpService;
import com.ruoyi.knowledge.service.IKbQuestionService;
import com.ruoyi.knowledge.service.IKbSourceService;

import org.junit.jupiter.api.Test;

import java.util.List;

class KbMaterialServiceTest {
    @Test
    void examOnlyCannotExpandCandidateScopeBeyondReferences() {
        KbMaterialMapper mapper = mock(KbMaterialMapper.class);
        KbMaterial material = new KbMaterial();
        material.setId(1L);
        material.setSelectionMode("EXAM_ONLY");
        when(mapper.selectById(1L)).thenReturn(material);
        when(mapper.selectQuestionIds(1L)).thenReturn(List.of());
        when(mapper.selectReferences(1L)).thenReturn(List.of());
        KbMaterialServiceImpl service = service(mapper);
        assertThrows(
                ServiceException.class,
                () -> service.prepareCandidates(1L, "ALL", new KbQuestionQuery()));
        KbQuestionQuery query = new KbQuestionQuery();
        service.prepareCandidates(1L, "REFERENCE", query);
        assertEquals(List.of(), query.getSourceIds());
        assertEquals(true, query.getVerifiedOnly());
    }

    @Test
    void examOnlyRejectsSelectedQuestionWithoutOwnVerifiedReference() {
        KbMaterialMapper mapper = mock(KbMaterialMapper.class);
        KbQuestionMapper questions = mock(KbQuestionMapper.class);
        when(questions.lockById(10L)).thenReturn(new com.ruoyi.knowledge.domain.KbQuestion());
        KbMaterialServiceImpl service =
                new KbMaterialServiceImpl(
                        mapper,
                        questions,
                        mock(IKbQuestionService.class),
                        mock(KbSourceMapper.class),
                        mock(IKbSourceService.class),
                        mock(IKbKpService.class));
        KbMaterial material = new KbMaterial();
        material.setTitle("专项");
        material.setSelectionMode("EXAM_ONLY");
        material.setQuestionIds(List.of(10L));
        material.setReferenceSources(List.of());
        assertThrows(ServiceException.class, () -> service.create(material));
        verify(mapper, never()).deleteItems(any());
    }

    private KbMaterialServiceImpl service(KbMaterialMapper mapper) {
        return new KbMaterialServiceImpl(
                mapper,
                mock(KbQuestionMapper.class),
                mock(IKbQuestionService.class),
                mock(KbSourceMapper.class),
                mock(IKbSourceService.class),
                mock(IKbKpService.class));
    }
}
