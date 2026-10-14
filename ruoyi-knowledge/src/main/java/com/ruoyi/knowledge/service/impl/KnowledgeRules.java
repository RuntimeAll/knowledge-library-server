package com.ruoyi.knowledge.service.impl;

import com.ruoyi.common.exception.ServiceException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/** 跨知识业务的基础输入规则；不承担持久化。 */
public final class KnowledgeRules {
    private KnowledgeRules() {}

    public static <T> T found(T value) {
        if (value == null) throw new ServiceException("记录不存在", 404);
        return value;
    }

    public static String text(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new ServiceException(label + "不能为空", 400);
        if (value.length() > max) throw new ServiceException(label + "过长", 400);
        return value.strip();
    }

    public static List<Long> ids(List<Long> values) {
        if (values == null) return List.of();
        if (values.stream().anyMatch(v -> v == null || v <= 0)
                || new HashSet<>(values).size() != values.size())
            throw new ServiceException("关联ID无效或重复", 400);
        return List.copyOf(values);
    }

    public static String choice(String value, String fallback, String... allowed) {
        String result = value == null ? fallback : value;
        if (!Arrays.asList(allowed).contains(result)) throw new ServiceException("状态或分类值无效", 400);
        return result;
    }

    public static void version(Long expected, Long actual) {
        if (expected == null || !expected.equals(actual))
            throw new ServiceException("记录已变化，请重新读取后保存", 409);
    }

    public static void changed(int count) {
        if (count != 1) throw new ServiceException("记录已变化，请重新读取后保存", 409);
    }

    public static void unreferenced(long count, String reason) {
        if (count > 0) throw new ServiceException(reason, 409);
    }
}
