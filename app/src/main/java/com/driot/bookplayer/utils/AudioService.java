package com.driot.bookplayer.utils;

import com.driot.bookplayer.R;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
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
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media.session.MediaButtonReceiver;

import com.driot.bookplayer.activities.LifecycleLoggingService;
import com.driot.bookplayer.activities.PlayActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.ZikFileDao;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.PlayList;
import com.driot.bookplayer.db.Sql;
import com.driot.bookplayer.db.ZikFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Date;
import java.sql.Time;
import java.text.DecimalFormat;
import java.util.Objects;

import static com.driot.bookplayer.activities.PlayActivity.SHARED_PREFERENCE_SPEED;
import static com.driot.bookplayer.utils.KanLogger.myLogE;
import static com.driot.bookplayer.utils.Tonio.FormatPercentDouble;
import static com.driot.bookplayer.utils.Tonio.FormatTime;
import static com.driot.bookplayer.utils.Tonio.fileExists;
import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Utils.copyStream;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 01/11/20
 */
class CustomMediaPlayer extends MediaPlayer {
    public void customSeekTo(int posMilliSec) {
        KanLogger.myLog("customSeekTo() : " + posMilliSec);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            seekTo(posMilliSec);
        } else {
            if (PlayList.getZikFile() != null && PlayList.getZikFile().isM4b()) {
                seekTo(posMilliSec, SEEK_CLOSEST);  //seek_closest needed for m4b...
                KanLogger.myLog("SEEK_CLOSEST (m4b)");
            } else {
                seekTo(posMilliSec);
            }
        }
    }
}
public class AudioService extends LifecycleLoggingService {

    private static final String CHANNEL_ID = "audio_channel_of_toto";

    private Handler handler;
    private Runnable timerRunnable;
    private int elapsedSeconds = 0;
    private int customSleepTime = 0;
    public static final int DELAY_CHECK_TIMER = 1000;

    public static final int REWIND_AFTER_PAUSE_MILLISECONDS = 3000;
    public static final int REWIND_AFTER_PAUSE_IF_DIFF_IN_MIN = 2;

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

