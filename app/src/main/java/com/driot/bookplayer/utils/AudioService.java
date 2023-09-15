package com.driot.bookplayer.utils;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.view.KeyEvent;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.global.PlayList;
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
import static com.driot.bookplayer.activities.PlayActivity.SHARED_PREFERENCE_SPEED;
import static com.driot.bookplayer.utils.Tonio.FormatPercentDouble;
import static com.driot.bookplayer.utils.Tonio.FormatTime;
import static com.driot.bookplayer.utils.Tonio.fileExists;
import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Utils.copyStream;
import static com.driot.tonylib.KanLogger.myLog;
import static com.driot.tonylib.KanLogger.myLogE;

import com.driot.bookplayer.R;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 01/11/20
 */
public class AudioService extends Service {

    private static final String CHANNEL_ID = "mychanelID129111";
    private Timer timer;
    private int tempsEcoule = 0;
    public static final int DELAY_MAXPLAYBACK = 1000*60*60; //1h
    public static final int DELAY_CHECK_TIMER = 1000*5;

    static final String TAG = "MusicService";
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
    private PlaybackState.Builder stateBuilder;
    private int maxTimeBeforeSleep;


    //private boolean fileHasBeenLoaded = false;
    private double speed = 1.0;

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
        myLog("Audio Service : onCreate()");
        super.onCreate();
        mediaPlayer = new MediaPlayer();
        mediaSession = new MediaSession(this, "MyTotoMediaSession");
        configureMediaSession();
        setMaxTimeBeforeSleep();
        createNotificationWhenLocked();

