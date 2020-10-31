package com.driot.bookplayer;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 30/10/20
 */

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;

import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Time;
import java.util.concurrent.TimeUnit;


public class PlayActivity extends Activity {

    static final int REQUEST_READ_SD_CARD = 1;

    private Button bForward,bPause,bPlay,bRewind;
    private ImageView iv;
    private MediaPlayer mediaPlayer;

    private double startTime = 0;
    private double finalTime = 0;

    private Handler myHandler = new Handler();;
    private int forwardTime = 5000;
    private int backwardTime = 5000;
    private SeekBar seekbar;
    private TextView txSeekBar,txTempsTotal,txNomFichier, txTitle, txSubTitle;
    private ZikFile currentZikFile;
    private Time zikFileAccessFirstTime;

    public static int oneTimeOnly = 0;
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

        zikFileAccessFirstTime = new Time(System.currentTimeMillis());

        // Todo Get object from Intent
        //ZikFile zikFile = (ZikFile) getIntent().getSerializableExtra("zikFile");
        //ZikFile zikFile = getIntent().getParcelableExtra("zikFile");

        String zikFilePath = getIntent().getStringExtra("zikFilePath");
        String zikFileName = getIntent().getStringExtra("zikFileName");
        String zikFolderName = "FolderName";
        long zikFilePosition = getIntent().getLongExtra("zikFilePosition",0);

        txSubTitle.setText(zikFileName);
        txTitle.setText(zikFolderName);

        String filePath = zikFilePath + "/" + zikFileName;   //"/storage/0123-4567/Droit/09 00.mp3"

        // DEBUG : check file exist
        File f = new File(filePath);
        if (f.exists()) { Log.d("titi","ok file found : " + filePath);}
        else {Log.d("titi","KO file not found : " + filePath);}

        // TODO, use openFileDescriptor & remove legacy from manifest
        mediaPlayer = new MediaPlayer();
        try {mediaPlayer.setDataSource(filePath);} catch (IOException e) {e.printStackTrace();}
        try {mediaPlayer.prepare();} catch (IOException e) {e.printStackTrace();}
        if (mediaPlayer == null) {Log.d("titi","Media Player creation failed");}

        seekbar = (SeekBar)findViewById(R.id.seekBar);
        seekbar.setClickable(false);
        bPause.setEnabled(false);
        txSeekBar.setVisibility(View.INVISIBLE);

        bPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mediaPlayer.start();

                finalTime = mediaPlayer.getDuration();
                startTime = mediaPlayer.getCurrentPosition();

                if (oneTimeOnly == 0) {
                    seekbar.setMax((int) finalTime);
                    oneTimeOnly = 1;
                }

                txTempsTotal.setText(GetBarTime("final"));
                txSeekBar.setText(GetBarTime("start"));
                seekbar.setProgress((int)startTime);
                myHandler.postDelayed(UpdateSongTime,100);
                SetInterfacePlayingMode();
            }

        });


        bPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mediaPlayer.pause();
                bPause.setEnabled(false);
                bPlay.setEnabled(true);
                updateZikFileState();
            }
        });

        bForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int temp = (int)startTime;

                if((temp+forwardTime)<=finalTime){
                    startTime = startTime + forwardTime;
                    mediaPlayer.seekTo((int) startTime);
                }
            }
        });

        bRewind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int temp = (int)startTime;

                if((temp-backwardTime)>0){
                    startTime = startTime - backwardTime;
                    mediaPlayer.seekTo((int) startTime);
                }
            }
        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mediaPlayer.release();
        updateZikFileState();
    }

    private void updateZikFileState() {

        class UpdateZikFileState extends AsyncTask<Void, Void, Void> {

            @Override
            protected Void doInBackground(Void... voids) {
                currentZikFile.setPosition(startTime);
                currentZikFile.setLastaccess(new Time(System.currentTimeMillis()));
                if (currentZikFile.getFirstaccess() != null) { currentZikFile.setFirstaccess(zikFileAccessFirstTime); }
                DatabaseClient
                        .getInstance(getApplicationContext())
                        .getAppDatabase()
                        .ZikFileDao()
                        .update(currentZikFile);
                return null;
            }

        }
        UpdateZikFileState gt = new UpdateZikFileState();
        gt.execute();
    }

    private Runnable UpdateSongTime = new Runnable() {
        public void run() {
            startTime = mediaPlayer.getCurrentPosition();
            txSeekBar.setText(GetBarTime("start"));
            seekbar.setProgress((int)startTime);
            myHandler.postDelayed(this, 100);
        }
    };

    private void SetInterfacePlayingMode() {
        bPause.setEnabled(true);
        bPlay.setEnabled(false);
        txNomFichier.setVisibility(View.INVISIBLE);
        txSeekBar.setVisibility(View.VISIBLE);
    }

    private String GetBarTime(String zeType) {
        String myReturn = "";
        switch (zeType) {
            case "start":
                myReturn = String.format("%d min, %d sec",
                        TimeUnit.MILLISECONDS.toMinutes((long) startTime),
                        TimeUnit.MILLISECONDS.toSeconds((long) startTime) -
                                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.
                                        toMinutes((long) startTime)));
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
}