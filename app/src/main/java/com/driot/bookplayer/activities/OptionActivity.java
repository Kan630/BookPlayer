package com.driot.bookplayer.activities;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.PermissionRequest;
import com.driot.tonylib.KanLogger;

import static com.driot.bookplayer.utils.PermissionRequest.isRecordAudioPermissionGranted;
import static com.driot.tonylib.KanLogger.myLongToast;
import static com.driot.tonylib.KanMail.DEFAULT_SEND_MAIL_METHOD_DEFAULT;

import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

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
    public static final int DEFAULT_CUSTOM_THEME = R.style.Theme_BookPlayer;

    public static final int  theme_01 = R.style.Theme_BookPlayer_Gray;
    public static final int  theme_02 = R.style.Theme_BookPlayer_Purple;
    public static final int  theme_03 = R.style.Theme_BookPlayer_Green;
    public static final int  theme_04 = R.style.Theme_BookPlayer_Pink;
    public static final int  theme_05 = R.style.Theme_BookPlayer_Blue;
    public static final int  theme_06 = R.style.Theme_BookPlayer_Orange;

    private int color_01, color_02, color_03, color_04, color_05, color_06;


    EditText et_timeBeforeSleep;
    EditText et_ForwardSeconds;
    CheckBox chk_copyZip, chk_UnZip;
    CheckBox chk_ScreenLock;
    CheckBox chk_MailMethod;
    CheckBox chk_beep_chapter, chk_beep_bookend, chk_beep_autostop;
    CheckBox chk_delete_source_file;
    CheckBox chk_visualizer_on;
    TextView tx_Visualizer_on;
    ImageButton btn_Color_01, btn_Color_02, btn_Color_03, btn_Color_04, btn_Color_05, btn_Color_06;
    Object[][] themesAndColors;
    private PermissionRequest mPermissionRequest;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_options); //trigers AutofillManager notifyValueChanged  ignoring on state UNKNOWN  (pollute log in Android 12)

        prefsOptions = this.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE);
        editorOptions = this.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit();
/*
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            this.revokeSelfPermissionOnKill(Manifest.permission.POST_NOTIFICATIONS);
        }

 */

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
        tx_Visualizer_on = findViewById(R.id.tx_Visualizer_on);
        btn_Color_01 = findViewById(R.id.btn_color_01);
        btn_Color_02 = findViewById(R.id.btn_color_02);
        btn_Color_03 = findViewById(R.id.btn_color_03);
        btn_Color_04 = findViewById(R.id.btn_color_04);
        btn_Color_05 = findViewById(R.id.btn_color_05);
        btn_Color_06 = findViewById(R.id.btn_color_06);

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
        chk_visualizer_on.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setVisualizerOnDefault(isChecked);
            if (isChecked && !isRecordAudioPermissionGranted(this)) {
                myLog("checkBox ticked and permission not granted => requesting");
                requestPermissions();
            }
        });

        themesAndColors = new Object[][] {
                {btn_Color_01, theme_01, color_01},
                {btn_Color_02, theme_02, color_02},
                {btn_Color_03, theme_03, color_03},
                {btn_Color_04, theme_04, color_04},
                {btn_Color_05, theme_05, color_05},
                {btn_Color_06, theme_06, color_06}
        };
        for (int color_iterator = 0; color_iterator < themesAndColors.length; color_iterator++) {
            ImageButton button = (ImageButton) themesAndColors[color_iterator][0];
            int themeId = (int) themesAndColors[color_iterator][1];
            int mainColor;
            mainColor = getPrimaryColorFromTheme(this, themeId);
            button.setBackgroundColor(mainColor);
            button.setOnClickListener(v -> changeBaseTheme(themeId));
        }

        setVisualizerPermissionText();

        // TODO : allows options
        chk_UnZip.setEnabled(false);
        chk_copyZip.setEnabled(false);
        findViewById(R.id.ll_ZipOptions_vert).setVisibility(View.INVISIBLE);

    }

    private void setVisualizerPermissionText() {
        if (isRecordAudioPermissionGranted(this)) {
            tx_Visualizer_on.setText(getString(R.string.option_visualizer_text));
        } else {
            String txt = getString(R.string.option_visualizer_text) + "\n" + getString(R.string.option_visualizer_no_permissions);
            tx_Visualizer_on.setText(txt);
        }
    }
