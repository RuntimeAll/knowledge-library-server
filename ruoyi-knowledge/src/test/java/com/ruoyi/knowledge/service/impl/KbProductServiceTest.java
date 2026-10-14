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
import com.ruoyi.knowledge.domain.KbMaterial;
import com.ruoyi.knowledge.domain.KbProduct;
import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.mapper.KbMaterialMapper;
import com.ruoyi.knowledge.mapper.KbProductMapper;
import com.ruoyi.knowledge.service.IKbMaterialService;
import com.ruoyi.knowledge.storage.IKbAssetService;

import org.junit.jupiter.api.Test;

import java.util.List;

class KbProductServiceTest {
    @Test
    void metadataCannotForgeFrozenSnapshotOrVersionChain() {
        KbProductMapper mapper = mock(KbProductMapper.class);
        KbProduct stored = new KbProduct();
        stored.setId(3L);
        stored.setRowVersion(0L);
        stored.setAssetId(10L);
        stored.setRootProductId(1L);
        stored.setPreviousProductId(2L);
        stored.setVersionNo(3);
        stored.setSnapshotJson("{\"materialId\":\"7\",\"rowVersion\":\"6\"}");
        when(mapper.lockById(3L)).thenReturn(stored);
        when(mapper.selectById(3L)).thenReturn(stored);
        when(mapper.update(stored)).thenReturn(1);
        KbProduct input = new KbProduct();
        input.setId(3L);
        input.setRowVersion(0L);
        input.setTitle("更新说明");
        input.setAssetId(10L);
        input.setRootProductId(999L);
        input.setPreviousProductId(998L);
        input.setVersionNo(99);
        input.setSnapshotJson("forged");
        KbProductServiceImpl service =
                new KbProductServiceImpl(
                        mapper,
                        mock(KbMaterialMapper.class),
                        mock(IKbAssetService.class),
                        mock(IKbMaterialService.class));
        service.update(input);
        assertEquals(1L, stored.getRootProductId());
        assertEquals(2L, stored.getPreviousProductId());
        assertEquals(3, stored.getVersionNo());
        assertEquals("{\"materialId\":\"7\",\"rowVersion\":\"6\"}", stored.getSnapshotJson());
    }

    @Test
    void existingVersionCannotReplaceFrozenAsset() {
        KbProductMapper mapper = mock(KbProductMapper.class);
        KbProduct stored = new KbProduct();
        stored.setId(1L);
        stored.setRowVersion(0L);
        stored.setAssetId(10L);
        when(mapper.lockById(1L)).thenReturn(stored);
        KbProduct input = new KbProduct();
        input.setId(1L);
        input.setRowVersion(0L);
        input.setAssetId(20L);
        KbProductServiceImpl service =
                new KbProductServiceImpl(
                        mapper,
                        mock(KbMaterialMapper.class),
                        mock(IKbAssetService.class),
                        mock(IKbMaterialService.class));
        assertThrows(ServiceException.class, () -> service.update(input));
        verify(mapper, never()).update(any());
    }

    @Test
    void newVersionLocksRootAndFreezesMaterialAndQuestionVersions() {
        KbProductMapper mapper = mock(KbProductMapper.class);
        KbMaterialMapper materials = mock(KbMaterialMapper.class);
        IKbMaterialService materialService = mock(IKbMaterialService.class);
        KbProduct prior = new KbProduct();
        prior.setId(2L);
        prior.setRootProductId(1L);
        when(mapper.selectById(2L)).thenReturn(prior);
        when(mapper.lockById(1L)).thenReturn(prior);
        when(mapper.nextVersion(1L)).thenReturn(3);
        KbQuestion question = new KbQuestion();
        question.setId(9L);
        question.setRowVersion(4L);
        KbMaterial material = new KbMaterial();
        material.setId(7L);
        material.setRowVersion(6L);
        material.setTitle("原专项");
        material.setQuestions(List.of(question));
        when(materials.lockById(7L)).thenReturn(material);
        when(materialService.get(7L)).thenReturn(material);
        KbProduct input = new KbProduct();
        input.setTitle("成品");
        input.setAssetId(12L);
        input.setMaterialId(7L);
        input.setPreviousProductId(2L);
        when(mapper.insert(input))
                .thenAnswer(
                        call -> {
                            input.setId(3L);
                            return 1;
                        });
        when(mapper.selectById(3L)).thenReturn(input);
        KbProductServiceImpl service =
                new KbProductServiceImpl(
                        mapper, materials, mock(IKbAssetService.class), materialService);
        service.create(input);
        verify(mapper).lockById(1L);
        assertEquals(3, input.getVersionNo());
        assertEquals(1L, input.getRootProductId());
        assertTrue(input.getSnapshotJson().contains("\"materialId\":\"7\""));
        assertTrue(input.getSnapshotJson().contains("\"rowVersion\":\"6\""));
        assertTrue(input.getSnapshotJson().contains("\"questionId\":\"9\""));
    }
}
