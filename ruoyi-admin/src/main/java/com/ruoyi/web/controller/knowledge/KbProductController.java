package com.ruoyi.web.controller.knowledge;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.knowledge.domain.KbProduct;
import com.ruoyi.knowledge.service.IKbProductService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 成品库的管理接口。正文导入和文件流量使用独立接口。 */
@RestController
@RequestMapping("/knowledge/product")
public class KbProductController extends BaseController {
    private final IKbProductService service;

    public KbProductController(IKbProductService service) {
        this.service = service;
    }

    @PreAuthorize("@ss.hasPermi('knowledge:product:list')")
    @GetMapping("/list")
    public TableDataInfo list(
            com.ruoyi.knowledge.catalog.KbCatalogQuery query,
            jakarta.servlet.http.HttpServletRequest request) {
        query.rejectUnknown(request.getParameterMap().keySet());
        service.prepareQuery(query);
        startPage();
        return getDataTable(service.list(query));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:product:query')")
    @GetMapping("/{id}")
    public AjaxResult get(@PathVariable Long id) {
        return success(service.get(id));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:product:add')")
    @Log(title = "成品库", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult create(@RequestBody KbProduct data) {
        data.setCreateBy(getUsername());
        return success(service.create(data));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:product:edit')")
    @Log(title = "成品库", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult update(@RequestBody KbProduct data) {
        data.setUpdateBy(getUsername());
        return success(service.update(data));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:product:remove')")
    @Log(title = "成品库", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult delete(@PathVariable Long[] ids) {
        service.delete(ids);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('knowledge:product:query')")
    @GetMapping("/{id}/versions")
    public AjaxResult versions(@PathVariable Long id) {
        return success(service.versions(id));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:product:edit')")
    @Log(title = "资料描述", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/catalog")
    public AjaxResult catalog(
            @PathVariable Long id,
            @RequestBody com.ruoyi.knowledge.catalog.KbCatalogCommand command) {
        return success(service.catalog(id, command, getUsername()));
    }
}
