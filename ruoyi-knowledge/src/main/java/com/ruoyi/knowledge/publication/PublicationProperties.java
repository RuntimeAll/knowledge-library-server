package com.ruoyi.knowledge.publication;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "knowledge.publication")
public class PublicationProperties {
    public enum DeliveryMode { PUBLIC_PAGE, OSS_DOWNLOAD }
    private DeliveryMode deliveryMode = DeliveryMode.PUBLIC_PAGE;
    public DeliveryMode getDeliveryMode() { return deliveryMode; }
    public void setDeliveryMode(DeliveryMode value) { deliveryMode = java.util.Objects.requireNonNull(value); }
    private boolean enabled;
    private boolean productDownloadsEnabled = true;
    public boolean isProductDownloadsEnabled() { return productDownloadsEnabled; }
    public void setProductDownloadsEnabled(boolean value) { productDownloadsEnabled = value; }
    private String publicBaseUrl;
    private boolean allowHttp;
    public boolean isAllowHttp() { return allowHttp; }
    public void setAllowHttp(boolean value) { allowHttp = value; }
    private Long pageTemplateAssetId;
    private String staticDirectory;
    public String getStaticDirectory() { return staticDirectory; }
    public void setStaticDirectory(String value) { staticDirectory = value; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String value) { publicBaseUrl = value; }
    public Long getPageTemplateAssetId() { return pageTemplateAssetId; }
    public void setPageTemplateAssetId(Long value) { pageTemplateAssetId = value; }
}
