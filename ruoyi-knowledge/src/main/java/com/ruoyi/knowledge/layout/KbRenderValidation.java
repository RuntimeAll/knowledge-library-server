package com.ruoyi.knowledge.layout;

import com.alibaba.fastjson2.*;
import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.domain.*;

import java.util.*;

public class KbRenderValidation {
    private Boolean valid;
    private Integer positionCount;
    private Integer uniqueQuestionCount;
    private Integer assetCount;
    private List<String> warnings;
    private KbRenderRequest normalizedRequest;

    public Boolean getValid() {
        return valid;
    }

    public void setValid(Boolean value) {
        valid = value;
    }

    public Integer getPositionCount() {
        return positionCount;
    }

    public void setPositionCount(Integer value) {
        positionCount = value;
    }

    public Integer getUniqueQuestionCount() {
        return uniqueQuestionCount;
    }

    public void setUniqueQuestionCount(Integer value) {
        uniqueQuestionCount = value;
    }

    public Integer getAssetCount() {
        return assetCount;
    }

    public void setAssetCount(Integer value) {
        assetCount = value;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> value) {
        warnings = value;
    }

    public KbRenderRequest getNormalizedRequest() {
        return normalizedRequest;
    }

    public void setNormalizedRequest(KbRenderRequest value) {
        normalizedRequest = value;
    }
}
