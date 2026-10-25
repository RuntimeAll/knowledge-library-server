package com.ruoyi.web.core.docs;

import com.ruoyi.framework.config.SecurityConfig;
import com.ruoyi.framework.config.properties.PermitAllUrlProperties;
import com.ruoyi.framework.security.filter.JwtAuthenticationTokenFilter;
import com.ruoyi.framework.security.handle.AuthenticationEntryPointImpl;
import com.ruoyi.framework.security.handle.LogoutSuccessHandlerImpl;
import com.ruoyi.framework.web.exception.GlobalExceptionHandler;
import com.ruoyi.framework.web.service.PermissionService;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.web.core.config.SwaggerConfig;
import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Set;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/** No component-wide production scan, datasource, Redis, scheduler or object storage. */
@Configuration
@EnableWebSecurity
@Import({SwaggerConfig.class, SecurityConfig.class, GlobalExceptionHandler.class,
        CoreApiExportTest.InternalContractFixture.class,
        IsolatedApiApplication.ControllerRegistry.class})
@ImportAutoConfiguration({JacksonAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
        WebMvcAutoConfiguration.class, org.springdoc.core.configuration.SpringDocConfiguration.class,
        org.springdoc.core.properties.SpringDocConfigProperties.class,
        org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration.class,
        org.springdoc.webmvc.core.configuration.MultipleOpenApiSupportConfiguration.class,
        org.springdoc.webmvc.ui.SwaggerConfig.class,
        org.springdoc.core.properties.SwaggerUiConfigProperties.class,
        org.springdoc.core.properties.SwaggerUiOAuthProperties.class})
public class IsolatedApiApplication {
    @Bean
    static org.springframework.beans.factory.config.BeanFactoryPostProcessor isolatedSecurityDependencies() {
        return factory -> {
            factory.registerSingleton("tokenService", Mockito.mock(TokenService.class));
            factory.registerSingleton("logoutHandler", Mockito.mock(LogoutSuccessHandlerImpl.class));
        };
    }
    @Bean
    JwtAuthenticationTokenFilter tokenFilter() { return new JwtAuthenticationTokenFilter(); }
    @Bean
    AuthenticationEntryPointImpl entryPoint() { return new AuthenticationEntryPointImpl(); }
    @Bean
    PermitAllUrlProperties publicUrls() { return new PermitAllUrlProperties(); }
    @Bean
    CorsFilter corsFilter() { return new CorsFilter(new UrlBasedCorsConfigurationSource()); }
    @Bean(name = "ss")
    PermissionService permissions() { return new PermissionService(); }

    static class ControllerRegistry implements ImportBeanDefinitionRegistrar {
        @Override
        public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
            Set<Class<?>> controllers = controllerTypes();
            Set<Class<?>> services = new LinkedHashSet<>();
            for (Class<?> type : controllers) {
                for (var constructor : type.getConstructors()) {
                    services.addAll(java.util.List.of(constructor.getParameterTypes()));
                }
                for (Field field : type.getDeclaredFields()) {
                    if (field.isAnnotationPresent(Autowired.class)) {
                        services.add(field.getType());
                    }
                }
                registry.registerBeanDefinition(type.getName(), new RootBeanDefinition(type));
            }
            for (Class<?> type : services) {
                RootBeanDefinition definition = new RootBeanDefinition(type);
                definition.setInstanceSupplier(() -> Mockito.mock(type));
                registry.registerBeanDefinition(type.getName(), definition);
            }
        }
    }

    static Set<Class<?>> controllerTypes() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<Class<?>> types = new LinkedHashSet<>();
        scanner.findCandidateComponents("com.ruoyi.web.controller.knowledge").forEach(candidate -> {
            try {
                types.add(Class.forName(candidate.getBeanClassName()));
            } catch (ClassNotFoundException exception) {
                throw new IllegalStateException(exception);
            }
        });
        types.add(com.ruoyi.web.controller.system.SysDictDataController.class);
        return types;
    }
}
