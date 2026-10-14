package com.ruoyi.knowledge.html;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import com.ruoyi.common.exception.ServiceException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

/** Deliberately supports static geometry, not active SVG documents. */
public final class KbSvgSafety {
    public static final Set<String> TAGS = Set.of("svg", "g", "path", "rect", "circle", "ellipse", "line",
            "polyline", "polygon", "text", "tspan", "title", "desc", "defs", "clippath");
    public static final Set<String> ATTRIBUTES = Set.of("xmlns", "viewbox", "width", "height", "x", "y",
            "x1", "y1", "x2", "y2", "cx", "cy", "r", "rx", "ry", "d", "points", "fill", "stroke",
            "stroke-width", "stroke-linecap", "stroke-linejoin", "stroke-dasharray", "opacity", "fill-opacity",
            "stroke-opacity", "transform", "font-size", "font-family", "font-weight", "text-anchor",
            "dominant-baseline", "dx", "dy", "id", "preserveaspectratio", "role", "aria-label");

    private KbSvgSafety() { }

    public static void validateBytes(byte[] bytes) {
        String xml = new String(bytes, StandardCharsets.UTF_8);
        if (xml.toLowerCase(Locale.ROOT).contains("<!doctype") || xml.toLowerCase(Locale.ROOT).contains("<!entity")) {
            throw new ServiceException("SVG不允许DOCTYPE或实体定义");
        }
        Element svg = Jsoup.parse(xml, "", Parser.xmlParser()).selectFirst("svg");
        if (svg == null) { throw new ServiceException("SVG内容无效"); }
        validate(svg);
    }

    public static void validate(Element root) {
        for (Element element : root.getAllElements()) {
            if (!TAGS.contains(element.tagName().toLowerCase(Locale.ROOT))) {
                throw new ServiceException("SVG包含不支持的元素：" + element.tagName() + "，请转换为静态图形");
            }
            for (Attribute attribute : element.attributes()) {
                String key = attribute.getKey().toLowerCase(Locale.ROOT);
                String value = attribute.getValue().toLowerCase(Locale.ROOT);
                if (!ATTRIBUTES.contains(key) || value.contains("url(") || value.contains("javascript:")
                        || value.contains("data:") || value.contains("expression(")) {
                    throw new ServiceException("SVG包含不支持或不安全的属性：" + attribute.getKey());
                }
                if (!"xmlns".equals(key) && (value.contains("http:") || value.contains("https:"))) {
                    throw new ServiceException("SVG不允许外部资源");
                }
            }
        }
    }
}
