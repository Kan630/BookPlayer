package com.driot.bookplayer.testutil;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;

import com.driot.bookplayer.objects.TaskStateRepository;
import com.driot.bookplayer.objects.TaskUiState;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class TaskStateTestProbe {

    public static final class Outcome {
        public final boolean finished;      // TaskUiState.finished
        public final boolean success;       // finished && errorText == null
        public final @Nullable String errorText;
        public final String progressText;

        Outcome(boolean finished, boolean success, @Nullable String errorText, String progressText) {
            this.finished = finished;
            this.success = success;
            this.errorText = errorText;
            this.progressText = progressText == null ? "" : progressText;
        }

        @Override public String toString() {
            return "Outcome{finished=" + finished + ", success=" + success +
                    ", error=" + errorText + ", progress='" + progressText + "'}";
        }
    }

    private final CountDownLatch finishedLatch = new CountDownLatch(1);
    private final AtomicReference<TaskUiState> last = new AtomicReference<>();

    private final Observer<TaskUiState> observer = s -> {
        last.set(s);
        if (s != null && s.finished) finishedLatch.countDown();
    };

    public void start() {
        // make sure repo is ready + observe on main
        getInstrumentation().runOnMainSync(() -> {
            TaskStateRepository.get().hydrateFromPrefs();
            TaskStateRepository.get().state().observeForever(observer);
        });
    }

    public void resetToIdle() {
        getInstrumentation().runOnMainSync(() -> TaskStateRepository.get().resetToIdle());
        last.set(null);
        while (finishedLatch.getCount() == 0) { /* do nothing: we allocate per import (see tip) */ }
    }

    public Outcome await(long timeoutMs) throws InterruptedException {
        boolean ended = finishedLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
        TaskUiState s = last.get();
        boolean finished = ended && s != null && s.finished;
        boolean success  = finished && s.errorText == null;
        String err = s == null ? "no state" : s.errorText;
        String txt = s == null ? "" : s.progressText;
        return new Outcome(finished, success, err, txt);
    }

    public void stop() {
        getInstrumentation().runOnMainSync(() ->
                TaskStateRepository.get().state().removeObserver(observer));
    }

    public @Nullable TaskUiState lastState() {
        return last.get();
    }

    public boolean isFinished() {
        TaskUiState s = last.get();
        return s != null && s.finished;
    }

    public boolean isSuccess() {
        TaskUiState s = last.get();
        return s != null && s.finished && s.errorText == null;
    }

    public boolean isFailed() {
        TaskUiState s = last.get();
        return s != null && s.finished && s.errorText != null;
    }
}
