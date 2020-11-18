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
import android.media.session.MediaSession;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.sqlite.db.SimpleSQLiteQuery;

import com.driot.bookplayer.utils.AudioService;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.ZikFile;

import java.sql.Date;
import java.sql.Time;

import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_AUDIOFOCUS_GAIN;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_AUDIOFOCUS_LOST;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_ERROR;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_FILELOADED;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_NEWTRACK;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_TRACKFINISHED;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_ZIP_FILE_LOADED;

import static com.driot.bookplayer.utils.Tonio.*;
import static com.driot.bookplayer.utils.Utils.animateView;

public class PlayActivity extends LifecycleLoggingActivity {

    private static final boolean DO_PLAY_NEXT_SONG = true;

    static final String TAG = "PlayActivity";

    private Button bForward, bPause, bPlay, bRewind;
    private ImageView iv;
    private View progressOverlay;
    private static final int DELAY_ANIMATION = 200;
    private boolean AnimationNow;

    private boolean HasBeenInitializedService = false;
    private boolean HasBeenInitializedUI = false;
    private boolean HasBeenPlayed = false;

    private Handler myHandler = new Handler();

    private static final int INTERVAL_REDRAW_SEEKBAR = 100;

    private int forwardTime = 5000;
    private int backwardTime = 5000;
    private SeekBar seekbar;
    private TextView txSeekBar, txTempsTotal, txNomFichier, txTitle, txSubTitle;
    private ZikFile zikFileFromIntent;

    private Intent intentMusicService;
    boolean boundToService;
    AudioService mService;
    boolean mBound = false;
    private Bundle bundleOnSavedinstance;

    private static MediaSession mediaSession;

    private boolean ShitHappensFlee = false;
    private static boolean isZipFile;


    /********************************************************************************
     ***       GESTION FLIP ECRAN
     ********************************************************************************
     */

