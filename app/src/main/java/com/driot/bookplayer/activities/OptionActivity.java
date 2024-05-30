package com.driot.bookplayer.activities;

import android.Manifest;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.PermissionRequest;
import com.driot.tonylib.KanLogger;

import static com.driot.tonylib.KanLogger.myLog;
import static com.driot.tonylib.KanLogger.myLongToast;
import static com.driot.tonylib.KanMail.DEFAULT_SEND_MAIL_METHOD_DEFAULT;

import androidx.annotation.NonNull;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 20/12/20
 */
public class OptionActivity extends LifecycleLoggingActivity {

    public SharedPreferences prefsOptions;
    public SharedPreferences.Editor editorOptions;

    public static final String SHARED_PREFERENCES_OPTIONS = "SHARED_PREFERENCES_OPTIONS"; // shared prefs xml file

    public static final int DEFAULT_FORWARD_SECONDS = 5;
    public static final int MINIMUM_FORWARD_SECONDS = 1;
    public static final int MAXIMUM_FORWARD_SECONDS = 300;

    public static final int DEFAULT_TIME_BEFORE_SLEEP = 120;
    public static final int MINIMUM_TIME_BEFORE_SLEEP = 1;
    public static final int MAXIMUM_TIME_BEFORE_SLEEP = 60*24;

    public static final boolean DEFAULT_UNZIP_LOCAL  = true;
    public static final boolean DEFAULT_COPY_ZIP_LOCAL  = true;
    public static final boolean DEFAULT_SCREEN_ORIENTATION_LOCK  = true;
    public static final boolean DEFAULT_BEEP_CHAPTER = true;
    public static final boolean DEFAULT_BEEP_BOOKEND = true;
    public static final boolean DEFAULT_BEEP_AUTOSTOP = true;
    public static final boolean DEFAULT_DELETE_SOURCE_FILE = false;
    public static final boolean DEFAULT_VISUALIZER_ON = false;

