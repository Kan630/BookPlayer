package com.driot.bookplayer.activities;

import static com.driot.tonylib.KanLogger.isMyPhoneDev;
import static com.driot.tonylib.TonioCommonStuff.MD5;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import androidx.annotation.Nullable;
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
import com.driot.tonylib.KanLogger;
import com.driot.tonylib.KanMail;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends LifecycleLoggingActivity {

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

        getFolders();
    }

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
       // } else if (itemId == R.id.menu_synchro) {
       //     startActivity(new Intent(this, SynchroActivity.class));
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


    ////////////////////////////////////////////////////////////////////////////////////////
    // INIT
    ////////////////////////////////////////////////////////////////////////////////////////
    private void printSomeStuffAboutDevice() {
        try {
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

            DisplayMetrics displayMetrics = new DisplayMetrics();
            WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            if (windowManager != null) {
                windowManager.getDefaultDisplay().getMetrics(displayMetrics);
            }
            KanLogger.myLog("========================== Screen :");
            KanLogger.myLog("Width = " + displayMetrics.widthPixels);
            KanLogger.myLog("Height = " + displayMetrics.heightPixels);
            KanLogger.myLog("========================== Miscellaneous :");
            KanLogger.myLog("Theme = " + getKindOfTheme());
            KanLogger.myLog("===");
            KanLogger.myLog("==========================");
            KanLogger.myLog("");
        } catch (Exception e) {
            Log.e("toto INIT", " " + e.getMessage());
        }
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
