package com.ruoyi.web.core.docs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.knowledge.publication.PublicationException;
import com.ruoyi.knowledge.publication.PublicationModels;
import com.ruoyi.knowledge.publication.PublicationPublicService;
import com.ruoyi.knowledge.publication.PublicationService;
import com.ruoyi.knowledge.storage.IKbAssetService;
import com.ruoyi.knowledge.storage.domain.KbAsset;
import com.ruoyi.web.controller.knowledge.PublicationErrorHandler;
import com.ruoyi.web.controller.knowledge.PublicationPublicErrorHandler;
import jakarta.servlet.Filter;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Real MVC binding/security/advice; no SQL, storage client or account is started. */
@SpringBootTest(classes = IsolatedApiApplication.class, properties = {
    "spring.config.location=optional:classpath:/api-docs-test-missing.yml",
    "springdoc.enable-default-api-docs=false", "springdoc.api-docs.version=OPENAPI_3_1", "springdoc.use-fqn=true",
    "logging.config=classpath:logback-test.xml", "logging.level.root=WARN", "spring.main.banner-mode=off"
})
@Import({PublicationErrorHandler.class, PublicationPublicErrorHandler.class})
class PublicationAnonymousHttpTest {
    private static final String CODE = "0123456789abcdef0123456789abcdef";
    private static final String BASE = "/public/publications/" + CODE;
    @Autowired WebApplicationContext context;
    @Autowired TokenService tokens;
    @Autowired PublicationPublicService publications;
    @Autowired PublicationService administration;
    @Autowired IKbAssetService assets;
    private MockMvc mvc;

