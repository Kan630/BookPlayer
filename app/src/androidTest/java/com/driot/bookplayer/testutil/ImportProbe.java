// testutil/ImportProbe.java
package com.driot.bookplayer.testutil;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.test.platform.app.InstrumentationRegistry;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.imports.ImportJob;
import com.driot.bookplayer.imports.TaskUiState;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

// testutil/ImportProbe.java
public final class ImportProbe {
    private final AppDatabase db;
    private LiveData<ImportJob> src;
    private Observer<ImportJob> obs;

    private final AtomicReference<TaskUiState> last = new AtomicReference<>(TaskUiState.idle());
    private final CountDownLatch done = new CountDownLatch(1);

    public ImportProbe(Context appCtx) {
        this.db = AppDatabase.getInstance(appCtx.getApplicationContext());
    }

    public void start() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            src = db.importJobDao().observeUniqueJob();
            obs = job -> {
                TaskUiState ui = (job == null) ? TaskUiState.idle() : TaskUiState.from(job, -1, -1);
                last.set(ui);
                if (ui.isFinished()) {
                    // ensure latch countDown happens even if this callback reenters
                    new Handler(Looper.getMainLooper()).post(() -> done.countDown());
                }
            };
            src.observeForever(obs); // MUST be main thread
        });
    }

    @Nullable
    public TaskUiState await(long timeoutMs) {
        try {
            if (!done.await(timeoutMs, TimeUnit.MILLISECONDS)) return null;
            return last.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public TaskUiState lastState() { return last.get(); }

    public void stop() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            if (src != null && obs != null) src.removeObserver(obs);
        });
    }
}
