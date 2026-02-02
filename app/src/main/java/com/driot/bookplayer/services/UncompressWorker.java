package com.driot.bookplayer.services;

import static com.driot.bookplayer.global.Var.ONLY_MIME_AUDIO;
import static com.driot.bookplayer.global.Var.SUPPORTED_AUDIO_EXTENSIONS;
import static com.driot.bookplayer.global.Var.SUPPORTED_COVER_PICTURE_EXTENSIONS;
import static com.driot.bookplayer.global.Var.SUPPORTED_VIDEO_EXTENSIONS;
import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Tonio.getMimeType;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.imports.ImportHelper;
import com.driot.bookplayer.imports.ImportJob;
import com.driot.bookplayer.imports.ImportWorker;
import com.driot.bookplayer.services.archives.ArchiveDispatch;
import com.driot.bookplayer.services.archives.ArchiveExtractor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UncompressWorker extends ImportWorker {

    private static final String TASK_NAME = Var.WORKER_TASK_LABEL_DECOMPRESS;

    private final Context context;

    public UncompressWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context.getApplicationContext();
    }

    @NonNull
    @Override
    public Result doWorkBody() {
        emitTaskStart(TASK_NAME, context.getString(R.string.Uncompress) + " " + context.getString(R.string.import_task_start));
        ImportJob j = jobOrFail();

        final String zipFilePath = ImportHelper.getSourceFilePathForWorker(j);
        final String destinationFolderPath = j.futureFolderPath;

        myLogD("----------------------------------------------------");
        myLog("From: " + zipFilePath);
        myLog("To: " + destinationFolderPath);
        myLogD("----------------------------------------------------");

        // Optionally enter foreground:
        // setForegroundEarly(buildForegroundInfo());

        if (zipFilePath == null || destinationFolderPath == null) {
            emitFailed(TASK_NAME, "Missing input data", context.getString(R.string.invalid_resource));
            return Result.failure();
        }

        File zipFile = new File(zipFilePath);
        File unzipFolder = new File(destinationFolderPath);

        if (!zipFile.exists()) {
            emitFailed(TASK_NAME, "Zip file not found", context.getString(R.string.invalid_resource));
            return Result.failure();
        }

        if (!unzipFolder.exists() && !unzipFolder.mkdirs()) {
            emitFailed(TASK_NAME, "Could not create destination folder", context.getString(R.string.failed_to_create_destination_folder));
            return Result.failure();
        }

        FirebaseAnalyticsHelper.logEvent("uncompress_worker");


        try {
            File archive = new File(zipFilePath);
            File destDir = new File(destinationFolderPath);

            ArchiveExtractor extractor = ArchiveDispatch.forFile(archive);
            extractor.extract(
                    archive,
                    destDir,
                    (cur, total, name) -> {
                        int pct = (total > 0) ? (int)((cur * 100L) / total) : 0;
                        String progressText = context.getString(R.string.Import_Progress_unzipping_file)
                                + cur + "/" + (total > 0 ? total : "?") + "\n" + name;
                        emitStepProgress(TASK_NAME, pct, progressText);
                    },
                    () -> isStopped() // your existing cancel check
            );

            if (!zipFile.delete()) myLogEE(null,"Error deleting internal compressed file");

            // Keep: audio, video, image, and single-book formats (epub, odt, fb2, m4b)
            if (unzipFolder.listFiles() != null) {
                for (File f : unzipFolder.listFiles()) {
                    if (f.isDirectory()) continue;
                    String mime = getMimeType(f);
                    String ext = getExtension(f.getName());
                    if (ext == null) ext = "";
                    ext = ext.toLowerCase(Locale.ROOT);
                    boolean isAudioOrImage = (mime != null && mime.startsWith(ONLY_MIME_AUDIO))
                            || SUPPORTED_AUDIO_EXTENSIONS.contains(ext)
                            || SUPPORTED_VIDEO_EXTENSIONS.contains(ext)
                            || SUPPORTED_COVER_PICTURE_EXTENSIONS.contains(ext);
                    boolean isBook = Var.SPLITTABLE_EBOOK_EXTENSIONS.contains(ext)
                            || "m4b".equals(ext);
                    if (!isAudioOrImage && !isBook) {
                        if (!f.delete()) myLogE("Could not delete unsupported file: " + f.getName());
                    }
                }
            }

            // If zip contained a single book (epub/odt/fb2/m4b) and no audio, update job so split runs
            detectAndUpdateForSingleBookInZip(unzipFolder, j);

            emitTaskCompleted(TASK_NAME, destinationFolderPath, context.getString(R.string.Uncompress) + " " + context.getString(R.string.done_));
            return Result.success();

        } catch (Exception e) {
            String msg = (e.getMessage() != null ? e.getMessage() : "");
            myLogE("main catch : " + msg);

            if (msg.toLowerCase(Locale.ROOT).endsWith("user_cancel")) {
                emitCancelled(TASK_NAME);
                return Result.failure();
            }

            // --- Check for ENOSPC ("no space left on device") ---
            boolean noSpace = msg.contains("ENOSPC")
                    || msg.contains("No space left on device")
                    || (e.getCause() != null && String.valueOf(e.getCause().getMessage()).contains("ENOSPC"));

            if (noSpace) {
                myLogEE(e, "uncompresses - disk full (ENOSPC)");
                String userMsg = context.getString(R.string.error_no_space_left)
                        + "\n\n" + context.getString(R.string.solution_free_space);
                emitWarning(userMsg);
                emitFailed(TASK_NAME, "No space left on device", context.getString(R.string.error_no_space_left));
            } else {
                emitFailed(TASK_NAME, e.getMessage(), null);
            }
            FileHelper.recursiveRemove(unzipFolder);
            return Result.failure();
        }
    }

    /**
     * If the unzipped folder contains exactly one book (epub/odt/fb2/m4b) and no audio files,
     * update the ImportJob so EbookSplitWorker or M4bSplitWorker will process it.
     */
    private void detectAndUpdateForSingleBookInZip(File unzipFolder, ImportJob j) {
        List<File> bookFiles = new ArrayList<>();
        int[] audioCount = new int[]{0};
        collectBookAndAudioRecursive(unzipFolder, bookFiles, audioCount);
        if (audioCount[0] > 0) return; // Zip has audio → treat as audio folder, no split
        if (bookFiles.size() != 1) return; // Zero or multiple books → no single-book split

        File bookFile = bookFiles.get(0);
        String absPath = bookFile.getAbsolutePath();
        String basePath = unzipFolder.getAbsolutePath();
        String relPath = absPath.substring(basePath.length());
        if (relPath.startsWith("/")) relPath = relPath.substring(1);
        String ext = getExtension(bookFile.getName());
        if (ext == null) ext = "";
        ext = ext.toLowerCase(Locale.ROOT);

        j.originalFile = relPath;
        j.fileExtension = ext;
        j.playType = "m4b".equals(ext) ? Var.PLAY_TYPE_AUDIO : Var.PLAY_TYPE_TEXT;
        j.doSplitEbook = Var.SPLITTABLE_EBOOK_EXTENSIONS.contains(ext);
        j.doSplitM4b = "m4b".equals(ext);
        AppDatabase.getInstance(context).importJobDao().update(j);
        myLogD("Post-unzip: single book detected -> originalFile=" + relPath + ", doSplitEbook="
                + j.doSplitEbook + ", doSplitM4b=" + j.doSplitM4b);
    }

    private void collectBookAndAudioRecursive(File dir, List<File> bookFiles, int[] audioCount) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory()) {
                collectBookAndAudioRecursive(f, bookFiles, audioCount);
            } else {
                String ext = getExtension(f.getName());
                if (ext == null) ext = "";
                ext = ext.toLowerCase(Locale.ROOT);
                if (Var.SPLITTABLE_EBOOK_EXTENSIONS.contains(ext) || "m4b".equals(ext)) {
                    bookFiles.add(f);
                } else if (SUPPORTED_AUDIO_EXTENSIONS.contains(ext)
                        || Var.SUPPORTED_VIDEO_EXTENSIONS.contains(ext)) {
                    audioCount[0]++;
                }
            }
        }
    }

}
