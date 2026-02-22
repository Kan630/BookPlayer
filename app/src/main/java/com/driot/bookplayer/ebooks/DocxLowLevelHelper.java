package com.driot.bookplayer.ebooks;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.zwobble.mammoth.Result;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * DOCX low-level extractor:
 * - Uses Mammoth to convert .docx to HTML.
 * - One chapter per
 * <h1>,
 * <h2>, or
 * <h3>.
 * - Fallback: single "Full Document" chapter if no headings.
 * - Cover: largest image found in word/media/.
 */
public final class DocxLowLevelHelper {
    private DocxLowLevelHelper() {
    }

    // ---------------- Types ----------------

    public static final class ExtractResult {
        public final String bookTitle;
        public final File outDir;
        public final List<File> chapterFiles;
        public final Bitmap coverBitmap;

        ExtractResult(String t, File d, List<File> f, Bitmap c) {
            bookTitle = t;
            outDir = d;
            chapterFiles = f;
            coverBitmap = c;
        }
    }

    private static final class Chapter {
        String title;
        StringBuilder buf = new StringBuilder();
    }

    // ---------------- Public API ----------------

    public static ExtractResult extractAll(Context ctx, Uri docxUri, boolean splitIntoChapters) throws Exception {
        myLog("=== DOCX extractAll: begin ===");

        // 1) Read whole DOCX into memory
        byte[] docxBytes = readAllBytes(ctx, docxUri);

        // 2) Walk ZIP entries: get largest picture from word/media/
        Bitmap cover = null;
        int bestBytes = -1;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docxBytes))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String name = e.getName();

                if (name.startsWith("word/media/")) {
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
        }

        // 3) Use Mammoth to convert to HTML
        org.zwobble.mammoth.DocumentConverter converter = new org.zwobble.mammoth.DocumentConverter();
        Result<String> result = converter.convertToHtml(new ByteArrayInputStream(docxBytes));
        String html = result.getValue();
        // Log warnings if any
        for (String warning : result.getWarnings()) {
            myLogW("Mammoth warning: " + warning);
        }

        // 4) Parse HTML and split into chapters
        List<Chapter> chapters = parseChapters(html, splitIntoChapters);
        myLog("Chapters parsed: " + chapters.size());

        // 5) Title heuristic: first heading text, else "untitled"
        String bookTitle = "untitled";
        if (!chapters.isEmpty() && chapters.get(0).title != null && !chapters.get(0).title.trim().isEmpty()) {
            bookTitle = chapters.get(0).title.trim();
        } else {
            // Try to find any heading in the document if the first chapter has no title
            for (Chapter ch : chapters) {
                if (ch.title != null && !ch.title.trim().isEmpty()) {
                    bookTitle = ch.title.trim();
                    break;
                }
            }
        }

        // 6) Write out chapters
        File outDir = new File(ctx.getExternalFilesDir(null), "docx_" + safe(bookTitle));
        if (!outDir.exists() && !outDir.mkdirs())
            throw new IllegalStateException("Cannot create " + outDir);

        List<File> outFiles = new ArrayList<>();
        int idx = 0;
        for (Chapter ch : chapters) {
            String text = EbookTextCleaner.removeReferencesIfEnabled(clean(ch.buf.toString()));
            if (text.isEmpty())
                continue;

            String title = (ch.title != null && !ch.title.trim().isEmpty())
                    ? ch.title.trim()
                    : deriveTitleFromText(text);
            String fname = String.format(Locale.US, "%03d_%s.txt", ++idx, safeSlug(title));
            File f = new File(outDir, fname);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(text.getBytes(StandardCharsets.UTF_8));
            }
            outFiles.add(f);
            myLogD("Wrote chapter: " + f.getName() + " (len=" + text.length() + ")");
        }

        // Fallback: if no chapters were written (e.g. all empty), but we have text
        if (outFiles.isEmpty()) {
            Document doc = Jsoup.parse(html);
            String fullText = EbookTextCleaner.removeReferencesIfEnabled(clean(doc.text()));
            if (!fullText.isEmpty()) {
                String fname = "001_document.txt";
                File f = new File(outDir, fname);
                try (FileOutputStream fos = new FileOutputStream(f)) {
                    fos.write(fullText.getBytes(StandardCharsets.UTF_8));
                }
                outFiles.add(f);
                myLogW("No chapters extracted; fallback to single document (len=" + fullText.length() + ")");
            }
        }

        myLog("=== DOCX extractAll: done; chapters=" + outFiles.size() + " ===");
        return new ExtractResult(bookTitle, outDir, outFiles, cover);
    }

    // ---------------- HTML Parsing ----------------

    private static List<Chapter> parseChapters(String html, boolean splitIntoChapters) {
        List<Chapter> chapters = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Element body = doc.body();

        if (!splitIntoChapters) {
            Chapter fullDoc = new Chapter();
            fullDoc.title = "Full Document";
            fullDoc.buf.append(clean(doc.text()));
            chapters.add(fullDoc);
            return chapters;
        }

        Chapter current = new Chapter();
        // Seed with a default title if needed, will be overwritten by first heading
        current.title = "";

        Elements elements = body.children();
        for (Element el : elements) {
            String tag = el.tagName().toLowerCase();
            String text = el.text().trim();

            boolean isHeading = tag.equals("h1") || tag.equals("h2") || tag.equals("h3");
            boolean isTocPattern = false;

            // Detect TOC pattern: "I - Title", "II - Title", "1 - Title", "Chapter 1 - ..."
            if (!text.isEmpty()) {
                // regex for roman numerals or digits at start followed by - or .
                // examples: "I - Title", "II. Title", "1 - Title", "Chapter 1 - Title"
                if (text.matches("^(?i)(Chapter\\s+)?([IVXLCDM]+|[0-9]+)(\\s*[-.]\\s*|\\s+).*")) {
                    isTocPattern = true;
                }
            }

            if (isHeading || isTocPattern) {
                // New chapter
                if (current.buf.length() > 0 || !current.title.isEmpty()) {
                    chapters.add(current);
                }
                current = new Chapter();
                current.title = text;
            } else {
                if (!text.isEmpty()) {
                    current.buf.append(el.text()).append("\n\n");
                }
            }
        }

        // Add final chapter
        if (current.buf.length() > 0 || !current.title.isEmpty()) {
            chapters.add(current);
        }

        return chapters;
    }

    // ---------------- IO helpers ----------------

    private static byte[] readAllBytes(Context ctx, Uri uri) throws Exception {
        try (InputStream in0 = ctx.getContentResolver().openInputStream(uri);
                BufferedInputStream in = new BufferedInputStream(in0)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(512 * 1024);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1)
                bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    private static byte[] readEntryAsBytes(ZipInputStream zis) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(64 * 1024);
        byte[] buf = new byte[8192];
        int n;
        while ((n = zis.read(buf)) != -1)
            bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    // ---------------- Small helpers ----------------

    private static String deriveTitleFromText(String text) {
        if (text == null)
            return "chapter";
        String[] lines = text.replace("\r", "").split("\n");
        for (String line : lines) {
            String t = line.trim();
            if (!t.isEmpty()) {
                return t.length() > 60 ? t.substring(0, 60) : t;
            }
        }
        return "chapter";
    }

    private static String clean(String s) {
        if (s == null)
            return "";
        return s.replace('\u00A0', ' ')
                .replace("\r\n", "\n").replace("\r", "\n")
                .replaceAll("[\\t ]{2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private static String safe(String s) {
        String out = s.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (out.isEmpty())
            out = "untitled";
        return out.length() > 60 ? out.substring(0, 60) : out;
    }

    private static String safeSlug(String s) {
        String out = s.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (out.isEmpty())
            out = "chapter";
        return out.length() > 40 ? out.substring(0, 40) : out;
    }
}
