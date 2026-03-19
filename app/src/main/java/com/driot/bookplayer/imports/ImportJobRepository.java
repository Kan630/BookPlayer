package com.driot.bookplayer.imports;

import android.content.Context;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.utils.log.LoggerHelper;

public class ImportJobRepository extends LoggerHelper {
    private final AppDatabase db;
    private final ImportJobDao dao;
    private final Context context;

    public ImportJobRepository(Context ctx) {
        super(ImportJobRepository.class);
        db = AppDatabase.getInstance(ctx.getApplicationContext());
        dao = db.importJobDao();
        context = ctx.getApplicationContext();
    }

    public void upsert(ImportJob job) {
        dao.upsert(job);
    }

    public ImportJob get(String id) {
        return dao.get(id);
    }

    public void setProgress(String id, String text, int pct) {
        dao.updateProgress(id, text, pct, System.currentTimeMillis());
    }

    public void setProgressText(String id, String text) {
        dao.updateProgressText(id, text, System.currentTimeMillis());
    }

    public void setWarning(String id, String w) {
        dao.appendWarning(id, w, System.currentTimeMillis());
    }

    public void taskStarted(String id, String taskName, String progressText) {
        dao.taskStart(id, taskName, progressText, System.currentTimeMillis());
    }

    public void taskCompleted(String id, String taskName, String destinationFolderPath, String playType,
            String progressText) {
        dao.taskComplete(id, taskName, destinationFolderPath, playType, progressText, System.currentTimeMillis());
    }

    public void downloadPause(String id, String why) {
        dao.downloadPause(id, why, System.currentTimeMillis());
    }

    public void downloadResuming(String id, String progressText) {
        dao.downloadResuming(id, progressText, System.currentTimeMillis());
    }

    public void downloadCompleted(String id, String taskName, String downloadedFileFullPath, String progressText) {
        dao.downloadComplete(id, taskName, downloadedFileFullPath, progressText, System.currentTimeMillis());
    }

    public void updateDownloadedFilePath(String id, String downloadedFileFullPath) {
        dao.updateDownloadedFilePath(id, downloadedFileFullPath, System.currentTimeMillis());
        myLogD("updating download file path : [" + downloadedFileFullPath + "]");
    }

    public void fail(String id, String devErrorMsg, String usrErrorMsg) {
        dao.fail(id, devErrorMsg, usrErrorMsg, System.currentTimeMillis());
        ImportJob j = dao.get(id);
        maybeToast(j, context.getString(R.string.Import_failed));
        checkBatchCompletion(j);
        if (j != null)
            FirebaseAnalyticsHelper.tellLoadBookFailed(j);
    }

    public void cancel(String id) {
        dao.cancel(id, System.currentTimeMillis());
        ImportJob j = dao.get(id);
        if (j != null)
            FirebaseAnalyticsHelper.tellLoadBookCancelled(j);
    }

    public void success(String id) {
        boolean hasWarnings = dao.hasWarnings(id);
        String text = hasWarnings
                ? context.getString(R.string.Import_Success_with_warnings)
                : context.getString(R.string.Import_Success);

        dao.success(id, text, System.currentTimeMillis());
        ImportJob j = dao.get(id);
        maybeToast(j, text);
        checkBatchCompletion(j);
        if (j != null)
            FirebaseAnalyticsHelper.tellLoadBookSuccess(j);
    }

    private void checkBatchCompletion(ImportJob j) {
        if (j == null || j.batchTotal <= 1 || j.uniqueChainName == null || j.uniqueChainName.isEmpty()) {
            return;
        }

        // Run on DB executor to avoid blocking and ensure we have latest data
        AppDatabase.databaseWriteExecutor.execute(() -> {
            int finished = dao.countBatchFinished(j.uniqueChainName);
            if (finished >= j.batchTotal) {
                int succeeded = dao.countBatchSucceeded(j.uniqueChainName);
                int failed = dao.countBatchFailed(j.uniqueChainName);
                String summary;
                if (failed == 0) {
                    summary = context.getString(R.string.Import) + ": " + succeeded + "/" + j.batchTotal + " "
                            + context.getString(R.string.succeeded);
                } else {
                    summary = context.getString(R.string.Import) + ": " + succeeded + "/" + j.batchTotal + " "
                            + context.getString(R.string.succeeded) + " (" + failed + " "
                            + context.getString(R.string.failed) + ")";
                }
                myLogI("Batch finished: " + summary);
                myToast(summary);
            }
        });
    }

    private void maybeToast(ImportJob j, String text) {
        if (j != null && j.batchTotal > 1) {
            // Suppress toast for Each item in a Mass Import
            return;
        }
        myToast(text);
    }

    public void silentSuccess(String id, String endOfProgressText) {
        dao.success(id, endOfProgressText, System.currentTimeMillis());
    }

    public void updateMetadataJson(String id, String json) {
        dao.updateMetadataJson(id, json, System.currentTimeMillis());
    }

}
