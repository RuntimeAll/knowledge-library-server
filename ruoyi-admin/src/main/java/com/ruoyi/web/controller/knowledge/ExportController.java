package com.ruoyi.web.controller.knowledge;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.knowledge.exporting.KbExportRequest;
import com.ruoyi.knowledge.exporting.IKbExportService;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/knowledge/export")
public class ExportController extends BaseController {
    private final IKbExportService exports;

    public ExportController(IKbExportService exports) {
        this.exports = exports;
    }

    @PostMapping("/questions")
    @PreAuthorize("@ss.hasPermi('knowledge:question:export')")
    @Log(title = "知识库离线HTML导出", businessType = BusinessType.EXPORT)
    @com.ruoyi.web.core.docs.CoreApi(value = "question.export", response = byte[].class, shape = com.ruoyi.web.core.docs.CoreApi.Shape.BINARY, mediaType = "application/zip")
    public void questions(@RequestBody KbExportRequest request, HttpServletResponse response)
            throws Exception {
        byte[] zip = exports.export(request);
        response.setContentType("application/zip");
        FileUtils.setAttachmentResponseHeader(response, "knowledge-questions.zip");
        response.setContentLength(zip.length);
        response.getOutputStream().write(zip);
    }
}
