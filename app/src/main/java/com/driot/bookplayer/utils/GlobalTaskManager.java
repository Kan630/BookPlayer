package com.driot.bookplayer.utils;

import static com.driot.bookplayer.global.Var.FOLDER_DOWNLOAD;
import static com.driot.bookplayer.utils.KanFiles.deleteFolderRecursive;

import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.driot.bookplayer.services.AddResourceService;
import com.driot.bookplayer.services.CopyFileService;
import com.driot.bookplayer.services.DownloadJobService;
import com.driot.bookplayer.services.DownloadService;
import com.driot.bookplayer.services.SplitM4bService;
import com.driot.bookplayer.services.UnzipService;

public class GlobalTaskManager {

    private static GlobalTaskManager instance;
    private String taskTitle = "";
    private String progressText = "";
    private int progressPercent = 0;
    private boolean taskRunning = false;

    private Runnable uiCallback;

    public static GlobalTaskManager getInstance() {
        if (instance == null) instance = new GlobalTaskManager();
        return instance;
    }

    public interface TaskListener {
        void onTaskFinished();
    }

    private final List<TaskListener> listeners = new ArrayList<>();

    public void registerListener(TaskListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void unregisterListener(TaskListener listener) {
        listeners.remove(listener);
    }

    public void notifyTaskFinished() {
        myLog("notifyTaskFinished()");
        endTask();
        for (TaskListener listener : listeners) {
            listener.onTaskFinished();
        }
    }

    public void startTask(String title) {
        myLog("startTask()");
        this.taskTitle = title;
        this.taskRunning = true;
        notifyUi();
    }

    public void updateProgress(String text, int percent) {
        this.progressText = text;
        this.progressPercent = percent;
        notifyUi();
    }

    public void endTask() {
        myLog("endTask()");
        this.taskRunning = false;
        this.taskTitle = "";
        this.progressText = "";
        this.progressPercent = 0;
        notifyUi();
    }

    public boolean isTaskRunning() { return taskRunning; }
    public String getTaskTitle() { return taskTitle; }
    public String getProgressText() { return progressText; }
    public int getProgressPercent() { return progressPercent; }

    public void setUiCallback(Runnable callback) {
        this.uiCallback = callback;
    }

    public void clearUiCallback() {
        this.uiCallback = null;
    }

    private void notifyUi() {
        if (uiCallback != null) uiCallback.run();
    }


    public boolean isSomeWorkFlowRunning(Context c) {
        return DownloadJobService.isJobRunning ||
                SplitM4bService.isSplitRunning ||
                UnzipService.isUnzipRunning ||
                CopyFileService.isCopyRunning ||
                DownloadService.isBusy ||
                AddResourceService.isBusy;
    }

    public void cancelAllOngoingTasks(Context context) {
        myLog("...cancelAllOngoingTasks() - from " + context.getClass().getSimpleName());

        DownloadJobService.isJobRunning = false;
        UnzipService.isUnzipRunning = false;
        SplitM4bService.isSplitRunning = false;
        CopyFileService.isCopyRunning = false;
        AddResourceService.isBusy = false;
        DownloadService.isBusy = false;

        context.stopService(new Intent(context, AddResourceService.class));
        context.stopService(new Intent(context, CopyFileService.class));
        context.stopService(new Intent(context, UnzipService.class));
        context.stopService(new Intent(context, SplitM4bService.class));

        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) scheduler.cancelAll();

        String downloadDirPath = context.getFilesDir().getAbsolutePath() + "/" + FOLDER_DOWNLOAD;
        deleteFolderRecursive(downloadDirPath);
        new File(downloadDirPath).mkdirs();

        endTask();
    }

    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogI(String str) { KanLogger.myLogI(this.getClass().getName(), str); }
    private void myLogW(String str) { KanLogger.myLogW(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    private void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, this.getClass().getName(), str); }
    private void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, this.getClass().getName(), str); }

}
