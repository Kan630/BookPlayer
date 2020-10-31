package com.driot.bookplayer.activities;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 30/10/20
 */

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.ZikFile;

import java.io.IOException;
import java.sql.Time;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;


public class PlayActivity extends LifecycleLoggingActivity {

    static final int REQUEST_READ_SD_CARD = 1;
    static final String TAG = "PlayActivity.java";

    private Button bForward,bPause,bPlay,bRewind;
    private ImageView iv;
    private static MediaPlayer mediaPlayer;

    private double currentProgress = 0;
    private double finalTime = 0;

    private boolean HasBeenInitialized = false;

    private Handler myHandler = new Handler();;
    Future UpdateSongTimeFuture;
    private static final int INTERVAL_REDRAW_SEEKBAR = 100;
    private volatile boolean threadSuspended;

    private int forwardTime = 5000;
    private int backwardTime = 5000;
    private SeekBar seekbar;
    private TextView txSeekBar,txTempsTotal,txNomFichier, txTitle, txSubTitle;
    private ZikFile currentZikFile;
    private Time zikFileAccessFirstTime;
    private int idCurrentZikFile;

    /********************************************************************************
     ***       GESTION FLIP ECRAN
     ********************************************************************************
     */
    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        outState.putDouble("position", currentProgress);
        myLog("SaveInstance - Time is " + currentProgress);
        if (mediaPlayer.isPlaying()) {
            outState.putBoolean("wasPlaying", true);
            mediaPlayer.pause();
        } else {
            outState.putBoolean("wasPlaying", false);
        }
        super.onSaveInstanceState(outState);
    }
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState)
    {
        boolean wasPlaying = savedInstanceState.getBoolean("wasPlaying",false);
        currentProgress = savedInstanceState.getDouble("position");
        myLog("RestaureInstance - Time is " + currentProgress);
        if (wasPlaying) {
            mediaPlayer.seekTo((int)currentProgress);
            mediaPlayer.start();
        }
        super.onRestoreInstanceState(savedInstanceState);
    }
    @Override


    /********************************************************************************
     ***       ON CREATE
     ********************************************************************************
     */

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);

        bRewind = (Button)findViewById(R.id.buttonRewind);
        bPlay = (Button) findViewById(R.id.buttonPlay);
        bPause = (Button) findViewById(R.id.buttonPause);
        bForward = (Button) findViewById(R.id.buttonForward);
        iv = (ImageView)findViewById(R.id.imageView);

        txSeekBar = (TextView)findViewById(R.id.textViewSeekBar);
        txTempsTotal = (TextView)findViewById(R.id.textViewTempsTotal);
        txNomFichier = (TextView)findViewById(R.id.textViewNomFichier);
        txTitle = (TextView)findViewById(R.id.textviewTitle);
        txSubTitle = (TextView)findViewById(R.id.textViewSubTitle);
        seekbar = (SeekBar)findViewById(R.id.seekBar);

        // TODO, use Parcelable
        //ZikFile zikFile = getIntent().getParcelableExtra("zikFile");

        //if (!HasBeenInitialized) {
        myLog("Initial Time is " + currentProgress);
        ZikFile currentZikFile = (ZikFile) getIntent().getSerializableExtra("ZikFile");
        Log.d("titi","Passed Intent in PlayActivity : " + currentZikFile.toString());
        //HasBeenInitialized=true;

        idCurrentZikFile = currentZikFile.getId();
        currentProgress = currentZikFile.getPosition();
        myLog("Start Time is " + currentProgress);
        txSubTitle.setText(currentZikFile.getName());
        txTitle.setText(currentZikFile.getFolderName());
        String filePath = currentZikFile.getPath() + "/" + currentZikFile.getName();   //"/storage/0123-4567/Droit/09 00.mp3"

        zikFileAccessFirstTime = new Time(System.currentTimeMillis());
        updateZikFileState(currentZikFile);
        myLog("updated on start : " + currentZikFile.getId());
        /*
        // DEBUG : check file exist
        File f = new File(filePath);
        if (f.exists()) { Log.d("titi","ok file found : " + filePath);} else {Log.d("titi","KO file not found : " + filePath);}
        */

        // TODO, use openFileDescriptor & remove legacy from manifest
        mediaPlayer = new MediaPlayer();
        try {mediaPlayer.setDataSource(filePath);} catch (IOException e) {e.printStackTrace();}
        try {mediaPlayer.prepare();} catch (IOException e) {e.printStackTrace();myLog("check permissions");}
        if (mediaPlayer == null) {Log.d("titi","Media Player creation failed");}

        finalTime = mediaPlayer.getDuration();
        myLog("Final Time is " + finalTime);
        seekbar.setMax((int) finalTime);

        mediaPlayer.seekTo((int)currentProgress);

        txTempsTotal.setText(GetBarTime("final"));

        /********************************************************************************
         ***       SEEKBAR
         ********************************************************************************
         */
        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentProgress = progress;
                    mediaPlayer.seekTo(progress);
                    txSeekBar.setText(GetBarTime("start"));
                    myLog("User changes Progress Time : " + progress);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        /********************************************************************************
         ***       BUTTON PLAY
         ********************************************************************************
         */
        bPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mediaPlayer.start();
                SetInterfacePlayingMode();

                // TODO plante quand on passe en mode paysage
                myHandler.postDelayed(UpdateSongTime,INTERVAL_REDRAW_SEEKBAR);
                /*
                ExecutorService executorService = Executors.newSingleThreadExecutor();
                // submit task to threadpool:
                UpdateSongTimeFuture = executorService.submit(UpdateSongTime);
                 */
            }
        });

        /********************************************************************************
         ***       BUTTON PAUSE
         ********************************************************************************
         */

        bPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mediaPlayer.pause();
                //UpdateSongTimeFuture.cancel(true);
                SetInterfacePausingMode();
                updateZikFileState(currentZikFile);
                myLog("updated on pause : " + currentZikFile.getId());
            }
        });

        bForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int temp = (int)currentProgress;

                if((temp+forwardTime)<=finalTime){
                    currentProgress = currentProgress + forwardTime;
                    mediaPlayer.seekTo((int) currentProgress);
                }
            }
        });

        bRewind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int temp = (int)currentProgress;

                if((temp-backwardTime)>0){
                    currentProgress = currentProgress - backwardTime;
                    mediaPlayer.seekTo((int) currentProgress);
                }
            }
        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        myLog("::OnDestroy()");
        myLog("1 Time is " + currentProgress);
        mediaPlayer.release();
        myLog("2 Time is " + currentProgress);
    }

    private void updateZikFileState(ZikFile zikFile) {

        if (zikFile.getFirstaccess() == null) { zikFile.setFirstaccess(zikFileAccessFirstTime); }
        final Time sLastAccess = new Time(System.currentTimeMillis());
        zikFile.setLastaccess(sLastAccess);
        myLog("current position : " + currentProgress);
        zikFile.setPosition(currentProgress);

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

    private void redrawSeekBar() {
        if (mediaPlayer.isPlaying()) {
            txSeekBar.setText(GetBarTime("start"));
            seekbar.setProgress((int)currentProgress);
        }
    }
    
    
    private Runnable UpdateSongTime = new Runnable() {
        public void run() {
            if (mediaPlayer.isPlaying()) {
                currentProgress = mediaPlayer.getCurrentPosition();
                redrawSeekBar();
                myHandler.postDelayed(this, INTERVAL_REDRAW_SEEKBAR);
                myLog("runnable current progress : " + currentProgress);
            }
        }
    };

    private void SetInterfacePlayingMode() {
        bPause.setEnabled(true);
        bPlay.setEnabled(false);
    }
    private void SetInterfacePausingMode() {
        bPause.setEnabled(false);
        bPlay.setEnabled(true);
    }



    private String GetBarTime(String zeType) {
        String myReturn = "";
        switch (zeType) {
            case "start":
                myReturn = String.format("%d min, %d sec",
                        TimeUnit.MILLISECONDS.toMinutes((long) currentProgress),
                        TimeUnit.MILLISECONDS.toSeconds((long) currentProgress) -
                                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.
                                        toMinutes((long) currentProgress)));
                break;
            case "final":
                myReturn = String.format("%d min, %d sec",
                        TimeUnit.MILLISECONDS.toMinutes((long) finalTime),
                        TimeUnit.MILLISECONDS.toSeconds((long) finalTime) -
                                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes((long)
                                        finalTime)));
                break;
        }
        return myReturn;
    }


    /********************
     *
     * END STUFF
     */

    private void myLog(String str) {
        //String TAG = this.getClass().getName().substring(this.getClass().getName().lastIndexOf(".")+1);
        Log.d("titi " + TAG + " ",str);
        System.out.println(str);
    }
/*
    @Override
    protected void onStart() {
        super.onStart();
        myLog("::OnStart()");

    }

    @Override
    protected void onStop() {
        super.onStop();
        myLog("::OnStop()");
    }

    @Override
    protected void onResume() {
        super.onResume();
        myLog("::OnResume()");
    }

    @Override
    protected void onPause() {
        super.onPause();
        myLog("::OnPause()");
    }
    */
}