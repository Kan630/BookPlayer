package com.driot.bookplayer.activities;

import static com.driot.tonylib.KanLogger.isMyPhoneDev;
import static com.driot.tonylib.TonioCommonStuff.MD5;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toolbar;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.BuildConfig;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.FolderDao;
import com.driot.tonylib.KanLogger;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
/*
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.google.android.play.core.tasks.Task;
*/
import java.util.List;

public class MainActivity extends ComponentActivity { //ComponentActivity used for this activity to be a LifecycleOwner in Observer

    private RecyclerView recyclerView;

    public static final int DAYS_FOR_FLEXIBLE_UPDATE = 10;
    public static final int UPDATE_APP_REQUEST_CODE = 6354;

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

    @SuppressLint("UseSupportActionBar")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        KanLogger.myLog("------------------------------------------------------------------");
        KanLogger.myLog("----------------     Main Activity onCreate()     ----------------");
        KanLogger.myLog("------------------------------------------------------------------");
        super.onCreate(savedInstanceState);

        init();
        
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerview_folders);
        if (recyclerView != null) recyclerView.setLayoutManager(new LinearLayoutManager(this));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setActionBar(toolbar);

        FloatingActionButton btn_Add = findViewById(R.id.FAB_Add);
        btn_Add.setOnClickListener(view -> openGetResourceActivity());

        getFolders();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        getFolders();
        myLog("recyclerview drawing through setAdapter on restart");
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        menu.findItem(R.id.menu_seelog).setVisible(isMyPhoneDev());

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_options) {
            startActivity(new Intent(getApplicationContext(), OptionActivity.class));
        } else if (itemId == R.id.menu_manual) {
            startActivity(new Intent(getApplicationContext(), HelpActivity.class));
        } else if (itemId == R.id.menu_otherapp) {
            startActivity(new Intent(getApplicationContext(), OtherAppsActivity.class));
        } else if (itemId == R.id.menu_seelog) {
            startActivity(new Intent(this, LogListActivity.class));
        } else if (itemId == R.id.menu_stats) {
            startActivity(new Intent(this, StatsActivity.class));
        } else {
            myLogE("MainActivity.onOptionsItemSelected : unknown Item selected in Menu");
        }
        return super.onOptionsItemSelected(item);
    }

    private void getFolders() {
        myLog("getFolders()");
        FolderDao folderDao = DatabaseClient.getInstance(getApplicationContext()).getAppDatabase().FolderDao();
        LiveData<List<Folder>> foldersLiveData = folderDao.getAllLiveData();
        foldersLiveData.observe(this, (Observer<List<Folder>>) folders -> { //getLifecycle()
            myLog("LiveData onChange observed - List<Folders>");
            if (folders.size() == 0) {
                if (!HasBeenProposedToOpenFile) openGetResourceActivity();
                HasBeenProposedToOpenFile = true;
            } else {
                FoldersAdapter adapter = new FoldersAdapter(MainActivity.this, folders);
                recyclerView.setAdapter(adapter);
            }
        });
    }

    public void openGetResourceActivity() {
        Intent intent = new Intent(getApplicationContext(), GetResourceActivity.class);
        startActivity(intent);
    }

    /*
        private void ClearCacheData() {
            myLog("Clearing Cache Data");
            try {
                deleteDir(getApplicationContext().getCacheDir());
                deleteDir(getApplicationContext().getCodeCacheDir());
            } catch (Exception e) {
                e.printStackTrace();
                myLogE("Error while clearing cache data");
            }
        }
        ////////////////////////////////////////////////////////////////////////////////////////
        // UPDATE STUFF
        ////////////////////////////////////////////////////////////////////////////////////////
        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == UPDATE_APP_REQUEST_CODE) {
                if (resultCode != RESULT_OK) {
                    // normalement on chope ca que pour les Flexibles, (pour les immédiates, on a poas le focus avant fin de l'update)

                    myLogE("Update flow failed! Result code: " + resultCode);
                    // If the update is cancelled or fails,
                    // you can request to start the update again.
                } else {
                    myLog("Update success");
                }
            }
        }
        private void checkForUpdate() {
            boolean DoZeUpdateIMMEDIATE = false;
            boolean DoZeUpdateFLEXIBLE = false;
            try {
                // Creates instance of the manager.
                AppUpdateManager appUpdateManager = AppUpdateManagerFactory.create(getApplicationContext());

                // Returns an intent object that you use to check for an update.
                Task<AppUpdateInfo> appUpdateInfoTask = appUpdateManager.getAppUpdateInfo();

                // Checks that the platform will allow the specified type of update.
                appUpdateInfoTask.addOnSuccessListener(appUpdateInfo -> {
                    myLog(appUpdateInfo.toString());
                    if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                            && appUpdateInfo.clientVersionStalenessDays() != null
                            && appUpdateInfo.clientVersionStalenessDays() >= DAYS_FOR_FLEXIBLE_UPDATE
                            && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {

                        //appUpdateInfo.updatePriority() >= HIGH_PRIORITY_UPDATE
                        //        && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {

                        // Request the update.
                        myLog("Update should be launched");

                        if (DoZeUpdateIMMEDIATE) {
                            try {
                                appUpdateManager.startUpdateFlowForResult(
                                        // Pass the intent that is returned by 'getAppUpdateInfo()'.
                                        appUpdateInfo,
                                        // Or 'AppUpdateType.FLEXIBLE' for flexible updates.
                                        AppUpdateType.IMMEDIATE,
                                        // The current activity making the update request.
                                        this,
                                        // Include a request code to later monitor this update request.
                                        UPDATE_APP_REQUEST_CODE);
                            } catch (IntentSender.SendIntentException e) {
                                e.printStackTrace();
                            }

                            if (DoZeUpdateFLEXIBLE) {

                                // Create a listener to track request state updates.
                                InstallStateUpdatedListener listener = state -> {
                                    // (Optional) Provide a download progress bar.
                                    if (state.installStatus() == InstallStatus.DOWNLOADING) {
                                        long bytesDownloaded = state.bytesDownloaded();
                                        long totalBytesToDownload = state.totalBytesToDownload();
                                        // Implement progress bar.
                                    }
                                    if (state.installStatus() == InstallStatus.DOWNLOADED) {
                                        // Log state or install the update.
                                        myLog("update downloaded !");
                                    }
                                };

                                // Before starting an update, register a listener for updates.
                                appUpdateManager.registerListener(listener);

                                // Start an update.

                                // When status updates are no longer needed, unregister the listener.
                                appUpdateManager.unregisterListener(listener);
                            }
                        }

                    } else {
                        myLog("Update will not be launched");
                    }
                });

            } catch (Exception e) {
                myLogE("error ocurred while checking Updates : " + e.getMessage());
                e.printStackTrace();
            }
        }
      */
    ////////////////////////////////////////////////////////////////////////////////////////
    // INIT
    ////////////////////////////////////////////////////////////////////////////////////////
    private void init() {
        KanLogger.setContext(getApplicationContext());
        //ClearCacheData();
        //KanLogger.myLog("Checking for Updates");
        //checkForUpdate();
        KanLogger.myLog("");
        KanLogger.myLog("========================== Fingerprint :");
        KanLogger.myLog("===");
        KanLogger.myLog("Build.FINGERPRINT = " + Build.FINGERPRINT);
        KanLogger.myLog("Build.FINGERPRINT MD5 = " + MD5(Build.FINGERPRINT));
        KanLogger.myLog("Phone is Dev ? => " + String.valueOf(isMyPhoneDev()));
        KanLogger.myLog("========================== Device info :");
        KanLogger.myLog("Build.Version SDK = " + Build.VERSION.SDK_INT);
        KanLogger.myLog("Build.Release = " + Build.VERSION.RELEASE);
        KanLogger.myLog("Build.Base_OS = " + Build.VERSION.BASE_OS);
        KanLogger.myLog("========================== App info :");
        KanLogger.myLog("BuildConfig.VERSION_CODE = " + BuildConfig.VERSION_CODE);
        KanLogger.myLog("BuildConfig.VERSION_NAME = " + BuildConfig.VERSION_NAME);
        KanLogger.myLog("BuildConfig.BUILD_TYPE = " + BuildConfig.BUILD_TYPE);
        KanLogger.myLog("BuildConfig.APPLICATION_ID = " + BuildConfig.APPLICATION_ID);
        KanLogger.myLog("===");
        KanLogger.myLog("==========================");
        KanLogger.myLog("");
    }
    
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }

}
