package com.driot.bookplayer.player;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;

import com.driot.bookplayer.utils.log.LoggerHelper;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.concurrent.TimeUnit;

import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;

/**
 * ExoPlayer-based PlayerEngine tuned for radio/streaming.
 *
 * Key improvements over the previous version:
 *  - OkHttpDataSource with JavaNetCookieJar: handles cookie-gated redirects
 *    (e.g. mdstrm.com 302 → CDN with session tokens).
 *  - DefaultMediaSourceFactory instead of ProgressiveMediaSource: auto-detects
 *    HLS (.m3u8), DASH, SmoothStreaming, and progressive (MP3/AAC/…).
 *  - No hardcoded MimeType on MediaItem: was forcing the MP3 extractor on HLS
 *    streams and causing ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED.
 */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class ExoRadioPlayerEngine extends LoggerHelper implements PlayerEngine {

    private final EngineListener listener;
    private final long gen;

    private final Context appCtx;
    private ExoPlayer player;

    private volatile boolean prepared  = false;
    private volatile boolean preparing = false;

    private float volume = 1f;

    private MediaItem currentItem;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public ExoRadioPlayerEngine(@NonNull Context ctx,
                                @NonNull EngineListener engineListener,
                                long generationToken) {
        super(ExoRadioPlayerEngine.class);
        this.appCtx   = ctx.getApplicationContext();
        this.listener = engineListener;
        this.gen      = generationToken;
        initPlayer();
    }

    // -------------------------------------------------------------------------
    // Player initialisation
    // -------------------------------------------------------------------------

    private void initPlayer() {
        final String userAgent = "BookPlayer/1.0 (ExoPlayer)";

        // --- Cookie-aware OkHttpClient ---
        // Many streaming CDNs (mdstrm.com, etc.) issue session cookies on the
        // first request and require them to be echoed on the redirected CDN URL.
        // OkHttp + JavaNetCookieJar handles this transparently.
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .cookieJar(new JavaNetCookieJar(cookieManager))
                // Inject User-Agent and Accept headers on every request
                .addInterceptor(chain -> chain.proceed(
                        chain.request().newBuilder()
                                .header("User-Agent", userAgent)
                                .header("Accept", "*/*")
                                .build()
                ))
                .build();

        // --- Media3 data-source factory backed by OkHttp ---
        OkHttpDataSource.Factory httpDataSourceFactory =
                new OkHttpDataSource.Factory(okHttpClient);

        // --- DefaultMediaSourceFactory auto-selects the right parser ---
        // HLS  → url ends in .m3u8  (or Content-Type: application/vnd.apple.mpegurl)
        // DASH → url ends in .mpd
        // Progressive → MP3, AAC, OGG, FLAC, …
        // No need to set a MimeType on the MediaItem; let ExoPlayer sniff.
        MediaSource.Factory mediaSourceFactory =
                new DefaultMediaSourceFactory(httpDataSourceFactory);

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
                        prepared  = false;
                        preparing = false;
                        listener.onCompletion(gen);
                        break;

                    case androidx.media3.common.Player.STATE_IDLE:
                        myLogD("Exo: STATE_IDLE");
                        prepared  = false;
                        preparing = false;
                        break;
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                prepared  = false;
                preparing = false;

                final int    code = error.errorCode;
                final String name = PlaybackException.getErrorCodeName(code);
                final String msg  = "Exo Error: " + name + " (" + code + ") " +
                        (error.getMessage() != null ? error.getMessage() : "");

                myLogE("onPlayerError code=" + code
                        + " name=" + name
                        + " cause=" + (error.getCause() != null ? error.getCause().toString() : "null")
                        + " msg=" + error.getMessage());

                if (isFatalExo(code)) {
                    listener.onFatal(gen, msg, code, 0);
                } else {
                    listener.onError(gen, msg, code, 0);
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // PlayerEngine — data source
    // -------------------------------------------------------------------------

    @Override
    public void setDataSource(@NonNull Context ctx,
                              @NonNull Uri uri,
                              @NonNull String displayName) {
        prepared  = false;
        preparing = false;

        // Do NOT set a MimeType here.
        // Setting MimeTypes.AUDIO_MPEG forces the MP3/progressive extractor and
        // breaks HLS streams (.m3u8).  DefaultMediaSourceFactory will sniff the
        // correct format from the Content-Type header or the URL extension.
        currentItem = new MediaItem.Builder()
                .setUri(uri)
                .setMediaId("radio:" + uri)
                .build();
    }

    // -------------------------------------------------------------------------
    // PlayerEngine — lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void prepareAsync() {
        prepared  = false;
        preparing = true;

        try { player.stop();            } catch (Throwable ignored) {}
        try { player.clearMediaItems(); } catch (Throwable ignored) {}

        if (currentItem != null) {
            player.setMediaItem(currentItem);
        }
        player.prepare();
    }

    @Override
    public void start() {
        try { player.play();       } catch (Throwable ignored) {}
        setVolume(volume);
    }

    @Override
    public void pause() {
        try { player.pause(); } catch (Throwable ignored) {}
    }

    @Override
    public void stop() {
        try { player.stop(); } catch (Throwable ignored) {}
    }

    @Override
    public void reset() {
        prepared  = false;
        preparing = false;
        try { player.stop();            } catch (Throwable ignored) {}
        try { player.clearMediaItems(); } catch (Throwable ignored) {}
    }

    /** Call when this engine instance is no longer needed. */
    public void release() {
        prepared  = false;
        preparing = false;
        if (player != null) {
            try { if (player.isPlaying()) player.stop(); } catch (Throwable ignored) {}
            try { player.clearMediaItems(); }              catch (Throwable ignored) {}
            try { player.release(); }                      catch (Throwable ignored) {}
        }
        myLog("ExoPlayerEngine.release() done");
    }

    // -------------------------------------------------------------------------
    // PlayerEngine — state queries
    // -------------------------------------------------------------------------

    @Override public boolean isPlaying() { return player != null && player.isPlaying(); }
    @Override public boolean isReady()   { return prepared && !preparing; }

    @Override
    public long getCurrentPosition() {
        if (!prepared) {
            myLogD("getCurrentPosition() while not prepared -> 0");
            return 0;
        }
        try   { return player.getCurrentPosition(); }
        catch (Throwable e) { myLogE("getCurrentPosition() ex"); return 0; }
    }

    @Override
    public long getDuration() {
        if (!prepared) {
            myLogD("getDuration() while not prepared -> 0");
            return 0;
        }
        try   { return player.getDuration(); }
        catch (Throwable e) { myLogE("getDuration() ex"); return 0; }
    }

    @Override
    public int getAudioSessionId() {
        try   { return player.getAudioSessionId(); }
        catch (Throwable ignored) { return 0; }
    }

    // -------------------------------------------------------------------------
    // PlayerEngine — controls
    // -------------------------------------------------------------------------

    @Override
    public void seekTo(long positionMs) {
        if (!prepared) {
            myLogE("seekTo(" + positionMs + ") while not prepared");
            return;
        }
        try   { player.seekTo(positionMs); myLogD("seekTo " + positionMs); }
        catch (Throwable t) { myLogEE(null, "seekTo failed: " + t.getMessage()); }
    }

    @Override
    public void setSpeed(float speed) {
        try { player.setPlaybackSpeed(speed); }
        catch (Throwable ignored) { /* live streams may ignore */ }
    }

    @Override
    public void setVolume(float v) {
        volume = Math.max(0f, Math.min(1f, v));
        try { player.setVolume(volume); } catch (Throwable ignored) {}
    }

    @Override
    public float getVolume() { return volume; }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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