package com.ruoyi.web.controller.knowledge;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.knowledge.publication.PublicationService;
import com.ruoyi.knowledge.publication.PublicationCheckService;
import com.ruoyi.knowledge.publication.PublicationSaleService;
import com.ruoyi.knowledge.publication.PublicationShareService;
import com.ruoyi.knowledge.publication.PublicationModels.Command;
import com.ruoyi.knowledge.publication.PublicationModels.ListQuery;
import com.ruoyi.knowledge.publication.PublicationModels.Summary;
import com.ruoyi.knowledge.publication.PublicationModels.Detail;
import com.ruoyi.knowledge.publication.PublicationModels.Page;
import com.ruoyi.knowledge.publication.PublicationModels.ImportResult;
import com.ruoyi.knowledge.publication.PublicationModels.CheckResult;
import com.ruoyi.knowledge.publication.PublicationModels.OperationReceipt;
import com.ruoyi.knowledge.publication.PublicationModels.SaleResult;
import com.ruoyi.web.core.docs.CoreApi;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/knowledge/publications")
public class PublicationController {
    private final PublicationService publications;
    private final PublicationCheckService checks;
    private final PublicationSaleService sales;
    private final PublicationShareService shares;
    private final com.ruoyi.knowledge.publication.PublicationDownloadService downloads;
    public PublicationController(PublicationService publications, PublicationCheckService checks,
            PublicationSaleService sales, PublicationShareService shares, com.ruoyi.knowledge.publication.PublicationDownloadService downloads) {
        this.publications = publications; this.checks = checks; this.sales = sales; this.shares = shares; this.downloads = downloads;
    }
    @InitBinder
    public void bind(org.springframework.web.bind.WebDataBinder binder) { binder.initDirectFieldAccess(); }
    @PostMapping("")
    @PreAuthorize("@ss.hasPermi('knowledge:publication:add')")
    @CoreApi(value = "publication.create", published = false, response = Detail.class)
    @Log(title = "发布台管理", businessType = BusinessType.UPDATE)
    public AjaxResult create(@Valid @RequestBody Command command) { return AjaxResult.success(publications.create(command)); }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('knowledge:publication:query')")
    @CoreApi(value = "publication.get", published = false, response = Detail.class)
    public AjaxResult get(@PathVariable Long id) { return AjaxResult.success(publications.detail(id)); }

