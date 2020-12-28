package com.driot.bookplayer.utils;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.media.session.MediaSession;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.Nullable;

import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.Sql;
import com.driot.bookplayer.db.ZikFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Date;
import java.sql.Time;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.ZipFile;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static com.driot.bookplayer.activities.OptionActivity.DEFAULT_TIME_BEFORE_SLEEP;
import static com.driot.bookplayer.activities.OptionActivity.SHARED_PREFERENCE_TIME_BEFORE_SLEEP;
import static com.driot.bookplayer.utils.Tonio.FormatPercentDouble;
import static com.driot.bookplayer.utils.Tonio.fileExists;
import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Utils.copyStream;
import static com.driot.tonylib.KanLogger.myLog;
import static com.driot.tonylib.KanLogger.myLogE;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 01/11/20
 */
public class AudioService extends Service {

    private Timer timer;
    private int tempsEcoule = 0;
    public static final int DELAY_MAXPLAYBACK = 1000*60*60; //1h
    public static final int DELAY_CHECK_TIMER = 1000*5;

    static final String TAG = "MusicService";
    private static final boolean LOG_TRACE = true;
    private static final boolean LOG_TRACE_ALL = false;

    private final IBinder binder = new BackgroundBinder();
    public static final String TRACKNUMBER = "tracknumber";
    public static final String NOTIFICATION_FILELOADED = "NOTIFICATION_FILELOADED";
    public static final String NOTIFICATION_NEWTRACK = "NOTIFICATION_NEWTRACK";
    public static final String NOTIFICATION_TRACKFINISHED = "NOTIFICATION_TRACKFINISHED";
    public static final String NOTIFICATION_ERROR = "NOTIFICATION_ERROR";
    public static final String NOTIFICATION_AUDIOFOCUS_LOST = "NOTIFICATION_AUDIOFOCUS_LOST";
    public static final String NOTIFICATION_AUDIOFOCUS_GAIN = "NOTIFICATION_AUDIOFOCUS_GAIN";
    public static final String NOTIFICATION_ZIP_FILE_LOADED = "NOTIFICATION_ZIP_FILE_LOADED";
    public static final String NOTIFICATION_PLAYLISTFINISHED = "NOTIFICATION_PLAYLISTFINISHED";
    public static final String NOTIFICATION_PLAYBACK_MAXTIMEREACH = "NOTIFICATION_PLAYBACK_MAXTIMEREACH";

    private static final int FORWARD_TIME = 5*1000;
    private static final int BACKWARD_TIME = 5*1000;

    private MediaPlayer mediaPlayer;
    private AudioManager mAudioManager;
    private AudioManager.OnAudioFocusChangeListener afChangeListener;
    private MediaSession mediaSession;
    private int maxTimeBeforeSleep;


    private boolean fileHasBeenLoaded = false;
    private int numSong = 0;
    private double speed;

    private ZikFile[] zikFilePlayList;
    private File tempFile = null;

    private boolean ErrorLoadingFile = false;


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
        mediaPlayer = new MediaPlayer();
        mediaSession = new MediaSession(this, "MyMediaSession");
        configureMediaSession();
        setMaxTimeBeforeSleep();

        mediaPlayer.setOnCompletionListener(mediaPlayer -> {
            if (!ErrorLoadingFile) {
                updateZikFileState(true);
                alertTrackFinished();
                fileHasBeenLoaded=false;

                if (numSong+1 == zikFilePlayList.length) {
                    myLog("mediaPlayer.OnCompletionListener  => calling PlayListFinish");

                    // 3 bips
                    ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
                    toneGen1.startTone(ToneGenerator.TONE_CDMA_PIP,500);

                    alertPlaylistFinished();
                } else {
                    myLog("mediaPlayer.OnCompletionListener => calling nextTrack");
                    nextTrack();
                }
            }
        });

