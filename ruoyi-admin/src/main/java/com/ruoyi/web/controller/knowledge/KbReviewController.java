package com.ruoyi.web.controller.knowledge;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.knowledge.review.KbReviewService;
import com.ruoyi.knowledge.review.KbReviewProofs;
import com.ruoyi.knowledge.review.ReviewModels;
import com.ruoyi.web.core.docs.CoreApi;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Explicit, permission-separated current-version review actions. */
@RestController
@RequestMapping("/knowledge/question")
public class KbReviewController extends BaseController {
    private final KbReviewService service;
    private final KbReviewProofs proofs;
    public KbReviewController(KbReviewService service, KbReviewProofs proofs) { this.service = service; this.proofs = proofs; }

    @GetMapping("/{id}/readiness")
    @PreAuthorize("@ss.hasPermi('knowledge:question:query')")
    @CoreApi(value = "review.readiness", response = ReviewModels.Readiness.class, published = false)
    public AjaxResult readiness(@PathVariable Long id) { return success(service.readiness(id)); }

    @PostMapping("/{id}/approve")
    @PreAuthorize("@ss.hasPermi('knowledge:question:approve')")
    @Log(title = "题目批准", businessType = BusinessType.UPDATE)
    @CoreApi(value = "review.approve", response = ReviewModels.Receipt.class, published = false)
    public AjaxResult approve(@PathVariable Long id, @Valid @RequestBody ReviewModels.Command command) {
        return success(service.act(id, "APPROVE", command));
    }

    @PostMapping("/approve-batch")
    @PreAuthorize("@ss.hasPermi('knowledge:question:approve')")
    @Log(title = "题目逐项批准", businessType = BusinessType.UPDATE)
    @CoreApi(value = "review.approveBatch", response = ReviewModels.BatchResult.class, published = false)
    public AjaxResult approveBatch(@Valid @RequestBody ReviewModels.BatchCommand command) { return success(service.approveBatch(command)); }

    @PostMapping("/{id}/return-to-draft")
    @PreAuthorize("@ss.hasPermi('knowledge:question:edit')")
    @Log(title = "题目退回", businessType = BusinessType.UPDATE)
    @CoreApi(value = "review.returnToDraft", response = ReviewModels.Receipt.class, published = false)
    public AjaxResult returnToDraft(@PathVariable Long id, @Valid @RequestBody ReviewModels.Command command) {
        return success(service.act(id, "RETURN_TO_DRAFT", command));
    }

    @PostMapping("/{id}/soft-delete")
    @PreAuthorize("@ss.hasPermi('knowledge:question:remove')")
    @Log(title = "题目软删除", businessType = BusinessType.DELETE)
    @CoreApi(value = "review.softDelete", response = ReviewModels.Receipt.class, published = false)
    public AjaxResult softDelete(@PathVariable Long id, @Valid @RequestBody ReviewModels.Command command) {
        return success(service.act(id, "SOFT_DELETE", command));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("@ss.hasPermi('knowledge:question:restore')")
    @Log(title = "题目恢复", businessType = BusinessType.UPDATE)
    @CoreApi(value = "review.restore", response = ReviewModels.Receipt.class, published = false)
    public AjaxResult restore(@PathVariable Long id, @Valid @RequestBody ReviewModels.Command command) {
        return success(service.act(id, "RESTORE", command));
    }

    @PostMapping("/granularity-exceptions")
    @PreAuthorize("@ss.hasPermi('knowledge:question:granularityException')")
    @Log(title = "计算练习数量例外", businessType = BusinessType.UPDATE)
    @CoreApi(value = "review.granularityException", response = ReviewModels.GranularityException.class, published = false)
    public AjaxResult exception(@RequestBody ReviewModels.ExceptionCommand command) {
        if (!service.enabled()) { throw new com.ruoyi.common.exception.ServiceException("质量审批尚未启用", 503); }
        return success(proofs.grant(command));
    }
}
