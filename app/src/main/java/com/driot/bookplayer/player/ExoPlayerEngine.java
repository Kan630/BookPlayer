package com.driot.bookplayer.player;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.datasource.DefaultHttpDataSource;

import com.driot.bookplayer.utils.log.LoggerHelper;

import java.util.HashMap;
import java.util.Map;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

/**
 * ExoPlayer-based PlayerEngine tuned for radio/streaming.
 * Mirrors MediaPlayerEngine's contract and EngineListener flow.
 */
public final class ExoPlayerEngine extends LoggerHelper implements PlayerEngine {

    private final EngineListener listener;
    private final long gen;

    private final Context appCtx;
    private ExoPlayer player;

    private volatile boolean prepared = false;
    private volatile boolean preparing = false;

    private float volume = 1f;

    private MediaItem currentItem;

    public ExoPlayerEngine(@NonNull Context ctx,
                           @NonNull EngineListener engineListener,
                           long generationToken) {
        super(ExoPlayerEngine.class);
        this.appCtx   = ctx.getApplicationContext();
        this.listener = engineListener;
        this.gen      = generationToken;
        initPlayer();
    }

    private void initPlayer() {
        // Radio-friendly HTTP data source
        String ua = "BookPlayer/1.0 (ExoPlayer)";

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(ua)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000);

        Map<String,String> headers = new HashMap<>();
        headers.put("Accept", "*/*");
        //headers.put("Icy-MetaData", "1"); // enable if you want ICY metadata
        http.setDefaultRequestProperties(headers);

        MediaSource.Factory mediaSourceFactory = new ProgressiveMediaSource.Factory(http);

        player = new ExoPlayer.Builder(appCtx)
                .setMediaSourceFactory(mediaSourceFactory)
                .build();

        player.setAudioAttributes(
                new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                /* handleAudioFocus= */ false
        );

        player.addListener(new androidx.media3.common.Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                switch (state) {
                    case androidx.media3.common.Player.STATE_BUFFERING:
                        myLogD("Exo: STATE_BUFFERING");
                        preparing = true;
                        break;

                    case androidx.media3.common.Player.STATE_READY:
                        myLogD("Exo: STATE_READY");
                        preparing = false;
                        prepared  = true;
                        listener.onPrepared(gen);
                        break;

                    case androidx.media3.common.Player.STATE_ENDED:
                        myLogD("Exo: STATE_ENDED");
                        prepared = false;
                        preparing = false;
                        listener.onCompletion(gen);
                        break;

                    case androidx.media3.common.Player.STATE_IDLE:
                        myLogD("Exo: STATE_IDLE");
                        // Keep flags conservative; engine not prepared.
                        prepared = false;
                        preparing = false;
                        break;
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                prepared  = false;
                preparing = false;

                final int code = error.errorCode;
                final String name = PlaybackException.getErrorCodeName(code);
                final String msg  = "Exo Error: " + name + " (" + code + ") " +
                        (error.getMessage() != null ? error.getMessage() : "");

                myLogE("onPlayerError code=" + code + " name=" + name + " cause=" +
                        (error.getCause()!=null ? error.getCause().toString() : "null") +
                        " msg=" + error.getMessage());

                // Classify "fatal enough" similar to your MediaPlayerEngine policy
                if (isFatalExo(code)) {
                    listener.onFatal(gen, msg, code, 0);
                } else {
                    listener.onError(gen, msg, code, 0);
                }
            }
        });
    }

    // ---- PlayerEngine ----

    @Override
    public void setDataSource(@NonNull Context ctx, @NonNull Uri uri, @NonNull String displayName) {
        prepared = false;
        preparing = false;

        // Hint MP3 progressive; fits most Icecast/Shoutcast mounts
        currentItem = new MediaItem.Builder()
                .setUri(uri)
                .setMimeType(MimeTypes.AUDIO_MPEG)
                .setMediaId("radio:" + uri)
                .build();
    }

    @Override
    public void prepareAsync() {
        prepared = false;
        preparing = true;

        try {
            player.stop();
            player.clearMediaItems();
        } catch (Throwable ignored) {}

        if (currentItem != null) {
            player.setMediaItem(currentItem);
        }
        player.prepare();
    }

    @Override public void start()        { try { player.play();  setVolume(volume); } catch (Throwable ignored) {} }
    @Override public void pause()        { try { player.pause(); } catch (Throwable ignored) {} }
    @Override public void stop()         { try { player.stop();  } catch (Throwable ignored) {} }
    @Override public void reset()        {
        prepared = false; preparing = false;
        try { player.stop(); } catch (Throwable ignored) {}
        try { player.clearMediaItems(); } catch (Throwable ignored) {}
    }

    @Override public boolean isPlaying() { return player != null && player.isPlaying(); }
    @Override public boolean isReady()   { return prepared && !preparing; }

    @Override public int getCurrentPosition() {
        if (!prepared) {
            myLogD("getCurrentPosition() while not prepared -> 0");
            return 0;
        }
        try { return (int) player.getCurrentPosition(); }
        catch (Throwable e) { myLogE("getCurrentPosition() ex"); return 0; }
    }

    @Override public int getDuration() {
        if (!prepared) {
            myLogD("getDuration() while not prepared -> 0");
            return 0;
        }
        try { return (int) player.getDuration(); }
        catch (Throwable e) { myLogE("getDuration() ex"); return 0; }
    }

    @Override public int getAudioSessionId() {
        try { return player.getAudioSessionId(); }
        catch (Throwable ignored) { return 0; }
    }

    @Override public void seekTo(int positionMs) {
        if (!prepared) {
            myLogE("seekTo(" + positionMs + ") while not prepared");
            return;
        }
        try { player.seekTo(positionMs); myLogD("seekTo " + positionMs); }
        catch (Throwable t) { myLogEE(null, "seekTo failed: " + t.getMessage()); }
    }

    @Override public void setSpeed(float speed) {
        try { player.setPlaybackSpeed(speed); }
        catch (Throwable ignored) { /* old devices / live streams may ignore */ }
    }

    @Override public void setVolume(float v) {
        float nv = Math.max(0f, Math.min(1f, v));
        volume = nv;
        try { player.setVolume(nv); } catch (Throwable ignored) {}
    }

    @Override public float getVolume() {
        return volume;
    }

    /** Call when done with this engine. */
    public void release() {
        prepared = false;
        preparing = false;
        if (player != null) {
            try { if (player.isPlaying()) player.stop(); } catch (Throwable ignored) {}
            try { player.clearMediaItems(); } catch (Throwable ignored) {}
            try { player.release(); } catch (Throwable ignored) {}
        }
        myLog("ExoPlayerEngine.release() done");
    }

    // ---- helpers ----
    private static boolean isFatalExo(int errorCode) {
        switch (errorCode) {
            case PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS:
            case PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND:
            case PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE:
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED:
            case PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED:
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
            case PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED:
            case PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED:
                return true;
            default:
                return false;
        }
    }
}
