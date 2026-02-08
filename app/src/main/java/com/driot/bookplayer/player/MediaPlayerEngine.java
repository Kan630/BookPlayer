package com.driot.bookplayer.player;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggerHelper;
import com.driot.bookplayer.utils.log.LoggerStaticHelper;

import java.io.IOException;

/**
 * MediaPlayer-based PlayerEngine with internal state + error mapping.
 * Thread-safe against stale callbacks via generation token.
 */
public final class MediaPlayerEngine extends LoggerHelper implements PlayerEngine {

    private final EngineListener listener;
    private final long gen;

    private MediaPlayer mp;
    private volatile boolean prepared = false;
    private volatile boolean preparing = false;

    private float volume = 1f;

    public MediaPlayerEngine(@NonNull EngineListener listener, long generationToken) {
        super(MediaPlayerEngine.class);
        this.listener = listener;
        this.gen = generationToken;
        initPlayer();
    }

    private void initPlayer() {
        mp = new MediaPlayer();

        mp.setOnPreparedListener(m -> {
            prepared = true;
            preparing = false;
            myLog("onPrepared");
            listener.onPrepared(gen);
        });

        mp.setOnCompletionListener(m -> {
            prepared = false; // conservative; many apps re-prepare next
            myLog("onCompletion");
            listener.onCompletion(gen);
        });

        mp.setOnErrorListener((m, what, extra) -> {
            prepared = false;
            preparing = false;
            String msg = classifyError(what, extra);
            boolean fatal = isFatalError(what, extra);
            myLogE("onError what=" + what + " extra=" + extra + " -> " + msg + " fatal=" + fatal);
            if (fatal) {
                listener.onFatal(gen, msg, what, extra);
            } else {
                listener.onError(gen, msg, what, extra);
            }
            // We handled it; prevent framework default dialog/log spam.
            return true;
        });
    }

    @Override
    public void setDataSource(@NonNull Context ctx, @NonNull Uri uri, @NonNull String displayName) throws IOException {
        prepared = false;
        preparing = false;
        mp.reset();
        mp.setDataSource(ctx, uri);
    }

    @Override
    public void prepareAsync() {
        prepared = false;
        preparing = true;
        mp.prepareAsync();
    }

    @Override
    public void start() {
        mp.start();
        // re-apply volume in case system reset it //TODO check that
        setVolume(volume);
    }

    @Override
    public void pause() {
        mp.pause();
    }

    @Override
    public void stop() {
        safeStop(mp);
    }

    @Override
    public void reset() {
        prepared = false;
        preparing = false;
        mp.reset();
    }

    @Override
    public boolean isPlaying() {
        return mp.isPlaying();
    }

    @Override
    public boolean isReady() {
        return prepared && !preparing;
    }

    @Override
    public long getCurrentPosition() {
        if (!prepared) {
            myLogD("getCurrentPosition() while not prepared -> 0");
            return 0;
        }
        try {
            return mp.getCurrentPosition();
        } catch (IllegalStateException e) {
            myLogE("getCurrentPosition() ISE");
            return 0;
        }
    }

    @Override
    public long getDuration() {
        if (!prepared) {
            myLogD("getDuration() while not prepared -> 0");
            return 0;
        }
        try {
            return mp.getDuration();
        } catch (IllegalStateException e) {
            myLogE("getDuration() ISE");
            return 0;
        }
    }

    @Override
    public int getAudioSessionId() {
        return mp.getAudioSessionId();
    }

    @Override
    public void seekTo(long ms) {
        if (!prepared) {
            myLogE("seekTo(" + ms + ") while not prepared");
            return;
        }
        try {
            // mp.seekTo(ms, MediaPlayer.SEEK_CLOSEST);
            // myLogD("seekTo CLOSEST " + ms);
            mp.seekTo((int) ms);
            myLogD("seekTo NORMAL " + Tonio.formatHhMmSsMs(ms));
        } catch (Throwable t) {
            myLogEE(null, "seekTo failed: " + t.getMessage());
        }
    }

    @Override
    public void setSpeed(float speed) {
        try {
            mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(speed));
        } catch (Throwable ignored) {
            /* old devices / streams */ }
    }

    @Override
    public void setVolume(float v) {
        float nv = Math.max(0f, Math.min(1f, v));
        volume = nv;
        try {
            mp.setVolume(nv, nv);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public float getVolume() {
        return volume;
    }

    /** Call when done with this engine. */
    public void release() {
        prepared = false;
        preparing = false;
        safeRelease(mp);
        mp = null;
    }

    // ---------- Helpers ----------

    private static void safeStop(MediaPlayer player) {
        if (player == null)
            return;
        try {
            player.stop();
        } catch (IllegalStateException ignored) {
        }
    }

    private static void safeRelease(MediaPlayer player) {
        if (player == null)
            return;
        try {
            if (player.isPlaying()) {
                try {
                    player.stop();
                } catch (IllegalStateException ignored) {
                }
            }
        } catch (IllegalStateException ignored) {
        }
        try {
            player.release();
        } catch (Exception ignored) {
        }
        LoggerStaticHelper.myLog("safeRelease() done");
    }

    private static boolean isFatalError(int what, int extra) {
        // You can tune this list over time from your crash telemetry
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
            case MediaPlayer.MEDIA_ERROR_MALFORMED:
            case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
            case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                return true;
            default:
                return false;
        }
    }

    private static String classifyError(int what, int extra) {
        String whatString;
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                whatString = "MEDIA_ERROR_UNKNOWN";
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                whatString = "MEDIA_ERROR_SERVER_DIED";
                break;
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                whatString = "MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK";
                break;
            case MediaPlayer.MEDIA_ERROR_IO:
                whatString = "MEDIA_ERROR_IO";
                break;
            case MediaPlayer.MEDIA_ERROR_MALFORMED:
                whatString = "MEDIA_ERROR_MALFORMED";
                break;
            case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                whatString = "MEDIA_ERROR_UNSUPPORTED";
                break;
            case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                whatString = "MEDIA_ERROR_TIMED_OUT";
                break;
            default:
                whatString = "UNKNOWN_CODE_" + what;
        }
        return "MediaPlayer Error: " + whatString + " (" + what + "), extra=" + extra;
    }
}
