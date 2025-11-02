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
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media.session.MediaButtonReceiver;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.tts.AppTtsManager;
import com.driot.bookplayer.utils.log.LoggingService;
import com.driot.bookplayer.activities.PlayActivity;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Pref;

import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.driot.bookplayer.utils.Tonio.formatTime;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 01/11/20
 */

public class AudioService extends LoggingService {

    // ---- TTS phase tracking ----
    private @NonNull String currentUiPhase = Intents.PHASE_LOADING_TEXT;
    private @Nullable String currentUiPhaseMsg = null;

    private final MutableLiveData<PlaybackUiState> uiLive = new MutableLiveData<>();
    private PlayList.MetaState lastPlayListMeta = new PlayList.MetaState(false, null, false);
    private final Observer<PlayList.MetaState> metaObs = meta -> {
        lastPlayListMeta = meta;          // cache latest meta
        broadcastUiState();       // rebuild + emit unified UI
    };

    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    public static volatile boolean isRunning = false;

    private final android.content.BroadcastReceiver pingReceiver = new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context ctx, android.content.Intent i) {
            if (i == null) return;
            if (Intents.ACTION_PING_UI.equals(i.getAction())) {
                myLog("PING received");
                broadcastUiState();
            }
        }
    };

    private static final String ID_NOTIFICATION_PLAY_AUDIO_CHANNEL = "audio_channel_of_bookplayer";
    private static final int ID_NOTIFICATION_PLAY_AUDIO_INT = 2;


    public static volatile com.driot.bookplayer.player.PlaybackUiState lastUiState = null;
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
    long playbackStateCompatAction =
            PlaybackStateCompat.ACTION_PLAY
                    | PlaybackStateCompat.ACTION_PAUSE
                    | PlaybackStateCompat.ACTION_STOP
                    | PlaybackStateCompat.ACTION_REWIND
                    | PlaybackStateCompat.ACTION_FAST_FORWARD
                    | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    | PlaybackStateCompat.ACTION_PLAY_PAUSE
                    | PlaybackStateCompat.ACTION_SEEK_TO;

    private static final boolean LOG_TRACE_ALL = false;

    // --- RADIO MODE ---
    private boolean radioMode = false;
    @Nullable private String radioTitle = null;
    @Nullable private String radioImageUrl = null; // you can display it in notif if you already support URL bitmaps
    @Nullable private Uri    radioUri = null;

    private final IBinder binder = new BackgroundBinder();
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
    public static final String NOTIFICATION_PLAYBACK_TIMER_VALUE = "NOTIFICATION_PLAYBACK_TIMER_VALUE";

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
            main.post(AudioService.this::onEnginePrepared);
        }

        @Override
        public void onCompletion(long gen) {
            if (gen != engineGen) return;
            main.post(AudioService.this::onEngineCompletion);
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
            LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(i);
            //});
        }
    };


    private void sendReadyToPlay(String why) {
        boolean ok = (engine != null && engine.isReady() && !ErrorLoadingFile);
        myLogD("sendReadyToPlay? [" + why + "] ok=" + ok + " ttsMode=" + isTtsMode());
        if (!ok) return;

        Intent i = new Intent(READY_TO_PLAY);
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
    }

    private PlayerEngine engine;
    private final Runnable onPrepared = this::onEnginePrepared;

    public boolean directPlay;
    private boolean justAdvancedToNext = false; //for TTS starting anywhere

    private boolean suppressMiniUntilNextPlay = false;

    public boolean isMiniSuppressed() {
        return suppressMiniUntilNextPlay;
    }

    private void broadcastUiCleared() {
        lastUiState = null;
        uiLive.postValue(new PlaybackUiState(false, 0, 0, "", "", "",
                0, 0, false, engine instanceof TtsEngine));
        Intent i = new Intent(Intents.ACTION_UI_STATE)
                .putExtra(Intents.EXTRA_UI_PLAYING, false)
                .putExtra(Intents.EXTRA_UI_POS, 0L)
                .putExtra(Intents.EXTRA_UI_DUR, 0L)
                .putExtra(Intents.EXTRA_UI_TITLE, "")
                .putExtra(Intents.EXTRA_UI_SUBTITLE, "")
                .putExtra(Intents.EXTRA_UI_SUPPRESS_MINI, true);

        currentUiPhase = Intents.PHASE_LOADING_TEXT;
        currentUiPhaseMsg = null;

        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
    }

    //needed for Car ?
    private void broadcastUiState() {
        // Build the UI snapshot first (radio-aware)
        PlaybackUiState s;
        if (radioMode) {
            String title = (radioTitle != null) ? radioTitle : getString(R.string.live_radio);
            String text  = getString(R.string.live_radio);
            String cover = (radioImageUrl != null) ? radioImageUrl : "";
            long   pos   = (engine != null) ? engine.getCurrentPosition() : 0;
            long   dur   = (engine != null) ? engine.getDuration()        : 0; // live => likely 0/unknown
            boolean playing = (engine != null) && engine.isPlaying();

            s = new PlaybackUiState(
                    playing,
                    pos,
                    dur,
                    title,
                    text,
                    cover,
                    /* trackId */ 0,
                    /* folderId */ 0,
                    /* ready */ (engine != null) && engine.isReady(),
                    /* ttsMode */ (engine instanceof TtsEngine)
            );
        } else {
            s = buildUiState();  // your existing file/TTS path
        }

        // Cache + publish LiveData
        lastUiState = s;
        uiLive.postValue(s);
        // Also broadcast the Intent (unchanged structure, but now using 's')
        Intent i = new Intent(Intents.ACTION_UI_STATE)
                .putExtra(Intents.EXTRA_UI_PLAYING, s.playing)
                .putExtra(Intents.EXTRA_UI_POS, s.positionMs)
                .putExtra(Intents.EXTRA_UI_DUR, s.durationMs)
                .putExtra(Intents.EXTRA_UI_TITLE, s.title)
                .putExtra(Intents.EXTRA_UI_SUBTITLE, s.subTitle)
                .putExtra(Intents.EXTRA_UI_COVER, s.cover)
                .putExtra(Intents.EXTRA_UI_SUPPRESS_MINI, suppressMiniUntilNextPlay)

                .putExtra(Intents.EXTRA_UI_TRACK_ID, s.trackId)
                .putExtra(Intents.EXTRA_UI_FOLDER_ID, s.folderId)
                .putExtra(Intents.EXTRA_UI_READY, s.ready)

                .putExtra(Intents.EXTRA_UI_TTS, s.ttsMode)
                .putExtra(Intents.EXTRA_UI_PHASE, currentUiPhase)
                .putExtra(Intents.EXTRA_UI_PHASE_MSG, currentUiPhaseMsg)

                .putExtra(Intents.EXTRA_UI_IS_RADIO, radioMode);

        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
    }


    private PlaybackUiState buildUiState() {
        PlayList pl = PlayList.getInstance();
        ZikFile z = (pl != null) ? pl.getZikFile() : null;
        Folder f = (pl != null) ? pl.getFolder() : null;

        String title = (z != null) ? z.getFolderName() : (f != null ? f.getName() : "");
        String text = (z != null) ? z.getDisplayName() : "";
        //String cover = (f != null) ? StorageHelper.checkAndCleanImagePath(this, f.image) : "";
        String cover = (f != null) ? f.image : "";

        // Be defensive around engine readiness to avoid 0/0 churn if you want
        long pos = (engine != null) ? (long) engine.getCurrentPosition() : 0;
        long dur = (engine != null) ? (long) engine.getDuration() : 0;
        boolean playing = (engine != null) && engine.isPlaying();

        int trackId = (z != null) ? z.getId() : 0;
        int folderId = (f != null) ? f.getId() : 0;
        boolean ready = (engine != null) && engine.isReady();
        boolean ttsMode = (engine instanceof TtsEngine);

        return new PlaybackUiState(playing, pos, dur, title, text, cover,
                trackId, folderId, ready, ttsMode);
    }


    private final MediaSessionCompat.Callback callback = new MediaSessionCompat.Callback() {

        @Override
        public void onPlay() { // is called by headset button pressed !!!
            myLog("MediaSessionCompat.Callback - onPlay()");
            super.onPlay();
            playPauseAudio();
        }

        @Override
        public void onPause() {
            myLog("MediaSessionCompat.Callback - onPause()");
            super.onPause();
            playPauseAudio();
        }

        @Override
        public void onStop() {
            myLog("MediaSessionCompat.Callback - onStop()");
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
            super.onFastForward();
            myLog("MediaSessionCompat.Callback - onFastForward()");
        }

        @Override
        public void onCommand(String command, Bundle extras, ResultReceiver cb) {
            myLog("MediaSessionCompat.Callback - onCommand(" + command + "..., Bundle extras, ResultReceiver cb");
            super.onCommand(command, extras, cb);
        }

        @Override
        public void onRewind() {
            myLog("MediaSessionCompat.Callback - onRewind()");
            super.onRewind();
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
            super.onSkipToNext();
        }

        @Override
        public void onSkipToPrevious() {
            backwardAudio();
            myLog("MediaSessionCompat.Callback - onSkipToPrevious()");
            super.onSkipToPrevious();
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
        media = new com.driot.bookplayer.player.MediaSessionController(this, callback);
        media.updateState(
                PlaybackStateCompat.STATE_PAUSED,
                0L,
                0f,
                playbackStateCompatAction /* your ACTION_* bitmask */);

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
                sleepCheckHandler, DELAY_CHECK_TIMER_SLEEP, (radioMode ? "radio" : "book"),
                new SleepTimer.Listener() {
                    @Override
                    public void onTick(int elapsedSeconds) {
                        Pref.addToTotalMsPlayed(DELAY_CHECK_TIMER_SLEEP);
                        updateZikFileStateInDB(false);
                        LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_PLAYBACK_TIMER_VALUE).putExtra(TIMER_VALUE, elapsedSeconds));
                    }

                    // go SLEEP
                    @Override
                    public void onReachedMax() {
                        if (Option.getBeepAutoStop()) playBeep("2beeps");
                        LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_PLAYBACK_MAXTIMEREACH));
                        pauseAudio();
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

        LocalBroadcastManager.getInstance(this).registerReceiver(
                pingReceiver,
                new android.content.IntentFilter(Intents.ACTION_PING_UI)
        );

        myLogD("onCreate() - END");
    }
// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void showForegroundNotification(boolean playing) {

        if (radioMode) {
            // 1) Limit session capabilities
            applyRadioPlaybackState(playing);

            // 2) (Async) fetch cover, then set metadata & update notif
            //    If you already have a cached Bitmap, set it now and skip Glide.
            Bitmap currentArt = null; // your cache if any
            applyRadioMetadata(currentArt, radioTitle);

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
                                    AudioService.this, PlaybackStateCompat.ACTION_PLAY);
                        }
                        @Override public PendingIntent pause() {
                            return MediaButtonReceiver.buildMediaButtonPendingIntent(
                                    AudioService.this, PlaybackStateCompat.ACTION_PAUSE);
                        }
                        @Override public PendingIntent content() {
                            return PendingIntent.getActivity(
                                    AudioService.this, 0,
                                    new Intent(AudioService.this, com.driot.bookplayer.activities.MainActivity.class),
                                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
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
                                applyRadioMetadata(bmp, radioTitle);
                                // rebuild/update the notification so largeIcon shows
                                Notification updated = notif.build(
                                        media.session(), playing,
                                        radioTitle != null ? radioTitle : getString(R.string.live_radio),
                                        getString(R.string.live_radio),
                                        /* same ActionProvider */ new PlaybackNotificationManager.ActionProvider() {
                                            @Override public PendingIntent rewind()      { return null; }
                                            @Override public PendingIntent fastForward() { return null; }
                                            @Override public PendingIntent play()  { return MediaButtonReceiver.buildMediaButtonPendingIntent(AudioService.this, PlaybackStateCompat.ACTION_PLAY); }
                                            @Override public PendingIntent pause() { return MediaButtonReceiver.buildMediaButtonPendingIntent(AudioService.this, PlaybackStateCompat.ACTION_PAUSE); }
                                            @Override public PendingIntent content() {
                                                return PendingIntent.getActivity(
                                                        AudioService.this, 0,
                                                        new Intent(AudioService.this, com.driot.bookplayer.activities.MainActivity.class),
                                                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
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
                    @Override
                    public PendingIntent rewind() {
                        return MediaButtonReceiver.buildMediaButtonPendingIntent(AudioService.this, PlaybackStateCompat.ACTION_REWIND);
                    }

                    @Override
                    public PendingIntent play() {
                        return MediaButtonReceiver.buildMediaButtonPendingIntent(AudioService.this, PlaybackStateCompat.ACTION_PLAY);
                    }

                    @Override
                    public PendingIntent pause() {
                        return MediaButtonReceiver.buildMediaButtonPendingIntent(AudioService.this, PlaybackStateCompat.ACTION_PAUSE);
                    }

                    @Override
                    public PendingIntent fastForward() {
                        return MediaButtonReceiver.buildMediaButtonPendingIntent(AudioService.this, PlaybackStateCompat.ACTION_FAST_FORWARD);
                    }

                    @Override
                    public PendingIntent content() {
                        final int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

                        androidx.core.app.TaskStackBuilder tsb = androidx.core.app.TaskStackBuilder.create(AudioService.this);
                        // 1) Always start at Main
                        tsb.addNextIntent(new Intent(AudioService.this, com.driot.bookplayer.activities.MainActivity.class));

                        // 2) If multiple tracks, insert the track list screen before PlayActivity
                        PlayList pl = PlayList.getInstance();
                        ZikFile z = (pl != null) ? pl.getZikFile() : null;
                        int folderId = (z != null) ? z.getIdFolder() : -1;

                        if (folderId > 0 && pl != null && pl.getSize() > 1) {
                            Intent trackList = new Intent(AudioService.this, com.driot.bookplayer.activities.ZikFileActivity.class)
                                    .putExtra(Intents.EXTRA_FOLDER_ID, folderId);
                            tsb.addNextIntent(trackList);
                        }

                        // 3) Finally PlayActivity (singleTop/clearTop like you already do)
                        tsb.addNextIntent(new Intent(AudioService.this, com.driot.bookplayer.activities.PlayActivity.class)
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
    private void applyRadioPlaybackState(boolean playing) {
        long actions = 0L;
        if (playing) actions |= PlaybackStateCompat.ACTION_PAUSE; else actions |= PlaybackStateCompat.ACTION_PLAY;

        // No seek/skip/ff/rw for radio:
        // (do NOT include SEEK_TO / SKIP_TO_NEXT / SKIP_TO_PREVIOUS / FAST_FORWARD / REWIND)

        final PlaybackStateCompat state = new PlaybackStateCompat.Builder()
                .setActions(actions)
                // Hide progress bar by reporting unknown position & speed:
                .setState(
                        playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, // <- key to hide seek bar
                        playing ? 1f : 0f,
                        System.currentTimeMillis()
                )
                .build();

        media.session().setPlaybackState(state);
    }
    private void applyRadioMetadata(@Nullable Bitmap largeIcon, @Nullable String title) {
        MediaMetadataCompat.Builder b = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title != null ? title : getString(R.string.live_radio));
        // Do NOT set METADATA_KEY_DURATION for live
        // Optionally mark as radio/stream via album/artist if you want

        if (largeIcon != null) {
            b.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, largeIcon);
            b.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, largeIcon);
        }
        media.session().setMetadata(b.build());
    }



    private void startPlayWithEngine() {
        // Safety net: ensure mini is unsuppressed when actually starting.
        if (suppressMiniUntilNextPlay) {
            suppressMiniUntilNextPlay = false;
            // We'll broadcast right after start so UI updates
        }

        // Audio focus first
        focus.request();

        //if maxReach + introCut + littleRewind
        if (!radioMode) setPositionPlayStart();

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

        media.updateState(
                PlaybackStateCompat.STATE_PLAYING,
                engine.getCurrentPosition(),
                (float) getSpeed(),
                playbackStateCompatAction);
        //engine.setSpeed((float) getSpeed());
        if (!media.session().isActive()) media.setActive(true);

        if (!sleepTimer.isRunning()) {
            int minutes = (customSleepTime == 0) ? Option.getTimeBeforeSleep() : customSleepTime;
            sleepTimer.start(minutes);
        }

        showForegroundNotification(true);
        broadcastUiState();
    }

    private void nextTrack() {
        myLog("Next track");

        if (engine instanceof TtsEngine) setUiPhase(Intents.PHASE_LOADING_TEXT, "Loading text…");

        justAdvancedToNext = true;
        PlayList pl = PlayList.getInstance();
        if (pl == null) {
            alertError("nextTrack", "nextTrack : error getting playlist");
            //loadFileKO();
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
        if (z != null) {
            media.updateState(PlaybackStateCompat.STATE_BUFFERING, 0, 0f, playbackStateCompatAction);
            media.setMetadata(z.getDisplayName(), z.getFolderName(), z.getFolderName(), 0L, null);
            showForegroundNotification(isPlaying());
        }
    }

    private void alertError(String from, String errMsg) {
        if (radioMode) {
            LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_ERROR)
                    .putExtra(FROM, from)
                    .putExtra(ERR_MSG, errMsg)
            );
        } else {
            LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_ERROR)
                    .putExtra(TRACKNUMBER, PlayList.getInstance().getNumZikFile())
                    .putExtra(FROM, from)
                    .putExtra(ERR_MSG, errMsg)
            );
        }
        myLogE("sendBroadcast alertError");
    }

    private void alertTrackFinished() {
        LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_TRACKFINISHED));
        myLog("--------------------------------------------------------------------------------- sendBroadcast alertTrackFinished --------------------------------------------------------------------------------");
    }

    private void alertPlaylistFinished() {
        LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_PLAYLISTFINISHED));
        myLog("sendBroadcast alertPlaylistFinished");
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        myLog("onStartCommand()");
        if (intent != null) {
            String strCallLog = "intent = " + intent +
                    "\ncalled by = " + intent.getStringExtra(Intents.EXTRA_CALLER) +
                    "\nwith action = " + intent.getAction();
            if (intent.getBooleanExtra(Intents.EXTRA_FOREGROUND, false)) {
                myLogI("FOREGROUND AudioService start\n" + strCallLog);
            } else {
                myLog("AudioService start\n" + strCallLog);
            }
        } else {            // happens when Android restarts your sticky service after it was killed, no 5-second foreground requirement in this case because the system didn’t just call startForegroundService(...) on your behalf;
            myLogW("AudioService start with no intent - Android restarts? - because of START_STICKY and no START_REDELIVER_INTENT");
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
            myLogW("AudioService start with no intent.action");
            return START_STICKY;
        }

        switch (action) {
            // -------- High-level “load something and (likely) play” intents --------
            case Intents.ACTION_PLAY_FROM_TRACK: {
                // Enter foreground *before* async work to satisfy the 5s rule
                goForegroundPreparing("Preparing…", "Loading selected track");

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

            case Intents.CMD_TTS_SET_VOICE: {
                final String voiceName = intent.getStringExtra(Intents.EXTRA_TTS_VOICE_NAME);
                if (voiceName == null) {
                    myLogEE(null, "CMD_TTS_SET_VOICE => EXTRA_TTS_VOICE_NAME is null");
                    return START_STICKY;
                }
                if (engine instanceof TtsEngine) {
                    try {
                        broadcastPhase(Intents.PHASE_WARMING_UP, getString(R.string.tts_phase_warming_up));
                        boolean ok = ((TtsEngine) engine).setVoiceByName(voiceName);
                        myLog("Voice change success = " + ok);
                        if (ok) {
                            broadcastPhase(Intents.PHASE_READY, null);
                        } else {
                            broadcastPhase(Intents.PHASE_ERROR, getString(R.string.tts_phase_error));
                        }

                    } catch (Throwable ignored) {
                    }
                }
                return START_STICKY;
            }

            case Intent.ACTION_MEDIA_BUTTON: {
                myLog("onStartCommand() - Intent.ACTION_MEDIA_BUTTON");
                MediaButtonReceiver.handleIntent(media.session(), intent);
                return START_STICKY;
            }

            case Intents.ACTION_PLAY_RADIO: {
                // Enter foreground ASAP (5s rule)
                goForegroundPreparing(getString(R.string.live_radio), null);

                final String url   = intent.getStringExtra(Intents.EXTRA_STREAM_URL);
                final String title = intent.getStringExtra(Intents.EXTRA_TITLE);
                final String img   = intent.getStringExtra(Intents.EXTRA_IMAGE_URL);

                if (url == null || url.isEmpty()) {
                    myLogE("ACTION_PLAY_RADIO without url");
                    return START_NOT_STICKY;
                }

                playRadioStream(url, title != null ? title : getString(R.string.live_radio), img);
                return START_STICKY;
            }


            default:
                // Unknown action — keep service alive and ensure we have a notif if needed
                myLogEE(null, "onStartCommand() - unknown action : [" + action + "]");
                showForegroundNotification(isPlaying());
                return START_STICKY;
        }
    }

    /**
     * Minimal foreground entry used before async prep to satisfy the 5s requirement.
     */
    private void goForegroundPreparing(@Nullable CharSequence title, @Nullable CharSequence text) {
        try {
            CharSequence t = (title != null) ? title : "Preparing…";
            CharSequence s = (text != null) ? text : "Please wait";

            if (radioMode) suppressMiniUntilNextPlay = false;

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
                                    AudioService.this, 0,
                                    new Intent(AudioService.this, PlayActivity.class)
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
        if (!isShuttingDown.compareAndSet(false, true)) {
            myLogI("shutdown() already running; ignore");
            return;
        }

        myLogI("shutdown(fromDestroy=" + fromDestroy + ")");
        broadcastUiCleared();
        isRunning = false;

        radioMode = false;
        radioTitle = null;
        radioImageUrl = null;
        radioUri = null;

        try {
            PlayList.getMetaLive().removeObserver(metaObs);
        } catch (Throwable ignore) {
        }
        try {
            sleepTimer.stop();
        } catch (Throwable ignore) {
        }
        try {
            focus.abandon();
        } catch (Throwable ignore) {
        }
        try {
            stopForeground(true);
        } catch (Throwable ignore) {
        }
        try {
            notif.cancel(ID_NOTIFICATION_PLAY_AUDIO_INT);
        } catch (Throwable ignore) {
        }
        PlayerEngine e = engine; // snapshot
        engine = null;           // prevent reuse elsewhere after shutdown begins
        if (e != null) {
            if (e instanceof TtsEngine) {
                try {
                    ((TtsEngine) e).release();
                } catch (Exception ex) {
                    myLogEE(ex, "TTS release");
                }
            } else {
                try {
                    e.stop();
                } catch (Exception ex) {
                    myLogEE(ex, "engine stop");
                }
                try {
                    e.reset();
                } catch (Exception ex) {
                    myLogEE(ex, "engine reset");
                }
            }
        }
        if (media != null) media.release();

        if (!fromDestroy) stopSelf();
    }

    @Override
    public void onDestroy() {
        myLog("onDestroy()");
        shutdown(true);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        myLog("onBind() - intent.DataString = " + intent.getDataString());
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        myLog("onUnBind() - intent.DataString = " + intent.getDataString());
        return super.onUnbind(intent);
    }

    public class BackgroundBinder extends Binder {
        public AudioService getService() {
            return AudioService.this;
        }
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
            // Try to restore once
            PlayList.restoreIfExists(this);
            pl = PlayList.getInstance();
            if (pl == null || pl.getZikFile() == null) {
                loadFileKO();
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
        media.updateState(PlaybackStateCompat.STATE_BUFFERING, 0, 0f, playbackStateCompatAction);
        ZikFile cur = getCurrentZikFile(); // should be zf, but stay defensive
        if (cur != null) {
            media.setMetadata(
                    cur.getDisplayName(),
                    cur.getFolderName(),
                    cur.getFolderName(),
                    0L,
                    null
            );
        }
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
            broadcastUiState();

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
        if (lastUiState == null) return true;
        return lastUiState.trackId != wantId;
    }

    public void playAudio() {
        myLog("playAudio() - start");

        if (suppressMiniUntilNextPlay) {
            suppressMiniUntilNextPlay = false;
            broadcastUiState();
        }

        if (engine == null || needsReloadForPlaylist()) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                directPlay = true;
                loadFile();
            });
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


    public void pauseAudioNoSave() {
        if (engine != null && engine.isPlaying()) {
            enginePause();
            focus.abandon();
            sleepTimer.stop();
            showForegroundNotification(false);
            broadcastUiState();
        }
    }

    public void pauseAudio() {
        if (engine != null && engine.isPlaying()) {
            enginePause();
            updateZikFileStateInDB(false);
            focus.abandon();
            sleepTimer.stop();
            showForegroundNotification(false);
            broadcastUiState();
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
        int temp = getPosition();
        if ((temp + lag) <= getDuration()) {
            setPosition(temp + lag);
        }
    }

    public void backwardAudio() {
        backwardAudio(Option.get_ForwardSeconds() * 1000);
    }

    public void backwardAudio(int lag) {
        myLog("backwardAudio() : " + lag);
        int temp = getPosition();
        if ((temp - lag) > 0) {
            setPosition(temp - lag);
        }
    }

    /********************************************************************************
     ***       SPEED - POSITION
     ********************************************************************************
     */

    public void setPosition(int position) {
        myLog("setPosition() : " + myDF.format(position));
        if (engine != null) {
            engine.seekTo(position);
            updatePlaybackStateForPosition();
            broadcastUiState();
        }
    }

    public int getPosition() {
        int pos = engine != null ? engine.getCurrentPosition() : 0;
        if (LOG_TRACE_ALL && PlayList.getInstance() != null && PlayList.getInstance().getZikFile() != null) {
            int curPosGlobalVar = (int) PlayList.getInstance().getZikFile().getPosition();
            int diff = curPosGlobalVar - pos;
            myLogD("getPosition() Saved/EngineCurrent  " + curPosGlobalVar + "/" + pos + "  -  Diff = " + diff);
        }
        return pos;
    }

    public int getDuration() {
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
            myLogEE(e, "AudioService Error setting Speed");
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

    /********************************************************************************
     ***       TIMER
     ********************************************************************************
     */
    public void updateSleepTimer(int customSleepTime) {
        this.customSleepTime = customSleepTime;
        if (sleepTimer != null) {
            int minutes = (customSleepTime == 0) ? Option.getTimeBeforeSleep() : customSleepTime;
            sleepTimer.reload(minutes);
        }
    }

    public void reloadSleepTimer() {
        if (sleepTimer != null && sleepTimer.isRunning()) {
            int minutes = (customSleepTime == 0) ? Option.getTimeBeforeSleep() : customSleepTime;
            sleepTimer.reload(minutes);
        }
    }

    public int getCustomSleepTime() {
        return customSleepTime;
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
        if (radioMode) return;
        ZikFile zf = getCurrentZikFile();
        if (zf == null) {
            myLogEE(null, "updateZikFileState : currentZikFile = null");
            return;
        }
        try {
            int pos = bFinished ? (int) zf.getDuration() : getPosition();
            int dur = bFinished ? (int) zf.getDuration() : getDuration();
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
        media.updateState(PlaybackStateCompat.STATE_PAUSED,
                engine != null ? engine.getCurrentPosition() : 0, 0f, playbackStateCompatAction);
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
        myLog("loadFileKO");
        FirebaseAnalyticsHelper.tellAnalyticsLoadFileKO(strFilePathError);
        LocalBroadcastManager.getInstance(AudioService.this)
                .sendBroadcast(new Intent(NOTIFICATION_FILENOTFOUND));
        ErrorLoadingFile = true;
        shutdown(false);
    }

    private void onEnginePrepared() {
        myLogD("onEnginePrepared()");

        setUiPhase(Intents.PHASE_READY, null);

        if (!radioMode) {
            try {
                int saved = getSavedResumePosition();
                myLogD(getCurrentZikFile().getName() + " - savedPosition = " + saved);
                if (engine != null && saved > 0) {
                    engine.seekTo(saved);
                }
                justAdvancedToNext = false;
            } catch (Exception e) {
                myLogEE(e, "seekTo(saved) in onEnginePrepared");
            }

            if (engine != null) {
                ZikFile z = getCurrentZikFile();
                if (z != null) {
                    media.setMetadata(
                            z.getDisplayName(),            // title
                            z.getFolderName(),             // artist (or podcast show)
                            z.getFolderName(),             // album (or same as folder)
                            engine.getDuration(),
                            /* art */ null                 // optionally load a Bitmap
                    );
                }
            }
        }

        // Only send READY when engine.isReady()==true
        sendReadyToPlay("onEnginePrepared");

        if (directPlay) {
            startPlayWithEngine();
        } else {
            //TODO usefull ?
            media.updateState(
                    PlaybackStateCompat.STATE_PAUSED,
                    engine != null ? engine.getCurrentPosition() : 0,
                    0f,
                    playbackStateCompatAction
            );
            // paused/ready state: show a paused notification
            showForegroundNotification(false);
        }
    }

    private void onEngineCompletion() {
        myLogD("onEngineCompletion()");
        if (!ErrorLoadingFile) {
            updateZikFileStateInDB(true);
            alertTrackFinished();
            if (PlayList.getInstance().isLastTrack()) {
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
        myLogEE(null, "Engine FATAL: " + msg + " (" + what + "," + extra + ")");
        ErrorLoadingFile = true;
        sleepTimer.stop();
        alertError(null, null);
    }


    private int getSavedResumePosition() {
        ZikFile z = getCurrentZikFile();
        if (z == null) return 0;
        int pos = (int) z.getPosition();
        int dur = (int) z.getDuration();
        if (dur > 0) pos = Math.max(0, Math.min(pos, dur));
        return pos;
    }

    public boolean isReadyToPlay() {
        return engine != null && engine.isReady();
    }

    public void pingUi() {
        broadcastUiState();
    }

    public boolean isTtsMode() {
        return engine instanceof TtsEngine;
    }

    public String getCurrentTtsVoiceName() {
        if (isTtsMode()) {
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
        if (engine == null) return;
        boolean playing = engine.isPlaying();
        int state = playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        media.updateState(state,
                engine.getCurrentPosition(),
                playing ? (float) getSpeed() : 0f,
                playbackStateCompatAction);
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
        myLogD("setPositionPlayStart()");
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

            //max reach ?, reset to 0
            //if (zikFile.getPosition() >= zikFile.getDuration()) {
            if (engine != null && engine.getCurrentPosition() >= engine.getDuration()) {
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
                int position = getPosition();
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
    }

    private void broadcastPhase(@NonNull String phase, @Nullable String msg) {
        Intent i = new Intent(Intents.ACTION_UI_STATE)
                .putExtra(Intents.EXTRA_UI_PHASE, phase)
                .putExtra(Intents.EXTRA_UI_PHASE_MSG, msg);
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
    }

    private void playRadioStream(@NonNull String url, @NonNull String title, @Nullable String imageUrl) {
        myLogI("playRadioStream: " + title + " -> " + url);

        // Mark radio mode + meta
        radioMode = true;
        radioTitle = title;
        radioImageUrl = imageUrl;
        radioUri = Uri.parse(url);
        suppressMiniUntilNextPlay = false;
        broadcastUiState();                  // first snapshot (BUFFERING)

        // Swap engine to Exo for radio
        engineGen++;
        long gen = engineGen;
        PlayerEngine fresh = new ExoPlayerEngine(getApplicationContext(), engineCb, gen);
        setEngine(fresh);
        ErrorLoadingFile = false;

        // Update MediaSession to BUFFERING with radio meta
        media.updateState(PlaybackStateCompat.STATE_BUFFERING, 0, 0f, playbackStateCompatAction);
        media.setMetadata(
                /* title   */ title,
                /* artist  */ getString(R.string.live_radio),
                /* album   */ title,
                /* durMs   */ 0L,
                /* artBmp  */ null
        );
        showForegroundNotification(false); // shows paused/buffering style

        try {
            engine.reset();
            engine.setDataSource(this, radioUri, title);
            engine.prepareAsync();

            // Broadcast a first UI state (pos/dur 0)
            broadcastUiState();
            // Auto-play when ready
            directPlay = true;

        } catch (Exception e) {
            myLogEE(e, "playRadioStream setDataSource/prepareAsync failed");
            alertError(null, null);
        }
    }

}

