package com.driot.bookplayer.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import com.driot.bookplayer.R;

import static com.driot.tonylib.KanLogger.myLog;
import static com.driot.tonylib.KanLogger.myLongToast;
import static com.driot.tonylib.KanLogger.myToast;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 20/12/20
 */
public class OptionActivity extends LifecycleLoggingActivity {

    public static final String SHARED_PREFERENCE_TIME_BEFORE_SLEEP="SHARED_PREFERENCE_TIME_BEFORE_SLEEP";
    public static final String SHARED_PREFERENCE_ZIP_OPTIONS="SHARED_PREFERENCE_ZIP_OPTIONS";
    public static final int DEFAULT_TIME_BEFORE_SLEEP = 120;
    public static final int MINIMUM_TIME_BEFORE_SLEEP = 10;
    public static final int MAXIMUM_TIME_BEFORE_SLEEP = 60*24;
    public static final boolean DEFAULT_UNZIP_LOCAL  = true;
    public static final boolean DEFAULT_COPY_ZIP_LOCAL  = true;

    EditText et_timeBeforeSleep;
    CheckBox chk_copyZip;
    CheckBox chk_UnZip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_options);

        et_timeBeforeSleep = findViewById(R.id.etTimeBeforeSleep);
        chk_copyZip = findViewById(R.id.chk_copyzip_local);
        chk_UnZip = findViewById(R.id.chk_unzip_local);

        int i = getTimeBeforeSleep();
        et_timeBeforeSleep.setText(String.valueOf(i));
        et_timeBeforeSleep.setOnFocusChangeListener((view, b) -> {
            if (!b) saveTimeBeforeSleep();
        });

        chk_copyZip.setChecked(getCopyZipLocal());
        chk_copyZip.setOnCheckedChangeListener((buttonView, isChecked) -> setCopyZipLocal(isChecked));

        chk_UnZip.setChecked(getUnZipLocal());
        chk_UnZip.setOnCheckedChangeListener((buttonView, isChecked) -> setUnZipLocal(isChecked));

        // TODO : allows options
        chk_UnZip.setEnabled(false);
        chk_copyZip.setEnabled(false);
        findViewById(R.id.ll_ZipOptions).setVisibility(View.INVISIBLE);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        saveTimeBeforeSleep();
    }

    private void saveTimeBeforeSleep() {
        String str = et_timeBeforeSleep.getText().toString().trim();
        if (str.equals("")) str = String.valueOf(DEFAULT_TIME_BEFORE_SLEEP);
        int i = Integer.parseInt(str);
        if (i < MINIMUM_TIME_BEFORE_SLEEP | i > MAXIMUM_TIME_BEFORE_SLEEP) {
            i=DEFAULT_TIME_BEFORE_SLEEP;
            myLongToast(getString(R.string.option_timeBeforeSleep_outOfBound));
        }
        setTimeBeforeSleep(i);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void setTimeBeforeSleep(int i) {
        SharedPreferences.Editor editor = this.getSharedPreferences(SHARED_PREFERENCE_TIME_BEFORE_SLEEP, MODE_PRIVATE).edit();
        editor.putInt("TIME_BEFORE_SLEEP",i).apply();
    }

    private int getTimeBeforeSleep() {
        SharedPreferences prefs = this.getSharedPreferences(SHARED_PREFERENCE_TIME_BEFORE_SLEEP, MODE_PRIVATE);
        return prefs.getInt("TIME_BEFORE_SLEEP", DEFAULT_TIME_BEFORE_SLEEP);
    }


    /////////////////// ZIP options ///////////////////
    private void setUnZipLocal(boolean i) {
        SharedPreferences.Editor editor = this.getSharedPreferences(SHARED_PREFERENCE_ZIP_OPTIONS, MODE_PRIVATE).edit();
        editor.putBoolean("UNZIP_LOCAL",i).apply();
    }
    private void setCopyZipLocal(boolean i) {
        SharedPreferences.Editor editor = this.getSharedPreferences(SHARED_PREFERENCE_ZIP_OPTIONS, MODE_PRIVATE).edit();
        editor.putBoolean("COPY_ZIP_LOCAL",i).apply();
    }
    private boolean getUnZipLocal() {
        SharedPreferences prefs = this.getSharedPreferences(SHARED_PREFERENCE_ZIP_OPTIONS, MODE_PRIVATE);
        return prefs.getBoolean("UNZIP_LOCAL", DEFAULT_UNZIP_LOCAL);
    }
    private boolean getCopyZipLocal() {
        SharedPreferences prefs = this.getSharedPreferences(SHARED_PREFERENCE_ZIP_OPTIONS, MODE_PRIVATE);
        return prefs.getBoolean("COPY_ZIP_LOCAL", DEFAULT_COPY_ZIP_LOCAL);
    }
    /////////////////// ZIP options ///////////////////

}