        // Create a new PlaybackState.Builder
        stateBuilder = new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE);
        mediaSession.setPlaybackState(stateBuilder.build());

        mediaPlayer.setOnCompletionListener(mediaPlayer -> {
            if (!ErrorLoadingFile) {
                updateZikFileState(true);
                alertTrackFinished();
                //fileHasBeenLoaded=false;

                if (PlayList.getNumZikFile()+1 == PlayList.getZikFilesList().size()) {
                    myLog("AudioService - mediaPlayer.OnCompletionListener  => calling PlayListFinish");

                    // 3 bips
                    ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
                    toneGen1.startTone(ToneGenerator.TONE_CDMA_PIP,500);

                    alertPlaylistFinished();
                } else {
                    myLog("AudioService - mediaPlayer.OnCompletionListener => calling nextTrack");
                    nextTrack();
                }
            }
        });

        mediaPlayer.setOnErrorListener((mediaPlayer, i, i1) -> {
            ErrorLoadingFile = true;
            myLog("AudioService - mediaPlayer.OnErrorListener Fired : " + i + " : " + i1 );
            alertError();
            return false;
        });

    }

    void nextTrack() {
        myLog("Audio Service : Next track");
        PlayList.setNumZikFile(PlayList.getNumZikFile()+1);
        mediaPlayer.reset();
        int curNum = PlayList.getNumZikFile() + 1;
        myLog("AudioService - loading next track : n°" + curNum + "/" + PlayList.getZikFilesList().size() );

        // petit bip
        ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        toneGen1.startTone(ToneGenerator.TONE_CDMA_PIP,150);

        loadZeFile();
        //TODO remplace par PlayAudio() ??
        myLog("AudioService : mediaPlayer.start() -- nextrack");
        mediaPlayer.start();
        setSpeed(getSpeed());
        alertNewTrack();
    }

    private void alertNewTrack() {
        Intent intent = new Intent(NOTIFICATION_NEWTRACK);
        intent.putExtra(TRACKNUMBER, PlayList.getNumZikFile());
        sendBroadcast(intent);
        myLog("AudioService - sendBroadcast alertNewTrack ");
    }

    private void alertError() {
        Intent intent = new Intent(NOTIFICATION_ERROR);
        intent.putExtra(TRACKNUMBER, PlayList.getNumZikFile());
        sendBroadcast(intent);
        myLog("AudioService - sendBroadcast alertError");
    }

    private void alertTrackFinished() {
        Intent intent = new Intent(NOTIFICATION_TRACKFINISHED);
        sendBroadcast(intent);
        myLog("AudioService --------------------------------------------------------------------------------- sendBroadcast alertTrackFinished --------------------------------------------------------------------------------");
    }

    private void alertPlaylistFinished() {
        Intent intent = new Intent(NOTIFICATION_PLAYLISTFINISHED);
        sendBroadcast(intent);
        myLog("AudioService - sendBroadcast alertPlaylistFinished");
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        myLog("Audio Service : onStartCommand()" + intent.toString());
        return START_NOT_STICKY; //TODO maybe to change... because memory pressure could kill it
    }
    @Override
    public void onDestroy() {
        myLog("Audio Service : onDestroy()");
        if (mediaPlayer.isPlaying()) {mediaPlayer.stop();}
        mediaPlayer.release();
        mediaPlayer = null;
        if (mAudioManager != null) { mAudioManager.abandonAudioFocus(afChangeListener); }
        if (tempFile != null && tempFile.exists()) { tempFile.delete();tempFile=null;}
        if (timer != null) this.timer.cancel();
        if(mediaSession != null) { mediaSession.release(); }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        myLog("Audio Service : onBind()");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        myLog("Audio Service : onUnBind()  " + intent.getDataString());
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
        myLog("AudioService - loadFiles(array) - folder : " + zikFiles[0].getIdFolder());
        loadZeFile();
    }

    private void loadZeFile() {
        myLog("AudioService - loadZeFile()");
        ZikFile zf = PlayList.getZikFile();
        if (zf.isIszipfile()) {
            loadFile(GetTempFilePathFromZipFile(zf));
        } else {
            String mPath = zf.getPath() + "/" + zf.getName();
            loadFile(mPath);
        }
    }

    private String GetTempFilePathFromZipFile(ZikFile file) {
        myLog("AudioService : ZIP, createTempFile " + file.getPath() );
        String pathOfTempFile = "";
        String zipFilePath = file.getPath();
        String fileName = file.getName();


        try {
            if (tempFile != null && tempFile.exists()) { tempFile.delete();tempFile=null;}

            myLog("AudioService : ZIP, instantiate ZipFile");
            //ZipFile zipFile = new ZipFile(zipFilePath);

            Uri uri = Uri.fromFile(new File(file.getPath()));
            myLog("Uri : "+ uri);

            InputStream inputStream = getContentResolver().openInputStream(Uri.fromFile(new File(file.getPath())));

            myLog("AudioService : ZIP, about to create InputStream");
            //InputStream inputStream = zipFile.getInputStream(zipFile.getEntry(fileName));
            tempFile = File.createTempFile("_AUDIO_", getExtension(fileName));

            //tempFile.deleteOnExit();
            FileOutputStream out = new FileOutputStream(tempFile);
            myLog("AudioService : ZIP, about to copy stream");
            copyStream(inputStream,out);
            pathOfTempFile = tempFile.getPath();

        } catch (IOException e) {
            myLogE("AudioService : ZIP, Error creating temp file : " + e.getMessage());
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
            myLogE("AudioService - loadFile(sPath) : ERROR -- File doesn't exist !! " + sPath);
            ErrorLoadingFile=true;
            return false;
        }

        myLog("AudioService - loadFile(" + sPath + ")");
        try {
            mediaPlayer.stop();
            mediaPlayer.reset();
            mediaPlayer.setDataSource(sPath);
            mediaPlayer.prepare();
            mediaPlayer.seekTo((int) PlayList.getZikFile().getPosition());
            //fileHasBeenLoaded = true;
            Intent intent = new Intent(NOTIFICATION_FILELOADED);
            sendBroadcast(intent);
        } catch (Exception e) {
            myLogE("AudioService : LoadFile - " + e.getMessage());
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
        myLog("AudioService.playAudio()");
        if (!mediaPlayer.isPlaying()) {

            mAudioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
            afChangeListener = focusChange -> {
                if(focusChange<=0) {
                    myLog("Audio Service : Audio Focus Lost");
                    AudioService.this.pauseAudio();
                    //mediaSession.setActive(false); // CHECK
                    Intent intent = new Intent(NOTIFICATION_AUDIOFOCUS_LOST);
                    sendBroadcast(intent);
                } else {
                    myLog("Audio Service : Audio Focus Gain");
                    AudioService.this.playAudio();
                    mediaSession.setActive(true);
                    Intent intent = new Intent(NOTIFICATION_AUDIOFOCUS_GAIN);
                    sendBroadcast(intent);
                }
            };

            mAudioManager.requestAudioFocus(afChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

            myLog("AudioService : mediaPlayer.start() -- playAudio");
            mediaPlayer.start();
            setSpeed(getSpeed());
            startTimer();
       }
    }

    public void pauseAudio() {
        myLog("AudioService : pauseAudio()");
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
        myLog("AudioService.forwardAudio()");
        int temp = getPosition();
        if ((temp + FORWARD_TIME ) <= getDuration()) {
            setPosition(temp + FORWARD_TIME );
        }
    }

    public void backwardAudio() {
        myLog("AudioService.backwardAudio()");
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
        try {
            this.speed = speed;
            if (mediaPlayer!=null && mediaPlayer.isPlaying()) {
                mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed((float) speed));
            }
            myLog("AudioService.setSpeed(" + speed + ")");
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
        myLog("AudioService.getSpeed() : " + speed);
        return speed;
    }

    public void setPosition(int position) {
        //myLog("setPosition-seekTo(" + position + ")");
        mediaPlayer.seekTo(position);
    }

    public int getPosition() {
        //return mediaPlayer.getCurrentPosition();
        int curPosMediaPlayer = mediaPlayer.getCurrentPosition();
        int curPosGlobalVar = (int) PlayList.getZikFile().getPosition();
        int diff = curPosGlobalVar-curPosMediaPlayer;
        if (LOG_TRACE_ALL) myLog("AudioService.getPosition() Saved/PlayerCurrent  " + curPosGlobalVar + "/" + curPosMediaPlayer + "  -  Diff = " + diff);
        //int pos = Math.max(curPosGlobalVar,curPosMediaPlayer);
        int pos = curPosMediaPlayer;
        return pos;
    }

    public int getDuration() {
        return (int) getCurrentZikFile().getDuration();
    }

    public boolean isPlaying() {
        if (LOG_TRACE_ALL) myLog("AudioService.isPlaying()");
        if (exist()) {
            return mediaPlayer.isPlaying();
        } else {
            return false;
        }

    }
    public boolean exist() {
        if (LOG_TRACE_ALL) myLog("AudioService.exist");
        if (mediaPlayer == null) {
            return false;
        } else {
            return true;
        }
    }

    private ZikFile getCurrentZikFile() {
        ZikFile zf = PlayList.getZikFile();
        if (!(zf==null)) {
            if (LOG_TRACE_ALL) myLog( "AudioService.getCurrentZikFile() : " + zf.getName());
            return zf;
        } else {
            myLogE( "AudioService.getCurrentZikFile() : NULL");
            return null;
        }
    }

    /********************************************************************************
     ***       MEDIA SESSION
     ********************************************************************************
     */
    private void configureMediaSession() {
        myLog("Audio Service : configureMediaSession()");

        // Overridden methods in the MediaSession.Callback class.
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                KeyEvent ke = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                myLog("AudioService - mediaSession Callback onMediaButtonEvent -- Received command = " + ke);
                if (ke != null && ke.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (ke.getKeyCode()) {
                        case KeyEvent.KEYCODE_MEDIA_PLAY:
                            myLog("AudioService - mediaSession Callback onMediaButtonEvent -- Play pressed --- KEYCODE_MEDIA_PLAY");
                            playPauseAudio();
                            break;
                        case KeyEvent.KEYCODE_MEDIA_PAUSE:
                            myLog("AudioService - mediaSession Callback onMediaButtonEvent -- Pause pressed --- KEYCODE_MEDIA_PAUSE");
                            playPauseAudio();
                            break;
                        case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                            myLog("AudioService - mediaSession Callback onMediaButtonEvent -- PlayPause pressed --- KEYCODE_MEDIA_PLAY_PAUSE");
                            playPauseAudio();
                            break;
                        case KeyEvent.KEYCODE_HEADSETHOOK:
                            myLog("AudioService - mediaSession Callback onMediaButtonEvent -- PlayPause pressed --- KEYCODE_HEADSETHOOK");
                            playPauseAudio();
                            break;
                        case KeyEvent.KEYCODE_MEDIA_NEXT:
                            myLog("AudioService - mediaSession Callback onMediaButtonEvent -- Next pressed --- KEYCODE_MEDIA_NEXT");
                            forwardAudio();
                            break;
                        case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                            myLog("AudioService - mediaSession Callback onMediaButtonEvent -- Previous pressed --- KEYCODE_MEDIA_PREVIOUS");
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

    /********************************************************************************
     ***       LOCKED SCREEN BUTTONS
     ********************************************************************************
     */

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


    /********************************************************************************
     ***       TIMER
     ********************************************************************************
     */

    private void startTimer() {
        timer = new Timer();
        tempsEcoule = 0;
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                myLog("Audio Service ----------------------------------------------------------------------------- " + tempsEcoule + "s. since timer started " );
                updateZikFileState(false);
                
                if (tempsEcoule > maxTimeBeforeSleep*60) {
                    myLog( "Audio Service : Max Playback Time Reached -- Stopping Service");

                    ToneGenerator toneGen2 = new ToneGenerator(AudioManager.STREAM_MUSIC, 50);
                    toneGen2.startTone(ToneGenerator.TONE_DTMF_0,1000);

                    Intent intent = new Intent(NOTIFICATION_PLAYBACK_MAXTIMEREACH);
                    sendBroadcast(intent);
                    killTimer();
                    mediaPlayer.stop();
                    stopSelf();

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
                    str = "AudioService.killTimer : ERROR zikFilePlayList==null";
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
                        myLog("Audio Service : ---------- zikFile updated (" + zf.getName() + ")- position : " + zf.getPosition());
                        Sql.calculateFolderProgress(getApplicationContext(), zf.getIdFolder());
                    }, throwable -> {
                        myLog("Audio Service : error sql updating zikFile :" + throwable.getMessage());
                    });

        } catch (Exception e) {
            myLog("AudioService  ==== ERROR ==== Updating File progress ");
        }


    }


    /** ----------------------------------------------------------------
    **           SHARED PREFS
    ** ----------------------------------------------------------------
    **/
    private void setMaxTimeBeforeSleep() {
        SharedPreferences prefs = this.getSharedPreferences(SHARED_PREFERENCE_TIME_BEFORE_SLEEP, MODE_PRIVATE);
        maxTimeBeforeSleep = prefs.getInt("TIME_BEFORE_SLEEP", DEFAULT_TIME_BEFORE_SLEEP);
    }

    private void saveSpeedToPref(double speed) {
        try {
            SharedPreferences.Editor editor = this.getSharedPreferences(SHARED_PREFERENCE_SPEED, MODE_PRIVATE).edit();
            editor.putString(String.valueOf(getCurrentZikFile().getIdFolder()),Double.toString(speed)).apply();
        } catch (Exception e) {
            myLogE("AudioService : error saving speed in prefs");
            myLogE(e.getMessage());
        }
    }

    private double getSpeedFromPref() {
        try {
            SharedPreferences prefs = this.getSharedPreferences(SHARED_PREFERENCE_SPEED, MODE_PRIVATE);
            return Double.parseDouble(prefs.getString(String.valueOf(getCurrentZikFile().getIdFolder()), "1.0"));
        } catch (Exception e) {
            myLogE("AudioService : error getting speed from prefs");
            myLogE(e.getMessage());
            return 1.0;
        }
    }

}