        mediaPlayer.setOnErrorListener((mediaPlayer, i, i1) -> {
            ErrorLoadingFile = true;
            myLog("mediaPlayer.OnErrorListener Fired : " + i + " : " + i1 );
            alertError();
            return false;
        });

    }

    void nextTrack() {
        numSong++;
        mediaPlayer.reset();
        int curNum = numSong + 1;
        myLog("loading next track : n°" + curNum + "/" + zikFilePlayList.length );

        // petit bip
        ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        toneGen1.startTone(ToneGenerator.TONE_CDMA_PIP,150);

        loadZeFile();
        mediaPlayer.start();
        alertNewTrack();
    }

    private void alertNewTrack() {
        Intent intent = new Intent(NOTIFICATION_NEWTRACK);
        intent.putExtra(TRACKNUMBER, numSong);
        sendBroadcast(intent);
        myLog("sendBroadcast alertNewTrack");
    }

    private void alertError() {
        Intent intent = new Intent(NOTIFICATION_ERROR);
        intent.putExtra(TRACKNUMBER, numSong);
        sendBroadcast(intent);
        myLog("sendBroadcast alertError");
    }

    private void alertTrackFinished() {
        Intent intent = new Intent(NOTIFICATION_TRACKFINISHED);
        sendBroadcast(intent);
        myLog("sendBroadcast alertTrackFinished");
    }

    private void alertPlaylistFinished() {
        Intent intent = new Intent(NOTIFICATION_PLAYLISTFINISHED);
        sendBroadcast(intent);
        myLog("sendBroadcast alertPlaylistFinished");
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        myLog("onStartCommand()");
        return START_NOT_STICKY;
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
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        myLog("onBind()");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        myLog("onUnBind()");
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
        myLog("loadFiles(array)");
        // sorte de constructeur
        numSong = 0;
        zikFilePlayList = zikFiles;

        // on charge le premier fichier
        loadZeFile();

    }

    private void loadZeFile() {
        if (zikFilePlayList[numSong].isIszipfile()) {
            loadFile(GetTempFilePathFromZipFile());
        } else {
            String mPath = zikFilePlayList[numSong].getPath() + "/" + zikFilePlayList[numSong].getName();
            loadFile(mPath);
        }
    }

    private String GetTempFilePathFromZipFile() {
        String pathOfTempFile = "";
        String zipFilePath = zikFilePlayList[numSong].getPath();
        String fileName = zikFilePlayList[numSong].getName();


        try {
            if (tempFile != null && tempFile.exists()) { tempFile.delete();tempFile=null;}
            ZipFile zipFile = new ZipFile(zipFilePath);
            InputStream inputStream = zipFile.getInputStream(zipFile.getEntry(fileName));
            tempFile = File.createTempFile("_AUDIO_", getExtension(fileName));
            //tempFile.deleteOnExit();
            FileOutputStream out = new FileOutputStream(tempFile);
            copyStream(inputStream,out);
            pathOfTempFile = tempFile.getPath();

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            Intent intent = new Intent(NOTIFICATION_ZIP_FILE_LOADED);
            sendBroadcast(intent);
        }
        return pathOfTempFile;
    }


    // TODO, use openFileDescriptor & remove legacy from manifest
    public boolean loadFile(String sPath) {
        ErrorLoadingFile = false; // for onCompletion Next Track...
        if (!fileExists(sPath)) {
            myLog("loadFile(sPath) : ERROR -- File doesn't exist !! " + sPath);
            ErrorLoadingFile=false;
            return false;
        }
        if (fileHasBeenLoaded) {
            myLog("loadFile(sPath) : ERROR -- File was already loaded !! " + sPath);
            return false;
        }
        myLog("loadFile(" + sPath + ")");
        try {
            mediaPlayer.setDataSource(sPath);
            mediaPlayer.prepare();
            fileHasBeenLoaded = true;
            Intent intent = new Intent(NOTIFICATION_FILELOADED);
            sendBroadcast(intent);
        } catch (Exception e) {
            myLog(" +++++***+++++ ERROR LOADING FILE +++++***+++++ (" + sPath + ")");
            e.printStackTrace();
            return false;
        }
        return true;
    }


    /********************************************************************************
     ***       PLAY-PAUSE
     ********************************************************************************
     */


    public void playAudio() {
        myLog("playAudio()");
        if (!mediaPlayer.isPlaying()) {

            mAudioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
            afChangeListener = focusChange -> {
                if(focusChange<=0) {
                    myLog("Audio Focus Lost");
                    AudioService.this.pauseAudio();
                    mediaSession.setActive(false);
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

            mAudioManager.requestAudioFocus(afChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

            mediaPlayer.start();
            startTimer();
       }
    }

    public void pauseAudio() {
        myLog("pauseAudio()");
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            updateZikFileState(false);
            if (mAudioManager != null) { mAudioManager.abandonAudioFocus(afChangeListener); }
            killTimer();
        }
    }

    public void playPauseAudio() {
        if (isPlaying()) {
            pauseAudio();
        } else {
            playAudio();
        }
    }

    public void forwardAudio() {
        myLog("forwardAudio()");
        int temp = getPosition();
        if ((temp + FORWARD_TIME ) <= getDuration()) {
            setPosition(temp + FORWARD_TIME );
        }
    }

    public void backwardAudio() {
        myLog("backwardAudio()");
        int temp = getPosition();
        if ((temp - BACKWARD_TIME) > 0) {
            setPosition(temp - BACKWARD_TIME);
        }
    }

    /********************************************************************************
     ***       SPEED - POSITION
     ********************************************************************************
     */


    public void setSpeed(double speed) {
        this.speed = speed;
        mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed((float) speed));
        myLog("setSpeed(" + speed + ")");
    }

    public double getSpeed() {
        speed = mediaPlayer.getPlaybackParams().getSpeed();
        if (speed == 0) speed = 1.0;
        myLog("getSpeed() : " + speed);
        return speed;
    }

    public void setPosition(int position) {
        //myLog("setPosition-seekTo(" + position + ")");
        mediaPlayer.seekTo(position);
    }

    public int getPosition() {
        if (LOG_TRACE_ALL) myLog("getPosition()");
        int curPos = mediaPlayer.getCurrentPosition();
        getCurrentZikFile().setPosition(curPos);
        return curPos;
    }

    public int getDuration() {
        myLog("getDuration()");
        //return mediaPlayer.getDuration();
        return (int) getCurrentZikFile().getDuration();
    }

    public boolean isPlaying() {
        if (LOG_TRACE_ALL) myLog("isPlaying()");
        return mediaPlayer.isPlaying();
    }
    public boolean exist() {
        if (LOG_TRACE_ALL) myLog("exist");
        if (mediaPlayer == null) {
            return false;
        } else {
            return true;
        }
    }

    public ZikFile getCurrentZikFile() {
        if (fileHasBeenLoaded) {
            if (LOG_TRACE_ALL) myLog( "getCurrentZikFile() : " + zikFilePlayList[numSong].getName());
            return zikFilePlayList[numSong];
        } else {
            myLog( "getCurrentZikFile() : ERROR file not loaded");
            return null;
        }
    }

    public ZikFile getLastZikFile() {
        myLog( "getLastZikFile()");
        if (fileHasBeenLoaded) {
            if (numSong > 0) {
                return zikFilePlayList[numSong - 1];
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private void configureMediaSession() {
        myLog("configureMediaSession()");

        // Overridden methods in the MediaSession.Callback class.
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                myLog("onMediaButtonEvent called: " + mediaButtonIntent);
                KeyEvent ke = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                myLog("onMediaButtonEvent Received command: " + ke);
                if (ke != null && ke.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (ke.getKeyCode()) {
                        case KeyEvent.KEYCODE_MEDIA_PLAY:
                            myLog("onMediaButtonEvent --- Play pressed --- KEYCODE");
                            playPauseAudio();
                            break;
                        case KeyEvent.KEYCODE_MEDIA_PAUSE:
                            myLog("onMediaButtonEvent --- Pause pressed --- KEYCODE");
                            playPauseAudio();
                            break;
                        case KeyEvent.KEYCODE_HEADSETHOOK:
                            myLog("onMediaButtonEvent --- PlayPause pressed --- KEYCODE");
                            playPauseAudio();
                            break;
                        case KeyEvent.KEYCODE_MEDIA_NEXT:
                            myLog("onMediaButtonEvent --- Next pressed --- KEYCODE");
                            forwardAudio();
                            break;
                        case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                            myLog("onMediaButtonEvent --- Previous pressed --- KEYCODE");
                            backwardAudio();
                            break;

                    }
                }
                return super.onMediaButtonEvent(mediaButtonIntent);
            }
        });
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setActive(true);
    }


    private void startTimer() {
        timer = new Timer();
        tempsEcoule = 0;
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                tempsEcoule = tempsEcoule + DELAY_CHECK_TIMER/1000;
                myLog( "AudioService started since " + tempsEcoule + " seconds");
                updateZikFileState(false);
                
                if (tempsEcoule > maxTimeBeforeSleep*60) {
                    myLog( "Max Playback Time Reached -- Stopping Service");

                    ToneGenerator toneGen2 = new ToneGenerator(AudioManager.STREAM_MUSIC, 50);
                    toneGen2.startTone(ToneGenerator.TONE_DTMF_0,1000);

                    Intent intent = new Intent(NOTIFICATION_PLAYBACK_MAXTIMEREACH);
                    sendBroadcast(intent);
                    killTimer();
                    mediaPlayer.stop();
                    stopSelf();

                }
            }
        }, 0,DELAY_CHECK_TIMER);
    }

    private void killTimer() {
        try {
            timer.cancel();
            timer.purge();
            timer = null;
        } catch (Exception e) {
            myLogE("killTimer, nothing to kill ?");
            e.printStackTrace();
        }
    }

    /********************************************************************************
     ***       UPDATE DB
     ********************************************************************************
     */
    private void updateZikFileState(boolean bFinished) {
        myLog("---------- ZikFile update start");
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
                //zf.setPosition(getPosition());
                zf.setPercentdone(FormatPercentDouble((double) getPosition() / getDuration()));
                //if (zf.getDuration() == 0) zf.setDuration(getDuration());
            }

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
                        myLog("---------- zikFile updated (" + zf.getName() + ")- position : " + zf.getPosition());
                        Sql.calculateFolderProgress(getApplicationContext(), zf.getIdFolder());
                    }, throwable -> {
                        myLogE("error sql updating zikFile :" + throwable.getMessage());
                    });

        } catch (Exception e) {
            myLog("==== ERROR ==== Updating File progress ");
        }


    }


    private void setMaxTimeBeforeSleep() {
        SharedPreferences prefs = this.getSharedPreferences(SHARED_PREFERENCE_TIME_BEFORE_SLEEP, MODE_PRIVATE);
        maxTimeBeforeSleep = prefs.getInt("TIME_BEFORE_SLEEP", DEFAULT_TIME_BEFORE_SLEEP);
    }


}