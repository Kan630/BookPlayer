package com.driot.bookplayer.ebooks;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.SupportedFilesHelper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * HTML low-level extractor:
 * - One chapter per
 * <h1>,
 * <h2>, or
 * <h3>based on heuristic.
 * - Extracts plain text from HTML segments.
 */
public final class HtmlLowLevelHelper {
    private HtmlLowLevelHelper() {
    }

    public static final class ExtractResult {
        public final String bookTitle;
        public final File outDir;
        public final List<File> chapterFiles;
        public final Bitmap coverBitmap;
        public final Map<String, String> trackTitles = new LinkedHashMap<>();

        ExtractResult(String t, File d, List<File> f, Bitmap c) {
            bookTitle = t;
            outDir = d;
            chapterFiles = f;
            coverBitmap = c;
        }
    }

    static final class Chapter {
        String title;
        StringBuilder buf = new StringBuilder();
    }

    public static ExtractResult extractAll(Context ctx, Uri htmlUri) throws Exception {
        myLog("=== HTML extractAll: begin ===");
        String fileName = SupportedFilesHelper.getFileName(ctx, htmlUri);
        String bookTitle = fileName;
        if (bookTitle != null) {
            String lower = bookTitle.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".html"))
                bookTitle = bookTitle.substring(0, bookTitle.length() - 5);
            else if (lower.endsWith(".htm"))
                bookTitle = bookTitle.substring(0, bookTitle.length() - 4);
        }
        if (bookTitle == null || bookTitle.trim().isEmpty()) {
            bookTitle = "html_import";
        }

        String html = readAllText(ctx, htmlUri);
        List<Chapter> chapters = parseChapters(html);

        File outDir = new File(ctx.getExternalFilesDir(null), "html_" + FileHelper.sanitizeFilename(bookTitle));
        if (!outDir.exists() && !outDir.mkdirs())
            throw new IllegalStateException("Cannot create " + outDir);

        List<File> outFiles = new ArrayList<>();
        ExtractResult result = new ExtractResult(bookTitle, outDir, outFiles, null);

        int idx = 0;
        for (Chapter ch : chapters) {
            String text = ch.buf.toString().trim();
            if (text.isEmpty() && (ch.title == null || ch.title.trim().isEmpty()))
                continue;

            String title = (ch.title != null && !ch.title.trim().isEmpty()) ? ch.title.trim() : "Part " + (idx + 1);
            String fname = String.format(Locale.US, "%03d_%s.txt", ++idx, FileHelper.sanitizeFilename(title));
            File f = new File(outDir, fname);

            StringBuilder finalContent = new StringBuilder();
            if (ch.title != null && !ch.title.trim().isEmpty()) {
                finalContent.append(ch.title.trim()).append("\n\n");
            }
            finalContent.append(text);

            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(finalContent.toString().getBytes(StandardCharsets.UTF_8));
            }
            outFiles.add(f);
            result.trackTitles.put(f.getName(), title);
        }

        myLog("=== HTML extractAll: done; chapters=" + result.chapterFiles.size() + " ===");
        return result;
    }

    private static String readAllText(Context ctx, Uri uri) throws Exception {
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null)
                return "";
            ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(64 * 1024, 4096));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1)
                bos.write(buf, 0, n);
            return bos.toString(StandardCharsets.UTF_8.name());
        }
    }

    static List<Chapter> parseChapters(String html) {
        List<Chapter> chapters = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Element body = doc.body();
        if (body == null)
            body = doc;

        int h1Count = body.select("h1").size();
        int h2Count = body.select("h2").size();
        int h3Count = body.select("h3").size();

        String bestTag = "h1";
        if (h1Count > 1)
            bestTag = "h1";
        else if (h2Count > 1)
            bestTag = "h2";
        else if (h3Count > 1)
            bestTag = "h3";

        myLogD("HTML Split Heuristic: h1=" + h1Count + ", h2=" + h2Count + ", h3=" + h3Count + " => bestTag="
                + bestTag);

        Elements children = body.children();
        Chapter current = new Chapter();
        current.title = "";

        for (Element el : children) {
            String tag = el.tagName().toLowerCase(Locale.ROOT);
            String text = el.text().trim();
            if (text.isEmpty() && el.children().isEmpty())
                continue;

            boolean isHeading = tag.matches("h[1-3]");
            boolean shouldSplit = tag.equals(bestTag) || tag.equals("h1");

            if (shouldSplit) {
                if (current.buf.length() > 0 || !current.title.isEmpty()) {
                    chapters.add(current);
                }
                current = new Chapter();
                current.title = text;
            } else {
                if (current.title.isEmpty() && isHeading) {
                    current.title = text;
                } else {
                    current.buf.append(el.text()).append("\n\n");
                }
            }
        }

        if (current.buf.length() > 0 || !current.title.isEmpty()) {
            chapters.add(current);
        }

        // Merge very short chapters
        if (chapters.size() > 1) {
            List<Chapter> merged = new ArrayList<>();
            for (int i = 0; i < chapters.size(); i++) {
                Chapter c = chapters.get(i);
                if (c.buf.length() < 150 && i < chapters.size() - 1) {
                    Chapter next = chapters.get(i + 1);
                    StringBuilder newBuf = new StringBuilder();
                    if (!c.title.isEmpty())
                        newBuf.append(c.title).append("\n\n");
                    newBuf.append(c.buf).append("\n\n").append(next.buf);
                    next.buf = newBuf;
                } else {
                    merged.add(c);
                }
            }
            return merged;
        }

        return chapters;
    }
}
