package com.driot.bookplayer.utils;

import static android.media.MediaPlayer.SEEK_CLOSEST;
import com.driot.bookplayer.R;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.session.MediaButtonReceiver;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.ZikFileDao;
import com.driot.bookplayer.global.PlayList;
import com.driot.bookplayer.db.Sql;
import com.driot.bookplayer.db.ZikFile;
import com.driot.tonylib.KanLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Date;
import java.sql.Time;
import java.text.DecimalFormat;
import java.util.Timer;
import java.util.TimerTask;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static com.driot.bookplayer.activities.OptionActivity.DEFAULT_BEEP_BOOKEND;
import static com.driot.bookplayer.activities.OptionActivity.DEFAULT_BEEP_CHAPTER;
import static com.driot.bookplayer.activities.OptionActivity.DEFAULT_FORWARD_SECONDS;
import static com.driot.bookplayer.activities.OptionActivity.DEFAULT_SCREEN_ORIENTATION_LOCK;
import static com.driot.bookplayer.activities.OptionActivity.DEFAULT_TIME_BEFORE_SLEEP;
import static com.driot.bookplayer.activities.OptionActivity.SHARED_PREFERENCES_OPTIONS;
import static com.driot.bookplayer.activities.PlayActivity.SHARED_PREFERENCE_SPEED;
import static com.driot.bookplayer.utils.Tonio.FormatPercentDouble;
import static com.driot.bookplayer.utils.Tonio.FormatTime;
import static com.driot.bookplayer.utils.Tonio.fileExists;
import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Tonio.getFileNameFromPath;
import static com.driot.bookplayer.utils.Utils.copyStream;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 01/11/20
 */
class CustomMediaPlayer extends MediaPlayer {
    public void customSeekTo(int posMilliSec) {
        if (Build.VERSION.SDK_INT >= 26) {
            seekTo(posMilliSec, SEEK_CLOSEST);  //seek_closest needed for m4b...
        } else {
            seekTo(posMilliSec);
        }
    }
}
public class AudioService extends Service {

    private static final String CHANNEL_ID = "mychanelID129111";
    private Timer timer;
    private int tempsEcoule = 0;
    public static final int DELAY_MAXPLAYBACK = 1000*60*60; //1h
    public static final int DELAY_CHECK_TIMER = 1000*5;

    private static final boolean LOG_TRACE_ALL = false;

    private final IBinder binder = new BackgroundBinder();
    public static final String TRACKNUMBER = "tracknumber";
    public static final String TIMER_VALUE = "TIMER_VALUE";
    public static final String NOTIFICATION_FILELOADED = "NOTIFICATION_FILELOADED";
    public static final String NOTIFICATION_NEWTRACK = "NOTIFICATION_NEWTRACK";
    public static final String NOTIFICATION_TRACKFINISHED = "NOTIFICATION_TRACKFINISHED";
    public static final String NOTIFICATION_ERROR = "NOTIFICATION_ERROR";
    public static final String NOTIFICATION_AUDIOFOCUS_LOST = "NOTIFICATION_AUDIOFOCUS_LOST";
    public static final String NOTIFICATION_AUDIOFOCUS_GAIN = "NOTIFICATION_AUDIOFOCUS_GAIN";
    public static final String NOTIFICATION_ZIP_FILE_LOADED = "NOTIFICATION_ZIP_FILE_LOADED";
    public static final String NOTIFICATION_PLAYLISTFINISHED = "NOTIFICATION_PLAYLISTFINISHED";
    public static final String NOTIFICATION_PLAYBACK_MAXTIMEREACH = "NOTIFICATION_PLAYBACK_MAXTIMEREACH";
    public static final String NOTIFICATION_PLAYBACK_TIMER_VALUE = "NOTIFICATION_PLAYBACK_TIMER_VALUE";

