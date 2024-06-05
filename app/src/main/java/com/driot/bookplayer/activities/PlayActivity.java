package com.driot.bookplayer.activities;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.PlayList;
import com.driot.bookplayer.utils.AudioService;
import com.driot.bookplayer.utils.FrequencyVisualizerView;
import com.driot.bookplayer.utils.PermissionRequest;
import com.driot.tonylib.KanLogger;

import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static com.driot.bookplayer.global.Var.PATH_CHECK_APPLICATION;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_AUDIOFOCUS_GAIN;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_AUDIOFOCUS_LOST;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_FILENOTFOUND;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_ERROR;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_FILELOADED;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_NEWTRACK;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_PLAYBACK_MAXTIMEREACH;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_PLAYBACK_TIMER_VALUE;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_PLAYLISTFINISHED;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_TRACKFINISHED;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_ZIP_FILE_LOADED;
import static com.driot.bookplayer.utils.AudioService.TIMER_VALUE;
import static com.driot.bookplayer.utils.PermissionRequest.isReadAudioPermissionGranted;
import static com.driot.bookplayer.utils.PermissionRequest.isRecordAudioPermissionGranted;
import static com.driot.bookplayer.utils.Tonio.formatNameForDisplay;
import static com.driot.bookplayer.utils.Tonio.FormatPercentStringForSpeed;
import static com.driot.bookplayer.utils.Tonio.FormatTime;
import static com.driot.bookplayer.utils.Utils.animateView;
import static com.driot.tonylib.KanLogger.myToastE;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 30/10/2020
 * <p>
 * onCreate
 * bindToService
 * getZikFiles
 * initialize
 */
public class PlayActivity extends LifecycleLoggingActivity {

    public static final String SHARED_PREFERENCE_SPEED="SHARED_PREFERENCE_SPEED";
    private static final int INTERVAL_REDRAW_SEEKBAR = 500; //  because looks like it happens erraticly when choosing value of 100 for this constant
    private static final int DELAY_ANIMATION = 200;
    private static final float INCREMENT_SPEED = 0.05f;
    AudioService audioService;
    boolean audioServiceBound = false;
    private Button bPlay, bRewind, bForward, bSpeedUp, bSpeedDown;
    List<Button> buttonsToLock;
    private SeekBar seekbar;
    private TextView txSeekBar, txTempsTotal, txNomFichier, txTitle, txSubTitle, txSpeed, txListeningTime, txTimeLeft;
    private View progressOverlay, messageOverlay;
    private FrequencyVisualizerView frequencyVisualizerView;
    private boolean AnimationNow;
    private boolean HasBeenInitializedService = false;
    private Intent intentMusicService;
    private Timer timerRedrawUI;

    private PermissionRequest mPermissionRequest;


    /********************************************************************************
     ***       SERVICE
     ********************************************************************************
     */

    private final ServiceConnection audioServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            myLog("onServiceConnected");
            AudioService.BackgroundBinder binder = (AudioService.BackgroundBinder) service;
            audioService = binder.getService();
            audioServiceBound = true;

            // Get PlayList
            if (!HasBeenInitializedService) {
                if (!(audioService.isPlaying())) {
                    loadPlayListIntoService();
                }
            }
            HasBeenInitializedService = true;

