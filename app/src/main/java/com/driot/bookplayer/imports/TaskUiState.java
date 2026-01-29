package com.driot.bookplayer.imports;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.global.Var;

import java.util.Objects;

public final class TaskUiState {

        @NonNull
        public final String status;
        public final boolean showToUser;
        public final boolean pauseAvailable;
        public final boolean paused;

        @NonNull
        public final String title;
        @NonNull
        public final String progressText;
        public final int progressPercent;
        
        // Position in queue (for mass import)
        public final int currentPosition; // 0-based, -1 if not applicable
        public final int totalCount; // Total items in queue, -1 if not applicable

        @Nullable
        public final String warningText;
        @Nullable
        public final String errorText;

        @Nullable
        public final String extension;
        @Nullable
        public final String originalUri;
        @Nullable
        public final String currentOperation;
        public final boolean doDownload;

        public static final String TAG_IMPORT = "IMPORT";
        public static final String TAG_SCAN = "SCAN";

        @NonNull
        public final String tag; // IMPORT or SCAN

        private TaskUiState(@NonNull String tag, @NonNull String status,
                        boolean showToUser,
                        boolean pauseAvailable,
                        boolean paused,
                        @NonNull String title,
                        @NonNull String progressText,
                        int progressPercent,
                        @Nullable String warningText,
                        @Nullable String errorText,
                        // tech
                        @Nullable String extension,
                        @Nullable String originalUri,
                        @Nullable String currentOperation,
                        boolean doDownload,
                        int currentPosition,
                        int totalCount) {
                this.tag = tag;
                this.status = status;
                this.showToUser = showToUser;
                this.pauseAvailable = pauseAvailable;
                this.paused = paused;
                this.title = title;
                this.progressText = progressText;
                this.progressPercent = progressPercent;
                this.warningText = warningText;
                this.errorText = errorText;
                // tech
                this.extension = extension;
                this.originalUri = originalUri;
                this.currentOperation = currentOperation;
                this.doDownload = doDownload;
                this.currentPosition = currentPosition;
                this.totalCount = totalCount;
        }

        public static TaskUiState idle() {
                return new TaskUiState(TAG_IMPORT, Var.IMPORT_STATUS_IDLE, false, false, false, "", "", 0, null, null,
                                null,
                                null, null, false, -1, -1);
        }

        public static TaskUiState scanning(android.content.Context context, String progressText) {
                return new TaskUiState(TAG_SCAN, Var.IMPORT_STATUS_RUNNING, true, false, false,
                                context.getString(com.driot.bookplayer.R.string.mass_import_scanning_title),
                                progressText, 0,
                                null, null, null, null, null, false, -1, -1);
        }

        public static TaskUiState scanFinished(android.content.Context context, int count) {
                return new TaskUiState(TAG_SCAN, Var.IMPORT_STATUS_SUCCEEDED, true, false, false,
                                context.getString(com.driot.bookplayer.R.string.mass_import_scan_complete),
                                context.getString(com.driot.bookplayer.R.string.mass_import_found_candidates_click,
                                                count),
                                100, null, null, null, null, null,
                                false, -1, -1);
        }

        public static TaskUiState from(@NonNull ImportJob j, int currentPosition, int totalCount) {

                String status = j.status;
                boolean finished = Var.IMPORT_STATUS_SUCCEEDED.equals(status) ||
                                Var.IMPORT_STATUS_FAILED.equals(status) ||
                                Var.IMPORT_STATUS_CANCELLED.equals(status);

                boolean pauseAvail = (Var.IMPORT_STATUS_RUNNING.equals(status) ||
                                Var.IMPORT_STATUS_PAUSED.equals(status))
                                && j.isPauseAvailable
                                && Var.WORKER_TASK_LABEL_DOWNLOAD.equals(j.currentOperation);

                boolean showToUser = j.showToUser || !finished;

                String pText = j.progressText != null ? j.progressText
                                : (j.currentOperation != null ? j.currentOperation : "");

                String err = Var.IMPORT_STATUS_FAILED.equals(status)
                                ? ((j.errorTextUser != null && !j.errorTextUser.isEmpty())
                                                ? j.errorTextUser
                                                : j.errorTextDev)
                                : null;

                return new TaskUiState(
                                TAG_IMPORT,
                                status,
                                showToUser,
                                !finished && pauseAvail,
                                !finished && j.isLoadingPaused,
                                j.title != null ? j.title : "",
                                pText,
                                Math.max(0, Math.min(100, j.progressPercent)),
                                j.warningText,
                                err,
                                j.fileExtension,
                                j.originalUri,
                                j.currentOperation,
                                j.doDownload,
                                currentPosition,
                                totalCount);
        }

        public boolean isFinished() {
                return Var.IMPORT_STATUS_SUCCEEDED.equals(status)
                                || Var.IMPORT_STATUS_FAILED.equals(status)
                                || Var.IMPORT_STATUS_CANCELLED.equals(status);
        }

        public boolean isRunningLike() {
                return Var.IMPORT_STATUS_RUNNING.equals(status)
                                || Var.IMPORT_STATUS_QUEUED.equals(status)
                                || Var.IMPORT_STATUS_PAUSED.equals(status);
        }

        @Override
        public boolean equals(Object o) {
                if (this == o)
                        return true;
                if (!(o instanceof TaskUiState that))
                        return false;
                return showToUser == that.showToUser &&
                                pauseAvailable == that.pauseAvailable &&
                                paused == that.paused &&
                                progressPercent == that.progressPercent &&
                                currentPosition == that.currentPosition &&
                                totalCount == that.totalCount &&
                                status.equals(that.status) &&
                                title.equals(that.title) &&
                                progressText.equals(that.progressText) &&
                                Objects.equals(warningText, that.warningText) &&
                                Objects.equals(errorText, that.errorText);
        }

        @Override
        public int hashCode() {
                return Objects.hash(status, showToUser, pauseAvailable, paused, title, progressText, progressPercent,
                                warningText, errorText, currentPosition, totalCount);
        }
}
