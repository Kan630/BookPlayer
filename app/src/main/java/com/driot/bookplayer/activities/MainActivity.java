package com.driot.bookplayer.activities;

import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toolbar;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.R;
import com.driot.tonylib.KanLogger;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.google.android.play.core.tasks.Task;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static com.driot.bookplayer.utils.Utils.deleteDir;
import static com.driot.tonylib.KanLogger.isMyPhoneDev;
import static com.driot.tonylib.KanLogger.myLog;
import static com.driot.tonylib.KanLogger.myLogE;

public class MainActivity extends LifecycleLoggingActivity {

    private RecyclerView recyclerView;

    private View progressOverlay;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //ClearCacheData();
        checkForUpdate();
        KanLogger.setContext(getApplicationContext());
        KanLogger.myLog("");
        KanLogger.myLog("============================================> Let's go");
        KanLogger.myLog("");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setActionBar(toolbar);

        recyclerView = findViewById(R.id.recyclerview_folders);
        FloatingActionButton btn_Add = findViewById(R.id.FAB_Add);
        progressOverlay = findViewById(R.id.progress_overlay);

        if (recyclerView != null) recyclerView.setLayoutManager(new LinearLayoutManager(this));

        btn_Add.setOnClickListener(view -> performFileSearch());

        getFolders();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        getFolders();
        myLog("recyclerview drawing through setAdapter on restart");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        if (isMyPhoneDev()) {
            menu.findItem(R.id.menu_seelog).setVisible(true);
        } else {
            menu.findItem(R.id.menu_seelog).setVisible(false);
        }

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
        } else {
            myLogE("menu click : action inconnue");
        }
        return super.onOptionsItemSelected(item);
    }

    private void getFolders() {
        myLog("getFolders()");
        Observable.fromCallable(() -> {
            List<Folder> folders = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .FolderDao()
                    .getAll();
            return folders;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((result) -> {
                    if (result.size() == 0) {
                        if (!HasBeenProposedToOpenFile) performFileSearch();
                        HasBeenProposedToOpenFile = true;
                    } else {
                        FoldersAdapter adapter = new FoldersAdapter(MainActivity.this, result);
                        recyclerView.setAdapter(adapter);
                    }
                });
    }

    public void performFileSearch() {
        Intent intent = new Intent(getApplicationContext(), GetResourceActivity.class);
        startActivity(intent);
    }

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


    private void checkForUpdate() {
        boolean DoZeUpdateIMMEDIATE = false;
        boolean DoZeUpdateFLEXIBLE = false;

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
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
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

}
