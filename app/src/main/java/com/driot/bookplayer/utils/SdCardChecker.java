package com.driot.bookplayer.utils;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class SdCardChecker {
    private static final long CACHE_MS = 10_000; // only recheck at most every 10s
    private static final Executor EXEC = Executors.newSingleThreadExecutor();

    private static volatile Boolean lastValue = null;
    private static volatile long lastCheckAt = 0L;

    /** Fast, non-blocking: returns cached value if recent; kicks an async refresh if stale. */
    public static boolean isExternalSDCardAvailable(@NonNull Context ctx) {
        long now = SystemClock.uptimeMillis();
        Boolean cached = lastValue;
        if (cached != null && (now - lastCheckAt) < CACHE_MS) {
            return cached;
        }
        // Return last known (or false if unknown) immediately to keep UI snappy.
        boolean fallback = cached != null ? cached : false;
        asyncRefresh(ctx.getApplicationContext());
        return fallback;
    }

    /** Call when you actually need a fresh value; result via callback on main thread. */
    public static AutoCloseable checkAsync(@NonNull Context ctx, @NonNull Consumer<Boolean> callback) {
        final Handler main = new Handler(Looper.getMainLooper());
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        EXEC.execute(() -> {
            if (cancelled.get()) return;
            boolean value = computeNow(ctx);
            lastValue = value;
            lastCheckAt = SystemClock.uptimeMillis();
            if (!cancelled.get()) main.post(() -> callback.accept(value));
        });
        // Allow caller to cancel if the UI goes away.
        return () -> cancelled.set(true);
    }

    private static void asyncRefresh(Context ctx) {
        EXEC.execute(() -> {
            boolean value = computeNow(ctx);
            lastValue = value;
            lastCheckAt = SystemClock.uptimeMillis();
        });
    }

    /** Actual probe, runs off the main thread only. */
    private static boolean computeNow(Context context) {
        try {
            StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            for (StorageVolume v : sm.getStorageVolumes()) {
                if (v.isRemovable() && Environment.MEDIA_MOUNTED.equals(v.getState())) {
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            // Be conservative on errors
            return false;
        }
    }
}
