package com.ruoyi.web.controller.knowledge;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.knowledge.domain.KbOutline;
import com.ruoyi.knowledge.domain.KbOutlineNode;
import com.ruoyi.knowledge.service.IKbOutlineService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 大纲管理的管理接口。正文导入和文件流量使用独立接口。 */
@RestController
@RequestMapping("/knowledge/outline")
public class KbOutlineController extends BaseController {
    private final IKbOutlineService service;

    public KbOutlineController(IKbOutlineService service) {
        this.service = service;
    }

    @PreAuthorize("@ss.hasAnyPermi('knowledge:outline:list,knowledge:outline:query')")
    @GetMapping("/options")
    @com.ruoyi.web.core.docs.CoreApi(value = "reference.outlineOptions", response = com.ruoyi.knowledge.domain.dto.KbOutlineOptions.class)
    public AjaxResult options() {
        return success(service.options());
    }

    @PreAuthorize("@ss.hasPermi('knowledge:outline:list')")
    @GetMapping("/list")
    @com.ruoyi.web.core.docs.CoreApi(value = "reference.outlineList", response = com.ruoyi.knowledge.domain.KbOutline.class, shape = com.ruoyi.web.core.docs.CoreApi.Shape.PAGE, queryFields = {"title", "catalogKind", "subject", "textbookVersion", "grade", "term", "status"})
    public TableDataInfo list(KbOutline query) {
        startPage();
        return getDataTable(service.list(query));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:outline:query')")
    @GetMapping("/{id}")
    @com.ruoyi.web.core.docs.CoreApi(value = "reference.outlineGet", response = com.ruoyi.knowledge.domain.KbOutline.class)
    public AjaxResult get(@PathVariable Long id) {
        return success(service.get(id));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:outline:add')")
    @Log(title = "大纲管理", businessType = BusinessType.INSERT)
    @PostMapping
    @com.ruoyi.web.core.docs.CoreApi(value = "reference.outlineCreate", response = com.ruoyi.knowledge.domain.KbOutline.class, published = false)
    public AjaxResult create(@RequestBody KbOutline data) {
        data.setCreateBy(getUsername());
        return success(service.create(data));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:outline:edit')")
    @Log(title = "大纲管理", businessType = BusinessType.UPDATE)
    @PutMapping
    @com.ruoyi.web.core.docs.CoreApi(value = "reference.outlineUpdate", response = com.ruoyi.knowledge.domain.KbOutline.class, published = false)
    public AjaxResult update(@RequestBody KbOutline data) {
        data.setUpdateBy(getUsername());
        return success(service.update(data));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:outline:remove')")
    @Log(title = "大纲管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    @com.ruoyi.web.core.docs.CoreApi(value = "reference.outlineDelete", response = Void.class, published = false)
    public AjaxResult delete(@PathVariable Long[] ids) {
        service.delete(ids);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('knowledge:outline:edit')")
    @Log(title = "大纲章节", businessType = BusinessType.INSERT)
    @PostMapping("/node")
    @com.ruoyi.web.core.docs.CoreApi(value = "reference.outlineNodeCreate", response = com.ruoyi.knowledge.domain.KbOutlineNode.class, published = false)
    public AjaxResult addNode(@RequestBody KbOutlineNode data) {
        data.setCreateBy(getUsername());
        return success(service.createNode(data));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:outline:edit')")
    @Log(title = "大纲章节", businessType = BusinessType.UPDATE)
    @PutMapping("/node")
    @com.ruoyi.web.core.docs.CoreApi(value = "reference.outlineNodeUpdate", response = com.ruoyi.knowledge.domain.KbOutlineNode.class, published = false)
    public AjaxResult editNode(@RequestBody KbOutlineNode data) {
        data.setUpdateBy(getUsername());
        return success(service.updateNode(data));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:outline:edit')")
    @Log(title = "大纲章节", businessType = BusinessType.DELETE)
    @DeleteMapping("/node/{id}")
    @com.ruoyi.web.core.docs.CoreApi(value = "reference.outlineNodeDelete", response = Void.class, published = false)
    public AjaxResult deleteNode(@PathVariable Long id) {
        service.deleteNode(id);
        return success();
    }
}
