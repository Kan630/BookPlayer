package com.driot.bookplayer.utils;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
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
    public static final String NOTIFICATION_AUDIOFOCUS_LOST = "NOTIFICATION_AUDIOFOCUS_LOST";
    public static final String NOTIFICATION_AUDIOFOCUS_GAIN = "NOTIFICATION_AUDIOFOCUS_GAIN";

    private static final boolean LOG_TRACE = false;
    
    private MediaPlayer mediaPlayer;
    private AudioManager mAudioManager;
    private AudioManager.OnAudioFocusChangeListener afChangeListener;
    private AudioAttributes playbackAttributes;
    private AudioFocusRequest focusRequest;

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
        myLog("MusicService onCreate");
        super.onCreate();
        mediaPlayer = new MediaPlayer();

        mAudioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        afChangeListener = new AudioManager.OnAudioFocusChangeListener() {

            @Override
            public void onAudioFocusChange(int focusChange) {
                if(focusChange<=0) {
                    //LOSS -> PAUSE
                    myLog("Audio Focus Lost");
                    AudioService.this.pause();
                    Intent intent = new Intent(NOTIFICATION_AUDIOFOCUS_LOST);
                    sendBroadcast(intent);
                } else {
                    //GAIN -> PLAY
                    myLog("Audio Focus Gain");
                    AudioService.this.start();
                    Intent intent = new Intent(NOTIFICATION_AUDIOFOCUS_GAIN);
                    sendBroadcast(intent);
                }
            }
        };
        mAudioManager.requestAudioFocus(afChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);


/*
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                // Executer de votre tâche
                increment++;
                myLog("Mon pti service " + increment);

            }
        }, 0, 1000);
*/
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                myLog("MusicService onCompletion - nextTrack");
                alertTrackFinished();
                fileHasBeenLoaded=false;
                nextTrack();
            }
        });

        mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
                myLog("MusicService - MediaPlayer On Error Fired : " + i + " : " + i1 );
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
        myLog("loading " + arrayPaths[numSong]);
        loadFile(arrayPaths[numSong]);
        mediaPlayer.start();
        alertNewTrack();
    }
        private void alertNewTrack() {
        Intent intent = new Intent(NOTIFICATION_NEWTRACK);
        intent.putExtra(TRACKNUMBER, numSong);
        sendBroadcast(intent);
        myLog("MusicService sendBroadcast alertNewTrack");
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
        //this.timer.cancel();
        if (mediaPlayer.isPlaying()) {mediaPlayer.stop();}
        mediaPlayer.release();
        mediaPlayer = null;
        if (mAudioManager != null) { mAudioManager.abandonAudioFocus(afChangeListener); }
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

    // TODO, check File Exist
        /*
    File f = new File(filePath);
    if (f.exists()) { Log.d("titi","ok file found : " + filePath);} else {Log.d("titi","KO file not found : " + filePath);}
    */

    public void loadFiles(ArrayList<String> sPaths) {
        myLog("MusicPlayer.loadFiles(array)");
        arrayPaths = sPaths.toArray(new String[0]);
        numSong = 0;
        loadFile(arrayPaths[numSong]);
    }



        // TODO, use openFileDescriptor & remove legacy from manifest
    public void loadFile(String sPath) {
        if (!fileHasBeenLoaded) {
            myLog("MusicService loadFile(" + sPath + ")");
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
            myLog("File was already loaded !! " + sPath);
        }
    }

    public void start() {
        myLog("MusicPlayer.start()");
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();



            /*
            mAudioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
            AudioManager.OnAudioFocusChangeListener afChangeListener = null;
            //mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            // Request audio focus for playback
            int result = mAudioManager.requestAudioFocus(afChangeListener,
                    // Use the music stream.
                    AudioManager.STREAM_MUSIC,
                    // Request permanent focus.
                    AudioManager.AUDIOFOCUS_GAIN);

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                // Start playback
                mediaPlayer.start();
            }
/*
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                playbackAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(playbackAttributes)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(afChangeListener, handler)
                        .build();

                int res = mAudioManager.requestAudioFocus(focusRequest);
                synchronized(focusLock) {
                    if (res == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
                        playbackNowAuthorized = false;
                    } else if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        playbackNowAuthorized = true;
                        playbackNow();
                    } else if (res == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
                        playbackDelayed = true;
                        playbackNowAuthorized = false;
                    }
                }
            } else {
                mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            }



                // ...
/*


            playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            AudioFocusRequest focusRequest;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(playbackAttributes)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(afChangeListener, handler)
                        .build();
            } else {

            }
  */

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
    }

    public int getPosition() {
        myLog("MusicPlayer.getPosition()");
        return mediaPlayer.getCurrentPosition();
    }

    public int getTrackNum() {
        myLog("MusicPlayer.getTrackNum()");
        return numSong;
    }

    public int getDuration() {
        myLog("MusicPlayer.getDuration()");
        return mediaPlayer.getDuration();
    }

    public boolean isPlaying() {
        myLog("MusicPlayer.isPlaying()");
        return mediaPlayer.isPlaying();
    }
    public boolean exist() {
        myLog("MusicPlayer.exist");
        if (mediaPlayer == null) {
            return false;
        } else {
            return true;
        }
    }

    private void myLog(String str) {
        if (LOG_TRACE) { Log.d("toto",str); }
    }
    
}