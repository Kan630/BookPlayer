/*
package com.driot.bookplayer.imports;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.utils.log.LoggerHelper;

public final class TaskStateRepository extends LoggerHelper {

    private static final TaskStateRepository INSTANCE = new TaskStateRepository();
    public static TaskStateRepository get() { return INSTANCE; }

    private final MutableLiveData<TaskUiState> live = new MutableLiveData<>(TaskUiState.idle());
    private final Handler main = new Handler(Looper.getMainLooper());

    private TaskStateRepository() {
        super(TaskStateRepository.class);
    }

    public LiveData<TaskUiState> state() { return live; }

    private volatile boolean bootstrapped = false;

    //Idempotent: safe to call multiple times. Restores title/progress after process death. /
    public synchronized void hydrateFromPrefs() {
        if (bootstrapped) return;
        LoadBookTaskState s = Pref.getLoadBookTaskState();
        if (s != null && s.onGoingLoading) {
            // Build a running state from persisted info
            TaskUiState next = TaskUiState.idle().started(nonNull(s.title))
                    .withProgress(s.progressPercent, nonNull(s.progressText))
                    .setPauseAvailable(String.valueOf(s.originalUri).startsWith("http"))
                    .setPaused(s.isLoadingPaused);
            post(next);
        }
        bootstrapped = true;
    }

    public void start(@NonNull String title,
                      @Nullable String currentOperation,
                      boolean pauseAvailable,
                      boolean isLoadingPaused) {

        TaskUiState base = TaskUiState.idle().started(title);

        TaskUiState next = base
                .setPauseAvailable(pauseAvailable)
                .setPaused(isLoadingPaused)
                .withProgressTextOnly(currentOperation == null ? "" : currentOperation);

        post(next);
    }

    public void setCurrentOperation(@NonNull String op) {
        post(s().withProgressTextOnly(op));
    }

    private String fallbackTitleFromPrefs() {
        LoadBookTaskState s = Pref.getLoadBookTaskState();
        return (s != null && s.title != null && !s.title.isEmpty()) ? s.title : "Task";
    }

    public void progress(int percent, @NonNull String text) {
        TaskUiState cur = s();
        TaskUiState next = cur.running
                ? cur.withProgress(percent, text)
                : TaskUiState.idle().started(
                cur.title == null || cur.title.isEmpty() ? fallbackTitleFromPrefs() : cur.title
        ).withProgress(percent, text);
        post(next);
    }

    public void setPaused(boolean paused) { post(s().setPaused(paused)); }

    public synchronized void warning(@NonNull String w) {
        post(s().withWarning(w));
    }

    public void downloadPaused(@NonNull String reason) {
        TaskUiState cur = s();
        TaskUiState next = cur
                .setPaused(true)
                .withWarning(reason)
                .withProgressTextOnly(reason + "\n" + cur.progressText);
        post(next);
    }
    public void downloadResuming(@NonNull String text) {
        TaskUiState cur = s();
        TaskUiState next = cur
                .setPaused(false)
                .withProgressTextOnly(text);
        post(next);
    }
    public void downloadComplete(@NonNull String currentOperation) {
        TaskUiState cur = s();
        TaskUiState next = cur
                .setPaused(false)
                .setPauseAvailable(false)
                .cleanWarning()
                .withProgressTextOnly(currentOperation);
        post(next);
    }

    public synchronized void finish(@NonNull String progressText) {
        post(s().success(progressText));
    }

    public synchronized void error(@NonNull String message, @NonNull String progressText) {
        post(s().failed(message, progressText));
    }

    public void resetToIdle() {
        post(TaskUiState.idle());
    }

    private TaskUiState s() { return live.getValue() == null ? TaskUiState.idle() : live.getValue(); }

    private void post(TaskUiState next) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            live.setValue(next);
        } else {
            main.post(() -> live.setValue(next));
        }
    }

    public void setProgressText(@NonNull String text) {          // keep this using text-only as well
        post(s().withProgressTextOnly(text));
    }

    private static String nonNull(String s) { return s == null ? "" : s; }

}
*/
