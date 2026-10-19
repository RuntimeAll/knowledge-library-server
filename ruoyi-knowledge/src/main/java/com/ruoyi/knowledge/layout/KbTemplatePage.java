package com.ruoyi.knowledge.layout;

import com.alibaba.fastjson2.*;
import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.domain.*;

import java.util.*;

public class KbTemplatePage {
    private String size;
    private String orientation;
    private Double marginTopMm;
    private Double marginRightMm;
    private Double marginBottomMm;
    private Double marginLeftMm;
    private String fontFamily;
    private Double fontSizePt;
    private Double lineHeight;
    private Integer columns;
    private Double questionGapMm;
    private Double answerSpaceMm;

    public String getSize() {
        return size;
    }

    public void setSize(String value) {
        size = value;
    }

    public String getOrientation() {
        return orientation;
    }

    public void setOrientation(String value) {
        orientation = value;
    }

    public Double getMarginTopMm() {
        return marginTopMm;
    }

    public void setMarginTopMm(Double value) {
        marginTopMm = value;
    }

    public Double getMarginRightMm() {
        return marginRightMm;
    }

    public void setMarginRightMm(Double value) {
        marginRightMm = value;
    }

    public Double getMarginBottomMm() {
        return marginBottomMm;
    }

    public void setMarginBottomMm(Double value) {
        marginBottomMm = value;
    }

    public Double getMarginLeftMm() {
        return marginLeftMm;
    }

    public void setMarginLeftMm(Double value) {
        marginLeftMm = value;
    }

    public String getFontFamily() {
        return fontFamily;
    }

    public void setFontFamily(String value) {
        fontFamily = value;
    }

    public Double getFontSizePt() {
        return fontSizePt;
    }

    public void setFontSizePt(Double value) {
        fontSizePt = value;
    }

    public Double getLineHeight() {
        return lineHeight;
    }

    public void setLineHeight(Double value) {
        lineHeight = value;
    }

    public Integer getColumns() {
        return columns;
    }

    public void setColumns(Integer value) {
        columns = value;
    }

    public Double getQuestionGapMm() {
        return questionGapMm;
    }

    public void setQuestionGapMm(Double value) {
        questionGapMm = value;
    }

    public Double getAnswerSpaceMm() {
        return answerSpaceMm;
    }

    public void setAnswerSpaceMm(Double value) {
        answerSpaceMm = value;
    }

    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new com.ruoyi.common.exception.ServiceException("未定义模板或生成字段：" + name, 400);
    }
}
