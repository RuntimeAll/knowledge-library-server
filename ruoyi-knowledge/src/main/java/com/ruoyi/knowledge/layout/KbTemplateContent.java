package com.ruoyi.knowledge.layout;

import com.alibaba.fastjson2.*;
import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.domain.*;

import java.util.*;

public class KbTemplateContent {
    private Integer optionColumns;
    private String optionLayout;
    private String subquestionLayout;
    private Boolean showAnalysis;
    private Double imageMaxWidthPercent;

    public Integer getOptionColumns() {
        return optionColumns;
    }

    public void setOptionColumns(Integer value) {
        optionColumns = value;
    }

    public String getOptionLayout() {
        return optionLayout;
    }

    public void setOptionLayout(String value) {
        optionLayout = value;
    }

    public String getSubquestionLayout() {
        return subquestionLayout;
    }

    public void setSubquestionLayout(String value) {
        subquestionLayout = value;
    }

    public Boolean getShowAnalysis() {
        return showAnalysis;
    }

    public void setShowAnalysis(Boolean value) {
        showAnalysis = value;
    }

    public Double getImageMaxWidthPercent() {
        return imageMaxWidthPercent;
    }

    public void setImageMaxWidthPercent(Double value) {
        imageMaxWidthPercent = value;
    }

    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new com.ruoyi.common.exception.ServiceException("未定义模板或生成字段：" + name, 400);
    }
}
