package com.ruoyi.knowledge.layout;

import com.alibaba.fastjson2.*;
import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.domain.*;

import java.util.*;

public class KbTemplateDefinition {
    private String html;
    private String css;
    private KbTemplatePage page;
    private KbTemplateOrganization organization;
    private KbTemplateContent content;

    public String getHtml() {
        return html;
    }

    public void setHtml(String value) {
        html = value;
    }

    public String getCss() {
        return css;
    }

    public void setCss(String value) {
        css = value;
    }

    public KbTemplatePage getPage() {
        return page;
    }

    public void setPage(KbTemplatePage value) {
        page = value;
    }

    public KbTemplateOrganization getOrganization() {
        return organization;
    }

    public void setOrganization(KbTemplateOrganization value) {
        organization = value;
    }

    public KbTemplateContent getContent() {
        return content;
    }

    public void setContent(KbTemplateContent value) {
        content = value;
    }

    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new com.ruoyi.common.exception.ServiceException("未定义模板或生成字段：" + name, 400);
    }
}
