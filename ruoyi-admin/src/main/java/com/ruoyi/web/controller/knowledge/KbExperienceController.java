package com.ruoyi.web.controller.knowledge;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.knowledge.layout.*;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/knowledge")
public class KbExperienceController extends BaseController {
    private final IKbExperienceService service;

    public KbExperienceController(IKbExperienceService service) {
        this.service = service;
    }

    @GetMapping("/experiences")
    @PreAuthorize("@ss.hasPermi('knowledge:experience:list')")
    public TableDataInfo list(KbExperience query) {
        startPage();
        return getDataTable(service.list(query));
    }

    @GetMapping("/experiences/{id}")
    @PreAuthorize("@ss.hasPermi('knowledge:experience:query')")
    public AjaxResult get(@PathVariable Long id) {
        return success(service.get(id));
    }

    @PostMapping("/experiences")
    @PreAuthorize("@ss.hasPermi('knowledge:experience:add')")
    @Log(title = "排版经验", businessType = BusinessType.INSERT)
    public AjaxResult create(@RequestBody KbExperience data) {
        return success(service.create(data));
    }

    @PutMapping("/experiences")
    @PreAuthorize("@ss.hasPermi('knowledge:experience:edit')")
    @Log(title = "排版经验", businessType = BusinessType.UPDATE)
    public AjaxResult update(@RequestBody KbExperience data) {
        return success(service.update(data));
    }

    @DeleteMapping("/experiences/{ids}")
    @PreAuthorize("@ss.hasPermi('knowledge:experience:remove')")
    @Log(title = "排版经验", businessType = BusinessType.DELETE)
    public AjaxResult delete(@PathVariable Long[] ids) {
        service.delete(ids);
        return success();
    }

    @GetMapping("/experiences/{id}/versions")
    @PreAuthorize("@ss.hasPermi('knowledge:experience:query')")
    public TableDataInfo versions(
            @PathVariable Long id, @RequestParam(required = false) String status) {
        startPage();
        return getDataTable(service.versions(id, status));
    }

    @PostMapping("/experiences/{id}/versions")
    @PreAuthorize("@ss.hasPermi('knowledge:experience:edit')")
    @Log(title = "模板版本", businessType = BusinessType.INSERT)
    public AjaxResult createVersion(@PathVariable Long id, @RequestBody KbExperienceVersion data) {
        return success(service.createVersion(id, data));
    }

    @GetMapping("/experience-versions/options")
    @PreAuthorize("@ss.hasAnyPermi('knowledge:experience:query,knowledge:render:add')")
    public AjaxResult options() {
        return success(service.options());
    }

    @GetMapping("/experience-versions/{id}")
    @PreAuthorize("@ss.hasAnyPermi('knowledge:experience:query,knowledge:render:add')")
    public AjaxResult version(@PathVariable Long id) {
        var permissions = SecurityUtils.getLoginUser().getPermissions();
        boolean manage =
                SecurityUtils.isAdmin(getUserId())
                        || permissions.contains("*:*:*")
                        || permissions.contains("knowledge:experience:query");
        return success(manage ? service.version(id) : service.usableVersion(id));
    }

    @PutMapping("/experience-versions/{id}")
    @PreAuthorize("@ss.hasPermi('knowledge:experience:edit')")
    @Log(title = "模板版本", businessType = BusinessType.UPDATE)
    public AjaxResult updateVersion(@PathVariable Long id, @RequestBody KbExperienceVersion data) {
        return success(service.updateVersion(id, data));
    }

    public record PublishRequest(Long rowVersion) {}

    @PostMapping("/experience-versions/{id}/publish")
    @PreAuthorize("@ss.hasPermi('knowledge:experience:publish')")
    @Log(title = "模板发布", businessType = BusinessType.UPDATE)
    public AjaxResult publish(@PathVariable Long id, @RequestBody PublishRequest request) {
        return success(service.publish(id, request.rowVersion(), getUsername()));
    }

    @DeleteMapping("/experience-versions/{id}")
    @PreAuthorize("@ss.hasPermi('knowledge:experience:remove')")
    @Log(title = "模板版本", businessType = BusinessType.DELETE)
    public AjaxResult deleteVersion(@PathVariable Long id) {
        service.deleteVersion(id);
        return success();
    }
}
