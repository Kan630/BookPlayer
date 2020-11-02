package com.driot.bookplayer.activities;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 30/10/20
 */

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.driot.bookplayer.BackgroundService;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.ZikFile;

import java.sql.Date;
import java.sql.Time;

import static com.driot.bookplayer.utils.Tonio.FormatTime;


public class PlayActivity extends LifecycleLoggingActivity {

    static final String TAG = "PlayActivity";

    private Button bForward, bPause, bPlay, bRewind;
    private ImageView iv;
    private double finalTime = 0;

    private boolean HasBeenInitialized = false;

    private Handler myHandler = new Handler();

    private static final int INTERVAL_REDRAW_SEEKBAR = 100;

    private int forwardTime = 5000;
    private int backwardTime = 5000;
    private SeekBar seekbar;
    private TextView txSeekBar, txTempsTotal, txNomFichier, txTitle, txSubTitle;
    private ZikFile currentZikFile;
    private String filePath;
    //private Time zikFileAccessFirstTime;
    private Date zikFileAccessFirstTime;
    private int idCurrentZikFile;

    BackgroundService mService;
    boolean mBound = false;
    private Bundle bundleOnSavedinstance;

    /********************************************************************************
     ***       GESTION FLIP ECRAN
     ********************************************************************************
     */

    @Override
    protected void onSaveInstanceState(Bundle outState) // entre stop et destroy
    {
        super.onSaveInstanceState(outState);
        if (mService != null && mService.isPlaying()) {
            outState.putBoolean("wasPlaying", true);
        } else {
            outState.putBoolean("wasPlaying", false);
        }
        bundleOnSavedinstance = outState;
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) // apres onStart
    {
        super.onRestoreInstanceState(savedInstanceState);
        boolean wasPlaying = savedInstanceState.getBoolean("wasPlaying", false);
        if (wasPlaying) {
            if (mService != null) {mService.start();}
            myHandler.postDelayed(UpdateSongTime, INTERVAL_REDRAW_SEEKBAR);
        }
    }

    /********************************************************************************
     ***       ON CREATE
     ********************************************************************************
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);

        bRewind = (Button) findViewById(R.id.buttonRewind);
        bPlay = (Button) findViewById(R.id.buttonPlay);
        bPause = (Button) findViewById(R.id.buttonPause);
        bForward = (Button) findViewById(R.id.buttonForward);
        iv = (ImageView) findViewById(R.id.imageView);

        txSeekBar = (TextView) findViewById(R.id.textViewSeekBar);
        txTempsTotal = (TextView) findViewById(R.id.textViewTempsTotal);
        txNomFichier = (TextView) findViewById(R.id.textViewNomFichier);
        txTitle = (TextView) findViewById(R.id.textviewTitle);
        txSubTitle = (TextView) findViewById(R.id.textViewSubTitle);
        seekbar = (SeekBar) findViewById(R.id.seekBar);

        Intent intentMusicService = new Intent(PlayActivity.this, BackgroundService.class);
        bindService(intentMusicService, connection, Context.BIND_AUTO_CREATE);
        Log.d("toto","Activity : bind to Service ");

        // TODO, use Parcelable
        //ZikFile zikFile = getIntent().getParcelableExtra("zikFile");
        ZikFile zikFileFromIntent = (ZikFile) getIntent().getSerializableExtra("ZikFile");
        idCurrentZikFile = zikFileFromIntent.getId();

        txSubTitle.setText(zikFileFromIntent.getName());
        txTitle.setText(zikFileFromIntent.getFolderName());
        filePath = zikFileFromIntent.getPath() + "/" + zikFileFromIntent.getName();   //"/storage/0123-4567/Droit/09 00.mp3"

        /********************************************************************************
         ***       SEEKBAR
         ********************************************************************************
         */
        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
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

