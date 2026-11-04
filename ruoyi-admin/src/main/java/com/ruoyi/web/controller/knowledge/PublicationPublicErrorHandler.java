package com.ruoyi.web.controller.knowledge;

import com.ruoyi.common.core.domain.AjaxResult;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Never expose storage, SQL, or ledger diagnostics through anonymous endpoints. */
@Order(-9)
@RestControllerAdvice(assignableTypes = PublicationPublicController.class)
public class PublicationPublicErrorHandler {
    @ExceptionHandler(Exception.class)
    public AjaxResult dependency(Exception error, HttpServletResponse response) {
        boolean invalid = error instanceof org.springframework.web.bind.MissingServletRequestParameterException
                || error instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
                || error instanceof org.springframework.validation.BindException;
        // A failed open may have already set the asset's length/type, but emitted no bytes.
        if (!response.isCommitted()) { response.reset(); }
        response.setStatus(invalid ? 400 : 503);
        response.setHeader("Cache-Control", "no-store");
        return AjaxResult.error(response.getStatus(), invalid ? "请求参数无效" : "资料暂不可用，请稍后重试");
    }
}