/*
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        saveTimeBeforeSleep();
        saveForwardSeconds();
    }

 */

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

    // PERMISSIONS REMOVAL
    // adb shell pm revoke com.driot.bookplayer android.permission.RECORD_AUDIO
    // cd C:\Users\adrio\AppData\Local\Android\Sdk\platform-tools\
    // Developer Options => Security settings of USB debugging... = OFF

    //adb shell dumpsys package com.driot.bookplayer
    //adb -s P7LFRGOFKVKRLNPF shell dumpsys package com.driot.bookplayer

    //adb devices

    // tablet
    //R9JT308QFNA

    // old Oppo
    //P7LFRGOFKVKRLNPF

    // Xiaomi Redmi
    //36085d331d5c


    private void requestPermissions() {

        mPermissionRequest = PermissionRequest
                .with(this)
                .permissions(Manifest.permission.RECORD_AUDIO) //Manifest.permission.READ_EXTERNAL_STORAGE,
                .rationale(R.string.permission_record_audio_rationale)
                //.granted(R.string.permission_read_write_granted)  // Tonio no need to display message if granted OK
                .denied(R.string.permission_record_audio_denied)
                .snackbar((ViewGroup) findViewById(android.R.id.content))
                .callback(new PermissionRequest.Callback() {
                    @Override
                    public void onPermissionsGranted() {
                        myLog("Granted");
                    }

                    @Override
                    public void onPermissionsDenied() {
                        myLog("Denied");
                        showPermissionDeniedDialog(); //ask user again... //not working yet...
                    }
                })
                .submit();
    }
    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage(getString(R.string.permission_record_audio_rationale) + "\n\n" + getString(R.string.permission_record_audio_rationale_after_denied))
                .setPositiveButton("Retry", (dialog, which) -> {
                    //requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 3438994);//REQUEST_CODE // Request permissions again
                    //requestPermissions();//REQUEST_CODE // Request permissions again
                    //askAgainForPermission();
                    openAppSettingsOnPhone();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    private void askAgainForPermission() {
    //    requestPermissions(this, new String[] { Manifest.permission.RECORD_AUDIO }, 123654);
        //
        //ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 3438994);
    }
    private void openAppSettingsOnPhone() {
        myLog("openAppSettingsOnPhone()");
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        } catch (Exception e) {
            myLogE("openAppSettingsOnPhone() => " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        myLog("onRequestPermissionsResult()" + permissions[0] + " - " + requestCode + " - " + grantResults[0]);
        // Redirect hook call to permission helper method.
        if (mPermissionRequest != null) {
            mPermissionRequest.onRequestPermissionsResult(requestCode, permissions, grantResults);
            mPermissionRequest = null; // request no longer needed
        } else {
            myLogE("onRequestPermissionsResult() - mPermissionRequest is null ! bad hook");
        }
        setVisualizerPermissionText();
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




    // ***********************************
    //           THEMES - COLORS
    // ***********************************
    public int getPrimaryColorFromTheme(Context context, @StyleRes int themeResId) {
        // Create a new theme based on the specified theme resource ID
        Resources.Theme theme = context.getResources().newTheme();
        theme.applyStyle(themeResId, true);

        // Obtain the colorPrimary attribute
        TypedArray typedArray = theme.obtainStyledAttributes(new int[]{androidx.appcompat.R.attr.colorPrimary});
        int primaryColor = typedArray.getColor(0, ContextCompat.getColor(context, android.R.color.black)); // Default to black if not found
        typedArray.recycle(); // Always recycle the TypedArray

        return primaryColor;
    }
    private void changeBaseTheme(int new_base_theme) {
        myLog("new Base theme is [" + new_base_theme + "]" );
        editorOptions.putInt("CUSTOM_THEME", new_base_theme).apply();
        editorOptions.putBoolean("ACTIVITY_OPTION_HAS_RESULT", true).apply();
        recreate();
    }

    @Override
    public void finish() { //needed because of recreate()
        if (prefsOptions.getBoolean("ACTIVITY_OPTION_HAS_RESULT", false)) {
            setResult(Activity.RESULT_OK);
        }
        super.finish();
    }

    //// Saving scroll position where reloading activity (after applying theme color change)
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        ScrollView scrollView = findViewById(R.id.scrollView);
        outState.putInt("scroll_position", scrollView.getScrollY());
    }
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        final ScrollView scrollView = findViewById(R.id.scrollView);
        final int scrollPosition = savedInstanceState.getInt("scroll_position");
        scrollView.post(() -> scrollView.scrollTo(0, scrollPosition));
    }

    @Override
    protected void onResume() {
        super.onResume();
        setVisualizerPermissionText();
    }

    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
}
