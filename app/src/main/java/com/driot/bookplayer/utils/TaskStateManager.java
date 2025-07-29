package com.driot.bookplayer.utils;

import static com.driot.bookplayer.utils.Tonio.formatSizeMB;

import android.content.Context;

import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.global.Pref;

public class TaskStateManager {

    public static void updateProgress(Context context, int percent, String progressText, String phase) {
        LoadBookTaskState state = Pref.getLoadBookTaskState(context);
        if (state != null) {
            state.progressPercent = percent;
            state.progressText = progressText;
            state.onGoingLoading = true;
            state.currentLoadingOperation = phase;
            state.isLoadingPaused = "Paused".equals(phase);
            Pref.setLoadBookTaskState(context, state);
        }
    }

    public static void markPaused(Context context, int percent, long bytes, long total) {
        String text = formatSizeMB(bytes) + " / " + formatSizeMB(total) + " (Paused)";
        updateProgress(context, percent, text, "Paused");
    }

    public static void markCancelled(Context context, int percent, long bytes, long total) {
        String text = formatSizeMB(bytes) + " / " + formatSizeMB(total) + " (Cancelled)";
        updateProgress(context, percent, text, "Cancelled");
    }

    public static void markResuming(Context context) {
        LoadBookTaskState state = Pref.getLoadBookTaskState(context);
        if (state != null && state.isLoadingPaused) {
            state.isLoadingPaused = false;
            state.currentLoadingOperation = "Resuming";
            Pref.setLoadBookTaskState(context, state);
        }
    }

    public static void updateProgressAndNotify(Context context, int percent, long bytes, long total, String phase) {
        String text = formatSizeMB(bytes) + " / " + formatSizeMB(total) + " (" + phase + ")";
        updateProgress(context, percent, text, phase);
        GlobalTaskManager.getInstance().updateProgress(text, percent);
    }

}
