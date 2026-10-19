package com.ruoyi.knowledge.layout;

import com.alibaba.fastjson2.*;
import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.domain.*;

import java.util.*;

public class KbRenderSection {
    private String key;
    private String title;
    private String dayKey;
    private List<KbRenderQuestionRef> questionRefs;

    public String getKey() {
        return key;
    }

    public void setKey(String value) {
        key = value;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String value) {
        title = value;
    }

    public String getDayKey() {
        return dayKey;
    }

    public void setDayKey(String value) {
        dayKey = value;
    }

    public List<KbRenderQuestionRef> getQuestionRefs() {
        return questionRefs;
    }

    public void setQuestionRefs(List<KbRenderQuestionRef> value) {
        questionRefs = value;
    }

    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new com.ruoyi.common.exception.ServiceException("未定义模板或生成字段：" + name, 400);
    }
}
