package com.driot.bookplayer.imports;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.utils.log.LoggerHelper;

public final class TaskUiState extends LoggerHelper {
    public final boolean running;
    public final boolean paused;
    public final boolean finished;
    public final boolean pauseAvailable;
    public final boolean showToUser;

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
                        @Nullable String errorText,
                        boolean showToUser) {
        super(TaskUiState.class);
        this.running = running;
        this.paused = paused;
        this.finished = finished;
        this.pauseAvailable = pauseAvailable;
        this.title = title;
        this.progressText = progressText;
        this.progressPercent = progressPercent;
        this.warningText = warningText;
        this.errorText = errorText;
        this.showToUser = showToUser;
    }

    public static TaskUiState idle() {
        return new TaskUiState(false, false, false, false, "", "", 0, null, null, false);
    }

    public TaskUiState started(String taskTitle) {
        return new TaskUiState(true, false, false, this.pauseAvailable, nonNull(taskTitle), "", 0, null, null, true);
    }

    public TaskUiState withProgress(int percent, String text) {
        return new TaskUiState(true, this.paused, false, this.pauseAvailable,
                this.title, nonNull(text), clamp(percent), this.warningText, null, this.showToUser);
    }

    public TaskUiState withProgressTextOnly(@NonNull String text) {           // NEW
        return new TaskUiState(
                this.running, this.paused, this.finished, this.pauseAvailable,
                this.title, nonNull(text), this.progressPercent,
                this.warningText, this.errorText, this.showToUser
        );
    }

    public TaskUiState setPauseAvailable(boolean available) {     // NEW
        return new TaskUiState(this.running, this.paused, this.finished, available,
                this.title, this.progressText, this.progressPercent,
                this.warningText, this.errorText, this.showToUser);
    }

    public TaskUiState setPaused(boolean paused) {                // NEW
        return new TaskUiState(this.running, paused, this.finished, this.pauseAvailable,
                this.title, this.progressText, this.progressPercent,
                this.warningText, this.errorText, this.showToUser);
    }

    public TaskUiState withWarning(String w) {
        myLogE("WITH WARNINGS");
        String merged = (this.warningText == null || this.warningText.trim().replace("\n","").isEmpty()) ? w : (this.warningText + "\n" + w);
        return new TaskUiState(this.running, this.paused, this.finished, this.pauseAvailable,
                this.title, this.progressText, this.progressPercent
                , merged, this.errorText, this.showToUser);
    }

    public TaskUiState cleanWarning() {
        return new TaskUiState(this.running, this.paused, this.finished, this.pauseAvailable,
                this.title, this.progressText, this.progressPercent
                , null, this.errorText, this.showToUser);
    }

    public TaskUiState cancelled(String errorText, String progressText) {
        myLogI("cancelled " + errorText);
        return new TaskUiState(false, false, true, false,
                this.title, progressText, 100
                , this.warningText, nonNull(errorText), false);
    }

    public TaskUiState failed(String errorTextUser, String progressText) {
        myLogI("failed " + errorTextUser);
        return new TaskUiState(false, false, true, false,
                this.title, progressText, 100
                , this.warningText, nonNull(errorTextUser), true);
    }

    public TaskUiState success(String progressText) {
        myLogI("success");
        return new TaskUiState(false, false, true, false,
                this.title, progressText, 100
                , this.warningText, null, this.showToUser);
    }

    private static String nonNull(String s) { return s == null ? "" : s; }
    private static int clamp(int p) { return Math.max(0, Math.min(100, p)); }

    public TaskUiState withShowToUser(boolean show) {
        return new TaskUiState(
                this.running, this.paused, this.finished, this.pauseAvailable,
                this.title, this.progressText, this.progressPercent,
                this.warningText, this.errorText, show
        );
    }
}
