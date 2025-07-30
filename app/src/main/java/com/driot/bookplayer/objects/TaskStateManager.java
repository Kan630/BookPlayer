package com.driot.bookplayer.objects;

import android.content.Context;

import com.driot.bookplayer.activities.OngoingTaskViewModelBridge;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.utils.KanLogger;

public class TaskStateManager {
    private static Context appContext;

    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    public static void markImportFinished() {
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        state.onGoingLoading = false;
        Pref.setLoadBookTaskState(state);
    }

    private static void updateTaskProgress(Context context, int percent, String progressText, String phase, boolean isLoadingPaused) {
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        if (state != null) {
            state.progressPercent = percent;
            state.progressText = progressText;
            state.onGoingLoading = true;
            state.currentLoadingOperation = phase;
            state.isLoadingPaused = isLoadingPaused;
            Pref.setLoadBookTaskState(state);
        }
    }

    public static void markDownloadResuming(Context context) {
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        String currentLoadingOperation = "Download resuming";
        state.isLoadingPaused = false;
        state.currentLoadingOperation = currentLoadingOperation;
        Pref.setLoadBookTaskState(state);
        OngoingTaskViewModelBridge.updateProgressText(currentLoadingOperation);
    }

    public static void markDownloadIsPaused(Context context) {
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        if (state != null) {
            String currentLoadingOperation = "Download paused";
            state.isLoadingPaused = true;
            state.currentLoadingOperation = currentLoadingOperation;
            if (!state.progressText.endsWith("(paused)")) {
                state.progressText = state.progressText + " (paused)";
            }
            Pref.setLoadBookTaskState(state);
            OngoingTaskViewModelBridge.updateProgressText(currentLoadingOperation);
        } else {
            myLogEE(null, "markIsPaused - No valid LoadBookTaskState found");
        }
    }

    public static void markDownloadProgress(Context context, int percent, String text) {
        updateTaskProgress(context, percent, text, "Downloading", false);
        OngoingTaskViewModelBridge.updateProgressFull(text, percent);
    }

    public static void markSplitComplete(Context context, String destPath) {
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        if (state != null) {
            String currentLoadingOperation = "m4b Split completed";
            state.currentLoadingOperation = currentLoadingOperation;
            Pref.setLoadBookTaskState(state);
            OngoingTaskViewModelBridge.updateProgressText(currentLoadingOperation);
        } else {
            myLogEE(null, "markSplitComplete - No valid LoadBookTaskState found");
        }
    }

    public static void markTaskCancelled(Context context, String taskName) {
        String currentLoadingOperation = taskName + " cancelled";
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        if (state != null) {
            state.currentLoadingOperation = currentLoadingOperation;
            state.onGoingLoading = false;
            Pref.setLoadBookTaskState(state);
            tellError(currentLoadingOperation);
        } else {
            myLogEE(null, "markTaskFailed - No valid LoadBookTaskState found - " + currentLoadingOperation);
        }
    }


    public static void markTaskFailed(String taskName, String errorText) {
        markTaskFailed(appContext, taskName, errorText);
    }

    public static void markTaskFailed(Context context, String taskName, String errorText) {
        String currentLoadingOperation = taskName + " failed - [" + errorText + "]";
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        if (state != null) {
            state.currentLoadingOperation = currentLoadingOperation;
            state.onGoingLoading = false;
            Pref.setLoadBookTaskState(state);
            tellError(currentLoadingOperation);
        } else {
            myLogEE(null, "markTaskFailed - No valid LoadBookTaskState found - " + currentLoadingOperation);
        }
    }

    public static void markTaskCompleted(Context context, String taskName, String destinationFolderPath) {
        String currentLoadingOperation = taskName + " completed - [" + destinationFolderPath + "]";
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        if (state != null) {
            state.currentLoadingOperation = currentLoadingOperation;
            state.dynamicDestinationFolderPath = destinationFolderPath;
            Pref.setLoadBookTaskState(state);
            myLogI(currentLoadingOperation);
        } else {
            myLogEE(null, "markTaskFailed - No valid LoadBookTaskState found - " + currentLoadingOperation);
        }
    }

    public static void tellWarning(String warningText) {
        myLogW("Warning: " + warningText);
        OngoingTaskViewModelBridge.tellWarning(warningText);
    }
    public static void tellProgress(int progress, String progressText) {
        OngoingTaskViewModelBridge.tellProgress(progress, progressText);
    }
    private static void tellError(String errorText) { //private because you need to always call markTaskFailed
        myLogE("Error: " + errorText);
        OngoingTaskViewModelBridge.tellError(errorText);
    }



    private static final String TAG = "TaskStateManager";
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }
}
