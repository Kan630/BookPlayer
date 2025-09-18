package com.driot.bookplayer.helpers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.driot.bookplayer.utils.KanLogger;

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
 *  - Fallback: whole doc as one chapter if no headings.
 *  - Cover: first image found in /Pictures.
 *
 * Heuristic-based, not a full ODF parser.
 */
public final class OdtLowLevelHelper {
    private OdtLowLevelHelper() {}

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
        myLogI("=== ODT extractAll: begin ===");

        // 1) Read whole ODT into memory
        byte[] odtBytes = readAllBytes(ctx, odtUri);

        // 2) Walk ZIP entries
        String contentXml = null;
        Bitmap cover = null;

        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(odtBytes));
        ZipEntry e;
        while ((e = zis.getNextEntry()) != null) {
            if (e.isDirectory()) continue;
            String name = e.getName();

            if ("content.xml".equals(name)) {
                contentXml = readEntryAsText(zis);
                myLogD("content.xml size=" + (contentXml != null ? contentXml.length() : 0));
            } else if (name.startsWith("Pictures/") && cover == null) {
                byte[] data = readEntryAsBytes(zis);
                cover = BitmapFactory.decodeByteArray(data, 0, data.length);
                if (cover != null) {
                    myLogD("Cover candidate: " + name + " (" + data.length + " bytes)");
                }
            }
            zis.closeEntry();
        }
        zis.close();

        if (contentXml == null) throw new IllegalStateException("No content.xml in ODT");

        // 3) Parse chapters
        List<Chapter> chapters = parseChapters(contentXml);
        myLogI("Chapters parsed: " + chapters.size());

        // 4) Title heuristic: first heading, else file name
        String bookTitle = (chapters.isEmpty() || chapters.get(0).title == null)
                ? "untitled"
                : chapters.get(0).title;

        // 5) Write out chapters
        java.io.File outDir = new java.io.File(ctx.getExternalFilesDir(null), "odt_" + safe(bookTitle));
        if (!outDir.exists() && !outDir.mkdirs()) throw new IllegalStateException("Cannot create " + outDir);

        List<java.io.File> outFiles = new ArrayList<>();
        int idx = 0;
        for (Chapter ch : chapters) {
            if (ch == null || ch.text == null || ch.text.trim().isEmpty()) continue;
            String title = (ch.title != null && !ch.title.trim().isEmpty()) ? ch.title : deriveTitleFromText(ch.text);
            String fname = String.format(Locale.US, "%03d_%s.txt", ++idx, safeSlug(title));
            java.io.File f = new java.io.File(outDir, fname);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(clean(ch.text).getBytes(StandardCharsets.UTF_8));
            }
            outFiles.add(f);
            myLogD("Wrote chapter: " + f.getName());
        }

        myLogI("=== ODT extractAll: done; chapters=" + outFiles.size() + " ===");
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
        StringBuilder buf = null;
        boolean inHeading = false;

        int t;
        while ((t = x.next()) != XmlPullParser.END_DOCUMENT) {
            if (t == XmlPullParser.START_TAG) {
                String tag = x.getName();
                if ("text:h".equals(tag)) {
                    // Save previous chapter
                    if (current != null && buf != null) {
                        current.text = buf.toString();
                        out.add(current);
                    }
                    current = new Chapter();
                    buf = new StringBuilder(8192);
                    inHeading = true;
                } else if ("text:p".equals(tag)) {
                    if (buf == null) buf = new StringBuilder(8192);
                    ensureBlankLine(buf);
                }
            } else if (t == XmlPullParser.TEXT) {
                String s = x.getText();
                if (s != null && !s.trim().isEmpty()) {
                    if (inHeading && current != null && current.title == null) {
                        current.title = s.trim();
                    } else if (buf != null) {
                        buf.append(s);
                    }
                }
            } else if (t == XmlPullParser.END_TAG) {
                String tag = x.getName();
                if ("text:h".equals(tag)) {
                    inHeading = false;
                } else if ("text:p".equals(tag)) {
                    ensureBlankLine(buf);
                }
            }
        }

        // flush last
        if (current != null && buf != null) {
            current.text = buf.toString();
            out.add(current);
        }

        // fallback: if no chapters, treat as one
        if (out.isEmpty() && buf != null && buf.length() > 0) {
            Chapter c = new Chapter();
            c.title = "Full Document";
            c.text = buf.toString();
            out.add(c);
        }

        return out;
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
        String[] lines = text.split("\n");
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

    // ---------------- Logging ----------------

    private static final String TAG = "OdtLowLevelHelper";
    private static void myLogD(String s) { KanLogger.myLogD(TAG, s); }
    private static void myLogI(String s) { KanLogger.myLogI(TAG, s); }
    private static void myLogW(String s) { KanLogger.myLogW(TAG, s); }
    @SuppressWarnings("SameParameterValue")
    private static void myLogEE(Throwable t, String s) { KanLogger.myLogEE(t, TAG, s); }
}
