package com.ruoyi.web.controller.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.framework.web.service.PermissionService;
import com.ruoyi.knowledge.publication.*;
import com.ruoyi.knowledge.publication.PublicationModels.Command;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

class PublicationPermissionTest {
    @Configuration @EnableMethodSecurity(proxyTargetClass = true) static class SecurityConfiguration { }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes(); }
    @Test void queryAndRegistrationCannotGrantSaleDeleteOrReconcile() {
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(new org.springframework.mock.web.MockHttpServletRequest()));
        try (var context = new AnnotationConfigApplicationContext()) {
            var service = mock(PublicationService.class); var checks = mock(PublicationCheckService.class);
            var sale = mock(PublicationSaleService.class); var shares = mock(PublicationShareService.class);
            context.register(SecurityConfiguration.class);
            context.registerBean("ss", PermissionService.class, PermissionService::new);
            context.registerBean(PublicationController.class, () -> new PublicationController(service, checks, sale, shares, mock(com.ruoyi.knowledge.publication.PublicationDownloadService.class)));
            context.refresh(); login(Set.of("knowledge:publication:query", "knowledge:publication:add"));
            PublicationController controller = context.getBean(PublicationController.class); Command command = new Command();
            assertThrows(AccessDeniedException.class, () -> controller.sale(1L, command));
            assertThrows(AccessDeniedException.class, () -> controller.delete(1L, command));
            assertThrows(AccessDeniedException.class, () -> controller.reconcile(1L, 2L, command));
            assertThrows(AccessDeniedException.class, () -> controller.retry(1L, command));
            verifyNoInteractions(sale, shares, checks, service);
            controller.get(1L); verify(service).detail(1L);
            login(Set.of("knowledge:publication:sale")); controller.sale(1L, command); verify(sale).sale(1L, command);
        }
    }
    private static void login(Set<String> permissions) {
        SysUser user = new SysUser(); user.setUserId(77L); user.setUserName("writer");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(77L, null, user, permissions), null, List.of()));
    }
}
