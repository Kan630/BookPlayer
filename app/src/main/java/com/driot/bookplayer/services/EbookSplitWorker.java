package com.driot.bookplayer.services;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.OdtLowLevelHelper;
import com.driot.bookplayer.imports.ImportWorker;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.helpers.EpubLowLevelHelper;
import com.driot.bookplayer.helpers.Fb2LowLevelHelper;

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

public class EbookSplitWorker extends ImportWorker {

    // Keep existing label for compatibility with UI/strings
    private static final String TASK_NAME = Var.WORKER_TASK_LABEL_SPLIT_EBOOK;

    private final Context context;

    public EbookSplitWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        LoadBookTaskState bookState = Pref.getLoadBookTaskState();
        if (bookState == null) {
            emitFailed(TASK_NAME, "bookState == null", getApplicationContext().getString(R.string.invalid_resource));
            return Result.failure();
        }

        final String ebookPath = bookState.dynamicSourceFilePath;   // absolute file path or content://
        final String destinationFolderPath = bookState.futureFolderPath;
        final String ebookType = guessTypeFromPath(ebookPath); //"epub" or "fb2"

        myLog("EbookSplitWorker received:");
        myLog("ebookPath = " + ebookPath);
        myLog("destinationFolderPath = " + destinationFolderPath);
        myLog("ebookType = " + ebookType);

        if (ebookPath == null || destinationFolderPath == null) {
            emitFailed(TASK_NAME, "Missing input data for EbookSplitWorker", getApplicationContext().getString(R.string.invalid_resource));
            myLogEE(null, "Missing input data for EbookSplitWorker");
            return Result.failure();
        }

        FirebaseAnalyticsHelper.tellAnalyticsEbookWorker(ebookType);

