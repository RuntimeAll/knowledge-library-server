package com.ruoyi.web.core.config;

import com.ruoyi.web.core.docs.CoreApi;
import com.ruoyi.web.core.docs.CoreApiContracts;
import com.ruoyi.web.core.docs.CoreApiDocumentation;
import io.swagger.v3.core.jackson.TypeNameResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Publishes only explicitly selected core handlers; business permissions are unchanged. */
@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI customOpenApi() {
        TypeNameResolver.std.setUseFqn(true);
        return new OpenAPI().components(new Components().addSecuritySchemes("bearerAuth",
                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .info(new Info().title("知识库核心接口").version("1")
                        .description("公开阅读、授权调用。使用 POST /login 获取自己的token，填入Authorize；POST /logout退出。"
                                + "刷新不保留token。HTTP 200不一定业务成功，请检查code和导入逐项结果。"
                                + "写操作会影响当前环境，只有手动Execute才发送；示例ID必须换成实际授权数据。"));
    }

    public static boolean isPublished(java.lang.reflect.Method method) {
        CoreApi contract = method.getAnnotation(CoreApi.class);
        return contract != null && contract.published();
    }

    @Bean
    public GroupedOpenApi coreApi() {
        CoreApiDocumentation documentation = new CoreApiDocumentation(new CoreApiContracts());
        return GroupedOpenApi.builder().group("core").displayName("知识库核心接口")
                .addOpenApiMethodFilter(SwaggerConfig::isPublished)
                .addOperationCustomizer(documentation).addOpenApiCustomizer(documentation).build();
    }
}
