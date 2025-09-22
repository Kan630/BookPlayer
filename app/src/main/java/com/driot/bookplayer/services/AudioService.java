package com.driot.bookplayer.services;

import com.driot.bookplayer.R;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
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

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media.session.MediaButtonReceiver;

import com.driot.bookplayer.helpers.TtsHelper;
import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.player.EngineListener;
import com.driot.bookplayer.player.MediaPlayerEngine;
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
import java.util.Objects;

import static com.driot.bookplayer.activities.PlayActivity.SHARED_PREFERENCE_SPEED;
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
    long playbackStateCompatAction = PlaybackStateCompat.ACTION_PLAY |
            PlaybackStateCompat.ACTION_PAUSE |
            PlaybackStateCompat.ACTION_STOP |
            PlaybackStateCompat.ACTION_REWIND |
            PlaybackStateCompat.ACTION_FAST_FORWARD |
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
            PlaybackStateCompat.ACTION_PLAY_PAUSE
            ;



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
            // not needed anymore
            //if (ke != null && ke.getAction() == KeyEvent.ACTION_DOWN) handleKeyEvent(ke.getKeyCode());
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
            super.onSeekTo(pos);
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
                () -> Pref.getPauseTime(),
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
                        Intent open = new Intent(AudioService.this, PlayActivity.class)
                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        return PendingIntent.getActivity(AudioService.this, 0, open,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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
    }

    void nextTrack() {
        myLog("Next track");
        PlayList.getInstance().nextTrack();
        if (engine != null) {
            try {
                if (engine instanceof TtsEngine) ((TtsEngine) engine).release();
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
        LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_NEWTRACK).putExtra(TRACKNUMBER, PlayList.getInstance().getNumZikFile()));
        //createNotification();
        myLog("sendBroadcast alertNewTrack ");
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

    private void handleKeyEvent(int keyCode) {
        switch (keyCode) { //now only for click on Notification
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                myLog("KEYCODE_MEDIA_REWIND pressed");
                // Handle the rewind action
                backwardAudio();
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                myLog("KEYCODE_MEDIA_PLAY pressed");
                // Handle the play action
                playPauseAudio();
                break;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                myLog("KEYCODE_MEDIA_PAUSE pressed");
                // Handle the pause action
                playPauseAudio();
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                myLog("KEYCODE_MEDIA_PLAY_PAUSE pressed");
                // Handle the pause action
                playPauseAudio();
                break;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                myLog("KEYCODE_MEDIA_FAST_FORWARD pressed");
                // Handle the fast forward action
                forwardAudio();
                break;
            case KeyEvent.KEYCODE_HEADSETHOOK:
                myLog("KEYCODE_HEADSETHOOK pressed");
                playPauseAudio();
                break;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                myLog("KEYCODE_MEDIA_NEXT pressed");
                forwardAudio();
                break;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                myLog("KEYCODE_MEDIA_PREVIOUS pressed");
                backwardAudio();
                break;
            // Add other cases for additional key codes as needed
            default:
                myLogE("Unknown key code: " + keyCode);
                break;
        }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        myLog("onStartCommand()"); //is called when user press icons buttons on Notification
        if (intent != null) {
            myLog("onStartCommand() - " + intent);
            if (Objects.equals(intent.getAction(), Intent.ACTION_MEDIA_BUTTON)) {
                if (intent.hasExtra(Intent.EXTRA_KEY_EVENT)) {
                    KeyEvent keyEvent = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                    if (keyEvent != null) {
                        int keyCode = keyEvent.getKeyCode();
                        handleKeyEvent(keyCode);
                    }
                }
            }
        }
        return START_STICKY; // usually better for audio playback service
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        myLog("onDestroy()");
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

    // TODO, use openFileDescriptor & remove legacy from manifest

    //called in :
    //* PlayActivity
    //* onPrepare
    //* onError
    public void loadFile() {
        myLogD("loadingFile.......  - Play Audio straight away : " + directPlay);

        if (PlayList.getInstance()==null || PlayList.getInstance().getZikFile()==null) { loadFileKO(); return; }
        ZikFile zf = PlayList.getInstance().getZikFile();

        Uri uriToPlay = null;
        String pathToPlay = null;

        // --- Your existing SAF/file/path resolution (kept) ---
        if (zf.getPath().startsWith("content://")) {
            myLog("New SAF file, content...");
            uriToPlay = UriHelper.buildFileUri(this, zf.getPath(), zf.getName());
            if (uriToPlay == null) { myLogEE(null,"buildFileUri returned null"); loadFileKO(); return; }
            DocumentFile file = DocumentFile.fromSingleUri(this, uriToPlay);
            if (!file.exists() || !file.isFile()) {
                myLogD("Try Single file");
                uriToPlay = Uri.parse(zf.getPath());
                file = DocumentFile.fromSingleUri(this, uriToPlay);
                if (!file.exists() || !file.isFile()) {
                    myLogEE(null,"Invalid SAF Uri: " + uriToPlay);
                    loadFileKO(); return;
                }
            }
        } else if (zf.getPath().startsWith("file://")) {
            Uri fileUri = Uri.parse(zf.getPath());
            String p = fileUri.getPath();
            if (p == null) { myLogEE(null,"Invalid file:// path"); loadFileKO(); return; }
            File f = new File(p);
            if (!f.exists() || !f.isFile()) {
                File maybe = new File(p, zf.getName());
                if (!maybe.exists() || !maybe.isFile()) { myLogEE(null,"File not found: " + p); loadFileKO(); return; }
                f = maybe; fileUri = Uri.fromFile(f);
            }
            uriToPlay = fileUri;
        } else {
            pathToPlay = zf.getPath();
            if (!fileExists(pathToPlay)) {
                myLogEE(null,"File doesn't exist: " + pathToPlay);
                pathToPlay = zf.getPath() + "/" + zf.getName();
                if (!fileExists(pathToPlay)) { myLogEE(null,"Still missing: " + pathToPlay); loadFileKO(); return; }
            }
            uriToPlay = Uri.fromFile(new File(pathToPlay));
        }

        // --- Decide engine: AUDIO vs TTS (text) ---
        final boolean isText =
                (zf.getPath()!=null && zf.getPath().toLowerCase().endsWith(".txt")) ||
                        (zf.getDisplayName()!=null && zf.getDisplayName().toLowerCase().endsWith(".txt"));

        engineGen++;
        long gen = engineGen;

        if (isText) {
            engine = new TtsEngine(getApplicationContext(), AppTtsManager.get(getApplicationContext()), engineCb, gen);
        } else {
            engine = new MediaPlayerEngine(engineCb, gen);
        }

        ErrorLoadingFile = false;

        try {
            engine.reset();
            engine.setDataSource(this, uriToPlay, zf.getDisplayName());
            engine.prepareAsync();
        } catch (Exception e) {
            myLogEE(e, "ERROR loading source");
            loadFileKO();
        }
    }



    /********************************************************************************
     ***       PLAY-PAUSE
     ********************************************************************************
     */

    public void playAudio() {
        myLog("playAudio() - start");
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

    public void pauseAudio() {
        myLog("pauseAudio()");
        if (engine != null && engine.isPlaying()) {
            mediaPlayerPause();
            media.setActive(false);
            updateZikFileStateInDB(false);
            focus.abandon();
            sleepTimer.stop();
            showForegroundNotification(false);
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
        if (engine != null) engine.seekTo(position);
        //createNotification();
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
        if (zf != null) saveSpeedToPref(zf.getIdFolder(), speed);
    }


    public double getSpeed() {
        ZikFile zf = getCurrentZikFile();
        if (zf != null) speed = getSpeedFromPref(zf.getIdFolder());
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


    private void saveSpeedToPref(int idFolder, double speed) {
        try {
            SharedPreferences.Editor editor = getSharedPreferences(SHARED_PREFERENCE_SPEED, MODE_PRIVATE).edit();
            editor.putString(String.valueOf(idFolder),Double.toString(speed)).apply();
        } catch (Exception e) {
            myLogEE(e,"error saving speed in prefs");
        }
    }

    private double getSpeedFromPref(int idFolder) {
        try {
            SharedPreferences prefs = getSharedPreferences(SHARED_PREFERENCE_SPEED, MODE_PRIVATE);
            return Double.parseDouble(prefs.getString(String.valueOf(idFolder), "1.0"));
        } catch (Exception e) {
            myLogEE(e,"error getting speed from prefs");
            return 1.0;
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
        notif.cancel(ID_NOTIFICATION_PLAY_AUDIO_INT);
        stopForeground(true);
        stopSelf();
    }

    private void onEnginePrepared() {
        myLogD("engine prepared");

        try {
            int saved = getSavedResumePosition();
            if (engine != null && saved > 0) {
                engine.seekTo(saved);
                myLogD("Seeked to saved position: " + saved + " ms");
            }
        } catch (Exception e) {
            myLogEE(e, "seekTo(saved) in onEnginePrepared");
        }

        if (engine != null) {
            media.setDuration(engine.getDuration());
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
        myLogE("Engine error: " + msg + " (" + what + "," + extra + ")");
        ErrorLoadingFile = true;
        sleepTimer.stop();
        if (msg.startsWith("TTS")) {
            alertError("TTS", msg);
        } else {
            alertError(null, null);
        }
    }

    private void onEngineFatal(String msg, int what, int extra) {
        myLogE("Engine FATAL: " + msg + " (" + what + "," + extra + ")");
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


}