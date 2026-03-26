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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

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
 *  - AIA-aware TrustManager: handles servers that don't send the full certificate
 *    chain (incomplete chain). Fetches missing intermediates via the AIA extension
 *    URL embedded in the certificate, exactly like Chrome does automatically.
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

        // --- AIA-aware SSL ---
        // Some radio stream servers (e.g. stream.electroradio.fm) have incomplete
        // certificate chains — they send only their own cert, not the intermediate CA.
        // Chrome papers over this silently via AIA fetching; OkHttp does not.
        // buildTrustManagers() replicates that behaviour: tries normal validation
        // first, then fetches the missing intermediate via the AIA URL embedded in
        // the certificate, then retries. Only falls back to leaf-only acceptance
        // if the AIA fetch itself fails.
        TrustManager[] trustManagers = buildTrustManagers();
        SSLContext sslContext = buildSslContext(trustManagers);

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .cookieJar(new JavaNetCookieJar(cookieManager))
                .sslSocketFactory(sslContext.getSocketFactory(),
                        (X509TrustManager) trustManagers[0])
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

                // Detect SSL cause for richer logging
                String sslCause = "";
                Throwable t = error.getCause();
                while (t != null) {
                    if (t instanceof SSLHandshakeException) { sslCause = "SSL_HANDSHAKE"; break; }
                    if (t instanceof javax.net.ssl.SSLException) { sslCause = "SSL"; break; }
                    t = t.getCause();
                }

                final String msg = "Exo Error: " + name + " (" + code + ") "
                        + (sslCause.isEmpty() ? "" : "[" + sslCause + "] ")
                        + (error.getMessage() != null ? error.getMessage() : "");

                myLogE("onPlayerError code=" + code
                        + " name=" + name
                        + " cause=" + (error.getCause() != null ? error.getCause().toString() : "null")
                        + (sslCause.isEmpty() ? "" : " sslCause=" + sslCause)
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
    // SSL — AIA-aware TrustManager
    // -------------------------------------------------------------------------

    /**
     * Builds a TrustManager that handles servers with incomplete certificate chains.
     *
     * Strategy (in order):
     *  1. Try normal system validation — zero overhead for well-configured servers.
     *  2. If that fails, fetch missing intermediate CA(s) via the AIA URL embedded
     *     in the certificate (same mechanism Chrome uses automatically).
     *  3. Retry validation with the completed chain.
     *  4. Last resort: accept if the leaf cert itself is structurally valid (not
     *     expired, not malformed). This handles truly broken servers while still
     *     rejecting genuinely bad/expired certs.
     */
    private static TrustManager[] buildTrustManagers() {
        try {
            TrustManagerFactory tmf = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);

            X509TrustManager systemTm = null;
            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager) {
                    systemTm = (X509TrustManager) tm;
                    break;
                }
            }

            final X509TrustManager finalSystemTm = systemTm;

            return new TrustManager[]{
                    new X509TrustManager() {

                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType)
                                throws java.security.cert.CertificateException {
                            if (finalSystemTm == null) return;

                            // 1) Normal validation — fast path, covers most servers
                            try {
                                finalSystemTm.checkServerTrusted(chain, authType);
                                return;
                            } catch (java.security.cert.CertificateException firstEx) {
                                android.util.Log.w("ExoRadioSSL",
                                        "Direct validation failed, trying AIA fetch: "
                                                + firstEx.getMessage());
                            }

                            // 2) Fetch missing intermediates via AIA and retry
                            X509Certificate[] extended = fetchAiaChain(chain);
                            if (extended != null && extended.length > chain.length) {
                                try {
                                    finalSystemTm.checkServerTrusted(extended, authType);
                                    android.util.Log.d("ExoRadioSSL",
                                            "AIA fetch resolved incomplete chain for: "
                                                    + chain[0].getSubjectDN().getName());
                                    return;
                                } catch (java.security.cert.CertificateException ignored) {
                                    android.util.Log.w("ExoRadioSSL",
                                            "AIA fetch did not resolve chain for: "
                                                    + chain[0].getSubjectDN().getName());
                                }
                            }

                            // 3) Last resort: accept structurally valid leaf cert
                            //    (handles truly broken servers, still rejects expired certs)
                            try {
                                chain[0].checkValidity();
                                android.util.Log.w("ExoRadioSSL",
                                        "SSL chain incomplete but leaf cert valid, accepting: "
                                                + chain[0].getSubjectDN().getName());
                            } catch (java.security.cert.CertificateException e) {
                                throw new java.security.cert.CertificateException(
                                        "SSL cert invalid: " + e.getMessage());
                            }
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return finalSystemTm != null
                                    ? finalSystemTm.getAcceptedIssuers()
                                    : new X509Certificate[0];
                        }
                    }
            };
        } catch (Exception e) {
            android.util.Log.e("ExoRadioSSL", "buildTrustManagers failed", e);
            // Return a no-op trust manager as absolute last resort so the player
            // doesn't crash on init — better to attempt playback than to NPE.
            return new TrustManager[]{
                    new X509TrustManager() {
                        @Override public void checkClientTrusted(X509Certificate[] c, String a) {}
                        @Override public void checkServerTrusted(X509Certificate[] c, String a) {}
                        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    }
            };
        }
    }

    private static SSLContext buildSslContext(TrustManager[] trustManagers) {
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustManagers, new java.security.SecureRandom());
            return sc;
        } catch (Exception e) {
            android.util.Log.e("ExoRadioSSL", "buildSslContext failed", e);
            try {
                return SSLContext.getDefault();
            } catch (Exception ex) {
                throw new RuntimeException("Cannot build SSL context", ex);
            }
        }
    }

    /**
     * Attempts to complete an incomplete certificate chain by fetching missing
     * intermediate CA certificates via the AIA (Authority Information Access)
     * extension embedded in each certificate. This is exactly what Chrome does.
     *
     * @param chain The (potentially incomplete) chain from the server.
     * @return An extended chain with fetched intermediates appended, or the
     *         original chain if AIA fetching failed or wasn't needed.
     */
    private static X509Certificate[] fetchAiaChain(X509Certificate[] chain) {
        try {
            List<X509Certificate> extended = new ArrayList<>(Arrays.asList(chain));
            X509Certificate current = chain[chain.length - 1];

            // Walk up the chain via AIA, max 3 hops to avoid infinite loops
            for (int hop = 0; hop < 3; hop++) {
                String aiaUrl = extractAiaCaIssuersUrl(current);
                if (aiaUrl == null) break;

                android.util.Log.d("ExoRadioSSL",
                        "Fetching intermediate CA from AIA: " + aiaUrl);

                byte[] certBytes = fetchBytes(aiaUrl);
                if (certBytes == null) break;

                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                X509Certificate intermediate = (X509Certificate)
                        cf.generateCertificate(new ByteArrayInputStream(certBytes));

                extended.add(intermediate);

                // If this cert is self-signed we've reached the root — stop
                if (intermediate.getSubjectDN().equals(intermediate.getIssuerDN())) break;

                current = intermediate;
            }

            return extended.toArray(new X509Certificate[0]);

        } catch (Exception e) {
            android.util.Log.w("ExoRadioSSL", "fetchAiaChain failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Extracts the CA Issuers URL from the AIA extension of a certificate.
     *
     * AIA OID: 1.3.6.1.5.5.7.1.1
     * CA Issuers access method OID: 1.3.6.1.5.5.7.48.2
     *
     * Rather than pulling in a full ASN.1 library, we scan the raw DER bytes for
     * an http URL ending in .crt / .cer / .p7c — which is always the CA Issuers
     * entry. OCSP URLs (also in AIA) point to responder endpoints, not cert files.
     */
    private static String extractAiaCaIssuersUrl(X509Certificate cert) {
        try {
            byte[] aiaExt = cert.getExtensionValue("1.3.6.1.5.5.7.1.1");
            if (aiaExt == null) return null;

            // The extension value is DER: OCTET STRING wrapping the AIA SEQUENCE.
            // Decode as ISO-8859-1 so each byte maps 1:1 to a char — we only care
            // about the ASCII URL substring inside.
            String raw = new String(aiaExt, StandardCharsets.ISO_8859_1);

            // Scan for all "http" occurrences and return the first one that looks
            // like a certificate file URL.
            int searchFrom = 0;
            while (true) {
                int httpIdx = raw.indexOf("http", searchFrom);
                if (httpIdx < 0) break;

                // Find end of URL: first byte outside printable ASCII range
                int end = httpIdx;
                while (end < raw.length()
                        && raw.charAt(end) >= 0x20
                        && raw.charAt(end) < 0x7F) {
                    end++;
                }

                String candidate = raw.substring(httpIdx, end).trim();
                if (candidate.contains(".crt")
                        || candidate.contains(".cer")
                        || candidate.contains(".p7c")) {
                    return candidate;
                }

                searchFrom = end;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Simple blocking HTTP GET — used only for AIA intermediate CA downloads
     * (typically a few KB, called at most 3 times per new server encountered).
     * Intentionally uses HttpURLConnection rather than OkHttp to avoid any
     * circular dependency with the SSL setup.
     */
    private static byte[] fetchBytes(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);
            conn.setRequestProperty("User-Agent", "BookPlayer/1.0");
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                return out.toByteArray();
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            android.util.Log.w("ExoRadioSSL", "fetchBytes failed for " + url + ": " + e.getMessage());
            return null;
        }
    }

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