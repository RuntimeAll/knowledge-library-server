package com.ruoyi.web.controller.knowledge;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.dto.KbQuestionQuery;
import com.ruoyi.knowledge.service.IKbQuestionService;
import com.ruoyi.knowledge.service.IKbSourceService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 题库的管理接口。正文导入和文件流量使用独立接口。 */
@RestController
@RequestMapping("/knowledge/question")
public class KbQuestionController extends BaseController {
    private final IKbQuestionService service;
    private final IKbSourceService sourceService;

    public KbQuestionController(IKbQuestionService service, IKbSourceService sourceService) {
        this.service = service;
        this.sourceService = sourceService;
    }

    @PreAuthorize("@ss.hasPermi('knowledge:question:list')")
    @GetMapping("/list")
    public TableDataInfo list(KbQuestionQuery query) {
        service.prepareQuery(query);
        startPage();
        return getDataTable(service.list(query));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:question:query')")
    @GetMapping("/{id}")
    public AjaxResult get(@PathVariable Long id) {
        return success(service.get(id));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:question:edit')")
    @Log(title = "题库", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult update(@RequestBody KbQuestion data) {
        data.setUpdateBy(getUsername());
        return success(service.updateMetadata(data));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:question:remove')")
    @Log(title = "题库", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult delete(@PathVariable Long[] ids) {
        service.delete(ids);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('knowledge:question:query')")
    @GetMapping("/{id}/occurrences")
    public AjaxResult occurrences(@PathVariable Long id) {
        return success(sourceService.occurrences(id, null));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:question:query')")
    @GetMapping("/{id}/lineage")
    public AjaxResult lineage(@PathVariable Long id) {
        return success(service.lineage(id));
    }
}
