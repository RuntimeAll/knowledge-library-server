package com.ruoyi.knowledge.layout;

import com.alibaba.fastjson2.*;
import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.domain.*;

import java.util.*;

public class KbRendererOutput {
    private String outputKey;
    private String role;
    private String pdfPath;
    private String htmlPath;
    private String packagePath;
    private String sha256;
    private String packageSha256;
    private Integer pageCount;

    public String getOutputKey() {
        return outputKey;
    }

    public void setOutputKey(String value) {
        outputKey = value;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String value) {
        role = value;
    }

    public String getPdfPath() {
        return pdfPath;
    }

    public void setPdfPath(String value) {
        pdfPath = value;
    }

    public String getHtmlPath() {
        return htmlPath;
    }

    public void setHtmlPath(String value) {
        htmlPath = value;
    }

    public String getPackagePath() {
        return packagePath;
    }

    public void setPackagePath(String value) {
        packagePath = value;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String value) {
        sha256 = value;
    }

    public String getPackageSha256() {
        return packageSha256;
    }

    public void setPackageSha256(String value) {
        packageSha256 = value;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public void setPageCount(Integer value) {
        pageCount = value;
    }
}
