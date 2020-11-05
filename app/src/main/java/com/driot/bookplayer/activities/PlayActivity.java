package com.driot.bookplayer.activities;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 30/10/20
 *
 * onCreate
 * bindToService
 * getZikFiles
 * initialize
 *
 *
 */

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

import com.driot.bookplayer.utils.AudioService;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.ZikFile;

import java.sql.Array;
import java.sql.Date;
import java.sql.Time;
import java.util.ArrayList;

import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_NEWTRACK;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_TRACKFINISHED;
import static com.driot.bookplayer.utils.Tonio.*;

public class PlayActivity extends LifecycleLoggingActivity {

    static final String TAG = "PlayActivity";

    private Button bForward, bPause, bPlay, bRewind;
    private ImageView iv;

    private boolean HasBeenInitialized = false;

    private Handler myHandler = new Handler();

    private static final int INTERVAL_REDRAW_SEEKBAR = 100;

    private int forwardTime = 5000;
    private int backwardTime = 5000;
    private SeekBar seekbar;
    private TextView txSeekBar, txTempsTotal, txNomFichier, txTitle, txSubTitle;
    private ZikFile zikFileFromIntent;
    private ZikFile currentZikFile;
    private String filePath;
    private ArrayList<ZikFile> arrayListZikFiles;
    private ArrayList<String> arrayListPaths;

    private boolean PlayNextSong = true;

    AudioService mService;
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

        Intent intentMusicService = new Intent(PlayActivity.this, AudioService.class);
        bindService(intentMusicService, connection, Context.BIND_AUTO_CREATE);
        Log.d("toto","Activity : bind to Service ");

        // TODO, use Parcelable
        //ZikFile zikFile = getIntent().getParcelableExtra("zikFile");

        zikFileFromIntent = (ZikFile) getIntent().getSerializableExtra("ZikFile");
        txSubTitle.setText(StripExtention(zikFileFromIntent.getName()));
        txTitle.setText(zikFileFromIntent.getFolderName());
        txNomFichier.setText("");
        txTempsTotal.setText(FormatTime(zikFileFromIntent.getDuration()));
        seekbar.setMax((int) zikFileFromIntent.getDuration());
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
                updateZikFileState(currentZikFile,false);
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
                if ((temp + forwardTime) <= mService.getDuration()) {
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
     ***       EVENTS
     * Destroy = Fleche Retour Arriere ou Change Inclinaison
     ********************************************************************************
     */
    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_NEWTRACK));
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_TRACKFINISHED));
    }
    @Override
    protected void onPause() {
        super.onPause();
        updateZikFileState(currentZikFile, false);
    }
    @Override
    protected void onStop() {
        super.onStop();
        bundleOnSavedinstance = null;
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
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
    private void getZikFiles() {

        class GetZikFiles extends AsyncTask<Void, Void, ZikFile[]> {

            @Override
            protected ZikFile[] doInBackground(Void... voids) {
                ZikFile[] zikFiles = DatabaseClient
                        .getInstance(getApplicationContext())
                        .getAppDatabase()
                        .ZikFileDao()
                        .getNextZikFiles(zikFileFromIntent.getIdFolder(),zikFileFromIntent.getName());
                return zikFiles;
            }

            @Override
            protected void onPostExecute(ZikFile[] zikFiles) {
                super.onPostExecute(zikFiles);
                currentZikFile = zikFiles[0];
                Log.d("toto","do it");
                arrayListZikFiles = new ArrayList<ZikFile>();
                arrayListPaths = new ArrayList<String>();
                for (ZikFile zikFile : zikFiles) {
                    Log.d("toto", zikFile.getName());
                    arrayListZikFiles.add(zikFile);
                    arrayListPaths.add(zikFileFromIntent.getPath() + "/" + zikFile.getName());
                }
                Initialize();
            }
        }
        GetZikFiles gt = new GetZikFiles();
        gt.execute();
    }

    private void Initialize() {
        mService.loadFiles(arrayListPaths);
        mService.setPosition((int) currentZikFile.getPosition());
        redrawSeekBar();

        updateZikFileState(currentZikFile, false);
    }

    /********************************************************************************
     ***       UPDATE DB
     ********************************************************************************
     */
    private void updateZikFileState(ZikFile zikFile, boolean bFinished) {

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
            zikFile.setPercentdone(FormatPercentDouble((double) mService.getPosition()/mService.getDuration()));
            if (zikFile.getDuration() == 0) {
                zikFile.setDuration(mService.getDuration());
            }
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
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            Log.d("toto","onServiceConnected");
            AudioService.BackgroundBinder binder = (AudioService.BackgroundBinder) service;
            mService = binder.getService();
            mBound = true;
            // si trop lent, on intervertit en chargeant juste la filepath avant la liste de filepath
            getZikFiles();
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
            Log.d("toto","Broadcast received");
            if (intent.getAction().equals(NOTIFICATION_NEWTRACK)) {
                Bundle bundle = intent.getExtras();
                if (bundle != null) {
                    int numSong = bundle.getInt(AudioService.TRACKNUMBER);
                    currentZikFile = arrayListZikFiles.get(numSong);
                    txSubTitle.setText(StripExtention(currentZikFile.getName()));
                    txTempsTotal.setText(FormatTime(currentZikFile.getDuration()));
                    seekbar.setMax((int) currentZikFile.getDuration());
                }
            }
            if (intent.getAction().equals(NOTIFICATION_TRACKFINISHED)) {
                updateZikFileState(currentZikFile, true);
            }
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
/*
    private double caclulatePercent(double division) {
        double ret = division*100;
        if (ret < 0) {
            ret = 0;
        }
        if (ret > 100) {
            ret = 100;
        }
        return ret;
    }
*/
    /********************************************************************************
     ***       DIVERS FONCTIONS
     ********************************************************************************
     */


    protected void myLog(String str) {
        //String TAG = this.getClass().getName().substring(this.getClass().getName().lastIndexOf(".")+1);
        Log.d("toto " + TAG + " ", str);
        System.out.println(str);
    }

}