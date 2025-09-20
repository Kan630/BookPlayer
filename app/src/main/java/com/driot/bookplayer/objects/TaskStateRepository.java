package com.driot.bookplayer.objects;

import android.os.Looper;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.utils.KanLogger;

public final class TaskStateRepository {

    private static final TaskStateRepository INSTANCE = new TaskStateRepository();
    public static TaskStateRepository get() { return INSTANCE; }

    private final MutableLiveData<TaskUiState> live = new MutableLiveData<>(TaskUiState.idle());
    private final Handler main = new Handler(Looper.getMainLooper());

    private final java.util.concurrent.atomic.AtomicBoolean acceptLate = new java.util.concurrent.atomic.AtomicBoolean(true);

    private TaskStateRepository() { }

    public LiveData<TaskUiState> state() { return live; }

    private volatile boolean bootstrapped = false;

    /** Idempotent: safe to call multiple times. Restores title/progress after process death. */
    public synchronized void hydrateFromPrefs() {
        if (bootstrapped) return;
        LoadBookTaskState s = Pref.getLoadBookTaskState();
        if (s != null && s.onGoingLoading) {
            // Build a running state from persisted info
            TaskUiState next = TaskUiState.idle().started(nonNull(s.title))
                    .withProgress(s.progressPercent, nonNull(s.progressText))
                    .setPauseAvailable(s.originalUri != null && String.valueOf(s.originalUri).startsWith("http"))
                    .setPaused(s.isLoadingPaused);
            post(next);
        }
        bootstrapped = true;
    }

    public void start(@NonNull String title) {
        acceptLate.set(true);
        TaskUiState cur = s();
        post(cur.running ? cur.forceRunningWithTitle(title) : TaskUiState.idle().started(title));
    }

    public void setCurrentOperation(@NonNull String op) {
        // Change ONLY the progress text; never touch the percent here
        post(s().withProgressTextOnly(op));
    }

    private String fallbackTitleFromPrefs() {
        LoadBookTaskState s = Pref.getLoadBookTaskState();
        String t = (s != null && s.title != null && !s.title.isEmpty()) ? s.title : "Task";
        return t;
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

    public void setPauseAvailable(boolean available) { post(s().setPauseAvailable(available)); }
    public void setPaused(boolean paused) { post(s().setPaused(paused)); }

    public void warning(@NonNull String w) { post(s().withWarning(w)); }
    public void finish() {
        acceptLate.set(false);
        post(s().finished());
    }

    public void error(@NonNull String message) {
        acceptLate.set(false);
        post(s().failed(message));
    }

    public void resetToIdle() {
        acceptLate.set(false);
        post(TaskUiState.idle());
    }

    private TaskUiState s() { return live.getValue() == null ? TaskUiState.idle() : live.getValue(); }

    private void post(TaskUiState next) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            live.setValue(next);
        } else {
            live.postValue(next);
        }
    }

    public void setProgressText(@NonNull String text) {          // keep this using text-only as well
        post(s().withProgressTextOnly(text));
    }

    private static String nonNull(String s) { return s == null ? "" : s; }




    private static final String TAG = "TaskStateRepository";
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }

}
