package com.driot.bookplayer.player;

import com.driot.bookplayer.R;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media.session.MediaButtonReceiver;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.nav.NavHelper;
import com.driot.bookplayer.player.heatmaps.PlayTickCompactor;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.CallerHelper;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.podcasts.PodcastHelper;
import com.driot.bookplayer.tts.TtsEngine;
import com.driot.bookplayer.tts.AppTtsManager;
import com.driot.bookplayer.tts.TtsErrorUtils;
import com.driot.bookplayer.tts.TtsHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingMediaBrowserServiceCompat;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Pref;

import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.driot.bookplayer.utils.Tonio.formatTime;

import javax.inject.Inject;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 01/11/20
 */

@AndroidEntryPoint
public class MediaService extends LoggingMediaBrowserServiceCompat {

    private int ttsErrorCountForGen = 0;
    private long lastTtsErrorGen = -1;

    private final java.util.concurrent.atomic.AtomicInteger boundClientCount = new java.util.concurrent.atomic.AtomicInteger();
    private final android.os.Handler serviceHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final int STOP_GRACE_MS = 3000; // small delay to avoid reconnect storms
    private final Runnable stopRunnable = () -> {
        if (boundClientCount.get() == 0) {
            myLogI("No bound clients after grace → stopSelf()");
            stopSelf();
        } else {
            myLogI("Clients re-bound during grace → keep service alive");
        }
    };

    private boolean hasBrowserClients() {
        return boundClientCount.get() > 0;
    }

    public static volatile boolean isRunning = false;

    enum ServiceState {
        RUNNING, SHUTTING_DOWN, STOPPED
    }

    private final AtomicReference<ServiceState> state = new AtomicReference<>(ServiceState.RUNNING);

    private boolean beginShutdown() {
        // Only the first caller wins; others will still be safe to call
        // finalizeShutdown()
        return state.compareAndSet(ServiceState.RUNNING, ServiceState.SHUTTING_DOWN);
    }

    private static final String ID_NOTIFICATION_PLAY_AUDIO_CHANNEL = "audio_channel_of_bookplayer";
    private static final int ID_NOTIFICATION_PLAY_AUDIO_INT = 2;

    private boolean pausedByFocusLoss = false;
    private float preDuckVolume = 1f;

    // Play Timer (for Sleep)
    public static final int DELAY_CHECK_TIMER_SLEEP = 1000;
    private Handler sleepCheckHandler;
    private int customSleepTime = 0;

    // Pause Timer (to free memory)
    public static final int TRIM_MEMORY_THRESHOLD = 20;
    public static final int DELAY_CHECK_TIMER_PAUSE = 60 * 1000;
    public static final int TRIM_AFTER_PAUSE_MS = 7 * 24 * 60 * 60 * 1000; // so basically never... 7 days
    private Handler pauseCheckHandler;

    private int lastCustomSleepMinutes = 0;

    // cache for Android Auto Bitmaps
    public static final android.util.LruCache<String, android.graphics.Bitmap> artCache = new android.util.LruCache<>(
            8);
    public static final android.util.LruCache<String, android.graphics.Bitmap> iconCache = new android.util.LruCache<>(
            24); // (Least Recently Used) cache with a maximum size of 24

    // private final IBinder binder = new BackgroundBinder();
    public static final String TRACKNUMBER = "tracknumber";
    public static final String FROM = "from";
    public static final String ERR_MSG = "err_msg";
    public static final String NOTIFICATION_ERROR = "NOTIFICATION_ERROR";
    public static final String NOTIFICATION_PLAYLISTFINISHED = "NOTIFICATION_PLAYLISTFINISHED";
    public static final String NOTIFICATION_PLAYBACK_MAXTIMEREACH = "NOTIFICATION_PLAYBACK_MAXTIMEREACH";
    public static final String TRACK_PATH = "track_path";

    private com.driot.bookplayer.player.PlaybackNotificationManager notif;
    private com.driot.bookplayer.player.MediaSessionController media;
    private com.driot.bookplayer.player.AudioFocusHelper focus;
    private com.driot.bookplayer.player.PlayTimer playTimer;
    private com.driot.bookplayer.player.PauseTrimWatcher pauseWatcher;
    private com.driot.bookplayer.player.PlaybackProgressUpdater progress;

    @Inject
    protected AppTtsManager ttsManager;

    private long engineGen = 0L;
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private final EngineListener engineCb = new EngineListener() {
        @Override
        public void onPrepared(long gen) {
            if (gen != engineGen)
                return;
            main.post(MediaService.this::onEnginePrepared);
        }

        @Override
        public void onCompletion(long gen) {
            if (gen != engineGen)
                return;
            main.post(MediaService.this::onEngineCompletion);
        }

        @Override
        public void onError(long gen, String msg, int what, int extra) {
            if (gen != engineGen)
                return;
            main.post(() -> onEngineError(msg, what, extra));
        }

        @Override
        public void onFatal(long gen, String msg, int what, int extra) {
            if (gen != engineGen)
                return;
            main.post(() -> onEngineFatal(msg, what, extra));
        }

        @Override
        public void onTtsRange(long gen, int s, int e) {
            if (gen != engineGen)
                return;
            // main.post(() -> { //surtout pas, source du décallage entre le highlight et
            // l'audio
            Intent i = new Intent(Intents.NOTIFICATION_TTS_RANGE)
                    .putExtra(Intents.EXTRA_TTS_START, s)
                    .putExtra(Intents.EXTRA_TTS_END, e);
            LocalBroadcastManager.getInstance(MediaService.this).sendBroadcast(i);
            // });
        }
    };

    private PlayerEngine engine;

    public boolean directPlay;

    private void broadcastUiState(String fromWhere) {
        final boolean ready = isReadyToPlay();
        final boolean playing = isPlaying();
        final String playMode = getPlayMode();

        PlaybackUiState s;

        String loadPhase = Intents.PHASE_OFF;
        if (PlaybackUiBus.get().state().getValue() != null) {
            loadPhase = PlaybackUiBus.get().state().getValue().loadPhase;
        }

        Bundle extras = new Bundle();
        extras.putInt(Intents.EXTRA_CUSTOM_SLEEP_MINUTES, lastCustomSleepMinutes);
        extras.putDouble(Intents.EXTRA_SPEED, getSpeed()); // TODO check that getSpeed, looks weird, ask Prefs... (every
                                                           // second since we are updating UI...)
        extras.putInt(Intents.EXTRA_AUDIO_SESSION_ID, getAudioSessionId());

        if (Var.PLAY_MODE_RADIO.equals(playMode) || isRadio()) {
            PlayList pl = PlayList.getInstance();
            String title = (pl != null && pl.getTitle() != null) ? pl.getTitle() : getString(R.string.live_radio);
            String text = getString(R.string.live_radio);
            String cover = (pl != null && pl.getImageUrl() != null) ? pl.getImageUrl() : "";
            long trackId = (pl != null) ? pl.getTrackId() : 0;

            s = new PlaybackUiState(
                    loadPhase, playing, ready, playMode,
                    0, 0, getSleepLeftMs(),
                    title, text, cover,
                    trackId,
                    /* folderId */ 0,
                    "MediaService.broadcastUiState() - radio " + fromWhere, -10, null);
        } else if (Var.PLAY_MODE_PODCAST.equals(playMode)) {
            PlayList pl = PlayList.getInstance();
            String title = (pl != null && pl.getTitle() != null) ? pl.getTitle() : getString(R.string.live_podcast);
            String text = getString(R.string.live_podcast);
            String cover = (pl != null && pl.getImageUrl() != null) ? pl.getImageUrl() : "";
            long trackId = (pl != null) ? pl.getTrackId() : 0;
            long pos = (engine != null) ? engine.getCurrentPosition() : 0;
            long dur = (engine != null) ? engine.getDuration() : 0;

            s = new PlaybackUiState(
                    loadPhase, playing, ready, playMode,
                    pos, dur, getSleepLeftMs(),
                    title, text, cover,
                    trackId,
                    /* folderId */ 0,
                    "MediaService.broadcastUiState() - podcast " + fromWhere, -10, extras);
        } else {

            long pos = (engine != null) ? engine.getCurrentPosition() : 0;
            long dur = (engine != null) ? engine.getDuration() : 0;

            PlayList pl = PlayList.getInstance();
            ZikFile z = (pl != null) ? pl.getZikFile() : null;
            Folder f = (pl != null) ? pl.getFolder() : null;

            String title = (z != null) ? z.getFolderName() : (f != null ? f.getName() : "");
            String subTitle = (z != null) ? z.getDisplayName() : "";
            String cover = (f != null) ? f.image : "";

            // Be defensive around engine readiness to avoid 0/0 churn if you want
            long trackId = (z != null) ? z.getId() : 0;
            long folderId = (f != null) ? f.getId() : 0;

            extras.putString(Intents.EXTRA_TTS_VOICE_NAME, getCurrentTtsVoiceName());
            // extras.putInt(Intents.EXTRA_TTS_START_OFFSET, currentStartChars);

            s = new PlaybackUiState(loadPhase, playing, ready, playMode, pos, dur, getSleepLeftMs(), title, subTitle,
                    cover, trackId, folderId, "MediaService.broadcastUiState() " + fromWhere, -10, extras);
        }
        PlaybackUiBus.get().emit(s);
    }

    private final MediaSessionCompat.Callback callback = new MediaSessionCompat.Callback() {

        // headset button pressed
        // android auto autoplay
        @Override
        public void onPlay() {
            super.onPlay();
            var info = MediaCallerHelper.getCallerInfo(MediaService.this);
            String callerInfo = MediaCallerHelper.describeCaller(MediaService.this, info);

            String lastPauseReason = Pref.getPauseReason();
            long lastPauseTime = Pref.getPauseTime();
            myLog("has been paused for " + Tonio.formatTime(System.currentTimeMillis() - lastPauseTime) + " - because ["
                    + lastPauseReason + "]");

            if ("AndroidAuto".equals(callerInfo)) {
                myLog("MediaSessionCompat.Callback : onPlay => from android auto - [" + callerInfo + "]");
                StartPlayHelper.carOnPlay(MediaService.this);
            } else {
                myLog("MediaSessionCompat.Callback : onPlay => from unknown - [" + callerInfo + "]");
                playPauseAudio("MediaSessionCompat.Callback");
            }
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            StartPlayHelper.carOnPlayFromMediaId(MediaService.this, mediaId, extras);
        }

        @Override
        public void onPause() {
            var info = MediaCallerHelper.getCallerInfo(MediaService.this);
            String pauseReason = "MediaSession.Callback.onPause - from "
                    + MediaCallerHelper.describeCaller(MediaService.this, info);
            super.onPause();
            playPauseAudio(pauseReason);
        }

        @Override
        public void onStop() {
            var info = MediaCallerHelper.getCallerInfo(MediaService.this);
            myLog("MediaSession.Callback.onStop - from " + MediaCallerHelper.describeCaller(MediaService.this, info));
            super.onStop();
            /*
             * This was a large source of bugs... by killing mediaPlayer... after a pause...
             * everything shit after...
             * mediaPlayerStop();
             * stopForeground(false);
             */
        }

        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
            KeyEvent ke = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            myLog("MediaSessionCompat.Callback - onMediaButtonEvent -- Received command = " + ke);
            return super.onMediaButtonEvent(mediaButtonIntent);
        }

