package com.ruoyi.knowledge.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.mapper.KbLayoutMapper;
import com.ruoyi.knowledge.service.impl.KnowledgeDictionary;
import com.ruoyi.knowledge.storage.IKbAssetService;

import org.junit.jupiter.api.Test;

class KbExperienceTest {
    @Test
    void publishedVersionCannotBeOverwritten() {
        var mapper = mock(KbLayoutMapper.class);
        var old = new KbExperienceVersion();
        old.setId(3L);
        old.setRowVersion(2L);
        old.setStatus("PUBLISHED");
        when(mapper.lockVersion(3L)).thenReturn(old);
        var input = KbLayoutProtocolTest.template();
        input.setRowVersion(2L);
        var service =
                new KbExperienceServiceImpl(
                        mapper, mock(IKbAssetService.class), mock(KnowledgeDictionary.class));
        assertEquals(
                409,
                assertThrows(ServiceException.class, () -> service.updateVersion(3L, input))
                        .getCode());
        verify(mapper, never()).updateVersion(any());
    }

    @Test
    void referencedTemplateCannotBeDeleted() {
        var mapper = mock(KbLayoutMapper.class);
        when(mapper.lockVersion(3L)).thenReturn(new KbExperienceVersion());
        when(mapper.versionJobs(3L)).thenReturn(1L);
        var service =
                new KbExperienceServiceImpl(
                        mapper, mock(IKbAssetService.class), mock(KnowledgeDictionary.class));
        assertThrows(ServiceException.class, () -> service.deleteVersion(3L));
        verify(mapper, never()).deleteVersion(any());
    }

    @Test
    void staleDraftVersionCannotOverwrite() {
        var mapper = mock(KbLayoutMapper.class);
        var old = new KbExperienceVersion();
        old.setRowVersion(2L);
        old.setStatus("DRAFT");
        when(mapper.lockVersion(3L)).thenReturn(old);
        var input = KbLayoutProtocolTest.template();
        input.setRowVersion(1L);
        var service =
                new KbExperienceServiceImpl(
                        mapper, mock(IKbAssetService.class), mock(KnowledgeDictionary.class));
        assertEquals(
                409,
                assertThrows(ServiceException.class, () -> service.updateVersion(3L, input))
                        .getCode());
    }
}
