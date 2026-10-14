package com.ruoyi.web.controller.knowledge;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.knowledge.storage.IKbAssetService;
import com.ruoyi.knowledge.storage.domain.KbAsset;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@RestController
@RequestMapping("/knowledge/asset")
public class AssetController extends BaseController {
    private final IKbAssetService assets;

    public AssetController(IKbAssetService assets) {
        this.assets = assets;
    }

    @PostMapping("/upload")
    @PreAuthorize("@ss.hasPermi('knowledge:asset:upload')")
    @Log(title = "知识库文件上传", businessType = BusinessType.INSERT)
    public AjaxResult upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("purpose") String purpose,
            @RequestParam(value = "idempotencyKey", required = false) String idempotencyKey,
            @RequestHeader(value = "X-KB-Test-Run", required = false) String testRunId) {
        return success(assets.upload(file, purpose, testRunId, idempotencyKey));
    }

    @GetMapping("/{assetId}")
    @PreAuthorize("@ss.hasPermi('knowledge:asset:query')")
    public AjaxResult metadata(@PathVariable Long assetId) {
        return success(assets.getAsset(assetId));
    }

    @GetMapping("/{assetId}/preview")
    @PreAuthorize("@ss.hasPermi('knowledge:asset:query')")
    public AjaxResult preview(@PathVariable Long assetId) {
        return success(assets.preview(assetId));
    }

    @GetMapping("/{assetId}/content")
    @PreAuthorize("@ss.hasPermi('knowledge:asset:download')")
    public void download(@PathVariable Long assetId, HttpServletResponse response)
            throws Exception {
        KbAsset asset = assets.requireReady(assetId);
        // Do not render user-supplied originals in the application origin.
        response.setContentType("application/octet-stream");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", "sandbox; default-src 'none'");
        FileUtils.setAttachmentResponseHeader(response, asset.getOriginalName());
        response.setContentLengthLong(asset.getFileSize());
        try (InputStream input = assets.openContent(assetId)) {
            input.transferTo(response.getOutputStream());
        }
    }

    @DeleteMapping("/{assetId}")
    @PreAuthorize("@ss.hasPermi('knowledge:asset:remove')")
    @Log(title = "知识库文件删除", businessType = BusinessType.DELETE)
    public AjaxResult delete(@PathVariable Long assetId) {
        assets.delete(assetId);
        return success();
    }
}
