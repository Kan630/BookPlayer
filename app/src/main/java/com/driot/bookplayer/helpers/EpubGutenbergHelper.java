package com.driot.bookplayer.helpers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

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
 * EPUB parser specialized for Project Gutenberg ebooks.
 *
 * Features:
 * - Handles both Gutenberg EPUB2 and EPUB3.
 * - Keeps *all* text; never discards chapters or chunks.
 * - Splits big spine items into logical "chapters" based on headings (H1/H2/H3),
 *   using heuristics tuned for Gutenberg (roman numerals, "Chapitre ..." etc.).
 * - Pure roman numeral headings ("I", "II", "III") are normalized to "Chapter I", etc.
 * - Boilerplate / non-content (header, footer, license, TOC) is *kept* but
 *   written with a filename slug prefixed by "zz_" so it appears at the end.
 * - Outputs plain UTF-8 .txt files with safe paragraph formatting for use in TextView.
 */
public final class EpubGutenbergHelper {

    private EpubGutenbergHelper() {}

    // ===== Config =====

    /** We rely heavily on Jsoup (if present). If not on classpath, we fall back gracefully. */
    private static final boolean USE_JSOUP = true;

    /** Limit snippet length in logs. */
    private static final int LOG_MAX_TEXT_SNIPPET = 160;

    /** Avoid pathologically large titles/filenames. */
    private static final int MAX_TITLE_LEN        = 80;
    private static final int MAX_FILENAME_SLUG    = 40;

    /** Names that are clearly not content chapters (used in friendlyFromFilename). */
    private static final String[] EXCLUDE_NAME_HINTS = new String[]{
            "cover", "titlepage", "title-page", "nav", "toc", "table-of-contents",
            "copyright", "acknowledg", "colophon", "imprint", "frontmatter", "backmatter",
            "glossary", "index", "notes", "endnotes", "footnotes", "about-the-author"
    };

    /** For prettifying filename-ish titles. */
    private static final Pattern FILENAME_WORDS = Pattern.compile("[_\\-]+");

    // ===== Public API =====

    public static final class ExtractResult {
        public final String bookTitle;
        public final File  outDir;
        public final List<File> chapterFiles;
        public final Bitmap coverBitmap;

        public ExtractResult(String t, File d, List<File> f, Bitmap c) {
            bookTitle   = t;
            outDir      = d;
            chapterFiles = f;
            coverBitmap = c;
        }
    }

    // Minimal OPF model
    private static final class OpfInfo {
        String opfPath;
        String title;
        String coverId;
        final Map<String,String> manifestHref  = new LinkedHashMap<>();
        final Map<String,String> manifestType  = new LinkedHashMap<>();
        final Map<String,String> manifestProps = new LinkedHashMap<>();
        final List<String> spine               = new ArrayList<>();
        final List<String> guideRawHrefsToSkip = new ArrayList<>();
        final java.util.Set<String> guideExcludeResolved = new LinkedHashSet<>();
    }

    /** Internal representation of one spine item (may be split into several "chapters"). */
    private static final class SpineItem {
        String itemId;
        String href;
        String resolvedPath;
        String mediaType;
        String fileBase;
    }

    /** One logical chapter chunk extracted from a spine item. */
    private static final class ChapterChunk {
        final String  title;
        final String  text;
        final boolean nonContent; // true = boilerplate/TOC/footer etc.

        ChapterChunk(String t, String s, boolean nonContent) {
            this.title      = t;
            this.text       = s;
            this.nonContent = nonContent;
        }
    }

