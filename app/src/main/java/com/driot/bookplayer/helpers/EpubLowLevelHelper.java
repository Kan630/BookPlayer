package com.driot.bookplayer.helpers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.Spanned;

import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;

import com.driot.bookplayer.utils.log.KanLogger;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Simplified EPUB extractor (1 file per usable spine item) with detailed logging.
 * - Skips obvious non-chapters (cover/nav/toc/copyright…).
 * - Uses H1–H3/role=doc-chapter as title when present; else <title>; else filename.
 * - Keeps numeric sequence (001_, 002_, …) and names file after detected chapter title.
 * - Optional Jsoup usage (toggle with USE_JSOUP).
 */
public final class EpubLowLevelHelper {
    private EpubLowLevelHelper() {}

    // ===== Config =====
    /** Toggle Jsoup usage. If true but Jsoup is missing, we log & fallback automatically. */
    public static boolean USE_JSOUP = true;

    private static final int LOG_MAX_TEXT_SNIPPET = 160;
    private static final int MIN_PLAUSIBLE_BODY_CHARS = 40; // small stubs can still be included; tune as you like

    private static final String[] EXCLUDE_NAME_HINTS = new String[]{
            "cover", "titlepage", "title-page", "nav", "toc", "table-of-contents",
            "copyright", "acknowledg", "colophon", "imprint", "frontmatter", "backmatter",
            "glossary", "index", "notes", "endnotes", "footnotes", "about-the-author"
    };

    // Useful to prettify filename-only titles like "chapter_001"
    private static final Pattern FILENAME_WORDS = Pattern.compile("[_\\-]+");

    // ===== Data =====
    public static final class OpfInfo {
        public String opfPath;
        public String title;
        public String coverId;
        public final Map<String,String> manifestHref  = new LinkedHashMap<>();
        public final Map<String,String> manifestType  = new LinkedHashMap<>();
        public final Map<String,String> manifestProps = new LinkedHashMap<>();
        public final List<String>      spine          = new ArrayList<>();
        public final List<String>      guideRawHrefsToSkip = new ArrayList<>();
        public final java.util.Set<String> guideExcludeResolved = new LinkedHashSet<>();
    }

    public static final class ExtractResult {
        public final String bookTitle;
        public final File outDir;
        public final List<File> chapterFiles;
        public final Bitmap coverBitmap;
        public ExtractResult(String t, File d, List<File> f, Bitmap c) { bookTitle=t; outDir=d; chapterFiles=f; coverBitmap=c; }
    }

    /** Minimal processed spine item for 1:1 exporting. */
    private static final class SpineItem {
        String itemId;
        String href;            // raw href from manifest
        String resolvedPath;    // base+href normalized
        String mediaType;
        boolean skip;
        String skipReason;

        String headingRaw;      // detected H1–H3/role heading text (if any)
        String titleTagRaw;     // <title> text (if any)
        String text;            // cleaned plain text body
        int    textLen;

        String fileBase;        // filename without extension

        @Override public String toString() {
            return "SpineItem{" +
                    "id='" + itemId + '\'' +
                    ", href='" + href + '\'' +
                    ", mt='" + mediaType + '\'' +
                    ", skip=" + skip +
                    (skip ? ", reason=" + skipReason : "") +
                    ", heading='" + headingRaw + '\'' +
                    ", titleTag='" + titleTagRaw + '\'' +
                    ", textLen=" + textLen +
                    '}';
        }
    }

