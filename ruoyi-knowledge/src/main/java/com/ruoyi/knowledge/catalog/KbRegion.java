package com.ruoyi.knowledge.catalog;


public class KbRegion extends CatalogFields {
    private String regionCode;
    private String parentCode;
    private String name;
    private String fullName;
    private Integer level;
    private String status;
    private String standardCode;
    private String sourceUrl;
    private Boolean hasChildren;

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String value) {
        regionCode = value;
        supplied("regionCode");
    }

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String value) {
        parentCode = value;
        supplied("parentCode");
    }

    public String getName() {
        return name;
    }

    public void setName(String value) {
        name = value;
        supplied("name");
    }

    public Integer getLevel() {
        return level;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String value) {
        fullName = value;
        supplied("fullName");
    }

    public void setLevel(Integer value) {
        level = value;
        supplied("level");
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String value) {
        status = value;
        supplied("status");
    }

    public String getStandardCode() {
        return standardCode;
    }

    public void setStandardCode(String value) {
        standardCode = value;
        supplied("standardCode");
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String value) {
        sourceUrl = value;
        supplied("sourceUrl");
    }

    public Boolean getHasChildren() {
        return hasChildren;
    }

    public void setHasChildren(Boolean value) {
        hasChildren = value;
        supplied("hasChildren");
    }
}