    @Override
    protected void onSaveInstanceState(Bundle outState) // entre stop et destroy
    {
        super.onSaveInstanceState(outState);
        outState.putBoolean("HasBeenPlayed", HasBeenPlayed);
        outState.putBoolean("HasBeenInitializedService", HasBeenInitializedService);
        outState.putInt("position", mService.getPosition());
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
        HasBeenPlayed = savedInstanceState.getBoolean("HasBeenPlayed", false);
        HasBeenInitializedService = savedInstanceState.getBoolean("HasBeenInitializedService", false);
        myHandler.postDelayed(UpdateSongTime, INTERVAL_REDRAW_SEEKBAR);
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
        progressOverlay = findViewById(R.id.progress_overlay);

        txSeekBar = (TextView) findViewById(R.id.textViewSeekBar);
        txTempsTotal = (TextView) findViewById(R.id.textViewTempsTotal);
        txNomFichier = (TextView) findViewById(R.id.textViewNomFichier);
        txTitle = (TextView) findViewById(R.id.textviewTitle);
        txSubTitle = (TextView) findViewById(R.id.textViewSubTitle);
        seekbar = (SeekBar) findViewById(R.id.seekBar);

        intentMusicService = new Intent(PlayActivity.this, AudioService.class);
        startService(intentMusicService);
        boundToService = bindService(intentMusicService, connection, Context.BIND_AUTO_CREATE);
        myLog("call start & bind to Service in Activity.onCreate() - bound result :" + boundToService + "");
        //myLog("bind to Service : " + Boolean.toString(boundToService));
        //myLog("mService not null: " + Boolean.toString(mService!=null));
        //myLog("mBound: " + mBound);

        // TODO, use Parcelable
        //ZikFile zikFile = getIntent().getParcelableExtra("zikFile");

        zikFileFromIntent = (ZikFile) getIntent().getSerializableExtra("ZikFile");
        isZipFile = zikFileFromIntent.isIszipfile();
        if (isZipFile) ShowProgressAnim();

        configureMediaSession();
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
        //-***       BUTTON PLAY
        //-*******************************************************************************

        bPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playMe();
            }
        });

        //-*******************************************************************************
        //-***       BUTTON PAUSE
        //-*******************************************************************************

        bPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pauseMe();
            }
        });

        //-*******************************************************************************
        //-***       BUTTONS AVANCE & RETOUR RAPIDE
        //-*******************************************************************************

        bForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                forwardMe();
            }
        });

        bRewind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                backwardMe();
            }
        });
    }

    private void playMe() {
        myLog("playMe call");
        if (mBound) {
            if (mService != null && mService.exist()) {
                mService.playAudio();
                SetInterfacePlayingMode();
                myHandler.postDelayed(UpdateSongTime, INTERVAL_REDRAW_SEEKBAR);
                HasBeenPlayed=true;
            }
        }
    }

    private void pauseMe() {
        mService.pause();
        SetInterfacePausingMode();
        updateZikFileState(mService.getCurrentZikFile(),false);
    }

    private void forwardMe() {
        int temp = mService.getPosition();
        if ((temp + forwardTime) <= mService.getDuration()) {
            mService.setPosition(temp + forwardTime);
            redrawSeekBar();
        }
    }

    private void backwardMe() {
        int temp = mService.getPosition();
        if ((temp - backwardTime) > 0) {
            mService.setPosition(temp - backwardTime);
            redrawSeekBar();
        }
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
    }
    @Override
    protected void onPause() {
        super.onPause();
        // car onPause est juste avant le onRestart le FolderContentActivity
        // mais probleme, update en Asynch et le temps de la faire, le onstart est deja passé....
        updateZikFileState(mService.getCurrentZikFile(), false);
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
        stopService(intentMusicService);
/*
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
        // car le MainActivity est bien loin et y a le temps
  */
        updateFolderState();
    }
    /********************************************************************************
     ***       GET FROM DB
     ********************************************************************************
     */
    private void loadPlayListIntoService() {
        myLog("+++++++++ loading PlayList Into Service - GetZikFiles");

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
                mService.loadFiles(zikFiles);
            }
        }
        GetZikFiles gt = new GetZikFiles();
        gt.execute();
    }

    private void DrawUI() {
        //myLog("mService not null: " + Boolean.toString(mService!=null));
        //myLog("mBound: " + mBound);
        //if (mService != null && mService.hasBeenLoaded()) {
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
        if (ShitHappensFlee) myLog("updateZikFile KO because Shit Happens so Flee far away and don't come back");DoIt = false;
        if (!HasBeenPlayed) myLog("updateZikFile KO because HasBeenPlayed=false");DoIt = false;
        if (zikFile == null) myLog("updateZikFile KO because zikFile=null");DoIt = false;
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
                        myLog("---------- ZikFile updated - position : " + zikFile.getPosition());
                        return null;
                    }

                }
                UpdateZikFileState gt = new UpdateZikFileState();
                gt.execute();

            } catch (Exception e) {
                myLog("==== ERROR ==== Updating File progress ");
            }

        }
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
            myLog("onServiceConnected");
            AudioService.BackgroundBinder binder = (AudioService.BackgroundBinder) service;
            mService = binder.getService();
            mBound = true;

            // Get PlayList
            if (!HasBeenInitializedService) { loadPlayListIntoService(); }
            HasBeenInitializedService = true;

            // retour de flip ecran
            DrawUI();
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
                    ShitHappensFlee=true;
                    myLog("broadcast received ERROR");
                    Toast.makeText(getApplicationContext(),"ERROR READING TRACK !",Toast.LENGTH_SHORT).show();
                    finish();
                case NOTIFICATION_TRACKFINISHED:
                    myLog("broadcast received TRACK FINISHED");
                    updateZikFileState(mService.getLastZikFile(), true);
                    break;
                case NOTIFICATION_AUDIOFOCUS_LOST:
                    myLog("broadcast received AUDIO FOCUS LOST");
                    SetInterfacePausingMode();
                    updateZikFileState(mService.getCurrentZikFile(),false);
                    break;
                case NOTIFICATION_AUDIOFOCUS_GAIN:
                    myLog("broadcast received AUDIO FOCUS GAIN");
                    SetInterfacePlayingMode();
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

    private void updateFolderState() {
        if (!ShitHappensFlee) {
            //String strSQL = "UPDATE Folder SET percentdone = 11.11 WHERE id = 1";
            String strSQL = "UPDATE Folder " +
                    " SET percentdone = (SELECT SUM(percentdone*duration)/SUM(duration) " +
                    "                   FROM ZikFile " +
                    "                   WHERE Folder.id = ZikFile.idFolder )" +
                    "   , LastAccess = strftime('%s','now') * 1000" +
                    "   , LastAccessTime = strftime('%s','now') * 1000 " +
                    " WHERE Folder.id = " + mService.getCurrentZikFile().getIdFolder();

            class UpdateFolderState extends AsyncTask<Void, Void, Void> {

                @Override
                protected Void doInBackground(Void... voids) {

                    //TODO try Direct SQL lite Query
                    /*
                    SQLiteDatabase db = this.getWritableDatabase();
                    String selectQuery = "select sum(odometer) as odometer from tripmileagetable where date like '2012-07%'";
                    Cursor cursor = db.rawQuery(selectQuery, null);
     */


                    SimpleSQLiteQuery query = new SimpleSQLiteQuery(strSQL);

                    DatabaseClient
                            .getInstance(getApplicationContext())
                            .getAppDatabase()
                            .FolderDao()
                            .runRawSql(query);
                    return null;
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    super.onPostExecute(aVoid);
                    //myLog( "run query " + strSQL);
                }
            }

            UpdateFolderState gt = new UpdateFolderState();
            gt.execute();

        }
    }



    private void configureMediaSession() {
        mediaSession = new MediaSession(this, "MyMediaSession");

        // Overridden methods in the MediaSession.Callback class.
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                myLog("onMediaButtonEvent called: " + mediaButtonIntent);
                KeyEvent ke = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                myLog("onMediaButtonEvent Received command: " + ke);
                if (ke != null && ke.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (ke.getKeyCode()) {
                        case KeyEvent.KEYCODE_MEDIA_PLAY:
                            myLog("onMediaButtonEvent --- Play pressed ---");
                            playMe();
                            break;
                        case KeyEvent.KEYCODE_MEDIA_PAUSE:
                            myLog("onMediaButtonEvent --- Pause pressed ---");
                            pauseMe();
                            break;
                        case KeyEvent.KEYCODE_HEADSETHOOK:
                            myLog("onMediaButtonEvent --- Pause pressed ---");
                            if (mService.isPlaying()) {
                                pauseMe();
                            } else {
                                playMe();
                            }
                            break;
                        case KeyEvent.KEYCODE_MEDIA_NEXT:
                            myLog("onMediaButtonEvent --- Next pressed ---");
                            forwardMe();
                            break;
                        case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                            myLog("onMediaButtonEvent --- Previous pressed ---");
                            backwardMe();
                            break;

                    }
                }
                return super.onMediaButtonEvent(mediaButtonIntent);
            }
        });
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setActive(true);
    }

    /********************************************************************************
     ***       DIVERS FONCTIONS
     ********************************************************************************
     */

    private void ShowProgressAnim() {
        animateView(progressOverlay, View.VISIBLE, 0.4f, DELAY_ANIMATION);
        AnimationNow=true;
    }
    private void HideProgressAnim() {
        if (AnimationNow) {
            animateView(progressOverlay, View.GONE, 0, DELAY_ANIMATION);
            AnimationNow=false;
        }

    }

    protected void myLog(String str) {
        Log.d("toto " + TAG + " ", str);
        System.out.println(str);
    }

}