    private CustomMediaPlayer mediaPlayer; //enhanced class by Tony
    private AudioManager audioManager;
    private AudioManager.OnAudioFocusChangeListener afChangeListener;
    private MediaSessionCompat mediaSession;
    private MediaSessionCompat.Callback callback = new MediaSessionCompat.Callback() {

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
            mediaPlayer.stop();
            createNotification();
            removeNotification(); // Remove notification when playback is stopped
        }
        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
            KeyEvent ke = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            myLog("MediaSessionCompat.Callback - onMediaButtonEvent -- Received command = " + ke);
            if (ke != null && ke.getAction() == KeyEvent.ACTION_DOWN) handleKeyEvent(ke.getKeyCode());
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
            myLog("MediaSessionCompat.Callback - onSkipToNext()");
            super.onSkipToNext();
        }

        @Override
        public void onSkipToPrevious() {
            myLog("MediaSessionCompat.Callback - onSkipToPrevious()");
            super.onSkipToPrevious();
        }
    };

    private PlaybackStateCompat.Builder stateBuilder;
    private int maxTimeBeforeSleep;
    private double speed = 1.0;
    private File tempFile = null;
    private boolean ErrorLoadingFile = false;
    DecimalFormat myDF = new DecimalFormat("#,###.");

    private boolean isTimerRunning = false;

    /********************************************************************************
     *       NATIVE METHODS
     *
     *  Because service always runs in the same process as clients, no need IPC.
     *
     */
    @Override
    public void onCreate() {
        myLog("onCreate()");
        super.onCreate();
        mediaPlayer = new CustomMediaPlayer();
        mediaSession = new MediaSessionCompat(this, "MyTotoMediaSession");

        handler = new Handler(); // for sleep timer

        myLog("configureMediaSession()");

        // Overridden methods in the MediaSession.Callback class.
        mediaSession.setCallback(callback);
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS); //useless ?
        mediaSession.setActive(true); //useless ?

        mediaPlayer.setOnCompletionListener(mediaPlayer -> {
            if (!ErrorLoadingFile) {
                updateZikFileState(true);
                alertTrackFinished();

                if (PlayList.getNumZikFile()+1 == PlayList.getZikFilesList().size()) {
                    myLog("mediaPlayer.OnCompletionListener  => calling PlayListFinish");

                    // 3 bips
                    if (Option.getBeepBookEnd(this)) {
                        playBeep("3beeps");
                    }

                    alertPlaylistFinished();
                    stopSleepTimer();
                } else {
                    myLog("mediaPlayer.OnCompletionListener => calling nextTrack");
                    nextTrack();
                }
            }
        });
        createNotificationChannel(); // for Android 14+ ( if not crash = CannotPostForegroundServiceNotificationException)
        createNotification();

        mediaPlayer.setOnErrorListener((mediaPlayer, i, i1) -> {
            ErrorLoadingFile = true;
            myLog("mediaPlayer.OnErrorListener Fired : " + i + " : " + i1 );
            alertError();
            return false;
        });

    }

    void nextTrack() {
        myLog("Next track");
        PlayList.setNumZikFile(PlayList.getNumZikFile()+1);
        mediaPlayer.reset();
        int curNum = PlayList.getNumZikFile() + 1;
        myLog("loading next track : n°" + curNum + "/" + PlayList.getZikFilesList().size() );

        // petit bip
        if (Option.getBeepChapter(this)) playBeep("1beep");

        loadZeFile(true);
        //TODO remplace par PlayAudio() ??
        myLog("mediaPlayer.start() -- nextrack");
        mediaPlayer.start();
        setSpeed(getSpeed());
        alertNewTrack();
    }

    private void alertNewTrack() {
        LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_NEWTRACK).putExtra(TRACKNUMBER, PlayList.getNumZikFile()));
        createNotification();
        myLog("sendBroadcast alertNewTrack ");
    }

    private void alertError() {
        LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_ERROR).putExtra(TRACKNUMBER, PlayList.getNumZikFile()));
        myLog("sendBroadcast alertError");
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
        switch (keyCode) {
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
        myLog("onStartCommand()" + intent.toString());
        if (Objects.equals(intent.getAction(), Intent.ACTION_MEDIA_BUTTON)) {
            if (intent.hasExtra(Intent.EXTRA_KEY_EVENT)) {
                KeyEvent keyEvent = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (keyEvent != null) {
                    int keyCode = keyEvent.getKeyCode();
                    handleKeyEvent(keyCode);
                }
            }
        }
        return START_NOT_STICKY; //TODO maybe to change... because memory pressure could kill it
    }
    @Override
    public void onDestroy() {
        myLog("onDestroy()");
        super.onDestroy();
        if (mediaPlayer.isPlaying()) {mediaPlayer.stop();}
        stopSleepTimer();
        mediaPlayer.release();
        mediaPlayer = null;
        if (audioManager != null) { audioManager.abandonAudioFocus(afChangeListener); }
        if (tempFile != null && tempFile.exists()) { tempFile.delete();tempFile=null;}
        stopSleepTimer();
        if(mediaSession != null) { mediaSession.release(); }
        //stopUpdatingPlaybackState();
        removeNotification();
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
        removeNotification();
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

    public void loadFiles(ZikFile[] zikFiles) {
        myLog("loadFiles(array) - folder : " + zikFiles[0].getIdFolder());
        loadZeFile(false);
    }

    private void loadZeFile(boolean startAtZero) {
        myLog("loadZeFile()");
        ZikFile zf = PlayList.getZikFile();
        if (zf.isIszipfile()) {
            loadFile(getTempFilePathFromZipFile(zf), startAtZero);
        } else {
            String mPath = zf.getPath() + "/" + zf.getName();
            loadFile(mPath, startAtZero);
        }
    }

    private String getTempFilePathFromZipFile(ZikFile file) {
        myLog("ZIP, createTempFile " + file.getPath() );
        String pathOfTempFile = "";
        try (InputStream inputStream = getContentResolver().openInputStream(Uri.fromFile(new File(file.getPath())));
            FileOutputStream out = new FileOutputStream(tempFile = File.createTempFile("_AUDIO_", getExtension(file.getName())))) {
            copyStream(inputStream, out);
            pathOfTempFile = tempFile.getPath();
        } catch (IOException e) {
            myLogE("ZIP, Error creating temp file : " + e.getMessage());
        } finally {
            LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_ZIP_FILE_LOADED));
        }
        return pathOfTempFile;
    }

    // TODO, use openFileDescriptor & remove legacy from manifest
    public boolean loadFile(String sPath, boolean startAtZero) {
        ErrorLoadingFile = false; // for onCompletion Next Track...
        if (!fileExists(sPath)) {
            myLogE("loadFile(sPath) : ERROR -- File doesn't exist !! " + sPath);
            LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_FILENOTFOUND));
            ErrorLoadingFile=true;
            stopSelf();
            return false;
        }

        myLog("loadFile(sPath) [" + sPath + "]");
        try {
            //mediaPlayer.stop();
            mediaPlayer.reset();
            mediaPlayer.setDataSource(sPath);
            mediaPlayer.prepare();
            if (startAtZero || PlayList.getZikFile() == null) {
                mediaPlayer.customSeekTo(0);
            } else {
                mediaPlayer.customSeekTo((int) PlayList.getZikFile().getPosition());
            }
            LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_FILELOADED));
            myLog("------------------------------------------------------------"); // to get the chapters of a .m4b, you need ffmpeg...
            MediaPlayer.TrackInfo[] trackInfoArray = mediaPlayer.getTrackInfo();
            for (MediaPlayer.TrackInfo trackInfo : trackInfoArray) {
                myLog("trackInfo.toString() : " + trackInfo.toString());
            }
            myLog("------------------------------------------------------------"); // to get the chapters of a .m4b, you need ffmpeg...

        } catch (IOException e) {
            myLogE("LoadFile - " + e.getMessage());
            myLogE(" +++++***+++++ ERROR LOADING FILE +++++***+++++ (" + sPath + ")");
            LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_FILENOTFOUND));
            ErrorLoadingFile=true;
            stopSelf();
            return false;
        }
        return true;
    }


    /********************************************************************************
     ***       PLAY-PAUSE
     ********************************************************************************
     */


    public void playAudio() {
        myLog("playAudio() - start");
        if (mediaPlayer != null) {
            if (!mediaPlayer.isPlaying()) {

                audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
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

                //myLog("playAudio() : audioManager.requestAudioFocus, mediaPlayer.start()");
                //audioManager.requestAudioFocus(afChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN); //looks useless now

                // Rewind After Pause
                if (Option.getRewindAfterPause(this)) {
                    if (PlayList.getZikFile() != null) {
                        Time lastAccessTime = PlayList.getZikFile().getLastaccessTime();
                        if (lastAccessTime != null) {
                            Time nowTime = new Time(System.currentTimeMillis());
                            long timeDiff = nowTime.getTime() - lastAccessTime.getTime();
                            if (timeDiff > REWIND_AFTER_PAUSE_IF_DIFF_IN_MIN*60*1000) {
                                myLog("Rewind after Pause - last play was " + timeDiff/1000/60 + " minutes ago.   - Threshold is " + REWIND_AFTER_PAUSE_IF_DIFF_IN_MIN + " min.   - Rewind Value is " + REWIND_AFTER_PAUSE_MILLISECONDS/1000 + " seconds.");
                                backwardAudio(REWIND_AFTER_PAUSE_MILLISECONDS);
                            } else {
                                myLog("NO Rewind after Pause - last play was " + timeDiff/1000/60 + " minutes ago.   - Threshold is " + REWIND_AFTER_PAUSE_IF_DIFF_IN_MIN + " min.   - Rewind Value is " + REWIND_AFTER_PAUSE_MILLISECONDS/1000 + " seconds.");
                            }
                        }
                    }
                }
                KanLogger.myLog("about to do mediaPlayer.start()...  mediaPlayer.getCurrentPosition : " + mediaPlayer.getCurrentPosition());
                mediaPlayer.start();
                setSpeed(getSpeed());
                startSleepTimer();
                createNotification();
            } else {
                myLogE("mediaPlayer was already Playing ... going out of AudioService.playAudio()");
            }
        } else { // car ca bug sur v27 on android sdk 27 (8.1) OPPO CPH1909
            myLogE("mediaPlayer was not instantiated ... going out of AudioService.playAudio()");
        }
    }

    public void pauseAudio() {
        myLog("pauseAudio()");
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
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
        forwardAudio(Option.get_ForwardSeconds(this)*1000);
    }
    public void forwardAudio(int lag) {
        myLog("forwardAudio()");
        int temp = getPosition();
        if ((temp + lag ) <= getDuration()) {
            setPosition(temp + lag );
            createNotification();
        }
    }
    public void backwardAudio() {
        backwardAudio(Option.get_ForwardSeconds(this)*1000);
    }
    public void backwardAudio(int lag) {
        myLog("backwardAudio() : " + lag);
        int temp = getPosition();
        if ((temp - lag) > 0) {
            setPosition(temp - lag);
            createNotification();
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
        } catch (Exception e) {
            myLogE("AudioService Error setting Speed");
        }
        if (!(getCurrentZikFile()==null)) {
            saveSpeedToPref(speed);
        }
    }

    public double getSpeed() {
        //speed = mediaPlayer.getPlaybackParams().getSpeed();
        if (getCurrentZikFile() != null) {
            speed = getSpeedFromPref();
        }
        if (speed == 0) speed = 1.0;
        myLog("getSpeed() : " + speed);
        return speed;
    }

    public void setPosition(int position) {

        mediaPlayer.customSeekTo(position);
        myLog("setPosition() : " + myDF.format(position));
    }

    public int getPosition() {
        //return mediaPlayer.getCurrentPosition();
        int curPosMediaPlayer = mediaPlayer.getCurrentPosition();
        if (LOG_TRACE_ALL) {
            int curPosGlobalVar = (int) PlayList.getZikFile().getPosition();
            int diff = curPosGlobalVar-curPosMediaPlayer;
            myLog("getPosition() Saved/PlayerCurrent  " + curPosGlobalVar + "/" + curPosMediaPlayer + "  -  Diff = " + diff);
        }
        return curPosMediaPlayer;
    }

    public void changeVolume(boolean increase) {
        if (audioManager != null) {
            if (increase) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
            } else {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
            }
        }
    }
    public double getVolume() {
        double zeValue;
        if (audioManager != null) {
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int curVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (maxVolume != 0) {
                zeValue = (double) curVolume / (double)  maxVolume;
            } else {
                zeValue = (double)  curVolume / 10.0;
            }
            return zeValue;
        }
        return -1.0;
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
        return PlayList.getZikFile();
    }

    /********************************************************************************
     ***       TIMER
     ********************************************************************************
     */

    private void startSleepTimer() {
        if (isTimerRunning) {
            myLogE("Timer is already running....   should we really start it again... TODO check if needed (aka if param like sleep duration changes)");
            //return; // Do not start again if already running
        }

        boolean doBeep = Option.getBeepAutoStop(this);
        int timeBeforeSleep = customSleepTime == 0 ? Option.getTimeBeforeSleep(this) : customSleepTime;

        elapsedSeconds = 0;
        isTimerRunning = true;

        myLog("----------------------------------------------------------------------------- timer STARTED -- ");
        LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_PLAYBACK_TIMER_VALUE).putExtra(TIMER_VALUE, elapsedSeconds));

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                myLogD("----------------------------------------------------------------------------- " + elapsedSeconds + "s. since timer started.....      (AutoSleep in " + timeBeforeSleep + "min.)");
                updateZikFileState(false);

                // Auto Sleep Option
                if (elapsedSeconds > timeBeforeSleep * 60) {
                    myLog("Max Playback Time Reached -- Stopping Service");
                    stopSleepTimer();

                    // 2 beeps
                    if (doBeep) playBeep("2beeps");

                    LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(new Intent(NOTIFICATION_PLAYBACK_MAXTIMEREACH));
                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                    }
                    stopSelf();
                } else {
                    Intent intent = new Intent(NOTIFICATION_PLAYBACK_TIMER_VALUE);
                    intent.putExtra(TIMER_VALUE, elapsedSeconds);
                    LocalBroadcastManager.getInstance(AudioService.this).sendBroadcast(intent);

                    elapsedSeconds += DELAY_CHECK_TIMER / 1000;

                    // Notification Update
                    int progress = 0;
                    int max;
                    if (PlayList.getZikFile() != null) {
                        progress = (int) PlayList.getZikFile().getPosition();
                        max = getDuration();
                    }
                    createNotification();
                    //updateNotificationProgress(max, progress); // seems useless in MediaSession => keep code for Download and other services

                    handler.postDelayed(this, DELAY_CHECK_TIMER);
                }
            }
        };

        handler.postDelayed(timerRunnable, DELAY_CHECK_TIMER);
    }

    // Call this method when the user sets a new custom sleep time
    public void updateSleepTimer(int customSleepTime) {
        this.customSleepTime = customSleepTime;
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
            if (!(PlayList.getZikFilesList()==null)) {
                str = getCurrentZikFile().getFolderName() + " : " + FormatTime(elapsedSeconds*1000);
                myLog("----------------------------------------------------------------------------- " + elapsedSeconds + "s. since timer started -- STOPPED -- " + str );
            } else {
                str = "killTimer : ERROR zikFilePlayList==null";
                myLogE("----------------------------------------------------------------------------- " + elapsedSeconds + "s. since timer started -- STOPPED -- " + str );
            }
        } catch (Exception e) {
            myLogE("killTimer, nothing to kill ?");
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
                //if (zf.getDuration() == 0) zf.setDuration(getDuration());
            }
            new Thread(() -> {
                try {
                    int mySqlresponse = 0 ;
                    AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
                    ZikFileDao zikFileDao = db.ZikFileDao();
                    mySqlresponse = zikFileDao.update(zf);
                    if (mySqlresponse > 0) {
                        myLogD("---------- zikFile updated (" + zf.getName() + ")- position : " + myDF.format(zf.getPosition()));
                        Sql.calculateFolderProgress(getApplicationContext(), zf.getIdFolder());
                    } else {
                        myLogE("updateZikFileState - Error sql response ---------- ZikFile NOT updated");
                    }
                } catch (Exception e) {
                    myLogE("updateZikFileState - Exception while Updating File progress in Thread - " + e.getMessage());
                }
            }).start();
        } catch (Exception e) {
            myLogE("updateZikFileState - Exception while Updating File progress in Initialization - " + e.getMessage());
            e.printStackTrace();
        }
    }

    /********************************************************************************
     ***       MEDIA SESSION - Lock Screen Actions
     ********************************************************************************
     */

    private void saveSpeedToPref(double speed) {
        try {
            SharedPreferences.Editor editor = getSharedPreferences(SHARED_PREFERENCE_SPEED, MODE_PRIVATE).edit();
            editor.putString(String.valueOf(getCurrentZikFile().getIdFolder()),Double.toString(speed)).apply();
        } catch (Exception e) {
            myLogE("error saving speed in prefs - " + e.getMessage());
        }
    }

    private double getSpeedFromPref() {
        try {
            SharedPreferences prefs = getSharedPreferences(SHARED_PREFERENCE_SPEED, MODE_PRIVATE);
            return Double.parseDouble(prefs.getString(String.valueOf(getCurrentZikFile().getIdFolder()), "1.0"));
        } catch (Exception e) {
            myLogE("error getting speed from prefs - " + e.getMessage());
            return 1.0;
        }
    }


    /********************************************************************************
     ***       MEDIA SESSION - Lock Screen Actions
     ********************************************************************************
     */

    private void createNotification() {
        if (mediaPlayer == null) {return;}
        try {
            PendingIntent playPauseAction;
            String actionName;
            int actionIcon;

            if (mediaPlayer.isPlaying()) {
                actionName = "Pause";
                actionIcon = android.R.drawable.ic_media_pause; //custom : R.drawable.ic_pause;
                playPauseAction = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE);
            } else {
                actionName = "Play";
                actionIcon = android.R.drawable.ic_media_play;
                playPauseAction = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY);
            }

            // Create an intent to open the app when the notification is tapped
            Intent openAppIntent = new Intent(this, PlayActivity.class);
            openAppIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP); // Ensures only one instance
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            //int progress = getPosition();
            //int progress = PlayList.getZikFile() == null ? 0 : (int) PlayList.getZikFile().getPosition();
            //int max = getDuration();

            int progress = 50;
            int max = 100;

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID) // channel is used for user to be able to disable all notifications from that channel, starting android 8
                    .setContentTitle(getCurrentZikFile().getFolderName())
                    .setContentText(getCurrentZikFile().getDisplayName())
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .setContentIntent(contentIntent)
                    .addAction(new NotificationCompat.Action(android.R.drawable.ic_media_rew, "backward", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_REWIND)))
                    .addAction(new NotificationCompat.Action(actionIcon, actionName, playPauseAction))
                    .addAction(new NotificationCompat.Action(android.R.drawable.ic_media_ff, "fastForward", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_FAST_FORWARD)))
                    .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                            .setMediaSession(mediaSession.getSessionToken())
                            .setShowActionsInCompactView(0,1,2))
                    //.setProgress(100,0, true)
                    //.setProgress(max, progress, false)  => TODO : check on samsung Tab, it seems to show there.. even without any code !!
                    //.setOngoing(true) //only effective android >= 14, maybe useless on mediaSession
                    //.setUsesChronometer(true)
            ;

            //val mediaMetadata = MediaMetadata.Builder().putLong(MediaMetadata.METADATA_KEY_DURATION, mp.duration.toLong()).build()
            //mediaSession.setMetadata(MediaMetadataCompat.fromMediaMetadata(mediaMetadata))

            startForeground(1, builder.build());
        } catch (Exception e) {
            myLogE("Error createNotification() - " + e.getMessage());
        }
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            myLogE("Device with Android < 8, will not do createNotificationChannel()");
        } else {
            myLog("createNotificationChannel()");
            try {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID, "Music Playback",
                        NotificationManager.IMPORTANCE_LOW); //LOW = no sound
                channel.setDescription("Bookplayer Music Playback Controls");
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                }
            } catch (Exception e) {
                myLogE("createNotificationChannel() - " + e.getMessage());
            }
        }
    }
    private void removeNotification() {
        stopForeground(true);
        stopSelf();
    }
    public int getAudioSessionId() {
        return mediaPlayer != null ? mediaPlayer.getAudioSessionId() : 0;
    }

    @SuppressWarnings("IfCanBeSwitch")
    private void playBeep(String beepType) {
        myLogE("playBeep - argument : " + beepType);
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
            myLogE("playBeep(" + beepType + ") - " + e.getMessage());
        }
    }


    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogI(String str) { KanLogger.myLogI(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }

}