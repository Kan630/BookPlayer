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

    public void upsert(ImportJob job) { dao.upsert(job); }

    public ImportJob get(String id) { return dao.get(id); }

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

    public void taskCompleted(String id, String taskName, String destinationFolderPath, String playType, String progressText) {
        dao.taskComplete(id, taskName, destinationFolderPath, playType, progressText, System.currentTimeMillis());
    }

    public void downloadPause(String id, String why) {
        dao.downloadPause(id, why, System.currentTimeMillis());
    }

    public void downloadResuming(String id, String progressText) {
        dao.downloadResuming(id, progressText,  System.currentTimeMillis());
    }

    public void downloadCompleted(String id, String taskName, String downloadedFileFullPath, String progressText) {
        dao.downloadComplete(id, taskName, downloadedFileFullPath, progressText, System.currentTimeMillis());
    }

    public void fail(String id, String devErrorMsg, String usrErrorMsg) {
        dao.fail(id, devErrorMsg, usrErrorMsg, System.currentTimeMillis());
        myToast(context.getString(R.string.Import_failed));
        ImportJob j = dao.get(id);
        if (j!=null) FirebaseAnalyticsHelper.tellLoadBookFailed(j.originalUri, j.currentOperation, j.errorTextDev, j.progressText, j.fileExtension, j.doDownload);
    }

    public void cancel(String id) {
        dao.cancel(id, System.currentTimeMillis());
        ImportJob j = dao.get(id);
        if (j!=null) FirebaseAnalyticsHelper.tellLoadBookCancelled(j.originalUri, j.currentOperation, j.progressText, j.fileExtension, j.doDownload);
    }

    public void success(String id) {
        boolean hasWarnings = dao.hasWarnings(id);
        String text = hasWarnings
                ? context.getString(R.string.Import_Success_with_warnings)
                : context.getString(R.string.Import_Success);

        dao.success(id, text, System.currentTimeMillis());
        myToast(text);
        ImportJob j = dao.get(id);
        if (j!=null) FirebaseAnalyticsHelper.tellLoadBookSuccess(j.originalUri, j.fileExtension, j.doDownload);
    }

}
