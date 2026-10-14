package com.ruoyi.knowledge.storage;

import java.io.InputStream;
import com.ruoyi.knowledge.storage.domain.KbAsset;
import org.springframework.web.multipart.MultipartFile;

public interface IKbAssetService {
    record Preview(String url, int expiresInSeconds, String contentType) { }
    KbAsset getAsset(Long assetId);
    KbAsset requireReady(Long assetId);
    KbAsset upload(MultipartFile file, String purpose, String testRunId);
    KbAsset upload(MultipartFile file, String purpose, String testRunId, String idempotencyKey);
    KbAsset uploadBytes(byte[] bytes, String originalName, String contentType, String purpose, String testRunId);
    KbAsset uploadBytes(byte[] bytes, String originalName, String contentType, String purpose, String testRunId, String idempotencyKey);
    InputStream openContent(Long assetId);
    Preview preview(Long assetId);
    void delete(Long assetId);
    String validateTestRunId(String testRunId);
}
