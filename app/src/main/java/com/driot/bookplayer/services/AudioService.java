package com.driot.bookplayer.services;

import com.driot.bookplayer.R;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
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
import android.service.notification.StatusBarNotification;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media.session.MediaButtonReceiver;

import com.driot.bookplayer.objects.KanMediaPlayer;
import com.driot.bookplayer.utils.log.LoggingService;
import com.driot.bookplayer.activities.PlayActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.ZikFileDao;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.objects.PlayList;
import com.driot.bookplayer.db.Sql;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.utils.Tonio;

import java.io.IOException;
import java.sql.Date;
import java.sql.Time;
import java.text.DecimalFormat;
import java.util.Objects;

import static com.driot.bookplayer.activities.PlayActivity.SHARED_PREFERENCE_SPEED;
import static com.driot.bookplayer.utils.FileUtils.buildFileUri;
import static com.driot.bookplayer.utils.Tonio.FormatPercentDouble;
import static com.driot.bookplayer.utils.Tonio.fileExists;
import static com.driot.bookplayer.utils.Tonio.formatTime;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 01/11/20
 */

public class AudioService extends LoggingService {

    public static volatile boolean isRunning = false;

    private static final String ID_NOTIFICATION_PLAY_AUDIO_CHANNEL = "audio_channel_of_bookplayer";
    private static final int ID_NOTIFICATION_PLAY_AUDIO_INT = 2;

    public static final int TRIM_MEMORY_THRESHOLD = 20;
    public static final int TRIM_AFTER_PAUSE_MS = 1000*60*10;
    public static final int DELAY_CHECK_TIMER = 1000;

    //Play Timer (for Sleep)
    private Handler handler;
    private Runnable timerRunnable;
    private int elapsedSeconds = 0;
    private int customSleepTime = 0;

    //Pause Timer (to free memory)
    private Handler pauseCheckHandler;
    private Runnable pauseCheckRunnable;
    private long pauseStartTimestampMs = 0;


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
    public static final String TIMER_VALUE = "TIMER_VALUE";
    public static final String NOTIFICATION_FILELOADED = "NOTIFICATION_FILELOADED";
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

    private KanMediaPlayer mediaPlayer;

    private AudioManager audioManager;
    private AudioManager.OnAudioFocusChangeListener afChangeListener;
    private MediaSessionCompat mediaSession;

    public boolean startAtZero, directPlay;


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
            mediaPlayerStop();
            stopForeground(false);
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

    private boolean isTimerRunning = false;

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

        startMemoryTrimTimer();

// ///////////////////////
//      PLAYER
// ///////////////////////

        mediaPlayer = new KanMediaPlayer();
        mediaPlayer.setListener(new KanMediaPlayer.Listener() {
            @Override
            public void onCompletion() {
                if (!ErrorLoadingFile) {
                    updateZikFileState(true);
                    alertTrackFinished();

                    if (PlayList.getInstance().isLastTrack()) {
                        myLog("mediaPlayer.OnCompletionListener  => Last track just completed !");

                        // 3 beeps
                        if (Option.getBeepBookEnd()) {
                            playBeep("3beeps");
                        }

                        alertPlaylistFinished();
                        stopSleepTimer();
                        pauseStartTimestampMs = System.currentTimeMillis();
                    } else {
                        myLog("mediaPlayer.OnCompletionListener => calling nextTrack");
                        nextTrack();
                    }
                }
            }

            @Override
            public void onPrepared() {
                myLogD("mediaPlayer.prepare - done");
                LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_FILELOADED));
                if (directPlay) {
                    startPlayWithMediaPlayer();
                } else {
                    myLogD("no direct play");
                }
            }

            @Override
            public void onError(String msg, int what, int extra) {
                myLogE("MediaPlayer onError: " + msg + " (" + what + ", " + extra + ")");
                ErrorLoadingFile = true;
            }

            @Override
            public void onFatalError(String msg, int what, int extra) {
                myLogE("MediaPlayer onFatalError: " + msg + " (" + what + ", " + extra + ")");
                ErrorLoadingFile = true;
                alertError();
            }
        });

