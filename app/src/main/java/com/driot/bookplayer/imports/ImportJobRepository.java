package com.driot.bookplayer.imports;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.driot.bookplayer.db.AppDatabase;

public class ImportJobRepository {
    private final AppDatabase db;
    private final ImportJobDao dao;

    public ImportJobRepository(Context ctx) {
        db = AppDatabase.getInstance(ctx.getApplicationContext());
        dao = db.importJobDao();
    }

    public void upsert(ImportJob job) { dao.upsert(job); }

    public ImportJob get(String id) { return dao.get(id); }

    public void setProgress(String id, String text, int pct) {
        dao.updateProgress(id, text, pct, System.currentTimeMillis());
    }

    public void setProgressText(String id, String text) {
        dao.updateProgressText(id, text, System.currentTimeMillis());
    }

    public void setStatus(String id, String status, String op) {
        dao.updateStatus(id, status, op, System.currentTimeMillis());
    }

    public void setWarning(String id, String w) {
        dao.updateWarning(id, w, System.currentTimeMillis());
    }

    public void fail(String id, String devErrorMsg, String usrErrorMsg) {
        dao.fail(id, devErrorMsg, usrErrorMsg, System.currentTimeMillis());
    }

    public void cancel(String id) {
        dao.cancel(id, System.currentTimeMillis());
    }

    public void finish(String id) {
        dao.finish(id, System.currentTimeMillis());
    }

    public void downloadPause(String id, String why) {
        dao.downloadPause(id, why, System.currentTimeMillis());
    }

    public void downloadResuming(String id) {
        dao.downloadResuming(id, "resuming download",  System.currentTimeMillis());
    }



    public boolean isAnyActive() {
        return dao.countActive(ImportJob.S_RUNNING, ImportJob.S_QUEUED, ImportJob.S_PAUSED) > 0;
    }
    public LiveData<Boolean> observeAnyActive() {
        return Transformations.map(
                dao.observeActiveCount(ImportJob.S_RUNNING, ImportJob.S_QUEUED, ImportJob.S_PAUSED),
                c -> c != null && c > 0
        );
    }

}
