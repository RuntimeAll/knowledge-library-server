package com.ruoyi.knowledge.importing;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.html.KbHtmlCodec;
import com.ruoyi.knowledge.importing.dto.KbRecordsRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class KbRecordHtmlConverterTest {
    final JsonMapper json = JsonMapper.builder().build();
    final KbRecordHtmlConverter converter = new KbRecordHtmlConverter();
    final KbHtmlCodec codec = new KbHtmlCodec();
    KbRecordsRequest request(String fields) {
        return json.readValue("{\"schemaVersion\":\"kb-question-records/v1\",\"idempotencyKey\":\"test\"," + fields + "}", KbRecordsRequest.class);
    }
    @Test void groupIsOneArticleAndPreservesItemKeysAndAnswers() {
        var document = request("""
            "records":[{"clientKey":"g","stem":"口算","items":[
              {"key":"1","stem":"1+2=","answer":"3"},{"key":"2","stem":"2+3=","answer":"5"}]}]
            """);
        var articles = codec.readArticles(converter.convert(document));
        assertEquals(1, articles.size());
        var parsed = codec.readArticle(articles.get(0));
        assertEquals("GROUP", parsed.metadata().unitKind());
        assertEquals(List.of("1", "2"), parsed.stem().select("[data-item-key]").eachAttr("data-item-key"));
        assertEquals(List.of("1", "2"), parsed.answer().select("[data-item-key]").eachAttr("data-item-key"));
    }
    @Test void optionImagesUseSameAssetResolverInputAndMetadataCannotBreakOut() {
        var document = request("""
            "resources":[{"key":"image.png","assetId":"12"}],
            "records":[{"clientKey":"c","metadata":{"questionType":"CHOICE","title":"</script><article>"},
              "stem":"<b>literal</b>","options":[{"key":"A","html":"<img src='image.png'>"},{"key":"B","text":"没有"}]}]
            """);
        var articles = codec.readArticles(converter.convert(document));
        assertEquals(1, articles.size());
        var parsed = codec.readArticle(articles.get(0));
        assertEquals("</script><article>", parsed.metadata().title());
        assertEquals("12", parsed.stem().selectFirst("img").attr("data-asset-id"));
        assertTrue(parsed.stem().html().contains("&lt;b&gt;literal&lt;/b&gt;"));
    }
    @Test void omittedAnswerRemainsAbsentAndExplicitEmptyAnswerCanClear() {
        var document = request("""
            "origin":"instance","records":[{"clientKey":"u","intent":"update","questionId":"1","rowVersion":"2","stem":"s"},
            {"clientKey":"v","stem":"s","answer":""}]
            """);
        var articles = codec.readArticles(converter.convert(document));
        assertNull(codec.readArticle(articles.get(0)).answer());
        assertNotNull(codec.readArticle(articles.get(1)).answer());
    }
    @Test void invalidShapesAndConflictingResourceReferencesRejectEarly() {
        assertItemFailure(request("\"records\":[{\"clientKey\":\"a\",\"stem\":\"s\",\"stemHtml\":\"s\"}]"));
        assertItemFailure(request("\"records\":[{\"clientKey\":\"a\",\"stem\":\"s\",\"intent\":\"update\"}]"));
        assertItemFailure(request("""
            "resources":[{"key":"a.png","assetId":"1"}],"records":[{"clientKey":"a","stemHtml":"<img src='a.png' data-asset-id='2'>"}]
            """));
    }
    void assertItemFailure(KbRecordsRequest request) {
        var articles = codec.readArticles(converter.convert(request));
        assertEquals(1, articles.size());
        assertThrows(ServiceException.class, () -> codec.readArticle(articles.get(0)));
    }
    @Test void malformedSiblingRetainsValidRecordAndFailedItemIdentity() {
        var articles = codec.readArticles(converter.convert(request("""
            "records":[{"clientKey":"valid","stem":"可入库"},{"clientKey":"invalid","answer":"缺少题干"}]
            """)));
        assertEquals(2, articles.size());
        assertEquals("valid", codec.readArticle(articles.get(0)).clientKey());
        assertEquals("invalid", articles.get(1).attr("data-client-key"));
        assertThrows(ServiceException.class, () -> codec.readArticle(articles.get(1)));
    }
    @Test void unknownInputFieldsAreRejectedInsteadOfSilentlyLost() {
        assertThrows(RuntimeException.class, () -> request("\"records\":[{\"clientKey\":\"a\",\"stem\":\"s\",\"hasImage\":true}]"));
        assertThrows(RuntimeException.class, () -> request("\"records\":[{\"clientKey\":\"a\",\"stem\":\"s\",\"metadata\":{\"hasImage\":true}}]"));
    }
    @Test void plainTextLineBreaksAndEscapingSurviveConversion() {
        var document = request("\"records\":[{\"clientKey\":\"lines\",\"stem\":\"first\\nsecond\\r\\nthird <b>\\n\"}]");
        var parsed = codec.readArticle(codec.readArticles(converter.convert(document)).get(0));
        assertEquals(3, parsed.stem().select("br").size());
        assertTrue(parsed.stem().html().contains("&lt;b&gt;"));
        assertTrue(parsed.stem().select("b").isEmpty());
    }
}
