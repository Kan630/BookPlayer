package com.driot.bookplayer.objects;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.objects.TaskStateRepository;
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
            this.order = order; this.weight = weight; this.label = label;
        }
    }

    private static final Map<String, StepInfo> stepMap = new LinkedHashMap<>();
    static {
        stepMap.put(Var.WORKER_TASK_LABEL_DOWNLOAD, new StepInfo(1, 20, "Downloading..."));
        stepMap.put(Var.WORKER_TASK_LABEL_COPY,     new StepInfo(2, 3,  "Copying files..."));
        stepMap.put(Var.WORKER_TASK_LABEL_UNZIP,    new StepInfo(3, 7,  "Unzipping..."));
        stepMap.put(Var.WORKER_TASK_LABEL_SPLIT_M4B,new StepInfo(4, 7,  "Splitting M4B..."));
        stepMap.put(Var.WORKER_TASK_LABEL_SPLIT_EBOOK,new StepInfo(4,7,"Splitting EPUB..."));
        stepMap.put(Var.WORKER_TASK_LABEL_SCAN,     new StepInfo(5, 2,  "Scanning audio..."));
    }

    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    // ---- Lifecycle edges ----

    public static void tellStart() {
        myLogD("tellStart");
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        if (state != null) {
            state.onGoingLoading = true;
            state.currentOperation = "initialization";
            Pref.setLoadBookTaskState(state);

            // Repo updates
            String title = state.title != null ? state.title : "";
            TaskStateRepository.get().start(title);
            TaskStateRepository.get().setCurrentOperation("initialization");
            boolean httpLike = (state.originalUri != null) &&
                    String.valueOf(state.originalUri).startsWith("http");
            TaskStateRepository.get().setPauseAvailable(httpLike);
            TaskStateRepository.get().setPaused(false);

            // reset cached calculations for a fresh run
            cachedState = state;
            totalWeight = 0;
            titleUI = "initializing";
        } else {
            myLogEE(null, "tellStart - state == null");
        }
    }

    public static void tellEnd() {
        TaskStateRepository.get().finish();

        // Kind of garbage collector
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> WorkFlow.cancelAllOngoingTasks(appContext), 500);
    }

    // ---- Markers that also persist sticky state in Pref ----

    public static void markDownloadResuming(Context context) {
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        String currentOperation = "Download resuming";
        if (state != null) {
            state.isLoadingPaused = false;
            state.currentOperation = currentOperation;
            Pref.setLoadBookTaskState(state);
        }
        TaskStateRepository.get().setPaused(false);
        TaskStateRepository.get().setCurrentOperation(currentOperation);
        TaskStateRepository.get().setProgressText(currentOperation);
    }

    public static void markDownloadCompleted(String taskName, String downloadedFileFullPath) {
        String currentOperation = taskName + " completed - [" + downloadedFileFullPath + "]";
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
        TaskStateRepository.get().setCurrentOperation(currentOperation);
        TaskStateRepository.get().setPauseAvailable(false);
        TaskStateRepository.get().setPaused(false);
    }

    public static void markUnzipCompleted(String taskName, String destinationFolderPath) {
        String currentOperation = taskName + " completed - [" + destinationFolderPath + "]";
        updateDynamicFolder(currentOperation, destinationFolderPath);
        TaskStateRepository.get().setCurrentOperation(currentOperation);
    }

    public static void markM4bSplitCompleted(String taskName, String destinationFolderPath) {
        String currentOperation = taskName + " completed - [" + destinationFolderPath + "]";
        updateDynamicFolder(currentOperation, destinationFolderPath);
        TaskStateRepository.get().setCurrentOperation(currentOperation);
    }

    public static void markEpubSplitCompleted(String taskName, String destinationFolderPath) {
        String currentOperation = taskName + " completed - [" + destinationFolderPath + "]";
        updateDynamicFolder(currentOperation, destinationFolderPath);
        TaskStateRepository.get().setCurrentOperation(currentOperation);
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
        TaskStateRepository.get().setCurrentOperation(currentOperation);
    }

    // ---- Progress / pause / cancel / fail ----

    public static void markDownloadProgress(Context context, String taskName, int percent, String text) {
        updateTaskProgress(context, percent, text, "Downloading", false);
        tellProgress(taskName, percent, text);
    }

    public static void markTaskPaused(String whyPaused) {
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        if (state != null) {
            state.currentOperation = whyPaused;
            state.isLoadingPaused = true;
            Pref.setLoadBookTaskState(state);
            tellWarning(whyPaused);
            TaskStateRepository.get().setPaused(true);
            TaskStateRepository.get().setCurrentOperation(whyPaused);
            TaskStateRepository.get().setProgressText(whyPaused);
        } else {
            myLogEE(null, "markTaskPaused - No valid LoadBookTaskState found - " + whyPaused);
        }
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
            myLogEE(null, "markTaskCancelled - No valid LoadBookTaskState found - " + currentOperation);
        }
        TaskStateRepository.get().error(currentOperation);
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
        TaskStateRepository.get().error(currentOperation);
        WorkFlow.cancelAllOngoingTasks(appContext);
    }

    // ---- Tell* helpers (now route to repo) ----

    private static void tellCurrentOperation(String currentOperation) {
        TaskStateRepository.get().setCurrentOperation(currentOperation);
    }

    public static void tellProgress(String taskName, int progress, String progressText) {
        int realProgress = getRealProgress(taskName, progress);
        TaskStateRepository.get().progress(realProgress, progressText);
        checkTitle(taskName);
    }

    public static void tellProgressText(String progressText) {
        TaskStateRepository.get().setProgressText(progressText);
    }

    public static void tellWarning(String warningText) {
        myLogW("Warning: " + warningText);
        TaskStateRepository.get().warning(warningText);
    }

    private static void tellError(String errorText) { // keep private: always go through markTaskFailed/cancelled
        myLogE("Error: " + errorText);
        TaskStateRepository.get().error(errorText);
    }

    // ---- Internals ----

    private static void updateTaskProgress(Context context, int percent, String progressText, String phase, boolean isLoadingPaused) {
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        if (state != null) {
            state.progressPercent = percent;
            state.progressText = progressText;
            state.onGoingLoading = true;
            state.currentOperation = phase;
            state.isLoadingPaused = isLoadingPaused;
            Pref.setLoadBookTaskState(state);
            cachedState = state; // keep cache fresh
        }
    }

    private static void updateDynamicFolder(@NonNull String currentOperation, @NonNull String path) {
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        if (state != null) {
            state.currentOperation = currentOperation;
            state.dynamicType = "Folder";
            state.dynamicUri = Uri.parse(path);
            state.dynamicDestinationFolderPath = path;
            state.dynamicSourceFilePath = path;
            Pref.setLoadBookTaskState(state);
        } else {
            myLogEE(null, "updateDynamicFolder - state == null");
        }
    }

    private static int getTotalWeight() {
        if (totalWeight > 0) return totalWeight;
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        if (state == null) return 1;
        if (state.doDownload) totalWeight += stepMap.get(Var.WORKER_TASK_LABEL_DOWNLOAD).weight;
        if (state.doUnzip) totalWeight += stepMap.get(Var.WORKER_TASK_LABEL_UNZIP).weight;
        if (state.doSplitM4b) totalWeight += stepMap.get(Var.WORKER_TASK_LABEL_SPLIT_M4B).weight;
        if (state.doCopy) totalWeight += stepMap.get(Var.WORKER_TASK_LABEL_COPY).weight;
        totalWeight += stepMap.get(Var.WORKER_TASK_LABEL_SCAN).weight; // Always run
        return totalWeight;
    }
    private static LoadBookTaskState getCachedState() {
        /*
        if (cachedState == null) {
            myLogD("reload cachedState");
            cachedState = Pref.getLoadBookTaskState();
        }
        return cachedState;
         */
        return Pref.getLoadBookTaskState();
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
            else if (Var.WORKER_TASK_LABEL_SPLIT_M4B.equals(key)) isEnabled = state.doSplitM4b;
            else if (Var.WORKER_TASK_LABEL_COPY.equals(key)) isEnabled = state.doCopy;
            else if (Var.WORKER_TASK_LABEL_SCAN.equals(key)) isEnabled = true;

            if (!isEnabled) continue;

            if (key.equals(taskName)) {
                float partialProgress = (percent / 100f) * info.weight;
                float totalProgress = (accumulatedWeight + partialProgress) / totalWeight * 100f;
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
}
