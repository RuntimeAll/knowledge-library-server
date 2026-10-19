package com.ruoyi.knowledge.html;

import com.ruoyi.common.exception.ServiceException;
import java.util.HashSet;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/** Explicit, portable layout semantics; never infers layout from images or arbitrary CSS. */
public final class KbHtmlLayout {
    private KbHtmlLayout() { }

    public record Structure(boolean grouped, boolean options, int itemCount) { }

    public static Structure inspect(String html) {
        return inspect(Jsoup.parseBodyFragment(html == null ? "" : html).body());
    }

    public static Structure inspect(Element root) {
        return inspect(root, false);
    }

    public static Structure inspect(Element root, boolean allowEmptyGroupItems) {
        var groups = root.select("[data-kb-role=group]");
        var options = root.select("[data-kb-role=options]");
        if (groups.size() > 1 || options.size() > 1)
            fail("每题只能有一个成组区域和一个选项区域");
        if (!groups.isEmpty() && !options.isEmpty())
            fail("成组题暂不支持嵌入独立选择题选项；请拆为独立选择题");
        for (Element element : root.select("[data-kb-role]")) {
            if (!"div".equals(element.tagName())
                    || !Set.of("options", "group", "vertical", "puzzle").contains(element.attr("data-kb-role")))
                fail("排版容器只支持div上的options、group、vertical或puzzle语义");
        }
        for (Element element : root.select("[data-columns]")) {
            if (!"group".equals(element.attr("data-kb-role"))
                    || !Set.of("2", "3", "4").contains(element.attr("data-columns")))
                fail("data-columns只允许成组容器使用2、3或4列");
        }
        for (Element element : root.select("[data-option-key]")) {
            if (!directChildOf(element, "options")) fail("选项必须是options容器的直属div");
        }
        for (Element element : root.select("[data-item-key]")) {
            if (!directChildOf(element, "group")) fail("子项必须是group容器的直属div");
        }
        if (!options.isEmpty()) validateChildren(options.get(0), "data-option-key", "[A-Z]", 2, 26, false);
        int count = 1;
        if (!groups.isEmpty()) {
            Element group = groups.get(0);
            if (!group.hasAttr("data-columns")) fail("成组容器必须明确data-columns为2、3或4");
            validateChildren(group, "data-item-key", "[A-Za-z0-9._-]{1,64}", 2, 200, allowEmptyGroupItems);
            count = group.childrenSize();
        }
        return new Structure(!groups.isEmpty(), !options.isEmpty(), count);
    }

    public static Structure validateParts(String stem, String answer, String analysis) {
        return validateParts(fragment(stem), fragment(answer), fragment(analysis));
    }

    public static Structure validateParts(Element stem, Element answer, Element analysis) {
        Structure result = inspect(stem);
        Element stemGroup = stem.selectFirst("[data-kb-role=group]");
        for (Element part : new Element[] {answer, analysis}) {
            if (part == null) continue;
            Structure other = inspect(part, true);
            if (other.options()) fail("选项结构只能放在题干中");
            if (other.grouped()) {
                Element group = part.selectFirst("[data-kb-role=group]");
                if (stemGroup == null
                        || !group.attr("data-columns").equals(stemGroup.attr("data-columns"))
                        || !group.children().eachAttr("data-item-key").equals(stemGroup.children().eachAttr("data-item-key")))
                    fail("成组答案或解析的列数、子项编号和顺序必须与题干一致；缺失内容请留空占位");
            }
        }
        return result;
    }

    private static Element fragment(String html) {
        return Jsoup.parseBodyFragment(html == null ? "" : html).body();
    }

    private static boolean directChildOf(Element element, String role) {
        return "div".equals(element.tagName()) && element.parent() != null
                && role.equals(element.parent().attr("data-kb-role"));
    }

    private static void validateChildren(Element container, String attribute, String pattern, int min, int max,
                                         boolean allowEmptyItems) {
        if (container.childrenSize() < min || container.childrenSize() > max || !container.ownText().isBlank())
            fail("选项或成组容器只能包含规定数量的直属子项，不能夹杂未标记内容");
        Set<String> keys = new HashSet<>();
        for (Element child : container.children()) {
            String key = child.attr(attribute);
            if (!"div".equals(child.tagName()) || !key.matches(pattern) || !keys.add(key))
                fail("选项或成组子项标识无效或重复：" + attribute);
            if (child.hasAttr("data-kb-role") || child.hasAttr("data-part"))
                fail("子项不能复用区域标记或排版容器角色");
            if (!allowEmptyItems && child.text().isBlank() && child.select("img,svg,[data-latex]").isEmpty())
                fail("选项或成组子项内容不能为空");
        }
    }

    private static void fail(String message) { throw new ServiceException(message, 400); }

    /** Same semantic selectors are used by the online preview. Large content may still span pages. */
    public static final String CSS = """
        article[data-pagination-mode=KEEP_TOGETHER]{break-inside:avoid;page-break-inside:avoid}
        article[data-pagination-mode=ALLOW_SPLIT]{break-inside:auto;page-break-inside:auto}
        [data-kb-role=group]{display:grid;gap:16px 24px}
        [data-kb-role=group][data-columns="2"]{grid-template-columns:repeat(2,minmax(0,1fr))}
        [data-kb-role=group][data-columns="3"]{grid-template-columns:repeat(3,minmax(0,1fr))}
        [data-kb-role=group][data-columns="4"]{grid-template-columns:repeat(4,minmax(0,1fr))}
        [data-item-key]{min-width:0;break-inside:avoid}
        [data-kb-role=options]{display:grid;gap:8px;margin:12px 0}
        [data-option-key]::before{content:attr(data-option-key) '. ';font-weight:bold}
        [data-kb-role=vertical] table{font-family:monospace;margin:8px auto}
        [data-kb-role=vertical] td,[data-kb-role=vertical] th{text-align:right;min-width:1.5em;padding:2px 6px}
        [data-kb-role=puzzle] table{font-family:monospace;margin:8px auto}
        [data-kb-role=puzzle] td,[data-kb-role=puzzle] th{text-align:center;min-width:2em;height:2em;padding:4px}
        """;
}
