package com.ruoyi.knowledge.storage;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import com.ruoyi.knowledge.storage.domain.KbAsset;
import org.springframework.web.multipart.MultipartFile;

public interface IKbAssetService {
    record Preview(String url, int expiresInSeconds, String contentType) { }
    KbAsset getAsset(Long assetId);
    KbAsset findOwnUpload(String externalKey);
    KbAsset requireReady(Long assetId);
    KbAsset requireOriginal(Long assetId);
    KbAsset upload(MultipartFile file, String purpose, String testRunId);
    KbAsset upload(MultipartFile file, String purpose, String testRunId, String idempotencyKey);
    KbAsset uploadBytes(byte[] bytes, String originalName, String contentType, String purpose, String testRunId);
    KbAsset uploadBytes(byte[] bytes, String originalName, String contentType, String purpose, String testRunId, String idempotencyKey);
    InputStream openContent(Long assetId);
    Preview preview(Long assetId);
    /** Internal publication download signing; never exposed as an arbitrary asset signing endpoint. */
    Preview publicationDownload(Long assetId);
    /** Internal fixed-policy OSS image signing, after the publication scope gate. */
    Preview publicationImagePreview(Long assetId);
    /** Batch signing for a bounded publication's images; does not issue one SQL query per image. */
    Map<Long, Preview> publicationImagePreviews(List<Long> assetIds);
    void delete(Long assetId);
    String validateTestRunId(String testRunId);
}
