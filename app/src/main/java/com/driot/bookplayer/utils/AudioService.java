package com.driot.bookplayer.utils;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.db.ZikFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.ZipFile;

import static com.driot.bookplayer.utils.Tonio.fileExists;
import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Utils.copyStream;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 01/11/20
 */
public class AudioService extends Service {

/*
    private Timer timer;
    private int tempsEcoule = 0;
    public static final int DELAY_MAXPLAYBACK = 1000*60*60; //1h
    public static final int DELAY_CHECK_TIMER = 1000*5;
*/

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
    public static final String NOTIFICATION_PLAYBACK_MAXTIMEREACH = "NOTIFICATION_PLAYBACK_MAXTIMEREACH";


    private MediaPlayer mediaPlayer;
    private AudioManager mAudioManager;
    private AudioManager.OnAudioFocusChangeListener afChangeListener;

    private boolean fileHasBeenLoaded = false;
    private int numSong = 0;

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

        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                if (!ErrorLoadingFile) {
                    myLog("mediaPlayer.OnErrorListener - nextTrack");
                    alertTrackFinished();
                    fileHasBeenLoaded=false;
                    nextTrack();
                }
            }
        });

        mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
                ErrorLoadingFile = true;
                myLog("mediaPlayer.OnErrorListener Fired : " + i + " : " + i1 );
                alertError();
                return false;
            }
        });

    }

    void nextTrack() {
        numSong++;
        mediaPlayer.reset();
        // TODO petit bip
        //mediaPlayer.create(this, Settings.System.DEFAULT_RINGTONE_URI);
        //mediaPlayer.start();
        //mediaPlayer.reset();

        myLog("loading next track");
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
        //this.timer.cancel();
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
     ***       USER METHODS
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

    public void playAudio() {
        myLog("playAudio()");
        if (!mediaPlayer.isPlaying()) {

            mAudioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
            afChangeListener = new AudioManager.OnAudioFocusChangeListener() {

                @Override
                public void onAudioFocusChange(int focusChange) {
                    if(focusChange<=0) {
                        myLog("Audio Focus Lost");
                        AudioService.this.pause();
                        //mediaPlayer.pause();
                        Intent intent = new Intent(NOTIFICATION_AUDIOFOCUS_LOST);
                        sendBroadcast(intent);
                    } else {
                        myLog("Audio Focus Gain");
                        //AudioService.this.start();
                        AudioService.this.playAudio();
                        //mediaPlayer.start();
                        Intent intent = new Intent(NOTIFICATION_AUDIOFOCUS_GAIN);
                        sendBroadcast(intent);
                    }
                }
            };

            mAudioManager.requestAudioFocus(afChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

            mediaPlayer.start();
            //StartTimer();
       }
    }

    public void pause() {
        myLog("pause()");
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            if (mAudioManager != null) { mAudioManager.abandonAudioFocus(afChangeListener); }
        }
    }

    public void setSpeed(float speed) {
        mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(speed));
        myLog("setSpeed(" + speed + ")");
    }

    public float getSpeed() {
        float speed = mediaPlayer.getPlaybackParams().getSpeed();
        myLog("getSpeed() : " + speed);
        return speed;
    }

    public void setPosition(int position) {
        myLog("setPosition-seekTo(" + position + ")");
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

    public boolean hasBeenLoaded() {
        if (fileHasBeenLoaded) {
            return true;
        } else {
            return false;
        }
    }

/*
    private void StartTimer() {
        timer = new Timer();
        tempsEcoule = 0;
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                tempsEcoule = tempsEcoule + DELAY_CHECK_TIMER/1000;
                myLog( "AudioService started since " + tempsEcoule + " seconds");
                if (tempsEcoule>DELAY_MAXPLAYBACK) {
                    myLog( "Max Playback Time Reached -- Stopping Service");
                    Intent intent = new Intent(NOTIFICATION_PLAYBACK_MAXTIMEREACH);
                    sendBroadcast(intent);
                    timer.cancel();
                    mediaPlayer.stop();
                    stopSelf();
                }
            }
        }, 0,DELAY_CHECK_TIMER);
    }
 */


    private void myLog(String str) {
        if (LOG_TRACE) { Log.d("toto " + TAG + " ",str); }
    }

}