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
import java.util.zip.ZipFile;

import static com.driot.bookplayer.utils.Tonio.fileExists;
import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Utils.copyStream;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 01/11/20
 */
public class AudioService extends Service {

    private final IBinder binder = new BackgroundBinder();
    public static final String TRACKNUMBER = "tracknumber";
    public static final String NOTIFICATION_FILELOADED = "NOTIFICATION_FILELOADED";
    public static final String NOTIFICATION_NEWTRACK = "NOTIFICATION_NEWTRACK";
    public static final String NOTIFICATION_TRACKFINISHED = "NOTIFICATION_TRACKFINISHED";
    public static final String NOTIFICATION_ERROR = "NOTIFICATION_ERROR";
    public static final String NOTIFICATION_AUDIOFOCUS_LOST = "NOTIFICATION_AUDIOFOCUS_LOST";
    public static final String NOTIFICATION_AUDIOFOCUS_GAIN = "NOTIFICATION_AUDIOFOCUS_GAIN";

    private static final boolean LOG_TRACE = false;
    private static final boolean LOG_TRACE_ALL = false;

    private MediaPlayer mediaPlayer;
    private AudioManager mAudioManager;
    private AudioManager.OnAudioFocusChangeListener afChangeListener;
    private AudioAttributes playbackAttributes;
    private AudioFocusRequest focusRequest;

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
        myLog("MusicService onCreate");
        super.onCreate();
        mediaPlayer = new MediaPlayer();

        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                if (!ErrorLoadingFile) {
                    myLog("MusicService onCompletion - nextTrack");
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
                myLog("MusicService - MediaPlayer On Error Fired : " + i + " : " + i1 );
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
        myLog("MusicService sendBroadcast alertNewTrack");
    }

    private void alertError() {
        Intent intent = new Intent(NOTIFICATION_ERROR);
        intent.putExtra(TRACKNUMBER, numSong);
        sendBroadcast(intent);
        myLog("MusicService sendBroadcast alertError");
    }

    private void alertTrackFinished() {
        Intent intent = new Intent(NOTIFICATION_TRACKFINISHED);
        sendBroadcast(intent);
        myLog("MusicService sendBroadcast alertTrackFinished");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        myLog("MusicService onStartCommand");
        return START_NOT_STICKY;
    }
    @Override
    public void onDestroy() {
        myLog("MusicService onDestroy");
        if (mediaPlayer.isPlaying()) {mediaPlayer.stop();}
        mediaPlayer.release();
        mediaPlayer = null;
        if (mAudioManager != null) { mAudioManager.abandonAudioFocus(afChangeListener); }
        if (tempFile != null && tempFile.exists()) { tempFile.delete();tempFile=null;}
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        myLog("MusicService onBind");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        myLog("MusicService onUnBind");
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
        myLog("MusicPlayer.loadFiles(array)");
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
        }
        return pathOfTempFile;
    }


    // TODO, use openFileDescriptor & remove legacy from manifest
    public boolean loadFile(String sPath) {
        ErrorLoadingFile = false; // for onCompletion Next Track...
        if (!fileExists(sPath)) {
            myLog("ERROR -- File doesn't exist !! " + sPath);
            ErrorLoadingFile=false;
            return false;
        }
        if (fileHasBeenLoaded) {
            myLog("ERROR -- File was already loaded !! " + sPath);
            return false;
        }
        myLog("MusicService loadFile(" + sPath + ")");
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

    public void start() {
        myLog("MusicPlayer.start()");
        if (!mediaPlayer.isPlaying()) {

            mAudioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
            afChangeListener = new AudioManager.OnAudioFocusChangeListener() {

                @Override
                public void onAudioFocusChange(int focusChange) {
                    if(focusChange<=0) {
                        myLog("Audio Focus Lost");
                        AudioService.this.pause();
                        Intent intent = new Intent(NOTIFICATION_AUDIOFOCUS_LOST);
                        sendBroadcast(intent);
                    } else {
                        myLog("Audio Focus Gain");
                        AudioService.this.start();
                        Intent intent = new Intent(NOTIFICATION_AUDIOFOCUS_GAIN);
                        sendBroadcast(intent);
                    }
                }
            };

            mAudioManager.requestAudioFocus(afChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

            mediaPlayer.start();
       }
    }

    public void pause() {
        myLog("MusicPlayer.pause()");
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            if (mAudioManager != null) { mAudioManager.abandonAudioFocus(afChangeListener); }
        }
    }

    public void setPosition(int position) {
        myLog("MusicPlayer.seekTo(" + position + ")");
        mediaPlayer.seekTo(position);
        //zikFilePlayList[numSong].setPosition(position);
    }

    public int getPosition() {
        if (LOG_TRACE_ALL) myLog("MusicPlayer.getPosition()");
        return mediaPlayer.getCurrentPosition();
    }

    public int getDuration() {
        myLog("MusicPlayer.getDuration()");
        return mediaPlayer.getDuration();
    }

    public boolean isPlaying() {
        if (LOG_TRACE_ALL) myLog("MusicPlayer.isPlaying()");
        return mediaPlayer.isPlaying();
    }
    public boolean exist() {
        if (LOG_TRACE_ALL) myLog("MusicPlayer.exist");
        if (mediaPlayer == null) {
            return false;
        } else {
            return true;
        }
    }

    public ZikFile getCurrentZikFile() {
        if (fileHasBeenLoaded) {
            Log.d("toto", "getCurrentZikFile : " + zikFilePlayList[numSong].getName());
            return zikFilePlayList[numSong];
        } else {
            Log.d("toto", "getCurrentZikFile : ERROR file not loaded");
            return null;
        }
    }

    public ZikFile getLastZikFile() {
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




    private void myLog(String str) {
        if (LOG_TRACE) { Log.d("toto",str); }
    }

}