package com.driot.bookplayer.imports;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.utils.log.LoggingWorker;

public abstract class ImportWorker extends LoggingWorker {
    public static final String KEY_IMPORT_ID = "importId";
    protected final ImportJobRepository repo;
    protected final String importId;

    public ImportWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
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
        repo.setStatus(importId, ImportJob.S_RUNNING, text);
        repo.setProgress(importId, text, global);
        setProgressAsync(new Data.Builder().putInt("progressPercent", global).putString("progressText", text).build());
    }
    protected void emitTextOnlyProgress(String text) {
        ImportJob j = jobOrFail();
        repo.setStatus(importId, ImportJob.S_RUNNING, text);
        repo.setProgressText(importId, text);
        setProgressAsync(new Data.Builder().putString("progressText", text).build());
    }

    protected void emitWarning(String warn)
        { repo.setWarning(importId, warn); }

    protected void emitFailed(String taskName, String errorTextDev, String errorTextUser)
    { repo.fail(importId, errorTextDev, errorTextUser); }

    protected void emitCancelled(String taskName)
    { repo.cancel(importId); }

    protected void emitTaskCompleted(String taskName, String destinationFolderPath)
    { }

    protected void emitFinished()
    { repo.finish(importId); }

    protected void emitDownloadPause(String why)
    { repo.downloadPause(importId, why); }

    protected void emitDownloadResuming()
    { repo.downloadResuming(importId); }

    protected Data out()
        { return new Data.Builder().putString(KEY_IMPORT_ID, importId).build(); }
}

