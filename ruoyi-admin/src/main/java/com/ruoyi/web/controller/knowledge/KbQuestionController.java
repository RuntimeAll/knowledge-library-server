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
    @com.ruoyi.web.core.docs.CoreApi(value = "question.list", response = com.ruoyi.knowledge.domain.KbQuestion.class, shape = com.ruoyi.web.core.docs.CoreApi.Shape.PAGE, queryFields = {"purpose", "ids", "pageNum", "pageSize", "title", "query", "matchMode", "kpId", "expand", "hasImage", "status", "subject", "contentKind", "layoutMode", "unitKind", "paginationMode", "outlineId", "outlineNodeId", "questionType", "difficulty", "creationKind", "sourceId", "verifiedOnly", "wrongOnly", "wrongSourceId", "examSourceId", "examPurpose", "examYear", "examRegionCode", "examRegionDescendants"})
    public TableDataInfo list(KbQuestionQuery query) {
        query.validatePage();
        service.prepareQuery(query);
        com.github.pagehelper.PageHelper.startPage(query.getPageNum(), query.getPageSize()).setReasonable(false);
        try { return getDataTable(service.list(query)); } finally { com.github.pagehelper.PageHelper.clearPage(); }
    }

    @PreAuthorize("@ss.hasPermi('knowledge:question:query')")
    @GetMapping("/{id}")
    @com.ruoyi.web.core.docs.CoreApi(value = "question.get", response = com.ruoyi.knowledge.domain.KbQuestion.class)
    public AjaxResult get(@PathVariable Long id) {
        return success(service.get(id));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:question:edit')")
    @Log(title = "题库", businessType = BusinessType.UPDATE)
    @PutMapping
    @com.ruoyi.web.core.docs.CoreApi(value = "question.updateMetadata", response = com.ruoyi.knowledge.domain.KbQuestion.class, published = false)
    public AjaxResult update(@RequestBody KbQuestion data) {
        data.setUpdateBy(getUsername());
        return success(service.updateMetadata(data));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:question:remove')")
    @Log(title = "题库", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    @com.ruoyi.web.core.docs.CoreApi(value = "question.deleteLegacy", response = Void.class, published = false)
    public AjaxResult delete(@PathVariable Long[] ids) {
        service.delete(ids);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('knowledge:question:query')")
    @GetMapping("/{id}/occurrences")
    @com.ruoyi.web.core.docs.CoreApi(value = "question.occurrences", response = com.ruoyi.knowledge.domain.KbOccurrence.class, published = false, shape = com.ruoyi.web.core.docs.CoreApi.Shape.ARRAY)
    public AjaxResult occurrences(@PathVariable Long id) {
        return success(sourceService.occurrences(id, null));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:question:query')")
    @GetMapping("/{id}/lineage")
    @com.ruoyi.web.core.docs.CoreApi(value = "question.lineage", response = com.ruoyi.knowledge.dto.KbLineage.class, published = false)
    public AjaxResult lineage(@PathVariable Long id) {
        return success(service.lineage(id));
    }
}
