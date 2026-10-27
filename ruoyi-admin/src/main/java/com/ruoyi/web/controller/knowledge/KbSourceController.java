package com.ruoyi.web.controller.knowledge;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.knowledge.domain.KbOccurrence;
import com.ruoyi.knowledge.domain.KbSource;
import com.ruoyi.knowledge.service.IKbSourceService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 来源资料的管理接口。正文导入和文件流量使用独立接口。 */
@RestController
@RequestMapping("/knowledge/source")
public class KbSourceController extends BaseController {
    private final IKbSourceService service;

    public KbSourceController(IKbSourceService service) {
        this.service = service;
    }

    @PreAuthorize("@ss.hasPermi('knowledge:source:list')")
    @GetMapping("/list")
    @com.ruoyi.web.core.docs.CoreApi(value = "source.list", response = com.ruoyi.knowledge.domain.KbSource.class, shape = com.ruoyi.web.core.docs.CoreApi.Shape.PAGE, queryFields = {"title", "subject", "resourceType", "catalogStatus", "sourceKind", "extractionStatus", "authenticityStatus", "chapterMatch", "textbookVersion", "grade", "term", "yearField", "regionRole", "regionCode", "coverageMode", "examPurpose", "organizerLevel", "paperNature", "audienceRole", "provenanceStatus", "historyMode", "outlineId", "outlineNodeId", "kpId", "rootProductId", "materialId", "yearFrom", "yearTo", "examYear", "academicYearStart", "regionDescendants", "includeNationwide"})
    public TableDataInfo list(
            com.ruoyi.knowledge.catalog.KbCatalogQuery query,
            jakarta.servlet.http.HttpServletRequest request) {
        query.rejectUnknown(request.getParameterMap().keySet());
        service.prepareQuery(query);
        startPage();
        return getDataTable(service.list(query));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:source:query')")
    @GetMapping("/{id}")
    @com.ruoyi.web.core.docs.CoreApi(value = "source.get", response = com.ruoyi.knowledge.domain.KbSource.class)
    public AjaxResult get(@PathVariable Long id) {
        return success(service.get(id));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:source:add')")
    @Log(title = "来源资料", businessType = BusinessType.INSERT)
    @PostMapping
    @com.ruoyi.web.core.docs.CoreApi(value = "source.create", response = com.ruoyi.knowledge.domain.KbSource.class, published = false)
    public AjaxResult create(@RequestBody KbSource data) {
        data.setCreateBy(getUsername());
        return success(service.create(data));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:source:edit')")
    @Log(title = "来源资料", businessType = BusinessType.UPDATE)
    @PutMapping
    @com.ruoyi.web.core.docs.CoreApi(value = "source.update", response = com.ruoyi.knowledge.domain.KbSource.class, published = false)
    public AjaxResult update(@RequestBody KbSource data) {
        data.setUpdateBy(getUsername());
        return success(service.update(data));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:source:remove')")
    @Log(title = "来源资料", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    @com.ruoyi.web.core.docs.CoreApi(value = "source.delete", response = Void.class, published = false)
    public AjaxResult delete(@PathVariable Long[] ids) {
        service.delete(ids);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('knowledge:source:query')")
    @GetMapping("/{id}/occurrences")
    @com.ruoyi.web.core.docs.CoreApi(value = "source.occurrences", response = com.ruoyi.knowledge.domain.KbOccurrence.class, published = false, shape = com.ruoyi.web.core.docs.CoreApi.Shape.ARRAY)
    public AjaxResult occurrences(@PathVariable Long id) {
        return success(service.occurrences(null, id));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:source:edit')")
    @Log(title = "题目出处", businessType = BusinessType.INSERT)
    @PostMapping("/occurrence")
    @com.ruoyi.web.core.docs.CoreApi(value = "source.createOccurrence", response = com.ruoyi.knowledge.domain.KbOccurrence.class, published = false)
    public AjaxResult addOccurrence(@RequestBody KbOccurrence data) {
        data.setCreateBy(getUsername());
        return success(service.createOccurrence(data));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:source:edit')")
    @Log(title = "题目出处", businessType = BusinessType.UPDATE)
    @PutMapping("/occurrence")
    @com.ruoyi.web.core.docs.CoreApi(value = "source.updateOccurrence", response = com.ruoyi.knowledge.domain.KbOccurrence.class, published = false)
    public AjaxResult editOccurrence(@RequestBody KbOccurrence data) {
        data.setUpdateBy(getUsername());
        return success(service.updateOccurrence(data));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:source:edit')")
    @Log(title = "题目出处", businessType = BusinessType.DELETE)
    @DeleteMapping("/occurrence/{id}")
    @com.ruoyi.web.core.docs.CoreApi(value = "source.deleteOccurrence", response = Void.class, published = false)
    public AjaxResult deleteOccurrence(@PathVariable Long id) {
        service.deleteOccurrence(id);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('knowledge:source:edit')")
    @Log(title = "资料描述", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/catalog")
    @com.ruoyi.web.core.docs.CoreApi(value = "source.updateCatalog", response = com.ruoyi.knowledge.domain.KbSource.class, published = false)
    public AjaxResult catalog(
            @PathVariable Long id,
            @RequestBody com.ruoyi.knowledge.catalog.KbCatalogCommand command) {
        return success(service.catalog(id, command, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:source:verify')")
    @Log(title = "考试来源核实", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/exam-verification")
    @com.ruoyi.web.core.docs.CoreApi(value = "source.verifyExam", response = com.ruoyi.knowledge.domain.KbSource.class, published = false)
    public AjaxResult verifyExam(
            @PathVariable Long id,
            @RequestBody com.ruoyi.knowledge.catalog.KbExamVerificationCommand command) {
        return success(service.verifyExam(id, command, getUsername()));
    }
}
