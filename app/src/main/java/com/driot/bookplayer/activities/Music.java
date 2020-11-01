package com.driot.bookplayer.activities;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.widget.Toast;

import java.io.FileDescriptor;
import java.io.IOException;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 01/11/20
 */
public abstract class Music extends Service implements MediaPlayer.OnCompletionListener {
    MediaPlayer mediaPlayer;
    boolean isPrepared = false;

    //// TEstes de servico
    @Override
    public void onCreate() {
        super.onCreate();
        info("Servico criado!");
    }
    @Override
    public void onDestroy() {
        info("Servico fudeu!");
    }
    @Override
    public void onStart(Intent intent, int startid) {
        info("Servico started!");
    }
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    public void info(String txt) {
        Toast toast = Toast.makeText(getApplicationContext(), txt, Toast.LENGTH_LONG);
        toast.show();
    }
    //// Fim testes de servico

    public Music(FileDescriptor fileDescriptor){
        mediaPlayer = new MediaPlayer();
        try{
            mediaPlayer.setDataSource(fileDescriptor);
            mediaPlayer.prepare();
            isPrepared = true;
            mediaPlayer.setOnCompletionListener(this);
        } catch(Exception ex){
            throw new RuntimeException("Couldn't load music, uh oh!");
        }
    }

    public void onCompletion(MediaPlayer mediaPlayer) {
        synchronized(this){
            isPrepared = false;
        }
    }

    public void play() {
        if(mediaPlayer.isPlaying()) return;
        try{
            synchronized(this){
                if(!isPrepared){
                    mediaPlayer.prepare();
                }
                mediaPlayer.seekTo(0);
                mediaPlayer.start();
            }
        } catch(IllegalStateException | IOException ex){
            ex.printStackTrace();
        }
    }

    public void stop() {
        mediaPlayer.stop();
        synchronized(this){
            isPrepared = false;
        }
    }

    public void switchTracks(){
        mediaPlayer.seekTo(0);
        mediaPlayer.pause();
    }

    public void pause() {
        mediaPlayer.pause();
    }

    public boolean isPlaying() {
        return mediaPlayer.isPlaying();
    }

    public boolean isLooping() {
        return mediaPlayer.isLooping();
    }

    public void setLooping(boolean isLooping) {
        mediaPlayer.setLooping(isLooping);
    }

    public void setVolume(float volumeLeft, float volumeRight) {
        mediaPlayer.setVolume(volumeLeft, volumeRight);
    }

    public String getDuration() {
        return String.valueOf((int)(mediaPlayer.getDuration()/1000));
    }
    public void dispose() {
        if(mediaPlayer.isPlaying()){
            stop();
        }
        mediaPlayer.release();
    }
}