package com.driot.bookplayer.activities;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 30/10/20
 * <p>
 * onCreate
 * bindToService
 * getZikFiles
 * initialize
 */

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.sqlite.db.SimpleSQLiteQuery;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.PlayList;
import com.driot.bookplayer.db.Sql;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.utils.AudioService;

import java.sql.Date;
import java.sql.Time;
import java.util.Timer;
import java.util.TimerTask;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static com.driot.bookplayer.activities.OptionActivity.DEFAULT_TIME_BEFORE_SLEEP;
import static com.driot.bookplayer.activities.OptionActivity.SHARED_PREFERENCE_TIME_BEFORE_SLEEP;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_AUDIOFOCUS_GAIN;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_AUDIOFOCUS_LOST;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_ERROR;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_FILELOADED;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_NEWTRACK;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_PLAYBACK_MAXTIMEREACH;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_PLAYLISTFINISHED;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_TRACKFINISHED;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_ZIP_FILE_LOADED;
import static com.driot.bookplayer.utils.Tonio.FormatNameForDisplay;
import static com.driot.bookplayer.utils.Tonio.FormatPercentDouble;
import static com.driot.bookplayer.utils.Tonio.FormatPercentStringForSpeed;
import static com.driot.bookplayer.utils.Tonio.FormatTime;
import static com.driot.bookplayer.utils.Utils.animateView;
import static com.driot.tonylib.KanLogger.myLog;
import static com.driot.tonylib.KanLogger.myLogE;
import static com.driot.tonylib.KanLogger.myLogInFile;
import static com.driot.tonylib.KanLogger.myToast;
import static com.driot.tonylib.KanLogger.myToastE;

public class PlayActivity extends LifecycleLoggingActivity {

    public static final String SHARED_PREFERENCE_SPEED="SHARED_PREFERENCE_SPEED";
    private static final boolean DO_PLAY_NEXT_SONG = true;
    private static final int INTERVAL_REDRAW_SEEKBAR = 500; //  because looks like it happens erraticly when choosing value of 100 for this constant
    private static final int DELAY_ANIMATION = 200;
    private static final float INCREMENT_SPEED = 0.05f;
    private static boolean isZipFile;
    boolean boundToService;
    AudioService mService;
    boolean mBound = false;
    private Button bForward, bPlay, bRewind, bSpeedUp, bSpeedDown;
    private SeekBar seekbar;
    private TextView txSeekBar, txTempsTotal, txNomFichier, txTitle, txSubTitle, txSpeed;
    private View progressOverlay;
    private ImageView iv;
    private boolean AnimationNow;
    private boolean HasBeenInitializedService = false;
    private boolean HasBeenInitializedUI = false;
    private ZikFile zikFileFromIntent;
    private ZikFile zikFileFromService;
    private ZikFile zikFile;
    private Intent intentMusicService;
    private boolean ShitHappensFlee = false;
    private Timer autoUpdate;

