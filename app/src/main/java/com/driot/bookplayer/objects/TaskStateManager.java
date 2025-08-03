package com.driot.bookplayer.objects;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.driot.bookplayer.activities.OngoingTaskViewModelBridge;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.utils.KanLogger;

import java.util.LinkedHashMap;
import java.util.Map;

public class TaskStateManager {
    private static Context appContext;

    private static String titleUI = "initializing";
    private static int totalWeight = 0;
    private static LoadBookTaskState cachedState = null;

    static class StepInfo {
        public final int order;
        public final int weight;
        public final String label;

        public StepInfo(int order, int weight, String label) {
            this.order = weight;
            this.weight = weight;
            this.label = label;
        }
    }
    private static final Map<String, StepInfo> stepMap = new LinkedHashMap<>();
    static {
        stepMap.put(Var.WORKER_TASK_LABEL_DOWNLOAD, new StepInfo(1, 20, "Downloading..."));
        stepMap.put(Var.WORKER_TASK_LABEL_COPY, new StepInfo(2, 3, "Copying files..."));
        stepMap.put(Var.WORKER_TASK_LABEL_UNZIP, new StepInfo(3, 7, "Unzipping..."));
        stepMap.put(Var.WORKER_TASK_LABEL_SPLIT, new StepInfo(4, 7, "Splitting M4B..."));
        stepMap.put(Var.WORKER_TASK_LABEL_SCAN, new StepInfo(5, 2, "Scanning audio..."));
    }

    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    public static void tellEnd() {
        OngoingTaskViewModelBridge.tellEnd(appContext);

        //Kind of garbage collector
        final Handler handler = new Handler(Looper.getMainLooper());
        Runnable runnable = () -> {
            WorkFlow.cancelAllOngoingTasks(appContext);
        };
        handler.postDelayed(runnable, 500);

    }

    private static void updateTaskProgress(Context context, int percent, String progressText, String phase, boolean isLoadingPaused) {
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        if (state != null) {
            state.progressPercent = percent;
            state.progressText = progressText;
            state.onGoingLoading = true;
            state.currentOperation = phase;
            state.isLoadingPaused = isLoadingPaused;
            Pref.setLoadBookTaskState(state);
        }
    }

    public static void markDownloadResuming(Context context) {
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        String currentOperation = "Download resuming";
        state.isLoadingPaused = false;
        state.currentOperation = currentOperation;
        Pref.setLoadBookTaskState(state);
        OngoingTaskViewModelBridge.updateProgressText(appContext, currentOperation);
    }

    public static void markDownloadIsPaused(Context context) {
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        String currentOperation = "Download paused";
        tellCurrentOperation(currentOperation);
        if (state != null) {
            state.isLoadingPaused = true;
            state.currentOperation = currentOperation;
            if (!state.progressText.endsWith("(paused)")) {
                state.progressText = state.progressText + " (paused)";
            }
            Pref.setLoadBookTaskState(state);
            OngoingTaskViewModelBridge.updateProgressText(appContext, currentOperation);
        } else {
            myLogEE(null, "markIsPaused - No valid LoadBookTaskState found");
        }
    }

    public static void markDownloadCompleted(String taskName, String downloadedFileFullPath) {
        String currentOperation = taskName + " completed - [" + downloadedFileFullPath + "]";
        OngoingTaskViewModelBridge.removePauseCapability(appContext);
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        if (state != null) {
            state.currentOperation = currentOperation;
            state.dynamicType = "File";
            state.dynamicUri = Uri.parse(downloadedFileFullPath);
            state.dynamicSourceFilePath = downloadedFileFullPath;
            Pref.setLoadBookTaskState(state);
        } else {
            myLogEE(null, "markDownloadCompleted - state == null");
        }
    }

    public static void markUnzipCompleted(String taskName, String destinationFolderPath) {
        String currentOperation = taskName + " completed - [" + destinationFolderPath + "]";
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        if (state != null) {
            state.currentOperation = currentOperation;
            state.dynamicType = "Folder";
            state.dynamicUri = Uri.parse(destinationFolderPath);
            state.dynamicDestinationFolderPath = destinationFolderPath;
            state.dynamicSourceFilePath = destinationFolderPath;
            Pref.setLoadBookTaskState(state);
        } else {
            myLogEE(null, "markUnzipCompleted - state == null");
        }
    }

    public static void markM4bSplitCompleted(String taskName, String destinationFolderPath) {
        String currentOperation = taskName + " completed - [" + destinationFolderPath + "]";
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        if (state != null) {
            state.currentOperation = currentOperation;
            state.dynamicType = "Folder";
            state.dynamicUri = Uri.parse(destinationFolderPath);
            state.dynamicDestinationFolderPath = destinationFolderPath;
            state.dynamicSourceFilePath = destinationFolderPath;
            Pref.setLoadBookTaskState(state);
        } else {
            myLogEE(null, "markM4bSplitCompleted - state == null");
        }
    }

    public static void markCopyCompleted(String taskName, String destinationFolderPath) {
        String currentOperation = taskName + " completed - [" + destinationFolderPath + "]";
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        if (state != null) {
            state.currentOperation = currentOperation;
            state.dynamicUri = Uri.parse(destinationFolderPath);
            state.dynamicDestinationFolderPath = destinationFolderPath;
            state.dynamicSourceFilePath = destinationFolderPath;
            Pref.setLoadBookTaskState(state);
        } else {
            myLogEE(null, "markCopyCompleted - state == null");
        }
    }

