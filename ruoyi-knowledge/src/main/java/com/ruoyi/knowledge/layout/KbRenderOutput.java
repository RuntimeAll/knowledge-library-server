package com.ruoyi.knowledge.layout;

import com.alibaba.fastjson2.*;
import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.domain.*;

import java.util.*;

public class KbRenderOutput {
    private Long productId;
    private Long assetId;
    private Long packageAssetId;
    private String outputKey;
    private String sha256;
    private String packageSha256;
    private Integer pageCount;

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long value) {
        productId = value;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long value) {
        assetId = value;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getPackageAssetId() {
        return packageAssetId;
    }

    public void setPackageAssetId(Long value) {
        packageAssetId = value;
    }

    public String getOutputKey() {
        return outputKey;
    }

    public void setOutputKey(String value) {
        outputKey = value;
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
