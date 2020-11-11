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
import android.database.sqlite.SQLiteDatabase;
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

import androidx.documentfile.provider.DocumentFile;
import androidx.sqlite.db.SimpleSQLiteQuery;

import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.utils.AudioService;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.ZikFile;

import org.w3c.dom.Text;

import java.sql.Array;
import java.sql.Date;
import java.sql.Time;
import java.util.ArrayList;
import java.util.HashSet;

import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_AUDIOFOCUS_GAIN;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_AUDIOFOCUS_LOST;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_FILELOADED;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_NEWTRACK;
import static com.driot.bookplayer.utils.AudioService.NOTIFICATION_TRACKFINISHED;
import static com.driot.bookplayer.utils.Tonio.*;

public class PlayActivity extends LifecycleLoggingActivity {

    private static final boolean DO_PLAY_NEXT_SONG = true;

    static final String TAG = "PlayActivity";

    private Button bForward, bPause, bPlay, bRewind;
    private ImageView iv;

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

        txSeekBar = (TextView) findViewById(R.id.textViewSeekBar);
        txTempsTotal = (TextView) findViewById(R.id.textViewTempsTotal);
        txNomFichier = (TextView) findViewById(R.id.textViewNomFichier);
        txTitle = (TextView) findViewById(R.id.textviewTitle);
        txSubTitle = (TextView) findViewById(R.id.textViewSubTitle);
        seekbar = (SeekBar) findViewById(R.id.seekBar);

        Intent intentMusicService = new Intent(PlayActivity.this, AudioService.class);
        bindService(intentMusicService, connection, Context.BIND_AUTO_CREATE);
        Log.d("toto","PlayActivity : bind to Service ");

        // TODO, use Parcelable
        //ZikFile zikFile = getIntent().getParcelableExtra("zikFile");

        zikFileFromIntent = (ZikFile) getIntent().getSerializableExtra("ZikFile");

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

                if (mBound) {
                    mService.start();
                    SetInterfacePlayingMode();
                    myHandler.postDelayed(UpdateSongTime, INTERVAL_REDRAW_SEEKBAR);
                    HasBeenPlayed=true;
                }
            }
        });

        //-*******************************************************************************
        //-***       BUTTON PAUSE
        //-*******************************************************************************

        bPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mService.pause();
                SetInterfacePausingMode();
                updateZikFileState(mService.getCurrentZikFile(),false);
            }
        });

        //-*******************************************************************************
        //-***       BUTTONS AVANCE & RETOUR RAPIDE
        //-*******************************************************************************

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
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_AUDIOFOCUS_GAIN));
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_AUDIOFOCUS_LOST));
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_FILELOADED));
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
        updateFolderState();
    }
    /********************************************************************************
     ***       GET FROM DB
     ********************************************************************************
     */
    private void loadPlayListIntoService() {
        Log.d("toto","+++++++++ loading PlayList Into Service - GetZikFiles");

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
        if (mService != null && mService.hasBeenLoaded()) {
            ZikFile zf = mService.getCurrentZikFile();
            txSubTitle.setText(StripExtention(zf.getName()));
            txTitle.setText(zf.getFolderName());
            txNomFichier.setText("");
            txTempsTotal.setText(FormatTime(zf.getDuration()));
            seekbar.setMax((int) zf.getDuration());
            txSeekBar.setText(FormatTime(zf.getPosition()));
            seekbar.setProgress((int) zf.getPosition());
            Log.d("toto","----------------------------- play screen drawn " + zf.getPosition());
        } else {
            Log.d("toto","----------------------------- play screen drawn ERROR no mService or not ready");
        }
    }


    /********************************************************************************
     ***       UPDATE DB
     ********************************************************************************
     */
    private void updateZikFileState(ZikFile zikFile, boolean bFinished) {
        Log.d("toto","---------- ZikFile called for update - position : " + mService.getPosition());
        if (HasBeenPlayed) {
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
                    Log.d("toto","---------- ZikFile updated - position : " + zikFile.getPosition());
                    return null;
                }

            }
            UpdateZikFileState gt = new UpdateZikFileState();
            gt.execute();
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
            Log.d("toto","onServiceConnected");
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
                    Log.d("toto","broadcast received NEW TRACK");
                    break;
                case NOTIFICATION_TRACKFINISHED:
                    Log.d("toto","broadcast received TRACK FINISHED");
                    updateZikFileState(mService.getLastZikFile(), true);
                    break;
                case NOTIFICATION_AUDIOFOCUS_LOST:
                    Log.d("toto","broadcast received AUDIO FOCUS LOST");
                    SetInterfacePausingMode();
                    updateZikFileState(mService.getCurrentZikFile(),false);
                    break;
                case NOTIFICATION_AUDIOFOCUS_GAIN:
                    Log.d("toto","broadcast received AUDIO FOCUS GAIN");
                    SetInterfacePlayingMode();
                    break;
                case NOTIFICATION_FILELOADED:
                    Log.d("toto","broadcast received FILE LOADED");
                    DrawUI();
                    mService.setPosition((int) mService.getCurrentZikFile().getPosition());
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
                Log.d("toto","run query " + strSQL);
                }
            }

    UpdateFolderState gt = new UpdateFolderState();
        gt.execute();
    }



    /********************************************************************************
     ***       DIVERS FONCTIONS
     ********************************************************************************
     */


    protected void myLog(String str) {
        Log.d("toto " + TAG + " ", str);
        System.out.println(str);
    }

}