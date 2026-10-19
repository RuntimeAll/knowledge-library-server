package com.ruoyi.knowledge.importing;

import com.ruoyi.knowledge.importing.dto.KbRecordsRequest;
import com.ruoyi.knowledge.importing.domain.KbImportBatch;
import java.io.*;
import java.nio.file.Files;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KbRecordImportServiceImpl implements IKbRecordImportService {
    private final KbRecordHtmlConverter converter;
    private final IKbImportService imports;
    public KbRecordImportServiceImpl(KbRecordHtmlConverter converter, IKbImportService imports) {
        this.converter = converter; this.imports = imports;
    }
    public KbImportBatch importRecords(KbRecordsRequest request, String testRunId) {
        return imports.importHtml(new HtmlFile(converter.convert(request)), List.of(),
                request.idempotencyKey(), request.sourceId(), testRunId);
    }
    private record HtmlFile(byte[] bytes) implements MultipartFile {
        public String getName() { return "file"; }
        public String getOriginalFilename() { return "records.html"; }
        public String getContentType() { return "text/html;charset=UTF-8"; }
        public boolean isEmpty() { return bytes.length == 0; }
        public long getSize() { return bytes.length; }
        public byte[] getBytes() { return bytes.clone(); }
        public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        public void transferTo(File target) throws IOException { Files.write(target.toPath(), bytes); }
    }
}
