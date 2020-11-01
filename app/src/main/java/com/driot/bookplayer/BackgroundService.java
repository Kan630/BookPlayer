package com.driot.bookplayer;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

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
    // Random number generator
    private final Random mGenerator = new Random();
    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    @Override
    public void onCreate() {
        Log.d("toto", "Service onCreate");
        super.onCreate();
        timer = new Timer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("toto", "Service onStartCommand");
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                // Executer de votre tâche
                increment++;
                Log.d("toto", "Mon pti service " + increment);
            }
        }, 0, 1000);

        return START_NOT_STICKY;
    }

    /** method for clients */
    public int getRandomNumber() {
        return mGenerator.nextInt(100);
    }

    public void pauseTimer() {
    }

    public double getPosition() {
        return increment;
    }

    public void setPosition(double position) {
        this.increment = (int) position;
    }

    @Override
    public void onDestroy() {
        Log.d("toto", "Service onDestroy");
        this.timer.cancel();
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