    EditText et_timeBeforeSleep;
    EditText et_ForwardSeconds;
    CheckBox chk_copyZip, chk_UnZip;
    CheckBox chk_ScreenLock;
    CheckBox chk_MailMethod;
    CheckBox chk_beep_chapter, chk_beep_bookend, chk_beep_autostop;
    CheckBox chk_delete_source_file;
    CheckBox chk_visualizer_on;
    private PermissionRequest mPermissionRequest;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_options);

        prefsOptions = this.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE);
        editorOptions = this.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit();

        et_timeBeforeSleep = findViewById(R.id.etTimeBeforeSleep);
        et_ForwardSeconds = findViewById(R.id.etForwardSeconds);
        chk_copyZip = findViewById(R.id.chk_copyzip_local);
        chk_UnZip = findViewById(R.id.chk_unzip_local);
        chk_ScreenLock = findViewById(R.id.chk_lock_orientation);
        chk_MailMethod = findViewById(R.id.chk_mail_method_default);
        chk_beep_chapter = findViewById(R.id.chk_beep_chapter_default);
        chk_beep_bookend = findViewById(R.id.chk_beep_bookend_defaut);
        chk_beep_autostop = findViewById(R.id.chk_beep_autostop_defaut);
        chk_delete_source_file = findViewById(R.id.chk_delete_source_file);
        chk_visualizer_on = findViewById(R.id.chk_visualizer_on);

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

        chk_MailMethod.setChecked(getMailMethodDefault());
        chk_MailMethod.setOnCheckedChangeListener((buttonView, isChecked) -> setMailMethodDefault(isChecked));

        chk_beep_chapter.setChecked(getBeepChapterDefault());
        chk_beep_chapter.setOnCheckedChangeListener((buttonView, isChecked) -> setBeepChapterDefault(isChecked));

        chk_beep_bookend.setChecked(getBeepBookEndDefault());
        chk_beep_bookend.setOnCheckedChangeListener((buttonView, isChecked) -> setBeepBookEndDefault(isChecked));

        chk_beep_autostop.setChecked(getBeepAutoStopDefault());
        chk_beep_autostop.setOnCheckedChangeListener((buttonView, isChecked) -> setBeepAutoStopDefault(isChecked));

        chk_delete_source_file.setChecked(getDeleteSourceFileDefault());
        chk_delete_source_file.setOnCheckedChangeListener((buttonView, isChecked) -> setDeleteSourceFileDefault(isChecked));

        chk_visualizer_on.setChecked(getVisualizerOnDefault());
        chk_visualizer_on.setOnCheckedChangeListener((buttonView, isChecked) -> {setVisualizerOnDefault(isChecked);
            requestPermissions();});


        // TODO : allows options
        chk_UnZip.setEnabled(false);
        chk_copyZip.setEnabled(false);
        findViewById(R.id.ll_ZipOptions_vert).setVisibility(View.INVISIBLE);
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

    private void requestPermissions() {
        mPermissionRequest = PermissionRequest
                .with(this)
                .permissions(Manifest.permission.RECORD_AUDIO) //Manifest.permission.READ_EXTERNAL_STORAGE,
                .rationale(R.string.permission_read_write_rationale)
                //.granted(R.string.permission_read_write_granted)  // Tonio no need to display message if granted OK
                .denied(R.string.permission_read_write_denied)
                .snackbar((ViewGroup) findViewById(android.R.id.content))
                .submit();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        myLog("onRequestPermissionsResult()" + permissions[0] + " - " + requestCode + " - " + grantResults[0]);
        // Redirect hook call to permission helper method.
        if (mPermissionRequest != null) {
            mPermissionRequest.onRequestPermissionsResult(requestCode, permissions, grantResults);
            mPermissionRequest = null; // request no longer needed
        } else {
            myLogE("onRequestPermissionsResult() - mPermissionRequest is null ! bad hook");
        }
    }

    /////////////////// SLEEP - AUTOMATIC PAUSE ///////////////////
    private void setTimeBeforeSleep(int i) {editorOptions.putInt("TIME_BEFORE_SLEEP",i).apply();}
    private int getTimeBeforeSleep() {return prefsOptions.getInt("TIME_BEFORE_SLEEP", DEFAULT_TIME_BEFORE_SLEEP);}

    /////////////////// FORWARD-BACKWARD DURATION ///////////////////
    private void set_ForwardSeconds(int i) {editorOptions.putInt("FORWARD_SECONDS",i).apply();}
    private int get_ForwardSeconds() {return prefsOptions.getInt("FORWARD_SECONDS", DEFAULT_FORWARD_SECONDS);}

    /////////////////// ZIP options ///////////////////
    private void setUnZipLocal(boolean bool) {editorOptions.putBoolean("UNZIP_LOCAL",bool).apply();}
    private void setCopyZipLocal(boolean bool) {editorOptions.putBoolean("COPY_ZIP_LOCAL",bool).apply();}
    private boolean getUnZipLocal() {return prefsOptions.getBoolean("UNZIP_LOCAL", DEFAULT_UNZIP_LOCAL);}
    private boolean getCopyZipLocal() {return prefsOptions.getBoolean("COPY_ZIP_LOCAL", DEFAULT_COPY_ZIP_LOCAL);}

    /////////////////// SCREEN ORIENTATION options ///////////////////
    private void setScreenOrientationLock(boolean bool) {editorOptions.putBoolean("LOCK_SCREEN_ORIENTATION",bool).apply();}
    private boolean getScreenOrientationLock() {return prefsOptions.getBoolean("LOCK_SCREEN_ORIENTATION", DEFAULT_SCREEN_ORIENTATION_LOCK);}

    /////////////////// SEND MAIL options ///////////////////
    private void setMailMethodDefault(boolean bool) {
        editorOptions.putBoolean("SEND_MAIL_METHOD_DEFAULT",bool).apply();
        myLog("bool : " + String.valueOf(bool));
        }
    private Boolean getMailMethodDefault() {return prefsOptions.getBoolean("SEND_MAIL_METHOD_DEFAULT", DEFAULT_SEND_MAIL_METHOD_DEFAULT);}

    /////////////////// BEEP options ///////////////////
    private void setBeepChapterDefault(boolean bool) {editorOptions.putBoolean("BEEP_CHAPTER",bool).apply();}
    private Boolean getBeepChapterDefault() {return prefsOptions.getBoolean("BEEP_CHAPTER", DEFAULT_BEEP_CHAPTER);}

    private void setBeepBookEndDefault(boolean bool) {editorOptions.putBoolean("BEEP_BOOKEND",bool).apply();}
    private Boolean getBeepBookEndDefault() {return prefsOptions.getBoolean("BEEP_BOOKEND", DEFAULT_BEEP_BOOKEND);}
    private void setBeepAutoStopDefault(boolean bool) {editorOptions.putBoolean("BEEP_AUTOSTOP",bool).apply();}
    private Boolean getBeepAutoStopDefault() {return prefsOptions.getBoolean("BEEP_AUTOSTOP", DEFAULT_BEEP_AUTOSTOP);}

    /////////////////// DELETE SOURCE FILE option ///////////////////
    private void setDeleteSourceFileDefault(boolean bool) {editorOptions.putBoolean("DELETE_SOURCE_FILE",bool).apply();}
    private Boolean getDeleteSourceFileDefault() {return prefsOptions.getBoolean("DELETE_SOURCE_FILE", DEFAULT_DELETE_SOURCE_FILE);}

    /////////////////// VISUALIZER option ///////////////////
    private void setVisualizerOnDefault(boolean bool) {editorOptions.putBoolean("VISUALIZER_ON",bool).apply();}
    private Boolean getVisualizerOnDefault() {return prefsOptions.getBoolean("VISUALIZER_ON", DEFAULT_VISUALIZER_ON);}

    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
}
