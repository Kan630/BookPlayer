package com.driot.bookplayer.ebooks;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.SupportedFilesHelper;
import com.driot.bookplayer.utils.log.KanLogger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Splits a single large .txt file into smaller chunks (chapters) for better
 * performance
 * and navigation in the library.
 */
public final class TextLowLevelHelper {

    private static final int CHUNK_SIZE_BYTES = 100 * 1024; // 100KB chunks

    private TextLowLevelHelper() {
    }

    public static final class ExtractResult {
        public final String bookTitle;
        public final File outDir;
        public final List<File> chapterFiles;
        public final Bitmap coverBitmap = null;
        public final Map<String, String> trackTitles = new LinkedHashMap<>();

        ExtractResult(String t, File d, List<File> f) {
            bookTitle = t;
            outDir = d;
            chapterFiles = f;
        }
    }

    public static ExtractResult extractAll(Context ctx, Uri txtUri) throws Exception {
        String fileName = SupportedFilesHelper.getFileName(ctx, txtUri);
        String bookTitle = fileName;
        if (bookTitle != null && bookTitle.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            bookTitle = bookTitle.substring(0, bookTitle.length() - 4);
        }
        if (bookTitle == null || bookTitle.trim().isEmpty()) {
            bookTitle = "text_import";
        }

        File outDir = new File(ctx.getExternalFilesDir(null), "text_split_" + FileHelper.sanitizeFilename(bookTitle));
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IllegalStateException("Cannot create " + outDir);
        }

        List<File> outFiles = new ArrayList<>();
        ExtractResult result = new ExtractResult(bookTitle, outDir, outFiles);

        try (InputStream in = ctx.getContentResolver().openInputStream(txtUri);
                BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

            StringBuilder chunk = new StringBuilder(CHUNK_SIZE_BYTES + 1024);
            char[] buf = new char[8192];
            int n;
            int partIdx = 0;

            while ((n = br.read(buf)) != -1) {
                chunk.append(buf, 0, n);

                if (chunk.length() >= CHUNK_SIZE_BYTES) {
                    // Try to find a good breaking point (newline)
                    int breakPos = chunk.lastIndexOf("\n");
                    if (breakPos > CHUNK_SIZE_BYTES / 2) {
                        String content = chunk.substring(0, breakPos);
                        String remaining = chunk.substring(breakPos + 1);
                        saveChunk(outDir, ++partIdx, content, result);
                        chunk.setLength(0);
                        chunk.append(remaining);
                    } else {
                        // Hard break if no newline found
                        saveChunk(outDir, ++partIdx, chunk.toString(), result);
                        chunk.setLength(0);
                    }
                }
            }

            if (chunk.length() > 0) {
                saveChunk(outDir, ++partIdx, chunk.toString(), result);
            }
        }

        KanLogger.myLog("=== TextLowLevelHelper.extractAll: done; chunks=" + outFiles.size() + " ===");
        return result;
    }

    private static void saveChunk(File outDir, int partIdx, String content, ExtractResult result) throws Exception {
        String title = String.format(Locale.ROOT, "Part %d", partIdx);
        String fname = String.format(Locale.ROOT, "%03d_part_%d.txt", partIdx, partIdx);
        File f = new File(outDir, fname);

        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
            bw.write(content);
            bw.flush();
        }

        result.chapterFiles.add(f);
        result.trackTitles.put(f.getName(), title);
    }
}
