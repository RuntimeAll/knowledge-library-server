package com.ruoyi.knowledge.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.mapper.KbDictionaryMapper;

import org.junit.jupiter.api.Test;

class KnowledgeDictionaryTest {
    @Test
    void unknownClassificationMayStayEmpty() {
        KbDictionaryMapper mapper = mock(KbDictionaryMapper.class);
        KnowledgeDictionary dictionary = new KnowledgeDictionary(mapper);
        dictionary.validate("kb_subject", null, "学科");
        dictionary.validate("kb_subject", "", "学科");
        verifyNoInteractions(mapper);
    }

    @Test
    void onlyEnabledValuesCanBeSaved() {
        KbDictionaryMapper mapper = mock(KbDictionaryMapper.class);
        when(mapper.containsEnabled("kb_subject", "MATH")).thenReturn(true);
        KnowledgeDictionary dictionary = new KnowledgeDictionary(mapper);
        dictionary.validate("kb_subject", "MATH", "学科");
        assertThrows(
                ServiceException.class, () -> dictionary.validate("kb_subject", "invalid", "学科"));
    }
}
