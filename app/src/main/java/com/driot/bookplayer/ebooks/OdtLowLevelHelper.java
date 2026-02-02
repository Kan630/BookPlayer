package com.driot.bookplayer.ebooks;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ODT low-level extractor:
 *  - One chapter per <text:h> (heading).
 *  - Fallback: single "Full Document" chapter if no headings.
 *  - Cover: largest image found in /Pictures.
 *
 * Heuristic-based, not a full ODF parser.
 */
public final class OdtLowLevelHelper {
    private OdtLowLevelHelper() {}

    private static final String TEXT_NS = "urn:oasis:names:tc:opendocument:xmlns:text:1.0";

    // ---------------- Types ----------------

    public static final class ExtractResult {
        public final String bookTitle;
        public final java.io.File outDir;
        public final List<java.io.File> chapterFiles;
        public final Bitmap coverBitmap;
        ExtractResult(String t, java.io.File d, List<java.io.File> f, Bitmap c) {
            bookTitle = t; outDir = d; chapterFiles = f; coverBitmap = c;
        }
    }

    private static final class Chapter {
        String title;
        String text;
    }

    // ---------------- Public API ----------------

    public static ExtractResult extractAll(Context ctx, Uri odtUri) throws Exception {
        myLog("=== ODT extractAll: begin ===");

        // 1) Read whole ODT into memory
        byte[] odtBytes = readAllBytes(ctx, odtUri);

        // 2) Walk ZIP entries: get content.xml + largest picture
        String contentXml = null;
        Bitmap cover = null;
        int bestBytes = -1;

        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(odtBytes));
        ZipEntry e;
        while ((e = zis.getNextEntry()) != null) {
            if (e.isDirectory()) { zis.closeEntry(); continue; }
            String name = e.getName();

            if ("content.xml".equals(name)) {
                contentXml = readEntryAsText(zis);
                myLogD("content.xml size=" + (contentXml != null ? contentXml.length() : 0));
            } else if (name.startsWith("Pictures/")) {
                byte[] data = readEntryAsBytes(zis);
                if (data != null && data.length > bestBytes) {
                    Bitmap candidate = BitmapFactory.decodeByteArray(data, 0, data.length);
                    if (candidate != null) {
                        cover = candidate;
                        bestBytes = data.length;
                        myLogD("Cover candidate (largest so far): " + name + " (" + data.length + " bytes)");
                    }
                }
            }
            zis.closeEntry();
        }
        zis.close();

        if (contentXml == null) throw new IllegalStateException("No content.xml in ODT");

        // 3) Parse chapters (namespace-aware; proper \n handling)
        List<Chapter> chapters = parseChapters(contentXml);
        myLog("Chapters parsed: " + chapters.size());

        // 4) Title heuristic: first heading text, else "untitled"
        String bookTitle = (chapters.isEmpty() || chapters.get(0).title == null || chapters.get(0).title.trim().isEmpty())
                ? "untitled"
                : chapters.get(0).title.trim();

        // 5) Write out chapters (ALWAYS write; even if text is empty)
        java.io.File outDir = new java.io.File(ctx.getExternalFilesDir(null), "odt_" + safe(bookTitle));
        if (!outDir.exists() && !outDir.mkdirs()) throw new IllegalStateException("Cannot create " + outDir);

