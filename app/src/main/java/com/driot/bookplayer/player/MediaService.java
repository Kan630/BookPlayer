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
import androidx.lifecycle.Observer;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.tts.AppTtsManager;
import com.driot.bookplayer.tts.TtsHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingMediaBrowserServiceCompat;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Pref;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static com.driot.bookplayer.utils.Tonio.formatTime;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 01/11/20
 */

public class MediaService extends LoggingMediaBrowserServiceCompat {

    // ---- Load phase tracking ----
    private @NonNull String currentUiPhase = Intents.PHASE_OFF;
    private @Nullable String currentUiPhaseMsg = null;

    private PlayList.MetaState lastPlayListMeta = new PlayList.MetaState(false, null, false);
    private final Observer<PlayList.MetaState> metaObs = meta -> {
        lastPlayListMeta = meta;          // cache latest meta
        //media.setImage(meta.)
        //broadcastUiState("PlayList.MetaState");       // rebuild + emit unified UI
    };

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
    private boolean hasBrowserClients() { return boundClientCount.get() > 0; }

    public static volatile boolean isRunning = false;
    enum ServiceState { RUNNING, SHUTTING_DOWN, STOPPED }
    private final AtomicReference<ServiceState> state = new AtomicReference<>(ServiceState.RUNNING);
    private boolean beginShutdown() {
        // Only the first caller wins; others will still be safe to call finalizeShutdown()
        return state.compareAndSet(ServiceState.RUNNING, ServiceState.SHUTTING_DOWN);
    }

    private static final String ID_NOTIFICATION_PLAY_AUDIO_CHANNEL = "audio_channel_of_bookplayer";
    private static final int ID_NOTIFICATION_PLAY_AUDIO_INT = 2;


    private boolean pausedByFocusLoss = false;
    private float preDuckVolume = 1f;

    //Play Timer (for Sleep)
    public static final int DELAY_CHECK_TIMER_SLEEP = 1000;
    private Handler sleepCheckHandler;
    private int customSleepTime = 0;

    //Pause Timer (to free memory)
    public static final int TRIM_MEMORY_THRESHOLD = 20;
    public static final int DELAY_CHECK_TIMER_PAUSE = 60 * 1000;
    public static final int TRIM_AFTER_PAUSE_MS = 7 * 24 * 60 * 60 * 1000; // so basically never... 7 days
    //public static final int DELAY_CHECK_TIMER_PAUSE = 2*1000;
    //public static final int TRIM_AFTER_PAUSE_MS = 5*1000; // so basically never... 7 days
    private Handler pauseCheckHandler;

    public static final int[][] REWIND_AFTER_PAUSE = {  // stopped listening since (in min)  ,  rewind delay (in ms)
            {2, 3000},
            {30, 5000},
            {60 * 12, 10000},
            {60 * 36, 15000},
            {60 * 24 * 3, 20000},
            {60 * 24 * 30, 30000},
    };

    private static final boolean LOG_TRACE_ALL = false;

    // --- RADIO MODE ---
    private boolean radioMode = false;
    @Nullable private String radioTitle = null;
    @Nullable private String radioImageUrl = null; // you can display it in notif if you already support URL bitmaps
    @Nullable private Uri    radioUri = null;
    private int lastCustomSleepMinutes = 0;
    private boolean podcastMode = false;
    private long podcastFeedId = -2;

    // cache for Android Auto Bitmaps
    public static final android.util.LruCache<String, android.graphics.Bitmap> artCache  = new android.util.LruCache<>(8);
    public static final android.util.LruCache<String, android.graphics.Bitmap> iconCache = new android.util.LruCache<>(24); //(Least Recently Used) cache with a maximum size of 24

    //private final IBinder binder = new BackgroundBinder();
    public static final String TRACKNUMBER = "tracknumber";
    public static final String FROM = "from";
    public static final String ERR_MSG = "err_msg";
    public static final String TIMER_VALUE = "TIMER_VALUE";
    public static final String READY_TO_PLAY = "NOTIFICATION_FILELOADED";
    public static final String NOTIFICATION_TRACKFINISHED = "NOTIFICATION_TRACKFINISHED";
    public static final String NOTIFICATION_FILENOTFOUND = "NOTIFICATION_FILENOTFOUND";
    public static final String NOTIFICATION_ERROR = "NOTIFICATION_ERROR";
    public static final String NOTIFICATION_PLAYLISTFINISHED = "NOTIFICATION_PLAYLISTFINISHED";
    public static final String NOTIFICATION_PLAYBACK_MAXTIMEREACH = "NOTIFICATION_PLAYBACK_MAXTIMEREACH";

    private com.driot.bookplayer.player.PlaybackNotificationManager notif;
    private com.driot.bookplayer.player.MediaSessionController media;
    private com.driot.bookplayer.player.AudioFocusHelper focus;
    private com.driot.bookplayer.player.SleepTimer sleepTimer;
    private com.driot.bookplayer.player.PauseTrimWatcher pauseWatcher;
    private com.driot.bookplayer.player.PlaybackProgressUpdater progress;

    private long engineGen = 0L;
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private final EngineListener engineCb = new EngineListener() {
        @Override
        public void onPrepared(long gen) {
            if (gen != engineGen) return;
            main.post(MediaService.this::onEnginePrepared);
        }

        @Override
        public void onCompletion(long gen) {
            if (gen != engineGen) return;
            main.post(MediaService.this::onEngineCompletion);
        }

        @Override
        public void onError(long gen, String msg, int what, int extra) {
            if (gen != engineGen) return;
            main.post(() -> onEngineError(msg, what, extra));
        }

        @Override
        public void onFatal(long gen, String msg, int what, int extra) {
            if (gen != engineGen) return;
            main.post(() -> onEngineFatal(msg, what, extra));
        }

        @Override
        public void onTtsRange(long gen, int s, int e) {
            if (gen != engineGen) return;
            //main.post(() -> {   //surtout pas, source du décallage entre le highlight et l'audio
            Intent i = new Intent(Intents.NOTIFICATION_TTS_RANGE)
                    .putExtra(Intents.EXTRA_TTS_START, s)
                    .putExtra(Intents.EXTRA_TTS_END, e);
            LocalBroadcastManager.getInstance(MediaService.this).sendBroadcast(i);
            //});
        }
    };


    private void sendReadyToPlay(String why) {
        boolean ok = (engine != null && engine.isReady() && !ErrorLoadingFile);
        myLogD("sendReadyToPlay? [" + why + "] ok=" + ok + " playMode=" + getPlayMode());
        if (!ok) return;

        Intent i = new Intent(READY_TO_PLAY);
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
    }

    private PlayerEngine engine;

    public boolean directPlay;

    private void broadcastUiCleared() {
        currentUiPhase = Intents.PHASE_OFF;
        currentUiPhaseMsg = null;
        PlaybackUiBus.get().clear();
    }


