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
        when(mapper.claimRetry(8L)).thenReturn(1);
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
}
