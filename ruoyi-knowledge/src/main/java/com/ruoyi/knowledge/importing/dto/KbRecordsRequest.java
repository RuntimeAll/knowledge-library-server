package com.ruoyi.knowledge.importing.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.html.HtmlMetadata;
import java.util.List;

/** JSON input contract. Text fields are escaped; only explicit *Html fields contain markup. */
public record KbRecordsRequest(String schemaVersion, String idempotencyKey, Long sourceId, String origin,
                               List<Record> records, List<Resource> resources) {
    public static final String SCHEMA = "kb-question-records/v1";
    @JsonAnySetter public void unknown(String name, Object value) { reject(name); }
    public static void reject(String name) { throw new ServiceException("录入JSON包含未定义字段：" + name, 400); }

    public record Record(String clientKey, String intent, Long questionId, Long rowVersion, Long parentId,
                         HtmlMetadata metadata, String stem, String stemHtml, String answer, String answerHtml,
                         String analysis, String analysisHtml, Integer columns, List<Item> items, List<Option> options,
                         Long expectedParentRowVersion, String expectedParentStemHash, String granularityExceptionId, String expectedParentContentHash) {
        public Record(String clientKey, String intent, Long questionId, Long rowVersion, Long parentId,
                HtmlMetadata metadata, String stem, String stemHtml, String answer, String answerHtml,
                String analysis, String analysisHtml, Integer columns, List<Item> items, List<Option> options) {
            this(clientKey,intent,questionId,rowVersion,parentId,metadata,stem,stemHtml,answer,answerHtml,
                    analysis,analysisHtml,columns,items,options,null,null,null,null);
        }
        @JsonAnySetter public void unknown(String name, Object value) { reject(name); }
    }
    public record Item(String key, String stem, String stemHtml, String answer, String answerHtml,
                       String analysis, String analysisHtml) {
        @JsonAnySetter public void unknown(String name, Object value) { reject(name); }
    }
    public record Option(String key, String text, String html) {
        @JsonAnySetter public void unknown(String name, Object value) { reject(name); }
    }
    public record Resource(String key, Long assetId) {
        @JsonAnySetter public void unknown(String name, Object value) { reject(name); }
    }
}
