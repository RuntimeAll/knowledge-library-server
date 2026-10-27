package com.ruoyi.web.controller.knowledge;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.knowledge.layout.*;
import com.ruoyi.knowledge.storage.IKbAssetService;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;

@RestController
@RequestMapping("/knowledge/render")
public class KbRenderController extends BaseController {
    private final IKbRenderService service;
    private final IKbAssetService assets;

    public KbRenderController(IKbRenderService service, IKbAssetService assets) {
        this.service = service;
        this.assets = assets;
    }

    @PostMapping("/validate")
    @PreAuthorize("@ss.hasPermi('knowledge:render:add')")
    @com.ruoyi.web.core.docs.CoreApi(value = "rendering.validate", response = com.ruoyi.knowledge.layout.KbRenderValidation.class, published = false)
    public AjaxResult validate(@RequestBody KbRenderRequest request) {
        return success(service.validate(request));
    }

    @PostMapping("/jobs")
    @PreAuthorize("@ss.hasPermi('knowledge:render:add')")
    @Log(title = "模板生成", businessType = BusinessType.INSERT)
    @com.ruoyi.web.core.docs.CoreApi(value = "rendering.submit", response = com.ruoyi.knowledge.layout.KbRenderJob.class, published = false)
    public AjaxResult submit(@RequestBody KbRenderRequest request) {
        return success(service.submit(request));
    }

    @GetMapping("/jobs")
    @PreAuthorize("@ss.hasPermi('knowledge:render:query')")
    @com.ruoyi.web.core.docs.CoreApi(value = "rendering.list", response = com.ruoyi.knowledge.layout.KbRenderJob.class, published = false, shape = com.ruoyi.web.core.docs.CoreApi.Shape.PAGE, queryFields = {"status", "materialId", "idempotencyKey"})
    public TableDataInfo list(KbRenderJob query) {
        startPage();
        return getDataTable(service.list(query));
    }

    @GetMapping("/jobs/{id}")
    @PreAuthorize("@ss.hasPermi('knowledge:render:query')")
    @com.ruoyi.web.core.docs.CoreApi(value = "rendering.get", response = com.ruoyi.knowledge.layout.KbRenderJob.class, published = false)
    public AjaxResult get(@PathVariable Long id) {
        return success(service.get(id));
    }

    @PostMapping("/jobs/{id}/retry")
    @PreAuthorize("@ss.hasPermi('knowledge:render:retry')")
    @Log(title = "生成重试", businessType = BusinessType.UPDATE)
    @com.ruoyi.web.core.docs.CoreApi(value = "rendering.retry", response = com.ruoyi.knowledge.layout.KbRenderJob.class, published = false)
    public AjaxResult retry(@PathVariable Long id) {
        return success(service.retry(id));
    }

    @DeleteMapping("/jobs/{id}")
    @PreAuthorize("@ss.hasPermi('knowledge:experience:remove')")
    @Log(title = "生成任务", businessType = BusinessType.DELETE)
    @com.ruoyi.web.core.docs.CoreApi(value = "rendering.delete", response = Void.class, published = false)
    public AjaxResult delete(@PathVariable Long id) {
        service.delete(id);
        return success();
    }

    @GetMapping("/jobs/{id}/outputs/{outputKey}")
    @PreAuthorize("@ss.hasPermi('knowledge:render:download')")
    @com.ruoyi.web.core.docs.CoreApi(value = "rendering.download", response = byte[].class, published = false, shape = com.ruoyi.web.core.docs.CoreApi.Shape.BINARY, mediaType = "application/octet-stream")
    public void download(
            @PathVariable Long id,
            @PathVariable String outputKey,
            @RequestParam(defaultValue = "pdf") String kind,
            HttpServletResponse response)
            throws Exception {
        Long assetId = service.downloadAsset(id, outputKey, kind);
        var asset = assets.requireReady(assetId);
        response.setContentType("application/octet-stream");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", "sandbox; default-src 'none'");
        FileUtils.setAttachmentResponseHeader(response, asset.getOriginalName());
        response.setContentLengthLong(asset.getFileSize());
        try (InputStream stream = assets.openContent(assetId)) {
            stream.transferTo(response.getOutputStream());
        }
    }
}
