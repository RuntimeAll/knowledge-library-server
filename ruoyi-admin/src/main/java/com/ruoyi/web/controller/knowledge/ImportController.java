package com.ruoyi.web.controller.knowledge;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.knowledge.importing.IKbImportService;
import com.ruoyi.knowledge.importing.IKbRecordImportService;
import com.ruoyi.knowledge.importing.dto.KbRecordsRequest;
import com.ruoyi.common.exception.ServiceException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/knowledge/import")
public class ImportController extends BaseController {
    private final IKbImportService imports;
    private final IKbRecordImportService records;

    public ImportController(IKbImportService imports, IKbRecordImportService records) {
        this.imports = imports;
        this.records = records;
    }

    @PostMapping("/records")
    @PreAuthorize("@ss.hasPermi('knowledge:import:execute')")
    @Log(title = "知识库JSON导入", businessType = BusinessType.IMPORT, isSaveRequestData = false)
    public AjaxResult records(@RequestBody KbRecordsRequest request,
            @RequestHeader(value = "X-KB-Test-Run", required = false) String testRunId) {
        return success(records.importRecords(request, testRunId));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public AjaxResult invalidJson(HttpMessageNotReadableException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ServiceException service) return AjaxResult.error(400, service.getMessage());
        }
        return AjaxResult.error(400, "录入JSON格式或字段类型无效，请按统一JSON模板填写");
    }

    @GetMapping("/schema")
    @PreAuthorize("@ss.hasPermi('knowledge:import:query')")
    public AjaxResult schema() {
        return success(imports.schema());
    }

    @PostMapping("/html")
    @PreAuthorize("@ss.hasPermi('knowledge:import:execute')")
    @Log(title = "知识库HTML导入", businessType = BusinessType.IMPORT, isSaveRequestData = false)
    public AjaxResult upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "resources", required = false) List<MultipartFile> resources,
            @RequestParam("idempotencyKey") String idempotencyKey,
            @RequestParam(value = "sourceId", required = false) Long sourceId,
            @RequestHeader(value = "X-KB-Test-Run", required = false) String testRunId) {
        return success(imports.importHtml(file, resources, idempotencyKey, sourceId, testRunId));
    }

    @GetMapping("/{batchId}")
    @PreAuthorize("@ss.hasPermi('knowledge:import:query')")
    public AjaxResult result(@PathVariable Long batchId) {
        return success(imports.get(batchId));
    }

    @PostMapping("/{batchId}/resume")
    @PreAuthorize("@ss.hasPermi('knowledge:import:execute')")
    @Log(title = "知识库HTML恢复", businessType = BusinessType.IMPORT, isSaveRequestData = false)
    public AjaxResult resume(
            @PathVariable Long batchId,
            @RequestHeader(value = "X-KB-Test-Run", required = false) String testRunId) {
        return success(imports.resume(batchId, testRunId));
    }
}
