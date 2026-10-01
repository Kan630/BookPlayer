package com.driot.bookplayer.ebooks;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Chapter names as the book's own table of contents gives them: EPUB 2 toc.ncx or EPUB 3 nav document.
 * Many EPUBs put the book title in every page's &lt;title&gt;, so without this each chapter got the same name.
 */
public final class EpubTocLabels {

    private EpubTocLabels() {
    }

    /**
     * @param tocContent the toc.ncx or nav document, as text
     * @param tocPath    its path inside the EPUB (hrefs are relative to it)
     * @return content file path inside the EPUB (no #fragment) -> its first TOC label, in TOC order
     */
    public static Map<String, String> parse(String tocContent, String tocPath) {
        Map<String, String> labels = new LinkedHashMap<>();
        if (tocContent == null || tocContent.isEmpty())
            return labels;
        String base = EpubCommonHelper.opfBase(tocPath);
        if (tocContent.contains("<ncx") || tocContent.contains("<navMap")) {
            Document doc = Jsoup.parse(tocContent, "", Parser.xmlParser());
            for (Element navPoint : doc.select("navPoint")) {
                // document order: a navPoint's own label/content come before its nested navPoints
                Element text = navPoint.selectFirst("navLabel text");
                Element content = navPoint.selectFirst("content[src]");
                if (text != null && content != null)
                    add(labels, base, content.attr("src"), text.text());
            }
        } else {
            Document doc = Jsoup.parse(tocContent);
            Elements links = doc.select("nav[epub|type=toc] a[href], nav[epub\\:type=toc] a[href], nav[role=doc-toc] a[href]");
            if (links.isEmpty())
                links = doc.select("nav a[href]");
            for (Element a : links)
                add(labels, base, a.attr("href"), a.text());
        }
        return labels;
    }

    private static void add(Map<String, String> labels, String base, String href, String label) {
        if (href == null || label == null)
            return;
        label = label.replaceAll("\\s+", " ").trim();
        if (label.isEmpty() || label.matches("\\[?\\d+\\]?")) // page-list entries: "[12]", "12"
            return;
        int hash = href.indexOf('#');
        if (hash >= 0)
            href = href.substring(0, hash);
        if (href.isEmpty())
            return;
        String path = EpubCommonHelper.resolve(base, decode(href));
        if (path != null && !labels.containsKey(path))
            labels.put(path, label);
    }

    /** hrefs are URL-encoded ("Chapter%201.xhtml"); a literal '+' is not a space here. */
    private static String decode(String href) {
        if (href.indexOf('%') < 0)
            return href;
        try {
            return java.net.URLDecoder.decode(href.replace("+", "%2B"), "UTF-8");
        } catch (Exception e) {
            return href;
        }
    }
}
