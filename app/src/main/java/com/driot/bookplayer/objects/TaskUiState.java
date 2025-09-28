package com.driot.bookplayer.objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.utils.KanLogger;

public final class TaskUiState {
    public final boolean running;
    public final boolean paused;
    public final boolean finished;
    public final boolean pauseAvailable;

    @NonNull public final String title;
    @NonNull public final String progressText;
    public final int progressPercent;

    @Nullable public final String warningText;
    @Nullable public final String errorText;

    private TaskUiState(boolean running,
                        boolean paused,
                        boolean finished,
                        boolean pauseAvailable,
                        @NonNull String title,
                        @NonNull String progressText,
                        int progressPercent,
                        @Nullable String warningText,
                        @Nullable String errorText) {
        this.running = running;
        this.paused = paused;
        this.finished = finished;
        this.pauseAvailable = pauseAvailable;
        this.title = title;
        this.progressText = progressText;
        this.progressPercent = progressPercent;
        this.warningText = warningText;
        this.errorText = errorText;
    }

    public static TaskUiState idle() {
        return new TaskUiState(false, false, false, false, "", "", 0, null, null);
    }

    public TaskUiState started(String taskTitle) {
        return new TaskUiState(true, false, false, this.pauseAvailable, nonNull(taskTitle), "", 0, null, null);
    }

    public TaskUiState withProgress(int percent, String text) {
        return new TaskUiState(true, this.paused, false, this.pauseAvailable,
                this.title, nonNull(text), clamp(percent), this.warningText, null);
    }

    public TaskUiState withProgressTextOnly(@NonNull String text) {           // NEW
        return new TaskUiState(
                this.running, this.paused, this.finished, this.pauseAvailable,
                this.title, nonNull(text), this.progressPercent, this.warningText, this.errorText
        );
    }
    public TaskUiState forceRunningWithTitle(@NonNull String newTitle) {      // NEW
        return new TaskUiState(
                true, /*paused*/ false, /*finished*/ false, this.pauseAvailable,
                nonNull(newTitle), this.progressText, this.progressPercent, this.warningText, null /*clear error*/
        );
    }

    public TaskUiState withTitleSuffix(@NonNull String suffix) {
        String base = (this.title == null || this.title.isEmpty()) ? "" : this.title;
        String t = (suffix == null || suffix.isEmpty()) ? base : base + "  (" + suffix + ")";
        return new TaskUiState(this.running, this.paused, this.finished, this.pauseAvailable,
                t, this.progressText, this.progressPercent, this.warningText, this.errorText);
    }

    public TaskUiState setPauseAvailable(boolean available) {     // NEW
        return new TaskUiState(this.running, this.paused, this.finished, available,
                this.title, this.progressText, this.progressPercent, this.warningText, this.errorText);
    }

    public TaskUiState setPaused(boolean paused) {                // NEW
        return new TaskUiState(this.running, paused, this.finished, this.pauseAvailable,
                this.title, this.progressText, this.progressPercent, this.warningText, this.errorText);
    }

    public TaskUiState withWarning(String w) {
        String merged = (this.warningText == null || this.warningText.isEmpty()) ? w : (this.warningText + "\n" + w);
        return new TaskUiState(this.running, this.paused, this.finished, this.pauseAvailable,
                this.title, this.progressText, this.progressPercent, merged, this.errorText);
    }

    public TaskUiState failed(String error) {
        myLogI("failed " + error);
        return new TaskUiState(false, false, true, false,
                this.title, "Import failed", 100, this.warningText, nonNull(error));
    }

    public TaskUiState finished() {
        myLogI("finished");
        return new TaskUiState(false, false, true, false,
                this.title, "Finished", 100, this.warningText, null);
    }

    private static String nonNull(String s) { return s == null ? "" : s; }
    private static int clamp(int p) { return Math.max(0, Math.min(100, p)); }


    private static final String TAG = "TaskUiState";
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
}
