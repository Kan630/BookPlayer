package com.driot.bookplayer.services;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.text.Html;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.objects.TaskStateManager;
import com.driot.bookplayer.tts.EpubLowLevel;
import com.driot.bookplayer.utils.log.LoggingWorker;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EpubSplitWorker extends LoggingWorker {

    private static final String TASK_NAME = Var.WORKER_TASK_LABEL_SPLIT_EPUB;

    public EpubSplitWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        LoadBookTaskState bookState = Pref.getLoadBookTaskState();
        if (bookState == null) {
            TaskStateManager.markTaskFailed(TASK_NAME, "bookState == null");
            return Result.failure();
        }

        final String epubPath = bookState.dynamicSourceFilePath;   // expected: absolute file path
        final String destinationFolderPath = bookState.futureFolderPath;

        myLog("EpubSplitWorker received:");
        myLog("epubPath = " + epubPath);
        myLog("destinationFolderPath = " + destinationFolderPath);

        if (epubPath == null || destinationFolderPath == null) {
            TaskStateManager.markTaskFailed(TASK_NAME, "Missing input data for EpubSplitWorker");
            myLogEE(null, "Missing input data for EpubSplitWorker");
            return Result.failure();
        }

        boolean ok = splitEpub(epubPath, destinationFolderPath);
        return ok ? Result.success() : Result.failure();
    }

    private boolean splitEpub(String epubPath, String destinationFolderPath) {
        Context ctx = getApplicationContext();
        try {
            File outFolder = new File(destinationFolderPath);
            if (!outFolder.exists() && !outFolder.mkdirs()) {
                TaskStateManager.markTaskFailed(TASK_NAME, "Cannot create output folder: " + destinationFolderPath);
                return false;
            }

            // Build a Uri from file path (supports "content://" too if ever passed)
            Uri uri = epubPath.startsWith("content://") || epubPath.startsWith("file://")
                    ? Uri.parse(epubPath)
                    : Uri.fromFile(new File(epubPath));

            // Extract all (cover + chapter files) using your helper
            TaskStateManager.tellProgress(TASK_NAME, 1, "Parsing EPUB…");
            EpubLowLevel.ExtractResult result = EpubLowLevel.extractAll(ctx, uri);
            Bitmap cover = result.coverBitmap;
            List<File> chapters = result.chapterFiles;

            if (chapters == null || chapters.isEmpty()) {
                TaskStateManager.markTaskFailed(TASK_NAME, "No chapters found in EPUB");
                return false;
            }

            // Optionally save cover image if present
            if (cover != null && !isStopped()) {
                try {
                    File coverFile = new File(outFolder, "cover.jpg");
                    FileOutputStream fos = new FileOutputStream(coverFile);
                    cover.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                    fos.flush();
                    fos.close();
                } catch (Exception e) {
                    myLogEE(e, "Saving cover.jpg failed");
                    // Non-fatal
                }
            }

            Set<String> usedNames = new HashSet<>();
            DecimalFormat numFmt = new DecimalFormat("000");
            final int total = chapters.size();

            for (int i = 0; i < total; i++) {
                if (isStopped()) {
                    TaskStateManager.markTaskCancelled(TASK_NAME);
                    return false;
                }

                File chapterFile = chapters.get(i);
                String html = readUtf8File(chapterFile);
                String title = pickBestTitle(html, chapterFile.getName());

                // sanitize + ensure uniqueness
                if (title == null || title.trim().isEmpty()) {
                    title = "chapter" + numFmt.format(i + 1);
                }
                title = toSafeFilename(title.trim());
                title = ensureUnique(usedNames, title);
                usedNames.add(title);

                // Convert HTML → plain text
                String text = htmlToPlainText(html);
                text = cleanText(text);

                // Write as UTF-8 .txt
                File out = new File(outFolder, title + ".txt");
                writeUtf8(out, text);

                int progress = (int) Math.round(((i + 1) * 100.0) / total);
                String progressText = "Splitting EPUB: " + (i + 1) + "/" + total + "\n\n" + title;
                TaskStateManager.tellProgress(TASK_NAME, progress, progressText);
                myLogD(progress + "% - " + progressText.replace("\n", " - "));
            }

            // If you want to delete source EPUB after split, uncomment:
            // File src = new File(epubPath);
            // if (src.exists() && !src.delete()) {
            //     myLogE("Could not delete source EPUB after split: " + epubPath);
            // }

            // Mark completion (add this helper in your TaskStateManager similar to markM4bSplitCompleted)
            TaskStateManager.markEpubSplitCompleted(TASK_NAME, outFolder.getAbsolutePath());
            return true;

        } catch (Exception e) {
            myLogEE(e, "splitEpub");
            TaskStateManager.markTaskFailed(TASK_NAME, e.getMessage());
            return false;
        }
    }

    // ---------- helpers ----------

    private static String readUtf8File(File f) {
        try (java.io.BufferedInputStream in = new java.io.BufferedInputStream(new java.io.FileInputStream(f));
             java.io.InputStreamReader isr = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8);
             java.io.BufferedReader br = new java.io.BufferedReader(isr, 64 * 1024)) {

            StringBuilder sb = new StringBuilder(Math.min((int) Math.max(f.length(), 128_000L), 2_000_000));
            char[] buf = new char[8192];
            int n;
            while ((n = br.read(buf)) != -1) sb.append(buf, 0, n);
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String htmlToPlainText(String html) {
        if (html == null) return "";
        try {
            return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString();
        } catch (Throwable t) {
            return html.replaceAll("<[^>]+>", " ");
        }
    }

    private static String cleanText(String raw) {
        if (raw == null) return "";
        String s = raw.replace("\r\n", "\n").replace("\r", "\n");
        // Remove control chars except \n and \t
        s = s.replaceAll("[\\p{Cntrl}&&[^\n\t]]", "");
        // Collapse >2 blank lines to just one
        s = s.replaceAll("\n{3,}", "\n\n");
        // Trim edges
        return s.trim();
    }

    private static final Pattern TITLE_TAG =
            Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern H1_TAG =
            Pattern.compile("<h1[^>]*>(.*?)</h1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern H2_TAG =
            Pattern.compile("<h2[^>]*>(.*?)</h2>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static String pickBestTitle(String html, String fallbackName) {
        if (html != null) {
            Matcher m = TITLE_TAG.matcher(html);
            if (m.find()) {
                String t = htmlToPlainText(m.group(1));
                if (t != null && !t.trim().isEmpty()) return t.trim();
            }
            m = H1_TAG.matcher(html);
            if (m.find()) {
                String t = htmlToPlainText(m.group(1));
                if (t != null && !t.trim().isEmpty()) return t.trim();
            }
            m = H2_TAG.matcher(html);
            if (m.find()) {
                String t = htmlToPlainText(m.group(1));
                if (t != null && !t.trim().isEmpty()) return t.trim();
            }
        }
        // fallback to file name minus extension
        String name = fallbackName == null ? "" : fallbackName;
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name;
    }

    private static String toSafeFilename(String s) {
        String cleaned = s.replaceAll("[\\\\/:*?\"<>|]", " ").replaceAll("\\s+", " ").trim();
        // limit length to something reasonable
        if (cleaned.length() > 80) cleaned = cleaned.substring(0, 80).trim();
        // Avoid empty
        if (cleaned.isEmpty()) cleaned = "chapter";
        return cleaned;
    }

    private static String ensureUnique(Set<String> used, String base) {
        String cand = base;
        int n = 2;
        // case-insensitive uniqueness
        while (used.contains(cand.toLowerCase(Locale.ROOT))) {
            cand = base + " (" + n + ")";
            n++;
        }
        return cand;
    }

    private static void writeUtf8(File file, String text) throws Exception {
        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8))) {
            bw.write(text != null ? text : "");
            bw.flush();
        }
    }
}
