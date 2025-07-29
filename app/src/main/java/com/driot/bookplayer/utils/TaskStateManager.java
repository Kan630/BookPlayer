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
        updateProgress(context, percent, text, "Download paused", true);
    }

    public static void markDownloadPausedDueToNetworkPolicy(Context context, int percent, long bytes, long total) {
        String text = formatSizeMB(bytes) + " / " + formatSizeMB(total) + " (Paused)";
        updateProgress(context, percent, text, "Download paused due to network policy", true);
    }

    public static void markDownloadCancelled(Context context, int percent, long bytes, long total) {
        String text = formatSizeMB(bytes) + " / " + formatSizeMB(total) + " (Cancelled)";
        updateProgress(context, percent, text, "Download cancelled", false);
    }

    public static void markDownloadResuming(Context context) {
        LoadBookTaskState state = Pref.getLoadBookTaskState(context);
        state.isLoadingPaused = false;
        state.currentLoadingOperation = "Download resuming";
        Pref.setLoadBookTaskState(context, state);
    }

    public static void updateTaskStateAndNotifyUi(Context context, int percent, long bytes, long total, String phase, boolean isLoadingPaused) {
        String moreText = phase.equalsIgnoreCase("downloading") ? "" : " (" + phase + ")";
        String text = formatSizeMB(bytes) + " / " + formatSizeMB(total) + moreText;
        updateProgress(context, percent, text, phase, isLoadingPaused);
        TaskUiManager.getInstance().updateProgress(text, percent);
    }

}
