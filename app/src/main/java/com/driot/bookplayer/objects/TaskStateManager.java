package com.driot.bookplayer.objects;

import android.content.Context;

import com.driot.bookplayer.activities.TaskViewModelBridge;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.utils.KanLogger;

public class TaskStateManager {

    private static void updateTaskProgress(Context context, int percent, String progressText, String phase, boolean isLoadingPaused) {
        LoadBookTaskState state = Pref.getLoadBookTaskState(context);
        if (state != null) {
            state.progressPercent = percent;
            state.progressText = progressText;
            state.onGoingLoading = true;
            state.currentLoadingOperation = phase;
            state.isLoadingPaused = isLoadingPaused;
            Pref.setLoadBookTaskState(context, state);
        }
    }

    public static void markDownloadResuming(Context context) {
        LoadBookTaskState state = Pref.getLoadBookTaskState(context);
        String currentLoadingOperation = "Download resuming";
        state.isLoadingPaused = false;
        state.currentLoadingOperation = currentLoadingOperation;
        Pref.setLoadBookTaskState(context, state);
        TaskViewModelBridge.updateProgressText(currentLoadingOperation);
    }

    public static void markDownloadIsPaused(Context context) {
        LoadBookTaskState state = Pref.getLoadBookTaskState(context);
        if (state != null) {
            String currentLoadingOperation = "Download paused";
            state.isLoadingPaused = true;
            state.currentLoadingOperation = currentLoadingOperation;
            if (!state.progressText.endsWith("(paused)")) {
                state.progressText = state.progressText + " (paused)";
            }
            Pref.setLoadBookTaskState(context, state);
            TaskViewModelBridge.updateProgressText(currentLoadingOperation);
        } else {
            myLogEE(null, "markIsPaused - No valid LoadBookTaskState found");
        }
    }

    public static void markDownloadProgress(Context context, int percent, String text) {
        updateTaskProgress(context, percent, text, "Downloading", false);
        TaskViewModelBridge.updateProgressFull(text, percent);
    }


    private static final String TAG = "TaskStateManager";
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }
}