        /********************************************************************************
         ***       BUTTON PLAY
         ********************************************************************************
         */
        bPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (mBound) {
                    mService.start();
                    SetInterfacePlayingMode();
                    myHandler.postDelayed(UpdateSongTime, INTERVAL_REDRAW_SEEKBAR);
                }
            }
        });

        /********************************************************************************
         ***       BUTTON PAUSE
         ********************************************************************************
         */

        bPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mService.pause();
                SetInterfacePausingMode();
                updateZikFileState(currentZikFile);
                myLog("updated on pause : " + currentZikFile.getId());
            }
        });

        /********************************************************************************
         ***       BUTTONS AVANCE & RETOUR RAPIDE
         ********************************************************************************
         */
        bForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int temp = mService.getPosition();
                if ((temp + forwardTime) <= finalTime) {
                    mService.setPosition(temp + forwardTime);
                    redrawSeekBar();
                }
            }
        });

        bRewind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int temp = mService.getPosition();
                if ((temp - backwardTime) > 0) {
                    mService.setPosition(temp - backwardTime);
                    redrawSeekBar();
                }
            }
        });
    }

    /********************************************************************************
     ***       DESTROY
     * Fleche Retour Arriere ou Change Inclinaison
     ********************************************************************************
     */
    @Override
    protected void onPause() {
        super.onPause();
        updateZikFileState(currentZikFile);
    }
    @Override
    protected void onStop() {
        super.onStop();
        mBound = false;
        bundleOnSavedinstance = null;
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        boolean stopzeAudio = true;
        if (bundleOnSavedinstance != null) {
            boolean wasPlaying = bundleOnSavedinstance.getBoolean("wasPlaying", false);
            if (wasPlaying) {
                stopzeAudio = false ;
            }
        }
        if (stopzeAudio) {
            unbindService(connection);
        }
    }
    /********************************************************************************
     ***       GET FROM DB
     ********************************************************************************
     */
    private void getZikFile(long id) {

        class GetZikFile extends AsyncTask<Void, Void, ZikFile> {

            @Override
            protected ZikFile doInBackground(Void... voids) {
                ZikFile zikFile = DatabaseClient
                        .getInstance(getApplicationContext())
                        .getAppDatabase()
                        .ZikFileDao()
                        .getZikFile(id);
                return zikFile;
            }

            @Override
            protected void onPostExecute(ZikFile zikFile) {
                super.onPostExecute(zikFile);
                currentZikFile = zikFile;
                Initialize();
            }
        }
        GetZikFile gt = new GetZikFile();
        gt.execute();
    }

    private void Initialize() {
        mService.setPosition((int) currentZikFile.getPosition());
        finalTime = mService.getDuration();

        updateZikFileState(currentZikFile);

        seekbar.setMax((int) finalTime);
        txSeekBar.setText(FormatTime(finalTime));
        redrawSeekBar();
    }

    /********************************************************************************
     ***       UPDATE DB
     ********************************************************************************
     */
    private void updateZikFileState(ZikFile zikFile) {

        if (zikFile.getFirstaccess() == null) {
            zikFile.setFirstaccess(new Date(System.currentTimeMillis()));
        }
        final Time sLastAccessTime = new Time(System.currentTimeMillis());
        final Date sLastAccess = new Date(System.currentTimeMillis());
        zikFile.setLastaccess(sLastAccess);
        zikFile.setLastaccessTime(sLastAccessTime);
        zikFile.setPosition(mService.getPosition());
        zikFile.setPercentdone(caclulatePercent());
        if (zikFile.getLength() == 0) {
            zikFile.setLength(finalTime);
        }

        class UpdateZikFileState extends AsyncTask<Void, Void, Void> {

            @Override
            protected Void doInBackground(Void... voids) {
                DatabaseClient.getInstance(getApplicationContext()).getAppDatabase()
                        .ZikFileDao().update(zikFile);
                return null;
            }

        }
        UpdateZikFileState gt = new UpdateZikFileState();
        gt.execute();
    }

    /********************************************************************************
     ***       UPDATE SEEKBAR
     ********************************************************************************
     */
    private Runnable UpdateSongTime = new Runnable() {
        public void run() {
            if (mService != null && mService.exist() && mService.isPlaying()) {
                redrawSeekBar();
                myHandler.postDelayed(this, INTERVAL_REDRAW_SEEKBAR);
            }
        }
    };

    private void redrawSeekBar() {
        int iPosition = mService.getPosition();
        txSeekBar.setText(FormatTime(iPosition));
        seekbar.setProgress(iPosition);
    }

    /********************************************************************************
     ***       SERVICE
     ********************************************************************************
     */

    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            BackgroundService.BackgroundBinder binder = (BackgroundService.BackgroundBinder) service;
            mService = binder.getService();
            mBound = true;
            mService.loadFile(filePath);
            getZikFile(idCurrentZikFile);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    /********************************************************************************
     ***       DIVERS
     ********************************************************************************
     */
    private void SetInterfacePlayingMode() {
        bPause.setEnabled(true);
        bPlay.setEnabled(false);
    }

    private void SetInterfacePausingMode() {
        bPause.setEnabled(false);
        bPlay.setEnabled(true);
    }

    private double caclulatePercent() {
        double ret = mService.getPosition() / finalTime;
        if (ret < 0) {
            ret = 0;
        }
        if (ret > 100) {
            ret = 100;
        }
        return ret;
    }

    /********************************************************************************
     ***       DIVERS FONCTIONS
     ********************************************************************************
     */


    private void myLog(String str) {
        //String TAG = this.getClass().getName().substring(this.getClass().getName().lastIndexOf(".")+1);
        Log.d("titi " + TAG + " ", str);
        System.out.println(str);
    }

}