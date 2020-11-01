package com.driot.bookplayer.activities;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 01/11/20
 */
public class PlayService extends Service implements IPlayService {

    //private MediaPlayer mediaPlayer;
    //private double currentPosition;

    private Timer timer;
    private long increment;

    public static final int PERIOD_TIMER_RUN = 1000;

    private PlayServiceBinder binder ;
    private List<IPlayServiceListener> listeners = null;
    private static IPlayService service;

/*
    public static IPlayService getService() {
        return service;
    }
*/

    @Override
    public void onCreate() {
        Log.d("toto" + this.getClass().getName(), "onCreate");
        super.onCreate();
        service = this;
        timer = new Timer();
        binder = new PlayServiceBinder(this);
        _onStart();

        //mediaPlayer = new MediaPlayer();
        //Log.d(this.getClass().getName(), "mediaPlayer created");
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("toto" + this.getClass().getName(), "onStartCommand");
        _onStart();
        return START_NOT_STICKY;
    }

    public void _onStart(){
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                if(listeners != null){
                    fireDataChanged(new Object());
                }
                // Executer de votre tâche
                increment++;
                Log.d("toto", "Mon pti service " + increment + " sec");
            }}, 0, PERIOD_TIMER_RUN);
    }




    @Override
    public void onDestroy() {
        this.listeners.clear();
        this.timer.cancel();
        Log.d("toto" + this.getClass().getName(), "onDestroy");

        //if (mediaPlayer.isPlaying()) {mediaPlayer.stop();}
        //mediaPlayer.release();
        //mediaPlayer = null;
    }

    // Ajout d'un listener
    public void addListener(IPlayServiceListener listener) {
        Log.d("toto" + this.getClass().getName(), "addListener");
/*
        if(listeners == null){
            listeners = new ArrayList<IGoogleWeatherListener>();
        }
*/
        listeners.add(listener);
    }

    // Suppression d'un listener
    public void removeListener(IPlayServiceListener listener) {
        if(listeners != null){
            listeners.remove(listener);
        }
    }

    // Notification des listeners
    private void fireDataChanged(Object data){
        Log.d("toto" + this.getClass().getName(), "fireDataChanged");
        if(listeners != null){
            for(IPlayServiceListener listener: listeners){
                listener.dataChanged(data);
            }
        }
    }
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d("toto" + this.getClass().getName(), "onBind");
        return null;
    }



}
