package com.driot.bookplayer.activities;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 30/10/20
 */

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaPlayer;
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

import java.io.IOException;
import java.sql.Date;
import java.sql.Time;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.driot.bookplayer.utils.Tonio.FormatTime;


public class PlayActivity extends LifecycleLoggingActivity {

    static final int REQUEST_READ_SD_CARD = 1;
    static final String TAG = "PlayActivity";

    private Button bForward, bPause, bPlay, bRewind;
    private ImageView iv;
//    private static MediaPlayer mediaPlayer;

//    private double currentProgress = 0;
    private double finalTime = 0;

    private boolean HasBeenInitialized = false;

    private Handler myHandler = new Handler();
    ;
    Future UpdateSongTimeFuture;
    private static final int INTERVAL_REDRAW_SEEKBAR = 100;
    private volatile boolean threadSuspended;

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
//        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
        if (mService != null && mService.isPlaying()) {
            outState.putBoolean("wasPlaying", true);
            //mediaPlayer.pause();
            //mService.pause();
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
//            mediaPlayer.start();
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

        // Bind to BackgroundService
        Intent intentBGS = new Intent(PlayActivity.this, BackgroundService.class);
        bindService(intentBGS, connection, Context.BIND_AUTO_CREATE);
        Log.d("toto","Activity : bind to Service ");

        // TODO, use Parcelable
        //ZikFile zikFile = getIntent().getParcelableExtra("zikFile");

        ZikFile zikFileFromIntent = (ZikFile) getIntent().getSerializableExtra("ZikFile");
        idCurrentZikFile = zikFileFromIntent.getId();

        txSubTitle.setText(zikFileFromIntent.getName());
        txTitle.setText(zikFileFromIntent.getFolderName());
        filePath = zikFileFromIntent.getPath() + "/" + zikFileFromIntent.getName();   //"/storage/0123-4567/Droit/09 00.mp3"

        /*
        // DEBUG : check file exist
        File f = new File(filePath);
        if (f.exists()) { Log.d("titi","ok file found : " + filePath);} else {Log.d("titi","KO file not found : " + filePath);}
        */

        // TODO, use openFileDescriptor & remove legacy from manifest
/*
//      mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            mediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
            myLog("check permissions");
        }
        if (mediaPlayer == null) {
            Log.d("titi", "Media Player creation failed");
        }
 */


        /********************************************************************************
         ***       SEEKBAR
         ********************************************************************************
         */
        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mService.setPosition(progress);
                    //currentProgress = progress;
                    //mediaPlayer.seekTo(progress);
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


                //mediaPlayer.start();
                mService.start();
                SetInterfacePlayingMode();

                if (mBound) {
                    // Call a method from the LocalService.
                    // However, if this call were something that might hang, then this request should
                    // occur in a separate thread to avoid slowing down the activity performance.
                    int num = mService.getRandomNumber();
                    Toast.makeText(PlayActivity.this, "number: " + num, Toast.LENGTH_SHORT).show();
                }

                //Intent intentBGS = new Intent(PlayActivity.this, BackgroundService.class);
                //startService(intentBGS);

                /*
                Intent intent = new Intent(PlayActivity.this, PlayService.class);
                startService(intent);

                // Ajout de l'activity à la liste des listeners du service
                final IPlayServiceListener listener = new IPlayServiceListener() {
                    public void dataChanged(final Object data) {
                        PlayActivity.this.runOnUiThread(new Runnable() {
                            public void run() {
                                // Mise à jour de l'UI
                                Log.d("toto", "coucou ca listen");
                                txTitle.setText("salut mec");
                            }
                        });
                        // mise à jour de l'interface graphique
                        Log.d("toto", "coucou ca listen");
                    }
                };
                ServiceConnection connection = new ServiceConnection() {
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        Log.d("toto PlayService", "Connected!");
                        IPlayService service2 = ((PlayServiceBinder)service).getService();
                        service2.addListener(listener);
                    }

                    public void onServiceDisconnected(ComponentName name) {
                        Log.d("toto PlayService", "Disconnected!");
                    }
                };

                bindService(intent,connection, Context.BIND_AUTO_CREATE);
*/

                // TODO plante quand on passe en mode paysage
                myHandler.postDelayed(UpdateSongTime, INTERVAL_REDRAW_SEEKBAR);
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
                mService.pause();
                //mediaPlayer.pause();
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
                //int temp = (int) currentProgress;
                int temp = mService.getPosition();

                if ((temp + forwardTime) <= finalTime) {
                    //currentProgress = currentProgress + forwardTime;
                    //mediaPlayer.seekTo((int) currentProgress);
                    mService.setPosition(temp + forwardTime);
                    redrawSeekBar();
                }
            }
        });

        bRewind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //int temp = (int) currentProgress;
                int temp = mService.getPosition();

                if ((temp - backwardTime) > 0) {
                    //currentProgress = currentProgress - backwardTime;
                    //mediaPlayer.seekTo((int) currentProgress);
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
        //currentProgress = mediaPlayer.getCurrentPosition();
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
        //stopAudio();
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
        //Intent intentBackGroundService = new Intent(this, BackgroundService.class);
        //stopService(intentBackGroundService);
    }
/*
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
*/
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
        //currentProgress = currentZikFile.getPosition();
        //zikFileAccessFirstTime = new Time(System.currentTimeMillis());
        zikFileAccessFirstTime = new Date(System.currentTimeMillis());

        updateZikFileState(currentZikFile);

        //finalTime = mediaPlayer.getDuration();
        finalTime = mService.getDuration();
        seekbar.setMax((int) finalTime);
        //mediaPlayer.seekTo((int) currentProgress);
        //txTempsTotal.setText(GetBarTime("final"));
        txSeekBar.setText(FormatTime(finalTime));
        redrawSeekBar();
    }

    /********************************************************************************
     ***       UPDATE DB
     ********************************************************************************
     */
    private void updateZikFileState(ZikFile zikFile) {

        if (zikFile.getFirstaccess() == null) {
            zikFile.setFirstaccess(zikFileAccessFirstTime);
        }
        final Time sLastAccessTime = new Time(System.currentTimeMillis());
        final Date sLastAccess = new Date(System.currentTimeMillis());
        zikFile.setLastaccess(sLastAccess);
        zikFile.setLastaccessTime(sLastAccessTime);
        //zikFile.setPosition(currentProgress);
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
            //if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            if (mService != null && mService.isPlaying()) {
                //currentProgress = mediaPlayer.getCurrentPosition();
                redrawSeekBar();
                myHandler.postDelayed(this, INTERVAL_REDRAW_SEEKBAR);
            }
        }
    };

    private void redrawSeekBar() {
        //if (mediaPlayer.isPlaying()) {
        //txSeekBar.setText(GetBarTime("start"));
        //txSeekBar.setText(FormatTime(currentProgress));
        //seekbar.setProgress((int) currentProgress);
        int iPosition = mService.getPosition();
        txSeekBar.setText(FormatTime(iPosition));
        seekbar.setProgress(iPosition);
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


/*
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
  */


    private double caclulatePercent() {
        //double ret = currentProgress / finalTime;
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

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            BackgroundService.BackgroundBinder binder = (BackgroundService.BackgroundBinder) service;
            mService = binder.getService();
            mBound = true;
             {mService.loadFile(filePath);}
            getZikFile(idCurrentZikFile);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    private void myLog(String str) {
        //String TAG = this.getClass().getName().substring(this.getClass().getName().lastIndexOf(".")+1);
        Log.d("titi " + TAG + " ", str);
        System.out.println(str);
    }

}