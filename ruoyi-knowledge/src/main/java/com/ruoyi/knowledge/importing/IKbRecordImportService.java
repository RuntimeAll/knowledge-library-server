package com.ruoyi.knowledge.importing;

import com.ruoyi.knowledge.importing.dto.KbRecordsRequest;
import com.ruoyi.knowledge.importing.domain.KbImportBatch;

public interface IKbRecordImportService {
    KbImportBatch importRecords(KbRecordsRequest request, String testRunId);
}
