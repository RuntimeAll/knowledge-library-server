package com.ruoyi.web.controller.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.framework.web.service.PermissionService;
import com.ruoyi.knowledge.review.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

class KbReviewPermissionTest {
    @Configuration @EnableMethodSecurity(proxyTargetClass=true) static class SecurityConfiguration { }
    @AfterEach void clear(){SecurityContextHolder.clearContext();org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
    @Test void ordinaryQueryEditAndImportNeverGrantApprovalExceptionOrRestore() {
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(new org.springframework.mock.web.MockHttpServletRequest()));
        try(var context=new AnnotationConfigApplicationContext()) {
            var service=mock(KbReviewService.class);var proofs=mock(KbReviewProofs.class);
            context.register(SecurityConfiguration.class);
            context.registerBean("ss",PermissionService.class,PermissionService::new);
            context.registerBean(KbReviewController.class,()->new KbReviewController(service,proofs));
            context.registerBean(KbCandidateController.class,()->new KbCandidateController(proofs));context.refresh();
            login(Set.of("knowledge:question:query","knowledge:question:edit","knowledge:import:execute"));
            var controller=context.getBean(KbReviewController.class);
            var command=new ReviewModels.Command("stable",0L,"review",true);
            assertThrows(AccessDeniedException.class,()->controller.approve(1L,command));
            assertThrows(AccessDeniedException.class,()->controller.approveBatch(new ReviewModels.BatchCommand(List.of())));
            assertThrows(AccessDeniedException.class,()->controller.exception(new ReviewModels.ExceptionCommand("e","reason",1L,0L,null,null,null)));
            assertThrows(AccessDeniedException.class,()->controller.restore(1L,command));
            verifyNoInteractions(service,proofs);
            controller.readiness(1L);verify(service).readiness(1L);
            context.getBean(KbCandidateController.class).candidate("run","key");verify(proofs).candidate("run","key");
            login(Set.of("knowledge:question:approve"));controller.approve(1L,command);verify(service).act(1L,"APPROVE",command);
        }
    }
    private static void login(Set<String> permissions){SysUser user=new SysUser();user.setUserId(77L);user.setUserName("writer");SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(77L,null,user,permissions),null,List.of()));}
}
