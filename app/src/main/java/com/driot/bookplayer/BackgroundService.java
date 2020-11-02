package com.driot.bookplayer;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.telephony.ClosedSubscriberGroupInfo;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.driot.bookplayer.activities.PlayActivity;

import java.io.IOException;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 01/11/20
 */
public class BackgroundService extends Service {

    private final IBinder binder = new BackgroundBinder();

    private Timer timer;
    private int increment = 0;
    private final Random mGenerator = new Random();

    private MediaPlayer mediaPlayer;

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    @Override
    public void onCreate() {
        Log.d("toto", "Service onCreate");
        super.onCreate();
        mediaPlayer = new MediaPlayer();

        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                // Executer de votre tâche
                increment++;
                Log.d("toto", "Mon pti service " + increment);

            }
        }, 0, 1000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("toto", "Service onStartCommand");
        return START_STICKY;
    }

    /** method for clients */
    public int getRandomNumber() {
        return mGenerator.nextInt(100);
    }

    // TODO, use openFileDescriptor & remove legacy from manifest
    public void loadFile(String sPath) {
        Log.d("toto", "Loading File " + sPath);
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
        if (mediaPlayer == null) {
        }
    }

    public String getTrackInfo() {
        return String.valueOf(mediaPlayer.getTrackInfo());
    }

    public void start() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    public void pause() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    public void setPosition(int position) {
        mediaPlayer.seekTo(position);
    }

    public int getPosition() {
        return mediaPlayer.getCurrentPosition();
    }

    public int getDuration() {
        return mediaPlayer.getDuration();
    }

    public boolean isPlaying() {
        return mediaPlayer.isPlaying();
    }


    @Override
    public void onDestroy() {
        Log.d("toto", "Service onDestroy");
        this.timer.cancel();
        if (mediaPlayer.isPlaying()) {mediaPlayer.stop();}
        mediaPlayer.release();
        mediaPlayer = null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d("toto", "Service onBind");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d("toto", "Service onUnBind");
        return super.onUnbind(intent);
    }

    public class BackgroundBinder extends Binder {
        public BackgroundService getService() {
            // Return this instance of LocalService so clients can call public methods
            return BackgroundService.this;
        }
    }


}