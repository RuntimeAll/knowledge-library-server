package com.ruoyi.knowledge.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.mapper.KbDictionaryMapper;

import org.springframework.stereotype.Component;

/** 元数据分类使用若依有效字典；留空表示尚未分类。 */
@Component
public class KnowledgeDictionary {
    private final KbDictionaryMapper mapper;

    public KnowledgeDictionary(KbDictionaryMapper mapper) {
        this.mapper = mapper;
    }

    public void validate(String type, String value, String label) {
        if (value != null && !value.isBlank() && !mapper.containsEnabled(type, value)) {
            throw new ServiceException(label + "不在有效字典中，请选择已启用的分类", 400);
        }
    }
}