    /**
     * Main entry point.
     * Reads EPUB from Uri, parses, splits into logical chapters, returns TXT files + cover.
     */
    public static ExtractResult extractAll(Context ctx, Uri epubUri) throws Exception {
        myLog("=== EpubGutenbergHelper.extractAll: begin ===");

        Map<String, byte[]> zip = readZip(epubUri, ctx);

        // --- container.xml -> OPF path ---
        byte[] container = zip.get("META-INF/container.xml");
        if (container == null) {
            myLogE("container.xml not found in EPUB (Gutenberg).");
            throw new IllegalStateException("container.xml not found");
        }
        String opfPath = findOpfPath(container);
        if (opfPath == null) {
            myLogE("OPF path not found in container.xml (Gutenberg).");
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

        // Resolve guide excludes
        opf.guideExcludeResolved.clear();
        for (String hrefRaw : opf.guideRawHrefsToSkip) {
            String resolved = normalize(resolve(basePath, hrefRaw));
            if (resolved != null) opf.guideExcludeResolved.add(resolved);
        }
        myLogD("Guide excludes (resolved): " + opf.guideExcludeResolved);

        String bookTitle = (opf.title != null && !opf.title.trim().isEmpty())
                ? opf.title.trim()
                : "untitled";
        String bookTitleNorm = normalizeTitle(bookTitle);
        myLog("Book title (Gutenberg): " + bookTitle);

        // Output directory: keep separate from generic EPUB helper
        File outDir = new File(ctx.getExternalFilesDir(null),
                "epub_gutenberg_" + safe(bookTitle));
        if (!outDir.exists() && !outDir.mkdirs()) {
            myLogE("Cannot create output dir: " + outDir);
            throw new IllegalStateException("Cannot create " + outDir);
        }

        // Cover
        Bitmap cover = extractCoverBitmap(zip, opf);
        if (cover != null) myLogD("Cover extracted OK (Gutenberg)");

        // --- Build list of spine items ---
        List<SpineItem> spineItems = new ArrayList<>();
        for (String id : opf.spine) {
            SpineItem it = new SpineItem();
            it.itemId      = id;
            it.href        = opf.manifestHref.get(id);
            it.mediaType   = opf.manifestType.get(id);
            if (it.href == null) {
                myLogW("Spine item has null href: " + id);
                continue;
            }
            it.resolvedPath = normalize(resolve(basePath, it.href));
            it.fileBase     = basenameNoExt(it.href);
            spineItems.add(it);
        }

        // --- Process each spine item: split into logical chapters ---
        List<File> outFiles = new ArrayList<>();
        int chapterIndex = 0;

        for (SpineItem it : spineItems) {
            // Only skip truly non-HTML items
            if (shouldSkipEarly(it)) {
                myLogD("SKIP non-HTML spine item: " + it.itemId + " href=" + it.href);
                continue;
            }

            byte[] x = zip.get(it.resolvedPath);
            if (x == null) {
                // Case-insensitive fallback
                for (String k : zip.keySet()) {
                    if (k.equalsIgnoreCase(it.resolvedPath)) {
                        x = zip.get(k);
                        break;
                    }
                }
            }
            if (x == null) {
                myLogW("Missing XHTML for spine item: " + it.resolvedPath);
                continue;
            }

            String xhtml = bytesToStringWithXmlGuess(x);

            // Try to split into logical chapters based on headings
            List<ChapterChunk> chapters = splitSpineIntoChapters(it, xhtml, bookTitleNorm);
            if (chapters.isEmpty()) {
                // Fallback: treat whole item as one chapter
                String plain = xhtmlToPlainSmart(xhtml);
                plain = ensureParagraphs(clean(plain));

                String title = chaptersHeadingFallback(xhtml, it.fileBase, bookTitleNorm);
                title = normalizeRomanChapterTitle(title);

                boolean nonContent = isProbablyNonContent(it, title, plain);
                chapters.add(new ChapterChunk(title, plain, nonContent));
            }

            // Write all chunks (no dropping)
            for (ChapterChunk cc : chapters) {
                if (cc.text == null) continue;
                String txt = cc.text.trim();
                if (txt.isEmpty()) continue;

                chapterIndex++;
                String baseName = cc.title != null ? cc.title : it.fileBase;
                String nameForFile = safeFileNamePreserveSpaces(baseName);

                if (nameForFile.isEmpty()) nameForFile = "chapter";

                // Non-content pushed to the end via "zz_" prefix
                if (cc.nonContent && !nameForFile.toLowerCase(Locale.US).startsWith("zz_")) {
                    nameForFile = "zz_" + nameForFile;
                }

                String fname = String.format(Locale.US,
                        "%03d_%s.txt", chapterIndex, nameForFile);
                File out = new File(outDir, fname);
                writeUtf8(out, txt);
                outFiles.add(out);

                int nl = 0;
                for (int i = 0; i < txt.length(); i++) if (txt.charAt(i) == '\n') nl++;
                myLogD("WROTE (Gutenberg) " + out.getName() + " len=" + txt.length() + " NL=" + nl + ",   snippet: " + snippet(txt));
            }
        }

        myLog("=== EpubGutenbergHelper.extractAll: done; chapters=" + outFiles.size() + " ===");
        return new ExtractResult(bookTitle, outDir, outFiles, cover);
    }

    // ===== Chapter splitting =====

    /**
     * Split a single spine XHTML into logical chapters using headings (H1/H2/H3).
     * Returns empty list if splitting is not worth it (then caller falls back).
     */
    private static List<ChapterChunk> splitSpineIntoChapters(SpineItem base,
                                                             String xhtml,
                                                             String bookTitleNorm) {
        List<ChapterChunk> out = new ArrayList<>();
        if (!USE_JSOUP || xhtml == null) return out;

        try {
            Class.forName("org.jsoup.Jsoup");
        } catch (Throwable t) {
            myLogW("Jsoup not on classpath; cannot split Gutenberg spine into chapters.");
            return out;
        }

        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(xhtml);
        org.jsoup.nodes.Element bodyEl = doc.body();
        if (bodyEl == null) return out;

        // Collect candidate headings
        org.jsoup.select.Elements hTags = bodyEl.select("h1, h2, h3");
        List<org.jsoup.nodes.Element> chapHeads = new ArrayList<>();
        for (org.jsoup.nodes.Element h : hTags) {
            String txt = h.text();
            if (isLikelyChapterHeading(txt, bookTitleNorm)) {
                chapHeads.add(h);
            }
        }

        // If we don\'t see at least a few plausible chapter headings, don\'t split here
        if (chapHeads.size() < 3) {
            myLogD("Gutenberg: spine has only " + chapHeads.size() + " candidate headings -> no split.");
            return out;
        }

        myLogD("Gutenberg: ------------------------ splitting spine into " + chapHeads.size() + " chapters :");

        for (int i = 0; i < chapHeads.size(); i++) {
            org.jsoup.nodes.Element h = chapHeads.get(i);
            org.jsoup.nodes.Element nextHead = (i + 1 < chapHeads.size()) ? chapHeads.get(i + 1) : null;

            // Build a mini HTML fragment: heading + following siblings until next heading
            StringBuilder sbHtml = new StringBuilder();
            org.jsoup.nodes.Node n = h;
            while (n != null && n != nextHead) {
                sbHtml.append(n.outerHtml());
                n = n.nextSibling();
            }

            String chunkHtml = sbHtml.toString();
            if (chunkHtml.trim().isEmpty()) continue;

            String plain = xhtmlToPlainSmart(chunkHtml);
            plain = ensureParagraphs(clean(plain));
            if (plain.trim().isEmpty()) continue;

            String title = h.text();
            if (title == null || title.trim().isEmpty()) title = "chapter " + (i + 1);
            title = title.trim();
            if (title.length() > MAX_TITLE_LEN) title = title.substring(0, MAX_TITLE_LEN).trim();

            // Roman numeral normalization
            title = normalizeRomanChapterTitle(title);

            boolean nonContent = false; // normal chapters by default
            out.add(new ChapterChunk(title, plain, nonContent));
        }

        return out;
    }

    /**
     * Heuristic: determine if a heading looks like a real chapter heading
     * (tuned for Gutenberg French novels).
     */
    private static boolean isLikelyChapterHeading(@Nullable String text,
                                                  String bookTitleNorm) {
        if (text == null) return false;
        String trimmed = text.replaceAll("\\s+", " ").trim();
        if (trimmed.isEmpty()) return false;

        // If it\'s literally the book title, skip it
        String norm = normalizeTitle(trimmed);
        if (!bookTitleNorm.isEmpty() && bookTitleNorm.equals(norm)) return false;

        // Pure roman numerals like I, II, XXIV, etc.
        if (trimmed.matches("(?i)^[IVXLCDM]+\\.?$")) {
            return true;
        }

        // "Chapitre I", "CHAPITRE XXIV", "Chapter 1", "Livre I", etc.
        if (trimmed.matches("(?i)^(chapitre|chapter|livre|partie|section)\\s+.+")) {
            return true;
        }

        // Short uppercase or capitalized word(s), typical in some PG French texts
        if (trimmed.length() <= 20 &&
                trimmed.matches("(?i)^[A-ZÉÈÀÇÙÂÊÎÔÛÄËÏÖÜ][\\p{L}0-9\\- ]+$")) {
            return true;
        }

        return false;
    }

    /** Fallback chapter title when splitting is disabled or fails. */
    private static String chaptersHeadingFallback(String xhtml,
                                                  String fileBase,
                                                  String bookTitleNorm) {
        String fromHeading = findTopHeadingText(xhtml);
        if (fromHeading != null && !fromHeading.trim().isEmpty()) {
            String norm = normalizeTitle(fromHeading);
            if (!norm.isEmpty() && !norm.equals(bookTitleNorm)) {
                return fromHeading.trim();
            }
        }
        String fromTitle = extractTitleTag(xhtml);
        if (fromTitle != null && !fromTitle.trim().isEmpty()) {
            String norm = normalizeTitle(fromTitle);
            if (!norm.isEmpty() && !norm.equals(bookTitleNorm)) {
                return fromTitle.trim();
            }
        }
        if (fileBase != null && !fileBase.isEmpty()) {
            return friendlyFromFilename(fileBase);
        }
        return "chapter";
    }

    /** Roman numeral chapter name normalization. */
    private static String normalizeRomanChapterTitle(String title) {
        if (title == null) return null;
        String trimmed = title.trim();
        // If the whole heading is just a roman numeral, turn it into "Chapter X"
        if (trimmed.matches("(?i)^[IVXLCDM]+\\.?$")) {
            return "Chapter " + trimmed;
        }
        return title;
    }

    /**
     * Heuristic to mark non-content (header/footer/license/TOC/etc.).
     * These are still kept, but filenames will get a "zz_" prefix.
     */
    private static boolean isProbablyNonContent(SpineItem it,
                                                @Nullable String title,
                                                @Nullable String text) {
        String id    = it.itemId != null ? it.itemId.toLowerCase(Locale.ROOT) : "";
        String href  = it.href   != null ? it.href.toLowerCase(Locale.ROOT)   : "";
        String t     = title     != null ? title.toLowerCase(Locale.ROOT)     : "";
        String first = "";

        if (text != null) {
            String trimmed = text.trim();
            int max = Math.min(trimmed.length(), 400);
            first = trimmed.substring(0, max).toLowerCase(Locale.ROOT);
        }

        // Spine IDs typical for Gutenberg boilerplate
        if (id.contains("pg-header") || id.contains("pg-footer")
                || id.contains("coverpage-wrapper")) {
            return true;
        }

        // TOCs, nav, etc.
        if (href.contains("toc") || href.contains("table-of-contents") || href.contains("nav")) {
            return true;
        }

        // Titles / content indicating license or PG boilerplate
        if (t.contains("project gutenberg") || first.contains("project gutenberg")) return true;
        if (t.startsWith("table") || t.startsWith("contents")) return true;
        if (first.contains("license") || first.contains("licence")) return true;

        return false;
    }

    // ===== Early skip (now *very* minimal: only skip non-HTML) =====

    private static boolean shouldSkipEarly(SpineItem it) {
        String mt = it.mediaType != null ? it.mediaType.toLowerCase(Locale.ROOT) : "";
        // Only skip if it is clearly not HTML/XHTML (images, audio, etc.)
        return !(mt.contains("xhtml") || mt.contains("html"));
    }

    // ===== ZIP & OPF parsing =====

    private static Map<String, byte[]> readZip(Uri uri, Context ctx) throws Exception {
        Map<String, byte[]> map = new LinkedHashMap<>();
        try (InputStream in = new BufferedInputStream(ctx.getContentResolver().openInputStream(uri));
             ZipInputStream zin = new ZipInputStream(in)) {
            byte[] buf = new byte[8192];
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (!e.isDirectory()) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream((int) Math.max(0, e.getSize()));
                    int n;
                    while ((n = zin.read(buf)) != -1) bos.write(buf, 0, n);
                    map.put(e.getName(), bos.toByteArray());
                }
                zin.closeEntry();
            }
        }
        myLogD("Gutenberg ZIP entries: " + map.size());
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
                    String nm   = attr(x, "name");
                    String prop = attr(x, "property");
                    if ("cover".equalsIgnoreCase(nm) || "cover".equalsIgnoreCase(prop)) {
                        String content = attr(x, "content");
                        if (content != null && !content.isEmpty()) o.coverId = content;
                    }
                } else if ("item".equalsIgnoreCase(name)) {
                    String id    = attr(x, "id");
                    String href  = attr(x, "href");
                    String mt    = attr(x, "media-type");
                    String props = attr(x, "properties");
                    if (id != null && href != null) {
                        o.manifestHref.put(id, href);
                        o.manifestType.put(id, mt != null ? mt : "");
                        if (props != null) o.manifestProps.put(id, props);
                    }
                } else if ("itemref".equalsIgnoreCase(name)) {
                    String idref = attr(x,"idref");
                    if (idref != null) o.spine.add(idref);
                } else if ("reference".equalsIgnoreCase(name)) {
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
        myLogD("OPF (Gutenberg): title=" + o.title + " spineItems=" + o.spine.size());
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
            for (int i = 0; i < x.getAttributeCount(); i++) {
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
                if (b.length() == 0 || b.charAt(b.length() - 1) == '\n') return;
                b.append('\n');
            }
            private void ensureBlankLine(StringBuilder b) {
                int len = b.length();
                if (len == 0) return;
                if (len >= 2 && b.charAt(len - 1) == '\n' && b.charAt(len - 2) == '\n') return;
                if (len >= 1 && b.charAt(len - 1) != '\n') b.append('\n');
                b.append('\n');
            }
        }, doc.body() != null ? doc.body() : doc);

        return out.toString()
                .replaceAll("[ \\t]+\\n", "\n")
                .trim();
    }

    private static String xhtmlToPlainCompat(String xhtml) {
        if (xhtml == null) return "";
        String pre = xhtml
                .replaceAll("(?is)</(p|div|section|article|blockquote|h[1-6]|ul|ol|li|table|tr)>", "</$1><br/>")
                .replaceAll("(?is)<(h[1-6])\\b[^>]*>", "<br/><$1>")
                .replaceAll("(?is)<li\\b[^>]*>", "<br/><li>");
        CharSequence sp = HtmlCompat.fromHtml(pre, HtmlCompat.FROM_HTML_MODE_LEGACY);
        String txt = sp.toString();
        txt = txt.replace("\r", "")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .replaceAll("[ \\t]{2,}", " ")
                .trim();
        return txt;
    }

    /** Find first H1–H3 heading text (fallback). */
    private static String findTopHeadingText(String xhtml) {
        if (xhtml == null) return null;
        if (USE_JSOUP) {
            try {
                Class.forName("org.jsoup.Jsoup");
                org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(xhtml);
                org.jsoup.nodes.Element h = doc.selectFirst("h1, h2, h3");
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
        String name = slash >= 0 ? href.substring(slash + 1) : href;
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(0, dot) : name;
    }

    private static String safe(String s) {
        String out = s.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (out.isEmpty()) out = "untitled";
        return out.length() > 60 ? out.substring(0, 60) : out;
    }

    private static String safeFileNamePreserveSpaces(String s) {
        if (s == null) return "chapter";
        // Remove forbidden filename chars, keep spaces
        String out = s.replaceAll("[\\\\/:*?\"<>|]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (out.isEmpty()) out = "chapter";
        if (out.length() > MAX_FILENAME_SLUG) {
            out = out.substring(0, MAX_FILENAME_SLUG).trim();
        }
        return out;
    }

    private static String friendlyFromFilename(@Nullable String base) {
        if (base == null || base.isEmpty()) return "chapter";
        String s = FILENAME_WORDS.matcher(base).replaceAll(" ");
        s = s.replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) return "chapter";
        return s;
    }

    private static String normalize(String p) {
        if (p == null) return null;
        String[] parts = p.split("/");
        Deque<String> stack = new ArrayDeque<>();
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) {
                if (!stack.isEmpty()) stack.removeLast();
            } else {
                stack.addLast(part);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String s : stack) {
            if (sb.length() > 0) sb.append('/');
            sb.append(s);
        }
        return sb.toString();
    }

    private static String opfBase(String opfPath) {
        if (opfPath == null) return "";
        int i = opfPath.lastIndexOf('/');
        return i >= 0 ? opfPath.substring(0, i + 1) : "";
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

    private static Bitmap extractCoverBitmap(Map<String, byte[]> zip, OpfInfo opf) {
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
                if (k.equalsIgnoreCase(resolved)) {
                    imgBytes = zip.get(k);
                    break;
                }
            }
        }
        if (imgBytes == null) return null;
        return BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.length);
    }

    private static String bytesToStringWithXmlGuess(byte[] data) {
        if (data == null || data.length == 0) return "";
        String sniff = new String(data, 0, Math.min(data.length, 256),
                Charset.forName("ISO-8859-1"));
        String enc = null;
        int i = sniff.indexOf("encoding=");
        if (i >= 0) {
            int q1 = sniff.indexOf('"', i), q2 = sniff.indexOf('"', q1 + 1);
            int a1 = sniff.indexOf('\'', i), a2 = sniff.indexOf('\'', a1 + 1);
            if (q1 > 0 && q2 > q1) enc = sniff.substring(q1 + 1, q2);
            else if (a1 > 0 && a2 > a1) enc = sniff.substring(a1 + 1, a2);
        }
        Charset cs;
        try { cs = enc != null ? Charset.forName(enc) : Charset.forName("UTF-8"); }
        catch (Exception e) { cs = Charset.forName("UTF-8"); }
        return new String(data, cs);
    }

    private static String clean(String s) {
        if (s == null) return "";
        String t = s.replace('\u00A0',' ');
        t = t.replace("\r\n","\n").replace("\r","\n");
        t = t.replaceAll("[\\t ]{2,}", " ");
        t = t.replaceAll("\n{3,}", "\n\n");
        return t.trim();
    }

    private static String ensureParagraphs(String s) {
        if (s == null) return "";
        int nl = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') nl++;
        if (nl >= 2) return s;

        String t = s.replace("\r", "").replace('\u00A0',' ')
                .replaceAll("[ \\t]{2,}", " ")
                .trim();

        // Heuristic: insert a blank line after sentence-ending punctuation
        t = t.replaceAll(
                "(?<=[.!?…])[ ]+(?=[\"“‘'\\(\\[]?[A-ZÀ-ÖØ-Þ0-9])",
                "\n\n"
        );

        // Scene breaks like ***  → keep visible and isolated
        t = t.replaceAll("[ ]*\\*\\*\\*[ ]*", "\n\n***\n\n");

        return t;
    }

    private static void writeUtf8(File file, String text) throws Exception {
        try (java.io.BufferedWriter bw = new java.io.BufferedWriter(
                new java.io.OutputStreamWriter(
                        new java.io.FileOutputStream(file, false),
                        java.nio.charset.StandardCharsets.UTF_8))) {
            bw.write(text != null ? text : "");
            bw.flush();
        }
    }
}