    private CustomMediaPlayer mediaPlayer; //enhanced class by Tony
    private AudioManager mAudioManager;
    private AudioManager.OnAudioFocusChangeListener afChangeListener;
    private MediaSessionCompat mediaSession;
    private Handler handler;
    private Runnable updateRunnable;
    private MediaSessionCompat.Callback callback = new MediaSessionCompat.Callback() {

        @Override
        public void onPlay() { // is called by headset button pressed !!!
            myLog("mediaSession Callback onPlay()");
            super.onPlay();
            playPauseAudio();
        }

        @Override
        public void onPause() {
            myLog("mediaSession Callback onPause()");
            super.onPause();
            playPauseAudio();
            //updatePlaybackState();
            //stopUpdatingPlaybackState();
        }
        @Override
        public void onStop() {
            super.onStop();
            mediaPlayer.stop();
            updatePlaybackState();
            //stopUpdatingPlaybackState();
            removeNotification(); // Remove notification when playback is stopped
        }
        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
            KeyEvent ke = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            myLog("mediaSession Callback onMediaButtonEvent -- Received command = " + ke);
            return super.onMediaButtonEvent(mediaButtonIntent);
        }
        /*
        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
            KeyEvent ke = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            myLog("mediaSession Callback onMediaButtonEvent -- Received command = " + ke);
            if (ke != null && ke.getAction() == KeyEvent.ACTION_DOWN) {
                switch (ke.getKeyCode()) {
                    case KeyEvent.KEYCODE_MEDIA_PLAY:
                        myLog("mediaSession Callback onMediaButtonEvent -- Play pressed --- KEYCODE_MEDIA_PLAY");
                        playPauseAudio();
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PAUSE:
                        myLog("mediaSession Callback onMediaButtonEvent -- Pause pressed --- KEYCODE_MEDIA_PAUSE");
                        playPauseAudio();
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                        myLog("mediaSession Callback onMediaButtonEvent -- PlayPause pressed --- KEYCODE_MEDIA_PLAY_PAUSE");
                        playPauseAudio();
                        break;
                    case KeyEvent.KEYCODE_HEADSETHOOK:
                        myLog("mediaSession Callback onMediaButtonEvent -- PlayPause pressed --- KEYCODE_HEADSETHOOK");
                        playPauseAudio();
                        break;
                    case KeyEvent.KEYCODE_MEDIA_NEXT:
                        myLog("mediaSession Callback onMediaButtonEvent -- Next pressed --- KEYCODE_MEDIA_NEXT");
                        forwardAudio();
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                        myLog("mediaSession Callback onMediaButtonEvent -- Previous pressed --- KEYCODE_MEDIA_PREVIOUS");
                        backwardAudio();
                        break;

                }
            }
            return super.onMediaButtonEvent(mediaButtonIntent);
        }

         */
    };

    private PlaybackStateCompat.Builder stateBuilder;
    private int maxTimeBeforeSleep;

    //private boolean fileHasBeenLoaded = false;
    private double speed = 1.0;

    private File tempFile = null;

    private boolean ErrorLoadingFile = false;

    DecimalFormat myDF = new DecimalFormat("#,###.");

    /********************************************************************************
     ***       NATIVE METHODS
     ********************************************************************************
     *  Because service always runs in the same process as clients, no need IPC.
     *
     */
    @Override
    public void onCreate() {
        myLog("onCreate()");
        super.onCreate();
        mediaPlayer = new CustomMediaPlayer();
        mediaSession = new MediaSessionCompat(this, "MyTotoMediaSession");

        myLog("configureMediaSession()");

        // Overridden methods in the MediaSession.Callback class.
        mediaSession.setCallback(callback);
        //mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        //mediaSession.setActive(true);

        /*
        handler = new Handler();
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                //updatePlaybackState();
                handler.postDelayed(this, 1000);
            }
        };

         */

        setMaxTimeBeforeSleep();

        // Create a new PlaybackState.Builder => obligatoire, sinon il affiche les ic_play et il bug
        stateBuilder = new PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_FAST_FORWARD | PlaybackStateCompat.ACTION_REWIND);
        mediaSession.setPlaybackState(stateBuilder.build());

        mediaPlayer.setOnCompletionListener(mediaPlayer -> {
            if (!ErrorLoadingFile) {
                updateZikFileState(true);
                alertTrackFinished();

                if (PlayList.getNumZikFile()+1 == PlayList.getZikFilesList().size()) {
                    myLog("mediaPlayer.OnCompletionListener  => calling PlayListFinish");

                    // 3 bips
                    SharedPreferences prefs = this.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE);
                    if (prefs.getBoolean("BEEP_BOOKEND", DEFAULT_BEEP_BOOKEND)) {
                        ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
                        toneGen1.startTone(ToneGenerator.TONE_CDMA_PIP,500);
                    }

                    alertPlaylistFinished();
                    killTimer();
                } else {
                    myLog("mediaPlayer.OnCompletionListener => calling nextTrack");
                    nextTrack();
                }
            }
        });
        createNotification();
        createNotificationChannel();

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
        SharedPreferences prefs = this.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE);
        if (prefs.getBoolean("BEEP_CHAPTER", DEFAULT_BEEP_CHAPTER)) {
            ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
            toneGen1.startTone(ToneGenerator.TONE_CDMA_PIP,150);
        }

        loadZeFile(true);
        //TODO remplace par PlayAudio() ??
        myLog("mediaPlayer.start() -- nextrack");
        mediaPlayer.start();
        setSpeed(getSpeed());
        alertNewTrack();
    }

    private void alertNewTrack() {
        Intent intent = new Intent(NOTIFICATION_NEWTRACK);
        intent.putExtra(TRACKNUMBER, PlayList.getNumZikFile());
        sendBroadcast(intent);
        myLog("sendBroadcast alertNewTrack ");
    }

    private void alertError() {
        Intent intent = new Intent(NOTIFICATION_ERROR);
        intent.putExtra(TRACKNUMBER, PlayList.getNumZikFile());
        sendBroadcast(intent);
        myLog("sendBroadcast alertError");
    }

    private void alertTrackFinished() {
        Intent intent = new Intent(NOTIFICATION_TRACKFINISHED);
        sendBroadcast(intent);
        myLog("AudioService --------------------------------------------------------------------------------- sendBroadcast alertTrackFinished --------------------------------------------------------------------------------");
    }

    private void alertPlaylistFinished() {
        Intent intent = new Intent(NOTIFICATION_PLAYLISTFINISHED);
        sendBroadcast(intent);
        myLog("sendBroadcast alertPlaylistFinished");
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        myLog("onStartCommand()" + intent.toString());
        return START_NOT_STICKY; //TODO maybe to change... because memory pressure could kill it
    }
    @Override
    public void onDestroy() {
        myLog("onDestroy()");
        if (mediaPlayer.isPlaying()) {mediaPlayer.stop();}
        mediaPlayer.release();
        mediaPlayer = null;
        if (mAudioManager != null) { mAudioManager.abandonAudioFocus(afChangeListener); }
        if (tempFile != null && tempFile.exists()) { tempFile.delete();tempFile=null;}
        if (timer != null) this.timer.cancel();
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
        myLog("onUnBind()  " + intent.getDataString());
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
            loadFile(GetTempFilePathFromZipFile(zf), startAtZero);
        } else {
            String mPath = zf.getPath() + "/" + zf.getName();
            loadFile(mPath, startAtZero);
        }
    }

    private String GetTempFilePathFromZipFile(ZikFile file) {
        myLog("ZIP, createTempFile " + file.getPath() );
        String pathOfTempFile = "";
        String zipFilePath = file.getPath();
        String fileName = file.getName();


        try {
            if (tempFile != null && tempFile.exists()) { tempFile.delete();tempFile=null;}

            myLog("ZIP, instantiate ZipFile");
            //ZipFile zipFile = new ZipFile(zipFilePath);

            Uri uri = Uri.fromFile(new File(file.getPath()));
            myLog("Uri : "+ uri);

            InputStream inputStream = getContentResolver().openInputStream(Uri.fromFile(new File(file.getPath())));

            myLog("ZIP, about to create InputStream");
            //InputStream inputStream = zipFile.getInputStream(zipFile.getEntry(fileName));
            tempFile = File.createTempFile("_AUDIO_", getExtension(fileName));

            //tempFile.deleteOnExit();
            FileOutputStream out = new FileOutputStream(tempFile);
            myLog("ZIP, about to copy stream");
            copyStream(inputStream,out);
            pathOfTempFile = tempFile.getPath();

        } catch (IOException e) {
            myLogE("ZIP, Error creating temp file : " + e.getMessage());
            e.printStackTrace();
        } finally {
            Intent intent = new Intent(NOTIFICATION_ZIP_FILE_LOADED);
            sendBroadcast(intent);
        }
        return pathOfTempFile;
    }

    // TODO, use openFileDescriptor & remove legacy from manifest
    public boolean loadFile(String sPath, boolean startAtZero) {
        ErrorLoadingFile = false; // for onCompletion Next Track...
        if (!fileExists(sPath)) {
            myLogE("loadFile(sPath) : ERROR -- File doesn't exist !! " + sPath);
            ErrorLoadingFile=true;
            return false;
        }

        myLog("loadFile(sPath) [" + sPath + "]");
        try {
            mediaPlayer.stop();
            mediaPlayer.reset();
            mediaPlayer.setDataSource(sPath);
            mediaPlayer.prepare();
            if (startAtZero) {
                mediaPlayer.customSeekTo(0);
            } else {
                mediaPlayer.customSeekTo((int) PlayList.getZikFile().getPosition());
            }
            //fileHasBeenLoaded = true;
            Intent intent = new Intent(NOTIFICATION_FILELOADED);
            sendBroadcast(intent);
            myLog("------------------------------------------------------------"); // to get the chapters of a .m4b, you need ffmpeg...
            MediaPlayer.TrackInfo[] trackInfoArray = mediaPlayer.getTrackInfo();
            for (MediaPlayer.TrackInfo trackInfo : trackInfoArray) {
                myLog("trackInfo.toString() : " + trackInfo.toString());
            }

        } catch (Exception e) {
            myLogE("LoadFile - " + e.getMessage());
            myLogE(" +++++***+++++ ERROR LOADING FILE +++++***+++++ (" + sPath + ")");
            e.printStackTrace();
            ErrorLoadingFile=true;
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

                mAudioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
                afChangeListener = focusChange -> {
                    if (focusChange <= 0) {
                        myLog("Audio Focus Lost");
                        AudioService.this.pauseAudio();
                        //mediaSession.setActive(false); // CHECK
                        Intent intent = new Intent(NOTIFICATION_AUDIOFOCUS_LOST);
                        sendBroadcast(intent);
                    } else {
                        myLog("Audio Focus Gain");
                        AudioService.this.playAudio();
                        mediaSession.setActive(true);
                        Intent intent = new Intent(NOTIFICATION_AUDIOFOCUS_GAIN);
                        sendBroadcast(intent);
                    }
                };

                myLog("playAudio() : mAudioManager.requestAudioFocus, mediaPlayer.start()");
                mAudioManager.requestAudioFocus(afChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                mediaPlayer.start();
                setSpeed(getSpeed());
                startTimer();
                updatePlaybackState();
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
            if (mAudioManager != null) { mAudioManager.abandonAudioFocus(afChangeListener); }
            killTimer();
            updatePlaybackState();
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

    private int get_ForwardSeconds() {
        SharedPreferences prefs = this.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE);
        return prefs.getInt("FORWARD_SECONDS", DEFAULT_FORWARD_SECONDS);
    }

    public void forwardAudio() {
        myLog("forwardAudio()");
        int temp = getPosition();
        int lag = get_ForwardSeconds()*1000;
        if ((temp + lag ) <= getDuration()) {
            setPosition(temp + lag );
        }
    }

    public void backwardAudio() {
        myLog("backwardAudio()");
        int temp = getPosition();
        int lag = get_ForwardSeconds()*1000;
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
            myLog("setSpeed(" + speed + ")");
        } catch (Exception e) {
            myLogE("AudioService Error setting Speed");
            e.printStackTrace();
        }
        if (!(getCurrentZikFile()==null)) {
            saveSpeedToPref(speed);
        }
    }

    public double getSpeed() {
        //speed = mediaPlayer.getPlaybackParams().getSpeed();
        if (!(getCurrentZikFile()==null)) {
            speed = getSpeedFromPref();
        }
        if (speed == 0) speed = 1.0;
        myLog("getSpeed() : " + speed);
        return speed;
    }

    public void setPosition(int position) {
        mediaPlayer.customSeekTo(position);
        myLog("set Position : " + myDF.format(position));
    }

    public int getPosition() {
        //return mediaPlayer.getCurrentPosition();
        int curPosMediaPlayer = mediaPlayer.getCurrentPosition();
        int curPosGlobalVar = (int) PlayList.getZikFile().getPosition();
        int diff = curPosGlobalVar-curPosMediaPlayer;
        if (LOG_TRACE_ALL) myLog("getPosition() Saved/PlayerCurrent  " + curPosGlobalVar + "/" + curPosMediaPlayer + "  -  Diff = " + diff);
        //int pos = Math.max(curPosGlobalVar,curPosMediaPlayer);
        int pos = curPosMediaPlayer;
        return pos;
    }

    public int getDuration() {
        return (int) getCurrentZikFile().getDuration();
    }

    public boolean isPlaying() {
        if (LOG_TRACE_ALL) myLog("isPlaying()");
        if (exist()) {
            return mediaPlayer.isPlaying();
        } else {
            return false;
        }

    }
    public boolean exist() {
        if (LOG_TRACE_ALL) myLog("exist");
        return mediaPlayer != null;
    }

    private ZikFile getCurrentZikFile() {
        ZikFile zf = PlayList.getZikFile();
        if (!(zf==null)) {
            if (LOG_TRACE_ALL) myLog( "getCurrentZikFile() : " + zf.getName());
            return zf;
        } else {
            myLogE( "getCurrentZikFile() : NULL");
            return null;
        }
    }

    /********************************************************************************
     ***       MEDIA SESSION
     ********************************************************************************
     */
    /********************************************************************************
     ***       LOCKED SCREEN BUTTONS
     ********************************************************************************

    private void createNotificationWhenLocked() {
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                //.setSmallIcon(R.drawable.notification_icon)
                .setSmallIcon(R.drawable.vd_pause)
                .setContentTitle("My notification")
                .setContentText("Hello World!")
                //.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.large_icon))
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.vd_pause))
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0,1))
                //.addAction(new Notification.Action.Builder(Icon.createWithResource(this, R.drawable.vd_play), "Previous", prevPendingIntent).build())
                .addAction(new Notification.Action.Builder(Icon.createWithResource(this, R.drawable.vd_play), "Previous", prevPendingIntent).build())
                .addAction(new Notification.Action.Builder(Icon.createWithResource(this, R.drawable.vd_pause), "Pause", pausePendingIntent).build())
                .setPriority(Notification.PRIORITY_LOW);
    }
     */


    /********************************************************************************
     ***       TIMER
     ********************************************************************************
     */

    private void startTimer() {
        timer = new Timer();
        tempsEcoule = 0;
        SharedPreferences prefs = this.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE);
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                myLogD("Audio Service ----------------------------------------------------------------------------- " + tempsEcoule + "s. since timer started " );
                updateZikFileState(false);
                
                if (tempsEcoule > maxTimeBeforeSleep*60) {
                    myLog( "Max Playback Time Reached -- Stopping Service");
                    killTimer();

                    // 2 bips
                    if (prefs.getBoolean("BEEP_BOOKEND", DEFAULT_BEEP_BOOKEND)) {
                        ToneGenerator toneGen2 = new ToneGenerator(AudioManager.STREAM_MUSIC, 50);
                        toneGen2.startTone(ToneGenerator.TONE_DTMF_0,1000);
                    }

                    Intent intent = new Intent(NOTIFICATION_PLAYBACK_MAXTIMEREACH);
                    sendBroadcast(intent);
                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {mediaPlayer.stop();}
                    stopSelf();

                } else {
                    Intent intent = new Intent(NOTIFICATION_PLAYBACK_TIMER_VALUE);
                    intent.putExtra(TIMER_VALUE, tempsEcoule);
                    sendBroadcast(intent);
                }

                tempsEcoule = tempsEcoule + DELAY_CHECK_TIMER/1000;
            }
        }, 0,DELAY_CHECK_TIMER);
    }

    private void killTimer() {
        if (!(timer == null)) {
            try {
                timer.cancel();
                timer.purge();
                timer = null;
                String str;
                if (!(PlayList.getZikFilesList()==null)) {
                    str = getCurrentZikFile().getFolderName() + " : " + FormatTime(tempsEcoule*1000);
                } else {
                    str = "killTimer : ERROR zikFilePlayList==null";
                }
                myLog("Audio Service ----------------------------------------------------------------------------- " + tempsEcoule + "s. since timer started -- STOPPED -- " + str );
            } catch (Exception e) {
                myLogE("killTimer, nothing to kill ?");
                e.printStackTrace();
            }
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
            Thread one;
            one = new Thread(() -> {
                try {
                    int mySqlresponse=0;
                    AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
                    ZikFileDao zikFileDao = db.ZikFileDao();
                    mySqlresponse = zikFileDao.update(zf);
                    if (mySqlresponse>0) {
                        myLogD("updateZikFileState---------- zikFile updated (" + zf.getName() + ")- position : " + myDF.format(zf.getPosition()));
                        Sql.calculateFolderProgress(getApplicationContext(), zf.getIdFolder());
                        myLogD("updateZikFileState---------- Folder Progress updated");
                    } else {
                        myLogE("updateZikFileState---------- ZikFile NOT updated");
                    }
                } catch (Exception e) {
                    myLogE("updateZikFileState - Exception while Updating File progress in Thread - " + e.getMessage());
                }
            });
            one.start();
/*
            Observable.fromCallable(() -> {
                DatabaseClient
                        .getInstance(getApplicationContext())
                        .getAppDatabase()
                        .ZikFileDao()
                        .update(zf);
                return false;
            })
                    .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                    .subscribe(result -> {
                        myLogD("---------- zikFile updated (" + zf.getName() + ")- position : " + myDF.format(zf.getPosition()));
                        Sql.calculateFolderProgress(getApplicationContext(), zf.getIdFolder());
                    }, throwable -> {
                        myLog("error sql updating zikFile :" + throwable.getMessage());
                    });
 */
        } catch (Exception e) {
            myLogE("updateZikFileState - Updating File progress in Initialization - " + e.getMessage());
            e.printStackTrace();
        }


    }


    /** ----------------------------------------------------------------
    **           SHARED PREFS
    ** ----------------------------------------------------------------
    **/
    private void setMaxTimeBeforeSleep() {
        SharedPreferences prefs = this.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE);
        maxTimeBeforeSleep = prefs.getInt("TIME_BEFORE_SLEEP", DEFAULT_TIME_BEFORE_SLEEP);
    }

    private void saveSpeedToPref(double speed) {
        try {
            SharedPreferences.Editor editor = this.getSharedPreferences(SHARED_PREFERENCE_SPEED, MODE_PRIVATE).edit();
            editor.putString(String.valueOf(getCurrentZikFile().getIdFolder()),Double.toString(speed)).apply();
        } catch (Exception e) {
            myLogE("error saving speed in prefs");
            myLogE(e.getMessage());
        }
    }

    private double getSpeedFromPref() {
        try {
            SharedPreferences prefs = this.getSharedPreferences(SHARED_PREFERENCE_SPEED, MODE_PRIVATE);
            return Double.parseDouble(prefs.getString(String.valueOf(getCurrentZikFile().getIdFolder()), "1.0"));
        } catch (Exception e) {
            myLogE("error getting speed from prefs");
            myLogE(e.getMessage());
            return 1.0;
        }
    }
    //Lock Screen Actions
    private void updatePlaybackState() {
        myLog("updatePlaybackState()");
        //
        // PlaybackStateCompat.Builder
        stateBuilder = new PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_FAST_FORWARD | PlaybackStateCompat.ACTION_REWIND);
        // | PlaybackStateCompat.ACTION_STOP | PlaybackStateCompat.ACTION_SKIP_TO_NEXT);
        if (mediaPlayer.isPlaying()) {
            stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, mediaPlayer.getCurrentPosition(), 1.0f);
        } else {
            //arg pour ci dessous :   (long) PlayList.getZikFile().getPosition(), ((float) getSpeed()) => ca change rien !
            stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, mediaPlayer.getCurrentPosition(), 1.0f);
        }
        mediaSession.setPlaybackState(stateBuilder.build());
    }
    private void createNotification() {
        if (mediaPlayer == null) {return;}
        myLog("createNotification()");
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "music_channel")
                .setContentTitle(getCurrentZikFile().getFolderName())
                .setContentText(getCurrentZikFile().getDisplayName())
   //             .setProgress(100,50, true)
                .setSmallIcon(R.drawable.ic_sound)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .addAction(new NotificationCompat.Action(
                        R.drawable.ic_pause, "Pause",
                        MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE)))
                .addAction(new NotificationCompat.Action(
                        R.drawable.ic_play, "Play",
                        MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY)))
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1));
        startForeground(1, builder.build());
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "music_channel", "Music Playback",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Music Playback Controls");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    private void removeNotification() {
        stopForeground(true);
        stopSelf();
    }
    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }

}