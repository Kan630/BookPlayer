package com.driot.bookplayer.utils;

import static com.driot.bookplayer.utils.Tonio.formatSizeMB;

import android.content.Context;

import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.global.Pref;

public class TaskStateManager {

    private static void updateProgress(Context context, int percent, String progressText, String phase, boolean isLoadingPaused) {
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
        state.isLoadingPaused = false;
        state.currentLoadingOperation = "Download resuming";
        Pref.setLoadBookTaskState(context, state);
    }

    public static void markIsPaused(Context context) {
        LoadBookTaskState state = Pref.getLoadBookTaskState(context);
        if (state != null) {
            state.isLoadingPaused = true;
            state.currentLoadingOperation = "Download paused";
            if (!state.progressText.endsWith("(paused)")) {
                state.progressText = state.progressText + " (paused)";
            }
            Pref.setLoadBookTaskState(context, state);
        } else {
            myLogEE(null, "markIsPaused - No valid LoadBookTaskState found");
        }
    }

    public static void updateTaskStateAndNotifyUiOfDownloadProgress(Context context, int percent, long bytes, long total) {
        String text = formatSizeMB(bytes) + " / " + formatSizeMB(total);
        updateProgress(context, percent, text, "Downloading", false);
        TaskUiManager.getInstance().updateProgress(text, percent);
    }


    private static final String TAG = "TaskStateManager";
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }
}
