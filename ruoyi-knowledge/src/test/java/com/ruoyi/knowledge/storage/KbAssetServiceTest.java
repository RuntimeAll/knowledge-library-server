package com.ruoyi.knowledge.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.knowledge.storage.domain.KbAsset;
import com.ruoyi.knowledge.storage.mapper.KbAssetMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class KbAssetServiceTest {
    private KbAssetMapper mapper;
    private ObjectStorage storage;
    private KbStorageProperties properties;
    private KbAssetServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(KbAssetMapper.class);
        storage = mock(ObjectStorage.class);
        properties = new KbStorageProperties();
        properties.setBucket("fixture-bucket");
        properties.setTestMode(true);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        service = new KbAssetServiceImpl(mapper, storage, properties, manager);
        doAnswer(invocation -> { ((KbAsset) invocation.getArgument(0)).setAssetId(1L); return 1; }).when(mapper).insert(any());
        when(mapper.finishUpload(any())).thenReturn(1);
    }

    @Test
    void originalBytesRemainUnchangedAndReceiptRecordsVersionHashAndSafeKey() {
        byte[] bytes = "<html><script>original()</script><p>原件</p></html>".getBytes(StandardCharsets.UTF_8);
        when(storage.put(anyString(), anyString(), any(), anyString())).thenReturn(new ObjectStorage.PutResult("version-7"));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("agent");
            KbAsset result = service.uploadBytes(bytes, "原件.html", "text/html", "PRODUCT_ORIGINAL", "run-1");
            assertTrue(result.getObjectKey().startsWith("knowledge-library/e2e/run-1/product_original/"));
            assertEquals(KbAssetServiceImpl.sha256(bytes), result.getSha256());
            assertEquals("version-7", result.getOssVersionId());
            assertEquals("READY", result.getStatus());
            verify(storage).put(eq("fixture-bucket"), eq(result.getObjectKey()), eq(bytes), eq("text/html"));
        }
    }

    @Test
    void repeatedUploadReturnsSameReceiptWithoutNewPut() {
        byte[] bytes = "content".getBytes(StandardCharsets.UTF_8);
        KbAsset existing = asset(bytes, "READY");
        when(mapper.selectByExternalKey("agent", "external-1")).thenReturn(existing);
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("agent");
            KbAsset actual = service.upload(new MockMultipartFile("file", "file.pdf", "application/pdf", bytes), "SOURCE_ORIGINAL", "run-1", "external-1");
            assertSame(existing, actual);
            verifyNoInteractions(storage);
            verify(mapper, never()).insert(any());
        }
    }

    @Test
    void retriesUncertainUploadUsingRemoteHashWithoutCreatingAnotherObjectVersion() {
        byte[] bytes = "content".getBytes(StandardCharsets.UTF_8);
        KbAsset existing = asset(bytes, "FAILED");
        when(mapper.selectByExternalKey("agent", "external-1")).thenReturn(existing);
        when(mapper.claimRetry(eq(8L), anyString())).thenReturn(1);
        when(storage.stat("fixture-bucket", existing.getObjectKey())).thenReturn(new ObjectStorage.StoredObject("original-version", bytes.length, existing.getSha256()));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("agent");
            KbAsset actual = service.upload(new MockMultipartFile("file", "file.pdf", "application/pdf", bytes), "SOURCE_ORIGINAL", "run-1", "external-1");
            assertEquals("READY", actual.getStatus());
            assertEquals("original-version", actual.getOssVersionId());
            verify(storage, never()).put(anyString(), anyString(), any(), anyString());
        }
    }

    @Test
    void rejectsMismatchedUploadKeyAndUnsafeTestPrefix() {
        when(mapper.selectByExternalKey("agent", "external-1")).thenReturn(asset("old".getBytes(StandardCharsets.UTF_8), "READY"));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("agent");
            assertThrows(ServiceException.class, () -> service.upload(new MockMultipartFile("file", "file.pdf", "application/pdf", "new".getBytes(StandardCharsets.UTF_8)), "SOURCE_ORIGINAL", "run-1", "external-1"));
        }
        assertThrows(ServiceException.class, () -> service.validateTestRunId("../other-project"));
        properties.setTestMode(false);
        assertThrows(ServiceException.class, () -> service.validateTestRunId("run-1"));
        verifyNoInteractions(storage);
    }

    @Test
    void referencedAssetCannotBeDeleted() {
        KbAsset asset = asset(new byte[]{1}, "READY");
        when(mapper.selectByIdForUpdate(8L)).thenReturn(asset);
        when(mapper.countReferences(8L)).thenReturn(1);
        assertThrows(ServiceException.class, () -> service.delete(8L));
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsSvgScriptsWithoutUploading() {
        assertThrows(ServiceException.class, () -> service.uploadBytes("<svg><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8), "bad.svg", "image/svg+xml", "QUESTION_IMAGE", "run-1"));
        verifyNoInteractions(storage);
    }

    @Test void publicationZipSigningDoesNotWidenPreviewOrSignForeignAssets() {
        KbAsset archive = asset(new byte[]{1, 2}, "READY"); archive.setPurpose("IMPORT_RESOURCE");
        archive.setContentType("application/zip"); archive.setOssVersionId("version-7");
        when(mapper.selectByIdForUpdate(8L)).thenReturn(archive);
        when(storage.previewUrl("fixture-bucket", archive.getObjectKey(), "version-7", 600)).thenReturn("https://oss.invalid/signed");
        assertEquals(600, service.publicationDownload(8L).expiresInSeconds());
        assertThrows(ServiceException.class, () -> service.preview(8L));
        archive.setContentType("text/html"); assertThrows(ServiceException.class, () -> service.publicationDownload(8L));
        archive.setContentType("application/zip"); archive.setPurpose("PRODUCT_ORIGINAL"); assertThrows(ServiceException.class, () -> service.publicationDownload(8L));
        archive.setPurpose("IMPORT_RESOURCE"); archive.setObjectKey("another-project/file.zip"); assertThrows(ServiceException.class, () -> service.publicationDownload(8L));
        archive.setObjectKey("knowledge-library/bundle.zip"); archive.setBucketName("other-bucket"); assertThrows(ServiceException.class, () -> service.publicationDownload(8L));
        verify(storage, times(1)).previewUrl(anyString(), anyString(), anyString(), anyInt());
    }

    @Test void publicationImagePreviewSignsCompressedDisplayWithoutReadingOrOverwritingOriginal() {
        KbAsset image = asset(new byte[]{1, 2}, "READY"); image.setStorageProvider("ALIYUN_OSS");
        image.setPurpose("IMPORT_RESOURCE"); image.setContentType("image/png"); image.setOssVersionId("version-7");
        when(mapper.selectByIdForUpdate(8L)).thenReturn(image);
        when(storage.imagePreviewUrl("fixture-bucket", image.getObjectKey(), "version-7", 600))
                .thenReturn("https://fixture-bucket.oss.invalid/preview");
        IKbAssetService.Preview result = service.publicationImagePreview(8L);
        assertEquals(600, result.expiresInSeconds()); assertEquals("image/webp", result.contentType());
        assertEquals("https://fixture-bucket.oss.invalid/preview", result.url());
        assertEquals("image/png", image.getContentType()); assertEquals(2L, image.getFileSize());
        assertEquals("version-7", image.getOssVersionId());
        verify(storage).imagePreviewUrl("fixture-bucket", image.getObjectKey(), "version-7", 600);
        verifyNoMoreInteractions(storage);
        verify(mapper, never()).insert(any()); verify(mapper, never()).updateState(any());
    }

    @Test void publicationImagePreviewRejectsUnreadyForeignAndNonRasterInputsBeforeSigning() {
        KbAsset image = asset(new byte[]{1}, "READY"); image.setStorageProvider("ALIYUN_OSS"); image.setContentType("image/png");
        when(mapper.selectByIdForUpdate(8L)).thenReturn(image);
        image.setStatus("DELETED"); assertThrows(ServiceException.class, () -> service.publicationImagePreview(8L));
        image.setStatus("READY");
        for (String type : java.util.List.of("image/svg+xml", "application/pdf", "text/html")) {
            image.setContentType(type); assertThrows(ServiceException.class, () -> service.publicationImagePreview(8L));
        }
        image.setContentType(null); assertThrows(ServiceException.class, () -> service.publicationImagePreview(8L));
        image.setContentType("image/png"); image.setBucketName("foreign-bucket");
        assertThrows(ServiceException.class, () -> service.publicationImagePreview(8L));
        image.setBucketName("fixture-bucket"); image.setObjectKey("another-project/image.png");
        assertThrows(ServiceException.class, () -> service.publicationImagePreview(8L));
        image.setObjectKey(null); assertThrows(ServiceException.class, () -> service.publicationImagePreview(8L));
        image.setObjectKey("knowledge-library/image.png"); image.setStorageProvider("FOREIGN");
        assertThrows(ServiceException.class, () -> service.publicationImagePreview(8L));
        image.setStorageProvider("ALIYUN_OSS"); image.setFileSize(20L * 1024 * 1024 + 1);
        assertThrows(ServiceException.class, () -> service.publicationImagePreview(8L));
        image.setFileSize(null); assertThrows(ServiceException.class, () -> service.publicationImagePreview(8L));
        verifyNoInteractions(storage);
    }

    @Test void sixPublicationPreviewsUseOneBatchQueryAndEmptyInputUsesNoQuery() {
        assertTrue(service.publicationImagePreviews(java.util.List.of()).isEmpty());
        verifyNoInteractions(mapper, storage);
        java.util.List<Long> ids = java.util.List.of(1L, 2L, 3L, 4L, 5L, 6L);
        java.util.List<KbAsset> images = ids.stream().map(id -> {
            KbAsset image = asset(new byte[]{1}, "READY"); image.setAssetId(id);
            image.setStorageProvider("ALIYUN_OSS"); image.setContentType("image/png"); image.setOssVersionId("version-7");
            image.setObjectKey("knowledge-library/image-" + id + ".png"); return image;
        }).toList();
        when(mapper.selectByIds(ids)).thenReturn(images);
        when(storage.imagePreviewUrl(anyString(), anyString(), eq("version-7"), eq(600)))
                .thenReturn("https://fixture-bucket.oss.invalid/preview");
        java.util.Map<Long, IKbAssetService.Preview> result = service.publicationImagePreviews(ids);
        assertEquals(ids, java.util.List.copyOf(result.keySet()));
        assertTrue(result.values().stream().allMatch(preview -> "image/webp".equals(preview.contentType())));
        verify(mapper, times(1)).selectByIds(ids); verify(mapper, never()).selectByIdForUpdate(anyLong());
        verify(storage, times(6)).imagePreviewUrl(anyString(), anyString(), eq("version-7"), eq(600));
    }

    @Test void batchPreviewMissingOrForeignAssetPreventsAllSigning() {
        KbAsset image = asset(new byte[]{1}, "READY"); image.setStorageProvider("ALIYUN_OSS"); image.setContentType("image/png");
        when(mapper.selectByIds(java.util.List.of(8L, 9L))).thenReturn(java.util.List.of(image));
        assertThrows(ServiceException.class, () -> service.publicationImagePreviews(java.util.List.of(8L, 9L)));
        assertThrows(ServiceException.class, () -> service.publicationImagePreviews(java.util.Arrays.asList(8L, null)));
        verifyNoInteractions(storage);
    }

    private KbAsset asset(byte[] bytes, String status) {
        KbAsset asset = new KbAsset();
        asset.setAssetId(8L);
        asset.setOriginalName("file.pdf");
        asset.setPurpose("SOURCE_ORIGINAL");
        asset.setSha256(KbAssetServiceImpl.sha256(bytes));
        asset.setFileSize((long) bytes.length);
        asset.setStatus(status);
        asset.setBucketName("fixture-bucket");
        asset.setObjectKey("knowledge-library/e2e/run-1/source_original/fixture.pdf");
        asset.setTestRunId("run-1");
        asset.setCreateBy("agent");
        return asset;
    }

    @Test void activeUploadDoesNotRaceOrCreateAnotherObject() {
        byte[] bytes = "content".getBytes(StandardCharsets.UTF_8);
        when(mapper.selectByExternalKey("agent", "external-1")).thenReturn(asset(bytes, "UPLOADING"));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("agent");
            assertThrows(ServiceException.class, () -> service.uploadBytes(bytes, "file.pdf", "application/pdf", "SOURCE_ORIGINAL", "run-1", "external-1"));
            verifyNoInteractions(storage);
        }
    }

    @Test void staleUploadReconcilesRemoteAndRejectsDifferentBytesWithoutOverwrite() {
        byte[] bytes = "content".getBytes(StandardCharsets.UTF_8);
        KbAsset existing = asset(bytes, "UPLOADING");
        when(mapper.selectByExternalKey("agent", "external-1")).thenReturn(existing);
        when(mapper.claimRetry(eq(8L), anyString())).thenReturn(1);
        when(storage.stat(anyString(), anyString())).thenReturn(new ObjectStorage.StoredObject("old", bytes.length, "different-hash"));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("agent");
            assertThrows(ServiceException.class, () -> service.uploadBytes(bytes, "file.pdf", "application/pdf", "SOURCE_ORIGINAL", "run-1", "external-1"));
            verify(storage, never()).put(anyString(), anyString(), any(), anyString());
            assertEquals("FAILED", existing.getStatus());
        }
    }

    @Test void lostUploadOwnershipCannotPublishReadyOrUseUnconditionalStateWrite() {
        byte[] bytes = "content".getBytes(StandardCharsets.UTF_8);
        when(storage.put(anyString(), anyString(), any(), anyString())).thenReturn(new ObjectStorage.PutResult("late"));
        when(mapper.finishUpload(any())).thenReturn(0);
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("agent");
            assertThrows(ServiceException.class, () -> service.uploadBytes(bytes, "file.pdf", "application/pdf", "SOURCE_ORIGINAL", "run-1", "external-1"));
            verify(mapper, never()).updateState(any());
        }
    }

    @Test void sourceAndProductMayShareOriginalButCannotRegisterAQuestionImageAsOriginal() {
        KbAsset shared = asset(new byte[]{1}, "READY");
        when(mapper.selectByIdForUpdate(8L)).thenReturn(shared);
        assertSame(shared, service.requireOriginal(8L));
        shared.setPurpose("PRODUCT_ORIGINAL");
        assertSame(shared, service.requireOriginal(8L));
        shared.setPurpose("QUESTION_IMAGE");
        assertThrows(ServiceException.class, () -> service.requireOriginal(8L));
    }
}