    /********************************************************************************
     ***       SERVICE
     ********************************************************************************
     */

    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            myLog("PlayActivity : onServiceConnected");
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
            DrawUI(); //utile pour suppression progressBar
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            myLog("PlayActivity : OnServiceDisconnected");
            mBound = false;
        }

    };
    private BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case NOTIFICATION_NEWTRACK:
                    myLog("PlayActivity : broadcast received NEW TRACK");
                    //if (isZipFile) ShowProgressAnim();
                    break;
                case NOTIFICATION_ERROR:
                    ShitHappensFlee = true;
                    myLog("PlayActivity : broadcast received ERROR");
                    Toast.makeText(getApplicationContext(), getString(R.string.error_reading_track), Toast.LENGTH_SHORT).show();
                    finish();
                case NOTIFICATION_TRACKFINISHED:
                    myLog("PlayActivity : broadcast received TRACK FINISHED");
                    break;
                case NOTIFICATION_PLAYLISTFINISHED:
                    myLog("PlayActivity : broadcast received PLAYLIST FINISHED");
                    finish();
                    break;
                case NOTIFICATION_PLAYBACK_MAXTIMEREACH:
                    myLog("PlayActivity : broadcast received PLAYBACK_MAXTIMEREACH");
                    finish();
                    break;
                case NOTIFICATION_AUDIOFOCUS_LOST:
                    myLog("PlayActivity : broadcast received AUDIO FOCUS LOST");
                    //SetInterfacePausingMode();
                    break;
                case NOTIFICATION_AUDIOFOCUS_GAIN:
                    myLog("PlayActivity : broadcast received AUDIO FOCUS GAIN");
                    //SetInterfacePlayingMode();
                    break;
                case NOTIFICATION_FILELOADED:
                    myLog("PlayActivity : broadcast received FILE LOADED");
                    DrawUI();
                    mService.setPosition((int) zikFile.getPosition());
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
        setContentView(R.layout.activity_play);

        bRewind = findViewById(R.id.buttonRewind);
        bPlay = findViewById(R.id.buttonPlay);
        bForward = findViewById(R.id.buttonForward);
        bSpeedUp = findViewById(R.id.bSpeedUp);
        bSpeedDown = findViewById(R.id.bSpeedDown);

        iv = findViewById(R.id.imageView);
        progressOverlay = findViewById(R.id.progress_overlay);

        txSeekBar = findViewById(R.id.textViewSeekBar);
        txTempsTotal = findViewById(R.id.textViewTempsTotal);
        txNomFichier = findViewById(R.id.textViewNomFichier);
        txTitle = findViewById(R.id.textviewTitle);
        txSubTitle = findViewById(R.id.textViewSubTitle);
        txSpeed = findViewById(R.id.textViewSpeed);
        seekbar = findViewById(R.id.seekBar);

        myLog("PlayActivity.onCreate() -- Launching Music Service");
        launchService();

        // TODO, use Parcelable
        //ZikFile zikFile = getIntent().getParcelableExtra("zikFile");

        zikFileFromIntent = (ZikFile) getIntent().getSerializableExtra("ZikFile");
        zikFileFromService = PlayList.currentZikFile;
        if (!(zikFileFromService==null)) {
            zikFile = zikFileFromService;
            myLog("PlayActivity.onCreate -- ZikFile from service : " + zikFile.toString());
        } else if (!(zikFileFromIntent==null)) {
            zikFile = zikFileFromIntent;
            myLog("PlayActivity.onCreate -- ZikFile from intent : " + zikFile.toString());
        } else {
            zikFile = null;
            myLog("PlayActivity.onCreate -- ZikFile = null");
        }
        isZipFile = zikFile.isIszipfile();
        if (isZipFile) ShowProgressAnim();

        //setPlaybackState(0);

        //-*******************************************************************************
        //-***       SEEKBAR
        //-*******************************************************************************

        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    myLog("PlayActivity : SeekBar");
                    mService.setPosition(progress);
                    txSeekBar.setText(FormatTime(progress));
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
            boundToService = bindService(intentMusicService, connection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            myLogE("PlayActivity : ERROR bindService");
            myLogE(e.getMessage());
        }
        myLog("PlayActivity : call start & bind to Service in Activity.onCreate() - bound result :" + boundToService + "");
    }

    private void playMe() {
        myLog("PlayActivity.PlayMe()");
        if (mBound) {
            if (mService != null && mService.exist()) {
                if (mService.isPlaying()) {
                    myLog("PlayActivity : pause");
                    mService.pauseAudio();
                    myLog("PlayActivity : unbinding service");
                    try {
                        unbindService(connection);
                    } catch (Exception e) {
                        myLogE("PlayActivity : unbinding service ERROR");
                        myLogE(e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    myLog("PlayActivity : play");
                    launchService();
                    myLog("PlayActivity : service has been launched");
                    mService.playAudio();
                }
            } else {
                myLogE("PlayActivity playMe() mService KO");
            }
        } else {
            myLogE("PlayActivity playMe() mBound False");
        }
    }

    private void forwardMe() {
        mService.forwardAudio();
        myLog("PlayActivity : Forward");
    }

    private void backwardMe() {
        mService.backwardAudio();
        myLog("PlayActivity : Backward");
    }

    private void SpeedMeUp() {
        setSpeed(mService.getSpeed() + INCREMENT_SPEED);
        myLog("PlayActivity : SpeedUp");
    }

    private void SpeedMeDown() {
        setSpeed(mService.getSpeed() - INCREMENT_SPEED);
        myLog("PlayActivity : SpeedDown");
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
        myLog("PlayActivity.onResume");
        super.onResume();
        myLog("PlayActivity.onResume, super done, then registering receiver");

        registerReceiver(receiver, new IntentFilter(NOTIFICATION_NEWTRACK));
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_TRACKFINISHED));
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_AUDIOFOCUS_GAIN));
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_AUDIOFOCUS_LOST));
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_FILELOADED));
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_ERROR));
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_ZIP_FILE_LOADED));
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_PLAYLISTFINISHED));
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_PLAYBACK_MAXTIMEREACH));

        myLog("PlayActivity.onResume, creating new timer");
        autoUpdate = new Timer();
        autoUpdate.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> redrawSeekBar());
            }
        }, 0, INTERVAL_REDRAW_SEEKBAR);

        myLog("PlayActivity.onResume, end");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
        //stopService(intentMusicService);
        //if (connection != null) { unbindService(connection); }
    }

    @Override
    public void onBackPressed() {
        if (mService.isPlaying()) {
            //myToast(getResources().getString(R.string.no_back_while_play));
            playMe();
        }
        super.onBackPressed();
    }

    /********************************************************************************
     ***       GET FROM DB
     ********************************************************************************
     */
    private void loadPlayListIntoService() {
        myLog("PlayActivity : +++++++++ loading PlayList Into Service - GetZikFiles - Folder : " + zikFile.getIdFolder());
        Observable.fromCallable(() -> DatabaseClient
                .getInstance(getApplicationContext())
                .getAppDatabase()
                .ZikFileDao()
                .getNextZikFiles(zikFile.getIdFolder(), zikFile.getName())).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((result) -> mService.loadFiles(result), throwable -> {
                    myToastE("PlayActivity : Error Loading playlist");
                    myLogE("PlayActivity : Error Loading playlist :" + throwable.getMessage());
                    throwable.printStackTrace();
                });
    }

    private void DrawUI() {
        try {
            myLog("PlayActivity : DrawUI zf : " + zikFile.getName());
            myLog("PlayActivity : DrawUI pl : " + PlayList.currentZikFile.getName());
            zikFile = PlayList.currentZikFile;
            txSubTitle.setText(FormatNameForDisplay(zikFile.getName()));
            txTitle.setText(zikFile.getFolderName());
            txNomFichier.setText("");
            txTempsTotal.setText(FormatTime(zikFile.getDuration()));
            seekbar.setMax((int) zikFile.getDuration());
            txSeekBar.setText(FormatTime(zikFile.getPosition()));
            seekbar.setProgress((int) zikFile.getPosition());
            txSpeed.setText(FormatPercentStringForSpeed( mService.getSpeed() * 100));
            HideProgressAnim();
            myLog("PlayActivity : ----------------------------- play screen drawn " + zikFile.getPosition());
        } catch (Exception e) {
            myLog("PlayActivity :----------------------------- play screen drawn ERROR");
            myLogE(e.getMessage());
        }
    }

    /********************************************************************************
     ***       UPDATE SEEKBAR
     ********************************************************************************
     */

    private void redrawSeekBar() {
        if (mService != null && mService.exist()) {
            if (mService.isPlaying()) {
                bPlay.setText(R.string.pause);
            } else {
                bPlay.setText(R.string.play);
            }
            int iPosition = mService.getPosition();
            txSeekBar.setText(FormatTime(iPosition));
            seekbar.setProgress(iPosition);
        }
        //myLog("PlayActivity :----------------------------- redraw Seek Bar");
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


}