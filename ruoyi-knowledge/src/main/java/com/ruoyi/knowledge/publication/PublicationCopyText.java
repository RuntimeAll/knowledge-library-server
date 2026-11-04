package com.ruoyi.knowledge.publication;

import java.util.List;
import java.util.stream.Collectors;

/** Shared plain-text presentation; original publication inputs and evidence remain unchanged. */
public final class PublicationCopyText {
    private static final String LEGACY_TOPICS = "#七年级科学 #科学课件 #课堂互动 #反应时间 #制动距离 #初中科学 #教学资源 #玉米训练营";
    private PublicationCopyText() { }
    public static String body(String value) {
        if (value == null) { return null; }
        String trimmed = value.stripTrailing();
        if (trimmed.equals(LEGACY_TOPICS)) { return ""; }
        return trimmed.endsWith("\n" + LEGACY_TOPICS)
                ? trimmed.substring(0, trimmed.length() - LEGACY_TOPICS.length()).stripTrailing() : value;
    }
    public static String baidu(String url, String code, List<String> names) {
        String name = names.stream().filter(java.util.Objects::nonNull).filter(n -> !n.isBlank())
                .collect(Collectors.joining("、"));
        return (name.isEmpty() ? "" : "通过百度网盘分享的文件：" + name + "\n")
                + "链接：" + url + (code == null || code.isBlank() ? "" : "\n提取码：" + code);
    }
}
