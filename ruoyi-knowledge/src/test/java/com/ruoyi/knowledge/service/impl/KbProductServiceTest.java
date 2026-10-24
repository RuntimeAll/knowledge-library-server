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
    void staleExportCannotBeAttachedToChangedMaterial() {
        var mapper = mock(KbProductMapper.class);
        var materials = mock(KbMaterialMapper.class);
        var stored = new KbMaterial();
        stored.setId(7L);
        stored.setRowVersion(9L);
        when(materials.lockById(7L)).thenReturn(stored);
        var input = new KbProduct();
        input.setTitle("旧导出包");
        input.setMaterialId(7L);
        input.setExpectedMaterialRowVersion(8L);
        input.setQuestionRefs(List.of());
        var service = new KbProductServiceImpl(mapper, materials,
                mock(IKbAssetService.class), mock(IKbMaterialService.class),
                mock(com.ruoyi.knowledge.service.IKbCatalogService.class));
        assertEquals(409, assertThrows(ServiceException.class, () -> service.create(input)).getCode());
        verify(mapper, never()).insert(any());
        verify(materials).lockById(7L);
    }

    @Test
    void guardedArchiveRequiresExplicitExportQuestionReferences() {
        var mapper = mock(KbProductMapper.class);
        var materials = mock(KbMaterialMapper.class);
        var stored = new KbMaterial();
        stored.setRowVersion(9L);
        when(materials.lockById(7L)).thenReturn(stored);
        var input = new KbProduct();
        input.setTitle("导出包");
        input.setMaterialId(7L);
        input.setExpectedMaterialRowVersion(9L);
        var service = new KbProductServiceImpl(mapper, materials,
                mock(IKbAssetService.class), mock(IKbMaterialService.class),
                mock(com.ruoyi.knowledge.service.IKbCatalogService.class));
        assertEquals(400, assertThrows(ServiceException.class, () -> service.create(input)).getCode());
        verify(mapper, never()).insert(any());
    }

    private static com.ruoyi.knowledge.catalog.KbCatalogProfile profile(String role) {
        var p = new com.ruoyi.knowledge.catalog.KbCatalogProfile();
        p.setAudienceRole(role);
        return p;
    }

    @Test
    void uploadedAnswerCannotContinueStudentVersionChain() {
        var mapper = mock(KbProductMapper.class);
        var previous = new KbProduct();
        previous.setId(1L);
        previous.setRootProductId(1L);
        previous.setProfile(profile("STUDENT"));
        when(mapper.selectById(1L)).thenReturn(previous);
        when(mapper.lockById(1L)).thenReturn(previous);
        var input = new KbProduct();
        input.setTitle("答案文件");
        input.setAssetId(20L);
        input.setPreviousProductId(1L);
        input.setProfile(profile("INLINE_ANSWER"));
        var service =
                new KbProductServiceImpl(
                        mapper,
                        mock(KbMaterialMapper.class),
                        mock(IKbAssetService.class),
                        mock(IKbMaterialService.class),
                        mock(com.ruoyi.knowledge.service.IKbCatalogService.class));
        assertEquals(
                409, assertThrows(ServiceException.class, () -> service.create(input)).getCode());
        verify(mapper, never()).insert(any());
    }

    @Test
    void generatedOutputRoleCannotBeChangedThroughCatalogMetadata() {
        var mapper = mock(KbProductMapper.class);
        var existing = new KbProduct();
        existing.setId(1L);
        existing.setRootProductId(1L);
        existing.setRowVersion(0L);
        existing.setRenderJobId(88L);
        existing.setRenderOutputKey("STUDENT");
        existing.setProfile(profile("STUDENT"));
        when(mapper.lockById(1L)).thenReturn(existing);
        when(mapper.selectById(1L)).thenReturn(existing);
        var command = new com.ruoyi.knowledge.catalog.KbCatalogCommand();
        command.setRowVersion(0L);
        command.setProfile(profile("ANSWER_ONLY"));
        var service =
                new KbProductServiceImpl(
                        mapper,
                        mock(KbMaterialMapper.class),
                        mock(IKbAssetService.class),
                        mock(IKbMaterialService.class),
                        mock(com.ruoyi.knowledge.service.IKbCatalogService.class));
        assertEquals(
                409,
                assertThrows(ServiceException.class, () -> service.catalog(1L, command, "agent"))
                        .getCode());
        verify(mapper, never()).update(any());
    }

    @Test
    void uploadedVersionRoleCannotBeRelabeled() {
        var mapper = mock(KbProductMapper.class);
        var existing = new KbProduct();
        existing.setId(3L);
        existing.setPreviousProductId(2L);
        existing.setRootProductId(1L);
        existing.setRowVersion(0L);
        existing.setProfile(profile("INLINE_ANSWER"));
        when(mapper.lockById(3L)).thenReturn(existing);
        when(mapper.selectById(3L)).thenReturn(existing);
        var input = new KbProduct();
        input.setId(3L);
        input.setRowVersion(0L);
        input.setProfile(profile("STUDENT"));
        var service =
                new KbProductServiceImpl(
                        mapper,
                        mock(KbMaterialMapper.class),
                        mock(IKbAssetService.class),
                        mock(IKbMaterialService.class),
                        mock(com.ruoyi.knowledge.service.IKbCatalogService.class));
        assertEquals(
                409, assertThrows(ServiceException.class, () -> service.update(input)).getCode());
        verify(mapper, never()).update(any());
    }

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
                        mock(IKbMaterialService.class),
                        mock(com.ruoyi.knowledge.service.IKbCatalogService.class));
        assertThrows(ServiceException.class, () -> service.update(input));
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
                        mock(IKbMaterialService.class),
                        mock(com.ruoyi.knowledge.service.IKbCatalogService.class));
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
        material.setReferenceSources(List.of());
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
                        mapper,
                        materials,
                        mock(IKbAssetService.class),
                        materialService,
                        mock(com.ruoyi.knowledge.service.IKbCatalogService.class));
        service.create(input);
        verify(mapper).lockById(1L);
        assertEquals(3, input.getVersionNo());
        assertEquals(1L, input.getRootProductId());
        assertTrue(input.getSnapshotJson().contains("\"materialId\":\"7\""));
        assertTrue(input.getSnapshotJson().contains("\"materialRowVersion\":\"6\""));
        assertTrue(input.getSnapshotJson().contains("\"questionId\":\"9\""));
    }
}