        List<java.io.File> outFiles = new ArrayList<>();
        int idx = 0;
        for (Chapter ch : chapters) {
            if (ch == null) continue;
            String text = (ch.text == null) ? "" : clean(ch.text);
            String title = (ch.title != null && !ch.title.trim().isEmpty())
                    ? ch.title.trim()
                    : deriveTitleFromText(text);
            String fname = String.format(Locale.US, "%03d_%s.txt", ++idx, safeSlug(title));
            java.io.File f = new java.io.File(outDir, fname);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(text.getBytes(StandardCharsets.UTF_8));
            }
            outFiles.add(f);
            myLogD("Wrote chapter: " + f.getName() + " (len=" + text.length() + ")");
        }

        myLog("=== ODT extractAll: done; chapters=" + outFiles.size() + " ===");
        return new ExtractResult(bookTitle, outDir, outFiles, cover);
    }

    // ---------------- Parse content.xml ----------------

    private static List<Chapter> parseChapters(String xml) throws Exception {
        List<Chapter> out = new ArrayList<>();

        XmlPullParserFactory f = XmlPullParserFactory.newInstance();
        f.setNamespaceAware(true);
        XmlPullParser x = f.newPullParser();
        x.setInput(new StringReader(xml));

        Chapter current = null;
        StringBuilder buf = null;        // accumulates body text for current chapter
        boolean inHeading = false;
        StringBuilder titleBuf = null;   // accumulates full heading text with inline spans

        int t;
        while ((t = x.next()) != XmlPullParser.END_DOCUMENT) {
            if (t == XmlPullParser.START_TAG) {
                String ns = x.getNamespace();
                String name = x.getName();

                if (TEXT_NS.equals(ns) && "h".equals(name)) {
                    // Save previous chapter (even if empty)
                    if (current != null) {
                        current.text = (buf == null) ? "" : buf.toString();
                        out.add(current);
                    }
                    current = new Chapter();
                    buf = new StringBuilder(8192);
                    inHeading = true;
                    titleBuf = new StringBuilder(256);

                } else if (TEXT_NS.equals(ns) && "p".equals(name)) {
                    if (buf == null) buf = new StringBuilder(8192);
                    ensureBlankLine(buf);

                } else if (TEXT_NS.equals(ns) && "line-break".equals(name)) {
                    if (buf == null) buf = new StringBuilder(8192);
                    buf.append('\n');

                } else if (TEXT_NS.equals(ns) && "s".equals(name)) {
                    // <text:s text:c="N"/> => N spaces (default 1)
                    String c = x.getAttributeValue(TEXT_NS, "c");
                    int n = parseIntSafely(c, 1);
                    if (buf == null) buf = new StringBuilder(8192);
                    for (int i = 0; i < Math.max(1, n); i++) buf.append(' ');

                } else if (TEXT_NS.equals(ns) && "tab".equals(name)) {
                    if (buf == null) buf = new StringBuilder(8192);
                    buf.append('\t');
                }

            } else if (t == XmlPullParser.TEXT) {
                String s = x.getText();
                if (s != null && !s.isEmpty()) {
                    if (inHeading && titleBuf != null) {
                        titleBuf.append(s);
                    } else {
                        if (buf == null) buf = new StringBuilder(8192);
                        buf.append(s);
                    }
                }

            } else if (t == XmlPullParser.END_TAG) {
                String ns = x.getNamespace();
                String name = x.getName();

                if (TEXT_NS.equals(ns) && "h".equals(name)) {
                    inHeading = false;
                    if (current != null) {
                        String ttxt = (titleBuf == null) ? null : titleBuf.toString().replaceAll("\\s+"," ").trim();
                        if (ttxt != null && !ttxt.isEmpty()) current.title = ttxt;
                    }
                    titleBuf = null;

                } else if (TEXT_NS.equals(ns) && "p".equals(name)) {
                    ensureBlankLine(buf);

                } else if (TEXT_NS.equals(ns) && "line-break".equals(name)) {
                    if (buf == null) buf = new StringBuilder(8192);
                    buf.append('\n');
                }
            }
        }

        // Flush last heading-based chapter (even if empty)
        if (current != null) {
            current.text = (buf == null) ? "" : buf.toString();
            out.add(current);
        }

        // Fallback: no headings → single chapter from all paragraphs.
        if (out.isEmpty()) {
            String fullText;
            try {
                fullText = collectAllText(xml);
            } catch (Throwable ignore) {
                fullText = "";
            }
            Chapter c = new Chapter();
            c.title = "Full Document";
            c.text  = (fullText == null) ? "" : fullText;
            out.add(c);
            myLogW("No headings found: fallback to single full document chapter (len=" +
                    (c.text == null ? 0 : c.text.length()) + ")");
        }

        return out;
    }

    /** Collects all text from <text:p> (plus inline breaks/spaces) into one big string (fallback). */
    private static String collectAllText(String xml) throws Exception {
        XmlPullParserFactory f = XmlPullParserFactory.newInstance();
        f.setNamespaceAware(true);
        XmlPullParser x = f.newPullParser();
        x.setInput(new StringReader(xml));

        StringBuilder buf = new StringBuilder(32 * 1024);

        int t;
        while ((t = x.next()) != XmlPullParser.END_DOCUMENT) {
            if (t == XmlPullParser.START_TAG) {
                String ns = x.getNamespace();
                String name = x.getName();

                if (TEXT_NS.equals(ns) && "p".equals(name)) {
                    ensureBlankLine(buf);
                } else if (TEXT_NS.equals(ns) && "line-break".equals(name)) {
                    buf.append('\n');
                } else if (TEXT_NS.equals(ns) && "s".equals(name)) {
                    String c = x.getAttributeValue(TEXT_NS, "c");
                    int n = parseIntSafely(c, 1);
                    for (int i = 0; i < Math.max(1, n); i++) buf.append(' ');
                } else if (TEXT_NS.equals(ns) && "tab".equals(name)) {
                    buf.append('\t');
                }

            } else if (t == XmlPullParser.TEXT) {
                String s = x.getText();
                if (s != null && !s.isEmpty()) {
                    buf.append(s);
                }

            } else if (t == XmlPullParser.END_TAG) {
                String ns = x.getNamespace();
                String name = x.getName();

                if (TEXT_NS.equals(ns) && "p".equals(name)) {
                    ensureBlankLine(buf);
                } else if (TEXT_NS.equals(ns) && "line-break".equals(name)) {
                    buf.append('\n');
                }
            }
        }
        return buf.toString();
    }

    // ---------------- IO helpers ----------------

    private static byte[] readAllBytes(Context ctx, Uri uri) throws Exception {
        try (InputStream in0 = ctx.getContentResolver().openInputStream(uri);
             BufferedInputStream in = new BufferedInputStream(in0)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(256 * 1024);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    private static String readEntryAsText(ZipInputStream zis) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(64 * 1024);
        byte[] buf = new byte[8192];
        int n;
        while ((n = zis.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toString(StandardCharsets.UTF_8.name());
    }

    private static byte[] readEntryAsBytes(ZipInputStream zis) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(32 * 1024);
        byte[] buf = new byte[8192];
        int n;
        while ((n = zis.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    // ---------------- Small helpers ----------------

    private static void ensureBlankLine(StringBuilder b) {
        int len = b.length();
        if (len == 0) return;
        if (b.charAt(len - 1) != '\n') b.append('\n');
        b.append('\n');
    }

    private static String deriveTitleFromText(String text) {
        if (text == null) return "chapter";
        String[] lines = text.replace("\r","").split("\n");
        for (String line : lines) {
            String t = line.trim();
            if (!t.isEmpty()) {
                return t.length() > 60 ? t.substring(0, 60) : t;
            }
        }
        return "chapter";
    }

    private static String clean(String s) {
        if (s == null) return "";
        return s.replace('\u00A0',' ')
                .replace("\r\n","\n").replace("\r","\n")
                .replaceAll("[\\t ]{2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
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

    private static int parseIntSafely(String s, int def) {
        try { return (s == null) ? def : Integer.parseInt(s); }
        catch (Throwable ignore) { return def; }
    }

}