        @Override
        public void onFastForward() {
            myLog("MediaSessionCompat.Callback - onFastForward()");
            forwardAudio();
            // super.onFastForward();
        }

        @Override
        public void onCommand(String command, Bundle extras, ResultReceiver cb) {
            myLog("MediaSessionCompat.Callback - onCommand(" + command + "..., Bundle extras, ResultReceiver cb");
            super.onCommand(command, extras, cb);
        }

        @Override
        public void onRewind() {
            myLog("MediaSessionCompat.Callback - onRewind()");
            backwardAudio();
            // super.onRewind();
        }

        @Override
        public void onSeekTo(long pos) {
            myLog("MediaSessionCompat.Callback - onSeekTo()");
            setPosition((int) pos);
            // super.onSeekTo(pos);
        }

        @Override
        public void onSkipToNext() {
            forwardAudio();
            myLog("MediaSessionCompat.Callback - onSkipToNext()");
            // super.onSkipToNext();
        }

        @Override
        public void onSkipToPrevious() {
            backwardAudio();
            myLog("MediaSessionCompat.Callback - onSkipToPrevious()");
            // super.onSkipToPrevious();
        }

        @Override
        public void onCustomAction(@NonNull String action, Bundle extras) {
            myLog("MediaSessionCompat.Callback - onCustomAction : " + action);
            switch (action) {
                case Intents.CMD_SET_SPEED: {
                    double s = extras != null ? extras.getDouble(Intents.EXTRA_SPEED, 1.0) : 1.0;
                    setSpeed(s); // your engine.setSpeed(...)
                    updateSessionState(isPlaying()); // reflect new speed in PlaybackState
                    break;
                }
                case Intents.CMD_TTS_SET_VOICE: {
                    String voice = extras != null ? extras.getString(Intents.EXTRA_TTS_VOICE_NAME) : null;
                    ContextCompat.startForegroundService(
                            MediaService.this,
                            new Intent(MediaService.this, MediaService.class)
                                    .setAction(Intents.CMD_TTS_SET_VOICE)
                                    .putExtra(Intents.EXTRA_TTS_VOICE_NAME, voice)
                                    .putExtra(Intents.EXTRA_FOREGROUND, true)
                                    .putExtra(Intents.EXTRA_CALLER, "MediaService.onCustomAction"));
                    break;
                }
                case Intents.CMD_TTS_SET_START: {
                    int start = extras != null ? extras.getInt(Intents.EXTRA_TTS_START_OFFSET, 0) : 0;
                    ContextCompat.startForegroundService(
                            MediaService.this,
                            new Intent(MediaService.this, MediaService.class)
                                    .setAction(Intents.CMD_TTS_SET_START)
                                    .putExtra(Intents.EXTRA_TTS_START_OFFSET, start)
                                    .putExtra(Intents.EXTRA_FOREGROUND, true)
                                    .putExtra(Intents.EXTRA_CALLER, "MediaService.onCustomAction"));
                    break;
                }
                case Intents.CMD_UPDATE_SLEEP: {
                    int minutes = extras != null ? extras.getInt(Intents.EXTRA_CUSTOM_SLEEP_MINUTES, 0) : 0;
                    ContextCompat.startForegroundService(
                            MediaService.this,
                            new Intent(MediaService.this, MediaService.class)
                                    .setAction(Intents.CMD_UPDATE_SLEEP)
                                    .putExtra(Intents.EXTRA_CUSTOM_SLEEP_MINUTES, minutes)
                                    .putExtra(Intents.EXTRA_FOREGROUND, true)
                                    .putExtra(Intents.EXTRA_CALLER, "MediaService.onCustomAction"));
                    break;
                }
                case "CMD_RESET_LAST_USER_ACTION": {
                    resetLastUserActionTimer();
                    break;
                }
                case Intents.CMD_TTS_GET_TEXT: {
                    myLog("MediaSessionCompat.Callback - onCustomAction : CMD_TTS_GET_TEXT");

                    android.os.ResultReceiver rr = (extras != null)
                            ? extras.getParcelable(Intents.EXTRA_RESULT_RECEIVER)
                            : null;

                    String txt = "";
                    if (engine instanceof TtsEngine) {
                        try {
                            String t = ((TtsEngine) engine).getText();
                            if (t != null)
                                txt = t;
                        } catch (Throwable t) {
                            myLogEE(t, "CMD_TTS_GET_TEXT - failed to get text");
                        }
                    } else {
                        myLogE("CMD_TTS_GET_TEXT ignored: engine is not TTS");
                    }

                    if (rr != null) {
                        Bundle out = new Bundle();
                        out.putString(Intents.EXTRA_TTS_TEXT, txt);
                        rr.send(0, out);
                    }
                    break;
                }

                default:
                    super.onCustomAction(action, extras);
            }
        }

    };

    private double speed = 1.0;
    private boolean ErrorLoadingFile = false;
    private long lastPausedTrackId = -1; // Track ID that was last paused, to detect track changes
    DecimalFormat myDF = new DecimalFormat("#,###.");

    /********************************************************************************
     * NATIVE METHODS
     * Because service always runs in the same process as clients, no need IPC.
     *
     */
    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onCreate() {
        isRunning = true;
        super.onCreate();
        myLogD("mediaService onCreate,  - called from " + CallerHelper.getCaller(3));

        // Media session (wrapped)
        media = new MediaSessionController(this, callback);
        MediaSessionCompat session = media.session();
        setSessionToken(session.getSessionToken());
        session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        myLogD("SERVICE session token=" + session.getSessionToken()
                + " token@=" + System.identityHashCode(session.getSessionToken()));

        updateSessionState(false);

        PendingIntent contentPi = PendingIntent.getActivity(
                this, 0,
                new Intent(this, PlayActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        media.setSessionActivity(contentPi);

        // Notification helper
        notif = new com.driot.bookplayer.player.PlaybackNotificationManager(
                this, ID_NOTIFICATION_PLAY_AUDIO_CHANNEL, R.mipmap.ic_launcher);
        notif.ensureChannel("Music Playback", "Bookplayer Music Playback Controls");

        // Sleep timer (ticks every second)
        sleepCheckHandler = new Handler();
        playTimer = new PlayTimer(
                sleepCheckHandler,
                DELAY_CHECK_TIMER_SLEEP,
                new PlayTimer.Listener() {
                    @Override
                    public void onTick(int elapsedSeconds) {
                        do_1sec_stuff(false);
                        emitUiTick("MediaService.SleepTimer.onTick");
                    }

                    @Override
                    public void onEveryMinute(@NonNull String elapsedCategory) {
                        FirebaseAnalyticsHelper.tellPlayFor1min(elapsedCategory, getPlayMode());
                    }

                    @Override
                    public void onReachedMax() {
                        if (Option.getBeepAutoStop())
                            playBeep("2beeps");
                        LocalBroadcastManager.getInstance(MediaService.this)
                                .sendBroadcast(new Intent(NOTIFICATION_PLAYBACK_MAXTIMEREACH));
                        pauseAudio("auto-sleep");
                    }
                });

        // Pause/trim watcher (kills service if paused too long)
        pauseCheckHandler = new Handler();
        pauseWatcher = new com.driot.bookplayer.player.PauseTrimWatcher(
                pauseCheckHandler, DELAY_CHECK_TIMER_PAUSE,
                new com.driot.bookplayer.player.PauseTrimWatcher.Killer() {
                    @Override
                    public void kill() {
                        shutdown(false);
                    }

                    @Override
                    public void onLog(String msg) {
                        String newMsg = msg;
                        if (PlayList.getInstance() == null)
                            newMsg += " [null playlist]";
                        myLogD(newMsg);
                    }
                },
                System::currentTimeMillis,
                Pref::getPauseTime,
                TRIM_AFTER_PAUSE_MS);
        pauseWatcher.start();

        // Audio focus
        focus = new com.driot.bookplayer.player.AudioFocusHelper(
                this,
                new com.driot.bookplayer.player.AudioFocusHelper.Listener() {
                    @Override
                    public void onFocusGain() {
                        myLogI("onFocusGain");
                        // restore volume if ducked
                        try {
                            if (engine != null)
                                engine.setVolume(preDuckVolume);
                        } catch (Throwable ignored) {
                        }
                        if (pausedByFocusLoss) {
                            myLogW("play audio because paused by focus lost");
                            playAudio();
                            pausedByFocusLoss = false;
                        }
                        // ensure session is active
                        media.setActive(true);
                    }

                    @Override
                    public void onFocusLost(int change) {
                        myLog("onFocusLost");
                        logFocusChange(change);
                        /*
                         * //TODO cree un timer sur le focus lost, et voir dans les 3sec si c'etait pas
                         * AA qui se connectait, si c'est le cas, remettre le play?
                         * boolean keepOnPhone = Option.getAutomotiveKeepPhonePlaybackOnCarConnect(); //
                         * new toggle (default false)
                         * boolean inGrace = CarSignals.withinCarConnectGrace(2500);
                         * myLog("keepOnPhone = " + keepOnPhone);
                         * myLog("inGrace = " + inGrace);
                         * 
                         * if (keepOnPhone && inGrace && (change ==
                         * AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || change ==
                         * AudioManager.AUDIOFOCUS_LOSS)) {
                         * myLog("AUDIOFOCUS_LOSS_TRANSIENT");
                         * // treat transient like duck during grace window (don’t pause)
                         * startDuck();
                         * return;
                         * }
                         */

                        // normal behavior: pause if we were playing
                        pausedByFocusLoss = isPlaying();
                        pauseAudio("focus lost");
                    }

                    @Override
                    public void onDuck(boolean ducking) {
                        if (ducking)
                            startDuck();
                        else
                            stopDuck();
                    }

                    private void startDuck() {
                        try {
                            if (engine != null) {
                                preDuckVolume = 1f; // if you have a getter, use it; else assume 1
                                engine.setVolume(0.2f);
                            }
                        } catch (Throwable ignored) {
                        }
                    }

                    private void stopDuck() {
                        try {
                            if (engine != null)
                                engine.setVolume(preDuckVolume);
                        } catch (Throwable ignored) {
                        }
                    }
                });

        // Progress updater (DB)
        progress = new PlaybackProgressUpdater(this);

        myLogD("onCreate() - END");
    }
    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private boolean showForegroundNotification(boolean playing) {

        if ("radio".equals(getPlayMode())) {
            // 1) Limit session capabilities
            updateSessionState(playing);

            PlayList pl = PlayList.getInstance();
            long trackId = (pl != null && pl.getTrackId() > 0) ? pl.getTrackId() : -1;
            String streamTitle = (pl != null && pl.getTitle() != null) ? pl.getTitle() : getString(R.string.live_radio);
            String streamImageUrl = (pl != null) ? pl.getImageUrl() : null;

            // 2) (Async) fetch cover, then set metadata & update notif
            Bitmap currentArt = null;
            media.setMetadataRadio(streamTitle, "", "",
                    currentArt);

            Notification n = notif.build(
                    media.session(),
                    playing,
                    streamTitle != null ? streamTitle : getString(R.string.live_radio),
                    getString(R.string.live_radio),
                    new PlaybackNotificationManager.ActionProvider() {
                        @NonNull
                        @Override
                        public PendingIntent rewind() {
                            return null;
                        } // no-op

                        @NonNull
                        @Override
                        public PendingIntent fastForward() {
                            return null;
                        } // no-op

                        @NonNull
                        @Override
                        public PendingIntent play() {
                            return MediaButtonReceiver.buildMediaButtonPendingIntent(MediaService.this,
                                    PlaybackStateCompat.ACTION_PLAY);
                        }

                        @NonNull
                        @Override
                        public PendingIntent pause() {
                            return MediaButtonReceiver.buildMediaButtonPendingIntent(MediaService.this,
                                    PlaybackStateCompat.ACTION_PAUSE);
                        }

                        @NonNull
                        @Override
                        public PendingIntent content() {
                            return NavHelper.getNavToRadioActivityPendingIntent(MediaService.this, trackId);
                        }
                    });
            if (!startForegroundWithBuildCheck(n))
                return false;

            // 3) If you only have a favicon URL, load it and refresh:
            if (currentArt == null && streamImageUrl != null && !streamImageUrl.isEmpty()) {
                com.bumptech.glide.Glide.with(getApplicationContext())
                        .asBitmap()
                        .load(streamImageUrl)
                        .into(new com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                            @Override
                            public void onResourceReady(@NonNull Bitmap bmp,
                                    com.bumptech.glide.request.transition.Transition<? super Bitmap> t) {
                                media.setMetadataRadio(
                                        streamTitle != null ? streamTitle : getString(R.string.live_radio), "", "",
                                        bmp);
                                // rebuild/update the notification so largeIcon shows
                                Notification updated = notif.build(
                                        media.session(), playing,
                                        streamTitle != null ? streamTitle : getString(R.string.live_radio),
                                        getString(R.string.live_radio),
                                        /* same ActionProvider */ new PlaybackNotificationManager.ActionProvider() {
                                            @NonNull
                                            @Override
                                            public PendingIntent rewind() {
                                                return null;
                                            }

                                            @NonNull
                                            @Override
                                            public PendingIntent fastForward() {
                                                return null;
                                            }

                                            @NonNull
                                            @Override
                                            public PendingIntent play() {
                                                return MediaButtonReceiver.buildMediaButtonPendingIntent(
                                                        MediaService.this, PlaybackStateCompat.ACTION_PLAY);
                                            }

                                            @NonNull
                                            @Override
                                            public PendingIntent pause() {
                                                return MediaButtonReceiver.buildMediaButtonPendingIntent(
                                                        MediaService.this, PlaybackStateCompat.ACTION_PAUSE);
                                            }

                                            @NonNull
                                            @Override
                                            public PendingIntent content() {
                                                return NavHelper.getNavToRadioActivityPendingIntent(MediaService.this,
                                                        trackId);
                                            }
                                        });
                                startForegroundWithBuildCheck(updated);
                            }

                            @Override
                            public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
                            }
                        });
            }
            return true;
        }

        CharSequence title = "---";
        CharSequence text = "---";
        Notification n = notif.build(
                media.session(),
                playing,
                title,
                text,
                new com.driot.bookplayer.player.PlaybackNotificationManager.ActionProvider() {
                    @NonNull
                    @Override
                    public PendingIntent rewind() {
                        return MediaButtonReceiver.buildMediaButtonPendingIntent(MediaService.this,
                                PlaybackStateCompat.ACTION_REWIND);
                    }

                    @NonNull
                    @Override
                    public PendingIntent play() {
                        return MediaButtonReceiver.buildMediaButtonPendingIntent(MediaService.this,
                                PlaybackStateCompat.ACTION_PLAY);
                    }

                    @NonNull
                    @Override
                    public PendingIntent pause() {
                        return MediaButtonReceiver.buildMediaButtonPendingIntent(MediaService.this,
                                PlaybackStateCompat.ACTION_PAUSE);
                    }

                    @NonNull
                    @Override
                    public PendingIntent fastForward() {
                        return MediaButtonReceiver.buildMediaButtonPendingIntent(MediaService.this,
                                PlaybackStateCompat.ACTION_FAST_FORWARD);
                    }

                    @NonNull
                    @Override
                    public PendingIntent content() {
                        return NavHelper.mediaServiceClickNavigateToActivity(MediaService.this);
                    }
                });
        return startForegroundWithBuildCheck(n);
    }

    private void startPlayWithEngine() {
        // Audio focus first
        focus.request();

        PlayerEngine e = this.engine; // snapshot to avoid races
        if (e == null) {
            myLogE("startPlayWithEngine: engine is null (race) — aborting start");
            // Optionally: try to reload
            // directPlay = true; loadFile();
            return;
        }

        myLogD("about to call engine.start()");
        logPauseTime();

        setUiPhase(Intents.PHASE_ENGINE_STARTING, null);

        engine.start();
        Pref.setPaused("NOT PAUSED, PLAYING", 0);

        updateSessionState(true);
        if (!(engine instanceof TtsEngine))
            setUiPhase(Intents.PHASE_SPEAKING, null);

        engine.setSpeed((float) getSpeed());
        if (!media.session().isActive())
            media.setActive(true);

        if (!playTimer.isRunning()) {
            int defaultSleep = isRadio() ? Option.getTimeBeforeSleepRadio() : Option.getTimeBeforeSleep();
            int minutes = (customSleepTime == 0) ? defaultSleep : customSleepTime;
            playTimer.start(minutes);
        }

        if (!showForegroundNotification(true)) {
            myLogEE(null, "startPlayWithEngine: failed to start foreground, aborting");
            // If we can't show notification, we likely can't play (FGS restriction).
            // We should stop the engine.
            try {
                if (engine != null)
                    engine.stop();
            } catch (Exception ignored) {
            }
            return;
        }
        broadcastUiState("startPlayWithEngine");
    }

    private void nextTrack() {
        myLog("Next track");

        if (engine instanceof TtsEngine)
            setUiPhase(Intents.PHASE_LOADING_TEXT, "Loading text…");

        if (engine instanceof ExoStreamPlayerEngine) {
            if (Option.getBeepBookEnd())
                playBeep("3beeps");
            myLog("podcast streaming end => kill service");
            shutdown(false);
            return;
        }

        PlayList pl = PlayList.getInstance();
        if (pl == null) {
            alertError("nextTrack", "nextTrack : error getting playlist");
            loadFileKO(null);
            return;
        }
        final ZikFile nextZikFile = pl.nextTrack();

        if (engine != null) {
            try {
                if (engine instanceof TtsEngine) {
                    engine.release();
                }
                engine.stop();
                engine.reset();
            } catch (Exception ignored) {
            }
        }

        myLog("loading next track : n°" + PlayList.getInstance().getNumSlashTotal());
        if (nextZikFile == null) {
            myLogEE(null, "next zikFile null");
            return;
        }

        if (Option.getBeepChapter())
            playBeep("1beep");

        AppDatabase.databaseWriteExecutor.execute(() -> {

            if (Option.getStartAtZeroNextTrack()) {
                nextZikFile.setPosition(0L);
                AppDatabase.getDatabase(this).zikFileDao().update(nextZikFile);
            }

            if (loadAndPlayTrack(nextZikFile)) {
                alertNewTrack();
            } else {
                myLogEE(null, "error loading next track");
            }

        });
    }

    private void alertNewTrack() {
        myLog("alertNewTrack()");
        PlayList pl = PlayList.getInstance();
        if (pl == null) {
            myLogEE(null, "playlist null on newTrack");
            return;
        }
        String cover = null;
        if (pl.getFolder() != null)
            cover = pl.getFolder().image;
        ZikFile z = pl.getZikFile();
        if (z != null) {
            media.updateState(PlaybackStateCompat.STATE_BUFFERING, 0, 0f, ACTIONS_FILE);
            media.setMetadata(z.getDisplayName(), z.getFolderName(), z.getFolderName(), 0L,
                    ImageHelper.decodeBitmapFromStringUri(this, cover, 512));
            if (!showForegroundNotification(isPlaying())) {
                myLogEE(null, "alertNewTrack: failed to show foreground notification");
            }
        }
    }

    // right now used only to close Activity, maybe add something on fragment ?
    // right now, if not tts, will finish activity and display error ui message
    private void alertError(String from, String errMsg) {
        myLogE("sendBroadcast alertError - from [" + from + "] - errMsg=[" + errMsg + "]");
        Intent i = new Intent(NOTIFICATION_ERROR)
                .putExtra(FROM, from)
                .putExtra(ERR_MSG, errMsg);

        PlayList pl = PlayList.getInstance();
        if (pl != null) {
            ZikFile zf = pl.getZikFile();
            if (zf != null) {
                i.putExtra(TRACK_PATH, zf.getPath());
            }
        }
        if (pl != null && pl.getNumZikFile() > 0
                && !("radio".equals(getPlayMode()) || "podcast".equals(getPlayMode()))) {
            i.putExtra(TRACKNUMBER, pl.getNumZikFile());
        }
        LocalBroadcastManager.getInstance(MediaService.this).sendBroadcast(i);
    }

    private void alertPlaylistFinished() {
        myLog("sendBroadcast alertPlaylistFinished");
        LocalBroadcastManager.getInstance(MediaService.this).sendBroadcast(new Intent(NOTIFICATION_PLAYLISTFINISHED));
    }

    // ----------------------------------------------------------------------------------------------------------------------------------------------------------
    // ----------------------------------------------------------------------------------------------------------------------------------------------------------
    // ------ START COMMAND
    // ----------------------------------------------------------------------------------------------------------------------------------------------------------
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        myLog("onStartCommand()");
        if (intent != null) {
            String strCallLog = "intent = " + intent +
                    "\ncalled by = " + intent.getStringExtra(Intents.EXTRA_CALLER) +
                    "\nwith action = " + intent.getAction();
            FirebaseAnalyticsHelper.setCustomKeyCrashlytics("MediaService.onStartCommand()", strCallLog);
            if (intent.getBooleanExtra(Intents.EXTRA_FOREGROUND, false)) {
                myLogI("FOREGROUND MediaService start\n" + strCallLog);
            } else {
                myLog("MediaService start\n" + strCallLog);
            }
        } else { // happens when Android restarts your sticky service after it was killed, no
                 // 5-second foreground requirement in this case because the system didn’t just
                 // call startForegroundService(...) on your behalf;
            FirebaseAnalyticsHelper.setCustomKeyCrashlytics("MediaService.onStartCommand()", "no intent");
            myLogW("MediaService start with no intent - Android restarts? - because of START_STICKY and no START_REDELIVER_INTENT");
            return START_STICKY;
        }

        Bundle b = intent.getExtras();
        if (b == null) {
            myLogW("No extras on intent!");
        } else {
            for (String k : b.keySet()) {
                Object v = b.get(k);
                myLogD("extra [" + k + "] = " + v + " (" + (v == null ? "null" : v.getClass().getSimpleName()) + ")");
            }
        }

        final String action = intent.getAction();
        if (action == null) {
            myLogW("onStartCommand with no intent.action");
            return START_STICKY;
        }

        switch (action) {
            case Intents.ACTION_PLAY_FROM_TRACK: {
                // Enter foreground *before* async work to satisfy the 5s rule
                goForegroundPreparing("Preparing…", "Loading selected track");

                final long trackId = intent.getLongExtra(Intents.EXTRA_TRACK_ID, -1);
                final boolean isPodcast = intent.getBooleanExtra(Intents.EXTRA_IS_PODCAST, false);
                final boolean newestFirst = intent.getBooleanExtra(Intents.EXTRA_TRACK_ORDER_NEWEST_FIRST, true);
                myLog("ACTION_PLAY_FROM_TRACK => trackId : [" + trackId + "] - isPodcast : [" + isPodcast
                        + "] - newestFirst : [" + newestFirst + "]");

                ZikFile zikFile = intent.getParcelableExtra(Intents.EXTRA_ZIKFILE);
                if (zikFile == null) {
                    AppDatabase.databaseReadExecutor.execute(() -> {
                        ZikFile newZikFile = AppDatabase.getDatabase(this).zikFileDao().getById(trackId);
                        if (newZikFile == null) {
                            myLogE("zikFile is Null");
                        } else {
                            boolean ok = loadAndPlayTrack(newZikFile, isPodcast, newestFirst);
                            if (!ok) {
                                myLogEE(null, "ACTION_PLAY_FROM_TRACK: playback failed [newZikFile]");
                            }
                        }
                    });
                } else {
                    AppDatabase.databaseReadExecutor.execute(() -> {
                        boolean ok = loadAndPlayTrack(zikFile, isPodcast, newestFirst);
                        if (!ok) {
                            myLogEE(null, "ACTION_PLAY_FROM_TRACK: playback failed [ZikFile]");
                        }
                    });
                }
                return START_STICKY;

            }

            case Intents.ACTION_PLAY_FROM_FOLDER: {
                goForegroundPreparing("Preparing…", "Loading folder");

                final long folderId = intent.getLongExtra(Intents.EXTRA_FOLDER_ID, -1);
                if (folderId > 0) {
                    AppDatabase.databaseReadExecutor.execute(() -> {
                        Folder folder = AppDatabase.getDatabase(this).folderDao().getById(folderId);
                        loadAndPlayFolder(folder);
                    });
                }
                return START_STICKY;
            }

            case "CMD_STOP": { // keep string CMD_STOP here as can be called by others than app
                myLog("CMD_STOP");
                shutdown(false);
                return START_NOT_STICKY; // let's try to avoid crashes
            }

            // -------- Direct transport commands (often from notif/media buttons) --------
            case "CMD_PLAY": {
                // If the service was started via startForegroundService() from background,
                // enter foreground immediately to avoid the 5s crash.
                goForegroundPreparing("Resuming…", null);
                playAudio(); // your existing logic will update the notif state
                return START_STICKY;
            }

            case "CMD_PAUSE": {
                if (engine == null || !engine.isPlaying()) {
                    // Show a paused notification if you want to remain foreground while paused:
                    if (!showForegroundNotification(false)) {
                        myLogEE(null, "CMD_PAUSE: failed to show foreground notification");
                    }
                }
                pauseAudio("cmd-pause");
                // If you prefer to drop foreground while paused:
                // try { stopForeground(false); } catch (Throwable ignore) {}
                return START_STICKY;
            }

            case "CMD_NEXT": {
                forwardAudio();
                return START_STICKY;
            }

            case "CMD_PREV": {
                backwardAudio();
                return START_STICKY;
            }

            case "CMD_SEEK": {
                setPosition(intent.getIntExtra("posMs", 0));
                return START_STICKY;
            }

            case Intent.ACTION_MEDIA_BUTTON: {
                KeyEvent ke = intent.hasExtra(Intent.EXTRA_KEY_EVENT)
                        ? intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                        : null;
                String keyEventString = (ke != null) ? ke.getCharacters() : "no key event";
                myLog("onStartCommand() - Intent.ACTION_MEDIA_BUTTON : " + keyEventString);
                FirebaseAnalyticsHelper.setCustomKeyCrashlytics("MediaService.onStartCommand() - ACTION_MEDIA_BUTTON",
                        keyEventString);

                goForegroundPreparing(getString(R.string.media_button_action), null);
                MediaButtonReceiver.handleIntent(media.session(), intent);

                // Optional: if handling didn’t start playback, keep or drop FG deliberately
                main.postDelayed(() -> {
                    boolean playing = (engine != null && engine.isPlaying());
                    if (!playing) {
                        // either keep a paused notif…
                        showForegroundNotification(false);
                        // …or drop foreground if you prefer:
                        // stopForeground(false);
                    }
                }, 200);

                return START_STICKY;
            }

            case Intents.ACTION_PLAY_STREAM: {
                // Enter foreground ASAP (5s rule)
                goForegroundPreparing(getString(R.string.loading), null);

                final String playMode = intent.getStringExtra(Intents.EXTRA_PLAY_MODE);
                final String url = intent.getStringExtra(Intents.EXTRA_STREAM_URL);
                // Note: PlayList is already created by StartPlayHelper.playStream() before
                // the intent is fired. We just need to re-play the engine.

                if (url == null || url.isEmpty()) {
                    myLogE("ACTION_PLAY_STREAM without url");
                    return START_NOT_STICKY;
                }
                if (playMode == null || playMode.isEmpty()) {
                    myLogE("ACTION_PLAY_STREAM without playMode");
                    return START_NOT_STICKY;
                }

                if (engine != null)
                    engine.stop();
                playStream(playMode, url);
                return START_STICKY;
            }

            case Intents.CMD_TTS_SET_VOICE: {
                if (intent.getBooleanExtra(Intents.EXTRA_FOREGROUND, false)) {
                    goForegroundPreparing("Changing voice...", null);
                }
                final String voiceName = intent.getStringExtra(Intents.EXTRA_TTS_VOICE_NAME);
                if (voiceName == null) {
                    myLogEE(null, "CMD_TTS_SET_VOICE => EXTRA_TTS_VOICE_NAME is null");
                    return START_STICKY;
                }
                try {
                    PlaybackUiBus.get().setLoadPhase(Intents.PHASE_LOADING_VOICE);
                    if (engine instanceof TtsEngine) {
                        boolean ok = ((TtsEngine) engine).setVoiceByName(voiceName);
                        myLog("Voice change to " + voiceName + ", success = " + ok);
                        if (ok) {
                            PlaybackUiBus.get().setLoadPhase(Intents.PHASE_VOICE_LOADED);
                            myLog("CMD_TTS_SET_VOICE: voice changed successfully, forcing engine start for recovery");
                            startPlayWithEngine();
                        } else {
                            PlaybackUiBus.get().setLoadPhase(Intents.PHASE_ERROR);
                        }
                    } else {
                        PlaybackUiBus.get().setLoadPhase(Intents.PHASE_WARMING_UP);
                        myLogW("CMD_TTS_SET_VOICE: engine is null or not TTS, attempting to re-init");
                        PlayList pl = PlayList.getInstance();
                        if (pl != null && pl.isZikFile()) {
                            loadAndPlayTrack(pl.getZikFile(), false, false);
                        } else {
                            myLogW("CMD_TTS_SET_VOICE: playlist null, attempting restore from storage");
                            PlayList.createFromStorage(this, true, restoredPl -> {
                                if (restoredPl != null && restoredPl.isZikFile()) {
                                    main.post(() -> loadAndPlayTrack(restoredPl.getZikFile(), false, false));
                                } else {
                                    myLogE("CMD_TTS_SET_VOICE: cannot re-init, playlist restore failed");
                                    PlaybackUiBus.get().setLoadPhase(Intents.PHASE_ERROR);
                                }
                            });
                        }
                    }
                } catch (Throwable t) {
                    myLogEE(t, "CMD_TTS_SET_VOICE failed");
                    PlaybackUiBus.get().setLoadPhase(Intents.PHASE_ERROR);
                }
                return START_STICKY;
            }

            case Intents.CMD_TTS_SET_START: {
                int ch = intent.getIntExtra(Intents.EXTRA_TTS_START_OFFSET, -1);
                if (ch >= 0)
                    handleTtsSeekChars(ch);
                return START_STICKY;
            }

            case Intents.CMD_UPDATE_SLEEP: {
                int newSleepValueInMin = intent.getIntExtra(Intents.EXTRA_CUSTOM_SLEEP_MINUTES, -1);
                if (newSleepValueInMin > 0)
                    playTimer.reload(newSleepValueInMin);
                return START_STICKY;
            }

            case "CMD_RESET_LAST_USER_ACTION": {
                resetLastUserActionTimer();
                return START_STICKY;
            }

            default:
                // Unknown action — keep service alive and ensure we have a notif if needed
                myLogEE(null, "onStartCommand() - unknown action : [" + action + "]");
                if (!showForegroundNotification(isPlaying())) {
                    myLogEE(null, "onStartCommand default: failed to show foreground notification");
                }
                return START_STICKY;
        }
    }
    // ----------------------------------------------------------------------------------------------------------------------------------------------------------
    // ----------------------------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Minimal foreground entry used before async prep to satisfy the 5s
     * requirement.
     */
    private void goForegroundPreparing(@Nullable CharSequence title, @Nullable CharSequence text) {
        try {
            CharSequence t = (title != null) ? title : "Preparing…";
            CharSequence s = (text != null) ? text : "Please wait";

            PlaybackNotificationManager.ActionProvider minimal = new PlaybackNotificationManager.ActionProvider() {
                @Override
                public PendingIntent rewind() {
                    return null;
                }

                @Override
                public PendingIntent play() {
                    return null;
                }

                @Override
                public PendingIntent pause() {
                    return null;
                }

                @Override
                public PendingIntent fastForward() {
                    return null;
                }

                @NonNull
                @Override
                public PendingIntent content() {
                    return NavHelper.navigateToMain(MediaService.this);
                }
            };

            Notification n = notif.buildPreparing(t, s, /* content PI */ minimal.content());
            if (!startForegroundWithBuildCheck(n)) {
                myLogEE(null, "goForegroundPreparing: failed to start foreground");
                return;
            }

            media.setActive(true);

            PlaybackStateCompat placeholder = new PlaybackStateCompat.Builder()
                    .setActions(currentActions())
                    .setState(
                            PlaybackStateCompat.STATE_BUFFERING,
                            0L,
                            0f,
                            System.currentTimeMillis())
                    .build();
            media.session().setPlaybackState(placeholder);

        } catch (Throwable e) {
            myLogEE(e, "goForegroundPreparing()");
        }
    }

    /*
     * onTaskRemoved
     * It fires when
     * The user opens the Recents screen and swipes your app’s card away.
     * The user taps “Clear all” in Recents (which removes your task).
     * You call finishAndRemoveTask() on an Activity (explicitly removes the task).
     * 
     * It does not fire when
     * The user presses Back to exit an Activity (unless you used
     * finishAndRemoveTask()).
     * The user presses Home, switches apps, or turns the screen off.
     * The process is killed by the system’s low-memory killer or the user hits
     * Force stop in Settings.
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        String logMsg = "onTaskRemoved";
        if (rootIntent.getAction() != null)
            logMsg = logMsg + " from " + rootIntent.getAction();
        myLogW(logMsg);
        shutdown(false);
        super.onTaskRemoved(rootIntent);
    }

    private void shutdown(boolean fromDestroy) {
        final boolean first = beginShutdown();
        myLogI("shutdown(" + fromDestroy + ") first=" + first + " state=" + state.get());

        stopAsyncWork();
        PlaybackUiBus.get().clear();

        // Tell controllers we’re stopping (prevents AA/BT from poking)
        try {
            if (media != null) {
                PlaybackStateCompat s = new PlaybackStateCompat.Builder()
                        .setActions(0L)
                        .setState(PlaybackStateCompat.STATE_STOPPED, 0L, 0f, System.currentTimeMillis())
                        .build();
                media.session().setPlaybackState(s);
                media.setActive(false);
            }
        } catch (Throwable ignored) {
        }

        // Always kill the audio path (idempotent)
        hardStopAudio("shutdown");

        // Drop focus after we’re silent
        try {
            if (focus != null)
                focus.abandon();
        } catch (Throwable ignored) {
        }

        // Remove UI surface *after* silence
        try {
            stopForeground(true);
        } catch (Throwable ignored) {
        }
        try {
            if (notif != null)
                notif.cancel(ID_NOTIFICATION_PLAY_AUDIO_INT);
        } catch (Throwable ignored) {
        }

        // Release session last
        // try { if (media != null) media.release(); } catch (Throwable ignored) {} //no
        // or I will get infinite onGetRoot !!

        // Clear state (safe to run twice)
        try {
            PlayList pl = PlayList.getInstance();
            if (pl != null)
                pl.clear();
        } catch (Throwable ignored) {
        }
        isRunning = false;

        state.set(ServiceState.STOPPED);
        if (!fromDestroy)
            requestGracefulStopIfNoClients(); // safe if already stopping
    }

    private void requestGracefulStopIfNoClients() {
        serviceHandler.removeCallbacks(stopRunnable);
        if (!hasBrowserClients()) {
            serviceHandler.postDelayed(stopRunnable, STOP_GRACE_MS);
        } else {
            myLogI("Browser clients present → do not stop service");
        }
    }

    private void stopAsyncWork() {
        try {
            if (playTimer != null)
                playTimer.stop();
        } catch (Throwable ignored) {
        }
        try {
            if (pauseWatcher != null)
                pauseWatcher.stop();
        } catch (Throwable ignored) {
        }
        try {
            main.removeCallbacksAndMessages(null);
        } catch (Throwable ignored) {
        }
    }

    private void hardStopAudio(@NonNull String why) {
        myLogI("hardStopAudio: " + why);
        PlayerEngine e = this.engine;
        this.engine = null; // prevent any more calls into it
        if (e != null) {
            try {
                e.pause();
            } catch (Throwable ignored) {
            }
            try {
                e.stop();
            } catch (Throwable ignored) {
            }
            try {
                e.release();
            } catch (Throwable t) {
                myLogEE(t, "engine.release failed");
            }
            try {
                e.reset();
            } catch (Throwable ignored) {
            } // ok to be a no-op after release
        }

        // Also stop TTS engine to prevent infinite loops
        try {
            AppTtsManager ttsMgr = ttsManager;
            if (ttsMgr != null) {
                ttsMgr.stop();
            }
        } catch (Throwable ignored) {
        }
        try {
            if (playTimer != null)
                playTimer.stop();
        } catch (Throwable ignored) {
        }
        try {
            if (pauseWatcher != null)
                pauseWatcher.stop();
        } catch (Throwable ignored) {
        }
        try {
            if (focus != null)
                focus.abandon();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onDestroy() {
        myLog("onDestroy()");
        shutdown(true);
        super.onDestroy();
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        myLogI("------------ onGetRoot ------------  ");
        myLog("from pkg=" + clientPackageName + " uid=" + clientUid + "\nhints : "
                + getBundleString(rootHints).replace(";", "\n"));

        var info = MediaCallerHelper.getCallerInfo(MediaService.this);
        String callerInfo = MediaCallerHelper.describeCaller(MediaService.this, info);
        myLog("callerInfo: " + callerInfo);

        return StartPlayHelper.onGetRoot(clientPackageName, callerInfo);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId,
            @NonNull Result<List<MediaBrowserCompat.MediaItem>> result,
            @NonNull Bundle options) {
        myLogD("onLoadChildren(+opts) parentId=" + parentId + "  - options=" + getBundleString(options));
        StartPlayHelper.loadChildrenImpl(this, parentId, options, result);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId,
            @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        myLogD("onLoadChildren parentId=" + parentId + " (no options)");
        StartPlayHelper.loadChildrenImpl(this, parentId, null, result);
    }

    @Override
    public void onSearch(@NonNull String query, Bundle extras,
            @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        StartPlayHelper.doSearch(this, query, extras, result);
    }

    @Override
    public IBinder onBind(Intent intent) {
        int c = boundClientCount.incrementAndGet();
        myLogD("onBind() -> boundClientCount=" + c + " intent=" + intent);
        // if a stop was pending, cancel it because a client just bound
        serviceHandler.removeCallbacks(stopRunnable);
        return super.onBind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        int c = boundClientCount.decrementAndGet();
        myLogD("onUnbind() -> boundClientCount=" + c + " intent=" + intent);
        // allow rebind callbacks if you want to observe them
        return true; // keep onRebind() callbacks
    }

    @Override
    public void onRebind(Intent intent) {
        int c = boundClientCount.incrementAndGet();
        myLogD("onRebind() -> boundClientCount=" + c + " intent=" + intent);
        serviceHandler.removeCallbacks(stopRunnable);
        super.onRebind(intent);
    }

    // ----------------------------------------------------------------------------------------------
    // ----------------------------------------------------------------------------------------------

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_THRESHOLD) {
            myLogW("onTrimMemory() - level=[" + level + "] >= " + TRIM_MEMORY_THRESHOLD);
            logPauseTime();
            /*
             * if (mediaPlayer != null) {
             * try {
             * mediaPlayer.release();
             * mediaPlayer = null;
             * myLog("mediaPlayer released due to memory pressure");
             * } catch (Exception e) {
             * myLogEE(e,"onTrimMemory - Error releasing mediaPlayer");
             * }
             * } else {
             * myLog("mediaPlayer was already null");
             * }
             */
        }
    }

    // Swap current engine with a new one, releasing TTS if needed, keeping flags
    // intact.
    private void setEngine(@NonNull PlayerEngine newEngine) {
        try {
            if (engine != null) {
                if (engine instanceof TtsEngine) {
                    ((TtsEngine) engine).release();
                } else {
                    engine.stop();
                    engine.reset();
                }
            }
        } catch (Throwable ignored) {
        }
        engine = newEngine;
    }

    /**
     * Simplified, side-effect-free loader.
     */
    public void loadFile(String playMode, Uri src, ZikFile zf) {
        myLogD("loadFile() - playMode=[" + playMode + "] - directPlay=" + directPlay +
                "\nuri = [" + src + "]");

        // New generation (guards async callbacks)
        engineGen++;
        long gen = engineGen;

        // Swap engine
        PlayerEngine fresh = Var.PLAY_MODE_TTS.equals(playMode)
                ? new TtsEngine(getApplicationContext(), ttsManager, engineCb, gen)
                : new MediaPlayerEngine(engineCb, gen);
        setEngine(fresh);

        ErrorLoadingFile = false;

        // Update UI to "buffering" and notif already done...
        if (!showForegroundNotification(isPlaying())) {
            myLogEE(null, "loadFile: failed to show foreground notification (FGS restriction?), aborting load.");
            setUiPhase(Intents.PHASE_ERROR, "Background start restricted");
            loadFileKO(zf.getPath());
            return;
        }

        // PHASE: LOADING_TEXT (text extraction / paragraphize happens inside
        // setDataSource)
        if (Var.PLAY_MODE_TTS.equals(playMode))
            setUiPhase(Intents.PHASE_LOADING_TEXT, "Loading text…");

        // Update media session metadata early (title/sub), set BUFFERING, show paused
        // notif
        media.updateState(PlaybackStateCompat.STATE_BUFFERING, 0, 0f, ACTIONS_FILE);

        // String cover = (pl.getFolder()==null) ? null : pl.getFolder().image;
        // media.setMetadata(zf.getDisplayName(), zf.getFolderName(),
        // zf.getFolderName(), 0L, ImageHelper.decodeBitmapFromStringUri(this, cover,
        // 512));

        // showForegroundNotification(isPlaying()); // Already done above

        try {
            engine.reset();
            engine.setDataSource(this, src, zf.getDisplayName());

            if (engine instanceof TtsEngine) {
                String picked = null;
                String pickedSource = "system";

                // 1) Per-book voice (never override this later)
                String perBook = null;
                PlayList pl = PlayList.getInstance();
                if (pl != null && pl.getFolder() != null) {
                    perBook = pl.getFolder().ttsVoice;
                }
                if (perBook != null && !perBook.isEmpty() && !Option.DEFAULT_VOICE.equalsIgnoreCase(perBook)) {
                    picked = perBook;
                    pickedSource = "book";
                } else {
                    // 2) App-wide fallback (only if no per-book)
                    String appWide = Option.getTtsVoice();
                    if (appWide != null && !appWide.isEmpty() && !Option.DEFAULT_VOICE.equalsIgnoreCase(appWide)) {
                        picked = appWide;
                        pickedSource = "global";
                    }
                }
                if (picked != null) {
                    try {
                        boolean ok = ((TtsEngine) engine).setVoiceByName(picked);
                        myLog("Applied initial TTS voice = " + picked + " (source=" + pickedSource + ", ok=" + ok
                                + ")");
                    } catch (Throwable ignored) {
                        myLogE("Failed to apply initial TTS voice = " + picked + " (source=" + pickedSource + ")");
                    }
                } else {
                    myLog("Initial TTS voice = system (no explicit voice)");
                }
            }
            engine.prepareAsync();

            // Optional: broadcast current title/pos (dur likely 0 → mini remains hidden).
            // This “primes” the UI with labels without forcing visibility.
            broadcastUiState("loadFile");

        } catch (Exception e) {
            myLogEE(e, "loadFile: setDataSource/prepareAsync failed");
            setUiPhase(Intents.PHASE_ERROR, "Failed to prepare TTS");
            loadFileKO(zf.getPath());
        }
    }

    /********************************************************************************
     *** PLAY-PAUSE
     ********************************************************************************
     */
    private boolean needsReloadForPlaylist() {

        PlayList pl = PlayList.getInstance();
        if (pl == null) {
            myLogW("needsReloadForPlaylist() - Playlist null");
            return true;
        }
        if (pl.isStream())
            return false;

        ZikFile zikFile = (pl != null) ? pl.getZikFile() : null;
        long wantId = (zikFile != null) ? zikFile.getId() : 0;

        // If we have no last snapshot or ids differ, we need to load.
        if (PlaybackUiBus.get().state().getValue() == null) {
            myLogW("needsReloadForPlaylist() - PlaybackUiBus null");
            return true;
        }
        if (PlaybackUiBus.get().state().getValue().trackId != wantId) {
            myLogW("needsReloadForPlaylist() - PlaybackUiBus, id diff");
            return true;
        } else {
            return false;
        }

    }

    public void playAudio() {
        myLog("playAudio() - start");

        broadcastUiState("playAudio");

        if (engine == null || needsReloadForPlaylist()) {
            loadAndPlay();
            return;
        }

        if (engine.isPlaying()) {
            myLogE("Engine already playing");
            return;
        }

        if (engine.isReady()) {
            startPlayWithEngine();
        } else {
            myLog("Engine not ready yet; will start on prepared");
            directPlay = true;
        }
    }

    public void pauseAudio(String why) {
        if (engine != null && engine.isPlaying()) {
            enginePause(why);
            // updateZikFileStateInDB(false);
            compactPlayTicks();
            focus.abandon();
            playTimer.stop();
            showForegroundNotification(false);
            broadcastUiState("pauseAudio");
        }
    }

    public void playPauseAudio(String why) {
        myLog("playPauseAudio() - [" + why + "]");
        if (isPlaying()) {
            pauseAudio(why);
        } else {
            playAudio();
            // Reset sleep timer on user resume
            if (playTimer != null && playTimer.isRunning()) {
                playTimer.resetLastUserAction();
            }
        }
    }

    public void forwardAudio() {
        forwardAudio(Option.get_ForwardSeconds() * 1000);
    }

    public void forwardAudio(int lag) {
        myLog("forwardAudio of " + lag);
        long temp = getPosition();
        if ((temp + lag) <= getDuration()) {
            setPosition(temp + lag);
        }
    }

    public void backwardAudio() {
        backwardAudio(Option.get_ForwardSeconds() * 1000);
    }

    public void backwardAudio(int lag) {
        myLog("backwardAudio() : " + lag);
        long temp = getPosition();
        if ((temp - lag) > 0) {
            setPosition(temp - lag);
        }
    }

    /********************************************************************************
     *** SPEED - POSITION
     ********************************************************************************
     */
    public long getPosition() {
        return engine != null ? engine.getCurrentPosition() : 0;
    }

    public long getDuration() {
        return engine != null ? engine.getDuration() : 0;
    }

    public boolean isPlaying() {
        return engine != null && engine.isPlaying();
    }

    public boolean isRunning() {
        return isRunning;
    }

    public int getAudioSessionId() {
        return engine != null ? engine.getAudioSessionId() : 0;
    }

    public void setPosition(long position) {
        setPosition(position, true); // Default: reset timer on user seek
    }

    public void setPosition(long position, boolean resetLastUserAction) {
        myLog("setPosition() : " + myDF.format(position) + " - " + Tonio.formatHhMmSs(position));
        if (engine != null) {
            progress.suspendOnce(300); // avoid races from progressUpdater => UI
            engine.seekTo(position);
            updatePlaybackStateForPosition();
            broadcastUiState("setPosition");
            // Reset sleep timer if this is a user-initiated seek
            if (resetLastUserAction && playTimer != null && playTimer.isRunning()) {
                playTimer.resetLastUserAction();
            }
        }
    }

    public void resetLastUserActionTimer() {
        if (playTimer != null && playTimer.isRunning()) {
            playTimer.resetLastUserAction();
        }
    }

    public void setSpeed(double speed) {
        try {
            this.speed = speed;
            if (engine != null)
                engine.setSpeed((float) speed);
            myLog("setSpeed() : " + speed);
        } catch (Exception e) {
            myLogEE(e, "MediaService Error setting Speed");
        }
        PlayList pl = PlayList.getInstance();
        if (pl != null && pl.isZikFile()) {
            ZikFile zf = pl.getZikFile();
            if (zf != null) {
                Folder f = pl.getFolder();
                if (f != null) {
                    f.speed = speed;
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        AppDatabase.getInstance(this).folderDao().update(f);
                    });
                }
            }
        }
    }

    public double getSpeed() {
        if (isZikFile()) {
            PlayList pl = PlayList.getInstance();
            if (pl != null) {
                Folder f = pl.getFolder();
                if (f != null) {
                    speed = f.speed;
                }
            }
        }
        if (speed == 0)
            speed = 1.0;
        return speed;
    }

    public void logPauseTime() {
        if (Pref.getPauseTime() != 0) {
            long pauseTime = (System.currentTimeMillis() - Pref.getPauseTime());
            myLogD("Paused since " + formatTime(pauseTime, true) + ".   MAX is "
                    + formatTime(TRIM_AFTER_PAUSE_MS, false, false));
        }
    }

    /********************************************************************************
     *** NOTIFICATIONS
     ********************************************************************************
     */

    private void enginePause(String why) {
        myLogD("mediaPlayerPause() - why = [" + why + "]");
        if (engine != null)
            engine.pause();
        // Track which track was paused for rewind-after-pause logic
        PlayList pl = PlayList.getInstance();
        if (pl != null && pl.isZikFile()) {
            ZikFile zf = pl.getZikFile();
            if (zf != null) {
                lastPausedTrackId = zf.getId();
            }
        }
        updateSessionState(false);
        Pref.setPaused(why, System.currentTimeMillis());
    }

    @SuppressWarnings("IfCanBeSwitch")
    private void playBeep(String beepType) {
        myLogI("playBeep - argument : " + beepType);
        try {
            if (beepType.equals("1beep")) {
                new ToneGenerator(AudioManager.STREAM_MUSIC, 100).startTone(ToneGenerator.TONE_CDMA_PIP, 150);
            } else if (beepType.equals("2beeps")) {
                new ToneGenerator(AudioManager.STREAM_MUSIC, 50).startTone(ToneGenerator.TONE_DTMF_0, 1000); // actually
                                                                                                             // a long
                                                                                                             // beep
            } else if (beepType.equals("3beeps")) {
                new ToneGenerator(AudioManager.STREAM_MUSIC, 100).startTone(ToneGenerator.TONE_CDMA_PIP, 500);
            } else {
                myLogE("playBeep - wrong argument : " + beepType);
            }
        } catch (Exception e) {
            myLogEE(e, "playBeep(" + beepType + ")");
        }
    }

    private void loadFileKO(String strFilePathError) {
        myLogE("loadFileKO");
        FirebaseAnalyticsHelper.tellAnalyticsLoadFileKO(strFilePathError, getPlayMode());
        ErrorLoadingFile = true;
        ErrorUi.showPlayAudioErrorMessage(this, null, strFilePathError);
        shutdown(false);
    }

    private void onEnginePrepared() {
        myLogD("onEnginePrepared()");

        setUiPhase(Intents.PHASE_ENGINE_PREPARED, null);

        if (!isStream()) {
            setPositionPlayStart();

            if (engine != null) {
                PlayList pl = PlayList.getInstance();
                if (pl != null) {
                    ZikFile zf = pl.getZikFile();
                    if (zf != null) {
                        String cover = (pl.getFolder() == null ? null : pl.getFolder().image);
                        media.setMetadata(zf.getDisplayName(), zf.getFolderName(), zf.getFolderName(),
                                engine.getDuration(), ImageHelper.decodeBitmapFromStringUri(this, cover, 512));
                    } else {
                        myLogE("onEnginePrepared zikFile null");
                    }
                }
            }
        }

        if (directPlay) {
            myLog("directPlay = " + directPlay);
            startPlayWithEngine();
        } else {
            myLogE("directPlay = " + directPlay);
            updateSessionState(false);
            showForegroundNotification(false);
        }
        broadcastUiState("onEnginePrepared");
    }

    private void onEngineCompletion() {
        myLogD("onEngineCompletion()");
        if (!ErrorLoadingFile) {
            do_1sec_stuff(true);
            PlayList pl = PlayList.getInstance();
            if (pl != null && pl.isLastTrack()) {
                if (Option.getBeepBookEnd())
                    playBeep("3beeps");
                alertPlaylistFinished();
                compactPlayTicks();
                shutdown(false);
            } else {
                nextTrack();
            }
        }
    }

    private void onEngineError(String msg, int what, int extra) {
        if (ErrorLoadingFile) {
            myLogW("onEngineError() - ErrorLoadingFile is already true, skipping alert to avoid UI duplication");
            return;
        }
        myLogEE(null, "Engine error: " + msg + " (" + what + "," + extra + ")");
        ErrorLoadingFile = true;
        playTimer.stop();

        if (msg != null && msg.startsWith("TTS")) {
            // --- Track how many TTS errors for the current engine generation ---
            if (engineGen != lastTtsErrorGen) {
                lastTtsErrorGen = engineGen;
                ttsErrorCountForGen = 0;
            }
            ttsErrorCountForGen++;

            boolean transientErr = TtsErrorUtils.isProbablyTransient(what);
            boolean configProblem = TtsErrorUtils.isProbablyConfigProblem(what);

            // 1) Always show some error state in UI
            setUiPhase(Intents.PHASE_ERROR,
                    "Speech engine error (" + what + ")");

            // 2) If it's probably transient (network, route, etc.) → do *not* touch voice
            if (transientErr) {
                myLogW("TTS error considered transient, keeping current voice/text.");
                // You could optionally retry once after a delay, but not auto.
                return;
            }

            // 3) If it's probably a config problem and we've seen it twice for this gen,
            // tell the user clearly that this voice looks incompatible.
            if (configProblem && ttsErrorCountForGen >= 2) {
                myToastEE(null,
                        getString(R.string.tts_voice_seems_incompatible)); // create a nice string
                myLogE("TTS voice seems incompatible for this text (code=" + what + ")");

                // Optional: mark this voice as "bad for this book" so you can avoid it next
                // time.
                // Example (adapt to your real API):
                try {
                    PlayList pl = PlayList.getInstance();
                    if (pl != null && pl.isZikFile()) {
                        Folder f = pl.getFolder();
                        if (f != null) {
                            String badVoice = getCurrentTtsVoiceName();
                            myLogW("Marking bad TTS voice for this book: " + badVoice);
                            // e.g. Pref.setBadTtsVoiceForBook(f.getId(), badVoice);
                        }
                    }
                } catch (Throwable ignored) {
                }

                // But we *do not* kill the service or auto-change the voice.
                // The user remains free to pick a new voice in the spinner.
            }

            // For a single config error, we just stay in ERROR phase and show the UI.
            return;
        }

        // Non-TTS = real fatal
        alertError("onEngineError", msg);
        shutdown(false);
    }

    private void onEngineFatal(String msg, int what, int extra) {
        myLogEE(null, "onEngineFatal: " + msg + " (" + what + "," + extra + ")");
        emitUiTick("onEngineFatal");
        ErrorLoadingFile = true;
        playTimer.stop();
        alertError("onEngineFatal", msg);
        shutdown(false);
        if ("podcast".equals(getPlayMode())) {
            if (msg.contains("ERROR_CODE_IO_BAD_HTTP_STATUS")) {
                myToastEE(null, getString(R.string.Podcast_source_error));
            } else {
                myToastE(getString(R.string.unexpected_error));
                myLogEE(null, "Engine FATAL: " + msg + " (" + what + "," + extra + ")");
            }
        }
        engine = null; // to force reload if user retry (added 2025-12-16 for radio)
    }

    public boolean isReadyToPlay() {
        return engine != null && engine.isReady();
    }

    public void pingUi() {
        broadcastUiState("pingUi");
    }

    public long getSleepLeftMs() {
        // myLog("sleep:" + (sleepTimer!=null ? "" + sleepTimer.getSleepLeftMs() :
        // "sleep timer null"));
        return (playTimer != null) ? playTimer.getSleepLeftMs() : 0;
    }

    public String getPlayMode() {
        if (engine instanceof TtsEngine) {
            return Var.PLAY_MODE_TTS;
        } else if (engine instanceof ExoRadioPlayerEngine) {
            return Var.PLAY_MODE_RADIO;
        } else if (engine instanceof ExoStreamPlayerEngine) {
            return Var.PLAY_MODE_PODCAST;
        } else if (engine instanceof MediaPlayerEngine) {
            return Var.PLAY_MODE_BOOK;
        } else {
            myLogE("getPlayMode() => no play mode => null");
            return null;
        }
    }

    public boolean isStream() {
        if (engine == null)
            return false;
        String playMode = getPlayMode();
        return (Var.PLAY_MODE_RADIO.equals(playMode) || Var.PLAY_MODE_PODCAST.equals(playMode));
    }

    public boolean isZikFile() {
        if (engine == null)
            return false;
        String playMode = getPlayMode();
        return (Var.PLAY_MODE_BOOK.equals(playMode) || Var.PLAY_MODE_TTS.equals(playMode));
    }

    public boolean isRadio() {
        return (engine != null) && Var.PLAY_MODE_RADIO.equals(getPlayMode());
    }

    public String getCurrentTtsVoiceName() {
        if ("tts".equals(getPlayMode())) {
            return ((TtsEngine) engine).getVoiceName();
        }
        return null;
    }

    public @Nullable String getTtsText() {
        if (engine instanceof TtsEngine) {
            return ((TtsEngine) engine).getText();
        }
        return null;
    }

    private void updatePlaybackStateForPosition() {
        if (engine == null)
            return;
        if (isStream())
            return;
        boolean playing = engine.isPlaying();
        int state = playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        media.updateState(state,
                engine.getCurrentPosition(),
                playing ? (float) getSpeed() : 0f,
                ACTIONS_FILE);
    }

    private void logFocusChange(int change) {
        String changeStr;
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
                changeStr = "AUDIOFOCUS_LOSS";
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                changeStr = "AUDIOFOCUS_LOSS_TRANSIENT";
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                changeStr = "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK";
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                changeStr = "AUDIOFOCUS_GAIN";
                break;
            default:
                changeStr = "UNKNOWN(" + change + ")";
                break;
        }
        myLogI("Audio Focus Change: " + changeStr + " (" + change + ")");
    }

    private void setPositionPlayStart() {
        try {
            PlayList pl = PlayList.getInstance();
            if (pl == null) {
                myLogEE(null, "setPositionPlayStart() - playlist null");
                return;
            }
            ZikFile zikFile = pl.getZikFile();
            if (zikFile == null) {
                myLogEE(null, "setPositionPlayStart() - zikFile null");
                return;
            }
            if (engine == null) {
                myLogEE(null, "setPositionPlayStart() - engine null");
                return;
            }

            int savedPos = (int) zikFile.getPosition();
            int dur = (int) zikFile.getDuration();
            if (dur > 0)
                savedPos = Math.max(0, Math.min(savedPos, dur));
            myLogD(zikFile.getName() + " - savedPosition = " + savedPos);
            if (savedPos >= (dur - Var.START_AT_ZERO_IF_TRACK_AT_END_BUFFER_DELAY_IN_MS)) {
                engine.seekTo(0);
                myLog("at end or near end, reset position to 0");
            } else {
                engine.seekTo(savedPos);
            }

            // max reach ?, reset to 0
            // if (zikFile.getPosition() >= zikFile.getDuration()) {
            myLogD("setPositionPlayStart() : " + (engine == null ? "engine is null"
                    : "pos = [" + Tonio.formatTime(engine.getCurrentPosition(), true) + "] - dur = ["
                            + Tonio.formatTime(engine.getDuration(), true) + "]"));
            if (engine != null && engine.getCurrentPosition() >= (engine.getDuration()
                    - Var.START_AT_ZERO_IF_TRACK_AT_END_BUFFER_DELAY_IN_MS)) { // because sometime, nearly at end but
                                                                               // not at end !
                myLogE("failsafe - at end or near end, reset position to 0");
                engine.seekTo(0);
            } else { // Rewind-after-pause
                // Only apply rewind-after-pause if it's the same track that was paused
                // This prevents incorrect rewind when switching to a different track
                boolean isSameTrack = (lastPausedTrackId == zikFile.getId());
                if (Option.getRewindAfterPause() && zikFile.lLastAccess != null && isSameTrack) {
                    long minutes = (System.currentTimeMillis() - zikFile.lLastAccess) / (60 * 1000);
                    int rewindMs = 0;
                    for (int[] rule : Var.REWIND_AFTER_PAUSE) {
                        if (minutes >= rule[0])
                            rewindMs = rule[1];
                        else
                            break;
                    }
                    if (rewindMs > 0) {
                        myLogD("Rewind " + (rewindMs / 1000) + "sec. after a [" + Tonio.formatTime(minutes * 60 * 1000)
                                + "] pause.");
                        backwardAudio(rewindMs);
                    }
                } else if (!isSameTrack && Option.getRewindAfterPause() && zikFile.lLastAccess != null) {
                    myLogD("Skipping rewind-after-pause: different track (paused=" + lastPausedTrackId + ", current="
                            + zikFile.getId() + ")");
                }
            }

            // Cut Intro (book option)
            int introCut = 0;
            if (pl != null && pl.getFolder() != null) {
                introCut = pl.getFolder().cutIntro * 1000;
            }
            if (introCut > 0) {
                long position = getPosition();
                myLog("position : [" + position + "]  introCut : [" + introCut + "]");
                if (position < introCut) {
                    engine.seekTo(introCut);
                    myLogI("=> Intro Cut");
                }
            }

        } catch (Exception e) {
            myLogEE(e, "setPositionPlayStart()");
        }
    }

    // Convenience for setting phase + optional message
    private void setUiPhase(@NonNull String phase, @Nullable String msg) {
        PlaybackUiBus.get().setLoadPhase(phase);
    }

    // Full file/audiobook actions (current behavior)
    private static final long ACTIONS_FILE = PlaybackStateCompat.ACTION_PLAY
            | PlaybackStateCompat.ACTION_PAUSE
            | PlaybackStateCompat.ACTION_STOP
            | PlaybackStateCompat.ACTION_REWIND
            | PlaybackStateCompat.ACTION_FAST_FORWARD
            | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            | PlaybackStateCompat.ACTION_PLAY_PAUSE
            | PlaybackStateCompat.ACTION_SEEK_TO;

    // Radio should only expose play/pause, nothing else
    private static final long ACTIONS_RADIO = PlaybackStateCompat.ACTION_PLAY
            | PlaybackStateCompat.ACTION_PAUSE;

    private long currentActions() {
        if (engine == null) {
            return ACTIONS_RADIO; // minimal actions if no engine
        } else {
            return isRadio() ? ACTIONS_RADIO : ACTIONS_FILE;
        }
    }

    private void playStream(@NonNull String playMode, @NonNull String url) {
        PlayList pl = PlayList.getInstance();
        String title = (pl != null) ? pl.getTitle() : null;
        myLogI("playStream " + playMode + " - title=[" + title + "] - url=[" + url + "]");

        if (playTimer != null) {
            // Always stop timer when starting a stream (Radio/Podcast) to pick up new sleep
            // settings
            playTimer.stop();
        }

        Uri streamUri = Uri.parse(url);
        if (streamUri == null) {
            myLogEE(null, "playStream: uri==null for url=" + url);
            return;
        }

        broadcastUiState("playStream " + playMode);

        // Swap engine to Exo for radio/podcast
        engineGen++;
        long gen = engineGen;
        PlayerEngine fresh = null;
        String playModeString;

        if ("podcast".equals(playMode)) {
            fresh = new ExoStreamPlayerEngine(getApplicationContext(), engineCb, gen);
            playModeString = getString(R.string.podcasts);
        } else if ("radio".equals(playMode)) {
            playModeString = getString(R.string.radio);
            fresh = new ExoRadioPlayerEngine(getApplicationContext(), engineCb, gen);
        } else {
            myToastEE(null, "unknown playMode " + playMode);
            return;
        }
        broadcastUiState("playStream " + playMode + " - engine started");

        setEngine(fresh);
        ErrorLoadingFile = false;

        PlaybackStateCompat s = new PlaybackStateCompat.Builder()
                .setActions(ACTIONS_FILE)
                .setState(PlaybackStateCompat.STATE_BUFFERING,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                        0f,
                        System.currentTimeMillis())
                .build();
        media.session().setPlaybackState(s);

        media.setMetadataRadio(
                /* title */ title,
                /* artist */ playModeString,
                /* album */ title,
                /* artBmp */ null);
        if (!showForegroundNotification(false)) {
            myLogEE(null, "playStream: failed to show foreground notification, aborting.");
            return;
        }

        try {
            engine.reset();
            engine.setDataSource(this, streamUri, title);
            engine.prepareAsync();

            broadcastUiState("playStream2");
            directPlay = true;

        } catch (Exception e) {
            myLogEE(e, "playStream setDataSource/prepareAsync failed, uri=" + streamUri);
            alertError(null, null);
        }
    }

    private void updateSessionState(boolean playing) {
        // Always run on main (MediaSession is main-thread oriented)
        main.post(() -> {
            MediaSessionCompat s = media.session();

            // Ensure we have *some* state before anyone reads it
            PlaybackStateCompat cur = s.getController().getPlaybackState();
            if (cur == null || cur.getState() == PlaybackStateCompat.STATE_NONE) {
                long actions = currentActions();
                long pos = (isStream()) ? PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN
                        : (engine != null ? engine.getCurrentPosition() : 0L);
                float sp = playing ? (float) getSpeed() : 0f;

                PlaybackStateCompat init = new PlaybackStateCompat.Builder()
                        .setActions(actions)
                        .setState(playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                                pos, sp, System.currentTimeMillis())
                        .build();
                s.setPlaybackState(init);
                cur = init; // so the debug log below never sees null
            }

            myLogD("updateSessionState(): active=[" + s.isActive() + "]"
                    + " prev=[" + PlaybackCommands.stateToString(cur.getState()) + "]"
                    + " actions=[" + PlaybackCommands.decodeActions(cur.getActions()) + "]");
            // Then set the *actual* state you want (radio vs file/tts)
            if (isRadio()) {
                long actions = playing ? PlaybackStateCompat.ACTION_PAUSE : PlaybackStateCompat.ACTION_PLAY;
                PlaybackStateCompat st = new PlaybackStateCompat.Builder()
                        .setActions(actions)
                        .setState(playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                                playing ? 1f : 0f,
                                System.currentTimeMillis())
                        .setBufferedPosition(PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN)
                        .build();
                s.setPlaybackState(st);
            } else {
                long pos = (engine != null) ? engine.getCurrentPosition() : 0L;
                float sp = playing ? (float) getSpeed() : 0f;
                media.updateState(playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                        pos, sp, currentActions()); // your helper calls setPlaybackState()
            }
        });
    }

    // TODO replace all this shot by MediaSession controller
    private void emitUiTick(String calledFrom) {
        broadcastUiState(calledFrom);
    }

    private void handleTtsSeekChars(int chars) {
        myLog("handleTtsSeekChars : [" + chars + "]");

        // Only meaningful in TTS mode
        if (!(engine instanceof TtsEngine)) {
            myLogD("handleTtsSeekChars ignored: not in TTS mode");
            return;
        }
        final TtsEngine tts = (TtsEngine) engine;

        // Clamp the requested char index against the actual text length
        String fullText = getTtsText(); // uses engine if TTS, else null
        if (fullText == null)
            fullText = "";
        int clamped = Math.max(0, Math.min(chars, fullText.length()));

        // Were we speaking?
        boolean wasPlaying = tts.isPlaying();

        // 1) Stop current utterance cleanly (no full reset)
        try {
            tts.pause(); // lighter than stop(); avoids resetting engine state
        } catch (Throwable ignored) {
        }

        // 2) Move the engine cursor
        try {
            tts.setStartOffsetChars(clamped);
        } catch (Throwable t) {
            myLogEE(t, "handleTtsSeekChars: setStartOffsetChars failed");
            // Best effort: keep UI consistent
            setUiPhase(Intents.PHASE_ERROR, "TTS seek failed");
            return;
        }

        // 3) Immediately snap UI highlight to the tapped word (one-shot)
        // (Your engine will continue sending NOTIFICATION_TTS_RANGE during playback)
        try {
            int[] w = TtsHelper.findWordBounds(fullText, clamped);
            Intent i = new Intent(Intents.NOTIFICATION_TTS_RANGE)
                    .putExtra(Intents.EXTRA_TTS_START, w[0])
                    .putExtra(Intents.EXTRA_TTS_END, w[1]);
            LocalBroadcastManager.getInstance(this).sendBroadcast(i);
        } catch (Throwable ignored) {
        }

        // 4) If we were speaking, resume from the new cursor;
        if (wasPlaying) {
            try {
                tts.start(); // continue speaking from new cursor
            } catch (Throwable t) {
                myLogEE(t, "handleTtsSeekChars: restart failed");
                setUiPhase(Intents.PHASE_ERROR, "TTS restart failed");
                return;
            }
        }

        // 5) Reflect new position in MediaSession/notification & UI snapshot
        updatePlaybackStateForPosition(); // keeps session in sync
        broadcastUiState("handleTtsSeekChars");
    }

    private boolean startForegroundWithBuildCheck(Notification n) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(ID_NOTIFICATION_PLAY_AUDIO_INT, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(ID_NOTIFICATION_PLAY_AUDIO_INT, n);
            }
            return true;
        } catch (Exception e) {
            myLogEE(e, "startForegroundWithBuildCheck failed - FGS restriction?");
            return false;
        }
    }

    private boolean loadAndPlayTrack(ZikFile zikFile) {
        if (zikFile == null) {
            myLogEE(null, "loadAndPlayTrack(ZikFile zikFile) => zikFile=null");
            return false;
        }
        return loadAndPlayTrack(zikFile, false, false);
    }

    private boolean loadAndPlayTrack(ZikFile zikFile, boolean isPodcast, boolean newestFirst) { // Always call me from a
                                                                                                // Background Thread !
        if (zikFile == null) {
            myLogEE(null, "loadAndPlayTrack(zikFile, isPodcast, newestFirst) => zikFile=null");
            return false;
        }
        // Resolve Uri
        Uri src = UriHelper.resolvePlayableUri(this, zikFile);
        String err;
        if (src == null) {
            err = "resolvePlayableUri failed for: " + zikFile.getPath();
            myLogEE(null, err);
            loadFileKO(err);
            return false;
        }

        Folder folder = AppDatabase.getDatabase(this).folderDao().getById(zikFile.getIdFolder());
        if (folder == null) {
            err = "Folder not found for [" + zikFile.getFolderName() + "]";
            myLogEE(null, err);
            loadFileKO(err);
            return false;
        }

        List<ZikFile> list;
        if (isPodcast) {
            list = PodcastHelper.getPodcastZikFiles(folder, this.getApplicationContext(), newestFirst);
        } else {
            list = AppDatabase.getDatabase(this).zikFileDao().getZikFiles(folder.getId());
        }
        if (list == null || list.isEmpty()) {
            err = "ZikFile list empty";
            myLogEE(null, err);
            loadFileKO(err);
            return false;
        }

        int index = 0;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == zikFile.getId()) {
                index = i;
                break;
            }
        }

        String playMode = ("text".equals(folder.playType) ? Var.PLAY_MODE_TTS : Var.PLAY_MODE_BOOK);

        PlayList.createFromZikFile(getApplicationContext(), playMode, folder, zikFile, list, index);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            directPlay = true;
            loadFile(playMode, src, zikFile);
        });
        return true;
    }

    private boolean loadAndPlayFolder(Folder folder) {

        ZikFile zikFile = AppDatabase.getDatabase(this).zikFileDao().getLastListenedZikFileOfFolder(folder.getId());
        String err;
        if (zikFile == null) {
            err = "loadAndPlayFolder: " + folder.getName() + " - could not find zikFile";
            myLogEE(null, err);
            loadFileKO(err);
            return false;
        }
        return loadAndPlayTrack(zikFile);
    }

    private void loadAndPlay() {
        myLogD("loadAndPlay()");
        PlayList.createFromStorage(this, true, pl -> {
            if (pl == null) {
                myLogEE(null, "error creating playlist from storage (pl == null)");
                // optionally stop service or update notification
                alertError("create playlist from storage", "could not recreate playlist");
                return;
            }

            // call on background thread = Important
            if (pl.isZikFile()) {
                ZikFile zikFile = pl.getZikFile();
                if (zikFile == null) {
                    myLogEE(null, "null zikfile in createFromStorage");
                    return;
                }
                AppDatabase.databaseReadExecutor.execute(() -> {
                    boolean ok = loadAndPlayTrack(zikFile);
                    if (!ok) {
                        myLogEE(null, "loadAndPlayFromStorage(): playback failed");
                    }
                });

            } else if (pl.isStream()) {
                String url = pl.getUrl();
                if (url == null || url.isEmpty()) {
                    myLogEE(null, "null url in createFromStorage");
                    return;
                }
                StartPlayHelper.playUndefinedStream(this.getApplicationContext(), url);
            } else {
                myLogEE(null, "error wrong playMode = " + pl.getPlayMode());
            }
        });
    }

    private void compactPlayTicks() {
        try {
            if (isZikFile()) {
                PlayList pl = PlayList.getInstance();
                if (pl != null && pl.getZikFile() != null) {
                    AppDatabase.databaseWriteExecutor.execute(
                            () -> PlayTickCompactor.compact(AppDatabase.getInstance(this), pl.getZikFile().getId()));
                }
            }
        } catch (Exception e) {
            myLogEE(e, "compactPlayTicks");
        }
    }

    private void do_1sec_stuff(boolean bFinished) {
        long timestamp = System.currentTimeMillis();
        String enginePlayMode = getPlayMode();

        PlayList pl = PlayList.getInstance();
        String playlistPlayMode;
        long trackId = 0;
        ZikFile zf = null;
        if (pl != null) {
            playlistPlayMode = pl.getPlayMode();
            if (!playlistPlayMode.equalsIgnoreCase(enginePlayMode)) {
                myLogEE(null, "engine and playlist playMode differs !");
            }
        }
        if (isZikFile()) {
            if (pl != null) {
                zf = pl.getZikFile();
            }
            if (zf == null) {
                myLogEE(null, "updateZikFileState : ZikFile = null");
                return;
            }
            long pos = 0;
            long dur = 0;
            try {
                pos = bFinished ? (int) zf.getDuration() : getPosition();
                dur = bFinished ? (int) zf.getDuration() : getDuration();
            } catch (Exception e) {
                myLogEE(e, "updateZikFileStateInDB - pos/dur");
                return;
            }

            // Cut End (book option): skip to next track a few seconds before the end
            if (!bFinished && dur > 0 && pl.getFolder() != null) {
                int cutEnd = pl.getFolder().cutEnd * 1000;
                if (cutEnd > 0 && pos >= (dur - cutEnd)) {
                    myLogI("=> End Cut: pos=" + pos + " dur=" + dur + " cutEnd=" + cutEnd
                            + " => triggering completion");
                    onEngineCompletion();
                    return;
                }
            }

            try {
                progress.update(zf, bFinished, pos, dur, enginePlayMode, timestamp);
            } catch (Exception e) {
                myLogEE(e, "updateZikFileStateInDB - progress");
            }
        } else {
            if (pl != null) {
                trackId = pl.getTrackId();
            } else {
                myLogEE(null, "updateStateInDB_notZikFile : no trackId");
                return;
            }
            try {
                progress.updateStream(enginePlayMode, trackId);
            } catch (Exception e) {
                myLogEE(e, "updateStateInDB_notZikFile - progress");
            }

        }
    }

}
