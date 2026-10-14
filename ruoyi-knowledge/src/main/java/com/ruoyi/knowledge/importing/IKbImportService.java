package com.ruoyi.knowledge.importing;

import java.util.List;
import com.ruoyi.knowledge.importing.domain.KbImportBatch;
import org.springframework.web.multipart.MultipartFile;

public interface IKbImportService {
    record Schema(String schemaVersion, String instanceId) { }
    Schema schema();
    KbImportBatch get(Long batchId);
    KbImportBatch resume(Long batchId, String testRunId);
    KbImportBatch importHtml(MultipartFile file, List<MultipartFile> resources, String idempotencyKey,
                             Long sourceId, String testRunId);
}