    private void broadcastUiState(String fromWhere) {
        final String loadPhase  = getLoadPhase();
        final boolean ready     = isReadyToPlay();
        final boolean playing   = isPlaying();
        final String playMode   = getPlayMode();

        PlaybackUiState s;

        Bundle extras = new Bundle();
        extras.putInt(Intents.EXTRA_CUSTOM_SLEEP_MINUTES, lastCustomSleepMinutes);
        extras.putDouble(Intents.EXTRA_SPEED, getSpeed());
        extras.putInt(Intents.EXTRA_AUDIO_SESSION_ID, getAudioSessionId());

        if (Objects.equals(playMode, "radio")) {
            String title = (radioTitle != null) ? radioTitle : getString(R.string.live_radio);
            String text = getString(R.string.live_radio);
            String cover = (radioImageUrl != null) ? radioImageUrl : "";


            s = new PlaybackUiState(
                    loadPhase, playing, ready, playMode,
                    0,0, getSleepLeftMs(),
                    title, text, cover,
                    /* trackId */ 0,
                    /* folderId */ 0,
                    /* podcastFeedId */ 0,
                    "MediaService.broadcastUiState() - radio " + fromWhere, -10, null
            );
        } else if (Objects.equals(playMode, "podcast")) {
            String title = (radioTitle != null) ? radioTitle : getString(R.string.live_podcast);
            String text  = getString(R.string.live_podcast);
            String cover = (radioImageUrl != null) ? radioImageUrl : "";
            long   pos   = (engine != null) ? engine.getCurrentPosition() : 0;
            long   dur   = (engine != null) ? engine.getDuration()        : 0;

            s = new PlaybackUiState(
                    loadPhase, playing, ready, playMode,
                    pos, dur, getSleepLeftMs(),
                    title, text, cover,
                    /* trackId */ 0,
                    /* folderId */ 0,
                    podcastFeedId,
                    "MediaService.broadcastUiState() - podcast " + fromWhere, -10, extras
            );
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
            int trackId = (z != null) ? z.getId() : 0;
            int folderId = (f != null) ? f.getId() : 0;

            extras.putString(Intents.EXTRA_TTS_VOICE_NAME, getCurrentTtsVoiceName());
            //extras.putInt(Intents.EXTRA_TTS_START_OFFSET, currentStartChars);

            s = new PlaybackUiState(loadPhase, playing, ready, playMode, pos, dur, getSleepLeftMs(), title, subTitle, cover,
                    trackId, folderId, 0, "MediaService.broadcastUiState() " + fromWhere, -10, extras);
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
            myLog("MediaSession.Callback.onPlay - from " + callerInfo);

            if ("AndroidAuto".equals(callerInfo)) {
                StartPlayHelper.carOnPlay(MediaService.this);
            } else {
                playPauseAudio();
            }
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            StartPlayHelper.carOnPlayFromMediaId(MediaService.this, mediaId, extras);
        }

        @Override
        public void onPause() {
            var info = MediaCallerHelper.getCallerInfo(MediaService.this);
            myLog("MediaSession.Callback.onPause - from " + MediaCallerHelper.describeCaller(MediaService.this, info));
            super.onPause();
            playPauseAudio();
        }

        @Override
        public void onStop() {
            var info = MediaCallerHelper.getCallerInfo(MediaService.this);
            myLog("MediaSession.Callback.onStop - from " + MediaCallerHelper.describeCaller(MediaService.this, info));
            super.onStop();
            /*  This was a large source of bugs... by killing mediaPlayer... after a pause... everything shit after...
            mediaPlayerStop();
            stopForeground(false);
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
            //super.onFastForward();
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
            //super.onRewind();
        }

        @Override
        public void onSeekTo(long pos) {
            myLog("MediaSessionCompat.Callback - onSeekTo()");
            setPosition((int) pos);
            //super.onSeekTo(pos);
        }

        @Override
        public void onSkipToNext() {
            forwardAudio();
            myLog("MediaSessionCompat.Callback - onSkipToNext()");
            //super.onSkipToNext();
        }

        @Override
        public void onSkipToPrevious() {
            backwardAudio();
            myLog("MediaSessionCompat.Callback - onSkipToPrevious()");
            //super.onSkipToPrevious();
        }

        @Override
        public void onCustomAction(@NonNull String action, Bundle extras) {
            myLog("MediaSessionCompat.Callback - onCustomAction : " + action);
            switch (action) {
                case Intents.CMD_SET_SPEED: {
                    double s = extras != null ? extras.getDouble(Intents.EXTRA_SPEED, 1.0) : 1.0;
                    setSpeed(s);                    // your engine.setSpeed(...)
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
                                    .putExtra(Intents.EXTRA_CALLER, "MediaService.onCustomAction")
                    );
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
                                    .putExtra(Intents.EXTRA_CALLER, "MediaService.onCustomAction")
                    );
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
                                    .putExtra(Intents.EXTRA_CALLER, "MediaService.onCustomAction")
                    );
                    break;
                }
                case Intents.CMD_TTS_GET_TEXT: {
                    // Optional: support queries with a ResultReceiver
                    android.os.ResultReceiver rr = extras != null
                            ? extras.getParcelable(Intents.EXTRA_RESULT_RECEIVER) : null;

                    // You can ask MediaService to produce the value and reply into rr
                    ContextCompat.startForegroundService(
                            MediaService.this,
                            new Intent(MediaService.this, MediaService.class)
                                    .setAction(Intents.CMD_TTS_GET_TEXT)
                                    .putExtra(Intents.EXTRA_RESULT_RECEIVER, rr)
                                    .putExtra(Intents.EXTRA_FOREGROUND, true)
                                    .putExtra(Intents.EXTRA_CALLER, "MediaService.onCustomAction")
                    );
                    break;
                }

                default:
                    super.onCustomAction(action, extras);
            }
        }

    };


    private double speed = 1.0;
    private boolean ErrorLoadingFile = false;
    DecimalFormat myDF = new DecimalFormat("#,###.");

    /********************************************************************************
     *       NATIVE METHODS
     *  Because service always runs in the same process as clients, no need IPC.
     *
     */
// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onCreate() {
        isRunning = true;
        super.onCreate();

        PlayList.getMetaLive().observeForever(metaObs);

        // Media session (wrapped)
        media = new MediaSessionController(this, callback);
        MediaSessionCompat session = media.session();
        setSessionToken(session.getSessionToken());
        session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        myLogI("SERVICE session getCallingPackage=" + session.getCallingPackage()
                + " token=" + session.getSessionToken()
                + " token@=" + System.identityHashCode(session.getSessionToken()));
/*
        media.session().setPlaybackState(
                new PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_PAUSED, 0L, 0f, System.currentTimeMillis())
                        .setActions(currentActions())
                        .build()
        );

 */

        updateSessionState(false);

        PendingIntent contentPi = PendingIntent.getActivity(
                this, 0,
                new Intent(this, PlayActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        media.setSessionActivity(contentPi);

        // Notification helper
        notif = new com.driot.bookplayer.player.PlaybackNotificationManager(
                this, ID_NOTIFICATION_PLAY_AUDIO_CHANNEL, R.mipmap.ic_launcher);
        notif.ensureChannel("Music Playback", "Bookplayer Music Playback Controls");

// Sleep timer (ticks every second)
        sleepCheckHandler = new Handler();
        sleepTimer = new com.driot.bookplayer.player.SleepTimer(
                sleepCheckHandler,
                DELAY_CHECK_TIMER_SLEEP,
                new SleepTimer.Listener() {
                    @Override
                    public void onTick(int elapsedSeconds) {
                        Pref.addToTotalMsPlayed(DELAY_CHECK_TIMER_SLEEP);
                        updateZikFileStateInDB(false);
                        emitUiTick("MediaService.SleepTimer.onTick");
                    }

                    @Override
                    public void onEveryMinute(@NonNull String elapsedCategory) {
                        // Decide dynamically based on current mode
                        if (radioMode) {
                            FirebaseAnalyticsHelper.tellRadioFor1min(elapsedCategory);
                        } else if (podcastMode) {
                            FirebaseAnalyticsHelper.tellPodcastFor1min(elapsedCategory);
                        } else {
                            FirebaseAnalyticsHelper.tellPlayFor1min(elapsedCategory);
                        }
                    }

                    @Override
                    public void onReachedMax() {
                        if (Option.getBeepAutoStop()) playBeep("2beeps");
                        LocalBroadcastManager.getInstance(MediaService.this)
                                .sendBroadcast(new Intent(NOTIFICATION_PLAYBACK_MAXTIMEREACH));
                        pauseAudio();
                    }
                }
        );

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
                        if (PlayList.getInstance() == null) newMsg += " [null playlist]";
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
                            if (engine != null) engine.setVolume(preDuckVolume);
                        } catch (Throwable ignored) {
                        }
                        if (pausedByFocusLoss) {
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
                        //TODO cree un timer sur le focus lost, et voir  dans les 3sec si c'etait pas AA qui se connectait, si c'est le cas, remettre le play?
                        boolean keepOnPhone = Option.getAutomotiveKeepPhonePlaybackOnCarConnect(); // new toggle (default false)
                        boolean inGrace     = CarSignals.withinCarConnectGrace(2500);
                        myLog("keepOnPhone = " + keepOnPhone);
                        myLog("inGrace = " + inGrace);

                        if (keepOnPhone && inGrace && (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || change == AudioManager.AUDIOFOCUS_LOSS)) {
                            myLog("AUDIOFOCUS_LOSS_TRANSIENT");
                            // treat transient like duck during grace window (don’t pause)
                            startDuck();
                            return;
                        }
                         */

                        // normal behavior: pause if we were playing
                        pausedByFocusLoss = isPlaying();
                        pauseAudio();
                    }

                    @Override
                    public void onDuck(boolean ducking) {
                        if (ducking) startDuck();
                        else stopDuck();
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
                            if (engine != null) engine.setVolume(preDuckVolume);
                        } catch (Throwable ignored) {
                        }
                    }
                }
        );

