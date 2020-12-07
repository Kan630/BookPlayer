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
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.utils.AudioService;

import java.sql.Date;
import java.sql.Time;
import java.util.Timer;
import java.util.TimerTask;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_AUDIOFOCUS_GAIN;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_AUDIOFOCUS_LOST;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_ERROR;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_FILELOADED;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_NEWTRACK;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_TRACKFINISHED;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_ZIP_FILE_LOADED;
import static com.driot.bookplayer.utils.Tonio.FormatNameForDisplay;
import static com.driot.bookplayer.utils.Tonio.FormatPercentDouble;
import static com.driot.bookplayer.utils.Tonio.FormatPercentStringForSpeed;
import static com.driot.bookplayer.utils.Tonio.FormatTime;
import static com.driot.bookplayer.utils.Utils.animateView;

public class PlayActivity extends LifecycleLoggingActivity {

    private static final boolean DO_PLAY_NEXT_SONG = true;
    private static final int INTERVAL_REDRAW_SEEKBAR = 100;
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
    private boolean HasBeenPlayed = false;
    private ZikFile zikFileFromIntent;
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
            myLog("onServiceConnected");
            AudioService.BackgroundBinder binder = (AudioService.BackgroundBinder) service;
            mService = binder.getService();
            mBound = true;

            // Get PlayList
            if (!HasBeenInitializedService) {
                loadPlayListIntoService();
            }
            HasBeenInitializedService = true;