    public static void markDownloadProgress(Context context, String taskName, int percent, String text) {
        updateTaskProgress(context, percent, text, "Downloading", false);
        tellProgress(taskName, percent, text);
    }

    public static void markTaskCancelled(String taskName) {
        String currentOperation = taskName + " cancelled";
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        if (state != null) {
            state.currentOperation = currentOperation;
            state.onGoingLoading = false;
            Pref.setLoadBookTaskState(state);
            tellError(currentOperation);
        } else {
            myLogEE(null, "markTaskFailed - No valid LoadBookTaskState found - " + currentOperation);
        }
    }

    public static void markTaskFailed(String taskName, String errorText) {
        String currentOperation = taskName + " failed - [" + errorText + "]";
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        if (state != null) {
            state.currentOperation = currentOperation;
            state.onGoingLoading = false;
            Pref.setLoadBookTaskState(state);
            tellError(currentOperation);
        } else {
            myLogEE(null, "markTaskFailed - No valid LoadBookTaskState found - " + currentOperation);
        }
        WorkFlow.cancelAllOngoingTasks(appContext);
    }


    private static void tellCurrentOperation(String currentOperation) {
        OngoingTaskViewModelBridge.tellCurrentOperation(appContext, currentOperation);
    }
    public static void tellProgress(String taskName, int progress, String progressText) {
        int realProgress = getRealProgress(taskName, progress);
        OngoingTaskViewModelBridge.tellProgress(appContext, realProgress, progressText);
        checkTitle(taskName);
    }
    public static void tellProgressText(String progressText) {
        OngoingTaskViewModelBridge.tellProgressText(appContext, progressText);
    }
    public static void tellWarning(String warningText) {
        myLogW("Warning: " + warningText);
        OngoingTaskViewModelBridge.tellWarning(appContext, warningText);
    }
    private static void tellError(String errorText) { //private because you need to always call markTaskFailed
        myLogE("Error: " + errorText);
        OngoingTaskViewModelBridge.tellError(appContext, errorText);
    }
    public static void tellStart() {
        myLogD("tellStart");
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        if (state != null) {
            state.onGoingLoading = true;
            state.currentOperation = "initialization";
            OngoingTaskViewModelBridge.tellStart(appContext);
            Pref.setLoadBookTaskState(state);
        } else {
            myLogEE(null, "tellStart - state == null");
        }
    }

    private static int getTotalWeight() {
        if (totalWeight > 0) return totalWeight;
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        myLogD("reload totalWeight");
        if (state == null) return 1;
        if (state.doDownload) totalWeight += stepMap.get(Var.WORKER_TASK_LABEL_DOWNLOAD).weight;
        if (state.doUnzip) totalWeight += stepMap.get(Var.WORKER_TASK_LABEL_UNZIP).weight;
        if (state.doSplit) totalWeight += stepMap.get(Var.WORKER_TASK_LABEL_SPLIT).weight;
        if (state.doCopy) totalWeight += stepMap.get(Var.WORKER_TASK_LABEL_COPY).weight;
        totalWeight += stepMap.get(Var.WORKER_TASK_LABEL_SCAN).weight; // Always run
        return totalWeight;
    }
    private static LoadBookTaskState getCachedState() {
        if (cachedState == null) {
            myLogD("reload cachedState");
            cachedState = Pref.getLoadBookTaskState();
        }
        return cachedState;
    }

    private static int getRealProgress(String taskName, int percent) {
        int totalWeight = getTotalWeight();
        if (totalWeight == 0) return 0;

        LoadBookTaskState state = getCachedState();
        if (state == null) return 0;

        int accumulatedWeight = 0;

        for (Map.Entry<String, StepInfo> entry : stepMap.entrySet()) {
            String key = entry.getKey();
            StepInfo info = entry.getValue();

            boolean isEnabled = false;
            if (Var.WORKER_TASK_LABEL_DOWNLOAD.equals(key)) isEnabled = state.doDownload;
            else if (Var.WORKER_TASK_LABEL_UNZIP.equals(key)) isEnabled = state.doUnzip;
            else if (Var.WORKER_TASK_LABEL_SPLIT.equals(key)) isEnabled = state.doSplit;
            else if (Var.WORKER_TASK_LABEL_COPY.equals(key)) isEnabled = state.doCopy;
            else if (Var.WORKER_TASK_LABEL_SCAN.equals(key)) isEnabled = true;

            if (!isEnabled) continue;

            if (key.equals(taskName)) {
                float partialProgress = (percent / 100f) * info.weight;
                float totalProgress = (accumulatedWeight + partialProgress) / totalWeight * 100;
                return (int) totalProgress;
            }

            accumulatedWeight += info.weight;
        }

        return 0;
    }
    private static void checkTitle(String taskName) {
        StepInfo info = stepMap.get(taskName);
        if (info != null && !info.label.equals(titleUI)) {
            titleUI = info.label;
            tellCurrentOperation(info.label);
        }
    }


    private static final String TAG = "TaskStateManager";
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }
}
