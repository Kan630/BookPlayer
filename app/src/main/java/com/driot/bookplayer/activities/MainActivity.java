package com.driot.bookplayer.activities;

import static com.driot.tonylib.KanLogger.isMyPhoneDev;
import static com.driot.tonylib.KanLogger.myToast;
import static com.driot.tonylib.TonioCommonStuff.MD5;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;

import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
//import androidx.credentials.CredentialManager;

import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.BuildConfig;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.FolderDao;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.utils.PermissionRequest;
import com.driot.tonylib.KanLogger;
import com.driot.tonylib.KanMail;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
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
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends LifecycleLoggingActivity {//AppCompatActivity //ComponentActivity used for this activity to be a LifecycleOwner in Observer

    private RecyclerView recyclerView;
    Toolbar toolbar;
    private static final int REQUEST_CODE_OPTION = 34343;
    public static final int DAYS_FOR_FLEXIBLE_UPDATE = 10;
    public static final int UPDATE_APP_REQUEST_CODE = 6354;

    //private CredentialManager credentialManager;  => KO after implementation commented in gradle

    private PermissionRequest mPermissionRequest;
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

    //@SuppressLint("UseSupportActionBar")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        KanLogger.setKanContext(getApplicationContext());
        KanLogger.myLog("------------------------------------------------------------------");
        KanLogger.myLog("----------------     Main Activity onCreate()     ----------------");
        KanLogger.myLog("------------------------------------------------------------------");

        super.onCreate(savedInstanceState);

        printSomeStuffAboutDevice();

        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        //toolbar.setTitle("Books");
        toolbar.setLogo(R.mipmap.ic_launcher);
        toolbar.setLogo(R.mipmap.ic_launcher);

        recyclerView = findViewById(R.id.recyclerview_folders);
        if (recyclerView != null) recyclerView.setLayoutManager(new LinearLayoutManager(this));

        FloatingActionButton btn_Add = findViewById(R.id.FAB_Add);
        btn_Add.setOnClickListener(view -> openGetResourceActivity());

        /*
        //doCredentialStuff;
        DataProvider dp = new DataProvider(this);
        if (dp.isSignedIn()) {
            myToast("Logged");
        } else {
            myToast("NOT Logged");
        }
        credentialManager = CredentialManager.create(this);
        res = credentialManager.createCredential(this,)

import androidx.fragment.app.FragmentActivity;
import com.google.android.gms.fido.fido2.api.common.CreatePublicKeyCredentialRequest;
import com.google.android.gms.fido.fido2.api.common.CreatePublicKeyCredentialResponse;
import com.google.android.gms.fido.fido2.api.common.CreateCredentialException;
import com.google.android.gms.fido.fido2.Fido2ApiClient;
import com.google.android.gms.fido.Fido;
import android.view.View;

// Assuming 'credentialManager' is an instance of Fido2ApiClient and 'fetchRegistrationJsonFromServer' is a method that returns a JSON string.
// Also assuming 'configureProgress' and 'handlePasskeyFailure' are methods defined elsewhere in your Java class.

        private CreatePublicKeyCredentialResponse createPasskey(FragmentActivity activity) {
            CreatePublicKeyCredentialRequest request = new CreatePublicKeyCredentialRequest(fetchRegistrationJsonFromServer());
            CreatePublicKeyCredentialResponse response = null;
            try {
                Fido2ApiClient credentialManager = Fido.getFido2ApiClient(activity);
                response = credentialManager.createCredential(request).getResult();
            } catch (CreateCredentialException e) {
                configureProgress(View.INVISIBLE);
                handlePasskeyFailure(e);
            }
            return response;
        }

// Note: The above Java method assumes synchronous execution. If you need to handle this asynchronously,
// you would need to use callbacks or futures to handle the result of 'createCredential'.


         */



        getFolders();
    }

    /*
    private void doCredentialStuff() {
        if (DataProvider.isSignedInThroughPasskeys(this)) {
            binding.signedInText.text = LOGGED_IN_THROUGH_PASSKEYS
        } else {
            binding.signedInText.text = LOGGED_IN_THROUGH_PASSWORD
        }
    }

     */

    @Override
    protected void onRestart() {
        super.onRestart();
        KanLogger.setKanContext(getApplicationContext()); //TODO this is shit
        getFolders();
        myLog("recyclerview drawing through setAdapter on restart");
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        myLog("onCreateOptionsMenu()");
        getMenuInflater().inflate(R.menu.action_bar, menu);
        return super.onCreateOptionsMenu(menu);
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
        } else if (itemId == R.id.menu_otherapp) {
            startActivity(new Intent(getApplicationContext(), OtherAppsActivity.class));
        } else if (itemId == R.id.menu_seelog) {
            startActivity(new Intent(this, LogListActivity.class));
        } else if (itemId == R.id.menu_stats) {
            startActivity(new Intent(this, StatsActivity.class));
        } else if (itemId == R.id.menu_sendmail) {
            KanMail.sendDaMail(this, "bookplayer@driot.com", "**Bookplayer**", "Dear developer...\n\n");
        } else if (itemId == R.id.menu_cacheFiles) {
            startActivity(new Intent(this, CacheFilesActivity.class));
        } else if (itemId == R.id.menu_synchro) {
            startActivity(new Intent(this, SynchroActivity.class));
        } else {
            myLogE("MainActivity.onOptionsItemSelected : unknown Item selected in Menu");
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
        myLog("getFolders()");
        FolderDao folderDao = DatabaseClient.getInstance(getApplicationContext()).getAppDatabase().FolderDao();
        LiveData<List<Folder>> foldersLiveData = folderDao.getAllLiveData();
        foldersLiveData.observe(this, (Observer<List<Folder>>) folders -> { //getLifecycle()
            myLog("LiveData onChange observed - List<Folders>");
            if (folders.size() == 0) {
                if (!HasBeenProposedToOpenFile) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        showPermissionSnackbar();
                    }
                    openGetResourceActivity();
                }
                HasBeenProposedToOpenFile = true;
            } else {
                FoldersAdapter adapter = new FoldersAdapter(MainActivity.this, folders);
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
    private void printSomeStuffAboutDevice() {
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
        KanLogger.myLog("========================== Region :");
        KanLogger.myLog("Locale.getDefault = " + Locale.getDefault().getCountry());
        KanLogger.myLog("TimeZone.getDefault = " + TimeZone.getDefault().getID());
        KanLogger.myLog("TelephonyManager country = " + getCountryFromTelephonyManager(this));
        KanLogger.myLog("========================== Miscellaneous :");
        KanLogger.myLog("Theme = " + getKindOfTheme());
        KanLogger.myLog("===");
        KanLogger.myLog("==========================");
        KanLogger.myLog("");
    }

    private static String getCountryFromTelephonyManager(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String countryIso = telephonyManager.getNetworkCountryIso(); // returns the country code, e.g., "us"
        return countryIso != null ? countryIso.toUpperCase() : null;
    }

    private String getKindOfTheme() {
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            return "Dark";
        } else {
            return "Light";
        }
    }

    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }

}
