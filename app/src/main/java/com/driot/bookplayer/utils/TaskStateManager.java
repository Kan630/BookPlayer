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

    public static void markDownloadPaused(Context context, int percent, long bytes, long total) {
        String text = formatSizeMB(bytes) + " / " + formatSizeMB(total) + " (Paused)";
        updateProgress(context, percent, text, "Paused", true);
    }

    public static void markDownloadPausedDueToNetworkPolicy(Context context, int percent, long bytes, long total) {
        String text = formatSizeMB(bytes) + " / " + formatSizeMB(total) + " (Paused)";
        updateProgress(context, percent, text, "Paused due to network policy", true);
    }

    public static void markDownloadCancelled(Context context, int percent, long bytes, long total) {
        String text = formatSizeMB(bytes) + " / " + formatSizeMB(total) + " (Cancelled)";
        updateProgress(context, percent, text, "Cancelled", false);
    }
/*
    public static void markDownloadResuming(Context context) {
        LoadBookTaskState state = Pref.getLoadBookTaskState(context);
        state.isLoadingPaused = false;
        state.currentLoadingOperation = "Resuming";
        Pref.setLoadBookTaskState(context, state);
    }

 */

    public static void updateProgressAndNotify(Context context, int percent, long bytes, long total, String phase, boolean isLoadingPaused) {
        String text = formatSizeMB(bytes) + " / " + formatSizeMB(total) + " (" + phase + ")";
        updateProgress(context, percent, text, phase, isLoadingPaused);
        GlobalTaskManager.getInstance().updateProgress(text, percent);
    }

}
