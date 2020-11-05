package com.driot.bookplayer.utils;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 01/11/20
 */
public class AudioService extends Service {

    private final IBinder binder = new BackgroundBinder();
    public static final String TRACKNUMBER = "tracknumber";
    public static final String NOTIFICATION_NEWTRACK = "NOTIFICATION_NEWTRACK";
    public static final String NOTIFICATION_TRACKFINISHED = "NOTIFICATION_TRACKFINISHED";

    private MediaPlayer mediaPlayer;
    private boolean fileHasBeenLoaded = false;
    private int numSong = 0;
    private String[] arrayPaths;

    // controle pour le debug...
    //private Timer timer;
    //private int increment = 0;

    /********************************************************************************
     ***       NATIVE METHODS
     ********************************************************************************
     *  Because service always runs in the same process as clients, no need IPC.
     *
     */
    @Override
    public void onCreate() {
        Log.d("toto", "MusicService onCreate");
        super.onCreate();
        mediaPlayer = new MediaPlayer();
/*
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                // Executer de votre tâche
                increment++;
                Log.d("toto", "Mon pti service " + increment);

            }
        }, 0, 1000);
*/
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                Log.d("toto","MusicService onCompletion - nextTrack");
                alertTrackFinished();
                fileHasBeenLoaded=false;
                nextTrack();
            }
        });

        mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
                Log.d("toto","MusicService - MediaPlayer On Error Fired : " + i + " : " + i1 );
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
        Log.d("toto","loading " + arrayPaths[numSong]);
        loadFile(arrayPaths[numSong]);
        mediaPlayer.start();
        alertNewTrack();
    }
        private void alertNewTrack() {
        Intent intent = new Intent(NOTIFICATION_NEWTRACK);
        intent.putExtra(TRACKNUMBER, numSong);
        sendBroadcast(intent);
        Log.d("toto","MusicService sendBroadcast alertNewTrack");
    }

    private void alertTrackFinished() {
        Intent intent = new Intent(NOTIFICATION_TRACKFINISHED);
        sendBroadcast(intent);
        Log.d("toto","MusicService sendBroadcast alertTrackFinished");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("toto", "MusicService onStartCommand");
        return START_NOT_STICKY;
    }
    @Override
    public void onDestroy() {
        Log.d("toto", "MusicService onDestroy");
        //this.timer.cancel();
        if (mediaPlayer.isPlaying()) {mediaPlayer.stop();}
        mediaPlayer.release();
        mediaPlayer = null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d("toto", "MusicService onBind");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d("toto", "MusicService onUnBind");
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

    // TODO, check File Exist
        /*
    File f = new File(filePath);
    if (f.exists()) { Log.d("titi","ok file found : " + filePath);} else {Log.d("titi","KO file not found : " + filePath);}
    */

    public void loadFiles(ArrayList<String> sPaths) {
        Log.d("toto","MusicPlayer.loadFiles(array)");
        arrayPaths = sPaths.toArray(new String[0]);
        numSong = 0;
        loadFile(arrayPaths[numSong]);
    }



        // TODO, use openFileDescriptor & remove legacy from manifest
    public void loadFile(String sPath) {
        if (!fileHasBeenLoaded) {
            Log.d("toto", "MusicService loadFile(" + sPath + ")");
            try {
                mediaPlayer.setDataSource(sPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                mediaPlayer.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
            fileHasBeenLoaded=true;
        } else {
            Log.d("toto", "File was already loaded !! " + sPath);
        }
    }

    public void start() {
        Log.d("toto","MusicPlayer.start()");
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    public void pause() {
        Log.d("toto","MusicPlayer.pause()");
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    public void setPosition(int position) {
        Log.d("toto","MusicPlayer.seekTo(" + position + ")");
        mediaPlayer.seekTo(position);
    }

    public int getPosition() {
        Log.d("toto","MusicPlayer.getPosition()");
        return mediaPlayer.getCurrentPosition();
    }

    public int getTrackNum() {
        Log.d("toto","MusicPlayer.getTrackNum()");
        return numSong;
    }

    public int getDuration() {
        Log.d("toto","MusicPlayer.getDuration()");
        return mediaPlayer.getDuration();
    }

    public boolean isPlaying() {
        Log.d("toto","MusicPlayer.isPlaying()");
        return mediaPlayer.isPlaying();
    }
    public boolean exist() {
        Log.d("toto","MusicPlayer.exist");
        if (mediaPlayer == null) {
            return false;
        } else {
            return true;
        }
    }


    }