    // ===== Entry point =====
    public static ExtractResult extractAll(Context ctx, Uri epubUri) throws Exception {
        myLogI("=== extractAll: begin ===");
        Map<String, byte[]> zip = readZip(epubUri, ctx);

        byte[] container = zip.get("META-INF/container.xml");
        if (container == null) {
            myLogE("container.xml not found in epub!");
            throw new IllegalStateException("container.xml not found");
        }
        String opfPath = findOpfPath(container);
        if (opfPath == null) {
            myLogE("content.opf not found from container.xml!");
            throw new IllegalStateException("content.opf not found");
        }
        myLogD("OPF path = " + opfPath);

        byte[] opfBytes = zip.get(opfPath);
        if (opfBytes == null) {
            myLogE("OPF bytes missing at " + opfPath);
            throw new IllegalStateException("OPF missing at " + opfPath);
        }

        OpfInfo opf = parseOpf(opfBytes);
        opf.opfPath = opfPath;
        String basePath = opfBase(opfPath);

        // Resolve guide excludes with OPF base
        opf.guideExcludeResolved.clear();
        for (String hrefRaw : opf.guideRawHrefsToSkip) {
            String resolved = normalize(resolve(basePath, hrefRaw));
            if (resolved != null) opf.guideExcludeResolved.add(resolved);
        }
        myLogD("Guide excludes (resolved): " + opf.guideExcludeResolved);

        String bookTitle = (opf.title != null && !opf.title.trim().isEmpty()) ? opf.title.trim() : "untitled";
        String bookTitleNorm = normalizeTitle(bookTitle);
        myLogI("Book title: " + bookTitle);

        File outDir = new File(ctx.getExternalFilesDir(null), "epub_" + safe(bookTitle));
        if (!outDir.exists() && !outDir.mkdirs()) {
            myLogE("Cannot create output dir: " + outDir);
            throw new IllegalStateException("Cannot create " + outDir);
        }

        Bitmap cover = extractCoverBitmap(zip, opf);
        if (cover != null) myLogD("Cover extracted OK");

        // Pass: parse every spine item; keep only textual, non-tiny, non-front/back matter
        List<SpineItem> body = new ArrayList<>();
        for (String itemId : opf.spine) {
            SpineItem it = new SpineItem();
            it.itemId   = itemId;
            it.href     = opf.manifestHref.get(itemId);
            it.mediaType= opf.manifestType.get(itemId);

            if (it.href == null) {
                myLogW("Spine item has null href: " + itemId);
                continue;
            }

            it.resolvedPath = normalize(resolve(basePath, it.href));
            it.fileBase     = basenameNoExt(it.href);

            // Early skip rules
            if (shouldSkipEarly(opf, it)) {
                it.skip = true;
                myLogD("SKIP(early): " + it);
                continue;
            }

            // Load xhtml bytes
            byte[] x = zip.get(it.resolvedPath);
            if (x == null) {
                // Case-insensitive fallback
                for (String k : zip.keySet()) {
                    if (k.equalsIgnoreCase(it.resolvedPath)) { x = zip.get(k); break; }
                }
            }
            if (x == null) {
                it.skip = true;
                it.skipReason = "missing-file";
                myLogW("SKIP missing file: " + it.resolvedPath);
                continue;
            }

            String xhtml = bytesToStringWithXmlGuess(x);

            // Extract possible heading and title
            it.headingRaw  = findTopHeadingText(xhtml);
            it.titleTagRaw = extractTitleTag(xhtml);

            // Plain text extraction
            String plain = xhtmlToPlainSmart(xhtml);
            plain = ensureParagraphs(plain);
            it.text = clean(plain);

            it.textLen = it.text.length();

            // Tiny/empty body? still allow small pages (tune threshold if needed)
            if (it.textLen < MIN_PLAUSIBLE_BODY_CHARS) {
                myLogD("DROP tiny/empty body: " + it.href + " (len=" + it.textLen + ")");
                continue;
            }

            body.add(it);

            int nl = 0; for (int i=0;i<plain.length();i++) if (plain.charAt(i)=='\n') nl++;
            myLogD("KEEP: " + it);
            myLogD("  NL count = " + nl); //TODO not working, then reading in activity, got 0 NL... currently failback in audioService do add some (ends up with like 200 instead of 40 NL)
            myLogD("  text-snippet: " + snippet(it.text));
        }

        myLogI("Usable content items: " + body.size());

        // Write: 1 file per usable item (keep running index; name after chosen chapter title)
        List<File> outFiles = new ArrayList<>();
        int idx = 0;
        for (SpineItem it : body) {
            ++idx;
            String chosenTitle = chooseTitle(it, bookTitleNorm);
            String fname = String.format(Locale.US, "%03d_%s.txt", idx, safeSlug(chosenTitle));
            File f = new File(outDir, fname);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(it.text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            outFiles.add(f);
            myLogD(String.format(Locale.US, "WROTE [%s] len=%d", f.getName(), it.textLen));
        }

        myLog("========================================================================================");
        myLogI("=== extractAll: done; files=" + outFiles.size() + " ===");
        myLog("========================================================================================");
        return new ExtractResult(bookTitle, outDir, outFiles, cover);
    }

    // ===== Title choosing (per item) =====
    private static String chooseTitle(SpineItem it, String bookTitleNorm) {
        // Prefer heading when it doesn't look like the book title
        if (it.headingRaw != null) {
            String hNorm = normalizeTitle(it.headingRaw);
            if (!hNorm.isEmpty() && !hNorm.equals(bookTitleNorm)) {
                return it.headingRaw.trim();
            }
        }
        // Otherwise, try <title> if it’s not the book title
        if (it.titleTagRaw != null) {
            String tNorm = normalizeTitle(it.titleTagRaw);
            if (!tNorm.isEmpty() && !tNorm.equals(bookTitleNorm)) {
                return it.titleTagRaw.trim();
            }
        }
        // Fallback to a cleaned filename base
        return friendlyFromFilename(it.fileBase);
    }

    private static String friendlyFromFilename(@Nullable String base) {
        if (base == null || base.isEmpty()) return "chapter";
        // Replace _ and - with spaces and collapse
        String s = FILENAME_WORDS.matcher(base).replaceAll(" ");
        s = s.replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) return "chapter";
        return s;
    }

