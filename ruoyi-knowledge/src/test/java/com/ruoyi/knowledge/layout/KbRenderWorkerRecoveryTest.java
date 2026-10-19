package com.ruoyi.knowledge.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.mapper.KbLayoutMapper;
import com.ruoyi.knowledge.storage.IKbAssetService;
import com.ruoyi.knowledge.storage.domain.KbAsset;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.nio.file.*;
import java.util.*;

/**
 * Exercises the real worker around a previously sealed render, without OSS/network fault switches.
 */
class KbRenderWorkerRecoveryTest {
    @TempDir Path root;
    KbLayoutMapper mapper;
    IKbAssetService assets;
    KbFrozenProductArchiver archiver;
    KbRenderWorker worker;
    KbRenderJob job;
    List<KbLayoutAsset> references = new ArrayList<>();
    List<String> terminalStates = new ArrayList<>();
    byte[] pdf = "%PDF-fixture".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    byte[] pack = "sealed-fixture".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    @BeforeEach
    void setup() throws Exception {
        mapper = mock(KbLayoutMapper.class);
        assets = mock(IKbAssetService.class);
        archiver = mock(KbFrozenProductArchiver.class);
        var service = mock(KbRenderServiceImpl.class);
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var properties = new KbRenderProperties();
        properties.setWorkDirectory(root.toString());
        // An unavailable script proves that recovery reuses sealed bytes instead of rendering
        // again.
        properties.setScript(root.resolve("not-a-renderer.mjs").toString());
        worker = new KbRenderWorker(mapper, service, assets, properties, archiver, transactions);
        job = new KbRenderJob();
        job.setId(88L);
        job.setActorUserId(42L);
        job.setCreateBy("fixture-agent");
        job.setAssets(List.of());
        var config = new KbRenderRequest();
        config.setOutputRoles(List.of("STUDENT"));
        config.setTestRunId("recovery-unit");
        job.setConfigSnapshot(config);
        when(mapper.job(88L)).thenReturn(job);
        when(service.hydrate(job)).thenReturn(job);
        when(mapper.lockOwnedJob(eq(88L), anyString())).thenReturn(job);
        when(mapper.jobAssets(88L)).thenAnswer(call -> new ArrayList<>(references));
        when(mapper.insertJobAsset(any()))
                .thenAnswer(
                        call -> {
                            references.add(call.getArgument(0));
                            return 1;
                        });
        when(mapper.finish(any()))
                .thenAnswer(
                        call -> {
                            terminalStates.add(((KbRenderJob) call.getArgument(0)).getStatus());
                            return 1;
                        });
        Path base = Files.createDirectories(root.resolve("job-88"));
        Path sealed = Files.createDirectories(base.resolve("sealed"));
        byte[] request = "{\"frozen\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(base.resolve("request.json"), request);
        Files.write(sealed.resolve("student.pdf"), pdf);
        Files.write(sealed.resolve("student.zip"), pack);
        Files.writeString(sealed.resolve("student.html"), "<p>Original frozen question</p>");
        var output = new JSONObject();
        output.put("outputKey", "STUDENT");
        output.put("role", "STUDENT");
        output.put("pdfPath", "student.pdf");
        output.put("packagePath", "student.zip");
        output.put("htmlPath", "student.html");
        output.put("sha256", KbLayoutProtocol.hash(pdf));
        output.put("packageSha256", KbLayoutProtocol.hash(pack));
        output.put("pageCount", 1);
        var manifest = new JSONObject();
        manifest.put("schemaVersion", "kb-render-output/v1");
        manifest.put("rendererFingerprint", "fixture-pinned-renderer");
        manifest.put("inputHash", KbLayoutProtocol.hash(request));
        manifest.put("outputs", List.of(output));
        Files.writeString(sealed.resolve("manifest.json"), manifest.toJSONString());
    }

    @AfterEach
    void shutdown() {
        worker.stop();
    }

    KbRenderJob claim(String token) {
        var claim = new KbRenderJob();
        claim.setId(88L);
        claim.setLeaseToken(token);
        return claim;
    }

    KbAsset ready(long id, byte[] bytes) {
        var asset = new KbAsset();
        asset.setAssetId(id);
        asset.setStatus("READY");
        asset.setSha256(KbLayoutProtocol.hash(bytes));
        return asset;
    }

    @Test
    void uploadFailureResumesSameBytesAndKeysWithoutDuplicatePdfOrArchive() throws Exception {
        var storedPdf = ready(101L, pdf);
        var storedPack = ready(102L, pack);
        when(assets.uploadBytes(
                        any(),
                        eq("render-88-STUDENT.pdf"),
                        anyString(),
                        anyString(),
                        anyString(),
                        eq("render:88:STUDENT:pdf")))
                .thenReturn(storedPdf);
        when(assets.uploadBytes(
                        any(),
                        eq("render-88-STUDENT.zip"),
                        anyString(),
                        anyString(),
                        anyString(),
                        eq("render:88:STUDENT:package")))
                .thenThrow(new ServiceException("temporary OSS upload failure"))
                .thenReturn(storedPack);
        when(assets.requireReady(101L)).thenReturn(storedPdf);
        var first = claim("first-owner");
        ReflectionTestUtils.invokeMethod(worker, "execute", first);
        assertEquals(List.of("FAILED"), terminalStates);
        assertEquals("STORAGE_OR_ARCHIVE_FAILED", first.getErrorCode());
        assertEquals(1, references.size());
        verifyNoInteractions(archiver);

        ReflectionTestUtils.invokeMethod(worker, "execute", claim("recovery-owner"));
        assertEquals(List.of("FAILED", "SUCCEEDED"), terminalStates);
        assertEquals(2, references.size());
        verify(assets, times(1))
                .uploadBytes(
                        any(),
                        eq("render-88-STUDENT.pdf"),
                        anyString(),
                        anyString(),
                        anyString(),
                        eq("render:88:STUDENT:pdf"));
        verify(assets, times(2))
                .uploadBytes(
                        any(),
                        eq("render-88-STUDENT.zip"),
                        anyString(),
                        anyString(),
                        anyString(),
                        eq("render:88:STUDENT:package"));
        verify(archiver, times(1)).archive(any(), eq("STUDENT"), eq(101L));
        assertArrayEquals(pdf, Files.readAllBytes(root.resolve("job-88/sealed/student.pdf")));
        assertArrayEquals(pack, Files.readAllBytes(root.resolve("job-88/sealed/student.zip")));
    }

    @Test
    void gracefulShutdownLeavesLeaseRecoverableInsteadOfMarkingDocumentFailed() {
        when(assets.uploadBytes(
                        any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(
                        call -> {
                            worker.stop();
                            throw new ServiceException("shutdown interruption");
                        });
        ReflectionTestUtils.invokeMethod(worker, "execute", claim("stopping-owner"));
        verify(mapper, never()).finish(any());
        verify(mapper, never()).insertJobAsset(any());
        verifyNoInteractions(archiver);
    }

    @Test
    void stolenLeaseCannotAttachUploadedAssetOrArchiveProduct() {
        when(assets.uploadBytes(
                        any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(ready(101L, pdf));
        when(mapper.lockOwnedJob(88L, "stale-owner")).thenReturn(null);
        // The SQL token CAS refuses the stale owner's terminal status as well.
        doReturn(0).when(mapper).finish(any());
        ReflectionTestUtils.invokeMethod(worker, "execute", claim("stale-owner"));
        verify(mapper, never()).insertJobAsset(any());
        verifyNoInteractions(archiver);
        assertTrue(references.isEmpty());
    }
}