// ///////////////////////
//      MEDIA SESSION
// ///////////////////////
        mediaSession = new MediaSessionCompat(this, "BookplayerMediaSession");
        handler = new Handler(); // for sleep timer

        myLog("configureMediaSession()");

        // Overridden methods in the MediaSession.Callback class.
        mediaSession.setCallback(callback);
        mediaSession.setActive(true); // Needed for media button handling

        myLog("onCreate() - END");
    }
// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    private void startPlayWithMediaPlayer() {
        myLogD("startPlayWithMediaPlayer");
        if (startAtZero || PlayList.getInstance().getZikFile() == null) {
            mediaPlayer.seekTo(0);
        } else {
            mediaPlayer.seekTo((int) PlayList.getInstance().getZikFile().getPosition());
        }
        audioManager = (AudioManager) AudioService.this.getSystemService(Context.AUDIO_SERVICE);
        afChangeListener = focusChange -> {
            if (focusChange <= 0) {
                myLog("Audio Focus Lost");
                AudioService.this.pauseAudio();
                //mediaSession.setActive(false); // CHECK
                Intent intent = new Intent(NOTIFICATION_AUDIOFOCUS_LOST);
                LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(intent);
            } else {
                myLog("Audio Focus Gain");
                AudioService.this.playAudio();
                mediaSession.setActive(true);
                Intent intent = new Intent(NOTIFICATION_AUDIOFOCUS_GAIN);
                LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(intent);
            }
        };

        // Rewind After Pause
        if (Option.getRewindAfterPause()) {
            if (PlayList.getInstance().getZikFile() != null) {
                Time lastAccessTime = PlayList.getInstance().getZikFile().getLastaccessTime();
                if (lastAccessTime != null) {
                    Time nowTime = new Time(System.currentTimeMillis());
                    long timeDiffMillis = nowTime.getTime() - lastAccessTime.getTime();
                    long timeDiffMinutes = timeDiffMillis / (60 * 1000);

                    int rewindDelay = 0; // default: no rewind
                    for (int[] ints : REWIND_AFTER_PAUSE) {
                        if (timeDiffMinutes >= ints[0]) {
                            rewindDelay = ints[1];
                        } else {
                            break; // stop at the first value that exceeds timeDiff
                        }
                    }

                    if (rewindDelay > 0) {
                        myLog("Rewind after Pause - last play was " + timeDiffMinutes + " minutes ago. Rewind value is " + (rewindDelay / 1000) + " seconds.");
                        backwardAudio(rewindDelay);
                    } else {
                        myLog("NO Rewind after Pause - last play was " + timeDiffMinutes + " minutes ago. No matching rewind rule found.");
                    }
                }
            }
        }
        doIntroCut();
        myLog("about to call mediaPlayer.start()...  mediaPlayer.getCurrentPosition : " + mediaPlayer.getCurrentPosition());
        mediaPlayer.start();
        pauseStartTimestampMs = 0;
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING, mediaPlayer.getCurrentPosition(), 1.0f);
        setSpeed(getSpeed());
        if (!mediaSession.isActive()) {
            mediaSession.setActive(true);
        }
        startSleepTimer();
        createNotificationChannel();
        createNotification();
    }


    void nextTrack() {
        myLog("Next track");
        PlayList.getInstance().nextTrack();
        mediaPlayer.reset();
        myLog("loading next track : n°" + PlayList.getInstance().getNumSlashTotal() );

        // petit bip
        if (Option.getBeepChapter()) playBeep("1beep");
        startAtZero = true;
        directPlay = true;
        loadFile();
        alertNewTrack();
    }

    private void alertNewTrack() {
        LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_NEWTRACK).putExtra(TRACKNUMBER, PlayList.getInstance().getNumZikFile()));
        createNotification();
        myLog("sendBroadcast alertNewTrack ");
    }

    private void alertError() {
        LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_ERROR).putExtra(TRACKNUMBER, PlayList.getInstance().getNumZikFile()));
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

        //TODO maybe to change... because memory pressure could kill it
        //return START_NOT_STICKY; //2025-06-27 - open test (v86)
        return START_STICKY; // 2025-06-24 - production
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        myLog("onDestroy()");
        stopSleepTimer();
        stopPauseTimer();
        stopForeground(true);
        if (mediaPlayer.isPlaying()) {mediaPlayerStop();}
        mediaPlayer.release();
        mediaPlayer = null;
        if (audioManager != null) { audioManager.abandonAudioFocus(afChangeListener); }
        if (mediaSession != null) { mediaSession.release(); }
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
    public void loadFile() {
        myLogI("loadingFile....... Start At Zero : " + startAtZero + " - Play Audio straight away : " + directPlay);
        Uri uriToPlay = null;
        String pathToPlay = null;

        ZikFile zf = PlayList.getInstance().getZikFile();
        if (zf==null) {
            myLogE("PlayList.getInstance().getZikFile==null");
            killIt();
            return;
        }

        myLog(zf.toString());
        myLogD(zf.toString().replace(",","\n"));

// NEW SAF URI
        if (zf.getPath().startsWith("content://")) {
            myLog("New SAF file, content...");
            uriToPlay = buildFileUri(Uri.parse(zf.getPath()),zf.getName());
            myKeyFirebase("loadFile", "uri");
            myLogFirebase("loadFile uri : " + Objects.toString(uriToPlay));
            //check...
            DocumentFile file = DocumentFile.fromSingleUri(this, uriToPlay);
            if (!file.exists() || !file.isFile()) {
                myLogEE(null,"Invalid or non-file Uri: " + uriToPlay);
                killIt();
                return;
            }
// OLD SCHOOL PATHS
        } else {
            pathToPlay = zf.getPath() + "/" + zf.getName();
            myKeyFirebase("loadFile", "path");
            myLogFirebase("loadFile path : " + pathToPlay);
            myLog("Good Old Way, Path style : " + pathToPlay);
            //check....
            if (!fileExists(pathToPlay)) {
                myLogEE(null,"loadFile(sPath) : ERROR -- File doesn't exist !! " + pathToPlay);
                killIt();
                return;
            }
        }

        if (uriToPlay==null && pathToPlay==null) {
            myLogE("cannot get file to play : null");
            killIt();
            return;
        }

        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayerStop();
            }
            mediaPlayer.reset();
            if (uriToPlay!=null) {
                mediaPlayer.setDataSource(this, uriToPlay);
            } else {
                mediaPlayer.setDataSource(pathToPlay);
            }
            mediaPlayer.prepareAsync();

        } catch (IOException e) {
            myLogE("LoadFile - " + e.getMessage());
            myLogEE(e, " +++++***+++++ ERROR LOADING PLAYLIST +++++***+++++ ");
            killIt();
            return;
        }
        myLog("loadFile - END");

    }


    /********************************************************************************
     ***       PLAY-PAUSE
     ********************************************************************************
     */

    public void playAudio() {
        myLog("playAudio() - start");
        if (mediaPlayer != null) {
            if (!mediaPlayer.isPlaying()) {

                if (mediaPlayer.isReady()) {
                    startPlayWithMediaPlayer();
                } else if (!mediaPlayer.isPreparing()) {
                    // Re-prepare if necessary
                    directPlay = true;
                    loadFile();
                } else {
                    myLogW("mediaPlayer is preparing, wait...");
                }

            } else {
                myLogE("mediaPlayer was already Playing ... going out of AudioService.playAudio()");
            }
        } else { // car ca bug sur v27 on android sdk 27 (8.1) OPPO CPH1909
            myLogE("mediaPlayer was not instantiated ... going out of AudioService.playAudio()");
        }
    }

    private void doIntroCut() {
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
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayerPause();
            if (mediaSession != null) {
                mediaSession.setActive(false);
            }
            updateZikFileState(false);
            if (audioManager != null) { audioManager.abandonAudioFocus(afChangeListener); }
            stopSleepTimer();
            createNotification();
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
        reloadSleepTimer();
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
        reloadSleepTimer();
        int temp = getPosition();
        if ((temp - lag) > 0) {
            setPosition(temp - lag);
        }
    }

    /********************************************************************************
     ***       SPEED - POSITION
     ********************************************************************************
     */


    public void setSpeed(double speed) {
        try {
            this.speed = speed;
            if (mediaPlayer!=null && mediaPlayer.isPlaying()) {
                mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed((float) speed));
            }
            myLog("setSpeed() : " + speed);
            reloadSleepTimer();
        } catch (Exception e) {
            myLogEE(e,"AudioService Error setting Speed");
        }
        if (!(getCurrentZikFile()==null)) {
            saveSpeedToPref(speed);
        }
    }

    public double getSpeed() {
        if (getCurrentZikFile() != null) {
            speed = getSpeedFromPref();
        }
        if (speed == 0) speed = 1.0;
        return speed;
    }

    public void setPosition(int position) {

        mediaPlayer.seekTo(position);
        reloadSleepTimer();
        createNotification();
        myLog("setPosition() : " + myDF.format(position));
    }

    public int getPosition() {
        int curPosMediaPlayer = mediaPlayer.getCurrentPosition();
        if (LOG_TRACE_ALL) {
            if (PlayList.getInstance().getZikFile()!=null) {
                int curPosGlobalVar = (int) PlayList.getInstance().getZikFile().getPosition();
                int diff = curPosGlobalVar-curPosMediaPlayer;
                myLog("getPosition() Saved/PlayerCurrent  " + curPosGlobalVar + "/" + curPosMediaPlayer + "  -  Diff = " + diff);
            }
        }
        return curPosMediaPlayer;
    }

    public int getDuration() {
        return getCurrentZikFile() == null ? 0 : (int) getCurrentZikFile().getDuration();
    }

    public boolean isPlaying() {
        if (LOG_TRACE_ALL) myLog("isPlaying()");
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public boolean exist() {
        if (LOG_TRACE_ALL) myLog("exist");
        return mediaPlayer != null;
    }

    private ZikFile getCurrentZikFile() {
        return PlayList.getInstance().getZikFile();
    }

    /********************************************************************************
     ***       TIMER
     ********************************************************************************
     */

    private void startSleepTimer() {
        if (isTimerRunning) {
            myLogD("Timer is already running....");
            return;
            //stopSleepTimer();
        }

        boolean doBeep = Option.getBeepAutoStop();
        int timeBeforeSleep = customSleepTime == 0 ? Option.getTimeBeforeSleep() : customSleepTime;

        elapsedSeconds = 0;
        isTimerRunning = true;

        myLog("----------------------------------------------------------------------------- timer STARTED -- ");
        LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_PLAYBACK_TIMER_VALUE).putExtra(TIMER_VALUE, elapsedSeconds));

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                myLogD("----------------------------------------------------------------------------- " + elapsedSeconds + "s. since timer started.....      (AutoSleep set to " + timeBeforeSleep + "min.)");
                updateZikFileState(false);

                // Auto Sleep Option
                if (elapsedSeconds > timeBeforeSleep * 60) {
                    myLog("Max Playback Time Reached -- Stopping Service");
                    stopSleepTimer();

                    // 2 beeps
                    if (doBeep) playBeep("2beeps");

                    LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_PLAYBACK_MAXTIMEREACH));
                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                        mediaPlayerStop();
                    }
                    stopSelf();
                } else {
                    Intent intent = new Intent(NOTIFICATION_PLAYBACK_TIMER_VALUE);
                    intent.putExtra(TIMER_VALUE, elapsedSeconds);
                    LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(intent);

                    elapsedSeconds += DELAY_CHECK_TIMER / 1000;

                    //HOHO
                    //createNotification();

                    handler.postDelayed(this, DELAY_CHECK_TIMER);
                }
            }
        };

        handler.postDelayed(timerRunnable, DELAY_CHECK_TIMER);
    }


    public void updateSleepTimer(int customSleepTime) {
        this.customSleepTime = customSleepTime;
        reloadSleepTimer();
    }
    public void reloadSleepTimer() {
        if (isTimerRunning) {
            stopSleepTimer();
            startSleepTimer();
        }
    }
    public int getCustomSleepTime() {
        return customSleepTime;
    }

    private void stopSleepTimer() {
        myLog("stopSleepTimer()");
        try {
            if (handler != null && timerRunnable != null) {
                handler.removeCallbacks(timerRunnable);
            }
            isTimerRunning = false;
            String str;
            if (!(PlayList.getInstance().getZikFilesList()==null)) {
                str = getCurrentZikFile().getFolderName() + " : " + Tonio.formatTime(elapsedSeconds*1000);
                myLog("----------------------------------------------------------------------------- " + elapsedSeconds + "s. since timer started -- STOPPED -- " + str );
            } else {
                str = "killTimer : ERROR zikFilePlayList==null";
                myLogE("----------------------------------------------------------------------------- " + elapsedSeconds + "s. since timer started -- STOPPED -- " + str );
            }
        } catch (Exception e) {
            myLogEE(e,"killTimer, nothing to kill ?");
        }
    }


    private void startMemoryTrimTimer() {
        pauseCheckHandler = new Handler();
        pauseCheckRunnable = new Runnable() {
            @Override
            public void run() {

                if (pauseStartTimestampMs != 0) {
                    logPauseTime();
                    if (System.currentTimeMillis() - pauseStartTimestampMs > TRIM_AFTER_PAUSE_MS) {
                        myLogW("let's kill it");
                        mediaPlayer.release();
                        mediaPlayer = null;
                    }
                }

                // Schedule the next check in 1 minute
                pauseCheckHandler.postDelayed(this, 30 * 1000); // 30sec
            }
        };

// Start the loop
        pauseCheckHandler.postDelayed(pauseCheckRunnable, 60 * 1000);
    }

    private void stopPauseTimer() {
        if (pauseCheckHandler != null) {
            pauseCheckHandler.removeCallbacks(pauseCheckRunnable);
        }
    }
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_THRESHOLD) {
            myLogW("onTrimMemory() - level=[" + level + "] >= " + TRIM_MEMORY_THRESHOLD);
            logPauseTime();
            if (mediaPlayer != null) {
                try {
                    mediaPlayer.release();
                    mediaPlayer = null;
                    myLog("mediaPlayer released due to memory pressure");
                } catch (Exception e) {
                    myLogEE(e,"onTrimMemory - Error releasing mediaPlayer: " + e.getMessage());
                }
            } else {
                myLog("mediaPlayer was already null");
            }
        }
    }
    public void logPauseTime() {
        if (pauseStartTimestampMs != 0) {
            long pauseTime = (System.currentTimeMillis() - pauseStartTimestampMs);
            myLog("And was paused for " + formatTime(pauseTime, true) + " min.   MAX is " + formatTime(TRIM_AFTER_PAUSE_MS));
        }
    }


    /********************************************************************************
     ***       UPDATE DB
     ********************************************************************************
     */
    private void updateZikFileState(boolean bFinished) {
        ZikFile zf = getCurrentZikFile();
        try {
            if (zf.getFirstaccess() == null) {
                zf.setFirstaccess(new Date(System.currentTimeMillis()));
            }
            final Time sLastAccessTime = new Time(System.currentTimeMillis());
            final Date sLastAccess = new Date(System.currentTimeMillis());
            zf.setLastaccess(sLastAccess);
            zf.setLastaccessTime(sLastAccessTime);
            if (bFinished) {
                zf.setPosition(zf.getDuration());
                zf.setPercentdone(100);
                zf.setFinished(true);
            } else {
                zf.setPosition(getPosition());
                zf.setPercentdone(FormatPercentDouble((double) getPosition() / getDuration()));
            }
            new Thread(() -> {
                try {
                    AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
                    ZikFileDao zikFileDao = db.ZikFileDao();
                    int mySqlresponse = zikFileDao.update(zf);
                    if (mySqlresponse > 0) {
                        myLogD("---------- zikFile updated (" + zf.getName() + ")- position : " + myDF.format(zf.getPosition()));
                        Sql.calculateFolderProgress(getApplicationContext(), zf.getIdFolder());
                    } else {
                        myLogE("updateZikFileState - Error sql response ---------- ZikFile NOT updated");
                    }
                } catch (Exception e) {
                    myLogEE(e,"updateZikFileState - Exception while Updating File progress in Thread - " + e.getMessage());
                }
            }).start();
        } catch (Exception e) {
            myLogEE(e,"updateZikFileState - Exception while Updating File progress in Initialization - " + e.getMessage());
        }
    }


    private void saveSpeedToPref(double speed) {
        try {
            SharedPreferences.Editor editor = getSharedPreferences(SHARED_PREFERENCE_SPEED, MODE_PRIVATE).edit();
            editor.putString(String.valueOf(getCurrentZikFile().getIdFolder()),Double.toString(speed)).apply();
        } catch (Exception e) {
            myLogEE(e,"error saving speed in prefs - " + e.getMessage());
        }
    }

    private double getSpeedFromPref() {
        try {
            SharedPreferences prefs = getSharedPreferences(SHARED_PREFERENCE_SPEED, MODE_PRIVATE);
            return Double.parseDouble(prefs.getString(String.valueOf(getCurrentZikFile().getIdFolder()), "1.0"));
        } catch (Exception e) {
            myLogEE(e,"error getting speed from prefs - " + e.getMessage());
            return 1.0;
        }
    }

    /********************************************************************************
     ***       NOTIFICATIONS
     ********************************************************************************
     */

    private void createNotification() {
        myLogD("createNotification()");
        if (mediaPlayer == null || mediaSession == null) {
            myLogE("MediaPlayer or MediaSession is null, skipping notification");
            return;
        }

        try {
            // custom addAction only ok on old Android devices... KO with Android 13+
            PendingIntent playPauseAction;
            String actionName;
            int actionIcon;
            if (mediaPlayer.isPlaying()) {
                actionName = "Pause";
                actionIcon = android.R.drawable.ic_media_pause;
                playPauseAction = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE);
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING, mediaPlayer.getCurrentPosition(), (float) getSpeed()); //to force update of the notification progressBar
            } else {
                actionName = "Play";
                actionIcon = android.R.drawable.ic_media_play;
                playPauseAction = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY);
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED, mediaPlayer.getCurrentPosition(), (float) getSpeed()); //to force update of the notification progressBar
            }



            // Create an intent to open the app when the notification is tapped
            Intent openAppIntent = new Intent(this, PlayActivity.class);
            openAppIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP); // Ensures only one instance
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                    //.putString(MediaMetadataCompat.METADATA_KEY_TITLE, getCurrentZikFile().getDisplayName())
                    //.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, getCurrentZikFile().getFolderName())
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer.getDuration())  //Shows the fucking progressBar !!
                    .build();
            mediaSession.setMetadata(metadata);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, ID_NOTIFICATION_PLAY_AUDIO_CHANNEL) // channel is used for user to be able to disable all notifications from that channel, starting android 8
                    .setContentTitle(getCurrentZikFile().getFolderName())
                    .setContentText(getCurrentZikFile().getDisplayName())
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(contentIntent)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOnlyAlertOnce(true)
                    .setOngoing(true) //if not, Notification may get destroyed by system
                    // custom addAction only ok on old Android devices... KO with Android 13+
                    .addAction(new NotificationCompat.Action(android.R.drawable.ic_media_rew, "Rewind", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_REWIND)))
                    .addAction(new NotificationCompat.Action(actionIcon, actionName, playPauseAction))
                    .addAction(new NotificationCompat.Action(android.R.drawable.ic_media_ff, "Forward", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_FAST_FORWARD)))
                    .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()  //that's the shit that fuck with the progressBar... but yeah...
                            .setMediaSession(mediaSession.getSessionToken())
                            .setShowActionsInCompactView(0,1,2)
                    )
            ;

            Notification notification = builder.build();

            try {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // sdk 29 (28 is Android 9)
                    myLogD("startForeground FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK");
                    startForeground(ID_NOTIFICATION_PLAY_AUDIO_INT, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                } else {
                    myLogD("startForeground");
                    startForeground(ID_NOTIFICATION_PLAY_AUDIO_INT, notification);
                }
            } catch (Throwable t) {
                myLogEE(t,"Notification startForeground failed : " + t.getMessage());
            }


        } catch (Exception t) {
            myLogEE(t,"Notification creation failed: " + t.getMessage());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            myLogE("Device with Android < 8, will not do createNotificationChannel()");
        } else {
            myLog("createNotificationChannel()");
            try {
                NotificationChannel channel = new NotificationChannel(
                        ID_NOTIFICATION_PLAY_AUDIO_CHANNEL, "Music Playback",
                        NotificationManager.IMPORTANCE_LOW); //LOW = no sound
                channel.setDescription("Bookplayer Music Playback Controls");
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                }
            } catch (Exception e) {
                myLogEE(e,"createNotificationChannel() - " + e.getMessage());
            }
        }
    }

    private void removeNotification() {
        try {
            stopForeground(true); // Remove the notification and stop being a foreground service

            if (mediaSession != null) {
                mediaSession.setActive(false); // Deactivate media session
            }

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(ID_NOTIFICATION_PLAY_AUDIO_INT); // ID 1 matches the one used in startForeground()

            myLogI("Notification removed");
        } catch (Exception e) {
            myLogEE(e,"Failed to remove notification: " + e.getMessage());
        }
    }

    private void mediaPlayerPause() {
        mediaPlayer.pause();
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED, mediaPlayer.getCurrentPosition(), 0.0f);
        pauseStartTimestampMs = System.currentTimeMillis();
    }
    private void mediaPlayerStop() {
        mediaPlayer.stop();
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED, 0, 0.0f);
        mediaSession.setActive(false);
        pauseStartTimestampMs = System.currentTimeMillis();
    }

    private void updatePlaybackState(int playbackState, long position, float playbackSpeed) {
        PlaybackStateCompat playbackStateCompat = new PlaybackStateCompat.Builder()
                .setState(playbackState, position, playbackSpeed)
                .setActions(playbackStateCompatAction)
                .build();
        mediaSession.setPlaybackState(playbackStateCompat);
    }

    public int getAudioSessionId() {
        return mediaPlayer != null ? mediaPlayer.getAudioSessionId() : 0;
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
            myLogEE(e,"playBeep(" + beepType + ") - " + e.getMessage());
        }
    }

    // 13-56
    // StatusBarNotification(pkg=com.driot.bookplayer user=UserHandle{0} id=1 tag=null key=0|com.driot.bookplayer|1|null|10333:
    // Notification(channel=audio_channel_of_bookplayer shortcut=null contentView=null vibrate=null sound=null defaults=0
    // flags=ONGOING_EVENT|ONLY_ALERT_ONCE|NO_CLEAR|FOREGROUND_SERVICE color=0x00000000 category=transport actions=3 vis=PUBLIC semFlags=0x0 semPriority=0 semMissedCount=0))
    private boolean isNotificationActive(Context context, int notificationId) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        StatusBarNotification[] notifications = ((NotificationManager) manager).getActiveNotifications();
        for (StatusBarNotification sbn : notifications) {
            myLog(sbn.toString());
            if (sbn.getId() == notificationId) {
                return true;
            }
        }
        return false;
    }

    private void killIt() {
        myLogI("killIt()");
        LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_FILENOTFOUND));
        ErrorLoadingFile=true;
        removeNotification();
        stopSelf();
    }

}