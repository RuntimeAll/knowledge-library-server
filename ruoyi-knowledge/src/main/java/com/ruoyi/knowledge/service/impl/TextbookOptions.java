package com.ruoyi.knowledge.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.dto.KbOutlineOptions;
import com.ruoyi.knowledge.domain.dto.KbOutlineOptions.Option;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.stream.IntStream;

/** 年级、学期是协议固定值，教材版本是可复用的自由文本。 */
public final class TextbookOptions {
    private TextbookOptions() {}

    public static String normalizeVersion(String value) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.equals("浙教版")) result = "ZHEJIAO";
        if (result.equals("人教版")) result = "RENJIAO";
        if (result.length() > 40) throw new ServiceException("教材版本不能超过40个字符", 400);
        return result;
    }

    public static void validate(String grade, String term) {
        if (grade != null && !grade.isBlank() && !grade.matches("G[1-9]"))
            throw new ServiceException("年级须为 G1 至 G9", 400);
        if (term != null && !term.isBlank() && !List.of("FIRST", "SECOND").contains(term))
            throw new ServiceException("学期须为 FIRST 或 SECOND", 400);
    }

    public static KbOutlineOptions options(List<String> existingVersions) {
        var versions = new LinkedHashSet<String>(List.of("ZHEJIAO", "RENJIAO"));
        if (existingVersions != null) existingVersions.stream().map(TextbookOptions::normalizeVersion)
                .filter(v -> v != null).forEach(versions::add);
        String[] names = {"一", "二", "三", "四", "五", "六", "七", "八", "九"};
        return new KbOutlineOptions(
                IntStream.range(1, 10).mapToObj(i -> new Option("G" + i, names[i - 1] + "年级")).toList(),
                List.of(new Option("FIRST", "上学期"), new Option("SECOND", "下学期")),
                versions.stream().map(v -> new Option(v, v.equals("ZHEJIAO") ? "浙教版" : v.equals("RENJIAO") ? "人教版" : v)).toList());
    }
}