            // retour de flip ecran
            myLog("onServiceConnected - DrawUI");
            DrawUI(); //utile pour suppression progressBar
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            myLog("OnServiceDisconnected");
            audioServiceBound = false;
        }

    };
    private final BroadcastReceiver broadCastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case NOTIFICATION_NEWTRACK:
                    myLog("broadcast received NEW TRACK");
                    break;
                case NOTIFICATION_ERROR:
                    myLog("broadcast received ERROR");
                    Toast.makeText(getApplicationContext(), getString(R.string.error_reading_track), Toast.LENGTH_SHORT).show();
                    finish();
                case NOTIFICATION_FILENOTFOUND:
                    myLog("broadcast received FILENOTFOUND");
                    Toast.makeText(getApplicationContext(), getString(R.string.error_reading_track) + "\n" + getString(R.string.error_file_not_found), Toast.LENGTH_SHORT).show();
                    lockButtonAndDisplayErrorMessage();
                case NOTIFICATION_TRACKFINISHED:
                    myLog("broadcast received TRACK FINISHED");
                    break;
                case NOTIFICATION_PLAYLISTFINISHED:
                    myLog("broadcast received PLAYLIST FINISHED");
                    finish();
                    break;
                case NOTIFICATION_PLAYBACK_MAXTIMEREACH:
                    myLog("broadcast received PLAYBACK_MAXTIMEREACH");
                    finish();
                    break;
                case NOTIFICATION_PLAYBACK_TIMER_VALUE:
                    myLogD("broadcast received PLAYBACK_TIMER_VALUE");
                    reDrawListeningSince(intent.getIntExtra(TIMER_VALUE,-999));
                    break;
                case NOTIFICATION_AUDIOFOCUS_LOST:
                    myLog("broadcast received AUDIO FOCUS LOST");
                    //SetInterfacePausingMode();
                    break;
                case NOTIFICATION_AUDIOFOCUS_GAIN:
                    myLog("broadcast received AUDIO FOCUS GAIN");
                    //SetInterfacePlayingMode();
                    break;
                case NOTIFICATION_FILELOADED:
                    myLog("broadcast received FILE LOADED");
                    myLog("fileloaded - DrawUI");
                    DrawUI();
                    //mService.setPosition((int) PlayList.getZikFile().getPosition());
                    HideProgressAnim();
                    break;
            }
        }
    };

    /********************************************************************************
     ***       ON CREATE
     ********************************************************************************
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Screen Orientation Locking
        if (Option.getScreenOrientationLock(this)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        }

        setContentView(R.layout.activity_play);

        bPlay = findViewById(R.id.buttonPlay);
        bRewind = findViewById(R.id.buttonRewind);
        bForward = findViewById(R.id.buttonForward);
        bSpeedUp = findViewById(R.id.bSpeedUp);
        bSpeedDown = findViewById(R.id.bSpeedDown);

        buttonsToLock = Arrays.asList(bPlay, bRewind, bForward, bSpeedUp, bSpeedDown);

        progressOverlay = findViewById(R.id.progress_overlay);
        messageOverlay = findViewById(R.id.message_overlay);

        txSeekBar = findViewById(R.id.textViewSeekBar);
        txTempsTotal = findViewById(R.id.textViewTempsTotal);
        txNomFichier = findViewById(R.id.textViewNomFichier);
        txTitle = findViewById(R.id.textviewTitle);
        txSubTitle = findViewById(R.id.textViewSubTitle);
        txSpeed = findViewById(R.id.textViewSpeed);
        seekbar = findViewById(R.id.seekBar);
        txListeningTime = findViewById(R.id.tv_ListeningTime);
        txTimeLeft = findViewById(R.id.tv_TimeLeft);
        frequencyVisualizerView = findViewById(R.id.frequencyVisualizerView);

        myLog("onCreate() -- Launching Music Service");
        launchService();

        try {
            if (PlayList.getZikFile() == null) {
                myToastE("Cannot get Playlist - PlayList.getZikFile() is null");
            } else {
                if (PlayList.getZikFile().isIszipfile()) ShowProgressAnim();
            }
        } catch (Exception e) {
            myLogE("ERR ShowProgressAnim()  " + e.getMessage());
        }

        //setPlaybackState(0);

        //-*******************************************************************************
        //-***       CHOOSE RIGHT FILE
        //-*******************************************************************************
        // which one to take, the one from the intent (click on recyclerview)
        //                 or the one from the globals var
        //
        // ancien systeme : on recupere de l'intent :
        //                zikFileFromIntent = (ZikFile) getIntent().getSerializableExtra("ZikFile");"
        //
        // nouveau systeme, on recupere des global vars,
        //          (si besoin, on recree depuis le save en fichiers de conf)
        //
        //
        //-*******************************************************************************





        //-*******************************************************************************
        //-***       SEEKBAR
        //-*******************************************************************************

        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    myLog("SeekBar");
                    audioService.setPosition(progress);
                    txSeekBar.setText(FormatTime(progress,true));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        //-*******************************************************************************
        //-***       BUTTONS
        //-*******************************************************************************

        bPlay.setOnClickListener(v -> playMe());
        bForward.setOnClickListener(v -> forwardMe());
        bRewind.setOnClickListener(v -> backwardMe());
        bSpeedUp.setOnClickListener(v -> SpeedMeUp());
        bSpeedDown.setOnClickListener(v -> SpeedMeDown());

    }

    private void launchService() {
        intentMusicService = new Intent(PlayActivity.this, AudioService.class);
        //TODO when flip screen the second time, service is destroyed....
/*
        if (isServiceRunning(AudioService.class)) {
            myLog("Starting Service");
            startService(intentMusicService);
        }
 */
        startService(intentMusicService);
        audioServiceBound = false;
        try {
            audioServiceBound = bindService(intentMusicService, audioServiceConnection, Context.BIND_AUTO_CREATE); //TODO leaked ServiceConnection if user press back
        } catch (Exception e) {
            myLogE("ERROR bindService");
            myLogE(e.getMessage());
        }
            myLog("call start & bind to AudioService in onCreate() - bound result :" + audioServiceBound + "");
    }
