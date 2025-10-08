package com.driot.bookplayer.player;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class PlayabilityProbe {

    private PlayabilityProbe() {}

    /** Try MediaPlayer; if that fails and exo=true, try Exo. */
    public static PlayProbeResult probe(Context context, Uri uri, int timeoutMs, boolean exo) {
        PlayProbeResult r = probeWithMediaPlayer(context, uri, timeoutMs);
        if (r.playable) return r;
        if (exo) {
            PlayProbeResult r2 = probeWithExo(context, uri, timeoutMs);
            if (r2.playable) return r2;
            return r.durationMs > 0 ? r : (r2.error != null ? r2 : r);
        }
        return r;
    }

    // -------------------- MediaPlayer --------------------

    public static PlayProbeResult probeWithMediaPlayer(Context context, Uri uri, int timeoutMs) {
        MediaPlayer mp = new MediaPlayer();
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean ok = new AtomicBoolean(false);
        final AtomicLong dur = new AtomicLong(0);
        final AtomicReference<String> err = new AtomicReference<>(null);

        try {
            // DataSource: try context+uri, then AFD, then path if file://
            if (!setDataSourceSmart(context, mp, uri)) {
                return PlayProbeResult.fail("mediaplayer", "setDataSource failed for " + uri);
            }

            mp.setOnPreparedListener(p -> {
                try {
                    int d = p.getDuration(); // may be -1 or 0 if unknown
                    dur.set(d > 0 ? d : 0);
                    ok.set(true);
                } catch (Throwable t) {
                    err.set("onPrepared: " + t.getMessage());
                } finally {
                    latch.countDown();
                }
            });
            mp.setOnErrorListener((p, what, extra) -> {
                err.set("onError what=" + what + " extra=" + extra);
                latch.countDown();
                return true; // consumed
            });

            mp.setAudioAttributes(
                    new android.media.AudioAttributes.Builder()
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .build()
            );

            mp.prepareAsync();

            if (!latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                return PlayProbeResult.fail("mediaplayer", "prepare timeout");
            }

            if (ok.get()) {
                return PlayProbeResult.ok("mediaplayer", dur.get());
            } else {
                return PlayProbeResult.fail("mediaplayer", err.get());
            }

        } catch (Throwable t) {
            return PlayProbeResult.fail("mediaplayer", "exception: " + t.getMessage());
        } finally {
            try { mp.reset(); } catch (Throwable ignore) {}
            try { mp.release(); } catch (Throwable ignore) {}
        }
    }

    /** Try several setDataSource variants; return true if any worked. */
    private static boolean setDataSourceSmart(Context ctx, MediaPlayer mp, Uri uri) {
        // 1) Preferred path: context + uri (works for content:// & file://)
        try {
            mp.setDataSource(ctx, uri);
            return true;
        } catch (Throwable ignored) {}

        // 2) AFD (often works when the above fails)
        try (android.content.res.AssetFileDescriptor afd =
                     ctx.getContentResolver().openAssetFileDescriptor(uri, "r")) {
            if (afd != null) {
                mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                return true;
            }
        } catch (Throwable ignored) {}

        // 3) String path for file://
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            String path = uri.getPath();
            if (path != null) {
                try {
                    mp.setDataSource(path);
                    return true;
                } catch (Throwable ignored) {}
            }
        }
        return false;
    }

    // -------------------- ExoPlayer --------------------

    public static PlayProbeResult probeWithExo(Context context, Uri uri, int timeoutMs) {
        // Exo needs a Looper; use a HandlerThread so we can run in workers
        android.os.HandlerThread ht = new android.os.HandlerThread("exo-probe");
        ht.start();
        android.os.Looper looper = ht.getLooper();

        //com.google.android.exoplayer2.ExoPlayer player = null;

        return PlayProbeResult.fail("exo", "");
    }
}
