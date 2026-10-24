package com.ruoyi.web.controller.knowledge;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.knowledge.domain.KbKp;
import com.ruoyi.knowledge.service.IKbKpService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import org.springframework.web.bind.annotation.RestController;

/** 考点管理的管理接口。正文导入和文件流量使用独立接口。 */
@RestController
@RequestMapping("/knowledge/kp")
public class KbKpController extends BaseController {
    private final IKbKpService service;

    public KbKpController(IKbKpService service) {
        this.service = service;
    }

    @PreAuthorize("@ss.hasPermi('knowledge:kp:list')")
    @GetMapping("/list")
    public TableDataInfo list(KbKp query) {
        startPage();
        return getDataTable(service.list(query));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:kp:query')")
    @GetMapping("/names")
    public AjaxResult names(@RequestParam("ids") List<Long> ids) {
        return success(service.names(ids));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:kp:query')")
    @GetMapping("/{id}")
    public AjaxResult get(@PathVariable Long id) {
        return success(service.get(id));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:kp:add')")
    @Log(title = "考点管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult create(@RequestBody KbKp data) {
        data.setCreateBy(getUsername());
        return success(service.create(data));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:kp:edit')")
    @Log(title = "考点管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult update(@RequestBody KbKp data) {
        data.setUpdateBy(getUsername());
        return success(service.update(data));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:kp:remove')")
    @Log(title = "考点管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult delete(@PathVariable Long[] ids) {
        service.delete(ids);
        return success();
    }
}