    @BeforeEach void setup() {
        reset(tokens, publications, administration, assets);
        SecurityContextHolder.clearContext();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void fiveShareGetRoutesAreAnonymousButPostAndAdminRoutesAreNot() throws Exception {
        when(publications.get(CODE)).thenReturn(new PublicationModels.PublicPublication(CODE, "1", "资料", "ON_SALE",
                "标题", "正文", null, List.of(), List.of(), List.of(), new PublicationModels.RelationCounts(1, 1, 1, 1, 1)));
        when(publications.materials(CODE, "BODY", null, null)).thenReturn(new PublicationModels.Materials("BODY", "1", null, null, "正文"));
        when(publications.relations(eq(CODE), any())).thenReturn(new PublicationModels.Page<>(List.of(), 0));
        KbAsset asset = asset();
        when(publications.asset(CODE, 111L, 1L)).thenReturn(asset);
        when(publications.page(CODE)).thenReturn(asset);
        when(assets.openContent(111L)).thenAnswer(ignored -> new ByteArrayInputStream(new byte[]{1, 2, 3}));
        mvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ON_SALE"));
        mvc.perform(get(BASE + "/materials").param("kind", "BODY")).andExpect(status().isOk()).andExpect(jsonPath("$.data.text").value("正文"));
        mvc.perform(get(BASE + "/relations").param("objectType", "QUESTION")).andExpect(status().isOk()).andExpect(jsonPath("$.rows").isArray());
        mvc.perform(get(BASE + "/assets/111/content").param("contentVersion", "1")).andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1, 2, 3}));
        mvc.perform(get("/p/" + CODE)).andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1, 2, 3}));
        clearInvocations(publications, assets);
        for (String path : List.of(BASE, BASE + "/materials", BASE + "/relations", BASE + "/assets/111/content",
                "/p/" + CODE, "/publication-static/assets/app-abcdef.js", "/knowledge/publications/1/sale")) {
            mvc.perform(post(path)).andExpect(jsonPath("$.code").value(401));
        }
        mvc.perform(get("/knowledge/publications/1")).andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(publications, assets, administration);
    }

    @Test void publicSqlAndFileErrorsAreFixed503WithoutDiagnosticsOrOldAssetHeaders() throws Exception {
        String privateMessage = "SELECT secret FROM kb_question at /private/server/credentials";
        when(publications.relations(eq(CODE), any())).thenThrow(new IllegalStateException(privateMessage));
        var sql = mvc.perform(get(BASE + "/relations").param("objectType", "QUESTION"))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value(503)).andExpect(jsonPath("$.msg").value("资料暂不可用，请稍后重试"))
                .andReturn().getResponse();
        assertFalse(sql.getContentAsString().contains("SELECT")); assertFalse(sql.getContentAsString().contains("/private/"));
        when(publications.asset(CODE, 111L, 1L)).thenReturn(asset());
        when(assets.openContent(111L)).thenThrow(new IllegalStateException(privateMessage));
        var file = mvc.perform(get(BASE + "/assets/111/content").param("contentVersion", "1"))
                .andExpect(status().isServiceUnavailable()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value(503)).andReturn().getResponse();
        assertNotEquals("3", file.getHeader("Content-Length"));
        assertFalse(file.getContentAsString().contains(privateMessage));
    }

    @Test void invalidQueryBindingReturns400AndTypedPublicErrorIsSanitized() throws Exception {
        mvc.perform(get(BASE + "/materials")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        mvc.perform(get(BASE + "/relations").param("objectType", "QUESTION").param("pageSize", "not-an-int"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400))
                .andExpect(header().string("Cache-Control", "no-store"));
        when(publications.get(CODE)).thenThrow(new PublicationException(403, "FORBIDDEN", "private actor path"));
        mvc.perform(get(BASE)).andExpect(status().isNotFound()).andExpect(jsonPath("$.msg").value("该分享资料不可用"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test void administrativePermissionErrorsKeepTheirBusinessCode() throws Exception {
        var user = new com.ruoyi.common.core.domain.entity.SysUser();
        user.setUserId(77L); user.setUserName("query-only");
        var login = new com.ruoyi.common.core.domain.model.LoginUser(77L, 1L, user,
                java.util.Set.of("knowledge:publication:query"));
        when(tokens.getLoginUser(any())).thenReturn(login);
        when(administration.detail(1L)).thenThrow(new PublicationException(403, "FORBIDDEN", "无权管理"));
        mvc.perform(get("/knowledge/publications/1").header("Authorization", "Bearer isolated"))
                .andExpect(jsonPath("$.code").value(403)).andExpect(jsonPath("$.msg").value("无权管理"));
        mvc.perform(post("/knowledge/publications/1/sale").header("Authorization", "Bearer isolated")
                        .contentType("application/json").content("{\"operationKey\":\"attempt-sale\"}"))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test void administrativeInvalidBodiesAndBindingsAre400WithoutJacksonDiagnostics() throws Exception {
        var user = new com.ruoyi.common.core.domain.entity.SysUser();
        user.setUserId(78L); user.setUserName("publication-editor");
        var login = new com.ruoyi.common.core.domain.model.LoginUser(78L, 1L, user,
                java.util.Set.of("knowledge:publication:add", "knowledge:publication:list"));
        when(tokens.getLoginUser(any())).thenReturn(login);
        for (String body : List.of("{\"operationKey\":\"invalid-actor\",\"actor\":\"forged-user\"}",
                "{\"operationKey\":", "{\"operationKey\":\" \"}")) {
            var response = mvc.perform(post("/knowledge/publications").header("Authorization", "Bearer isolated")
                            .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.data.errorCode").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.msg").value("请求参数无效"))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andReturn().getResponse();
            assertFalse(response.getContentAsString().contains("jackson"));
            assertFalse(response.getContentAsString().contains("forged-user"));
        }
        mvc.perform(get("/knowledge/publications").header("Authorization", "Bearer isolated").param("pageSize", "invalid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.errorCode").value("INVALID_REQUEST"));
        verifyNoInteractions(administration);
    }

    private static KbAsset asset() {
        KbAsset asset = new KbAsset(); asset.setAssetId(111L); asset.setContentType("image/png");
        asset.setOriginalName("sample.png"); asset.setFileSize(3L); return asset;
    }
}
