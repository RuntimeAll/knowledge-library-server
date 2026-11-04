package com.ruoyi.web.controller.knowledge;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.knowledge.publication.PublicationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(-10)
@RestControllerAdvice(assignableTypes = {PublicationController.class, PublicationPublicController.class})
public class PublicationErrorHandler {
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class, BindException.class})
    public AjaxResult invalidRequest(Exception error, HttpServletRequest request, HttpServletResponse response) {
        if (!response.isCommitted()) { response.reset(); }
        response.setStatus(400);
        response.setHeader("Cache-Control", "no-store");
        if (isPublic(request)) { return AjaxResult.error(400, "请求参数无效"); }
        PublicationException invalid = new PublicationException(400, "INVALID_REQUEST", "请求参数无效");
        return new AjaxResult(invalid.getCode(), invalid.getMessage(), invalid.getDetail());
    }

    @ExceptionHandler(PublicationException.class)
    public AjaxResult handle(PublicationException error, HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        if (isPublic(request)) {
            if (!response.isCommitted()) { response.reset(); }
            response.setHeader("Cache-Control", "no-store");
            response.setStatus(error.getCode() >= 500 ? 503 : error.getCode() == 400 ? 400 : 404);
            return AjaxResult.error(response.getStatus(), error.getCode() >= 500 ? "资料暂不可用，请稍后重试" : "该分享资料不可用");
        }
        return new AjaxResult(error.getCode(), error.getMessage(), error.getDetail());
    }

    private boolean isPublic(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/public/") || request.getRequestURI().startsWith("/p/");
    }
}
