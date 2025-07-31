package com.driot.bookplayer.activities;

/**
 * Testing a new branch ....
 */


import static com.driot.bookplayer.global.Pref.shouldCheckApiForAutoDownload;
import static com.driot.bookplayer.global.Var.PODCASTINDEXORG_SINCE_DEBUG;
import static com.driot.bookplayer.utils.WorkFlow.maybeResumeWorkFlow;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;

import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.FoldersRVAdapter;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.FolderDao;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.objects.PlayList;
import com.driot.bookplayer.services.AudioService;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.InfoHelper;
import com.driot.bookplayer.utils.KanLogger;
import com.driot.bookplayer.utils.KanMail;
import com.driot.bookplayer.helpers.PodcastHelper;
import com.driot.bookplayer.utils.log.LoggingActivity;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

public class MainActivity extends LoggingActivity {

    private RecyclerView recyclerView;
    Toolbar toolbar;
    private static final int REQUEST_CODE_OPTION = 34343;

    private boolean HasBeenProposedToOpenFile;

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("HasBeenProposedToOpenFile", HasBeenProposedToOpenFile);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        HasBeenProposedToOpenFile = savedInstanceState.getBoolean("HasBeenProposedToOpenFile", false);
    }

    // Just in case we are here while we shouldn't, because isPlaying...
    AudioService audioService;
    //boolean audioServiceBound;
    private final ServiceConnection audioServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            AudioService.BackgroundBinder binder = (AudioService.BackgroundBinder) service;
            audioService = binder.getService();
            if (audioService.isPlaying()) {
                myLogW("AudioService.isPlaying => return to PlayActivity");
                if (PlayList.getInstance() == null) {
                    myLogEE(null,"AudioService.isPlaying => return to PlayActivity.... PlayList.getInstance() == null");
                } else {
                    startActivity(new Intent(MainActivity.this, PlayActivity.class));
                }
            }
            //audioServiceBound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            //audioServiceBound = false;
        }

    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        KanLogger.myLog("------------------------------------------------------------------");
        KanLogger.myLog("----------------     Main Activity onCreate()     ----------------");
        KanLogger.myLog("------------------------------------------------------------------");

        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            InfoHelper.printSomeStuffAboutDevice(this);
        }

        //WorkManager.getInstance(this).cancelAllWork();
        //WorkFlow.cancelAllOngoingTasks(this);

        myLogD("Checking AudioService");
        if (AudioService.isRunning) {
            myLog("AudioService.isRunning");
            bindService(new Intent(this, AudioService.class), audioServiceConnection, 0);
        }
/*
        myLogD("-----------------");
        FileHelper.listAllFiles(StorageHelper.getUnzipFolder(this, true));
        myLogD("-----------------");
        FileHelper.listAllFiles(StorageHelper.getUnzipFolder(this, false));
        myLogD("-----------------");

 */

        //Sql.log_all_Folders(this);
/*
        //clearLoadBookTaskState(this);
        LoadBookTaskState lbts = new LoadBookTaskState();
        lbts.downloadedFilePath = getApplication().getFilesDir().getPath() + "/download/Harry_Potter_1.zip";
        lbts.downloadedFileReady = true;
        lbts.type = "ZIP";
        lbts.title = "toto";
        lbts.uri = Uri.parse("https://bookplayer.driot.com/audiobooks/quick_family_share/Harry_Potter_1.zip");
        setLoadBookTaskState(this, lbts);

 */
