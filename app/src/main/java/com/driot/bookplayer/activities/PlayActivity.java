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
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;


public class PlayActivity extends LifecycleLoggingActivity {

    static final int REQUEST_READ_SD_CARD = 1;
    static final String TAG = "PlayActivity";

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
    protected void onSaveInstanceState(Bundle outState) // entre stop et destroy
    {
        super.onSaveInstanceState(outState);
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            outState.putBoolean("wasPlaying", true);
            mediaPlayer.pause();
        } else {
            outState.putBoolean("wasPlaying", false);
        }
    }
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) // apres onStart
    {
        super.onRestoreInstanceState(savedInstanceState);
        boolean wasPlaying = savedInstanceState.getBoolean("wasPlaying",false);
        if (wasPlaying) {
            mediaPlayer.start();
            myHandler.postDelayed(UpdateSongTime,INTERVAL_REDRAW_SEEKBAR);
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

        ZikFile zikFileFromIntent = (ZikFile) getIntent().getSerializableExtra("ZikFile");
        idCurrentZikFile = zikFileFromIntent.getId();

        txSubTitle.setText(zikFileFromIntent.getName());
        txTitle.setText(zikFileFromIntent.getFolderName());
        String filePath = zikFileFromIntent.getPath() + "/" + zikFileFromIntent.getName();   //"/storage/0123-4567/Droit/09 00.mp3"

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

        getZikFile(idCurrentZikFile);

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

        /********************************************************************************
         ***       BUTTONS AVANCE & RETOUR RAPIDE
         ********************************************************************************
         */
        bForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int temp = (int)currentProgress;

                if((temp+forwardTime)<=finalTime){
                    currentProgress = currentProgress + forwardTime;
                    mediaPlayer.seekTo((int) currentProgress);
                    redrawSeekBar();
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
        currentProgress = mediaPlayer.getCurrentPosition();
        updateZikFileState(currentZikFile);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAudio();
    }

    private void stopAudio() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
                updateZikFileState(currentZikFile);
            }
            mediaPlayer.release();
            mediaPlayer = null;
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
        currentProgress = currentZikFile.getPosition();
        zikFileAccessFirstTime = new Time(System.currentTimeMillis());

        updateZikFileState(currentZikFile);

        finalTime = mediaPlayer.getDuration();
        seekbar.setMax((int) finalTime);
        mediaPlayer.seekTo((int)currentProgress);
        txTempsTotal.setText(GetBarTime("final"));
        redrawSeekBar();
    }

    /********************************************************************************
     ***       UPDATE DB
     ********************************************************************************
     */
    private void updateZikFileState(ZikFile zikFile) {

        if (zikFile.getFirstaccess() == null) { zikFile.setFirstaccess(zikFileAccessFirstTime); }
        final Time sLastAccess = new Time(System.currentTimeMillis());
        zikFile.setLastaccess(sLastAccess);
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

    /********************************************************************************
     ***       UPDATE SEEKBAR
     ********************************************************************************
     */
    private Runnable UpdateSongTime = new Runnable() {
        public void run() {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                currentProgress = mediaPlayer.getCurrentPosition();
                redrawSeekBar();
                myHandler.postDelayed(this, INTERVAL_REDRAW_SEEKBAR);
            }
        }
    };
    private void redrawSeekBar() {
        //if (mediaPlayer.isPlaying()) {
            txSeekBar.setText(GetBarTime("start"));
            seekbar.setProgress((int)currentProgress);
        //}
    }


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


    /********************************************************************************
     ***       DIVERS FONCTIONS
     ********************************************************************************
     */

    private void myLog(String str) {
        //String TAG = this.getClass().getName().substring(this.getClass().getName().lastIndexOf(".")+1);
        Log.d("titi " + TAG + " ",str);
        System.out.println(str);
    }
}