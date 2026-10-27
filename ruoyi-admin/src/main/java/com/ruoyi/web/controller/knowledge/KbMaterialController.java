package com.ruoyi.web.controller.knowledge;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.knowledge.domain.KbMaterial;
import com.ruoyi.knowledge.dto.KbQuestionQuery;
import com.ruoyi.knowledge.service.IKbMaterialService;
import com.ruoyi.knowledge.service.IKbQuestionService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 专项资料的管理接口。正文导入和文件流量使用独立接口。 */
@RestController
@RequestMapping("/knowledge/material")
public class KbMaterialController extends BaseController {
    private final IKbMaterialService service;
    private final IKbQuestionService questionService;

    public KbMaterialController(IKbMaterialService service, IKbQuestionService questionService) {
        this.service = service;
        this.questionService = questionService;
    }

    @PreAuthorize("@ss.hasPermi('knowledge:material:list')")
    @GetMapping("/list")
    @com.ruoyi.web.core.docs.CoreApi(value = "reference.materialList", response = com.ruoyi.knowledge.domain.KbMaterial.class, shape = com.ruoyi.web.core.docs.CoreApi.Shape.PAGE, queryFields = {"title"})
    public TableDataInfo list(KbMaterial query) {
        startPage();
        return getDataTable(service.list(query));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:material:query')")
    @GetMapping("/{id}")
    @com.ruoyi.web.core.docs.CoreApi(value = "reference.materialGet", response = com.ruoyi.knowledge.domain.KbMaterial.class)
    public AjaxResult get(@PathVariable Long id) {
        return success(service.get(id));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:material:add')")
    @Log(title = "专项资料", businessType = BusinessType.INSERT)
    @PostMapping
    @com.ruoyi.web.core.docs.CoreApi(value = "reference.materialCreate", response = com.ruoyi.knowledge.domain.KbMaterial.class, published = false)
    public AjaxResult create(@RequestBody KbMaterial data) {
        data.setCreateBy(getUsername());
        return success(service.create(data));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:material:edit')")
    @Log(title = "专项资料", businessType = BusinessType.UPDATE)
    @PutMapping
    @com.ruoyi.web.core.docs.CoreApi(value = "reference.materialUpdate", response = com.ruoyi.knowledge.domain.KbMaterial.class, published = false)
    public AjaxResult update(@RequestBody KbMaterial data) {
        data.setUpdateBy(getUsername());
        return success(service.update(data));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:material:remove')")
    @Log(title = "专项资料", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    @com.ruoyi.web.core.docs.CoreApi(value = "reference.materialDelete", response = Void.class, published = false)
    public AjaxResult delete(@PathVariable Long[] ids) {
        service.delete(ids);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('knowledge:material:query')")
    @GetMapping("/{id}/candidates")
    @com.ruoyi.web.core.docs.CoreApi(value = "reference.materialCandidates", response = com.ruoyi.knowledge.domain.KbQuestion.class, published = false, shape = com.ruoyi.web.core.docs.CoreApi.Shape.PAGE, queryFields = {"pageNum", "pageSize", "purpose", "ids", "title", "query", "matchMode", "kpId", "expand", "hasImage", "status", "subject", "contentKind", "questionType", "difficulty", "creationKind", "sourceId", "verifiedOnly", "wrongOnly", "wrongSourceId", "examSourceId", "outlineId", "outlineNodeId"})
    public TableDataInfo candidates(
            @PathVariable Long id,
            @RequestParam(defaultValue = "REFERENCE") String scope,
            KbQuestionQuery query) {
        query.validatePage();
        service.prepareCandidates(id, scope, query);
        com.github.pagehelper.PageHelper.startPage(query.getPageNum(), query.getPageSize()).setReasonable(false);
        try { return getDataTable(questionService.list(query)); } finally { com.github.pagehelper.PageHelper.clearPage(); }
    }

    @PreAuthorize("@ss.hasPermi('knowledge:material:query')")
    @GetMapping("/{id}/comparison")
    @com.ruoyi.web.core.docs.CoreApi(value = "reference.materialComparison", response = com.ruoyi.knowledge.dto.KbComparison.class, published = false)
    public AjaxResult comparison(
            @PathVariable Long id,
            @RequestParam(defaultValue = "REFERENCE") String scope,
            @RequestParam(defaultValue = "true") boolean expand,
            @RequestParam(defaultValue = "1") int matchPageNum,
            @RequestParam(defaultValue = "20") int matchPageSize,
            @RequestParam(defaultValue = "1") int coveragePageNum,
            @RequestParam(defaultValue = "20") int coveragePageSize,
            @RequestParam(defaultValue = "1") int pointPageNum,
            @RequestParam(defaultValue = "20") int pointPageSize) {
        return success(
                service.comparison(
                        id,
                        scope,
                        expand,
                        matchPageNum,
                        matchPageSize,
                        coveragePageNum,
                        coveragePageSize,
                        pointPageNum,
                        pointPageSize));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:material:query')")
    @GetMapping("/{id}/validate")
    @com.ruoyi.web.core.docs.CoreApi(value = "reference.materialValidate", response = com.ruoyi.knowledge.domain.KbMaterial.class, published = false)
    public AjaxResult validate(@PathVariable Long id) {
        return success(service.get(id));
    }
}
