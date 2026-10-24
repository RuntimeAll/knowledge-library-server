package com.ruoyi.knowledge.catalog;


public class KbRegionLink extends CatalogFields {
    private String role;
    private String regionCode;
    private String name;
    private String fullName;

    public String getRole() {
        return role;
    }

    public void setRole(String value) {
        role = value;
        supplied("role");
    }

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String value) {
        regionCode = value;
        supplied("regionCode");
    }

    public String getName() {
        return name;
    }

    public void setName(String value) {
        name = value;
        supplied("name");
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String value) {
        fullName = value;
        supplied("fullName");
    }
}
