package com.ruoyi.knowledge.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.KbOutline;
import com.ruoyi.knowledge.mapper.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KbTextbookOutlineTest {
    @Test void bindingKnowledgePointsDoesNotInvalidateVerifiedChapterText() {
        var mapper = mock(KbOutlineMapper.class);
        var nodes = mock(KbOutlineNodeMapper.class);
        var points = mock(KbKpMapper.class);
        var service = new KbOutlineServiceImpl(mapper, nodes, points, mock(KnowledgeDictionary.class));
        var outline = new KbOutline(); outline.setId(1L); outline.setStatus("CONFIRMED");
        outline.setCatalogKind("TEXTBOOK"); outline.setSubject("MATH");
        when(mapper.lockById(1L)).thenReturn(outline); when(mapper.selectById(1L)).thenReturn(outline);
        var chapter = new com.ruoyi.knowledge.domain.KbOutlineNode();
        chapter.setId(2L); chapter.setOutlineId(1L); chapter.setTitle("有理数"); chapter.setSortOrder(1); chapter.setRowVersion(0L);
        when(nodes.lockById(2L)).thenReturn(chapter); when(nodes.selectById(2L)).thenReturn(chapter); when(nodes.update(any())).thenReturn(1);
        var input = new com.ruoyi.knowledge.domain.KbOutlineNode();
        input.setId(2L); input.setOutlineId(1L); input.setTitle("有理数"); input.setSortOrder(1); input.setRowVersion(0L);
        input.setKpIds(java.util.List.of());
        service.updateNode(input);
        assertEquals("CONFIRMED", outline.getStatus());
        verify(mapper, never()).update(any());
    }

    @Test
    void textbookCannotBeConfirmedWithoutVerifiedEditionAndChapters() {
        var mapper = mock(KbOutlineMapper.class);
        var dictionary = mock(KnowledgeDictionary.class);
        var service = new KbOutlineServiceImpl(mapper, mock(KbOutlineNodeMapper.class), mock(KbKpMapper.class), dictionary);
        var outline = new KbOutline();
        outline.setTitle("浙教七上数学"); outline.setCatalogKind("TEXTBOOK"); outline.setStatus("CONFIRMED");
        outline.setSubject("MATH"); outline.setTextbookVersion("ZHEJIAO"); outline.setGrade("G7"); outline.setTerm("FIRST");
        assertThrows(ServiceException.class, () -> service.create(outline));
        outline.setEdition("待核验测试版"); outline.setCatalogSource("测试目录依据");
        assertThrows(ServiceException.class, () -> service.create(outline));
        verify(mapper, never()).insert(any());
        verify(dictionary, never()).validate(eq("kb_textbook_version"), any(), any());
    }

    @Test
    void incompleteTextbookDimensionsAreRejected() {
        var mapper = mock(KbOutlineMapper.class);
        var service = new KbOutlineServiceImpl(mapper, mock(KbOutlineNodeMapper.class), mock(KbKpMapper.class), mock(KnowledgeDictionary.class));
        var outline = new KbOutline(); outline.setTitle("只有一个名称");
        assertThrows(ServiceException.class, () -> service.create(outline));
        verify(mapper, never()).insert(any());
    }

    @Test void createCannotBorrowExistingOutlineChaptersForConfirmation() {
        var mapper = mock(KbOutlineMapper.class);
        var service = new KbOutlineServiceImpl(mapper, mock(KbOutlineNodeMapper.class), mock(KbKpMapper.class), mock(KnowledgeDictionary.class));
        var data = new KbOutline(); data.setId(99L); data.setTitle("教材"); data.setSubject("MATH");
        data.setCatalogKind("TEXTBOOK"); data.setTextbookVersion("ZHEJIAO"); data.setGrade("G7"); data.setTerm("FIRST");
        data.setEdition("2024"); data.setCatalogSource("目录"); data.setStatus("CONFIRMED");
        when(mapper.countNodes(99L)).thenReturn(10L);
        assertThrows(ServiceException.class, () -> service.create(data));
        assertNull(data.getId()); verify(mapper, never()).insert(any());
    }

    @Test void changedEditionRequiresConfirmationAgain() {
        var mapper = mock(KbOutlineMapper.class);
        var service = new KbOutlineServiceImpl(mapper, mock(KbOutlineNodeMapper.class), mock(KbKpMapper.class), mock(KnowledgeDictionary.class));
        var old = new KbOutline(); old.setId(1L); old.setRowVersion(0L); old.setStatus("CONFIRMED");
        old.setSubject("MATH"); old.setCatalogKind("TEXTBOOK"); old.setEdition("2024");
        when(mapper.lockById(1L)).thenReturn(old); when(mapper.selectById(1L)).thenReturn(old); when(mapper.update(any())).thenReturn(1);
        var input = new KbOutline(); input.setId(1L); input.setRowVersion(0L); input.setTitle("教材");
        input.setCatalogKind("TEXTBOOK"); input.setSubject("MATH"); input.setTextbookVersion("ZHEJIAO");
        input.setGrade("G7"); input.setTerm("FIRST"); input.setStatus("CONFIRMED"); input.setEdition("2025");
        service.update(input);
        assertEquals("DRAFT", input.getStatus());
    }
}
