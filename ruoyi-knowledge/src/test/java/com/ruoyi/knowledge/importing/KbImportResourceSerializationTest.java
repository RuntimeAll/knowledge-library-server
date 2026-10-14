package com.ruoyi.knowledge.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ruoyi.knowledge.importing.domain.KbImportResource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class KbImportResourceSerializationTest {
    @Test
    void resourceIdentifiersStayExactBeyondJavascriptIntegerPrecision() {
        KbImportResource resource = new KbImportResource();
        resource.setBatchId(9007199254740993L);
        resource.setAssetId(9007199254740995L);
        JsonMapper mapper = JsonMapper.builder().build();
        var json = mapper.readTree(mapper.writeValueAsString(resource));
        assertTrue(json.get("batchId").isString());
        assertTrue(json.get("assetId").isString());
        assertEquals("9007199254740993", json.get("batchId").asString());
        assertEquals("9007199254740995", json.get("assetId").asString());
    }
}