/*
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(HashWorker.class).build();

        WorkManager.getInstance(this).enqueue(request);

        WorkManager.getInstance(this).getWorkInfoByIdLiveData(request.getId())
                .observe(this, workInfo -> {
                    if (workInfo != null && workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                        myLogD("All folder hashes updated!");
                    }
                });

 */


        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        try {
            setSupportActionBar(toolbar); //si ca plante, check le color theme saved ???
        } catch (Exception e) {
            myLogEE(e,"Action bar error"); // on a Samsung S20 FE, android 13
        }

        toolbar.setLogo(R.mipmap.ic_launcher);
        toolbar.setLogo(R.mipmap.ic_launcher);

        recyclerView = findViewById(R.id.recyclerview_folders);
        if (recyclerView != null) recyclerView.setLayoutManager(new LinearLayoutManager(this));

        getFolders();

        doSomeBackgroundJobs();
    }

    @Override
    protected void onResume() {
        super.onResume();
        maybeResumeWorkFlow(this);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        myLogD("onCreateOptionsMenu()");
        getMenuInflater().inflate(R.menu.action_bar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem seeLogItem = menu.findItem(R.id.menu_seelog);
        if (seeLogItem != null) {
            boolean showLog = Option.getTechLog();
            seeLogItem.setVisible(showLog);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_menu_three_dot) {
        } else if (itemId == R.id.menu_options) {
            this.getSharedPreferences(Option.SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit().putBoolean("ACTIVITY_OPTION_HAS_RESULT", false).apply(); //trick to reload MainActivity if color changed in OptionActivity, by allowing to set Result=OK only if color is changed
            startActivityForResult(new Intent(this, OptionActivity.class), REQUEST_CODE_OPTION);
        } else if (itemId == R.id.menu_manual) {
            startActivity(new Intent(getApplicationContext(), HelpActivity.class));
        } else if (itemId == R.id.menu_seelog) {
            startActivity(new Intent(this, LogListActivity.class));
        } else if (itemId == R.id.menu_stats) {
            startActivity(new Intent(this, StatsActivity.class));
        } else if (itemId == R.id.menu_sendmail) {
            KanMail.sendDaMail(this, "bookplayer@driot.com", "**Bookplayer**", "Dear developer...\n\n");
        } else if (itemId == R.id.menu_cacheFiles) {
            startActivity(new Intent(this, CleanMemoryActivity.class));
        } else if (itemId == R.id.menu_website) {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(Var.WEBSITE_URL));
            startActivity(browserIntent);
        } else if (itemId == R.id.menu_open) {
            startActivity(new Intent(getApplicationContext(), GetResourceActivity.class));
        } else if (itemId == R.id.action_menu_addBook) {
            startActivity(new Intent(getApplicationContext(), GetResourceActivity.class));
     // } else if (itemId == R.id.menu_synchro) {
       //     startActivity(new Intent(this, SynchroActivity.class));
        } else {
            myLogEE(null,"MainActivity.onOptionsItemSelected : unknown Item selected in Menu");
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OPTION) {
            myLog("coming back from OptionActivity - resultCode=[" + resultCode + "] Activity.RESULT_OK=[" + Activity.RESULT_OK + "]");
            if (resultCode == Activity.RESULT_OK) {
                recreate();
            }
        }
    }
    private void getFolders() {
        myLogD("getFolders()");
        FolderDao folderDao = DatabaseClient.getInstance(getApplicationContext()).getAppDatabase().FolderDao();
        LiveData<List<Folder>> foldersLiveData = folderDao.getAllLiveData();
        foldersLiveData.observe(this, (Observer<List<Folder>>) folders -> { //getLifecycle()
            myLogD("LiveData onChange observed - List<Folders>");
            if (folders.isEmpty()) {
                if (!HasBeenProposedToOpenFile) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        showPermissionSnackbar();
                    }
                    openGetResourceActivity();
                }
                HasBeenProposedToOpenFile = true;
            } else {
                FoldersRVAdapter adapter = new FoldersRVAdapter(MainActivity.this, folders);
                recyclerView.setAdapter(adapter);
            }
        });
    }

    private void showPermissionSnackbar() {
        View view = findViewById(android.R.id.content);
        Snackbar snackbar = Snackbar.make(view, getString(R.string.permission_record_audio_rationale_01), Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction("OK", v -> {
            snackbar.dismiss();
            askPermission();
        });
        snackbar.show();
    }
    private void askPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 2399843);
    }


    public void openGetResourceActivity() {
        Intent intent = new Intent(getApplicationContext(), GetResourceActivity.class);
        startActivity(intent);
    }


    private void doSomeBackgroundJobs() {
        if (shouldCheckApiForAutoDownload()) {
            PodcastHelper.checkForNewEpisodesToAutoDownload(this, PODCASTINDEXORG_SINCE_DEBUG);
        }
        ImageHelper.processPendingImages(this);
    }


}
