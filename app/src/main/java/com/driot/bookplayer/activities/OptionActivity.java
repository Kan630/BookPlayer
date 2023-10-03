package com.driot.bookplayer.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import com.driot.bookplayer.R;

import static com.driot.tonylib.KanLogger.myLongToast;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 20/12/20
 */
public class OptionActivity extends LifecycleLoggingActivity {

    public static final String SHARED_PREFERENCES_OPTIONS = "SHARED_PREFERENCES_OPTIONS"; // shared prefs xml file

    public static final int DEFAULT_FORWARD_SECONDS = 5;
    public static final int MINIMUM_FORWARD_SECONDS = 1;
    public static final int MAXIMUM_FORWARD_SECONDS = 300;

    public static final int DEFAULT_TIME_BEFORE_SLEEP = 120;
    public static final int MINIMUM_TIME_BEFORE_SLEEP = 10;
    public static final int MAXIMUM_TIME_BEFORE_SLEEP = 60*24;

    public static final boolean DEFAULT_UNZIP_LOCAL  = true;
    public static final boolean DEFAULT_COPY_ZIP_LOCAL  = true;
    public static final boolean DEFAULT_SCREEN_ORIENTATION_LOCK  = true;

    EditText et_timeBeforeSleep;
    EditText et_ForwardSeconds;
    CheckBox chk_copyZip;
    CheckBox chk_UnZip;
    CheckBox chk_ScreenLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_options);

        et_timeBeforeSleep = findViewById(R.id.etTimeBeforeSleep);
        et_ForwardSeconds = findViewById(R.id.etForwardSeconds);
        chk_copyZip = findViewById(R.id.chk_copyzip_local);
        chk_UnZip = findViewById(R.id.chk_unzip_local);
        chk_ScreenLock = findViewById(R.id.chk_lock_orientation);

        int i = getTimeBeforeSleep();
        et_timeBeforeSleep.setText(String.valueOf(i));
        et_timeBeforeSleep.setOnFocusChangeListener((view, b) -> {
            if (!b) saveTimeBeforeSleep();
        });

        int intForwardSec = get_ForwardSeconds();
        et_ForwardSeconds.setText(String.valueOf(intForwardSec));
        et_ForwardSeconds.setOnFocusChangeListener((view, b) -> {
            if (!b) saveForwardSeconds();
        });

        chk_copyZip.setChecked(getCopyZipLocal());
        chk_copyZip.setOnCheckedChangeListener((buttonView, isChecked) -> setCopyZipLocal(isChecked));

        chk_UnZip.setChecked(getUnZipLocal());
        chk_UnZip.setOnCheckedChangeListener((buttonView, isChecked) -> setUnZipLocal(isChecked));

        chk_ScreenLock.setChecked(getScreenOrientationLock());
        chk_ScreenLock.setOnCheckedChangeListener((buttonView, isChecked) -> setScreenOrientationLock(isChecked));

        // TODO : allows options
        chk_UnZip.setEnabled(false);
        chk_copyZip.setEnabled(false);
        findViewById(R.id.ll_ZipOptions).setVisibility(View.INVISIBLE);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        saveTimeBeforeSleep();
        saveForwardSeconds();
    }

    private void saveTimeBeforeSleep() {
        String str = et_timeBeforeSleep.getText().toString().trim();
        if (str.equals("")) str = String.valueOf(DEFAULT_TIME_BEFORE_SLEEP);
        int i = Integer.parseInt(str);
        if (i < MINIMUM_TIME_BEFORE_SLEEP | i > MAXIMUM_TIME_BEFORE_SLEEP) {
            i = DEFAULT_TIME_BEFORE_SLEEP;
            myLongToast(getString(R.string.option_timeBeforeSleep_outOfBound));
        }
        setTimeBeforeSleep(i);
    }

    private void saveForwardSeconds() {
        String str = et_ForwardSeconds.getText().toString().trim();
        if (str.equals("")) str = String.valueOf(DEFAULT_FORWARD_SECONDS);
        int i = Integer.parseInt(str);
        if (i < MINIMUM_FORWARD_SECONDS | i > MAXIMUM_FORWARD_SECONDS) {
            i = DEFAULT_FORWARD_SECONDS;
            myLongToast("out of bounds - must be between 1 and 300");
        }
        set_ForwardSeconds(i);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /////////////////// SLEEP - AUTOMATIC PAUSE ///////////////////
    private void setTimeBeforeSleep(int i) {
        SharedPreferences.Editor editor = this.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit();
        editor.putInt("TIME_BEFORE_SLEEP",i).apply();
    }
    private int getTimeBeforeSleep() {
        SharedPreferences prefs = this.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE);
        return prefs.getInt("TIME_BEFORE_SLEEP", DEFAULT_TIME_BEFORE_SLEEP);
    }

    /////////////////// FORWARD-BACKWARD DURATION ///////////////////
    private void set_ForwardSeconds(int i) {
        SharedPreferences.Editor editor = this.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit();
        editor.putInt("FORWARD_SECONDS",i).apply();
    }
    private int get_ForwardSeconds() {
        SharedPreferences prefs = this.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE);
        return prefs.getInt("FORWARD_SECONDS", DEFAULT_FORWARD_SECONDS);
    }

    /////////////////// ZIP options ///////////////////
    private void setUnZipLocal(boolean bool) {
        SharedPreferences.Editor editor = this.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit();
        editor.putBoolean("UNZIP_LOCAL",bool).apply();
    }
    private void setCopyZipLocal(boolean bool) {
        SharedPreferences.Editor editor = this.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit();
        editor.putBoolean("COPY_ZIP_LOCAL",bool).apply();
    }
    private boolean getUnZipLocal() {
        SharedPreferences prefs = this.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE);
        return prefs.getBoolean("UNZIP_LOCAL", DEFAULT_UNZIP_LOCAL);
    }
    private boolean getCopyZipLocal() {
        SharedPreferences prefs = this.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE);
        return prefs.getBoolean("COPY_ZIP_LOCAL", DEFAULT_COPY_ZIP_LOCAL);
    }
    /////////////////// ZIP options ///////////////////

    /////////////////// SCREEN ORIENTATION options ///////////////////
    private void setScreenOrientationLock(boolean bool) {
        SharedPreferences.Editor editor = this.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit();
        editor.putBoolean("LOCK_SCREEN_ORIENTATION",bool).apply();
    }
    private boolean getScreenOrientationLock() {
        SharedPreferences prefs = this.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE);
        return prefs.getBoolean("LOCK_SCREEN_ORIENTATION", DEFAULT_SCREEN_ORIENTATION_LOCK);
    }
}
