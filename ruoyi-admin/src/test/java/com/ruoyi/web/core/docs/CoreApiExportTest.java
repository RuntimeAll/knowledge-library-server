package com.ruoyi.web.core.docs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.service.IKbQuestionService;
import jakarta.servlet.Filter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(classes = IsolatedApiApplication.class, properties = {
    "spring.config.location=optional:classpath:/api-docs-test-missing.yml",
    "springdoc.enable-default-api-docs=false", "springdoc.api-docs.version=OPENAPI_3_1",
    "springdoc.use-fqn=true", "springdoc.swagger-ui.disable-swagger-default-url=true",
    "springdoc.swagger-ui.persist-authorization=false", "springdoc.swagger-ui.deep-linking=true", "springdoc.swagger-ui.query-config-enabled=false",
    "springdoc.swagger-ui.validator-url=", "springdoc.swagger-ui.try-it-out-enabled=false",
    "logging.config=classpath:logback-test.xml", "logging.level.root=WARN", "spring.main.banner-mode=off"
})
class CoreApiExportTest {
    @Autowired private WebApplicationContext context;
    @Autowired private TokenService tokens;
    @Autowired private IKbQuestionService questions;
    @Autowired private com.ruoyi.knowledge.service.IKbCatalogService regions;
    @Autowired private com.ruoyi.knowledge.service.IKbAgentIngestionService ingestion;
    @Autowired private com.ruoyi.knowledge.importing.IKbRecordImportService imports;
    @Autowired private com.ruoyi.knowledge.storage.IKbAssetService assets;
    @Autowired private com.ruoyi.knowledge.exporting.IKbExportService exports;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setup() {
        reset(tokens, questions, regions, ingestion, imports, assets, exports);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(new ForwardedHeaderFilter(),
                context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @Test
    void publishesOnlyRegisteredCoreAndExportsSameBuild() throws Exception {
        String document = mvc.perform(get("/v3/api-docs/core")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode spec = json.readTree(document);
        assertTrue(spec.path("openapi").asText().startsWith("3.1."));
        assertEquals("知识库核心接口", spec.at("/info/title").asText());
        assertEquals("../../", spec.at("/servers/0/url").asText());
        assertEquals("bearer", spec.at("/components/securitySchemes/bearerAuth/scheme").asText());
        List<Map<String, Object>> observed = observed();
        Set<String> selected = new java.util.TreeSet<>();
        observed.stream().filter(item -> Boolean.TRUE.equals(item.get("core")))
                .forEach(item -> selected.add(item.get("method") + " " + item.get("path")));
        Set<String> published = new java.util.TreeSet<>();
        Set<String> ids = new java.util.HashSet<>();
        spec.path("paths").properties().forEach(path -> path.getValue().properties().forEach(method -> {
            published.add(method.getKey().toUpperCase() + " " + path.getKey());
            assertTrue(ids.add(method.getValue().path("operationId").asText()));
            for (String key : List.of("x-kb-group", "x-kb-action", "x-kb-prd-refs", "x-kb-contract-refs",
                    "x-kb-responsibility", "x-kb-permission-rule", "x-kb-invocation-rules", "x-kb-business-errors")) {
                assertTrue(method.getValue().has(key), key);
            }
        }));
        assertEquals(expectedCorePaths(), selected);
        assertEquals(29, selected.size());
        assertEquals(selected, published);
        assertFalse(document.contains("uploadToken"));
        assertFalse(document.contains("claimToken"));
        assertFalse(document.contains("/pipeline/"));
        assertFalse(document.contains("/approve"));
        assertFalse(document.contains("/internal-contract-fixture"));
        Path root = projectRoot();
        String digest = sourceDigest(root);
        Path output = root.resolve(".runtime/work/prd-009");
        Files.createDirectories(output);
        Files.writeString(output.resolve("backend-openapi.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(spec) + "\n");
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("serviceId", "knowledge");
        envelope.put("sourceKind", "registered-mappings");
        envelope.put("sourceDigest", digest);
        envelope.put("specSha256", sha(Files.readAllBytes(output.resolve("backend-openapi.json"))));
        envelope.put("coverage", List.of("com.ruoyi.web.controller.knowledge.*", "SysDictDataController"));
        envelope.put("operations", observed);
        Files.writeString(output.resolve("backend-observed.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(envelope) + "\n");
        assertEquals(digest, sourceDigest(root), "Source changed while exporting");
    }

    @Test
    void alternativesDoNotExposeSchemasAndConfigListsOnlyCore() throws Exception {
        for (String path : List.of("/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/default",
                "/v3/api-docs/default.yaml", "/v3/api-docs/core.yaml", "/v3/api-docs.yaml/core", "/v3/api-docs/unknown")) {
            String result = mvc.perform(get(path)).andReturn().getResponse().getContentAsString();
            assertFalse(result.contains("\"paths\""), path);
            assertFalse(result.contains("\"schemas\""), path);
        }
        String config = mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode value = json.readTree(config);
        assertEquals(1, value.path("urls").size());
        assertEquals("知识库核心接口", value.at("/urls/0/name").asText());
        assertFalse(config.contains("petstore"));
        assertFalse(value.path("persistAuthorization").asBoolean());
        assertTrue(value.path("deepLinking").asBoolean());
        String forwarded = mvc.perform(get("/v3/api-docs/swagger-config").header("X-Forwarded-Prefix", "/dev-api"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(forwarded.contains("/dev-api/v3/api-docs/core"), forwarded);
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void authorizationAndLongSerializationRemainRuntimeBehavior() throws Exception {
        mvc.perform(get("/knowledge/question/1")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401));
        authenticate(Set.of("knowledge:source:query"));
        mvc.perform(get("/knowledge/question/1").header("Authorization", "Bearer isolated"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(403));
        authenticate(Set.of("knowledge:question:query"));
        KbQuestion question = new KbQuestion();
        question.setId(Long.MAX_VALUE);
        question.setRowVersion(Long.MAX_VALUE);
        when(questions.get(1L)).thenReturn(question);
        mvc.perform(get("/knowledge/question/1").header("Authorization", "Bearer isolated"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(Long.toString(Long.MAX_VALUE)))
                .andExpect(jsonPath("$.data.rowVersion").value(Long.toString(Long.MAX_VALUE)));
        verify(questions).get(1L);
    }

    @org.junit.jupiter.api.AfterEach
    void clearPageContext() {
        com.ruoyi.common.utils.PageUtils.clearPage();
    }

    @Test
    void schemaTypesPreserveIdsAndOptionalDataWithoutInternalModels() throws Exception {
        JsonNode spec = json.readTree(mvc.perform(get("/v3/api-docs/core")).andReturn().getResponse().getContentAsString());
        JsonNode models = spec.at("/components/schemas");
        assertTrue(models.path("com.ruoyi.knowledge.domain.KbQuestion.Response").at("/properties/id/type").toString().contains("string"));
        assertEquals("string", models.path("com.ruoyi.knowledge.importing.dto.KbRecordsRequest.Record")
                .at("/properties/rowVersion/type").asText());
        assertTrue(models.path("com.ruoyi.knowledge.storage.domain.KbAsset.Response")
                .at("/properties/fileSize/type").toString().contains("string"));
        assertFalse(models.path("com.ruoyi.knowledge.importing.dto.KbRecordsRequest").path("additionalProperties").asBoolean(true));
        assertFalse(models.has("com.ruoyi.common.core.domain.entity.SysUser"));
        assertTrue(models.path("com.ruoyi.knowledge.domain.dto.KbOutlineOptions.Option.Response").path("properties").has("label"));
        assertTrue(models.path("com.ruoyi.knowledge.importing.dto.KbRecordsRequest.Option").path("properties").has("html"));
        JsonNode byKey = spec.path("paths").path("/knowledge/asset/by-key").path("get");
        JsonNode required = byKey.at("/responses/200/content/application~1json/schema/oneOf/0/required");
        assertFalse(required.toString().contains("data"));
        JsonNode detail = spec.path("paths").path("/knowledge/question/{id}").path("get");
        assertTrue(detail.at("/responses/200/content/application~1json/schema/oneOf/0/required").toString().contains("data"));
        JsonNode list = spec.path("paths").path("/knowledge/question/list").path("get");
        for (JsonNode parameter : list.path("parameters")) {
            if (parameter.path("name").asText().equals("pageSize")) {
                assertEquals("integer", parameter.at("/schema/type").asText());
                assertFalse(parameter.path("schema").has("maximum"));
            } else if (parameter.path("name").asText().equals("reasonable")) {
                assertTrue(parameter.at("/schema/default").asBoolean());
                assertTrue(parameter.path("description").asText().contains("末页"));
            }
        }
    }

    @Test
    void optionalAggregateNullsDeserializeAndRequiredInputsStayNonNull() throws Exception {
        authenticate(Set.of("knowledge:source:add", "knowledge:source:query", "knowledge:product:add", "knowledge:product:query"));
        JsonNode models = json.readTree(mvc.perform(get("/v3/api-docs/core")).andReturn().getResponse().getContentAsString())
                .at("/components/schemas");
        var contracts = new CoreApiContracts();
        for (String kind : List.of("source", "product")) {
            String id = kind.equals("source") ? "source.register" : "product.archive";
            var body = (com.fasterxml.jackson.databind.node.ObjectNode) json.valueToTree(contracts.require(id).requestExample());
            List<String> fields = kind.equals("source")
                    ? List.of("profile", "outlineScopes", "declaredKpIds", "regions", "examDetail")
                    : List.of("profile", "outlineScopes", "declaredKpIds", "regions", "questionRefs", "referenceSources", "expectedMaterialRowVersion");
            String modelName = "com.ruoyi.knowledge.dto.KbAgent" + (kind.equals("source") ? "Source" : "Product") + "Request";
            JsonNode model = models.path(modelName);
            for (String field : fields) {
                body.putNull(field);
                assertTrue(allowsNull(model.path("properties").path(field)), field);
            }
            assertFalse(allowsNull(model.at("/properties/externalKey")));
            assertFalse(allowsNull(model.path("properties").path(kind)));
            assertTrue(model.path("required").toString().contains(kind));
            mvc.perform(post("/knowledge/agent/" + kind).header("Authorization", "Bearer isolated")
                            .contentType("application/json").content(body.toString()))
                    .andExpect(jsonPath("$.code").value(200));
        }
        var source = org.mockito.ArgumentCaptor.forClass(com.ruoyi.knowledge.dto.KbAgentSourceRequest.class);
        var product = org.mockito.ArgumentCaptor.forClass(com.ruoyi.knowledge.dto.KbAgentProductRequest.class);
        verify(ingestion).saveSource(source.capture(), anyString());
        verify(ingestion).saveProduct(product.capture(), anyString());
        assertNotNull(source.getValue().source());
        assertNull(source.getValue().profile());
        assertNull(source.getValue().examDetail());
        assertNotNull(product.getValue().product());
        assertNull(product.getValue().questionRefs());
        assertNull(product.getValue().expectedMaterialRowVersion());
    }

    @Test
    void nestedUnknownImportMembersAreRejectedByJacksonAndDocumented() throws Exception {
        authenticate(Set.of("knowledge:import:execute"));
        JsonNode models = json.readTree(mvc.perform(get("/v3/api-docs/core")).andReturn().getResponse().getContentAsString())
                .at("/components/schemas");
        var valid = (com.fasterxml.jackson.databind.node.ObjectNode) json.valueToTree(
                new CoreApiContracts().require("question.importRecords").requestExample());
        for (String type : List.of("Record", "Item", "Option", "Resource", "HtmlMetadata")) {
            String modelName = type.equals("HtmlMetadata") ? "com.ruoyi.knowledge.html.HtmlMetadata"
                    : "com.ruoyi.knowledge.importing.dto.KbRecordsRequest." + type;
            assertFalse(models.path(modelName).path("additionalProperties").asBoolean(true), type);
            var body = valid.deepCopy();
            var record = (com.fasterxml.jackson.databind.node.ObjectNode) body.path("records").get(0);
            com.fasterxml.jackson.databind.node.ObjectNode target = switch (type) {
                case "Record" -> record;
                case "Item" -> record.putArray("items").addObject().put("key", "a").put("stem", "示例题干");
                case "Option" -> record.putArray("options").addObject().put("key", "A").put("text", "示例选项");
                case "Resource" -> body.putArray("resources").addObject().put("key", "example.png").put("assetId", "1");
                default -> record.withObject("metadata");
            };
            target.put("notAField", 1);
            mvc.perform(post("/knowledge/import/records").header("Authorization", "Bearer isolated")
                            .contentType("application/json").content(body.toString()))
                    .andExpect(jsonPath("$.code").value(400));
        }
        verifyNoInteractions(imports);
    }

    private static boolean allowsNull(JsonNode schema) {
        if (schema.path("type").toString().contains("null")) {
            return true;
        }
        for (JsonNode branch : schema.path("anyOf")) {
            if (allowsNull(branch)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void tokenCannotExposeAlternateDocuments() throws Exception {
        authenticate(Set.of("*:*:*"));
        for (String path : List.of("/v3/api-docs", "/v3/api-docs/default", "/v3/api-docs.yaml/core", "/v3/api-docs/core.yaml")) {
            var response = mvc.perform(get(path).header("Authorization", "Bearer isolated")).andReturn().getResponse();
            assertEquals(403, response.getStatus(), path);
            assertFalse(response.getContentAsString().contains("\"paths\""));
        }
    }

    @Test
    void internalContractCanBeCompleteWithoutPublication() throws Exception {
        var method = InternalContractFixture.class.getDeclaredMethod("lookup");
        CoreApi marker = method.getAnnotation(CoreApi.class);
        assertFalse(marker.published());
        assertTrue(new CoreApiContracts().complete(marker.value()));
        assertFalse(new CoreApiContracts().complete("missing.contract"));
        assertFalse(com.ruoyi.web.core.config.SwaggerConfig.isPublished(method));
        var operation = new io.swagger.v3.oas.models.Operation().operationId("untouched-internal");
        new CoreApiDocumentation(new CoreApiContracts()).customize(operation,
                new org.springframework.web.method.HandlerMethod(new InternalContractFixture(), method));
        assertEquals("untouched-internal", operation.getOperationId());
    }

    @Test
    void responseNullabilityMatchesJacksonWithoutRelaxingInputSchema() throws Exception {
        authenticate(Set.of("knowledge:source:query"));
        var region = new com.ruoyi.knowledge.catalog.KbRegion();
        region.setRegionCode("00000000");
        when(regions.region("00000000")).thenReturn(region);
        JsonNode body = json.readTree(mvc.perform(get("/knowledge/region/00000000")
                .header("Authorization", "Bearer isolated")).andReturn().getResponse().getContentAsString());
        assertTrue(body.path("data").has("parentCode"));
        assertTrue(body.at("/data/parentCode").isNull());
        JsonNode spec = json.readTree(mvc.perform(get("/v3/api-docs/core")).andReturn().getResponse().getContentAsString());
        JsonNode types = spec.path("components").path("schemas").path("com.ruoyi.knowledge.catalog.KbRegion.Response")
                .at("/properties/parentCode/type");
        assertTrue(types.toString().contains("null"));
        assertTrue(types.toString().contains("string"));
        JsonNode input = spec.path("components").path("schemas")
                .path("com.ruoyi.knowledge.importing.dto.KbRecordsRequest").at("/properties/schemaVersion/type");
        assertEquals("string", input.asText());
    }

    @org.springframework.web.bind.annotation.RestController
    static class InternalContractFixture {
        @CoreApi(value = "question.get", response = KbQuestion.class, published = false)
        @org.springframework.web.bind.annotation.GetMapping("/internal-contract-fixture")
        public void lookup() { }
    }

    @Test
    void emptyByKeyAndMultipartKeepExistingAuthorizationAndShape() throws Exception {
        authenticate(Set.of("knowledge:source:query"));
        mvc.perform(get("/knowledge/agent/source/by-key").param("externalKey", "missing")
                        .header("Authorization", "Bearer isolated"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data").doesNotExist());
        var file = new org.springframework.mock.web.MockMultipartFile("file", "example.txt", "text/plain", new byte[]{1, 2, 3});
        mvc.perform(multipart("/knowledge/asset/upload").file(file).param("purpose", "SOURCE_ORIGINAL")
                        .header("Authorization", "Bearer isolated"))
                .andExpect(jsonPath("$.code").value(403));
        verifyNoInteractions(assets);
        authenticate(Set.of("knowledge:asset:upload"));
        var asset = new com.ruoyi.knowledge.storage.domain.KbAsset();
        asset.setAssetId(Long.MAX_VALUE);
        asset.setFileSize(3L);
        asset.setStatus("READY");
        when(assets.upload(any(), eq("SOURCE_ORIGINAL"), isNull(), eq("fixture"))).thenReturn(asset);
        mvc.perform(multipart("/knowledge/asset/upload").file(file).param("purpose", "SOURCE_ORIGINAL")
                        .param("idempotencyKey", "fixture").header("Authorization", "Bearer isolated"))
                .andExpect(jsonPath("$.data.assetId").value(Long.toString(Long.MAX_VALUE)))
                .andExpect(jsonPath("$.data.fileSize").value("3"));
    }

    @Test
    void binarySuccessJsonFailureAndPartialImportAreDifferentResults() throws Exception {
        authenticate(Set.of("knowledge:question:export", "knowledge:import:execute"));
        when(exports.export(any())).thenReturn(new byte[]{80, 75, 3, 4});
        mvc.perform(post("/knowledge/export/questions").header("Authorization", "Bearer isolated")
                        .contentType("application/json").content("{\"questionIds\":[\"1\"]}"))
                .andExpect(content().contentType("application/zip")).andExpect(content().bytes(new byte[]{80, 75, 3, 4}));
        when(exports.export(any())).thenThrow(new com.ruoyi.common.exception.ServiceException("示例导出拒绝"));
        mvc.perform(post("/knowledge/export/questions").header("Authorization", "Bearer isolated")
                        .contentType("application/json").content("{\"questionIds\":[\"1\"]}"))
                .andExpect(content().contentTypeCompatibleWith("application/json")).andExpect(jsonPath("$.code").value(500));
        var batch = new com.ruoyi.knowledge.importing.domain.KbImportBatch();
        batch.setBatchId(Long.MAX_VALUE);
        batch.setStatus("PARTIAL");
        batch.setSuccessCount(1);
        batch.setFailureCount(1);
        var failed = new com.ruoyi.knowledge.importing.domain.KbImportItem();
        failed.setClientKey("fixture-failed");
        failed.setStatus("FAILED");
        failed.setMessage("示例逐项拒绝");
        batch.setItems(List.of(failed));
        when(imports.importRecords(any(), isNull())).thenReturn(batch);
        mvc.perform(post("/knowledge/import/records").header("Authorization", "Bearer isolated")
                        .contentType("application/json").content("{\"schemaVersion\":\"kb-question-records/v1\",\"records\":[]}"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.status").value("PARTIAL"))
                .andExpect(jsonPath("$.data.items[0].status").value("FAILED"));
        mvc.perform(post("/knowledge/import/records").header("Authorization", "Bearer isolated")
                        .contentType("application/json").content("{\"notAField\":1}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    private static Set<String> expectedCorePaths() {
        return Set.of(
                "POST /knowledge/asset/upload", "GET /knowledge/asset/by-key", "GET /knowledge/asset/{assetId}",
                "GET /knowledge/asset/{assetId}/content", "POST /knowledge/agent/source", "GET /knowledge/source/list",
                "GET /knowledge/source/{id}", "GET /knowledge/agent/source/by-key", "GET /knowledge/import/schema",
                "POST /knowledge/import/records", "GET /knowledge/import/{batchId}", "POST /knowledge/import/{batchId}/resume",
                "GET /knowledge/question/list", "GET /knowledge/question/{id}", "POST /knowledge/export/questions",
                "POST /knowledge/agent/product", "GET /knowledge/product/list", "GET /knowledge/product/{id}",
                "GET /knowledge/agent/product/by-key", "GET /system/dict/data/type/{dictType}",
                "GET /knowledge/outline/options", "GET /knowledge/outline/list", "GET /knowledge/outline/{id}",
                "GET /knowledge/kp/list", "GET /knowledge/kp/{id}", "GET /knowledge/region/list", "GET /knowledge/region/{code}",
                "GET /knowledge/material/list", "GET /knowledge/material/{id}");
    }

    private void authenticate(Set<String> permissions) {
        SysUser user = new SysUser();
        user.setUserId(900001L);
        user.setUserName("isolated-api-test");
        LoginUser login = new LoginUser(900001L, 1L, user, permissions);
        when(tokens.getLoginUser(any())).thenAnswer(invocation -> {
            jakarta.servlet.http.HttpServletRequest request = invocation.getArgument(0);
            return request != null && "Bearer isolated".equals(request.getHeader("Authorization")) ? login : null;
        });
    }

    private List<Map<String, Object>> observed() throws Exception {
        var mapping = context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        List<Map<String, Object>> result = new ArrayList<>();
        Path root = projectRoot();
        for (var entry : mapping.getHandlerMethods().entrySet()) {
            var handler = entry.getValue();
            if (!handler.getBeanType().getName().startsWith("com.ruoyi.web.controller.")) {
                continue;
            }
            CoreApi marker = handler.getMethodAnnotation(CoreApi.class);
            String sourceFile = "backend/ruoyi-admin/src/main/java/" + handler.getBeanType().getName().replace('.', '/') + ".java";
            for (var method : entry.getKey().getMethodsCondition().getMethods()) {
                for (String path : entry.getKey().getPatternValues()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("method", method.name());
                    item.put("path", path);
                    item.put("core", marker != null && marker.published());
                    item.put("contractComplete", marker != null && new CoreApiContracts().complete(marker.value()));
                    if (marker != null) {
                        item.put("operationId", marker.value());
                    }
                    item.put("source", handler.getBeanType().getName() + "#" + handler.getMethod().getName());
                    item.put("sourceFile", sourceFile);
                    item.put("sourceHash", sha(Files.readAllBytes(root.resolve(sourceFile))));
                    result.add(item);
                }
            }
        }
        result.sort(Comparator.comparing(item -> item.get("path") + " " + item.get("method")));
        return result;
    }

    static Path projectRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.isDirectory(path.resolve("backend/ruoyi-admin"))) {
            path = path.getParent();
        }
        if (path == null) { throw new IllegalStateException("Cannot find codeplace"); }
        return path;
    }

    static String sourceDigest(Path root) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var stream = Files.walk(root.resolve("backend"))) {
            List<Path> paths = stream.filter(Files::isRegularFile)
                    .filter(path -> !path.toString().matches(".*[/](target|\\.git|node_modules)[/].*"))
                    .filter(path -> path.toString().matches(".*\\.(java|xml|yml|yaml|json|properties)$"))
                    .sorted(Comparator.comparing(path -> root.relativize(path).toString())).toList();
            for (Path path : paths) {
                String row = root.relativize(path) + "\0" + sha(Files.readAllBytes(path)) + "\n";
                digest.update(row.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha(byte[] input) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
    }
}
