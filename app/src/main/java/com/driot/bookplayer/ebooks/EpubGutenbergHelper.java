package com.driot.bookplayer.ebooks;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.Nullable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;
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
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

/**
 * Gutenberg-focused EPUB extractor.
 *
 * Strategy:
 * - Parse container.xml -> content.opf.
 * - From OPF:
 * - title, coverId, manifest, spine, nav item (properties="nav").
 * - Read toc.xhtml nav[epub:type=toc] and:
 * - For each <a href="file.xhtml#fragment">Title</a> create a chapter.
 * - Ignore pagelist entries like "[1]".
 * - For each referenced XHTML file:
 * - Locate each fragment id in the XHTML string.
 * - Take the substring from this id up to the next id used by TOC.
 * - Convert HTML -> plain text with Jsoup, preserving paragraphs.
 * - For XHTML files *not* referenced by TOC:
 * - Export all their text as a zz_* chapter (typically license, etc.).
 *
 * Nothing is discarded; "non-book" content is simply pushed at the end
 * with a "zz_" prefix in the filename.
 */
public final class EpubGutenbergHelper {

    private EpubGutenbergHelper() {
    }

    // ---------- Data classes ----------

    public static final class OpfInfo {
        public String opfPath;
        public String title;
        public String coverId;
        public final Map<String, String> manifestHref = new LinkedHashMap<>();
        public final Map<String, String> manifestType = new LinkedHashMap<>();
        public final Map<String, String> manifestProps = new LinkedHashMap<>();
        public final List<String> spine = new ArrayList<>();
    }

    public static final class ExtractResult {
        public final String bookTitle;
        public final File outDir;
        public final List<File> chapterFiles;
        @Nullable
        public final Bitmap coverBitmap;

        public ExtractResult(String t, File d, List<File> f, @Nullable Bitmap c) {
            this.bookTitle = t;
            this.outDir = d;
            this.chapterFiles = f;
            this.coverBitmap = c;
        }
    }

    /** One TOC entry. */
    private static final class TocEntry {
        String filePath; // normalized, with OPF base
        String fragmentId; // after '#', may be null
        String title; // visible nav text
    }

    // For slugging filenames a little nicer (replace _ / - with spaces when
    // deriving labels if needed).
    private static final Pattern FILENAME_WORDS = Pattern.compile("[_\\-]+");
    private static final int MAX_FILENAME_CHARS = 80;

    // ---------- Public entry point ----------

