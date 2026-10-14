package com.ruoyi.knowledge.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** OSS credentials are supplied only by external configuration. */
@Component
@ConfigurationProperties(prefix = "knowledge.storage")
public class KbStorageProperties {
    private boolean enabled;
    private String endpoint;
    private String region = "cn-hangzhou";
    private String bucket;
    private String prefix = "knowledge-library";
    private String accessKeyId;
    private String accessKeySecret;
    private boolean testMode;
    private long maxFileSize = 104857600;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }
    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
    public String getAccessKeySecret() { return accessKeySecret; }
    public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }
    public boolean isTestMode() { return testMode; }
    public void setTestMode(boolean testMode) { this.testMode = testMode; }
    public long getMaxFileSize() { return maxFileSize; }
    public void setMaxFileSize(long maxFileSize) { this.maxFileSize = maxFileSize; }
}
