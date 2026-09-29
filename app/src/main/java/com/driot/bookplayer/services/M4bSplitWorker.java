package com.driot.bookplayer.services;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.imports.ImportHelper;
import com.driot.bookplayer.imports.ImportJob;
import com.driot.bookplayer.imports.ImportWorker;
import com.driot.bookplayer.objects.AudioInfo;
import com.driot.bookplayer.objects.AudioProber;
import com.driot.bookplayer.services.m4b.M4bSplitter;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class M4bSplitWorker extends ImportWorker {

    private static final String TASK_NAME = Var.WORKER_TASK_LABEL_SPLIT_M4B;

    private final Context context;

    public M4bSplitWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context.getApplicationContext();
    }

    @NonNull
    @Override
    public Result doWorkBody() {
        emitTaskStart(TASK_NAME,
                context.getString(R.string.import_task_m4b_split) + " " +
                        context.getString(R.string.import_task_start));
        ImportJob j = jobOrFail();

        final String m4bFilePath = ImportHelper.getSourceFilePathForWorker(j);
        final String destinationFolderPath = j.futureFolderPath;

        myLogD("----------------------------------------------------");
        myLog("m4bFilePath = " + m4bFilePath);
        myLog("destinationFolderPath = " + destinationFolderPath);
        myLogD("----------------------------------------------------");

        if (m4bFilePath == null || destinationFolderPath == null) {
            emitFailed(TASK_NAME, "Missing input data for M4bSplitWorker",
                    getApplicationContext().getString(R.string.invalid_resource));
            myLogEE(null, "Missing input data for M4bSplitWorker");
            return Result.failure();
        }

        FirebaseAnalyticsHelper.logEvent("m4b_worker");

        boolean success = splitM4bLocal(m4bFilePath, destinationFolderPath);
        return success ? Result.success() : Result.failure();
    }

    private boolean splitM4bLocal(String m4bFilePath, String destinationFolderPath) {
        Context context = getApplicationContext();
        File m4bFile = new File(m4bFilePath);

        // --- METADATA ---
        emitTextOnlyProgress(getApplicationContext().getString(R.string.parsing_metadata));
        try {
            AudioInfo audioInfo = AudioProber.probe(context, Uri.fromFile(new File(m4bFilePath)), true);
            if (audioInfo != null && audioInfo.cover != null) {
                audioInfo.saveCover(this.getApplicationContext());
            }
        } catch (Exception e) {
            myLogEE(e, "Error Parsing Metadata");
        }

        File outputFolder = new File(destinationFolderPath);
        boolean outputFolderExistedBefore = outputFolder.exists();
        // noinspection ResultOfMethodCallIgnored
        outputFolder.mkdirs();

        // Split into a temporary folder, verify every chapter file decodes, only then replace the M4B.
        // Anything doubtful -> NOT_SPLIT with the M4B untouched: never worse than an unsplit import.
        M4bSplitter.Outcome o = new M4bSplitter(new M4bSplitter.Host() {
            @Override
            public boolean isCancelled() {
                return isStopped();
            }

            @Override
            public void onChapter(int index, int count, String title) {
                double progress = (double) (index + 1) / count * 100;
                String text = context.getString(R.string.Import_Progress_splitting_m4b_file)
                        + (index + 1) + "/" + count + "\n\n"
                        + context.getString(R.string.Import_Progress_chapter_title) + " : " + title;
                emitStepProgress(TASK_NAME, (int) progress, text);
            }
        }).split(m4bFile, outputFolder);

        switch (o.kind) {
            case SPLIT:
                if (!M4bSplitter.METHOD_MP4PARSER.equals(o.method)) {
                    // mp4parser couldn't read this book, the fallback split it: worth counting
                    FirebaseAnalyticsHelper.logEvent("m4b_split_rescued");
                    myLogI("M4B split by the fallback (" + o.method + ") | " + o.boxMap);
                }
                try {
                    ImportJob job = jobOrFail();
                    JSONObject meta = new JSONObject(
                            job.metadataJson != null && !job.metadataJson.isEmpty() ? job.metadataJson : "{}");
                    meta.put("track_titles", o.titles);
                    job.metadataJson = meta.toString();
                    repo.upsert(job);
                } catch (Exception e) {
                    myLogEE(e, "Error saving track_titles to metadataJson");
                }
                emitTaskCompleted(TASK_NAME, outputFolder.getAbsolutePath(),
                        context.getString(R.string.import_task_m4b_split) + " " + context.getString(R.string.done));
                return true;

            case CANCELLED:
                emitCancelled(TASK_NAME);
                return false;

            case NO_SPACE: {
                myLogEE(o.error, "splitM4bLocal - disk full (ENOSPC)");
                String userMsg = context.getString(R.string.error_no_space_left)
                        + "\n\n" + context.getString(R.string.solution_free_space);
                emitWarning(userMsg);
                emitFailed(TASK_NAME, "No space left on device", context.getString(R.string.error_no_space_left));
                return false;
            }

            case NOT_SPLIT:
            default:
                // counted (log_w) with the reason and the file structure, to see which fallback is still missing
                myLogWA(o.error, "M4B not split: " + o.reason + (o.boxMap != null ? " | " + o.boxMap : ""));
                return fallbackToSingleM4b(context, m4bFile, outputFolder, outputFolderExistedBefore,
                        new ArrayList<>(), userMessageFor(context, o), "splitM4bLocal - " + o.reason);
        }
    }

    /** Same user messages as before, chosen from the error behind the refusal. */
    private static String userMessageFor(Context context, M4bSplitter.Outcome o) {
        String msg = "";
        if (o.error != null) {
            String raw = o.error.getMessage() != null ? o.error.getMessage() : "";
            String cause = o.error.getCause() != null && o.error.getCause().getMessage() != null
                    ? o.error.getCause().getMessage() : "";
            msg = (raw + " " + cause + " " + o.error).toLowerCase(Locale.ROOT);
        }
        boolean tooLarge = msg.contains("map failed") || msg.contains("mmap") || msg.contains("filechannelimpl.map")
                || msg.contains("cannot allocate") || msg.contains("outofmemory") || msg.contains("enomem")
                || msg.contains("scudo") || msg.contains("markcompact") || msg.contains("kernelpreparerange")
                || msg.contains("size >") || msg.contains("too large");
        if (tooLarge)
            return context.getString(R.string.m4b_error_too_large_or_incompatible_structure);
        if (o.error == null || msg.contains("no suitable") || msg.contains("required audio or chapter")
                || msg.contains("parse") || msg.contains("box") || msg.contains("corrupt"))
            return context.getString(R.string.m4b_error_non_standard_chapter_format);
        return context.getString(R.string.Import_Experimental_M4B_warning)
                + "\n\n" + context.getString(R.string.Import_Experimental_M4B_iferror)
                + ", " + context.getString(R.string.Import_Experimental_M4B_solution_1)
                + "\n" + context.getString(R.string.Import_Experimental_M4B_solution_2)
                + "\n\n"
                + context.getString(R.string.m4b_error_will_import_as_single_file);
    }





    // NEW: remove partial chapter files if something went wrong
    private void cleanupPartialOutputs(List<File> createdFiles,
            File outputFolder,
            boolean folderExistedBefore) {
        if (createdFiles != null) {
            for (File f : createdFiles) {
                if (f != null && f.exists() && f.isFile() && f.getName().endsWith(".aac")) {
                    if (!f.delete()) {
                        myLogE("Could not delete partial chapter file: " + f.getAbsolutePath());
                    }
                }
            }
        }

        // If we created the folder just for this import and it is now empty, try to
        // remove it
        if (!folderExistedBefore && outputFolder != null && outputFolder.isDirectory()) {
            File[] remaining = outputFolder.listFiles();
            if (remaining == null || remaining.length == 0) {
                // noinspection ResultOfMethodCallIgnored
                outputFolder.delete();
            }
        }
    }

    // Fallback: keep single M4B, remove partial .aac, still mark task as completed
    private boolean fallbackToSingleM4b(Context context,
            File m4bFile,
            File outputFolder,
            boolean outputFolderExistedBefore,
            List<File> createdChapterFiles,
            String warningMessageForUser,
            String logTag) {

        FirebaseAnalyticsHelper.logEvent("m4b_fallback");

        // 1) Log + warn
        myLogE(logTag + " - falling back to single M4B import");
        if (warningMessageForUser != null && !warningMessageForUser.isEmpty()) {
            emitWarning(warningMessageForUser);
        } else {
            emitWarning("Error while splitting M4B. Importing original file instead.");
        }

        // 2) Cleanup any partial .aac files
        cleanupPartialOutputs(createdChapterFiles, outputFolder, outputFolderExistedBefore);

        // 3) Make sure M4B is inside the output folder, so the next step sees it
        try {
            if (outputFolder == null) {
                outputFolder = m4bFile.getParentFile();
            }

            if (outputFolder != null && !outputFolder.equals(m4bFile.getParentFile())) {
                File destM4b = new File(outputFolder, m4bFile.getName());
                if (!destM4b.equals(m4bFile)) {
                    // Try to move; if it fails, stay in original folder
                    if (!m4bFile.renameTo(destM4b)) {
                        myLogE("Could not move M4B to output folder, keeping original location: "
                                + m4bFile.getAbsolutePath());
                        // fall back: use the M4B parent as task path
                        outputFolder = m4bFile.getParentFile();
                    } else {
                        m4bFile = destM4b;
                    }
                }
            }
        } catch (Exception moveEx) {
            myLogEE(moveEx, "fallbackToSingleM4b - error while moving M4B");
            // If move fails, we still have the original file somewhere; import code
            // will just see it where it is.
        }

        // 4) Mark task as "completed with fallback" so the pipeline continues
        String completedMsg = context.getString(R.string.import_task_m4b_split) + " - "
                + context.getString(R.string.Import_Experimental_M4B_iferror);
        String pathForNextStep = (outputFolder != null ? outputFolder.getAbsolutePath() : m4bFile.getParent());

        emitTaskCompleted(TASK_NAME, pathForNextStep, completedMsg);

        // We return true so doWorkBody() returns Result.success()
        return true;
    }

}
