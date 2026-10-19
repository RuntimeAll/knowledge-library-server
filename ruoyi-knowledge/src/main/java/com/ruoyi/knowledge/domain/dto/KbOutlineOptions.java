package com.ruoyi.knowledge.domain.dto;

import java.util.List;

/** 教材字段选项；不属于系统字典。 */
public record KbOutlineOptions(List<Option> grades, List<Option> terms, List<Option> textbookVersions) {
    public record Option(String value, String label) {}
}
