package com.ruoyi.knowledge.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.KbOutline;
import com.ruoyi.knowledge.mapper.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TextbookOptionsTest {
    @Test void reusesExistingCodesAndAllowsNewPublishersWithoutDictionaryEntries() {
        var options = TextbookOptions.options(List.of("ZHEJIAO", "浙教版", "自定义教材"));
        assertEquals(9, options.grades().size());
        assertEquals(2, options.terms().size());
        assertEquals(3, options.textbookVersions().size());
        assertEquals("ZHEJIAO", TextbookOptions.normalizeVersion(" 浙教版 "));
        TextbookOptions.validate("G7", "FIRST");
        assertThrows(ServiceException.class, () -> TextbookOptions.validate("G10", "FIRST"));
        assertThrows(ServiceException.class, () -> TextbookOptions.validate("G7", "第三学期"));
    }

    @Test void catalogReferencesPreventIdentityChangesAndDeletion() {
        var mapper = mock(KbOutlineMapper.class);
        var service = new KbOutlineServiceImpl(mapper, mock(KbOutlineNodeMapper.class), mock(KbKpMapper.class), mock(KnowledgeDictionary.class));
        var old = new KbOutline(); old.setId(1L); old.setRowVersion(0L); old.setGrade("G7");
        when(mapper.lockById(1L)).thenReturn(old);
        when(mapper.countCatalogReferences(1L)).thenReturn(1L);
        var changed = new KbOutline(); changed.setId(1L); changed.setRowVersion(0L); changed.setGrade("G8");
        assertThrows(ServiceException.class, () -> service.update(changed));
        assertThrows(ServiceException.class, () -> service.delete(new Long[]{1L}));
        verify(mapper, never()).deleteById(1L);
    }
}
