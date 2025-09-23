package com.driot.bookplayer.services;

import com.driot.bookplayer.R;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media.session.MediaButtonReceiver;

import com.driot.bookplayer.helpers.TtsHelper;
import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.player.EngineListener;
import com.driot.bookplayer.player.MediaPlayerEngine;
import com.driot.bookplayer.player.PlaybackUiState;
import com.driot.bookplayer.player.PlayerEngine;
import com.driot.bookplayer.player.TtsEngine;
import com.driot.bookplayer.utils.AppTtsManager;
import com.driot.bookplayer.utils.log.LoggingService;
import com.driot.bookplayer.activities.PlayActivity;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.objects.PlayList;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Pref;

import java.io.File;
import java.text.DecimalFormat;

import static com.driot.bookplayer.utils.Tonio.fileExists;
import static com.driot.bookplayer.utils.Tonio.formatTime;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 01/11/20
 */

public class AudioService extends LoggingService {

    public static volatile boolean isRunning = false;
    private static final String ID_NOTIFICATION_PLAY_AUDIO_CHANNEL = "audio_channel_of_bookplayer";
    private static final int ID_NOTIFICATION_PLAY_AUDIO_INT = 2;

    public static final String NOTIFICATION_TTS_RANGE = "NOTIFICATION_TTS_RANGE";
    public static final String EXTRA_TTS_START = "EXTRA_TTS_START";
    public static final String EXTRA_TTS_END   = "EXTRA_TTS_END";

    public static final String EXTRA_UI_SUPPRESS_MINI = "extra_ui_suppress_mini";
    public static final String ACTION_UI_STATE      = "com.driot.bookplayer.action.UI_STATE";
    public static final String EXTRA_UI_PLAYING     = "extra_ui_playing";
    public static final String EXTRA_UI_POS         = "extra_ui_pos";
    public static final String EXTRA_UI_DUR         = "extra_ui_dur";
    public static final String EXTRA_UI_TITLE       = "extra_ui_title";
    public static final String EXTRA_UI_SUBTITLE    = "extra_ui_subtitle";
    public static final String ACTION_CMD = "com.driot.bookplayer.action.CMD";
    public static final String EXTRA_CMD  = "extra_cmd";
    public static final String CMD_PAUSE_AND_SUPPRESS = "pause_and_suppress";


    public static volatile com.driot.bookplayer.player.PlaybackUiState lastUiState = null;

    //Play Timer (for Sleep)
    public static final int DELAY_CHECK_TIMER_SLEEP = 1000;
    private Handler sleepCheckHandler;
    private int customSleepTime = 0;

    //Pause Timer (to free memory)
    public static final int TRIM_MEMORY_THRESHOLD = 20;
    public static final int DELAY_CHECK_TIMER_PAUSE = 60*1000;
    public static final int TRIM_AFTER_PAUSE_MS = 7*24*60*60*1000; // so basically never... 7 days
    //public static final int DELAY_CHECK_TIMER_PAUSE = 2*1000;
    //public static final int TRIM_AFTER_PAUSE_MS = 5*1000; // so basically never... 7 days
    private Handler pauseCheckHandler;

