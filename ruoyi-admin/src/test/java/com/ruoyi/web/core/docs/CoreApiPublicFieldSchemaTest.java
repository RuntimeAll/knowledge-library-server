package com.ruoyi.web.core.docs;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Public-field DTOs must describe the same precision and nulls that Jackson actually emits. */
class CoreApiPublicFieldSchemaTest {
    @Test void inheritedPublicFieldsAndNestedTypesAreDescribedAsSerialized() throws Exception {
        CoreApiSchemas schemas = new CoreApiSchemas();
        String reference = schemas.resolve(PublicEnvelope.class).get$ref();
        schemas.describe(PublicEnvelope.class, Map.of("data.id", "稳定字符串ID",
                "data.children[].assetId", "嵌套资产ID"), "data");
        var all = schemas.all();
        var root = all.get(reference.substring("#/components/schemas/".length()));
        var id = (io.swagger.v3.oas.models.media.Schema<?>) root.getProperties().get("id");
        assertEquals("string", id.getType());
        assertEquals("稳定字符串ID", id.getDescription());
        assertFalse(root.getProperties().containsKey("ignored"));
        assertFalse(root.getProperties().containsKey("CONSTANT"));
        var children = (io.swagger.v3.oas.models.media.Schema<?>) root.getProperties().get("children");
        var child = all.get(children.getItems().get$ref().substring("#/components/schemas/".length()));
        var asset = (io.swagger.v3.oas.models.media.Schema<?>) child.getProperties().get("assetId");
        assertEquals("string", asset.getType()); assertEquals("嵌套资产ID", asset.getDescription());
        PublicEnvelope value = new PublicEnvelope(); value.id = Long.MAX_VALUE;
        assertEquals(Long.toString(Long.MAX_VALUE), new ObjectMapper().valueToTree(value).path("id").asText());
    }

    @Test void responsePublicReferencesAndStringsAllowActualNullValues() {
        CoreApiSchemas schemas = new CoreApiSchemas(true);
        String reference = schemas.resolve(PublicEnvelope.class).get$ref();
        var root = schemas.all().get(reference.substring("#/components/schemas/".length()) + ".Response");
        var id = (io.swagger.v3.oas.models.media.Schema<?>) root.getProperties().get("id");
        assertTrue(id.getTypes().containsAll(List.of("string", "null")));
        var count = (io.swagger.v3.oas.models.media.Schema<?>) root.getProperties().get("count");
        assertTrue(count.getTypes() == null || !count.getTypes().contains("null"));
        var nested = (io.swagger.v3.oas.models.media.Schema<?>) root.getProperties().get("detail");
        assertTrue(nested.getAnyOf().stream().anyMatch(s -> s.get$ref() != null && s.get$ref().endsWith(".Response")));
    }

    public static class PublicBase {
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long id;
    }
    public static class PublicEnvelope extends PublicBase {
        public static final String CONSTANT = "ignore";
        @JsonIgnore public String ignored;
        public List<PublicChild> children;
        public PublicChild detail;
        public int count;
    }
    public static class PublicChild {
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long assetId;
    }
}
