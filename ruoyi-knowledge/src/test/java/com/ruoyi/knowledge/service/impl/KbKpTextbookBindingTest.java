package com.ruoyi.knowledge.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.KbKp;
import com.ruoyi.knowledge.domain.KbOutline;
import com.ruoyi.knowledge.domain.KbKpEdge;
import com.ruoyi.knowledge.mapper.KbKpMapper;
import com.ruoyi.knowledge.mapper.KbOutlineMapper;
import com.ruoyi.knowledge.mapper.KbOutlineNodeMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KbKpTextbookBindingTest {
    final KbKpMapper points = mock(KbKpMapper.class);
    final KbOutlineMapper outlines = mock(KbOutlineMapper.class);
    final KbKpServiceImpl service = new KbKpServiceImpl(points, outlines, mock(KnowledgeDictionary.class));

    KbKp point() {
        KbKp data = new KbKp();
        data.setId(1L); data.setRowVersion(0L); data.setName("有理数运算"); data.setSubject("MATH");
        return data;
    }
    KbOutline textbook(long id, String subject) {
        KbOutline outline = new KbOutline(); outline.setId(id); outline.setCatalogKind("TEXTBOOK");
        outline.setSubject(subject); outline.setRowVersion(0L); return outline;
    }
    @Test void creationRequiresTextbookAndMatchingSubject() {
        KbKp data = point();
        assertThrows(ServiceException.class, () -> service.create(data));
        data.setOutlineIds(List.of(16L));
        when(outlines.lockById(16L)).thenReturn(textbook(16L,"SCIENCE"));
        assertThrows(ServiceException.class, () -> service.create(data));
        KbOutline coverage = textbook(16L,"MATH"); coverage.setCatalogKind("MATERIAL_COVERAGE");
        when(outlines.lockById(16L)).thenReturn(coverage);
        assertThrows(ServiceException.class, () -> service.create(data));
        verify(points, never()).insert(any());
    }
    @Test void omittedOutlineBindingsRemainAndPointHasNoGrade() {
        KbKp input = point();
        when(points.lockById(1L)).thenReturn(point());
        when(points.selectById(1L)).thenReturn(point());
        when(points.selectOutlineIds(1L)).thenReturn(List.of(16L));
        when(outlines.lockById(16L)).thenReturn(textbook(16L,"MATH"));
        when(points.update(any())).thenReturn(1);
        assertEquals(List.of(16L), service.update(input).getOutlineIds());
        verify(points, never()).deleteOutline(any(),any());
    }
    @Test void lastBindingCannotBeRemovedAndChapterBindingCannotBeOrphaned() {
        KbKp input=point(); input.setOutlineIds(List.of());
        when(points.lockById(1L)).thenReturn(point());
        assertThrows(ServiceException.class, () -> service.update(input));
        input.setOutlineIds(List.of(17L));
        when(outlines.lockById(17L)).thenReturn(textbook(17L,"MATH"));
        when(points.selectOutlineIds(1L)).thenReturn(List.of(16L));
        when(points.countChapterLinks(1L,16L)).thenReturn(1L);
        when(points.update(any())).thenReturn(1);
        assertThrows(ServiceException.class, () -> service.update(input));
        verify(points, never()).deleteOutline(1L,16L);
    }
    @Test void linkedOutlineCannotBeDeletedOrChangedToAnotherSubject() {
        var outlineService = new KbOutlineServiceImpl(outlines,mock(KbOutlineNodeMapper.class),points,mock(KnowledgeDictionary.class));
        when(outlines.lockById(16L)).thenReturn(textbook(16L,"MATH"));
        when(points.countOutlinePoints(16L)).thenReturn(1L);
        assertThrows(ServiceException.class, () -> outlineService.delete(new Long[]{16L}));
        assertThrows(ServiceException.class, () -> outlineService.update(textbook(16L,"SCIENCE")));
        verify(outlines, never()).deleteById(any());
        verify(outlines, never()).update(any());
    }

    @Test void existingIncomingEdgesPreventCrossSubjectMove() {
        KbKp input=point(); input.setOutlineIds(List.of(16L));
        KbKp other=point(); other.setId(2L); other.setSubject("SCIENCE");
        KbKpEdge edge=new KbKpEdge(); edge.setFromKpId(2L); edge.setToKpId(1L); edge.setKind("NEST");
        when(points.lockById(1L)).thenReturn(point()); when(points.selectById(2L)).thenReturn(other);
        when(points.selectEdges()).thenReturn(List.of(edge));
        when(outlines.lockById(16L)).thenReturn(textbook(16L,"MATH"));
        assertThrows(ServiceException.class, () -> service.update(input));
        verify(points, never()).update(any());
    }
}