        // Progress updater (DB)
        progress = new PlaybackProgressUpdater(this);

        myLogD("onCreate() - END");
    }
// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void showForegroundNotification(boolean playing) {

        if (radioMode) {
            // 1) Limit session capabilities
            updateSessionState(playing);

            // 2) (Async) fetch cover, then set metadata & update notif
            //    If you already have a cached Bitmap, set it now and skip Glide.
            Bitmap currentArt = null; // your cache if any
            media.setMetadataRadio(radioTitle != null ? radioTitle : getString(R.string.live_radio), "", "", currentArt);


            Notification n = notif.build(
                    media.session(),
                    playing,
                    radioTitle != null ? radioTitle : getString(R.string.live_radio),
                    getString(R.string.live_radio),
                    new PlaybackNotificationManager.ActionProvider() {
                        @Override public PendingIntent rewind()      { return null; } // no-op
                        @Override public PendingIntent fastForward() { return null; } // no-op
                        @Override public PendingIntent play() {
                            return MediaButtonReceiver.buildMediaButtonPendingIntent(
                                    MediaService.this, PlaybackStateCompat.ACTION_PLAY);
                        }
                        @Override public PendingIntent pause() {
                            return MediaButtonReceiver.buildMediaButtonPendingIntent(
                                    MediaService.this, PlaybackStateCompat.ACTION_PAUSE);
                        }
                        @Override public PendingIntent content() {
                            final int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
                            return PendingIntent.getActivity(
                                    MediaService.this,
                                    0,
                                    new Intent(MediaService.this, com.driot.bookplayer.activities.MainActivity.class)
                                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                                    flags
                            );
                        }
                    }
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(ID_NOTIFICATION_PLAY_AUDIO_INT, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(ID_NOTIFICATION_PLAY_AUDIO_INT, n);
            }

            // 3) If you only have a favicon URL, load it and refresh:
            if (currentArt == null && radioImageUrl != null && !radioImageUrl.isEmpty()) {
                com.bumptech.glide.Glide.with(getApplicationContext())
                        .asBitmap()
                        .load(radioImageUrl)
                        .into(new com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                            @Override public void onResourceReady(Bitmap bmp,
                                                                  com.bumptech.glide.request.transition.Transition<? super Bitmap> t) {
                                media.setMetadataRadio(radioTitle != null ? radioTitle : getString(R.string.live_radio), "", "", bmp);
                                // rebuild/update the notification so largeIcon shows
                                Notification updated = notif.build(
                                        media.session(), playing,
                                        radioTitle != null ? radioTitle : getString(R.string.live_radio),
                                        getString(R.string.live_radio),
                                        /* same ActionProvider */ new PlaybackNotificationManager.ActionProvider() {
                                            @Override public PendingIntent rewind()      { return null; }
                                            @Override public PendingIntent fastForward() { return null; }
                                            @Override public PendingIntent play()  { return MediaButtonReceiver.buildMediaButtonPendingIntent(MediaService.this, PlaybackStateCompat.ACTION_PLAY); }
                                            @Override public PendingIntent pause() { return MediaButtonReceiver.buildMediaButtonPendingIntent(MediaService.this, PlaybackStateCompat.ACTION_PAUSE); }
                                            @Override public PendingIntent content() {
                                                final int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
                                                return PendingIntent.getActivity(
                                                        MediaService.this,
                                                        0,
                                                        new Intent(MediaService.this, com.driot.bookplayer.activities.MainActivity.class)
                                                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                                                        flags
                                                );
                                            }
                                        }
                                );
                                // For foreground services, call startForeground again or notify:
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    startForeground(ID_NOTIFICATION_PLAY_AUDIO_INT, updated, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                                } else {
                                    startForeground(ID_NOTIFICATION_PLAY_AUDIO_INT, updated);
                                }
                            }
                            @Override public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {}
                        });
            }
            return;        }


        ZikFile zf = getCurrentZikFile();
        CharSequence title = zf == null ? "---" : zf.getFolderName();
        CharSequence text = zf == null ? "---" : zf.getDisplayName();

        Notification n = notif.build(
                media.session(),
                playing,
                title,
                text,
                new com.driot.bookplayer.player.PlaybackNotificationManager.ActionProvider() {
                    @NonNull @Override public PendingIntent rewind() {
                        return MediaButtonReceiver.buildMediaButtonPendingIntent(MediaService.this, PlaybackStateCompat.ACTION_REWIND);
                    }

                    @NonNull @Override public PendingIntent play() {
                        return MediaButtonReceiver.buildMediaButtonPendingIntent(MediaService.this, PlaybackStateCompat.ACTION_PLAY);
                    }

                    @NonNull @Override public PendingIntent pause() {
                        return MediaButtonReceiver.buildMediaButtonPendingIntent(MediaService.this, PlaybackStateCompat.ACTION_PAUSE);
                    }

                    @NonNull @Override public PendingIntent fastForward() {
                        return MediaButtonReceiver.buildMediaButtonPendingIntent(MediaService.this, PlaybackStateCompat.ACTION_FAST_FORWARD);
                    }

                    @NonNull @Override public PendingIntent content() {
                        final int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

                        androidx.core.app.TaskStackBuilder tsb = androidx.core.app.TaskStackBuilder.create(MediaService.this);
                        // 1) Always start at Main
                        tsb.addNextIntent(new Intent(MediaService.this, com.driot.bookplayer.activities.MainActivity.class));

                        // 2) If multiple tracks, insert the track list screen before PlayActivity
                        PlayList pl = PlayList.getInstance();
                        ZikFile z = (pl != null) ? pl.getZikFile() : null;
                        int folderId = (z != null) ? z.getIdFolder() : -1;

                        if (folderId > 0 && pl != null && pl.getSize() > 1) {
                            Intent trackList = new Intent(MediaService.this, com.driot.bookplayer.activities.ZikFileActivity.class)
                                    .putExtra(Intents.EXTRA_FOLDER_ID, folderId);
                            tsb.addNextIntent(trackList);
                        }

                        // 3) Finally PlayActivity (singleTop/clearTop like you already do)
                        tsb.addNextIntent(new Intent(MediaService.this, PlayActivity.class)
                                .putExtra(Intents.EXTRA_AUTOPLAY, false));

                        return tsb.getPendingIntent(0, flags);
                    }

                });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ID_NOTIFICATION_PLAY_AUDIO_INT, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(ID_NOTIFICATION_PLAY_AUDIO_INT, n);
        }
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

        if (engine instanceof TtsEngine) setUiPhase(Intents.PHASE_SPEAKING, null);

        engine.start();
        Pref.setPauseTime(0);

        updateSessionState(true);
        engine.setSpeed((float) getSpeed());
        if (!media.session().isActive()) media.setActive(true);

        if (!sleepTimer.isRunning()) {
            int minutes = (customSleepTime == 0) ? Option.getTimeBeforeSleep() : customSleepTime;
            sleepTimer.start(minutes);
        }

        showForegroundNotification(true);
        broadcastUiState("startPlayWithEngine");
    }

    private void nextTrack() {
        myLog("Next track");

        if (engine instanceof TtsEngine) setUiPhase(Intents.PHASE_LOADING_TEXT, "Loading text…");

        if (engine instanceof ExoStreamPlayerEngine) {
            if (Option.getBeepBookEnd()) playBeep("3beeps");
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
                    ((TtsEngine) engine).release();
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

        if (Option.getBeepChapter()) playBeep("1beep");

        AppDatabase.databaseWriteExecutor.execute(() -> {

            if (Option.getStartAtZeroNextTrack()) {
                nextZikFile.setPosition(0L);
                AppDatabase.getDatabase(this).zikFileDao().update(nextZikFile);
            }

            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                directPlay = true;
                loadFile();
            });
            alertNewTrack();

        });
    }

    private void alertNewTrack() {
        myLog("alertNewTrack()");
        ZikFile z = getCurrentZikFile();
        PlayList pl = PlayList.getInstance();
        String cover = null;
        if (pl!=null && pl.getFolder()!=null) cover = pl.getFolder().image;
        if (z != null) {
            media.updateState(PlaybackStateCompat.STATE_BUFFERING, 0, 0f, ACTIONS_FILE);
            media.setMetadata(z.getDisplayName(), z.getFolderName(), z.getFolderName(), 0L, ImageHelper.decodeBitmapFromStringUri(this, cover, 512));
            showForegroundNotification(isPlaying());
        }
    }

    private void alertError(String from, String errMsg) {
        myLogE("sendBroadcast alertError - from [" + from + "] - errMsg=[" + errMsg + "]" );
        Intent i = new Intent(NOTIFICATION_ERROR)
                .putExtra(FROM, from)
                .putExtra(ERR_MSG, errMsg);
        PlayList pl = PlayList.getInstance();
        if (pl!=null && pl.getNumZikFile()>0 && !(radioMode || podcastMode)) {
            i.putExtra(TRACKNUMBER, pl.getNumZikFile());
        }
        LocalBroadcastManager.getInstance(MediaService.this).sendBroadcast(i);
    }

    private void alertTrackFinished() {
        myLog("--------------------------------------------------------------------------------- sendBroadcast alertTrackFinished --------------------------------------------------------------------------------");
        LocalBroadcastManager.getInstance(MediaService.this).sendBroadcast(new Intent(NOTIFICATION_TRACKFINISHED));
    }

    private void alertPlaylistFinished() {
        myLog("sendBroadcast alertPlaylistFinished");
        LocalBroadcastManager.getInstance(MediaService.this).sendBroadcast(new Intent(NOTIFICATION_PLAYLISTFINISHED));
    }

    //----------------------------------------------------------------------------------------------------------------------------------------------------------
    //----------------------------------------------------------------------------------------------------------------------------------------------------------
    //------    START COMMAND
    //----------------------------------------------------------------------------------------------------------------------------------------------------------
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
        } else {            // happens when Android restarts your sticky service after it was killed, no 5-second foreground requirement in this case because the system didn’t just call startForegroundService(...) on your behalf;
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
            myLogW("MediaService start with no intent.action");
            return START_STICKY;
        }

        switch (action) {
            // -------- High-level “load something and (likely) play” intents --------
            case Intents.ACTION_PLAY_FROM_TRACK: {
                // Enter foreground *before* async work to satisfy the 5s rule
                goForegroundPreparing("Preparing…", "Loading selected track");
                radioMode = false;
                podcastMode = false;

                final int trackId = intent.getIntExtra(Intents.EXTRA_TRACK_ID, -1);
                final boolean isPodcast = intent.getBooleanExtra(Intents.EXTRA_IS_PODCAST, false);
                final boolean newestFirst = intent.getBooleanExtra(Intents.EXTRA_TRACK_ORDER_NEWEST_FIRST, true);
                myLog("trackId : " + trackId + " - isPodcast : " + isPodcast + " - newestFirst : " + newestFirst);
                if (trackId > 0) {
                    AppDatabase.databaseReadExecutor.execute(() -> {
                        ZikFile clicked = AppDatabase.getDatabase(this).zikFileDao().getById(trackId);
                        if (clicked == null) return;

                        int folderId = clicked.getIdFolder();
                        Folder folder = AppDatabase.getDatabase(this).folderDao().getById(folderId);
                        List<ZikFile> list;
                        if (isPodcast) {
                            if (newestFirst) {
                                list = AppDatabase.getDatabase(this).zikFileDao().getPodcastZikFilesDesc(folderId);
                            } else {
                                list = AppDatabase.getDatabase(this).zikFileDao().getPodcastZikFilesAsc(folderId);
                            }
                        } else {
                            list = AppDatabase.getDatabase(this).zikFileDao().getZikFiles(folderId);
                        }
                        if (list == null || list.isEmpty()) {
                            myLogE("ZikFile list empty");
                            return;
                        }

                        int index = 0;
                        for (int i = 0; i < list.size(); i++) {
                            if (list.get(i).getId() == trackId) {
                                index = i;
                                break;
                            }
                        }

                        PlayList.create(getApplicationContext(), folder, list, index);
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            directPlay = true;
                            loadFile();
                        });
                    });
                } else {
                    myLogE("track id = " + trackId);
                }
                return START_STICKY;
            }

            case Intents.ACTION_PLAY_FROM_FOLDER: {
                goForegroundPreparing("Preparing…", "Loading folder");
                radioMode = false;
                podcastMode = false;

                final int folderId = intent.getIntExtra(Intents.EXTRA_FOLDER_ID, -1);
                final int index = Math.max(0, intent.getIntExtra(Intents.EXTRA_INDEX, 0));
                if (folderId > 0) {
                    AppDatabase.databaseReadExecutor.execute(() -> {
                        Folder folder = AppDatabase.getDatabase(this).folderDao().getById(folderId);
                        List<ZikFile> list = AppDatabase.getDatabase(this).zikFileDao().getZikFiles(folderId);
                        if (list == null || list.isEmpty()) return;
                        PlayList.create(getApplicationContext(), folder, list, Math.min(index, list.size() - 1));
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            directPlay = true;
                            loadFile();
                        });
                    });
                }
                return START_STICKY;
            }

            case "CMD_STOP": { //keep string CMD_STOP here as can be called by others than app
                myLog("CMD_STOP");
                shutdown(false);
                return START_NOT_STICKY; //let's try to avoid crashes
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
                    showForegroundNotification(false);
                }
                pauseAudio();
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
                KeyEvent ke = intent.hasExtra(Intent.EXTRA_KEY_EVENT) ? intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) : null;
                String keyEventString = (ke!=null) ? ke.getCharacters() : "no key event";
                myLog("onStartCommand() - Intent.ACTION_MEDIA_BUTTON : " + keyEventString);
                FirebaseAnalyticsHelper.setCustomKeyCrashlytics("MediaService.onStartCommand() - ACTION_MEDIA_BUTTON", keyEventString);

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

            case Intents.ACTION_PLAY_RADIO: {
                // Enter foreground ASAP (5s rule)
                goForegroundPreparing(getString(R.string.live_radio), null);

                PlayList pl = PlayList.getInstance();
                if (pl!=null) pl.clear();

                final String url   = intent.getStringExtra(Intents.EXTRA_STREAM_URL);
                final String title = intent.getStringExtra(Intents.EXTRA_TITLE);
                final String img   = intent.getStringExtra(Intents.EXTRA_IMAGE_URL);

                if (url == null || url.isEmpty()) {
                    myLogE("ACTION_PLAY_RADIO without url");
                    return START_NOT_STICKY;
                }

                if (engine != null) engine.stop();
                playRadioStream(url, title != null ? title : getString(R.string.live_radio), img);
                return START_STICKY;
            }

            case Intents.ACTION_PLAY_PODCAST: {
                // Enter foreground ASAP (5s rule)
                goForegroundPreparing(getString(R.string.podcasts), null);

                PlayList pl = PlayList.getInstance();
                if (pl!=null) pl.clear();

                final String url   = intent.getStringExtra(Intents.EXTRA_STREAM_URL);
                final String title = intent.getStringExtra(Intents.EXTRA_TITLE);
                final String img   = intent.getStringExtra(Intents.EXTRA_IMAGE_URL);
                final long podcastFeedID = intent.getLongExtra(Intents.EXTRA_PODCAST_FEED_ID, -1);

                if (url == null || url.isEmpty()) {
                    myLogE("ACTION_PLAY_PODCAST without url");
                    return START_NOT_STICKY;
                }

                if (engine != null) engine.stop();
                playPodcastStream(podcastFeedID, url, title != null ? title : getString(R.string.podcasts), img);
                return START_STICKY;
            }


            case Intents.CMD_TTS_SET_VOICE: {
                final String voiceName = intent.getStringExtra(Intents.EXTRA_TTS_VOICE_NAME);
                if (voiceName == null) {
                    myLogEE(null, "CMD_TTS_SET_VOICE => EXTRA_TTS_VOICE_NAME is null");
                    return START_STICKY;
                }
                if (engine instanceof TtsEngine) {
                    try {
                        PlaybackUiBus.get().setLoadPhase(Intents.PHASE_WARMING_UP); //, getString(R.string.tts_phase_warming_up)
                        boolean ok = ((TtsEngine) engine).setVoiceByName(voiceName);
                        myLog("Voice change success = " + ok);
                        if (ok) {
                            PlaybackUiBus.get().setLoadPhase(Intents.PHASE_READY);
                        } else {
                            PlaybackUiBus.get().setLoadPhase(Intents.PHASE_ERROR); //getString(R.string.tts_phase_error)
                        }

                    } catch (Throwable ignored) {
                    }
                }
                return START_STICKY;
            }

            case Intents.CMD_TTS_SET_START: {
                int ch = intent.getIntExtra(Intents.EXTRA_TTS_START_OFFSET, -1);
                if (ch >= 0) handleTtsSeekChars(ch);
                return START_STICKY;
            }

            case Intents.CMD_TTS_GET_TEXT: {
                android.os.ResultReceiver rr =
                        intent.getParcelableExtra(Intents.EXTRA_RESULT_RECEIVER);
                String txt = getTtsText(); // returns engine text if TTS, else null
                Bundle out = new Bundle();
                out.putString(Intents.EXTRA_TTS_TEXT, (txt != null) ? txt : "");
                if (rr != null) rr.send(0, out);
                return START_STICKY;
            }

            case Intents.CMD_UPDATE_SLEEP: {
                int newSleepValueInMin = intent.getIntExtra(Intents.EXTRA_CUSTOM_SLEEP_MINUTES, -1);
                if (newSleepValueInMin>0) sleepTimer.reload(newSleepValueInMin);
                return START_STICKY;
            }

            default:
                // Unknown action — keep service alive and ensure we have a notif if needed
                myLogEE(null, "onStartCommand() - unknown action : [" + action + "]");
                showForegroundNotification(isPlaying());
                return START_STICKY;
        }
    }
    //----------------------------------------------------------------------------------------------------------------------------------------------------------
    //----------------------------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Minimal foreground entry used before async prep to satisfy the 5s requirement.
     */
    private void goForegroundPreparing(@Nullable CharSequence title, @Nullable CharSequence text) {
        try {
            CharSequence t = (title != null) ? title : "Preparing…";
            CharSequence s = (text != null) ? text : "Please wait";

            PlaybackNotificationManager.ActionProvider minimal =
                    new PlaybackNotificationManager.ActionProvider() {
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

                        @Override
                        public PendingIntent content() {
                            // Tap → open PlayActivity (or your main)
                            return PendingIntent.getActivity(
                                    MediaService.this, 0,
                                    new Intent(MediaService.this, PlayActivity.class)
                                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                            );
                        }
                    };

            Notification n = notif.buildPreparing(t, s, /* content PI */ minimal.content());
            /*
            Notification n = notif.build(
                    media.session(),
                    false,
                    t,
                    s,
                    minimal
            );
*/
            // to call before 5sec :
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(ID_NOTIFICATION_PLAY_AUDIO_INT, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(ID_NOTIFICATION_PLAY_AUDIO_INT, n);
            }

            media.setActive(true);

            final long actions = radioMode
                    ? ACTIONS_RADIO
                    : ACTIONS_FILE;

            PlaybackStateCompat placeholder = new PlaybackStateCompat.Builder()
                    .setActions(actions)
                    .setState(
                            PlaybackStateCompat.STATE_BUFFERING,
                            0L,
                            0f,
                            System.currentTimeMillis()
                    )
                    .build();
            media.session().setPlaybackState(placeholder);

        } catch (Throwable e) {
            myLogEE(e, "goForegroundPreparing()");
        }
    }


    /*  onTaskRemoved
        It fires when
        The user opens the Recents screen and swipes your app’s card away.
        The user taps “Clear all” in Recents (which removes your task).
        You call finishAndRemoveTask() on an Activity (explicitly removes the task).

        It does not fire when
        The user presses Back to exit an Activity (unless you used finishAndRemoveTask()).
        The user presses Home, switches apps, or turns the screen off.
        The process is killed by the system’s low-memory killer or the user hits Force stop in Settings.
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        String logMsg = "onTaskRemoved";
        if (rootIntent.getAction() != null) logMsg = logMsg + " from " + rootIntent.getAction();
        myLogW(logMsg);
        shutdown(false);
        super.onTaskRemoved(rootIntent);
    }

    private void shutdown(boolean fromDestroy) {
        final boolean first = beginShutdown();
        myLogI("shutdown(" + fromDestroy + ") first=" + first + " state=" + state.get());

        // Quiesce repeating sources no matter who called us
        stopAsyncWork();

        broadcastUiCleared();

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
        } catch (Throwable ignored) {}

        // Always kill the audio path (idempotent)
        hardStopAudio("shutdown");

        // Drop focus after we’re silent
        try { if (focus != null) focus.abandon(); } catch (Throwable ignored) {}

        // Remove UI surface *after* silence
        try { stopForeground(true); } catch (Throwable ignored) {}
        try { if (notif != null) notif.cancel(ID_NOTIFICATION_PLAY_AUDIO_INT); } catch (Throwable ignored) {}

        // Release session last
        //try { if (media != null) media.release(); } catch (Throwable ignored) {} //no or I will get infinite onGetRoot !!

        // Clear state (safe to run twice)
        try { PlayList pl = PlayList.getInstance(); if (pl != null) pl.clear(); } catch (Throwable ignored) {}
        radioMode = false; podcastMode = false;
        radioTitle = null; radioImageUrl = null; radioUri = null;
        isRunning = false;

        state.set(ServiceState.STOPPED);
        if (!fromDestroy) requestGracefulStopIfNoClients(); // safe if already stopping
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
        try { if (sleepTimer != null) sleepTimer.stop(); } catch (Throwable ignored) {}
        try { if (pauseWatcher != null) pauseWatcher.stop(); } catch (Throwable ignored) {}
        try { PlayList.getMetaLive().removeObserver(metaObs); } catch (Throwable ignored) {}
        try { main.removeCallbacksAndMessages(null); } catch (Throwable ignored) {}
    }
    private void hardStopAudio(@NonNull String why) {
        myLogI("hardStopAudio: " + why);
        PlayerEngine e = this.engine;
        this.engine = null; // prevent any more calls into it
        if (e != null) {
            try { e.pause(); }   catch (Throwable ignored) {}
            try { e.stop(); }    catch (Throwable ignored) {}
            try { e.release(); } catch (Throwable t) { myLogEE(t, "engine.release failed"); }
            try { e.reset(); }   catch (Throwable ignored) {} // ok to be a no-op after release
        }
        try { if (sleepTimer != null) sleepTimer.stop(); } catch (Throwable ignored) {}
        try { if (pauseWatcher != null) pauseWatcher.stop(); } catch (Throwable ignored) {}
        try { if (focus != null) focus.abandon(); } catch (Throwable ignored) {}
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
        myLog("from pkg=" + clientPackageName + " uid=" + clientUid  + "\n" + " hints=" + rootHints.toString().replace(",","\n"));

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


    @Override public IBinder onBind(Intent intent) {
        int c = boundClientCount.incrementAndGet();
        myLogD("onBind() -> boundClientCount=" + c + " intent=" + intent);
        // if a stop was pending, cancel it because a client just bound
        serviceHandler.removeCallbacks(stopRunnable);
        return super.onBind(intent);
    }

    @Override public boolean onUnbind(Intent intent) {
        int c = boundClientCount.decrementAndGet();
        myLogD("onUnbind() -> boundClientCount=" + c + " intent=" + intent);
        // allow rebind callbacks if you want to observe them
        return true; // keep onRebind() callbacks
    }

    @Override public void onRebind(Intent intent) {
        int c = boundClientCount.incrementAndGet();
        myLogD("onRebind() -> boundClientCount=" + c + " intent=" + intent);
        serviceHandler.removeCallbacks(stopRunnable);
        super.onRebind(intent);
    }

    /********************************************************************************
     ***       LOADING FILES
     ********************************************************************************
     */

    /**
     * Swap current engine with a new one, releasing TTS if needed, keeping flags intact.
     */
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
    public void loadFile() {
        myLogD("loadFile()  directPlay=" + directPlay);

        // Ensure we actually have something to play
        PlayList pl = PlayList.getInstance();
        if (pl == null || pl.getZikFile() == null) {
/*
            PlayList.createFromScratch(this);
            pl = PlayList.getInstance();
            if (pl == null || pl.getZikFile() == null) {
                loadFileKO(null);
                return;
            }

 */
            loadFileKO("playlist/zikFile null");
            return;
        }

        ZikFile zf = pl.getZikFile();

        // Decide engine type from display or path
        final boolean isText = (zf.getPath() != null && zf.getPath().toLowerCase().endsWith(".txt"));

        // Resolve Uri
        Uri src = UriHelper.resolvePlayableUri(this, zf);
        if (src == null) {
            myLogEE(null, "resolvePlayableUri failed for: " + zf.getPath());
            loadFileKO(zf.getPath());
            return;
        }

        // New generation (guards async callbacks)
        engineGen++;
        long gen = engineGen;

        // Swap engine
        PlayerEngine fresh = isText
                ? new TtsEngine(getApplicationContext(), AppTtsManager.get(getApplicationContext()), engineCb, gen)
                : new MediaPlayerEngine(engineCb, gen);
        setEngine(fresh);

        ErrorLoadingFile = false;

        // Update UI to "buffering" and notif already done...
        showForegroundNotification(isPlaying());

        // PHASE: LOADING_TEXT (text extraction / paragraphize happens inside setDataSource)
        if (fresh instanceof TtsEngine) setUiPhase(Intents.PHASE_LOADING_TEXT, "Loading text…");

        // Update media session metadata early (title/sub), set BUFFERING, show paused notif
        media.updateState(PlaybackStateCompat.STATE_BUFFERING, 0, 0f, ACTIONS_FILE);

        String cover = (pl.getFolder()==null) ? null : pl.getFolder().image;
        media.setMetadata(zf.getDisplayName(), zf.getFolderName(), zf.getFolderName(), 0L, ImageHelper.decodeBitmapFromStringUri(this, cover, 512));

        showForegroundNotification(isPlaying());

        try {
            engine.reset();
            engine.setDataSource(this, src, zf.getDisplayName());

            if (engine instanceof TtsEngine) {
                String picked = null;
                String pickedSource = "system";

                // 1) Per-book voice (never override this later)
                String perBook = Pref.getBookTtsVoiceName(this, zf.getIdFolder());
                if (perBook != null && !perBook.isEmpty() && !"system".equalsIgnoreCase(perBook)) {
                    picked = perBook;
                    pickedSource = "book";
                } else {
                    // 2) App-wide fallback (only if no per-book)
                    String appWide = Option.getTtsVoice();
                    if (appWide != null && !appWide.isEmpty() && !"system".equalsIgnoreCase(appWide)) {
                        picked = appWide;
                        pickedSource = "global";
                    }
                }
                if (picked != null) {
                    try {
                        boolean ok = ((TtsEngine) engine).setVoiceByName(picked);
                        myLog("Applied initial TTS voice = " + picked + " (source=" + pickedSource + ", ok=" + ok + ")");
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
     ***       PLAY-PAUSE
     ********************************************************************************
     */
    private boolean needsReloadForPlaylist() {
        PlayList pl = PlayList.getInstance();
        ZikFile cur = (pl != null) ? pl.getZikFile() : null;
        int wantId = (cur != null) ? cur.getId() : 0;

        // If we have no last snapshot or ids differ, we need to load.
        if (PlaybackUiBus.get().state().getValue() == null) return true;
        return PlaybackUiBus.get().state().getValue().trackId != wantId;
    }

    public void playAudio() {
        myLog("playAudio() - start");

        broadcastUiState("playAudio");

        if (!radioMode && !podcastMode) {
            if (engine == null || needsReloadForPlaylist()) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    directPlay = true;
                    loadFile();
                });
                return;
            }
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


    public void pauseAudioNoSave() {
        if (engine != null && engine.isPlaying()) {
            enginePause();
            focus.abandon();
            sleepTimer.stop();
            showForegroundNotification(false);
            broadcastUiState("pauseAudioNoSave");
        }
    }

    public void pauseAudio() {
        if (engine != null && engine.isPlaying()) {
            enginePause();
            updateZikFileStateInDB(false);
            focus.abandon();
            sleepTimer.stop();
            showForegroundNotification(false);
            broadcastUiState("pauseAudio");
        }
    }

    public void playPauseAudio() {
        myLog("playPauseAudio()");
        if (isPlaying()) {
            pauseAudio();
        } else {
            playAudio();
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
     ***       SPEED - POSITION
     ********************************************************************************
     */

    public void setPosition(long position) {
        myLog("setPosition() : " + myDF.format(position) + " - " + Tonio.formatMmSs(position));
        if (engine != null) {
            progress.suspendOnce(300); //avoid races from progressUpdater => UI
            engine.seekTo(position);
            updatePlaybackStateForPosition();
            broadcastUiState("setPosition");
        }
    }

    public long getPosition() {
        long pos = engine != null ? engine.getCurrentPosition() : 0;
        if (LOG_TRACE_ALL && PlayList.getInstance() != null && PlayList.getInstance().getZikFile() != null) {
            int curPosGlobalVar = (int) PlayList.getInstance().getZikFile().getPosition();
            long diff = curPosGlobalVar - pos;
            myLogD("getPosition() Saved/EngineCurrent  " + curPosGlobalVar + "/" + pos + "  -  Diff = " + diff);
        }
        return pos;
    }

    public long getDuration() {
        return engine != null ? engine.getDuration() : 0;
    }

    public boolean isPlaying() {
        return engine != null && engine.isPlaying();
    }

    public int getAudioSessionId() {
        return engine != null ? engine.getAudioSessionId() : 0;
    }

    public void setSpeed(double speed) {
        try {
            this.speed = speed;
            if (engine != null) engine.setSpeed((float) speed);
            myLog("setSpeed() : " + speed);
        } catch (Exception e) {
            myLogEE(e, "MediaService Error setting Speed");
        }
        ZikFile zf = getCurrentZikFile();
        if (zf != null) Pref.saveSpeedToPref(zf.getIdFolder(), speed);
    }

    public double getSpeed() {
        ZikFile zf = getCurrentZikFile();
        if (zf != null) speed = Pref.getSpeedFromPref(zf.getIdFolder());
        if (speed == 0) speed = 1.0;
        return speed;
    }

    public boolean isRunning() {
        if (LOG_TRACE_ALL) myLogD("isRunning : " + isRunning);
        return isRunning;
    }

    private @Nullable ZikFile getCurrentZikFile() {
        PlayList pl = PlayList.getInstance();
        return (pl != null) ? pl.getZikFile() : null;
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_THRESHOLD) {
            myLogW("onTrimMemory() - level=[" + level + "] >= " + TRIM_MEMORY_THRESHOLD);
            logPauseTime();
            /*
            if (mediaPlayer != null) {
                try {
                    mediaPlayer.release();
                    mediaPlayer = null;
                    myLog("mediaPlayer released due to memory pressure");
                } catch (Exception e) {
                    myLogEE(e,"onTrimMemory - Error releasing mediaPlayer");
                }
            } else {
                myLog("mediaPlayer was already null");
            }
             */
        }
    }

    public void logPauseTime() {
        if (Pref.getPauseTime() != 0) {
            long pauseTime = (System.currentTimeMillis() - Pref.getPauseTime());
            myLogD("Paused since " + formatTime(pauseTime, true) + ".   MAX is " + formatTime(TRIM_AFTER_PAUSE_MS, false, false));
        }
    }


    /********************************************************************************
     ***       UPDATE DB
     ********************************************************************************
     */
    private void updateZikFileStateInDB(boolean bFinished) {
        if (radioMode || podcastMode) return;
        ZikFile zf = getCurrentZikFile();
        if (zf == null) {
            myLogEE(null, "updateZikFileState : currentZikFile = null");
            return;
        }
        try {
            long pos = bFinished ? (int) zf.getDuration() : getPosition();
            long dur = bFinished ? (int) zf.getDuration() : getDuration();
            progress.update(zf, bFinished, pos, dur);
        } catch (Exception e) {
            myLogEE(e, "updateZikFileStateInDB");
        }
    }


    /********************************************************************************
     ***       NOTIFICATIONS
     ********************************************************************************
     */


    private void enginePause() {
        myLogD("mediaPlayerPause()");
        if (engine != null) engine.pause();
        updateSessionState(false);
        Pref.setPauseTime();
    }

    @SuppressWarnings("IfCanBeSwitch")
    private void playBeep(String beepType) {
        myLogI("playBeep - argument : " + beepType);
        try {
            if (beepType.equals("1beep")) {
                new ToneGenerator(AudioManager.STREAM_MUSIC, 100).startTone(ToneGenerator.TONE_CDMA_PIP, 150);
            } else if (beepType.equals("2beeps")) {
                new ToneGenerator(AudioManager.STREAM_MUSIC, 50).startTone(ToneGenerator.TONE_DTMF_0, 1000); // actually a long beep
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
        FirebaseAnalyticsHelper.tellAnalyticsLoadFileKO(strFilePathError);
        //LocalBroadcastManager.getInstance(MediaService.this).sendBroadcast(new Intent(NOTIFICATION_FILENOTFOUND));
        ErrorLoadingFile = true;
        ErrorUi.showPlayAudioErrorMessage(this, null);
        shutdown(false);
    }

    private void onEnginePrepared() {
        myLogD("onEnginePrepared()");

        setUiPhase(Intents.PHASE_READY, null);

        if (!radioMode && !podcastMode) {
            setPositionPlayStart();

            if (engine != null) {
                PlayList pl = PlayList.getInstance();
                if (pl != null) {
                    ZikFile zf = pl.getZikFile();
                    if (zf != null) {
                        String cover = (pl.getFolder()==null ? null : pl.getFolder().image);
                        media.setMetadata(zf.getDisplayName(), zf.getFolderName(), zf.getFolderName(), engine.getDuration(), ImageHelper.decodeBitmapFromStringUri(this, cover, 512));
                    }
                }
            }
        }

        // Only send READY when engine.isReady()==true
        sendReadyToPlay("onEnginePrepared");

        if (directPlay) {
            startPlayWithEngine();
        } else {
            updateSessionState(false);
            showForegroundNotification(false);
        }
        broadcastUiState("onEnginePrepared");
    }

    private void onEngineCompletion() {
        myLogD("onEngineCompletion()");
        if (!ErrorLoadingFile) {
            updateZikFileStateInDB(true);
            alertTrackFinished();
            PlayList pl = PlayList.getInstance();
            if (pl!=null && pl.isLastTrack()) {
                if (Option.getBeepBookEnd()) playBeep("3beeps");
                alertPlaylistFinished();
                shutdown(false);
            } else {
                nextTrack();
            }
        }
    }


    private void onEngineError(String msg, int what, int extra) {
        myLogEE(null, "Engine error: " + msg + " (" + what + "," + extra + ")");
        ErrorLoadingFile = true;
        sleepTimer.stop();

        // TTS errors are recoverable → do NOT send NOTIFICATION_ERROR
        if (msg != null && msg.startsWith("TTS")) {
            setUiPhase(Intents.PHASE_ERROR, "Speech engine error (" + what + ")");
            // Keep UI alive; do not broadcast NOTIFICATION_ERROR.
            return;
        }

        // Non-TTS = real fatal
        alertError(null, null);
        //broadcastUiState();
    }


    private void onEngineFatal(String msg, int what, int extra) {
        ErrorLoadingFile = true;
        sleepTimer.stop();
        alertError(null, null);
        if (podcastMode) {
            if (msg.contains("ERROR_CODE_IO_BAD_HTTP_STATUS")) {
                myToastEE(null, getString(R.string.Podcast_source_error));
            } else {
                myToastE(getString(R.string.unexpected_error));
                myLogEE(null, "Engine FATAL: " + msg + " (" + what + "," + extra + ")");
            }
        }
    }

    public boolean isReadyToPlay() {
        return engine != null && engine.isReady();
    }

    public void pingUi() {
        broadcastUiState("pingUi");
    }

    public long getSleepLeftMs() {
        //myLog("sleep:" + (sleepTimer!=null ? "" + sleepTimer.getSleepLeftMs() : "sleep timer null"));
        return (sleepTimer!=null) ? sleepTimer.getSleepLeftMs() : 0;
    }

    public String getPlayMode() {
        //String playMode = "book", "tts", "radio", "podcast", "book"
        if (engine instanceof TtsEngine) {
            return "tts";
        } else if (engine instanceof ExoRadioPlayerEngine) {
            return "radio";
        } else if (engine instanceof ExoStreamPlayerEngine) {
            return "podcast";
        }
        return "book";
    }

    public String getLoadPhase() {
        if (engine==null) return Intents.PHASE_OFF;;
        if (engine.isPlaying() || engine.isReady()) {
            return Intents.PHASE_READY;
        } else {
            return Intents.PHASE_BUFFERING;
        }
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

    public void setTtsStartOffsetChars(int start) {
        if (!(engine instanceof TtsEngine)) return;
        myLogD("setTtsStartOffsetChars : " + start);
        ((TtsEngine) engine).setStartOffsetChars(start);
    }

    private void updatePlaybackStateForPosition() {
        if (radioMode) return;
        if (podcastMode) return;
        if (engine == null) return;
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
            if (dur > 0) savedPos = Math.max(0, Math.min(savedPos, dur));
            myLogD(zikFile.getName() + " - savedPosition = " + savedPos);
            if (savedPos >= (dur - Var.START_AT_ZERO_IF_TRACK_AT_END_BUFFER_DELAY_IN_MS)) {
                engine.seekTo(0);
                myLog("at end or near end, reset position to 0");
            } else {
                engine.seekTo(savedPos);
            }

            //max reach ?, reset to 0
            //if (zikFile.getPosition() >= zikFile.getDuration()) {
            myLogD("setPositionPlayStart() : " + (engine == null ? "engine is null" : "pos=" + Tonio.formatMmSsMs(engine.getCurrentPosition()) + " - dur=" + Tonio.formatMmSsMs(engine.getDuration())));
            if (engine != null && engine.getCurrentPosition() >= (engine.getDuration() - Var.START_AT_ZERO_IF_TRACK_AT_END_BUFFER_DELAY_IN_MS)) { // because sometime, nearly at end but not at end !
                myLogE("failsafe - at end or near end, reset position to 0");
                engine.seekTo(0);
            } else { // Rewind-after-pause
                if (Option.getRewindAfterPause() && zikFile.lLastAccess != null) {
                    long minutes = (System.currentTimeMillis() - zikFile.lLastAccess) / (60 * 1000);
                    int rewindMs = 0;
                    for (int[] rule : REWIND_AFTER_PAUSE) {
                        if (minutes >= rule[0]) rewindMs = rule[1];
                        else break;
                    }
                    if (rewindMs > 0) {
                        myLogD("Rewind " + (rewindMs / 1000) + "sec. after a " + minutes + "min. pause.");
                        backwardAudio(rewindMs);
                    }
                }
            }

            //Cut Intro (book option)
            int introCut = Pref.getIntroCutFromPref(this, zikFile.getIdFolder()) * 1000;
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
        myLog("setUiPhase : " + phase + " - msg : " + msg);
        currentUiPhase = phase;
        currentUiPhaseMsg = msg;
        PlaybackUiBus.get().setLoadPhase(phase);
    }

    // Full file/audiobook actions (current behavior)
    private static final long ACTIONS_FILE =
            PlaybackStateCompat.ACTION_PLAY
                    | PlaybackStateCompat.ACTION_PAUSE
                    | PlaybackStateCompat.ACTION_STOP
                    | PlaybackStateCompat.ACTION_REWIND
                    | PlaybackStateCompat.ACTION_FAST_FORWARD
                    | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    | PlaybackStateCompat.ACTION_PLAY_PAUSE
                    | PlaybackStateCompat.ACTION_SEEK_TO;

    // Radio should only expose play/pause, nothing else
    private static final long ACTIONS_RADIO =
            PlaybackStateCompat.ACTION_PLAY
                    | PlaybackStateCompat.ACTION_PAUSE;

    private long currentActions() {
        return radioMode ? ACTIONS_RADIO : ACTIONS_FILE;
    }
    private void playRadioStream(@NonNull String url, @NonNull String title, @Nullable String imageUrl) {
        myLogD("playRadioStream: " + title + " -> " + url);

        // Mark radio mode + meta
        radioMode = true;
        podcastMode = false;
        radioTitle = title;
        radioImageUrl = imageUrl;
        radioUri = Uri.parse(url);
        broadcastUiState("playRadioStream");                  // first snapshot (BUFFERING)

        // Swap engine to Exo for radio
        engineGen++;
        long gen = engineGen;
        PlayerEngine fresh = new ExoRadioPlayerEngine(getApplicationContext(), engineCb, gen);
        setEngine(fresh);
        ErrorLoadingFile = false;

        // Update MediaSession to BUFFERING with radio meta
        //media.updateState(PlaybackStateCompat.STATE_BUFFERING, 0, 0f, playbackStateCompatAction);

        updateSessionState(false);

        media.setMetadataRadio(
                /* title   */ title,
                /* artist  */ getString(R.string.live_radio),
                /* album   */ title,
                /* artBmp  */ null
        );
        showForegroundNotification(false); // shows paused/buffering style

        try {
            engine.reset();
            engine.setDataSource(this, radioUri, title);
            engine.prepareAsync();

            // Broadcast a first UI state (pos/dur 0)
            broadcastUiState("playRadioStream2");
            // Auto-play when ready
            directPlay = true;

        } catch (Exception e) {
            myLogEE(e, "playRadioStream setDataSource/prepareAsync failed");
            alertError(null, null);
        }
    }
    private void playPodcastStream(long podcastFeedId, @NonNull String url, @NonNull String title, @Nullable String imageUrl) {
        myLogI("playPodcastStream: [" + title + "] -> [" + url + "] - id=" + podcastFeedId);

        // Mark radio mode + meta
        radioMode = false;
        podcastMode = true;
        radioTitle = title;
        radioImageUrl = imageUrl;
        this.podcastFeedId = podcastFeedId;
        radioUri = Uri.parse(url);
        if (radioUri==null) {
            myLogEE(null, "playPodcastStream : radioUri==null");
            return;
        }
        broadcastUiState("playPodcastStream");                  // first snapshot (BUFFERING)

        // Swap engine to Exo for radio
        engineGen++;
        long gen = engineGen;
        PlayerEngine fresh = new ExoStreamPlayerEngine(getApplicationContext(), engineCb, gen);
        setEngine(fresh);
        ErrorLoadingFile = false;

        // Update MediaSession to BUFFERING with radio meta
        //media.updateState(PlaybackStateCompat.STATE_BUFFERING, 0, 0f, playbackStateCompatAction);
        PlaybackStateCompat s = new PlaybackStateCompat.Builder()
                .setActions(ACTIONS_FILE)
                .setState(PlaybackStateCompat.STATE_BUFFERING,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                        0f,
                        System.currentTimeMillis())
                .build();
        media.session().setPlaybackState(s);

        media.setMetadataRadio(
                /* title   */ title,
                /* artist  */ getString(R.string.podcasts),
                /* album   */ title,
                /* artBmp  */ null
        );
        showForegroundNotification(false); // shows paused/buffering style

        try {
            engine.reset();
            engine.setDataSource(this, radioUri, title);
            engine.prepareAsync();

            // Broadcast a first UI state (pos/dur 0)
            broadcastUiState("playPodcastStream2");
            // Auto-play when ready
            directPlay = true;

        } catch (Exception e) {
            myLogEE(e, "playPodcastStream setDataSource/prepareAsync failed");
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
                long pos     = (radioMode || podcastMode) ? PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN
                        : (engine != null ? engine.getCurrentPosition() : 0L);
                float sp     = playing ? (float) getSpeed() : 0f;

                PlaybackStateCompat init = new PlaybackStateCompat.Builder()
                        .setActions(actions)
                        .setState(playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                                pos, sp, System.currentTimeMillis())
                        .build();
                s.setPlaybackState(init);
                cur = init; // so the debug log below never sees null
            }

            myLogD("updateSessionState(): active=" + s.isActive()
                    + " prev=" + cur.getState()
                    + " actions=" + Long.toHexString(cur.getActions()));

            // Then set the *actual* state you want (radio vs file/tts)
            if (radioMode) {
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

    //TODO replace all this shot by MediaSession controller
    private void emitUiTick(String calledFrom) {
        broadcastUiState(calledFrom);
        // TODO for later, just a light snapshot :
        /*
        public void setPositionAndPlaying(long pos, long dur, boolean playing, String calledFrom) {
            PlaybackUiState cur = _state.getValue();
            if (cur == null) return;
            emit(new PlaybackUiState(
                    cur.loadPhase, playing, cur.ready, cur.playMode,
                    pos, dur, cur.title, cur.subTitle, cur.cover,
                    cur.trackId, cur.folderId, cur.podcastFeedId,
                    calledFrom, cur.callCounter + 1
            ));

        }
         */
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
        if (fullText == null) fullText = "";
        int clamped = Math.max(0, Math.min(chars, fullText.length()));

        // Were we speaking?
        boolean wasPlaying = tts.isPlaying();

        // 1) Stop current utterance cleanly (no full reset)
        try {
            tts.pause(); // lighter than stop(); avoids resetting engine state
        } catch (Throwable ignored) {}

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
        //    (Your engine will continue sending NOTIFICATION_TTS_RANGE during playback)
        try {
            int[] w = TtsHelper.findWordBounds(fullText, clamped);
            Intent i = new Intent(Intents.NOTIFICATION_TTS_RANGE)
                    .putExtra(Intents.EXTRA_TTS_START, w[0])
                    .putExtra(Intents.EXTRA_TTS_END,   w[1]);
            LocalBroadcastManager.getInstance(this).sendBroadcast(i);
        } catch (Throwable ignored) {}

        // 4) If we were speaking, resume from the new cursor; otherwise stay READY
        if (wasPlaying) {
            setUiPhase(Intents.PHASE_STARTING, null); // brief spinner if you like
            try {
                tts.start(); // continue speaking from new cursor
            } catch (Throwable t) {
                myLogEE(t, "handleTtsSeekChars: restart failed");
                setUiPhase(Intents.PHASE_ERROR, "TTS restart failed");
                return;
            }
        } else {
            setUiPhase(Intents.PHASE_READY, null);
        }

        // 5) Reflect new position in MediaSession/notification & UI snapshot
        updatePlaybackStateForPosition();   // keeps session in sync
        broadcastUiState("handleTtsSeekChars");
    }

}
