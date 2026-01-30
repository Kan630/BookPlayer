package com.driot.bookplayer.services;

import static com.driot.bookplayer.global.Var.ONLY_MIME_AUDIO;
import static com.driot.bookplayer.global.Var.SUPPORTED_AUDIO_EXTENSIONS;
import static com.driot.bookplayer.global.Var.SUPPORTED_COVER_PICTURE_EXTENSIONS;
import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Tonio.getMimeType;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.imports.ImportHelper;
import com.driot.bookplayer.imports.ImportJob;
import com.driot.bookplayer.imports.ImportWorker;
import com.driot.bookplayer.services.archives.ArchiveDispatch;
import com.driot.bookplayer.services.archives.ArchiveExtractor;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.*;

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

            // Clean non-audio/image
            if (unzipFolder.listFiles() != null) {
                for (File f : unzipFolder.listFiles()) {
                    String mime = getMimeType(f);
                    String ext = getExtension(f.getName());
                    // Check if mime is null before calling startsWith
                    boolean isAudioOrImage = (mime != null && mime.startsWith(ONLY_MIME_AUDIO))
                            || SUPPORTED_AUDIO_EXTENSIONS.contains(ext)
                            || SUPPORTED_COVER_PICTURE_EXTENSIONS.contains(ext);
                    if (!isAudioOrImage) {
                        if (!f.delete()) myLogE("Could not delete non-audio/image: " + f.getName());
                    }
                }
            }

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

}