    public static final int[][] REWIND_AFTER_PAUSE = {  // stopped listening since (in min)  ,  rewind delay (in ms)
            {2, 3000},
            {30, 5000},
            {60*12, 10000},
            {60*36, 15000},
            {60*24*3, 20000},
            {60*24*30, 30000},
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

    private final IBinder binder = new BackgroundBinder();
    public static final String TRACKNUMBER = "tracknumber";
    public static final String FROM = "from";
    public static final String ERR_MSG = "err_msg";
    public static final String TIMER_VALUE = "TIMER_VALUE";
    public static final String READY_TO_PLAY = "NOTIFICATION_FILELOADED";
    public static final String NOTIFICATION_NEWTRACK = "NOTIFICATION_NEWTRACK";
    public static final String NOTIFICATION_TRACKFINISHED = "NOTIFICATION_TRACKFINISHED";
    public static final String NOTIFICATION_FILENOTFOUND = "NOTIFICATION_FILENOTFOUND";
    public static final String NOTIFICATION_ERROR = "NOTIFICATION_ERROR";
    public static final String NOTIFICATION_AUDIOFOCUS_LOST = "NOTIFICATION_AUDIOFOCUS_LOST";
    public static final String NOTIFICATION_AUDIOFOCUS_GAIN = "NOTIFICATION_AUDIOFOCUS_GAIN";
    public static final String NOTIFICATION_ZIP_FILE_LOADED = "NOTIFICATION_ZIP_FILE_LOADED";
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
    private final EngineListener engineCb = new EngineListener() {
        @Override public void onPrepared(long gen) {
            if (gen != engineGen) return;
            onEnginePrepared(); // your existing method
        }
        @Override public void onCompletion(long gen) {
            if (gen != engineGen) return;
            onEngineCompletion();
        }
        @Override public void onError(long gen, String msg, int what, int extra) {
            if (gen != engineGen) return;
            onEngineError(msg, what, extra);
        }
        @Override public void onFatal(long gen, String msg, int what, int extra) {
            if (gen != engineGen) return;
            onEngineFatal(msg, what, extra);
        }
        @Override public void onTtsRange(long gen, int s, int e) {
            if (gen != engineGen) return;
            Intent i = new Intent(NOTIFICATION_TTS_RANGE)
                    .putExtra(EXTRA_TTS_START, s)
                    .putExtra(EXTRA_TTS_END, e);
            LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(i);
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
    public void suppressMiniUntilNextPlay() {
        suppressMiniUntilNextPlay = true;
        broadcastUiState(); // so observers hide immediately
    }
    public boolean isMiniSuppressed() { return suppressMiniUntilNextPlay; }

    private void broadcastUiCleared() {
        lastUiState = null;
        Intent i = new Intent(ACTION_UI_STATE)
                .putExtra(EXTRA_UI_PLAYING,  false)
                .putExtra(EXTRA_UI_POS,      0)
                .putExtra(EXTRA_UI_DUR,      0)
                .putExtra(EXTRA_UI_TITLE,    "")
                .putExtra(EXTRA_UI_SUBTITLE, "")
                .putExtra(EXTRA_UI_SUPPRESS_MINI, true);
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
    }

    private void broadcastUiState() {
        PlaybackUiState s = buildUiState();
        lastUiState = s;
        Intent i = new Intent(ACTION_UI_STATE)
                .putExtra(EXTRA_UI_PLAYING,  s.playing)
                .putExtra(EXTRA_UI_POS,      s.positionMs)
                .putExtra(EXTRA_UI_DUR,      s.durationMs)
                .putExtra(EXTRA_UI_TITLE,    s.title)
                .putExtra(EXTRA_UI_SUBTITLE, s.subTitle)
                .putExtra(EXTRA_UI_SUPPRESS_MINI, suppressMiniUntilNextPlay);
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
    }

    private com.driot.bookplayer.player.PlaybackUiState buildUiState() {
        ZikFile z = getCurrentZikFile();
        String title = (z != null) ? z.getFolderName()  : "";
        String text  = (z != null) ? z.getDisplayName() : "";
        int pos = (engine != null) ? engine.getCurrentPosition() : 0;
        int dur = (engine != null) ? engine.getDuration() : 0;
        boolean playing = (engine != null) && engine.isPlaying();
        return new com.driot.bookplayer.player.PlaybackUiState(playing, pos, dur, title, text);
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
                sleepCheckHandler, DELAY_CHECK_TIMER_SLEEP,
                new com.driot.bookplayer.player.SleepTimer.Listener() {
                    @Override public void onTick(int elapsedSeconds) {
                        updateZikFileStateInDB(false);
                        Intent i = new Intent(NOTIFICATION_PLAYBACK_TIMER_VALUE)
                                .putExtra(TIMER_VALUE, elapsedSeconds);
                        LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(i);
                    }
                    @Override public void onReachedMax() {
                        if (Option.getBeepAutoStop()) playBeep("2beeps");
                        LocalBroadcastManager.getInstance(AudioService.this)
                                .sendBroadcast(new Intent(NOTIFICATION_PLAYBACK_MAXTIMEREACH));
                        if (engine != null && engine.isPlaying()) mediaPlayerStop();
                        stopSelf();
                    }
                });

        // Pause/trim watcher (kills service if paused too long)
        pauseCheckHandler = new Handler();
        pauseWatcher = new com.driot.bookplayer.player.PauseTrimWatcher(
                pauseCheckHandler, DELAY_CHECK_TIMER_PAUSE,
                new com.driot.bookplayer.player.PauseTrimWatcher.Killer() {
                    @Override public void kill() { killService(); }
                    @Override public void onLog(String msg) { myLogD(msg); }
                },
                System::currentTimeMillis,
                Pref::getPauseTime,
                TRIM_AFTER_PAUSE_MS);
        pauseWatcher.start();

        // Audio focus
        focus = new com.driot.bookplayer.player.AudioFocusHelper(
                this,
                new com.driot.bookplayer.player.AudioFocusHelper.Listener() {
                    @Override public void onFocusLost() {
                        myLogI("Audio Focus Lost");
                        pauseAudio();
                        LocalBroadcastManager.getInstance(AudioService.this)
                                .sendBroadcast(new Intent(NOTIFICATION_AUDIOFOCUS_LOST));
                    }
                    @Override public void onFocusGain() {
                        myLogI("Audio Focus Gain");
                        playAudio();
                        media.setActive(true);
                        LocalBroadcastManager.getInstance(AudioService.this)
                                .sendBroadcast(new Intent(NOTIFICATION_AUDIOFOCUS_GAIN));
                    }
                });

        // Progress updater (DB)
        progress = new com.driot.bookplayer.player.PlaybackProgressUpdater(
                getApplicationContext(),
                new com.driot.bookplayer.player.PlaybackProgressUpdater.Logger() {
                    @Override public void d(String m) { myLogD(m); }
                    @Override public void e(String m) { myLogE(m); }
                    @Override public void ee(Throwable t, String m) { myLogEE(t, m); }
                });

        myLogD("onCreate() - END");
    }
// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void showForegroundNotification(boolean playing) {
        ZikFile zf = getCurrentZikFile();
        CharSequence title = zf == null ? "---" : zf.getFolderName();
        CharSequence text  = zf == null ? "---" : zf.getDisplayName();

        Notification n = notif.build(
                media.session(),
                playing,
                title,
                text,
                new com.driot.bookplayer.player.PlaybackNotificationManager.ActionProvider() {
                    @Override public PendingIntent rewind()      { return MediaButtonReceiver.buildMediaButtonPendingIntent(AudioService.this, PlaybackStateCompat.ACTION_REWIND); }
                    @Override public PendingIntent play()        { return MediaButtonReceiver.buildMediaButtonPendingIntent(AudioService.this, PlaybackStateCompat.ACTION_PLAY); }
                    @Override public PendingIntent pause()       { return MediaButtonReceiver.buildMediaButtonPendingIntent(AudioService.this, PlaybackStateCompat.ACTION_PAUSE); }
                    @Override public PendingIntent fastForward() { return MediaButtonReceiver.buildMediaButtonPendingIntent(AudioService.this, PlaybackStateCompat.ACTION_FAST_FORWARD); }
                    @Override public PendingIntent content() {
                        final int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

                        androidx.core.app.TaskStackBuilder tsb =
                                androidx.core.app.TaskStackBuilder.create(AudioService.this);

                        // Always start at Main
                        tsb.addNextIntent(new Intent(AudioService.this, com.driot.bookplayer.activities.MainActivity.class));

                        // If this book has multiple tracks, add the track list before Play
                        com.driot.bookplayer.objects.PlayList pl = com.driot.bookplayer.objects.PlayList.getInstance();
                        if (pl != null && pl.getSize() > 1) {
                            // Prefer passing the Folder object if it's Parcelable/Serializable; else pass folderId
                            Intent trackList = new Intent(AudioService.this, com.driot.bookplayer.activities.ZikFileActivity.class);
                            trackList.putExtra("folder", pl.getFolder()); // if your Folder is Serializable/Parcelable
                            tsb.addNextIntent(trackList);
                        }

                        // Finally PlayActivity
                        tsb.addNextIntent(new Intent(AudioService.this, com.driot.bookplayer.activities.PlayActivity.class)
                                .putExtra(com.driot.bookplayer.activities.PlayActivity.EXTRA_AUTOPLAY, false));

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
        // Safety net: ensure mini is unsuppressed when actually starting.
        if (suppressMiniUntilNextPlay) {
            suppressMiniUntilNextPlay = false;
            // We'll broadcast right after start so UI updates
        }

        // Audio focus first
        focus.request();

        // Rewind-after-pause
        if (Option.getRewindAfterPause()) {
            ZikFile currentZik = PlayList.getInstance().getZikFile();
            if (currentZik != null && currentZik.lLastAccess != null) {
                long minutes = (System.currentTimeMillis() - currentZik.lLastAccess) / (60 * 1000);
                int rewindMs = 0;
                for (int[] rule : REWIND_AFTER_PAUSE) { if (minutes >= rule[0]) rewindMs = rule[1]; else break; }
                if (rewindMs > 0) { myLogD("Rewind after Pause: " + (rewindMs/1000) + "s"); backwardAudio(rewindMs); }
            }
        }

        doIntroCut();
        myLogD("about to call engine.start()");
        logPauseTime();

        engine.start();
        Pref.setPauseTime(0);

        media.updateState(
                PlaybackStateCompat.STATE_PLAYING,
                engine.getCurrentPosition(),
                (float) getSpeed(),
                playbackStateCompatAction);
        engine.setSpeed((float) getSpeed());
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
        justAdvancedToNext = true;
        PlayList.getInstance().nextTrack();
        if (engine != null) {
            try {
                if (engine instanceof TtsEngine) {
                    ((TtsEngine) engine).release();
                }
                engine.stop();
                engine.reset();
            } catch (Exception ignored) {}
        }

        myLog("loading next track : n°" + PlayList.getInstance().getNumSlashTotal());
        if (Option.getBeepChapter()) playBeep("1beep");
        directPlay = true;
        loadFile();
        alertNewTrack();
    }

    private void alertNewTrack() {
        myLog("alertNewTrack()");
        LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_NEWTRACK).putExtra(TRACKNUMBER, PlayList.getInstance().getNumZikFile()));
        ZikFile z = getCurrentZikFile();
        if (z != null) {
            media.updateState(PlaybackStateCompat.STATE_BUFFERING, 0, 0f, playbackStateCompatAction);
            media.setMetadata(z.getDisplayName(), z.getFolderName(), z.getFolderName(), 0L, null);
            showForegroundNotification(isPlaying());
        }
    }

    private void alertError(String from, String errMsg) {
        LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_ERROR)
                .putExtra(TRACKNUMBER, PlayList.getInstance().getNumZikFile())
                .putExtra(FROM, from)
                .putExtra(ERR_MSG, errMsg)
        );
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
        if (intent != null && ACTION_CMD.equals(intent.getAction())) {
            String cmd = intent.getStringExtra(EXTRA_CMD);
            if (CMD_PAUSE_AND_SUPPRESS.equals(cmd)) {
                myLog("stopping audio");
                pauseAudio();
                suppressMiniUntilNextPlay();
                broadcastUiCleared();
                stopForeground(false);
                stopSelf();
            }
            return START_STICKY;
        }

        // (existing) route media buttons etc.
        if (intent != null) {
            MediaButtonReceiver.handleIntent(media.session(), intent);
        }
        showForegroundNotification(isPlaying());
        return START_STICKY;
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
        pauseAudio();
        suppressMiniUntilNextPlay();
        broadcastUiCleared();
        stopForeground(false);
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        myLog("onDestroy()");
        // Send cleared first so observers hide mini even if process dies right after
        broadcastUiCleared();                     // ← ensure cleared

        isRunning = false;
        sleepTimer.stop();
        stopForeground(true);

        if (engine != null) {
            try {
                if (engine instanceof TtsEngine) ((TtsEngine) engine).release();
                engine.stop();
            } catch (Exception ignored) {}
        }

        focus.abandon();
        notif.cancel(ID_NOTIFICATION_PLAY_AUDIO_INT);
        if (media != null) media.release();

        stopSelf();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        myLog("onBind()");
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

    /** Swap current engine with a new one, releasing TTS if needed, keeping flags intact. */
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
        } catch (Throwable ignored) {}
        engine = newEngine;
    }

    /** Simplified, side-effect-free loader. */
    public void loadFile() {
        myLogD("loadFile()  directPlay=" + directPlay);

        // Ensure we actually have something to play
        PlayList pl = PlayList.getInstance();
        if (pl == null || pl.getZikFile() == null) {
            // Try to restore once
            PlayList.restoreIfExists(this);
            pl = PlayList.getInstance();
            if (pl == null || pl.getZikFile() == null) {
                loadFileKO();
                return;
            }
        }

        ZikFile zf = pl.getZikFile();

        // Decide engine type from display or path
        final boolean isText =
                (zf.getPath()!=null && zf.getPath().toLowerCase().endsWith(".txt")) ||
                        (zf.getDisplayName()!=null && zf.getDisplayName().toLowerCase().endsWith(".txt"));

        // Resolve Uri
        Uri src = UriHelper.resolvePlayableUri(this, zf);
        if (src == null) {
            myLogEE(null, "resolvePlayableUri failed for: " + zf.getPath());
            loadFileKO();
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
            engine.prepareAsync();

            // Optional: broadcast current title/pos (dur likely 0 → mini remains hidden).
            // This “primes” the UI with labels without forcing visibility.
            broadcastUiState();

        } catch (Exception e) {
            myLogEE(e, "loadFile: setDataSource/prepareAsync failed");
            loadFileKO();
        }
    }


    /********************************************************************************
     ***       PLAY-PAUSE
     ********************************************************************************
     */

    public void playAudio() {
        myLog("playAudio() - start");
        // If user explicitly plays, we want the mini back
        if (suppressMiniUntilNextPlay) {
            suppressMiniUntilNextPlay = false;
            broadcastUiState();
        }

        if (engine == null) {
            directPlay = true;
            loadFile();
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
            // Don't arm directPlay while TTS is switching language
            boolean ttsSwitching = (engine instanceof TtsEngine) && !engine.isReady();
            if (!ttsSwitching) {
                directPlay = true;
            }
        }
    }

    private void doIntroCut() {
        myLog("doIntroCut");
        int introCut = 0;
        try {
            if (PlayList.getInstance().getZikFile()!=null) {
                introCut = Pref.getIntroCutFromPref(this,PlayList.getInstance().getZikFile().getIdFolder()) * 1000;
            }
        } catch (Exception e) {
            myLogEE(e, "Error getting introCut from Pref - getIdFolder null ?");
        }
        if (introCut > 0) {
            int position = getPosition();
            myLog("position : [" + position + "]  introCut : [" + introCut + "]");
            if (position < introCut) {
                forwardAudioTo(introCut);
                myLogI("=> Intro Cut");
            }
        }
    }

    public void pauseAudioNoSave() {
        if (engine != null && engine.isPlaying()) {
            mediaPlayerPause();
            focus.abandon();
            sleepTimer.stop();
            showForegroundNotification(false);
            broadcastUiState();
        }
    }
    public void pauseAudio() {
        if (engine != null && engine.isPlaying()) {
            mediaPlayerPause();
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
        forwardAudio(Option.get_ForwardSeconds()*1000);
    }
    public void forwardAudio(int lag) {
        myLog("forwardAudio of " + lag);
        int temp = getPosition();
        if ((temp + lag ) <= getDuration()) {
            setPosition(temp + lag );
        }
    }
    public void forwardAudioTo(int lag) {
        myLog("forwardAudio to " + lag);
        if (lag <= getDuration()) {
            setPosition(lag);
        }
    }
    public void backwardAudio() {
        backwardAudio(Option.get_ForwardSeconds()*1000);
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
        if (LOG_TRACE_ALL && PlayList.getInstance()!=null && PlayList.getInstance().getZikFile()!=null) {
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
            if (engine != null && engine.isPlaying()) engine.setSpeed((float) speed);
            myLog("setSpeed() : " + speed);
        } catch (Exception e) { myLogEE(e,"AudioService Error setting Speed"); }
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

    public int getCustomSleepTime() { return customSleepTime; }


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
            myLogD("Paused since " + formatTime(pauseTime, true) + ".   MAX is " + formatTime(TRIM_AFTER_PAUSE_MS,false, false));
        }
    }


    /********************************************************************************
     ***       UPDATE DB
     ********************************************************************************
     */
    private void updateZikFileStateInDB(boolean bFinished) {
        ZikFile zf = getCurrentZikFile();
        if (zf==null) {
            myLogEE(null, "updateZikFileState : currentZikFile = null");
            return;
        }
        try {
            int pos = bFinished ? (int) zf.getDuration() : getPosition();
            int dur = bFinished ? (int) zf.getDuration() : getDuration();
            progress.update(zf, bFinished, pos, dur);
        } catch (Exception e) {
            myLogEE(e,"updateZikFileStateInDB");
        }
    }


    /********************************************************************************
     ***       NOTIFICATIONS
     ********************************************************************************
     */


    private void mediaPlayerPause() {
        myLogD("mediaPlayerPause()");
        if (engine != null) engine.pause();
        media.updateState(PlaybackStateCompat.STATE_PAUSED,
                engine != null ? engine.getCurrentPosition() : 0, 0f, playbackStateCompatAction);
        Pref.setPauseTime();
    }

    private void mediaPlayerStop() {
        myLogD("mediaPlayerStop()");
        if (engine != null) engine.stop();
        media.updateState(PlaybackStateCompat.STATE_STOPPED, 0, 0f, playbackStateCompatAction);
        media.setActive(false);
        if (Pref.getPauseTime() == 0) Pref.setPauseTime();
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
            myLogEE(e,"playBeep(" + beepType + ")");
        }
    }

    private void killService() {
        myLogI("killService()");
        broadcastUiCleared();
        isRunning = false;
        sleepTimer.stop();

        if (engine != null) {
            try {
                if (engine instanceof TtsEngine) ((TtsEngine) engine).release();
                engine.stop();
            } catch (Exception ignored) {}
        }

        focus.abandon();
        notif.cancel(ID_NOTIFICATION_PLAY_AUDIO_INT);
        stopForeground(true);
        stopSelf();
    }

    private void loadFileKO() {
        myLog("loadFileKO");
        LocalBroadcastManager.getInstance(AudioService.this)
                .sendBroadcast(new Intent(NOTIFICATION_FILENOTFOUND));
        ErrorLoadingFile = true;
        broadcastUiCleared();
        notif.cancel(ID_NOTIFICATION_PLAY_AUDIO_INT);
        stopForeground(true);
        stopSelf();
    }

    private void onEnginePrepared() {
        myLogD("engine prepared");

        try {
            int saved = getSavedResumePosition();
            myLogD(getCurrentZikFile().getName() + " - savedPosition = " + saved);
            boolean startAtZero = Option.getStartAtZeroNextTrack() && justAdvancedToNext;
            justAdvancedToNext = false;

            if (!startAtZero && engine != null && saved > 0) {
                engine.seekTo(saved);
                myLogD("Seeked to saved position: " + saved + " ms");
            }
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

        // Only send READY when engine.isReady()==true
        sendReadyToPlay("onEnginePrepared");

        if (directPlay) {
            startPlayWithEngine();
        } else {
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
                sleepTimer.stop();
                Pref.setPauseTime();
            } else {
                nextTrack();
            }
        }
    }


    private void onEngineError(String msg, int what, int extra) {
        myLogEE(null,"Engine error: " + msg + " (" + what + "," + extra + ")");
        ErrorLoadingFile = true;
        sleepTimer.stop();
        if (msg.startsWith("TTS")) {
            alertError("TTS", msg);
        } else {
            alertError(null, null);
        }
    }

    private void onEngineFatal(String msg, int what, int extra) {
        myLogEE(null,"Engine FATAL: " + msg + " (" + what + "," + extra + ")");
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

    public void pingUi() { broadcastUiState(); }

    public boolean isTtsMode() {
        return engine instanceof TtsEngine;
    }

    public @Nullable String getTtsText() {
        if (engine instanceof TtsEngine) {
            return ((TtsEngine) engine).getText();
        }
        return null;
    }

    public void setTtsVoiceByNameAndWarmUp(String voiceName, long timeoutMs, TtsEngine.WarmupCallback cb) {
        if (!(engine instanceof TtsEngine)) { cb.onResult(false, TtsHelper.ERROR); return; }
        myLogD("setTtsVoiceByNameAndWarmUp : " + voiceName);
        engine.pause();
        ((TtsEngine) engine).setVoiceByNameAndWarmUp(voiceName, timeoutMs, (ready, reason) -> {
            myLogD("Warmup result ready=" + ready + " reason=" + reason);
            cb.onResult(ready, reason);
            // You decide when to resume; often resume only if ready==true
            if (ready) {
                myLog("ready, resuming");
                //engine.resume();
            }
        });
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
}