    // ===== Skip heuristics =====
    private static boolean shouldSkipEarly(OpfInfo opf, SpineItem it) {
        String mt = it.mediaType != null ? it.mediaType.toLowerCase(Locale.ROOT) : "";
        if (!(mt.contains("xhtml") || mt.contains("html"))) {
            it.skipReason = "non-html";
            return true;
        }

        // manifest properties: nav or cover-image etc.
        String props = opf.manifestProps.get(it.itemId);
        if (props != null) {
            String p = props.toLowerCase(Locale.ROOT);
            if (p.contains("nav") || p.contains("cover-image") || p.contains("titlepage")) {
                it.skipReason = "props:" + p;
                return true;
            }
        }

        // guide excludes (resolved)
        if (it.resolvedPath != null && opf.guideExcludeResolved.contains(it.resolvedPath)) {
            it.skipReason = "guide";
            return true;
        }

        // filename hints
        String h = it.href.toLowerCase(Locale.ROOT);
        for (String kw : EXCLUDE_NAME_HINTS) {
            if (h.contains("/" + kw) || h.endsWith(kw + ".xhtml") || h.endsWith(kw + ".html") || h.contains("_" + kw)) {
                it.skipReason = "namehint:" + kw;
                return true;
            }
        }
        return false;
    }

