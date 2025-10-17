package com.driot.bookplayer.imports;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.global.Var;

import java.util.Objects;

public final class TaskUiState {

    public enum Result { IDLE, RUNNING, PAUSED, QUEUED, SUCCEEDED, FAILED, CANCELLED }

    @NonNull public final Result result;
    public final boolean showToUser;
    public final boolean pauseAvailable;
    public final boolean paused;

    @NonNull public final String title;
    @NonNull public final String progressText;
    public final int progressPercent;

    @Nullable public final String warningText;
    @Nullable public final String errorText;

    @Nullable public final String extension;
    @Nullable public final String originalUri;
    @Nullable public final String currentOperation;
    public final boolean doDownload;

    private TaskUiState(@NonNull Result result,
                        boolean showToUser,
                        boolean pauseAvailable,
                        boolean paused,
                        @NonNull String title,
                        @NonNull String progressText,
                        int progressPercent,
                        @Nullable String warningText,
                        @Nullable String errorText,
                        //tech
                        @Nullable String extension,
                        @Nullable String originalUri,
                        @Nullable String currentOperation,
                        boolean doDownload
    ) {
        this.result = result;
        this.showToUser = showToUser;
        this.pauseAvailable = pauseAvailable;
        this.paused = paused;
        this.title = title;
        this.progressText = progressText;
        this.progressPercent = progressPercent;
        this.warningText = warningText;
        this.errorText = errorText;
        //tech
        this.extension = extension;
        this.originalUri = originalUri;
        this.currentOperation = currentOperation;
        this.doDownload = doDownload;
    }

    public static TaskUiState idle() {
        return new TaskUiState(Result.IDLE, false, false, false, "", "", 0, null, null, null, null, null, false);
    }

    public static TaskUiState from(@NonNull ImportJob j) {
        Result r;
        switch (j.status) {
            case ImportJob.S_RUNNING:   r = Result.RUNNING; break;
            case ImportJob.S_PAUSED:    r = Result.PAUSED;  break;
            case ImportJob.S_QUEUED:    r = Result.QUEUED;  break;
            case ImportJob.S_SUCCEEDED: r = Result.SUCCEEDED; break;
            case ImportJob.S_FAILED:    r = Result.FAILED;    break;
            case ImportJob.S_CANCELLED: r = Result.CANCELLED; break;
            default:                    r = Result.IDLE;
        }

        String title = j.title != null ? j.title : "";
        String pText = j.progressText != null ? j.progressText
                : (j.currentOperation != null ? j.currentOperation : "");
        int pct = Math.max(0, Math.min(100, j.progressPercent));

        String err = (r == Result.FAILED)
                ? (j.errorTextUser != null && !j.errorTextUser.isEmpty() ? j.errorTextUser : j.errorTextDev)
                : null;

        final boolean pauseAvailNow =
                (r == Result.RUNNING || r == Result.PAUSED)           // chain is alive
                        && j.isPauseAvailable                                 // DB says download step is active
                        && Var.WORKER_TASK_LABEL_DOWNLOAD.equals(j.currentOperation);   // we are actually in the download step

        final boolean finished = (r == Result.SUCCEEDED || r == Result.FAILED || r == Result.CANCELLED);

        return new TaskUiState(
                r,
                j.showToUser,              // DB is authority
                finished ? false : pauseAvailNow,
                finished ? false : j.isLoadingPaused,
                title,
                pText,
                pct,
                j.warningText,
                err,
                //tech
                j.fileExtension,
                j.originalUri,
                j.currentOperation,
                j.doDownload
        );
    }

    // helpful booleans
    public boolean isFinished() { return result == Result.SUCCEEDED || result == Result.FAILED || result == Result.CANCELLED; }
    public boolean isRunningLike() { return result == Result.RUNNING || result == Result.QUEUED || result == Result.PAUSED; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaskUiState)) return false;
        TaskUiState that = (TaskUiState) o;
        return showToUser == that.showToUser &&
                pauseAvailable == that.pauseAvailable &&
                paused == that.paused &&
                progressPercent == that.progressPercent &&
                result == that.result &&
                title.equals(that.title) &&
                progressText.equals(that.progressText) &&
                Objects.equals(warningText, that.warningText) &&
                Objects.equals(errorText, that.errorText);
    }
    @Override public int hashCode() {
        return Objects.hash(result, showToUser, pauseAvailable, paused, title, progressText, progressPercent, warningText, errorText);
    }
}
