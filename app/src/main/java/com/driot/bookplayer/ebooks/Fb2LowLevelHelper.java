package com.driot.bookplayer.ebooks;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.utils.Tonio;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * FB2 low-level extractor:
 * - One chapter file per top-level <section> in the main <body>.
 * - Paragraphs preserved (blank lines between blocks).
 * - Cover image extracted from
 * <description><title-info><coverpage><image xlink:href="#id">
 * and decoded from corresponding <binary id="..."> base64.
 *
 * No external libraries required.
 */
public final class Fb2LowLevelHelper {
    private Fb2LowLevelHelper() {
    }

    // ---------------- Types ----------------

    public static final class ExtractResult {
        public final String bookTitle;
        public final java.io.File outDir;
        public final List<java.io.File> chapterFiles;
        public final Bitmap coverBitmap;
        public final Map<String, String> trackTitles = new LinkedHashMap<>();

        ExtractResult(String t, java.io.File d, List<java.io.File> f, Bitmap c) {
            bookTitle = t;
            outDir = d;
            chapterFiles = f;
            coverBitmap = c;
        }
    }

    public static final class Meta {
        public String title; // <description><title-info><book-title>
        public String coverImageId; // id without '#' from coverpage image (original case)
        // Store binaries keyed by LOWERCASE id for robust lookup
        public final java.util.Map<String, byte[]> binaries = new LinkedHashMap<>();
        public final java.util.Map<String, String> binaryTypes = new LinkedHashMap<>(); // LOWERCASE id -> content-type
    }

    // ---------------- Public API ----------------

    public static ExtractResult extractAll(Context ctx, Uri fb2Uri) throws Exception {
        myLog("=== FB2 extractAll: begin ===");

        // Read whole FB2 as UTF-8 text
        String xml = readAllText(ctx, fb2Uri);
        myLogD("FB2 size: " + Tonio.getReadableSize(xml.length()) + " chars");

        // Pass 1: metadata + binaries (cover image decoding)
        Meta meta = parseMetaAndBinaries(xml);
        String bookTitle = (meta.title != null && !meta.title.trim().isEmpty()) ? meta.title.trim() : "untitled";
        myLog("Book title: " + bookTitle);

        // Resolve cover bytes
        byte[] coverBytes = null;
        String chosenCoverId = null;

        if (meta.coverImageId != null) {
            String key = meta.coverImageId.toLowerCase(Locale.ROOT);
            coverBytes = meta.binaries.get(key);
            if (coverBytes != null) {
                chosenCoverId = key;
            } else {
                myLogW("Cover id present but no matching <binary>: " + meta.coverImageId);
            }
        }

        // Fallback: choose the first image/* binary if no explicit cover
        if (coverBytes == null && !meta.binaryTypes.isEmpty()) {
            // sort ids alphabetically for deterministic selection
            List<String> ids = new ArrayList<>(meta.binaryTypes.keySet());
            ids.sort(String::compareToIgnoreCase);

            for (String id : ids) {
                String type = meta.binaryTypes.get(id);
                if (type != null && type.toLowerCase(Locale.ROOT).startsWith("image/")) {
                    coverBytes = meta.binaries.get(id);
                    chosenCoverId = id;
                    myLogW("No <coverpage>; using first image binary id=" + id + " (" + type + ")");
                    break;
                }
            }

            // Last resort: any first binary (sorted by id)
            if (coverBytes == null && !ids.isEmpty()) {
                String id = ids.get(0);
                coverBytes = meta.binaries.get(id);
                chosenCoverId = id;
                myLogW("No image/* binaries; using first binary id=" + id);
            }
        }

        Bitmap cover = null;
        if (coverBytes != null) {
            cover = decodeDownsampled(coverBytes, /* maxDim */ 2048); // safe decode
            if (cover != null) {
                myLog("Cover decoded OK (" + Tonio.getReadableSize(coverBytes.length) + " bytes)");
            } else {
                myLogW("Cover decode failed for id=" + chosenCoverId);
            }
        }

        // Pass 2: chapters (one file per top-level <section> in main <body>)
        List<Chapter> chapters = parseChapters(xml);
        myLog("Chapters found (top-level sections): " + chapters.size());

        // Write out
        File outDir = new File(ctx.getExternalFilesDir(null),
                "fb2_" + FileHelper.sanitizeFilename(bookTitle));
        if (!outDir.exists() && !outDir.mkdirs())
            throw new IllegalStateException("Cannot create " + outDir);

        List<File> outFiles = new ArrayList<>();
        ExtractResult result = new ExtractResult(bookTitle, outDir, outFiles, cover);
        int idx = 0;
        for (Chapter ch : chapters) {
            if (ch == null)
                continue;
            String title = (ch.title != null && !ch.title.trim().isEmpty())
                    ? ch.title.trim()
                    : deriveTitleFromText(ch.text);
            String fname = String.format(Locale.US, "%03d_%s.txt", ++idx, FileHelper.sanitizeFilename(title));
            File f = new File(outDir, fname);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(clean(ch.text).getBytes(StandardCharsets.UTF_8));
            }
            outFiles.add(f);
            result.trackTitles.put(f.getName(), title);
            myLogD(String.format(Locale.US, "WROTE [%s] len=%d", f.getName(), ch.text.length()));
        }

