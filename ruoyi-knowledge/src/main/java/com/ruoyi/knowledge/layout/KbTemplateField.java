package com.ruoyi.knowledge.layout;

import com.alibaba.fastjson2.*;
import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.domain.*;

import java.util.*;

public class KbTemplateField {
    private String key;
    private String type;
    private String label;
    private Boolean required;
    private Object defaultValue;
    private Double min;
    private Double max;
    private List<String> options;

    public String getKey() {
        return key;
    }

    public void setKey(String value) {
        key = value;
    }

    public String getType() {
        return type;
    }

    public void setType(String value) {
        type = value;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String value) {
        label = value;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean value) {
        required = value;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object value) {
        defaultValue = value;
    }

    public Double getMin() {
        return min;
    }

    public void setMin(Double value) {
        min = value;
    }

    public Double getMax() {
        return max;
    }

    public void setMax(Double value) {
        max = value;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> value) {
        options = value;
    }

    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new com.ruoyi.common.exception.ServiceException("未定义模板或生成字段：" + name, 400);
    }
}
