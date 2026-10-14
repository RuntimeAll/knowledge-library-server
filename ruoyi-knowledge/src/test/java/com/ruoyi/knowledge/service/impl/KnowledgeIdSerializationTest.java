package com.ruoyi.knowledge.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ruoyi.knowledge.domain.KbQuestion;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import java.util.List;

class KnowledgeIdSerializationTest {
    @Test
    void idsAboveJavaScriptIntegerPrecisionSerializeAsDecimalStrings() {
        KbQuestion question = new KbQuestion();
        question.setId(9007199254740993L);
        question.setRowVersion(9007199254740994L);
        question.setKpIds(List.of(9007199254740995L));
        JsonMapper mapper = JsonMapper.builder().build();
        var json = mapper.readTree(mapper.writeValueAsString(question));
        assertTrue(json.get("id").isString());
        assertEquals("9007199254740993", json.get("id").asString());
        assertTrue(json.get("rowVersion").isString());
        assertEquals("9007199254740995", json.get("kpIds").get(0).asString());
    }
}