        myLog("=== FB2 extractAll: done; chapters=" + outFiles.size() + " ===");
        return result;
    }

    // ---------------- Parsing: meta + binaries ----------------

    public static Meta parseMetaAndBinaries(String xml) throws Exception {
        Meta meta = new Meta();

        XmlPullParserFactory f = XmlPullParserFactory.newInstance();
        f.setNamespaceAware(true);
        XmlPullParser x = f.newPullParser();
        x.setInput(new StringReader(xml));

        final String XLINK = "http://www.w3.org/1999/xlink";

        boolean inDescription = false;
        boolean inTitleInfo = false;
        boolean inCoverpage = false; // NEW: ensure image is from <coverpage>
        boolean inBookTitle = false;
        boolean inBinary = false;

        String currentBinaryId = null;
        String currentBinaryType = null;
        StringBuilder binBuf = null;

        int t;
        while ((t = x.next()) != XmlPullParser.END_DOCUMENT) {
            if (t == XmlPullParser.START_TAG) {
                String tag = x.getName();

                // Description / title-info / coverpage / book-title
                if ("description".equalsIgnoreCase(tag)) {
                    inDescription = true;
                } else if (inDescription && "title-info".equalsIgnoreCase(tag)) {
                    inTitleInfo = true;
                } else if (inTitleInfo && "coverpage".equalsIgnoreCase(tag)) {
                    inCoverpage = true; // begin cover scope
                } else if (inTitleInfo && "book-title".equalsIgnoreCase(tag)) {
                    inBookTitle = true;
                } else if (inCoverpage && "image".equalsIgnoreCase(tag)) {
                    // Only treat image under coverpage as the cover
                    String href = attrNs(x, XLINK, "href");
                    if (href == null)
                        href = attr(x, "href");
                    if (href != null && href.startsWith("#"))
                        href = href.substring(1);
                    if (href != null && !href.isEmpty()) {
                        meta.coverImageId = href;
                        myLogD("Cover image id: " + href);
                    }
                }

                // Binary (base64)
                if ("binary".equalsIgnoreCase(tag)) {
                    inBinary = true;
                    currentBinaryId = attr(x, "id");
                    currentBinaryType = attr(x, "content-type");
                    binBuf = new StringBuilder(64 * 1024);
                }

            } else if (t == XmlPullParser.TEXT) {
                if (inBookTitle) {
                    String s = x.getText();
                    if (s != null) {
                        meta.title = (meta.title == null) ? s : (meta.title + s);
                    }
                } else if (inBinary && binBuf != null) {
                    String s = x.getText();
                    if (s != null)
                        binBuf.append(s);
                }

            } else if (t == XmlPullParser.END_TAG) {
                String tag = x.getName();

                if ("coverpage".equalsIgnoreCase(tag)) {
                    inCoverpage = false;
                } else if ("book-title".equalsIgnoreCase(tag)) {
                    inBookTitle = false;
                } else if ("title-info".equalsIgnoreCase(tag)) {
                    inTitleInfo = false;
                } else if ("description".equalsIgnoreCase(tag)) {
                    inDescription = false;
                } else if ("binary".equalsIgnoreCase(tag)) {
                    inBinary = false;
                    if (currentBinaryId != null && binBuf != null) {
                        try {
                            String base64 = binBuf.toString().replaceAll("\\s+", ""); // strip whitespace for safety
                            byte[] data = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                            String key = currentBinaryId.toLowerCase(Locale.ROOT);
                            meta.binaries.put(key, data);
                            if (currentBinaryType != null) {
                                meta.binaryTypes.put(key, currentBinaryType);
                            }
                            myLogD("Captured <binary> id=" + currentBinaryId + " bytes="
                                    + Tonio.getReadableSize(data.length) + " (" + currentBinaryType + ")");
                        } catch (Throwable e) {
                            myLogEE(e, "decode <binary> id=" + currentBinaryId);
                        }
                    }
                    currentBinaryId = null;
                    currentBinaryType = null;
                    binBuf = null;
                }
            }
        }

        if (meta.title != null)
            meta.title = meta.title.trim();
        return meta;
    }

    // ---------------- Parsing: chapters ----------------

    private static final class Chapter {
        String title;
        String text;
    }

    /** Parse chapters as top-level <section> under the first main <body>. */
    private static List<Chapter> parseChapters(String xml) throws Exception {
        List<Chapter> out = new ArrayList<>();

        XmlPullParserFactory f = XmlPullParserFactory.newInstance();
        f.setNamespaceAware(true);
        XmlPullParser x = f.newPullParser();
        x.setInput(new StringReader(xml));

        boolean inBody = false;
        boolean chosenBody = false; // we've selected the main body
        int bodyDepth = 0; // depth from the chosen <body>
        int t;

        while ((t = x.next()) != XmlPullParser.END_DOCUMENT) {
            if (t == XmlPullParser.START_TAG) {
                String tag = x.getName();

                if ("body".equalsIgnoreCase(tag)) {
                    // Choose the first <body> that is likely the main text
                    if (!chosenBody) {
                        String name = attr(x, "name");
                        if (isMainBodyName(name)) {
                            inBody = true;
                            chosenBody = true;
                            bodyDepth = 0;
                            myLogD("Selected main <body> (name=" + name + ")");
                        }
                    } else if (inBody) {
                        bodyDepth++;
                    }
                } else if (inBody && "section".equalsIgnoreCase(tag) && bodyDepth == 0) {
                    // top-level section within chosen body -> chapter
                    Chapter ch = parseSection(x);
                    if (ch != null && ch.text != null && ch.text.trim().length() > 0) {
                        out.add(ch);
                    }
                }

            } else if (t == XmlPullParser.END_TAG) {
                String tag = x.getName();
                if ("body".equalsIgnoreCase(tag) && chosenBody) {
                    if (!inBody) {
                        /* noop */ } else if (bodyDepth == 0) {
                        inBody = false;
                    } else
                        bodyDepth--;
                }
            }
        }

        return out;
    }

    /**
     * Parse one <section> including nested sections (concatenate content). Assumes
     * parser is positioned on START_TAG section.
     */
    private static Chapter parseSection(XmlPullParser x) throws Exception {
        Chapter ch = new Chapter();
        StringBuilder text = new StringBuilder(8 * 1024);

        int depth = 0; // depth relative to this <section>
        boolean inTitle = false;
        boolean inTitleP = false;
        StringBuilder titleBuf = null;

        int t;
        while ((t = x.next()) != XmlPullParser.END_DOCUMENT) {
            if (t == XmlPullParser.START_TAG) {
                String tag = x.getName();

                if ("section".equalsIgnoreCase(tag)) {
                    depth++;
                    if (depth > 0) {
                        // Nested section: parse it recursively and append its title + text
                        Chapter sub = parseSection(x);
                        if (sub != null && sub.text != null && sub.text.trim().length() > 0) {
                            if (sub.title != null && !sub.title.trim().isEmpty()) {
                                ensureBlankLine(text);
                                text.append(sub.title.trim()).append('\n');
                            }
                            ensureBlankLine(text);
                            text.append(sub.text.trim());
                        }
                        continue; // parser has consumed nested section
                    }
                } else if ("title".equalsIgnoreCase(tag) && depth == 0) {
                    inTitle = true;
                    titleBuf = new StringBuilder(256);
                } else if (inTitle && "p".equalsIgnoreCase(tag)) {
                    inTitleP = true;
                } else if (isParagraphish(tag)) {
                    ensureBlankLine(text);
                } else if ("empty-line".equalsIgnoreCase(tag)) {
                    text.append('\n'); // explicit blank
                } else if (isLineBreak(tag)) {
                    text.append('\n');
                }

            } else if (t == XmlPullParser.TEXT) {
                String s = x.getText();
                if (s != null && !s.isEmpty()) {
                    if (inTitle && inTitleP) {
                        titleBuf.append(s);
                    } else {
                        text.append(s);
                    }
                }

            } else if (t == XmlPullParser.END_TAG) {
                String tag = x.getName();

                if ("p".equalsIgnoreCase(tag) && inTitle && inTitleP) {
                    inTitleP = false;
                } else if ("title".equalsIgnoreCase(tag) && inTitle) {
                    inTitle = false;
                    String ttxt = titleBuf == null ? null : titleBuf.toString().replaceAll("\\s+", " ").trim();
                    if (ttxt != null && !ttxt.isEmpty())
                        ch.title = ttxt;
                    titleBuf = null;
                } else if (isParagraphish(tag)) {
                    ensureBlankLine(text);
                } else if ("section".equalsIgnoreCase(tag)) {
                    if (depth == 0) {
                        ch.text = EbookTextCleaner.removeReferencesIfEnabled(text.toString());
                        return ch; // done with this section
                    } else {
                        depth--;
                    }
                }
            }
        }

        // If we reach here, malformed, but return what we got
        ch.text = EbookTextCleaner.removeReferencesIfEnabled(text.toString());
        return ch;
    }

    // --------------- Helpers: tags / body selection / cleaning ---------------

    /**
     * FB2 may name the main body "book" or leave @name empty; skip "notes",
     * "comments", etc.
     */
    private static boolean isMainBodyName(String name) {
        if (name == null || name.trim().isEmpty())
            return true;
        String n = name.toLowerCase(Locale.ROOT);
        if (n.equals("book") || n.equals("main") || n.equals("text"))
            return true;
        return !(n.equals("notes") || n.equals("comments") || n.equals("footnotes")
                || n.equals("images") || n.equals("title") || n.equals("cover"));
    }

    private static boolean isParagraphish(String tag) {
        String t = tag.toLowerCase(Locale.ROOT);
        return t.equals("p") || t.equals("subtitle") || t.equals("text-author")
                || t.equals("epigraph") || t.equals("cite")
                || t.equals("stanza") || t.equals("v") // poem lines/stanzas
                || t.equals("poem");
    }

    private static boolean isLineBreak(String tag) {
        String t = tag.toLowerCase(Locale.ROOT);
        return t.equals("br") || t.equals("line-break") || t.equals("text:line-break");
    }

    private static void ensureBlankLine(StringBuilder b) {
        int len = b.length();
        if (len == 0)
            return;
        // end with exactly two newlines between blocks
        if (len >= 2 && b.charAt(len - 1) == '\n' && b.charAt(len - 2) == '\n')
            return;
        if (b.charAt(len - 1) != '\n')
            b.append('\n');
        b.append('\n');
    }

    private static String deriveTitleFromText(String text) {
        if (text == null)
            return "chapter";
        String[] lines = text.replace("\r", "").split("\n");
        for (String line : lines) {
            String t = line.trim();
            if (!t.isEmpty()) {
                if (t.length() > 60)
                    t = t.substring(0, 60);
                return t;
            }
        }
        return "chapter";
    }

    private static String clean(String s) {
        if (s == null)
            return "";
        String t = s.replace('\u00A0', ' ')
                .replace("\r\n", "\n").replace("\r", "\n")
                .replaceAll("[\\t ]{2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return t;
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

    private static String attrNs(XmlPullParser x, String ns, String name) {
        return x.getAttributeValue(ns, name);
    }

    // ---------------- Bitmap decode helper ----------------

    /**
     * Decode with down-sampling if needed to keep the largest dimension <= maxDim.
     */
    private static Bitmap decodeDownsampled(byte[] bytes, int maxDim) {
        if (bytes == null)
            return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
        int w = o.outWidth, h = o.outHeight;
        if (w <= 0 || h <= 0) {
            o.inJustDecodeBounds = false;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length); // try anyway
        }
        int sample = 1;
        while ((w / sample) > maxDim || (h / sample) > maxDim) {
            sample <<= 1; // power-of-two sample size
        }
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        o2.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o2);
    }

    // ---------------- IO ----------------

    public static String readAllText(Context ctx, Uri uri) throws Exception {
        try (InputStream in0 = ctx.getContentResolver().openInputStream(uri);
                BufferedInputStream in = new BufferedInputStream(in0)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(128 * 1024, 8192));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1)
                bos.write(buf, 0, n);
            // FB2 is XML; most files are UTF-8; if BOM present, this handles it fine
            return bos.toString(StandardCharsets.UTF_8.name());
        }
    }
}
