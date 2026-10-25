package com.ruoyi.web.controller.knowledge;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.knowledge.dto.KbAgentProductRequest;
import com.ruoyi.knowledge.dto.KbAgentSourceRequest;
import com.ruoyi.knowledge.service.IKbAgentIngestionService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Agent convenience endpoints reuse ordinary source/product authorization and services. */
@RestController
@RequestMapping("/knowledge/agent")
public class KbAgentController extends BaseController {
    private final IKbAgentIngestionService service;

    public KbAgentController(IKbAgentIngestionService service) {
        this.service = service;
    }

    @GetMapping("/source/by-key")
    @PreAuthorize("@ss.hasPermi('knowledge:source:query')")
    @com.ruoyi.web.core.docs.CoreApi(value = "source.findSubmission", response = com.ruoyi.knowledge.domain.KbSource.class)
    public AjaxResult sourceByKey(@RequestParam String externalKey) {
        return success(service.findSource(externalKey, getUsername()));
    }

    @GetMapping("/product/by-key")
    @PreAuthorize("@ss.hasPermi('knowledge:product:query')")
    @com.ruoyi.web.core.docs.CoreApi(value = "product.findSubmission", response = com.ruoyi.knowledge.domain.KbProduct.class)
    public AjaxResult productByKey(@RequestParam String externalKey) {
        return success(service.findProduct(externalKey, getUsername()));
    }

    @PostMapping("/source")
    @PreAuthorize("@ss.hasPermi('knowledge:source:add') and @ss.hasPermi('knowledge:source:query')")
    @Log(title = "Agent来源登记", businessType = BusinessType.INSERT)
    @com.ruoyi.web.core.docs.CoreApi(value = "source.register", response = com.ruoyi.knowledge.domain.KbSource.class)
    public AjaxResult source(@RequestBody KbAgentSourceRequest request) {
        return success(service.saveSource(request, getUsername()));
    }

    @PostMapping("/product")
    @PreAuthorize(
            "@ss.hasPermi('knowledge:product:add') and @ss.hasPermi('knowledge:product:query')")
    @Log(title = "Agent成品归档", businessType = BusinessType.INSERT)
    @com.ruoyi.web.core.docs.CoreApi(value = "product.archive", response = com.ruoyi.knowledge.domain.KbProduct.class)
    public AjaxResult product(@RequestBody KbAgentProductRequest request) {
        return success(service.saveProduct(request, getUsername()));
    }
}