/*
    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
 */

    private void playMe() {
        myLog("PlayMe()");
        if (audioServiceBound) {
            if (audioService != null && audioService.exist()) {
                if (audioService.isPlaying()) {
                    /////////   PAUSE
                    myLog("pause");
                    audioService.pauseAudio();
                    reDrawListeningSince(0);
                    /////// PLAY
                } else {
                    myLog("play");
                    audioService.playAudio();
                    runVisualizer();
                }
            } else {
                myLogE("playMe() mService KO");
            }
        } else {
            myLogE("playMe() mBound False");
        }
    }

    private void forwardMe() {
        audioService.forwardAudio();
        myLog("Forward");
    }

    private void backwardMe() {
        audioService.backwardAudio();
        myLog("Backward");
    }

    private void SpeedMeUp() {
        setSpeed(audioService.getSpeed() + INCREMENT_SPEED);
        myLog("SpeedUp");
    }

    private void SpeedMeDown() {
        setSpeed(audioService.getSpeed() - INCREMENT_SPEED);
        myLog("SpeedDown");
    }

    private void setSpeed(double speed) {
        audioService.setSpeed(speed);
        String txt = FormatPercentStringForSpeed((double) speed * 100);
        txSpeed.setText(txt);
    }

    /********************************************************************************
     ***       EVENTS
     * Destroy = Fleche Retour Arriere ou Change Inclinaison
     ********************************************************************************
     */
    @Override
    protected void onResume() {
        myLog("onResume()... registering broadCastReceiver");


        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(broadCastReceiver, new IntentFilter(NOTIFICATION_NEWTRACK), RECEIVER_NOT_EXPORTED);
            registerReceiver(broadCastReceiver, new IntentFilter(NOTIFICATION_TRACKFINISHED), RECEIVER_NOT_EXPORTED);
            registerReceiver(broadCastReceiver, new IntentFilter(NOTIFICATION_AUDIOFOCUS_GAIN), RECEIVER_NOT_EXPORTED);
            registerReceiver(broadCastReceiver, new IntentFilter(NOTIFICATION_AUDIOFOCUS_LOST), RECEIVER_NOT_EXPORTED);
            registerReceiver(broadCastReceiver, new IntentFilter(NOTIFICATION_FILELOADED), RECEIVER_NOT_EXPORTED);
            registerReceiver(broadCastReceiver, new IntentFilter(NOTIFICATION_ERROR), RECEIVER_NOT_EXPORTED);
            registerReceiver(broadCastReceiver, new IntentFilter(NOTIFICATION_ZIP_FILE_LOADED), RECEIVER_NOT_EXPORTED);
            registerReceiver(broadCastReceiver, new IntentFilter(NOTIFICATION_PLAYLISTFINISHED), RECEIVER_NOT_EXPORTED);
            registerReceiver(broadCastReceiver, new IntentFilter(NOTIFICATION_PLAYBACK_MAXTIMEREACH), RECEIVER_NOT_EXPORTED);
            registerReceiver(broadCastReceiver, new IntentFilter(NOTIFICATION_PLAYBACK_TIMER_VALUE), RECEIVER_NOT_EXPORTED);
            registerReceiver(broadCastReceiver, new IntentFilter(NOTIFICATION_FILENOTFOUND), RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(broadCastReceiver, new IntentFilter(NOTIFICATION_NEWTRACK));
            registerReceiver(broadCastReceiver, new IntentFilter(NOTIFICATION_TRACKFINISHED));
            registerReceiver(broadCastReceiver, new IntentFilter(NOTIFICATION_AUDIOFOCUS_GAIN));
            registerReceiver(broadCastReceiver, new IntentFilter(NOTIFICATION_AUDIOFOCUS_LOST));
            registerReceiver(broadCastReceiver, new IntentFilter(NOTIFICATION_FILELOADED));
            registerReceiver(broadCastReceiver, new IntentFilter(NOTIFICATION_ERROR));
            registerReceiver(broadCastReceiver, new IntentFilter(NOTIFICATION_ZIP_FILE_LOADED));
            registerReceiver(broadCastReceiver, new IntentFilter(NOTIFICATION_PLAYLISTFINISHED));
            registerReceiver(broadCastReceiver, new IntentFilter(NOTIFICATION_PLAYBACK_MAXTIMEREACH));
            registerReceiver(broadCastReceiver, new IntentFilter(NOTIFICATION_PLAYBACK_TIMER_VALUE));
            registerReceiver(broadCastReceiver, new IntentFilter(NOTIFICATION_FILENOTFOUND));
        }

        myLog("onResume() - creating new timer for Display");
        runTimerForDisplay();
        myLog("onResume() - bind to service");
        audioServiceBound = bindService(intentMusicService, audioServiceConnection, Context.BIND_AUTO_CREATE);
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        myLog("onDestroy - unregister Broadcast Receiver");
//should let the system continue playing, even if activity is destroyed, by let's say, phone os battery saver routine -- also flip screen...
        if (audioServiceBound) {
            try {
                unbindService(audioServiceConnection);
                unregisterReceiver(broadCastReceiver);
            } catch (Exception e) {
                myLogE("onDestroy() - " + e.getMessage());
            }
        }

        super.onDestroy();
    }

    @Override
    protected void onPause() {
        myLog("onPause() - killing display timer");
        killTimerForDisplay();
        //unbindService(audioServiceConnection);
        ////// SURTOUT PAS !!!
        // c'est l'ecran qui s'eteint.. ca call onPause, on UnBind null, on stop, puis 1min apres ca call on Destroy et plus de son
        // du coup, si on reste bind, on passe pas par destroy...
        super.onPause();
    }

    @Override
    public void onBackPressed() { // user presses back
        myLogI("onBackPressed() -- (should be user action)");
        if (audioService.isPlaying()) {
            playMe();
        }
        if (audioServiceBound) {
            try {
                unbindService(audioServiceConnection);
                unregisterReceiver(broadCastReceiver);
            } catch (Exception e) {
                myLogE("onBackPressed() - " + e.getMessage());
            }
        }
        super.onBackPressed();
    }

    public void onBackInvoked() {
        myLog("onBackInvoked() -- (should be user action)");
    }

    @Override
    protected void onStop() {
        myLog("onStop()");
        super.onStop();
    }

    /********************************************************************************
     ***       GET FROM DB
     ********************************************************************************
     */
    private void loadPlayListIntoService() {
        if (PlayList.getZikFile() == null) {
            myToastE("Cannot get Playlist - PlayList.getZikFile() is null");
            return;
        }
        myLog("+++++++++ loading PlayList Into Service - GetZikFiles - Folder : " + PlayList.getZikFile().getIdFolder());
        Observable.fromCallable(() -> DatabaseClient
                .getInstance(getApplicationContext())
                .getAppDatabase()
                .ZikFileDao()
                .getNextZikFiles(PlayList.getZikFile().getIdFolder(), PlayList.getZikFile().getName())).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((result) -> audioService.loadFiles(result), throwable -> {
                    myToastE("Error Loading playlist");
                    myLogE("Error Loading playlist :" + throwable.getMessage());
                    throwable.printStackTrace();
                });
    }

    private void DrawUI() {
        if (PlayList.getZikFile() == null) {
            myToastE("Cannot get Playlist - PlayList.getZikFile() is null");
        }
        try {
            myLog("DrawUI : " + PlayList.getZikFile().getName() + " -- " + PlayList.getZikFile().getPosition());
            txSubTitle.setText(formatNameForDisplay(PlayList.getZikFile().getName()));
            txTitle.setText(PlayList.getZikFile().getFolderName());
            txNomFichier.setText("");
            txTempsTotal.setText(FormatTime(PlayList.getZikFile().getDuration(),true));
            seekbar.setMax((int) PlayList.getZikFile().getDuration());
            txSeekBar.setText(FormatTime(PlayList.getZikFile().getPosition(),true));
            seekbar.setProgress((int) PlayList.getZikFile().getPosition());
            txSpeed.setText(FormatPercentStringForSpeed( audioService.getSpeed() * 100));
            HideProgressAnim();
            myLogD("----------------------------- play screen drawn " + PlayList.getZikFile().getPosition());
        } catch (Exception e) {
            myLogE(":----------------------------- play screen drawn ERROR");
            myLogE(e.getMessage());
        }
    }
    private void reDrawListeningSince(int tempsEcoule) { // le call vient d'1 timer dans le service...
        String zeText_since;
        String zeText_left;
        int time_before_sleep = Option.getTimeBeforeSleep(this);
        if (tempsEcoule > 0) {
            zeText_since = getString(R.string.tv_ListeningTime) + " " + FormatTime(tempsEcoule*1000,true);
            zeText_left = getString(R.string.tv_TimeLeft) + " : " + FormatTime(time_before_sleep*1000*60-tempsEcoule*1000,true);
            txListeningTime.setText(zeText_since);
            txTimeLeft.setText(zeText_left);
        } else {
            txListeningTime.setText("");
            txTimeLeft.setText("");
        }
    }


    /********************************************************************************
     ***       UPDATE SEEKBAR
     ********************************************************************************
     */

    private void runTimerForDisplay() {
        timerRedrawUI = new Timer();
        timerRedrawUI.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> redrawSeekBar());
            }
        }, 0, INTERVAL_REDRAW_SEEKBAR);

    }
    private void killTimerForDisplay() {
        timerRedrawUI.cancel();
    }

        private void redrawSeekBar() {
        if (audioService != null && audioService.exist()) {
            if (audioService.isPlaying()) {
                bPlay.setText(R.string.pause);
            } else {
                bPlay.setText(R.string.play);
            }
            int iPosition = audioService.getPosition();
            txSeekBar.setText(FormatTime(iPosition,true));
            seekbar.setProgress(iPosition);

        } else {
            bPlay.setText(R.string.pause);
            myLog("redrawSeekBar => service KO => drawing pause button");
        }
    }

    /********************************************************************************
     ***       DIVERS FONCTIONS
     ********************************************************************************
     */

    private void ShowProgressAnim() {
        animateView(progressOverlay, View.VISIBLE, 0.4f, DELAY_ANIMATION);
        AnimationNow = true;
    }

    private void HideProgressAnim() {
        if (AnimationNow) {
            animateView(progressOverlay, View.GONE, 0, DELAY_ANIMATION);
            AnimationNow = false;
        }
    }
    private void ShowMessageOverlay() {
        animateView(messageOverlay, View.VISIBLE, 1f, DELAY_ANIMATION);
        AnimationNow = true;
    }

    private void HideMessageOverlay() {
        if (AnimationNow) {
            animateView(messageOverlay, View.GONE, 0, DELAY_ANIMATION);
            AnimationNow = false;
        }
    }

    private void runVisualizer() { // check option + permission
        if (Option.getVisualizerOn(this)) {
            if (isRecordAudioPermissionGranted(this)) {
                try {
                    //frequencyVisualizerView.setEnabled(false);
                    frequencyVisualizerView.link_toto(audioService.getAudioSessionId());
                } catch (Exception e) {
                    myLogE("runVisualizer - " + e.getMessage());
                }
            }
        }
    }

    private void lockButtonAndDisplayErrorMessage() {
        myLog("lockButtonAndDisplayErrorMessage");
        unregisterReceiver(broadCastReceiver);
        bPlay.setEnabled(false);
        for (Button b : buttonsToLock) {
                b.setEnabled(false);
        }
        seekbar.setEnabled(false);
        ShowMessageOverlay();
        TextView tv = findViewById(R.id.textViewOverlayedMessage);
        TextView tv2 = findViewById(R.id.textViewOverlayedMessageDetails);
        Button b1 = findViewById(R.id.btOverlayed01);
        tv.setText("The source file could not be found or read.\n"); // in case bug later
        try {
            //b1.setVisibility(View.INVISIBLE);
            String zePath = PlayList.getZikFile().getPath();
            String pathText = "Path of source file = \n[" + zePath + "]";
            if (zePath.contains(PATH_CHECK_APPLICATION)) {
                tv.setText("The source file could not be found or read.\n");
                myLog("Source file is inside app memory");
            } else if (isReadAudioPermissionGranted(this)) {
                tv.setText("The source file could not be found. It may have been deleted.");
                tv2.setText(pathText);
            } else {
                tv.setText("The permission is not set.");
                tv2.setText("To set, click on the below button to display App info, then go to \'App Permissions\' section and manually set \'MUSIC AND AUDIO\'.\n\nPermission is needed because the source file to read is not in Bookplayer internal memory.\n\n" + pathText);
                b1.setVisibility(View.VISIBLE);
                b1.setText("Device Settings for Bookplayer ");
                b1.setOnClickListener(v -> openAppSettingsOnPhone());
            }
        } catch (Exception e) {
            myLogE("lockButtonAndDisplayErrorMessage - " + e.getMessage());
        }
        unbindService(audioServiceConnection);
        killTimerForDisplay();
    }

    private void openAppSettingsOnPhone() {
        myLog("openAppSettingsOnPhone()");
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        } catch (Exception e) {
            myLogE("openAppSettingsOnPhone() => " + e.getMessage());
        }
    }




    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogI(String str) { KanLogger.myLogI(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }

}