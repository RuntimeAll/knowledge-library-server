package com.ruoyi.knowledge.service.impl;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/** Determines whether an imported question has visible educational content. */
public final class QuestionContentRules {
    private QuestionContentRules() {}

    public static boolean hasContent(String html) {
        if (html == null || html.isBlank()) {
            return false;
        }
        Document document = Jsoup.parseBodyFragment(html);
        document.select("script:not([type^=math/tex]),style").remove();
        String text =
                document.body()
                        .text()
                        .replaceAll("\\\\\\(\\s*\\\\\\)|\\\\\\[\\s*\\\\\\]|\\$\\$\\s*\\$\\$", "")
                        .replaceAll("[\\s\\p{Z}\\u200B\\uFEFF]", "");
        if (!text.isEmpty()) {
            return true;
        }
        if (document.select("img[src]").stream()
                .anyMatch(element -> !element.attr("src").isBlank())) {
            return true;
        }
        if (!document.select(
                        "svg path[d],svg rect[width][height],svg circle[r],svg ellipse[rx][ry],svg"
                                + " line[x1][x2],svg polyline[points],svg polygon[points]")
                .isEmpty()) {
            return true;
        }
        return document.select("script[type^=math/tex]").stream()
                .anyMatch(element -> !element.data().isBlank());
    }
}