    @GetMapping("/{id}/download")
    @PreAuthorize("@ss.hasPermi('knowledge:publication:query')")
    @CoreApi(value = "publication.download", published = false, response = com.ruoyi.knowledge.publication.PublicationModels.Download.class)
    public AjaxResult download(@PathVariable Long id, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return AjaxResult.success(downloads.issue(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('knowledge:publication:edit')")
    @CoreApi(value = "publication.update", published = false, response = Detail.class)
    @Log(title = "发布台管理", businessType = BusinessType.UPDATE)
    public AjaxResult update(@PathVariable Long id, @Valid @RequestBody Command command) { return AjaxResult.success(publications.update(id, command)); }

    @PostMapping("/imports")
    @PreAuthorize("@ss.hasPermi('knowledge:publication:import')")
    @CoreApi(value = "publication.import", published = false, response = ImportResult.class)
    @Log(title = "发布台管理", businessType = BusinessType.UPDATE)
    public AjaxResult importOne(@Valid @RequestBody Command command) { return AjaxResult.success(publications.importOne(command)); }

    @GetMapping("/{id}/operations/by-key")
    @PreAuthorize("@ss.hasPermi('knowledge:publication:query')")
    @CoreApi(value = "publication.operationByKey", published = false, response = OperationReceipt.class)
    public AjaxResult operation(@PathVariable Long id, @RequestParam String operationKey) { return AjaxResult.success(publications.operation(id, operationKey)); }

    @GetMapping("/{id}/checks/{checkId}")
    @PreAuthorize("@ss.hasPermi('knowledge:publication:query')")
    @CoreApi(value = "publication.checkGet", published = false, response = CheckResult.class)
    public AjaxResult checkResult(@PathVariable Long id, @PathVariable Long checkId) { return AjaxResult.success(publications.check(id, checkId)); }

    @PostMapping("/{id}/checks")
    @PreAuthorize("@ss.hasPermi('knowledge:publication:check')")
    @CoreApi(value = "publication.check", published = false, response = CheckResult.class)
    @Log(title = "发布台管理", businessType = BusinessType.UPDATE)
    public AjaxResult check(@PathVariable Long id, @Valid @RequestBody Command command) { return AjaxResult.success(checks.run(id, command)); }

    @PostMapping("/{id}/sale")
    @PreAuthorize("@ss.hasPermi('knowledge:publication:sale')")
    @CoreApi(value = "publication.sale", published = false, response = SaleResult.class)
    @Log(title = "发布台管理", businessType = BusinessType.UPDATE)
    public AjaxResult sale(@PathVariable Long id, @Valid @RequestBody Command command) { return AjaxResult.success(sales.sale(id, command)); }

    @PostMapping("/{id}/operations/{operationId}/reconcile")
    @PreAuthorize("@ss.hasPermi('knowledge:publication:sale')")
    @CoreApi(value = "publication.reconcile", published = false, response = OperationReceipt.class)
    @Log(title = "发布台管理", businessType = BusinessType.UPDATE)
    public AjaxResult reconcile(@PathVariable Long id, @PathVariable Long operationId, @Valid @RequestBody Command command) { return AjaxResult.success(sales.reconcileAndClose(id, operationId, command)); }

    @PostMapping("/{id}/retract")
    @PreAuthorize("@ss.hasPermi('knowledge:publication:retract')")
    @CoreApi(value = "publication.retract", published = false, response = OperationReceipt.class)
    @Log(title = "发布台管理", businessType = BusinessType.UPDATE)
    public AjaxResult retract(@PathVariable Long id, @Valid @RequestBody Command command) { return AjaxResult.success(publications.state(id, command, "RETRACT")); }

    @PostMapping("/{id}/delete")
    @PreAuthorize("@ss.hasPermi('knowledge:publication:remove')")
    @CoreApi(value = "publication.delete", published = false, response = OperationReceipt.class)
    @Log(title = "发布台管理", businessType = BusinessType.UPDATE)
    public AjaxResult delete(@PathVariable Long id, @Valid @RequestBody Command command) { return AjaxResult.success(publications.state(id, command, "DELETE")); }

    @PostMapping("/{id}/restore")
    @PreAuthorize("@ss.hasPermi('knowledge:publication:restore')")
    @CoreApi(value = "publication.restore", published = false, response = OperationReceipt.class)
    @Log(title = "发布台管理", businessType = BusinessType.UPDATE)
    public AjaxResult restore(@PathVariable Long id, @Valid @RequestBody Command command) { return AjaxResult.success(publications.state(id, command, "RESTORE")); }

    @PostMapping("/{id}/share/retry")
    @PreAuthorize("@ss.hasPermi('knowledge:publication:shareRetry')")
    @CoreApi(value = "publication.shareRetry", published = false, response = OperationReceipt.class)
    @Log(title = "发布台管理", businessType = BusinessType.UPDATE)
    public AjaxResult retry(@PathVariable Long id, @Valid @RequestBody Command command) {
        var operation = shares.retry(id, command);
        return AjaxResult.success(publications.receipt(publications.require(id), operation));
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermi('knowledge:publication:list')")
    @CoreApi(value = "publication.list", published = false, response = Summary.class, shape = CoreApi.Shape.PAGE,
            queryFields = {"keyword", "status", "outlineId", "kpId", "pageNum", "pageSize"})
    public com.ruoyi.common.core.page.TableDataInfo list(ListQuery query) {
        Page<Summary> page = publications.list(query);
        var result = new com.ruoyi.common.core.page.TableDataInfo(page.rows(), page.total());
        result.setCode(200); result.setMsg("查询成功"); return result;
    }
}
