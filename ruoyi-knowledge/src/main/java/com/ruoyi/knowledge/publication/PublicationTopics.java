package com.ruoyi.knowledge.publication;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Ordered search keywords; the publishing app still resolves and binds its own topics. */
final class PublicationTopics {
    private static final int MAX_KEYWORDS = 20;
    private static final int MAX_KEYWORD_LENGTH = 80;
    private PublicationTopics() { }

    static void validate(List<String> keywords) {
        if (keywords == null) { return; }
        if (keywords.size() > MAX_KEYWORDS) { invalid(); }
        Set<String> seen = new HashSet<>();
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank() || keyword.length() > MAX_KEYWORD_LENGTH
                    || !keyword.equals(keyword.strip()) || keyword.indexOf('#') >= 0 || keyword.indexOf('＃') >= 0
                    || keyword.codePoints().anyMatch(PublicationTopics::lineBreak) || !seen.add(keyword)) { invalid(); }
        }
    }

    static List<String> read(String raw, PublicationSupport support) {
        if (raw == null) { return List.of(); }
        try {
            String[] keywords = support.decode(raw, String[].class);
            if (keywords == null) { return List.of(); }
            List<String> result = java.util.Arrays.asList(keywords);
            validate(result);
            return List.copyOf(result);
        } catch (PublicationException e) {
            throw PublicationSupport.fail(503, "DEPENDENCY_UNAVAILABLE", "话题关键词暂不可核对");
        }
    }

    private static boolean lineBreak(int codePoint) {
        return codePoint == '\n' || codePoint == '\r' || codePoint == 0x85 || codePoint == 0x2028 || codePoint == 0x2029;
    }
    private static void invalid() {
        throw PublicationSupport.fail(400, "INVALID_REQUEST", "话题关键词需为不重复的纯关键词，每条不含#或换行，最多20条且每条最多80字");
    }
}
