package com.driot.bookplayer.objects;

import static com.driot.bookplayer.utils.WorkFlow.cancelAllOngoingTasks;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.MainActivity;
import com.driot.bookplayer.activities.OngoingTaskViewModelBridge;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.utils.KanLogger;
import com.driot.bookplayer.utils.WorkFlow;

public class TaskStateManager {
    private static Context appContext;

    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    public static void tellEnd() {
        OngoingTaskViewModelBridge.tellEnd();

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

    public static void markDownloadCompleted(String taskName, String destinationFolderPath) {
        markTaskCompleted(taskName, destinationFolderPath);
        OngoingTaskViewModelBridge.removePauseCapability();
    }

    public static void markUnzipCompleted(String taskName, String destinationFolderPath) {
        markTaskCompleted(taskName, destinationFolderPath);
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        if (state != null) {
            state.dynamicType = "Folder";
            state.dynamicUri = Uri.parse(destinationFolderPath);
            Pref.setLoadBookTaskState(state);
        } else {
            myLogEE(null, "markUnzipCompleted - state == null");
        }
    }


    public static void markDownloadProgress(Context context, int percent, String text) {
        updateTaskProgress(context, percent, text, "Downloading", false);
        OngoingTaskViewModelBridge.updateProgressFull(text, percent);
    }

    public static void markTaskCancelled(String taskName) {
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

    public static void markTaskCompleted(String taskName, String destinationItem) {
        String currentLoadingOperation = taskName + " completed - [" + destinationItem + "]";
        LoadBookTaskState state = Pref.getLoadBookTaskState();
        if (state != null) {
            state.currentLoadingOperation = currentLoadingOperation;
            state.dynamicDestinationFolderPath = destinationItem;
            state.dynamicSourceFilePath = destinationItem;
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