        boolean ok = splitEbook(ebookPath, destinationFolderPath, ebookType);
        return ok ? Result.success() : Result.failure();
    }

    private boolean splitEbook(String ebookPath, String destinationFolderPath, String ebookType) {
        Context ctx = getApplicationContext();
        try {
            File outFolder = new File(destinationFolderPath);
            if (!outFolder.exists() && !outFolder.mkdirs()) {
                emitFailed(TASK_NAME
                        , "failed_to_create_destination_folder : " + destinationFolderPath
                        , context.getString(R.string.failed_to_create_destination_folder) + ": " + destinationFolderPath);
                return false;
            }

            // Build a Uri from file path
            Uri uri = (ebookPath.startsWith("content://") || ebookPath.startsWith("file://"))
                    ? Uri.parse(ebookPath)
                    : Uri.fromFile(new File(ebookPath));

            // Extract (cover + chapter files) using the appropriate helper
            Bitmap cover;
            List<File> chapters;

            if ("fb2".equals(ebookType)) {
                emitStepProgress(TASK_NAME, 1, "Parsing FB2…");
                Fb2LowLevelHelper.ExtractResult result = Fb2LowLevelHelper.extractAll(ctx, uri);
                cover    = result.coverBitmap;
                chapters = result.chapterFiles;
            } else if ("epub".equals(ebookType)) {
                emitStepProgress(TASK_NAME, 1, "Parsing EPUB…");
                EpubLowLevelHelper.ExtractResult result = EpubLowLevelHelper.extractAll(ctx, uri);
                cover    = result.coverBitmap;
                chapters = result.chapterFiles;
            } else if ("odt".equals(ebookType)) {
                emitStepProgress(TASK_NAME, 1, "Parsing ODT…");
                OdtLowLevelHelper.ExtractResult result = OdtLowLevelHelper.extractAll(ctx, uri);
                cover    = result.coverBitmap;
                chapters = result.chapterFiles;
            } else {
                emitFailed(TASK_NAME, "unsupported_ebook_type: [" + ebookType + "]", ctx.getString(R.string.Unsupported_ebook_type) + ". (" + ebookType + ")");
                return false;
            }

            if (chapters == null || chapters.isEmpty()) {
                emitFailed(TASK_NAME
                        , "no_chapters_found : [" + ebookType + "]"
                        , ctx.getString(R.string.No_chapters_found));
                return false;
            }

            // Save cover image if present (JPEG to keep previous behavior)
            if (cover != null && !isStopped()) {
                try (FileOutputStream fos = new FileOutputStream(new File(outFolder, "cover.jpg"))) {
                    cover.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                    fos.flush();
                } catch (Exception e) {
                    myLogEE(e, "Saving cover.jpg failed");
                    // Non-fatal
                }
            }

            // Prepare output names (keep indices, use chapter filename as title)
            Set<String> usedNames = new HashSet<>();
            DecimalFormat numFmt = new DecimalFormat("000");
            final int total = chapters.size();

            for (int i = 0; i < total; i++) {
                if (isStopped()) {
                    emitCancelled(TASK_NAME);
                    return false;
                }

                File chapterFile = chapters.get(i);

                // Our helpers already wrote *plain text* with preserved newlines.
                String text = readUtf8File(chapterFile);

                // Derive title from file name (remove ###_ prefix and extension)
                String title = titleFromFileName(chapterFile.getName());
                if (title == null || title.trim().isEmpty()) {
                    title = "chapter" + numFmt.format(i + 1);
                }
                title = toSafeFilename(title.trim());
                title = ensureUnique(usedNames, title);
                usedNames.add(title);

                // Write as UTF-8 .txt (preserve newlines; do minimal cleanup only)
                File out = new File(outFolder, title + ".txt");
                writeUtf8(out, cleanTextKeepParagraphs(text));

                int progress = (int) Math.round(((i + 1) * 100.0) / total);
                String progressText = "Splitting " + ebookType.toUpperCase(Locale.ROOT) + ": "
                        + (i + 1) + "/" + total + "\n\n" + title;
                emitStepProgress(TASK_NAME, progress, progressText);
                myLogD(progress + "% - " + progressText.replace("\n", " - "));
            }

            // Reuse existing completion hook for EPUB (keeps app logic unchanged)
            emitTaskCompleted(TASK_NAME, outFolder.getAbsolutePath());
            return true;

        } catch (Exception e) {
            myLogEE(e, "splitEbook");
            emitFailed(TASK_NAME, e.getMessage(), null);
            return false;
        }
    }

    // ---------- helpers ----------

    private static String guessTypeFromPath(String path) {
        String name = new File(path).getName().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String ext = dot > 0 ? name.substring(dot + 1) : "";
        switch (ext) {
            case "epub": return "epub";
            case "fb2":  return "fb2";
            // common zipped fb2 variants could be handled later (fb2.zip/fbz) if you add unzip
            default:     return "epub"; // safe default if you mostly import EPUBs
        }
    }

    private static String readUtf8File(File f) {
        try (java.io.BufferedInputStream in = new java.io.BufferedInputStream(new java.io.FileInputStream(f));
             java.io.InputStreamReader isr = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8);
             java.io.BufferedReader br = new java.io.BufferedReader(isr, 64 * 1024)) {

            StringBuilder sb = new StringBuilder((int) Math.min(Math.max(f.length(), 128_000L), 2_000_000));
            char[] buf = new char[8192];
            int n;
            while ((n = br.read(buf)) != -1) sb.append(buf, 0, n);
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Preserve paragraphs; do only safe cleanup. */
    private static String cleanTextKeepParagraphs(String raw) {
        if (raw == null) return "";
        String s = raw.replace("\r\n", "\n").replace("\r", "\n");
        // Remove control chars except \n and \t
        s = s.replaceAll("[\\p{Cntrl}&&[^\n\t]]", "");
        // Collapse 3+ blank lines → 2
        s = s.replaceAll("\n{3,}", "\n\n");
        return s.trim();
    }

    /** From file like "003_chapter-title.txt" → "chapter-title". */
    private static String titleFromFileName(String name) {
        if (name == null) return null;
        String base = name;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        // drop leading "###_" index if present
        if (base.length() >= 4 && Character.isDigit(base.charAt(0)) && Character.isDigit(base.charAt(1))
                && Character.isDigit(base.charAt(2)) && base.charAt(3) == '_') {
            base = base.substring(4);
        }
        return base;
    }

    private static String toSafeFilename(String s) {
        String cleaned = s.replaceAll("[\\\\/:*?\"<>|]", " ").replaceAll("\\s+", " ").trim();
        if (cleaned.length() > 80) cleaned = cleaned.substring(0, 80).trim();
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