    public static ExtractResult extractAll(Context ctx, Uri epubUri) throws Exception {
        myLog("=== EpubGutenbergHelper.extractAll: begin ===");

        Map<String, byte[]> zip = readZip(epubUri, ctx);

        byte[] container = zip.get("META-INF/container.xml");
        if (container == null) {
            myLogE("container.xml not found in EPUB");
            throw new IllegalStateException("container.xml not found");
        }

        String opfPath = findOpfPath(container);
        if (opfPath == null) {
            myLogE("content.opf not found from container.xml");
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

        myLogD("OPF (Gutenberg): title=" + opf.title + " spineItems=" + opf.spine.size());

        // Book title
        String bookTitle = (opf.title != null && !opf.title.trim().isEmpty())
                ? opf.title.trim()
                : "untitled";
        String safeBookTitle = safeName(bookTitle);
        myLog("Book title (Gutenberg): " + bookTitle);

        // Output dir
        File outDir = new File(ctx.getExternalFilesDir(null), "epub_gutenberg_" + safeBookTitle);
        if (!outDir.exists() && !outDir.mkdirs()) {
            myLogE("Cannot create output dir: " + outDir);
            throw new IllegalStateException("Cannot create " + outDir);
        }

        // Cover
        Bitmap cover = extractCoverBitmap(zip, opf);
        if (cover != null)
            myLogD("Cover extracted OK (Gutenberg)");

        // TOC (toc.xhtml)
        String navHref = findNavHref(opf);
        List<TocEntry> tocEntries = new ArrayList<>();
        if (navHref != null) {
            String navPath = normalizePath(resolve(basePath, navHref));
            byte[] navBytes = zip.get(navPath);
            if (navBytes != null) {
                String navHtml = bytesToStringWithXmlGuess(navBytes);
                tocEntries = parseToc(navHtml, basePath);
            } else {
                myLogW("Nav file not found in zip: " + navPath + " – falling back to empty TOC");
            }
        } else {
            myLogW("No nav item with properties=\"nav\" found in OPF – TOC will be empty");
        }

        // Group TOC entries by file
        Map<String, List<TocEntry>> byFile = new LinkedHashMap<>();
        for (TocEntry t : tocEntries) {
            byFile.computeIfAbsent(t.filePath, k -> new ArrayList<>()).add(t);
        }
        for (List<TocEntry> list : byFile.values()) {
            // keep TOC order (already in nav order) – no need to sort
        }

        // Build chapters from TOC
        int expectedChapters = tocEntries.size();
        // Estimate non-TOC HTML files
        for (Map.Entry<String, String> e : opf.manifestHref.entrySet()) {
            String mt = opf.manifestType.get(e.getKey());
            if (mt != null && (mt.contains("html") || mt.contains("xhtml"))) {
                String p = normalizePath(resolve(basePath, e.getValue()));
                if (!byFile.containsKey(p)) {
                    expectedChapters++;
                }
            }
        }
        int padWidth = Math.max(3, String.valueOf(expectedChapters).length());

        List<File> outFiles = new ArrayList<>();
        Set<String> usedFilePaths = new LinkedHashSet<>();
        int globalChapterIndex = 0; // Global counter for all chapters

        for (Map.Entry<String, List<TocEntry>> e : byFile.entrySet()) {
            String filePath = e.getKey();
            List<TocEntry> entries = e.getValue();
            usedFilePaths.add(filePath);

            byte[] xhtmlBytes = zip.get(filePath);
            if (xhtmlBytes == null) {
                // case-insensitive fallback
                for (String k : zip.keySet()) {
                    if (k.equalsIgnoreCase(filePath)) {
                        xhtmlBytes = zip.get(k);
                        break;
                    }
                }
            }
            if (xhtmlBytes == null) {
                myLogW("TOC file not found in zip: " + filePath);
                continue;
            }

            String html = bytesToStringWithXmlGuess(xhtmlBytes);
            globalChapterIndex = buildChaptersFromSingleFile(html, filePath, entries, outDir, outFiles,
                    globalChapterIndex, padWidth);
        }

        // Any extra XHTML files not referenced in TOC -> zz_* chapter
        addNonTocHtmlAsZzChapters(zip, opf, usedFilePaths, basePath, outDir, outFiles, globalChapterIndex, padWidth);

        myLog("=== EpubGutenbergHelper.extractAll: done; chapters=" + outFiles.size() + " ===");
        return new ExtractResult(bookTitle, outDir, outFiles, cover);
    }

    // ---------- Core helpers ----------

    /**
     * Build chapter files for one XHTML file, using TOC anchors as boundaries.
     * 
     * @return The next global chapter index to use
     */
    private static int buildChaptersFromSingleFile(String html,
            String filePath,
            List<TocEntry> entries,
            File outDir,
            List<File> outFiles,
            int globalChapterIndex,
            int padWidth) throws Exception {
        if (entries == null || entries.isEmpty())
            return globalChapterIndex;

        String lower = html.toLowerCase(Locale.ROOT);

        // First, compute start index for each entry in this file
        class Bound {
            TocEntry entry;
            int start;
        }
        List<Bound> bounds = new ArrayList<>();

        for (TocEntry t : entries) {
            Bound b = new Bound();
            b.entry = t;
            b.start = findFragmentStartIndex(html, lower, t.fragmentId);
            if (b.start < 0) {
                myLogW("Could not locate fragment id=" + t.fragmentId + " in " + filePath);
                continue;
            }
            bounds.add(b);
        }

        if (bounds.isEmpty())
            return globalChapterIndex;

        // Sort in document order
        bounds.sort((a, b) -> Integer.compare(a.start, b.start));

        // Now slice segments between each anchor and the next
        for (int i = 0; i < bounds.size(); i++) {
            Bound b = bounds.get(i);
            int start = b.start;
            int end = (i + 1 < bounds.size()) ? bounds.get(i + 1).start : html.length();

            if (start < 0 || start >= html.length() || end <= start)
                continue;

            String segmentHtml = html.substring(start, end);
            String plain = xhtmlToPlain(segmentHtml);
            plain = ensureParagraphs(plain);
            plain = cleanText(plain);

            String baseTitle = humanChapterTitle(b.entry.title);
            globalChapterIndex++;
            String fileName = makeSafeFilename(baseTitle, globalChapterIndex, false, padWidth);

            File f = new File(outDir, fileName + ".txt");
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            outFiles.add(f);

            myLogD("WROTE (Gutenberg) " + f.getName()
                    + " len=" + plain.length()
                    + " from=" + start + " to=" + end);
        }

        return globalChapterIndex;
    }

    /**
     * Add chapters for XHTML files that are in the manifest but not referenced by
     * TOC (e.g., pg-footer).
     * These are saved with a "zz_" prefix so they appear at the end and are clearly
     * "extra".
     */
    private static void addNonTocHtmlAsZzChapters(Map<String, byte[]> zip,
            OpfInfo opf,
            Set<String> usedFilePaths,
            String basePath,
            File outDir,
            List<File> outFiles,
            int globalChapterIndex,
            int padWidth) throws Exception {
        Set<String> alreadyDone = new LinkedHashSet<>(usedFilePaths);
        for (Map.Entry<String, String> e : opf.manifestHref.entrySet()) {
            String id = e.getKey();
            String href = e.getValue();
            String mt = opf.manifestType.get(id);
            if (mt == null)
                mt = "";

            if (!(mt.contains("html") || mt.contains("xhtml")))
                continue;

            String path = normalizePath(resolve(basePath, href));
            if (alreadyDone.contains(path))
                continue;

            byte[] xhtmlBytes = zip.get(path);
            if (xhtmlBytes == null) {
                for (String k : zip.keySet()) {
                    if (k.equalsIgnoreCase(path)) {
                        xhtmlBytes = zip.get(k);
                        break;
                    }
                }
            }
            if (xhtmlBytes == null) {
                myLogW("Non-TOC HTML file missing: " + path);
                continue;
            }

            String html = bytesToStringWithXmlGuess(xhtmlBytes);
            String plain = xhtmlToPlain(html);
            plain = ensureParagraphs(plain);
            plain = cleanText(plain);

            boolean looksLikeLicense = plain.toUpperCase(Locale.ROOT)
                    .contains("PROJECT GUTENBERG LICENSE");
            String baseTitle = looksLikeLicense ? "zz_Licence" : ("zz_" + basenameNoExt(href));

            globalChapterIndex++;
            String fileName = makeSafeFilename(baseTitle, globalChapterIndex, true, padWidth);
            File f = new File(outDir, fileName + ".txt");
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            outFiles.add(f);

            myLogD("WROTE (Gutenberg extra) " + f.getName()
                    + " len=" + plain.length());
        }
    }

    /**
     * Find the start index in the XHTML string for a given fragment id.
     * Returns the index of the '<' starting the element that owns the id, or -1 if
     * not found.
     */
    private static int findFragmentStartIndex(String html, String lower, String fragmentId) {
        if (fragmentId == null || fragmentId.isEmpty())
            return -1;

        String fragLower = fragmentId.toLowerCase(Locale.ROOT);
        int idx = -1;

        // id="..."
        String p1 = "id=\"" + fragLower + "\"";
        idx = lower.indexOf(p1);
        if (idx < 0) {
            // id='...'
            String p2 = "id='" + fragLower + "'";
            idx = lower.indexOf(p2);
        }
        if (idx < 0) {
            // name="..."
            String p3 = "name=\"" + fragLower + "\"";
            idx = lower.indexOf(p3);
        }
        if (idx < 0) {
            String p4 = "name='" + fragLower + "'";
            idx = lower.indexOf(p4);
        }
        if (idx < 0)
            return -1;

        int tagStart = html.lastIndexOf('<', idx);
        return (tagStart >= 0) ? tagStart : idx;
    }

    // ---------- TOC parsing ----------

    private static List<TocEntry> parseToc(String navHtml, String basePath) {
        List<TocEntry> result = new ArrayList<>();
        try {
            Document doc = Jsoup.parse(navHtml);

            // Try epub:type=toc or role=doc-toc
            Element nav = doc.selectFirst("nav[epub|type=toc], nav[epub\\:type=toc], nav[role=doc-toc]");
            if (nav == null) {
                myLogW("TOC nav not found in toc.xhtml");
                return result;
            }

            // All <a> under that nav; we'll filter out pagelist entries "[12]" etc.
            for (Element a : nav.select("a[href]")) {
                String title = a.text();
                if (title == null)
                    title = "";
                title = title.trim();
                if (title.isEmpty())
                    continue;

                // Skip explicit pagelist (page number) links like "[12]"
                if (title.matches("\\[\\d+\\]"))
                    continue;

                String href = a.attr("href");
                if (href == null || href.isEmpty())
                    continue;

                // Split "file.xhtml#fragment"
                String file = href;
                String frag = null;
                int hash = href.indexOf('#');
                if (hash >= 0) {
                    file = href.substring(0, hash);
                    frag = href.substring(hash + 1);
                }

                if (file == null || file.isEmpty())
                    continue;

                TocEntry te = new TocEntry();
                te.filePath = normalizePath(resolve(basePath, file));
                te.fragmentId = (frag != null && !frag.isEmpty()) ? frag : null;
                te.title = title;

                result.add(te);
            }
        } catch (Exception e) {
            myLogEE(e, "parseToc (Gutenberg)");
        }

        myLogD("Gutenberg TOC entries: " + result.size());
        return result;
    }

    private static String findNavHref(OpfInfo opf) {
        for (Map.Entry<String, String> e : opf.manifestHref.entrySet()) {
            String id = e.getKey();
            String href = e.getValue();
            String props = opf.manifestProps.get(id);
            String mt = opf.manifestType.get(id);
            if (props != null && props.toLowerCase(Locale.ROOT).contains("nav")
                    && mt != null && mt.contains("xhtml")) {
                return href;
            }
        }
        return null;
    }

    // ---------- OPF / ZIP helpers ----------

    private static Map<String, byte[]> readZip(Uri uri, Context ctx) throws Exception {
        Map<String, byte[]> map = new LinkedHashMap<>();
        try (InputStream in = new BufferedInputStream(ctx.getContentResolver().openInputStream(uri));
                ZipInputStream zin = new ZipInputStream(in)) {
            byte[] buf = new byte[8192];
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (!e.isDirectory()) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream(
                            (int) Math.max(0, e.getSize()));
                    int n;
                    while ((n = zin.read(buf)) != -1)
                        bos.write(buf, 0, n);
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
                if (p != null)
                    return p;
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
                } else if (inMetadata &&
                        ("dc:title".equalsIgnoreCase(name) || "title".equalsIgnoreCase(name))) {
                    o.title = text(x).trim();
                } else if ("meta".equalsIgnoreCase(name)) {
                    String nm = attr(x, "name");
                    String prop = attr(x, "property");
                    if ("cover".equalsIgnoreCase(nm) || "cover".equalsIgnoreCase(prop)) {
                        String content = attr(x, "content");
                        if (content != null && !content.isEmpty())
                            o.coverId = content;
                    }
                } else if ("item".equalsIgnoreCase(name)) {
                    String id = attr(x, "id");
                    String href = attr(x, "href");
                    String mt = attr(x, "media-type");
                    String props = attr(x, "properties");
                    if (id != null && href != null) {
                        o.manifestHref.put(id, href);
                        o.manifestType.put(id, mt != null ? mt : "");
                        if (props != null)
                            o.manifestProps.put(id, props);
                    }
                } else if ("itemref".equalsIgnoreCase(name)) {
                    String idref = attr(x, "idref");
                    if (idref != null)
                        o.spine.add(idref);
                }
            } else if (t == XmlPullParser.END_TAG && "metadata".equalsIgnoreCase(x.getName())) {
                inMetadata = false;
            }
        }
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
        if (v == null)
            v = x.getAttributeValue("", name);
        if (v == null && x.getAttributeCount() > 0) {
            for (int i = 0; i < x.getAttributeCount(); i++) {
                if (name.equals(x.getAttributeName(i)))
                    return x.getAttributeValue(i);
            }
        }
        return v;
    }

    private static String text(XmlPullParser x) throws Exception {
        StringBuilder sb = new StringBuilder();
        int t;
        while ((t = x.next()) != XmlPullParser.END_DOCUMENT) {
            if (t == XmlPullParser.TEXT)
                sb.append(x.getText());
            else if (t == XmlPullParser.END_TAG)
                break;
        }
        return sb.toString();
    }

    private static String opfBase(String opfPath) {
        if (opfPath == null)
            return "";
        int i = opfPath.lastIndexOf('/');
        return i >= 0 ? opfPath.substring(0, i + 1) : "";
    }

    private static String resolve(String base, String href) {
        if (href == null)
            return null;
        if (href.startsWith("/"))
            return href.substring(1);
        if (base == null || base.isEmpty())
            return normalizePath(href);
        return normalizePath(base + href);
    }

    private static String normalizePath(String p) {
        if (p == null)
            return null;
        String[] parts = p.split("/");
        Deque<String> stack = new ArrayDeque<>();
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part))
                continue;
            if ("..".equals(part)) {
                if (!stack.isEmpty())
                    stack.removeLast();
            } else {
                stack.addLast(part);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String s : stack) {
            if (sb.length() > 0)
                sb.append('/');
            sb.append(s);
        }
        return sb.toString();
    }

    // ---------- Cover & encoding ----------

    private static Bitmap extractCoverBitmap(Map<String, byte[]> zip, OpfInfo opf) {
        String basePath = opfBase(opf.opfPath);
        String coverHref = null;

        if (opf.coverId != null) {
            coverHref = opf.manifestHref.get(opf.coverId);
        }
        if (coverHref == null) {
            // fallback: first image in manifest
            for (Map.Entry<String, String> e : opf.manifestType.entrySet()) {
                String mt = e.getValue();
                if (mt != null && mt.startsWith("image/")) {
                    coverHref = opf.manifestHref.get(e.getKey());
                    if (coverHref != null)
                        break;
                }
            }
        }
        if (coverHref == null)
            return null;

        String resolved = normalizePath(resolve(basePath, coverHref));
        byte[] imgBytes = zip.get(resolved);
        if (imgBytes == null) {
            for (String k : zip.keySet()) {
                if (k.equalsIgnoreCase(resolved)) {
                    imgBytes = zip.get(k);
                    break;
                }
            }
        }
        if (imgBytes == null)
            return null;
        return BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.length);
    }

    private static String bytesToStringWithXmlGuess(byte[] data) {
        if (data == null)
            return "";

        String sniff = new String(data, 0, Math.min(data.length, 256), Charset.forName("ISO-8859-1"));
        String enc = null;
        int i = sniff.indexOf("encoding=");
        if (i >= 0) {
            int q1 = sniff.indexOf('"', i);
            int q2 = sniff.indexOf('"', q1 + 1);
            int a1 = sniff.indexOf('\'', i);
            int a2 = sniff.indexOf('\'', a1 + 1);
            if (q1 > 0 && q2 > q1)
                enc = sniff.substring(q1 + 1, q2);
            else if (a1 > 0 && a2 > a1)
                enc = sniff.substring(a1 + 1, a2);
        }
        Charset cs;
        try {
            cs = (enc != null) ? Charset.forName(enc) : Charset.forName("UTF-8");
        } catch (Exception e) {
            cs = Charset.forName("UTF-8");
        }
        return new String(data, cs);
    }

    // ---------- HTML → text ----------

    private static String xhtmlToPlain(String xhtml) {
        if (xhtml == null)
            return "";
        Document doc = Jsoup.parse(xhtml);
        doc.outputSettings(new Document.OutputSettings().prettyPrint(false));

        final StringBuilder out = new StringBuilder(xhtml.length() / 2);

        NodeTraversor.traverse(new NodeVisitor() {
            boolean atLineStart = true;

            @Override
            public void head(org.jsoup.nodes.Node node, int depth) {
                if (node instanceof org.jsoup.nodes.TextNode) {
                    String text = ((org.jsoup.nodes.TextNode) node).text();
                    if (!text.trim().isEmpty()) {
                        if (!atLineStart)
                            out.append(' ');
                        out.append(text.trim());
                        atLineStart = false;
                    }
                    return;
                }

                String name = node.nodeName();
                if ("li".equalsIgnoreCase(name)) {
                    newLine(out);
                    out.append("• ");
                    atLineStart = false;
                } else if ("br".equalsIgnoreCase(name)) {
                    newLine(out);
                    atLineStart = true;
                } else if (name.matches("(?i)h[1-6]")) {
                    ensureBlankLine(out);
                    atLineStart = true;
                }
            }

            @Override
            public void tail(org.jsoup.nodes.Node node, int depth) {
                String name = node.nodeName();
                if (isBlock(name)) {
                    ensureBlankLine(out);
                }
            }

            private boolean isBlock(String name) {
                String n = name.toLowerCase(Locale.ROOT);
                return n.equals("p") || n.equals("div") || n.equals("section") || n.equals("article")
                        || n.equals("blockquote") || n.equals("ul") || n.equals("ol")
                        || n.equals("table") || n.equals("tr") || n.equals("thead") || n.equals("tbody")
                        || n.matches("h[1-6]");
            }

            private void newLine(StringBuilder b) {
                int len = b.length();
                if (len == 0 || b.charAt(len - 1) == '\n')
                    return;
                b.append('\n');
            }

            private void ensureBlankLine(StringBuilder b) {
                int len = b.length();
                if (len == 0)
                    return;
                if (len >= 2 && b.charAt(len - 1) == '\n' && b.charAt(len - 2) == '\n')
                    return;
                if (len >= 1 && b.charAt(len - 1) != '\n')
                    b.append('\n');
                b.append('\n');
            }
        }, doc.body() != null ? doc.body() : doc);

        String txt = out.toString();
        txt = txt.replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return txt;
    }

    private static String ensureParagraphs(String s) {
        if (s == null)
            return "";
        int nl = 0;
        for (int i = 0; i < s.length(); i++)
            if (s.charAt(i) == '\n')
                nl++;
        if (nl >= 2)
            return s;

        String t = s.replace("\r", "").replace('\u00A0', ' ')
                .replaceAll("[ \\t]{2,}", " ").trim();

        // crude sentence-based paragraphing
        t = t.replaceAll(
                "(?<=[.!?…])[ ]+(?=[\"“‘'\\(\\[]?[A-ZÀ-ÖØ-Þ0-9])",
                "\n\n");
        t = t.replaceAll("[ ]*\\*\\*\\*[ ]*", "\n\n***\n\n");
        return t;
    }

    private static String cleanText(String s) {
        if (s == null)
            return "";
        String t = s.replace('\u00A0', ' ');
        t = t.replace("\r\n", "\n").replace("\r", "\n");
        t = t.replaceAll("[\\t ]{2,}", " ");
        t = t.replaceAll("\n{3,}", "\n\n");
        return t.trim();
    }

    // ---------- Naming helpers ----------

    private static String basenameNoExt(String href) {
        if (href == null)
            return "chapter";
        int slash = href.lastIndexOf('/');
        String name = (slash >= 0) ? href.substring(slash + 1) : href;
        int dot = name.lastIndexOf('.');
        return (dot >= 0) ? name.substring(0, dot) : name;
    }

    private static String humanChapterTitle(String navTitle) {
        if (navTitle == null)
            return "chapter";
        String t = navTitle.trim();
        // If nav title is pure roman numeral, prefix with "chapter "
        if (t.matches("(?i)^[IVXLCDM]+$")) {
            return "chapter " + t;
        }
        return t;
    }

    private static String safeName(String s) {
        if (s == null)
            return "untitled";
        String out = s.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (out.isEmpty())
            out = "untitled";
        if (out.length() > 60)
            out = out.substring(0, 60).trim();
        return out;
    }

    private static String makeSafeFilename(String baseTitle, int index, boolean isZz, int padWidth) {
        String cleaned = baseTitle;
        // Remove illegal filename chars
        cleaned = cleaned.replaceAll("[\\\\/:*?\"<>|]", " ");
        // Collapse whitespace
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty())
            cleaned = "chapter";
        if (cleaned.length() > MAX_FILENAME_CHARS) {
            cleaned = cleaned.substring(0, MAX_FILENAME_CHARS).trim();
        }

        // Always add numeric prefix for proper ordering, even for zz_ files
        String prefix = String.format(Locale.US, "%0" + padWidth + "d_", Math.max(1, index));
        return prefix + cleaned;
    }
}
