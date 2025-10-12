package com.driot.bookplayer.imports;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.utils.log.LoggingWorker;

public abstract class ImportWorker extends LoggingWorker {
    public static final String KEY_IMPORT_ID = "importId";
    protected final ImportJobRepository repo;
    protected final String importId;

    private Context appContext;

    public ImportWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
        this.appContext = ctx.getApplicationContext();
        this.repo = new ImportJobRepository(ctx);
        this.importId = params.getInputData().getString(KEY_IMPORT_ID);
    }

    protected ImportJob jobOrFail() {
        ImportJob j = repo.get(importId);
        if (j == null) throw new IllegalStateException("ImportJob not found: " + importId);
        return j;
    }

    /** Step-scoped progress: converts 0..100 for a step into global % and writes Room. */
    protected void emitStepProgress(String stepKey, int stepPercent, String text) {
        ImportJob j = jobOrFail();
        int global = ImportProgressWeigher.toGlobalPercent(j, stepKey, stepPercent);
        repo.setProgress(importId, text, global);
        // Worker internal logic can hold progress...
        //setProgressAsync(new Data.Builder().putInt("progressPercent", global).putString("progressText", text).build());
    }
    protected void emitTextOnlyProgress(String text) {
        repo.setProgressText(importId, text);
        // Worker internal logic can hold progress...
        //setProgressAsync(new Data.Builder().putString("progressText", text).build());
    }

    protected void emitWarning(String warn) {
        myLogW("emitWarning    - warn = [" + warn + "]");
        repo.setWarning(importId, warn);
    }

    protected void emitFailed(String taskName, String errorTextDev, String errorTextUser) {
        myLogE("emitFailed " + taskName + " - errorTextDev = [" + errorTextDev + "]");
        repo.fail(importId, errorTextDev, errorTextUser);
        ImportHelper.cleanUp(appContext, true, jobOrFail().futureFolderPath);
    }

    protected void emitCancelled(String taskName) {
        myLog("emitCancelled " + taskName);
        repo.cancel(importId);
        ImportHelper.cleanUp(appContext, true, jobOrFail().futureFolderPath);
    }

    protected void emitTaskStart(String taskName, String progressText) {
        myLog("emitTaskStart " + taskName + " - progressText = [" + progressText + "]");
        repo.taskStarted(importId, taskName, progressText);
    }

    protected void emitTaskCompleted(String taskName, String destination, String progressText) {
        myLog("emitTaskCompleted " + taskName + " - destination = [" + destination + "] - progressText = [" + progressText + "]");
        if (Var.WORKER_TASK_LABEL_DOWNLOAD.equals(taskName)) {
            repo.downloadCompleted(importId, taskName, destination, appContext.getString(R.string.Download_finished));
        } else if (Var.WORKER_TASK_LABEL_SPLIT_EBOOK.equals(taskName)) {
            //TODO ugly, should certainly be somewhere else
            repo.taskCompleted(importId, taskName, destination, Var.PLAY_TYPE_TEXT, progressText);
        } else {
            repo.taskCompleted(importId, taskName, destination, Var.PLAY_TYPE_AUDIO, progressText);
        }
    }

    protected void emitSuccess() {
        myLogI("emitSuccess");
        repo.success(importId);
        ImportHelper.cleanUp(appContext, false, jobOrFail().futureFolderPath);
    }

    protected void emitDownloadPause(String why) {
        myLogI("emitDownloadPause " + why);
        repo.downloadPause(importId, why);
    }

    protected void emitDownloadResuming() {
        myLogI("emitDownloadResuming");
        repo.downloadResuming(importId);
    }

    protected Data out()
        { return new Data.Builder().putString(KEY_IMPORT_ID, importId).build(); }

    public static class ImportAbortException extends RuntimeException {
        public final Data out;
        public ImportAbortException(String taskName, String devMsg, String userMsg) {
            super(userMsg);
            this.out = new Data.Builder()
                    .putString("error_task", taskName)
                    .putString("errorTextDev", devMsg)
                    .putString("errorTextUsr", userMsg)
                    .build();
        }
    }

    protected void failNow(String taskName, String devMsg, String userMsg) {
        repo.fail(importId, devMsg, userMsg); // persist failure
        //throw new ImportAbortException(taskName, devMsg, userMsg);
    }

    protected Result failResult(String taskName, String devMsg, String userMsg) {
        repo.fail(importId, devMsg, userMsg); // persist failure in Room
        return Result.failure(
                new Data.Builder()
                        .putString("error_task", taskName)
                        .putString("errorTextDev", devMsg)
                        .putString("errorTextUsr", userMsg)
                        .build()
        );
    }

}

