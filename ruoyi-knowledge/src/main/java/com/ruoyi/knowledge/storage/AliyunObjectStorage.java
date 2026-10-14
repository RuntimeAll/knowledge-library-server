package com.ruoyi.knowledge.storage;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Date;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.model.DeleteVersionRequest;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.ruoyi.common.exception.ServiceException;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class AliyunObjectStorage implements ObjectStorage {
    private final KbStorageProperties properties;
    private volatile OSS client;

    public AliyunObjectStorage(KbStorageProperties properties) { this.properties = properties; }

    private OSS client() {
        if (!properties.isEnabled() || blank(properties.getEndpoint()) || blank(properties.getBucket())
                || blank(properties.getAccessKeyId()) || blank(properties.getAccessKeySecret())) {
            throw new ServiceException("知识库OSS尚未配置，请联系管理员");
        }
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
                    configuration.setConnectionTimeout(10000);
                    configuration.setSocketTimeout(60000);
                    // Retries are coordinated with the persisted asset receipt, not blind PUT retries.
                    configuration.setMaxErrorRetry(0);
                    client = new OSSClientBuilder().build(properties.getEndpoint(),
                            properties.getAccessKeyId(), properties.getAccessKeySecret(), configuration);
                }
            }
        }
        return client;
    }

    @Override
    public PutResult put(String bucket, String objectKey, byte[] bytes, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        metadata.setContentType(contentType);
        metadata.addUserMetadata("kb-sha256", KbAssetServiceImpl.sha256(bytes));
        // Private per object even when the reused bucket defaults to public read.
        metadata.setHeader("x-oss-object-acl", "private");
        PutObjectResult result = client().putObject(new PutObjectRequest(bucket, objectKey,
                new ByteArrayInputStream(bytes), metadata));
        return new PutResult(result.getVersionId());
    }

    @Override
    public StoredObject stat(String bucket, String objectKey) {
        try {
            ObjectMetadata metadata = client().getObjectMetadata(bucket, objectKey);
            return new StoredObject(metadata.getVersionId(), metadata.getContentLength(), metadata.getUserMetadata().get("kb-sha256"));
        } catch (OSSException e) {
            if ("NoSuchKey".equals(e.getErrorCode())) { return null; }
            throw e;
        }
    }

    @Override
    public InputStream open(String bucket, String objectKey, String versionId) {
        GetObjectRequest request = new GetObjectRequest(bucket, objectKey);
        if (!blank(versionId)) { request.setVersionId(versionId); }
        return client().getObject(request).getObjectContent();
    }

    @Override
    public String previewUrl(String bucket, String objectKey, String versionId, int validSeconds) {
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, objectKey, HttpMethod.GET);
        request.setExpiration(new Date(System.currentTimeMillis() + validSeconds * 1000L));
        if (!blank(versionId)) { request.addQueryParameter("versionId", versionId); }
        return client().generatePresignedUrl(request).toExternalForm();
    }

    @Override
    public void delete(String bucket, String objectKey, String versionId) {
        if (!blank(versionId)) {
            client().deleteVersion(new DeleteVersionRequest(bucket, objectKey, versionId));
        } else {
            client().deleteObject(bucket, objectKey);
        }
    }

    @PreDestroy
    public void close() {
        if (client != null) { client.shutdown(); }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
