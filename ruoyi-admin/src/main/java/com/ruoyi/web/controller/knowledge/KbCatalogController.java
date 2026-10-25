package com.ruoyi.web.controller.knowledge;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.service.*;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/knowledge")
public class KbCatalogController extends BaseController {
    private final IKbCatalogService catalog;
    private final IKbSourceService sources;

    public KbCatalogController(IKbCatalogService catalog, IKbSourceService sources) {
        this.catalog = catalog;
        this.sources = sources;
    }

    @GetMapping("/region/list")
    @PreAuthorize(
            "@ss.hasPermi('knowledge:source:query') or @ss.hasPermi('knowledge:product:query')")
    @com.ruoyi.web.core.docs.CoreApi(value = "reference.regionList", response = com.ruoyi.knowledge.catalog.KbRegion.class, shape = com.ruoyi.web.core.docs.CoreApi.Shape.PAGE)
    public TableDataInfo regions(
            @RequestParam(required = false) String parentCode,
            @RequestParam(required = false) String keyword) {
        startPage();
        return getDataTable(catalog.regions(parentCode, keyword));
    }

    @GetMapping("/region/{code}")
    @PreAuthorize(
            "@ss.hasPermi('knowledge:source:query') or @ss.hasPermi('knowledge:product:query')")
    @com.ruoyi.web.core.docs.CoreApi(value = "reference.regionGet", response = com.ruoyi.knowledge.catalog.KbRegion.class)
    public AjaxResult region(@PathVariable String code) {
        return success(catalog.region(code));
    }

    @GetMapping("/occurrence/list")
    @PreAuthorize("@ss.hasPermi('knowledge:source:query')")
    public TableDataInfo occurrences(KbOccurrenceQuery query) {
        startPage();
        return getDataTable(sources.occurrenceList(query));
    }

    @PutMapping("/occurrence/{id}/collections")
    @PreAuthorize("@ss.hasPermi('knowledge:source:edit')")
    @Log(title = "出处收录用途", businessType = BusinessType.UPDATE)
    public AjaxResult collections(
            @PathVariable Long id, @RequestBody KbCollectionsCommand command) {
        return success(sources.collections(id, command, getUsername()));
    }
}
