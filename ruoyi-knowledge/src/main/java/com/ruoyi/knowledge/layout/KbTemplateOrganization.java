package com.ruoyi.knowledge.layout;

import com.alibaba.fastjson2.*;
import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.domain.*;

import java.util.*;

public class KbTemplateOrganization {
    private Boolean sectionPageBreak;
    private Boolean dayPageBreak;
    private String numbering;

    public Boolean getSectionPageBreak() {
        return sectionPageBreak;
    }

    public void setSectionPageBreak(Boolean value) {
        sectionPageBreak = value;
    }

    public Boolean getDayPageBreak() {
        return dayPageBreak;
    }

    public void setDayPageBreak(Boolean value) {
        dayPageBreak = value;
    }

    public String getNumbering() {
        return numbering;
    }

    public void setNumbering(String value) {
        numbering = value;
    }

    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new com.ruoyi.common.exception.ServiceException("未定义模板或生成字段：" + name, 400);
    }
}
