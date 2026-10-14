package com.ruoyi.knowledge.mapper;

import org.apache.ibatis.annotations.Param;

/** 只读复用若依字典，避免知识模块依赖系统模块。 */
public interface KbDictionaryMapper {
    boolean containsEnabled(@Param("type") String type, @Param("value") String value);
}
