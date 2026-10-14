package com.ruoyi.knowledge.storage;

import java.io.InputStream;

/** Infrastructure boundary; business services never depend on an OSS SDK. */
public interface ObjectStorage {
    record PutResult(String versionId) { }
    record StoredObject(String versionId, long contentLength, String sha256) { }
    StoredObject stat(String bucket, String objectKey);
    PutResult put(String bucket, String objectKey, byte[] bytes, String contentType);
    InputStream open(String bucket, String objectKey, String versionId);
    String previewUrl(String bucket, String objectKey, String versionId, int validSeconds);
    void delete(String bucket, String objectKey, String versionId);
}
