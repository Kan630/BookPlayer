/*
package com.driot.bookplayer.utils;


import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.objects.LoadBookTaskState;

public class TaskUiManager {

    private static TaskUiManager instance;
    private static Context appContext;

    private String taskTitle = "";
    private String progressText = "";
    private int progressPercent = 0;
    private boolean taskRunning = false;

    private Runnable uiCallback;

    private TaskUiManager() {
        if (appContext != null) {
            LoadBookTaskState state = Pref.getLoadBookTaskState(appContext, false);
            if (state != null && state.onGoingLoading) {
                this.taskRunning = true;
                this.taskTitle = state.title;
                this.progressPercent = state.progressPercent;
                this.progressText = state.progressText;
            }
        }
    }

    // This should be called at least once early in the app lifecycle (e.g. in Application.onCreate)
    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }
    public void reInit() {
        LoadBookTaskState state = Pref.getLoadBookTaskState(appContext);
        if (state != null && state.onGoingLoading) {
            this.taskRunning = true;
            this.taskTitle = state.title != null ? state.title : "";
            this.progressPercent = state.progressPercent;
            this.progressText = state.progressText != null ? state.progressText : "";
        } else {
            this.taskRunning = false;
            this.taskTitle = "";
            this.progressPercent = 0;
            this.progressText = "";
        }
    }

    public static TaskUiManager getInstance() {
        if (instance == null) {
            instance = new TaskUiManager();
        }
        return instance;
    }
    public LoadBookTaskState getCurrentTaskState() {
        return Pref.getLoadBookTaskState(appContext);
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

    public void updateProgressText(String text) {
        this.progressText = text;
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



    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }


}

 */