            // retour de flip ecran
            DrawUI(); //utile pour suppression progressBar
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            myLog("OnServiceDisconnected");
            mBound = false;
        }

    };
    private BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case NOTIFICATION_NEWTRACK:
                    myLog("broadcast received NEW TRACK");
                    //if (isZipFile) ShowProgressAnim();
                    break;
                case NOTIFICATION_ERROR:
                    ShitHappensFlee = true;
                    myLog("broadcast received ERROR");
                    Toast.makeText(getApplicationContext(), "ERROR READING TRACK !", Toast.LENGTH_SHORT).show();
                    finish();
                case NOTIFICATION_TRACKFINISHED:
                    myLog("broadcast received TRACK FINISHED");
                    updateZikFileState(mService.getLastZikFile(), true);
                    break;
                case NOTIFICATION_AUDIOFOCUS_LOST:
                    myLog("broadcast received AUDIO FOCUS LOST");
                    //SetInterfacePausingMode();
                    updateZikFileState(mService.getCurrentZikFile(), false);
                    break;
                case NOTIFICATION_AUDIOFOCUS_GAIN:
                    myLog("broadcast received AUDIO FOCUS GAIN");
                    //SetInterfacePlayingMode();
                    break;
                case NOTIFICATION_FILELOADED:
                    myLog("broadcast received FILE LOADED");
                    DrawUI();
                    mService.setPosition((int) mService.getCurrentZikFile().getPosition());
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

        intentMusicService = new Intent(PlayActivity.this, AudioService.class);
        startService(intentMusicService);
        boundToService = bindService(intentMusicService, connection, Context.BIND_AUTO_CREATE);
        myLog("call start & bind to Service in Activity.onCreate() - bound result :" + boundToService + "");

        // TODO, use Parcelable
        //ZikFile zikFile = getIntent().getParcelableExtra("zikFile");

        zikFileFromIntent = (ZikFile) getIntent().getSerializableExtra("ZikFile");
        isZipFile = zikFileFromIntent.isIszipfile();
        if (isZipFile) ShowProgressAnim();

        //setPlaybackState(0);

        //-*******************************************************************************
        //-***       SEEKBAR
        //-*******************************************************************************

        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mService.setPosition(progress);
                    txSeekBar.setText(FormatTime(progress));
                    HasBeenPlayed = true;
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

    private void playMe() {
        if (mBound) {
            if (mService != null && mService.exist()) {
                if (mService.isPlaying()) {
                    myLog("pause call from UI");
                    mService.pauseAudio();
                    updateZikFileState(mService.getCurrentZikFile(), false);
                } else {
                    myLog("play call from UI");
                    mService.playAudio();
                    HasBeenPlayed = true;
                }
            }
        }
    }

    private void forwardMe() {
        mService.forwardAudio();
    }

    private void backwardMe() {
        mService.backwardAudio();
    }

    private void SpeedMeUp() {
        double newSpeed = mService.getSpeed() + INCREMENT_SPEED;
        mService.setSpeed(newSpeed);
        String txt = FormatPercentStringForSpeed((double) newSpeed * 100);
        txSpeed.setText(txt);
    }

    private void SpeedMeDown() {
        double newSpeed = mService.getSpeed() - INCREMENT_SPEED;
        mService.setSpeed(newSpeed);
        String txt = FormatPercentStringForSpeed((double) newSpeed * 100);
        txSpeed.setText(txt);
    }

    /********************************************************************************
     ***       EVENTS
     * Destroy = Fleche Retour Arriere ou Change Inclinaison
     ********************************************************************************
     */
    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(receiver, new IntentFilter(NOTIFICATION_NEWTRACK));
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_TRACKFINISHED));
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_AUDIOFOCUS_GAIN));
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_AUDIOFOCUS_LOST));
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_FILELOADED));
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_ERROR));
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_ZIP_FILE_LOADED));

        autoUpdate = new Timer();
        autoUpdate.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> redrawSeekBar());
            }
        }, 0, INTERVAL_REDRAW_SEEKBAR);

    }

    @Override
    protected void onPause() {
        super.onPause();
        // car onPause est juste avant le onRestart le FolderContentActivity
        // mais probleme, update en Asynch et le temps de la faire, le onstart est deja passé....
        updateZikFileState(mService.getCurrentZikFile(), false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
        stopService(intentMusicService);
        updateFolderState();
    }

    /********************************************************************************
     ***       GET FROM DB
     ********************************************************************************
     */
    private void loadPlayListIntoService() {
        myLog("+++++++++ loading PlayList Into Service - GetZikFiles");
        Observable.fromCallable(() -> DatabaseClient
                .getInstance(getApplicationContext())
                .getAppDatabase()
                .ZikFileDao()
                .getNextZikFiles(zikFileFromIntent.getIdFolder(), zikFileFromIntent.getName())).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((result) -> mService.loadFiles(result), throwable -> {
                    myToastE(this, "Error Loading playlist");
                    myLogE("Error Loading playlist :" + throwable.getMessage());
                    throwable.printStackTrace();
                });
    }

    private void DrawUI() {
        try {
            ZikFile zf = mService.getCurrentZikFile();
            txSubTitle.setText(FormatNameForDisplay(zf.getName()));
            txTitle.setText(zf.getFolderName());
            txNomFichier.setText("");
            txTempsTotal.setText(FormatTime(zf.getDuration()));
            seekbar.setMax((int) zf.getDuration());
            txSeekBar.setText(FormatTime(zf.getPosition()));
            seekbar.setProgress((int) zf.getPosition());
            HideProgressAnim();
            myLog("----------------------------- play screen drawn " + zf.getPosition());
        } catch (Exception e) {
            myLog("----------------------------- play screen drawn ERROR");
        }
    }

    /********************************************************************************
     ***       UPDATE DB
     ********************************************************************************
     */
    private void updateZikFileState(ZikFile zikFile, boolean bFinished) {
        boolean DoIt = true;
        myLog("---------- ZikFile called for update");
        if (ShitHappensFlee) {
            myLog("won't update ZikFile because Shit Happens so Flee far away and don't come back");
            DoIt = false;
        }
        if (!HasBeenPlayed) {
            myLog("won't update ZikFile because HasBeenPlayed=false");
            DoIt = false;
        }
        if (zikFile == null) {
            myLog("won't update ZikFile because zikFile=null");
            DoIt = false;
        }
        if (DoIt) {
            try {
                if (zikFile.getFirstaccess() == null) {
                    zikFile.setFirstaccess(new Date(System.currentTimeMillis()));
                }
                final Time sLastAccessTime = new Time(System.currentTimeMillis());
                final Date sLastAccess = new Date(System.currentTimeMillis());
                zikFile.setLastaccess(sLastAccess);
                zikFile.setLastaccessTime(sLastAccessTime);
                if (bFinished) {
                    zikFile.setPosition(zikFile.getDuration());
                    zikFile.setPercentdone(100);
                    zikFile.setFinished(true);
                } else {
                    zikFile.setPosition(mService.getPosition());
                    zikFile.setPercentdone(FormatPercentDouble((double) mService.getPosition() / mService.getDuration()));
                    if (zikFile.getDuration() == 0) {
                        zikFile.setDuration(mService.getDuration());
                    }
                }

                Observable.fromCallable(() -> {
                    DatabaseClient
                            .getInstance(getApplicationContext())
                            .getAppDatabase()
                            .ZikFileDao()
                            .update(zikFile);
                    return false;
                })
                        .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                        .subscribe(result -> {
                            myLog("---------- ZikFile updated - position : " + zikFile.getPosition());
                        }, throwable -> {
                            myLogE("error sql updating ZikFile :" + throwable.getMessage());
                        });

            } catch (Exception e) {
                myLog("==== ERROR ==== Updating File progress ");
            }

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
                int iPosition = mService.getPosition();
                txSeekBar.setText(FormatTime(iPosition));
                seekbar.setProgress(iPosition);
            } else {
                bPlay.setText(R.string.play);
            }
        }
    }

    /********************************************************************************
     ***       DIVERS
     ********************************************************************************
     */
    //TODO try Direct SQL lite Query
    //SQLiteDatabase db = this.getWritableDatabase();
    //String selectQuery = "select sum(odometer) as odometer from tripmileagetable where date like '2012-07%'";
    //Cursor cursor = db.rawQuery(selectQuery, null);
    private void updateFolderState() {
        if (!ShitHappensFlee) {
            String strSQL = "UPDATE Folder " +
                    " SET percentdone = (SELECT SUM(percentdone*duration)/SUM(duration) " +
                    "                   FROM ZikFile " +
                    "                   WHERE Folder.id = ZikFile.idFolder )" +
                    "   , LastAccess = strftime('%s','now') * 1000" +
                    "   , LastAccessTime = strftime('%s','now') * 1000 " +
                    " WHERE Folder.id = " + mService.getCurrentZikFile().getIdFolder();
            SimpleSQLiteQuery query = new SimpleSQLiteQuery(strSQL);

            Observable.fromCallable(() -> DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .FolderDao()
                    .runRawSql(query)).subscribeOn(Schedulers.io());
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

}