    // ===== EPUB parsing =====
    private static Map<String, byte[]> readZip(Uri uri, Context ctx) throws Exception {
        Map<String, byte[]> map = new LinkedHashMap<>();
        try (InputStream in = new BufferedInputStream(ctx.getContentResolver().openInputStream(uri));
             ZipInputStream zin = new ZipInputStream(in)) {
            byte[] buf = new byte[8192];
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (!e.isDirectory()) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream((int)Math.max(0, e.getSize()));
                    int n;
                    while ((n = zin.read(buf)) != -1) bos.write(buf, 0, n);
                    map.put(e.getName(), bos.toByteArray());
                }
                zin.closeEntry();
            }
        }
        myLogD("ZIP entries: " + map.size());
        return map;
    }

    private static String findOpfPath(byte[] containerXml) throws Exception {
        XmlPullParser x = newPull(containerXml);
        int t;
        while ((t = x.next()) != XmlPullParser.END_DOCUMENT) {
            if (t == XmlPullParser.START_TAG && "rootfile".equals(x.getName())) {
                String p = attr(x, "full-path");
                if (p != null) return p;
            }
        }
        return null;
    }

    private static OpfInfo parseOpf(byte[] opfXml) throws Exception {
        OpfInfo o = new OpfInfo();
        XmlPullParser x = newPull(opfXml);
        int t;
        boolean inMetadata = false;

        while ((t = x.next()) != XmlPullParser.END_DOCUMENT) {
            if (t == XmlPullParser.START_TAG) {
                String name = x.getName();
                if ("metadata".equalsIgnoreCase(name)) {
                    inMetadata = true;
                } else if (inMetadata && ("dc:title".equalsIgnoreCase(name) || "title".equalsIgnoreCase(name))) {
                    o.title = text(x).trim();
                } else if ("meta".equalsIgnoreCase(name)) {
                    String nm = attr(x,"name");
                    String prop = attr(x,"property");
                    if ("cover".equalsIgnoreCase(nm) || "cover".equalsIgnoreCase(prop)) {
                        String content = attr(x,"content");
                        if (content != null && !content.isEmpty()) o.coverId = content;
                    }
                } else if ("item".equalsIgnoreCase(name)) {
                    String id   = attr(x,"id");
                    String href = attr(x,"href");
                    String mt   = attr(x,"media-type");
                    String props= attr(x,"properties"); // EPUB3
                    if (id != null && href != null) {
                        o.manifestHref.put(id, href);
                        o.manifestType.put(id, mt != null ? mt : "");
                        if (props != null) o.manifestProps.put(id, props);
                    }
                } else if ("itemref".equalsIgnoreCase(name)) {
                    String idref = attr(x,"idref");
                    if (idref != null) o.spine.add(idref);
                } else if ("reference".equalsIgnoreCase(name)) {
                    // EPUB2 guide landmarks
                    String type = attr(x,"type");
                    String href = attr(x,"href");
                    if (type != null && href != null) {
                        String ty = type.toLowerCase(Locale.ROOT);
                        if (ty.matches("cover|title-page|toc|index|copyright-page|acknowledgements|colophon|glossary|backmatter|frontmatter")) {
                            o.guideRawHrefsToSkip.add(href);
                        }
                    }
                }
            } else if (t == XmlPullParser.END_TAG && "metadata".equalsIgnoreCase(x.getName())) {
                inMetadata = false;
            }
        }
        myLogD("OPF: title=" + o.title + " spineItems=" + o.spine.size());
        return o;
    }

    private static XmlPullParser newPull(byte[] bytes) throws Exception {
        XmlPullParserFactory f = XmlPullParserFactory.newInstance();
        f.setNamespaceAware(true);
        XmlPullParser x = f.newPullParser();
        x.setInput(new ByteArrayInputStream(bytes), null);
        return x;
    }

    private static String attr(XmlPullParser x, String name) {
        String v = x.getAttributeValue(null, name);
        if (v == null) v = x.getAttributeValue("", name);
        if (v == null && x.getAttributeCount() > 0) {
            for (int i=0;i<x.getAttributeCount();i++) {
                if (name.equals(x.getAttributeName(i))) return x.getAttributeValue(i);
            }
        }
        return v;
    }

    private static String text(XmlPullParser x) throws Exception {
        StringBuilder sb = new StringBuilder();
        int t;
        while ((t = x.next()) != XmlPullParser.END_DOCUMENT) {
            if (t == XmlPullParser.TEXT) sb.append(x.getText());
            else if (t == XmlPullParser.END_TAG) break;
        }
        return sb.toString();
    }

    // ===== HTML → Text & headings =====
    private static String xhtmlToPlainSmart(String xhtml) {
        if (USE_JSOUP) {
            try {
                Class.forName("org.jsoup.Jsoup");
                return xhtmlToPlainWithJsoup(xhtml);
            } catch (Throwable t) {
                myLogW("Jsoup not on classpath; falling back to HtmlCompat.");
            }
        }
        return xhtmlToPlainCompat(xhtml);
    }

    private static String xhtmlToPlainWithJsoup(String xhtml) {
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(xhtml);
        doc.outputSettings(new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false));

        StringBuilder out = new StringBuilder(xhtml.length() / 2);
        org.jsoup.select.NodeTraversor.traverse(new org.jsoup.select.NodeVisitor() {
            private boolean atLineStart = true;
            @Override public void head(org.jsoup.nodes.Node node, int depth) {
                String name = node.nodeName();

                if (node instanceof org.jsoup.nodes.TextNode) {
                    String text = ((org.jsoup.nodes.TextNode) node).text();
                    if (!text.trim().isEmpty()) {
                        if (!atLineStart) out.append(' ');
                        out.append(text.trim());
                        atLineStart = false;
                    }
                    return;
                }
                if ("li".equalsIgnoreCase(name)) { newLine(out); out.append("• "); atLineStart = false; }
                if ("br".equalsIgnoreCase(name)) { newLine(out); atLineStart = true; }
                if (name.matches("h[1-6]")) { ensureBlankLine(out); atLineStart = true; }
            }
            @Override public void tail(org.jsoup.nodes.Node node, int depth) {
                String name = node.nodeName();
                if (isBlockForPlain(name)) ensureBlankLine(out);
            }
            private boolean isBlockForPlain(String name) {
                String n = name.toLowerCase(Locale.ROOT);
                return n.equals("p") || n.equals("div") || n.equals("section") || n.equals("article")
                        || n.equals("blockquote") || n.equals("ul") || n.equals("ol")
                        || n.equals("table") || n.equals("tr") || n.equals("thead") || n.equals("tbody")
                        || n.matches("h[1-6]");
            }
            private void newLine(StringBuilder b) {
                if (b.length() == 0 || b.charAt(b.length()-1) == '\n') return;
                b.append('\n');
            }
            private void ensureBlankLine(StringBuilder b) {
                int len = b.length();
                if (len == 0) return;
                if (len >= 2 && b.charAt(len-1) == '\n' && b.charAt(len-2) == '\n') return;
                if (len >= 1 && b.charAt(len-1) != '\n') b.append('\n');
                b.append('\n');
            }
        }, doc.body() != null ? doc.body() : doc);

        return out.toString().replaceAll("[ \\t]+\\n", "\n").trim();
    }

    private static String xhtmlToPlainCompat(String xhtml) {
        if (xhtml == null) return "";
        String pre = xhtml
                .replaceAll("(?is)</(p|div|section|article|blockquote|h[1-6]|ul|ol|li|table|tr)>", "</$1><br/>")
                .replaceAll("(?is)<(h[1-6])\\b[^>]*>", "<br/><$1>")
                .replaceAll("(?is)<li\\b[^>]*>", "<br/><li>");
        Spanned sp = HtmlCompat.fromHtml(pre, HtmlCompat.FROM_HTML_MODE_LEGACY);
        String txt = sp.toString();
        txt = txt.replace("\r", "")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .replaceAll("[ \\t]{2,}", " ")
                .trim();
        return txt;
    }

    /** H1–H3 (or epub landmarks) else null. */
    private static String findTopHeadingText(String xhtml) {
        if (xhtml == null) return null;
        if (USE_JSOUP) {
            try {
                Class.forName("org.jsoup.Jsoup");
                org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(xhtml);
                org.jsoup.nodes.Element h = doc.selectFirst(
                        "h1, h2, h3, [role=doc-chapter], [epub\\:type=chapter], [epub\\:type~=chapter]"
                );
                if (h != null) {
                    String txt = h.text();
                    if (txt != null) {
                        txt = txt.replaceAll("\\s+"," ").trim();
                        if (!txt.isEmpty()) return txt;
                    }
                }
            } catch (Throwable t) {
                myLogW("Jsoup not on classpath; heading detection falling back.");
            }
        }
        // Lightweight fallback: first H1/H2/H3 text
        String lower = xhtml.toLowerCase(Locale.ROOT);
        int[] tags = { lower.indexOf("<h1"), lower.indexOf("<h2"), lower.indexOf("<h3") };
        int best = Integer.MAX_VALUE, start = -1;
        for (int p : tags) if (p >= 0 && p < best) { best = p; start = p; }
        if (start >= 0) {
            int gt = lower.indexOf(">", start);
            if (gt > start) {
                int endH1 = lower.indexOf("</h1>", gt);
                int endH2 = lower.indexOf("</h2>", gt);
                int endH3 = lower.indexOf("</h3>", gt);
                int end = Integer.MAX_VALUE;
                if (endH1 >= 0) end = Math.min(end, endH1);
                if (endH2 >= 0) end = Math.min(end, endH2);
                if (endH3 >= 0) end = Math.min(end, endH3);
                if (end != Integer.MAX_VALUE && end > gt) {
                    String raw = xhtml.substring(gt + 1, end).replaceAll("\\s+"," ").trim();
                    if (!raw.isEmpty()) return raw;
                }
            }
        }
        return null;
    }

    private static String extractTitleTag(String xhtml) {
        if (xhtml == null) return null;
        String lower = xhtml.toLowerCase(Locale.US);
        int i = lower.indexOf("<title>");
        if (i >= 0) {
            int j = lower.indexOf("</title>", i + 7);
            if (j > i) {
                String s = xhtml.substring(i + 7, j).replaceAll("\\s+", " ").trim();
                if (!s.isEmpty()) return s;
            }
        }
        return null;
    }

    // ===== Misc utils =====
    private static String basenameNoExt(String href) {
        if (href == null) return null;
        int slash = href.lastIndexOf('/');
        String name = slash>=0 ? href.substring(slash+1) : href;
        int dot = name.lastIndexOf('.');
        return dot>=0 ? name.substring(0,dot) : name;
    }

    private static String safe(String s) {
        String out = s.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (out.isEmpty()) out = "untitled";
        return out.length() > 60 ? out.substring(0,60) : out;
    }

    private static String safeSlug(String s) {
        String out = s.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+","-")
                .replaceAll("^-+|-+$","");
        if (out.isEmpty()) out = "chapter";
        return out.length()>40 ? out.substring(0,40) : out;
    }

    private static String normalize(String p) {
        if (p == null) return null;
        String[] parts = p.split("/");
        Deque<String> stack = new ArrayDeque<>();
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) { if (!stack.isEmpty()) stack.removeLast(); }
            else stack.addLast(part);
        }
        StringBuilder sb = new StringBuilder();
        for (String s : stack) { if (sb.length()>0) sb.append('/'); sb.append(s); }
        return sb.toString();
    }

    private static String opfBase(String opfPath) {
        if (opfPath == null) return "";
        int i = opfPath.lastIndexOf('/');
        return i>=0 ? opfPath.substring(0, i+1) : "";
    }

    private static String resolve(String base, String href) {
        if (href == null) return null;
        if (href.startsWith("/")) return href.substring(1);
        if (base == null || base.isEmpty()) return normalize(href);
        return normalize(base + href);
    }

    private static String normalizeTitle(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}“”‘’«»]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String snippet(String s) {
        if (s == null) return "";
        String t = s.replace("\n"," ").replaceAll("\\s+"," ").trim();
        return t.length() <= LOG_MAX_TEXT_SNIPPET ? t : t.substring(0, LOG_MAX_TEXT_SNIPPET) + "…";
    }

    // ===== Cover & encoding =====
    public static Bitmap extractCoverBitmap(Map<String, byte[]> zip, OpfInfo opf) {
        String basePath = opfBase(opf.opfPath);
        String coverHref = null;
        if (opf.coverId != null) coverHref = opf.manifestHref.get(opf.coverId);
        if (coverHref == null) {
            for (Map.Entry<String,String> e : opf.manifestType.entrySet()) {
                String mt = e.getValue();
                if (mt != null && mt.startsWith("image/")) {
                    coverHref = opf.manifestHref.get(e.getKey());
                    if (coverHref != null) break;
                }
            }
        }
        if (coverHref == null) return null;
        String resolved = resolve(basePath, coverHref);
        byte[] imgBytes = zip.get(resolved);
        if (imgBytes == null) {
            for (String k : zip.keySet()) {
                if (k.equalsIgnoreCase(resolved)) { imgBytes = zip.get(k); break; }
            }
        }
        if (imgBytes == null) return null;
        return BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.length);
    }

    private static String bytesToStringWithXmlGuess(byte[] data) {
        String sniff = new String(data, 0, Math.min(data.length, 256), Charset.forName("ISO-8859-1"));
        String enc = null;
        int i = sniff.indexOf("encoding=");
        if (i >= 0) {
            int q1 = sniff.indexOf('"', i), q2 = sniff.indexOf('"', q1+1);
            int a1 = sniff.indexOf('\'', i), a2 = sniff.indexOf('\'', a1+1);
            if (q1>0 && q2>q1) enc = sniff.substring(q1+1, q2);
            else if (a1>0 && a2>a1) enc = sniff.substring(a1+1, a2);
        }
        Charset cs;
        try { cs = enc!=null ? Charset.forName(enc) : Charset.forName("UTF-8"); }
        catch (Exception e) { cs = Charset.forName("UTF-8"); }
        return new String(data, cs);
    }

    private static String clean(String s) { if (s == null) return ""; String t = s.replace('\u00A0',' '); t = t.replace("\r\n","\n").replace("\r","\n"); t = t.replaceAll("[\\t ]{2,}", " "); t = t.replaceAll("\n{3,}", "\n\n"); return t.trim(); }

    private static String ensureParagraphs(String s) {
        if (s == null) return "";
        // If we already have some line breaks, keep as-is
        int nl = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') nl++;
        if (nl >= 2) return s;

        // Normalize spaces a bit
        String t = s.replace("\r", "").replace('\u00A0',' ')
                .replaceAll("[ \\t]{2,}", " ").trim();

        // Heuristic: insert a blank line after sentence-ending punctuation
        // when the next token looks like a new sentence/paragraph.
        // Break on “. ”, “? ”, “! ”, “…” etc., before a capital/quote/number.
        t = t.replaceAll(
                "(?<=[.!?…])[ ]+(?=[\"“‘'\\(\\[]?[A-ZÀ-ÖØ-Þ0-9])",
                "\n\n"
        );

        // Scene breaks like ***  → keep visible and isolated
        t = t.replaceAll("[ ]*\\*\\*\\*[ ]*", "\n\n***\n\n");

        return t;
    }


    // ===== Logging =====
    private static final String TAG = "EpubLowLevelHelper";
    private static void myLog(String str)  { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }
}
