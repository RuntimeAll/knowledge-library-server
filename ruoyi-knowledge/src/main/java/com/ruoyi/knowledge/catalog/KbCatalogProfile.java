package com.ruoyi.knowledge.catalog;

import java.time.LocalDateTime;

public class KbCatalogProfile extends CatalogFields {
    private String description;
    private String subject;
    private String resourceType;
    private String originKind;
    private String authorName;
    private String publisherName;
    private String catalogStatus;
    private String audienceRole;
    private String provenanceStatus;
    private Integer publishedYear;
    private Integer preparedYear;
    private LocalDateTime acquiredAt;
    private Boolean legacy;

    public String getDescription() {
        return description;
    }

    public void setDescription(String value) {
        description = value;
        supplied("description");
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String value) {
        subject = value;
        supplied("subject");
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String value) {
        resourceType = value;
        supplied("resourceType");
    }

    public String getOriginKind() {
        return originKind;
    }

    public void setOriginKind(String value) {
        originKind = value;
        supplied("originKind");
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String value) {
        authorName = value;
        supplied("authorName");
    }

    public String getPublisherName() {
        return publisherName;
    }

    public void setPublisherName(String value) {
        publisherName = value;
        supplied("publisherName");
    }

    public String getCatalogStatus() {
        return catalogStatus;
    }

    public void setCatalogStatus(String value) {
        catalogStatus = value;
        supplied("catalogStatus");
    }

    public String getAudienceRole() {
        return audienceRole;
    }

    public void setAudienceRole(String value) {
        audienceRole = value;
        supplied("audienceRole");
    }

    public String getProvenanceStatus() {
        return provenanceStatus;
    }

    public void setProvenanceStatus(String value) {
        provenanceStatus = value;
        supplied("provenanceStatus");
    }

    public Integer getPublishedYear() {
        return publishedYear;
    }

    public void setPublishedYear(Integer value) {
        publishedYear = value;
        supplied("publishedYear");
    }

    public Integer getPreparedYear() {
        return preparedYear;
    }

    public void setPreparedYear(Integer value) {
        preparedYear = value;
        supplied("preparedYear");
    }

    public LocalDateTime getAcquiredAt() {
        return acquiredAt;
    }

    public void setAcquiredAt(LocalDateTime value) {
        acquiredAt = value;
        supplied("acquiredAt");
    }

    public Boolean getLegacy() {
        return legacy;
    }

    public void setLegacy(Boolean value) {
        legacy = value;
        supplied("legacy");
    }

    public void apply(KbCatalogProfile patch) {
        if (patch.has("description")) setDescription(patch.getDescription());
        if (patch.has("subject")) setSubject(patch.getSubject());
        if (patch.has("resourceType")) setResourceType(patch.getResourceType());
        if (patch.has("originKind")) setOriginKind(patch.getOriginKind());
        if (patch.has("authorName")) setAuthorName(patch.getAuthorName());
        if (patch.has("publisherName")) setPublisherName(patch.getPublisherName());
        if (patch.has("catalogStatus")) setCatalogStatus(patch.getCatalogStatus());
        if (patch.has("audienceRole")) setAudienceRole(patch.getAudienceRole());
        if (patch.has("provenanceStatus")) setProvenanceStatus(patch.getProvenanceStatus());
        if (patch.has("publishedYear")) setPublishedYear(patch.getPublishedYear());
        if (patch.has("preparedYear")) setPreparedYear(patch.getPreparedYear());
        if (patch.has("acquiredAt")) setAcquiredAt(patch.getAcquiredAt());
    }
}
