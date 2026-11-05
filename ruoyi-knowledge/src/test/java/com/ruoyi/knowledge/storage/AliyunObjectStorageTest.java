package com.ruoyi.knowledge.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** Uses local SDK signing only; no OSS request or private configuration is needed. */
class AliyunObjectStorageTest {
    @Test void processingAndVersionAreIncludedBeforeSigningAndOriginalSigningRemainsUnprocessed() throws Exception {
        KbStorageProperties properties = properties();
        OSS sdk = new OSSClientBuilder().build(properties.getEndpoint(), "fixture-access-id", "fixture-secret");
        OSS client = mock(OSS.class);
        when(client.generatePresignedUrl(any(GeneratePresignedUrlRequest.class)))
                .thenAnswer(invocation -> sdk.generatePresignedUrl(invocation.getArgument(0)));
        AliyunObjectStorage storage = new AliyunObjectStorage(properties);
        ReflectionTestUtils.setField(storage, "client", client);
        try {
            long before = System.currentTimeMillis();
            String processed = storage.imagePreviewUrl("fixture-bucket", "knowledge-library/image.png", "version-7", 600);
            ArgumentCaptor<GeneratePresignedUrlRequest> capture = ArgumentCaptor.forClass(GeneratePresignedUrlRequest.class);
            verify(client).generatePresignedUrl(capture.capture());
            GeneratePresignedUrlRequest request = capture.getValue();
            assertEquals(HttpMethod.GET, request.getMethod());
            assertEquals("image/resize,m_lfit,w_720,h_1280,limit_1/quality,q_75/format,webp", request.getProcess());
            assertEquals("version-7", request.getQueryParameter().get("versionId"));
            assertTrue(request.getExpiration().getTime() >= before + 600000L);
            assertTrue(request.getExpiration().getTime() <= System.currentTimeMillis() + 600000L);
            Map<String, String> query = query(processed);
            assertEquals(request.getProcess(), query.get("x-oss-process"));
            assertEquals("version-7", query.get("versionId"));

            // Same expiration/key/version: only the transform differs, so it must alter the signature.
            GeneratePresignedUrlRequest original = new GeneratePresignedUrlRequest(request.getBucketName(), request.getKey(), HttpMethod.GET);
            original.setExpiration(request.getExpiration()); original.addQueryParameter("versionId", "version-7");
            String unprocessed = sdk.generatePresignedUrl(original).toExternalForm();
            assertNotEquals(query.get("Signature"), query(unprocessed).get("Signature"));

            String ordinary = storage.previewUrl("fixture-bucket", "knowledge-library/image.png", "version-7", 600);
            assertNull(query(ordinary).get("x-oss-process"));
            assertEquals("version-7", query(ordinary).get("versionId"));
        } finally {
            sdk.shutdown(); storage.close();
        }
    }

    private static KbStorageProperties properties() {
        KbStorageProperties properties = new KbStorageProperties(); properties.setEnabled(true);
        properties.setBucket("fixture-bucket"); properties.setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");
        properties.setAccessKeyId("fixture-access-id"); properties.setAccessKeySecret("fixture-secret");
        return properties;
    }

    private static Map<String, String> query(String url) {
        return Arrays.stream(URI.create(url).getRawQuery().split("&")).map(value -> value.split("=", 2))
                .collect(Collectors.toMap(parts -> URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        parts -> URLDecoder.decode(parts[1], StandardCharsets.UTF_8)));
    }
}
