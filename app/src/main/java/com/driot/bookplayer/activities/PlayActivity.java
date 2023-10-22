package com.driot.bookplayer.activities;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.driot.bookplayer.BuildConfig;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.global.PlayList;
import com.driot.bookplayer.utils.AudioService;
import com.driot.tonylib.KanLogger;

import java.util.Timer;
import java.util.TimerTask;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static com.driot.bookplayer.activities.OptionActivity.DEFAULT_SCREEN_ORIENTATION_LOCK;
import static com.driot.bookplayer.activities.OptionActivity.SHARED_PREFERENCES_OPTIONS;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_AUDIOFOCUS_GAIN;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_AUDIOFOCUS_LOST;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_ERROR;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_FILELOADED;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_NEWTRACK;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_PLAYBACK_MAXTIMEREACH;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_PLAYBACK_TIMER_VALUE;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_PLAYLISTFINISHED;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_TRACKFINISHED;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_ZIP_FILE_LOADED;
import static com.driot.bookplayer.utils.AudioService.TIMER_VALUE;
import static com.driot.bookplayer.utils.Tonio.FormatNameForDisplay;
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
    boolean boundToService;
    AudioService mService;
    boolean mBound = false;
    private Button bPlay;
    private SeekBar seekbar;
    private TextView txSeekBar, txTempsTotal, txNomFichier, txTitle, txSubTitle, txSpeed, txListeningTime;
    private View progressOverlay;
    private boolean AnimationNow;
    private boolean HasBeenInitializedService = false;
    private Intent intentMusicService;
    private Timer timerRedrawUI;

    /********************************************************************************
     ***       SERVICE
     ********************************************************************************
     */

    private final ServiceConnection audioServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            myLog("onServiceConnected");
            AudioService.BackgroundBinder binder = (AudioService.BackgroundBinder) service;
            mService = binder.getService();
            mBound = true;

            // Get PlayList
            if (!HasBeenInitializedService) {
                if (!(mService.isPlaying())) {
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
            mBound = false;
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
        SharedPreferences prefs = this.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE);
        if (prefs.getBoolean("LOCK_SCREEN_ORIENTATION", DEFAULT_SCREEN_ORIENTATION_LOCK)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        }

        setContentView(R.layout.activity_play);

        Button bRewind = findViewById(R.id.buttonRewind);
        bPlay = findViewById(R.id.buttonPlay);
        Button bForward = findViewById(R.id.buttonForward);
        Button bSpeedUp = findViewById(R.id.bSpeedUp);
        Button bSpeedDown = findViewById(R.id.bSpeedDown);

        progressOverlay = findViewById(R.id.progress_overlay);

        txSeekBar = findViewById(R.id.textViewSeekBar);
        txTempsTotal = findViewById(R.id.textViewTempsTotal);
        txNomFichier = findViewById(R.id.textViewNomFichier);
        txTitle = findViewById(R.id.textviewTitle);
        txSubTitle = findViewById(R.id.textViewSubTitle);
        txSpeed = findViewById(R.id.textViewSpeed);
        seekbar = findViewById(R.id.seekBar);
        txListeningTime = findViewById(R.id.tv_ListeningTime);

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
                    mService.setPosition(progress);
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
        startService(intentMusicService);
        boundToService=false;
        try {
            boundToService = bindService(intentMusicService, audioServiceConnection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            myLogE("ERROR bindService");
            myLogE(e.getMessage());
        }
        myLog("call start & bind to AudioService in onCreate() - bound result :" + boundToService + "");
    }

    private void playMe() {
        myLog("PlayMe()");
        if (mBound) {
            if (mService != null && mService.exist()) {
                if (mService.isPlaying()) {
                    /////////   PAUSE
                    myLog("pause");
                    mService.pauseAudio();
                    reDrawListeningSince(0);
                    /////// PLAY
                } else {
                    myLog("play");
                    mService.playAudio();
                }
            } else {
                myLogE("playMe() mService KO");
            }
        } else {
            myLogE("playMe() mBound False");
        }
    }

    private void forwardMe() {
        mService.forwardAudio();
        myLog("Forward");
    }

    private void backwardMe() {
        mService.backwardAudio();
        myLog("Backward");
    }

    private void SpeedMeUp() {
        setSpeed(mService.getSpeed() + INCREMENT_SPEED);
        myLog("SpeedUp");
    }

    private void SpeedMeDown() {
        setSpeed(mService.getSpeed() - INCREMENT_SPEED);
        myLog("SpeedDown");
    }

    private void setSpeed(double speed) {
        mService.setSpeed(speed);
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
        }

        myLog("onResume() - creating new timer for Display");
        runTimerForDisplay();
        myLog("onResume() - bind to service");
        boundToService = bindService(intentMusicService, audioServiceConnection, Context.BIND_AUTO_CREATE);
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        myLog("onDestroy - unregister Broadcast Receiver");
        unregisterReceiver(broadCastReceiver);
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
    public void onBackPressed() {
        myLog("onBackPressed() - stop playing");
        if (mService.isPlaying()) {
            playMe();
        }
        super.onBackPressed();
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
                .subscribe((result) -> mService.loadFiles(result), throwable -> {
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
            txSubTitle.setText(FormatNameForDisplay(PlayList.getZikFile().getName()));
            txTitle.setText(PlayList.getZikFile().getFolderName());
            txNomFichier.setText("");
            txTempsTotal.setText(FormatTime(PlayList.getZikFile().getDuration(),true));
            seekbar.setMax((int) PlayList.getZikFile().getDuration());
            txSeekBar.setText(FormatTime(PlayList.getZikFile().getPosition(),true));
            seekbar.setProgress((int) PlayList.getZikFile().getPosition());
            txSpeed.setText(FormatPercentStringForSpeed( mService.getSpeed() * 100));
            HideProgressAnim();
            myLogD("----------------------------- play screen drawn " + PlayList.getZikFile().getPosition());
        } catch (Exception e) {
            myLogE(":----------------------------- play screen drawn ERROR");
            myLogE(e.getMessage());
        }
    }
    private void reDrawListeningSince(int tempsEcoule) {
        String zeText;
        if (tempsEcoule > 0) {
            zeText = getString(R.string.tv_ListeningTime) + " " + FormatTime(tempsEcoule*1000,true);
            txListeningTime.setText(zeText);
        } else {
            txListeningTime.setText("");
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
        if (mService != null && mService.exist()) {
            if (mService.isPlaying()) {
                bPlay.setText(R.string.pause);
            } else {
                bPlay.setText(R.string.play);
            }
            int iPosition = mService.getPosition();
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

    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }

}