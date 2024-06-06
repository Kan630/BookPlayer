package com.driot.bookplayer.activities;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Html;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.utils.PermissionRequest;
import com.driot.tonylib.KanLogger;

import static com.driot.bookplayer.global.Option.DEFAULT_FORWARD_SECONDS;
import static com.driot.bookplayer.global.Option.DEFAULT_TIME_BEFORE_SLEEP;
import static com.driot.bookplayer.utils.PermissionRequest.isRecordAudioPermissionGranted;
import static com.driot.tonylib.KanLogger.myLongToast;

import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.core.content.ContextCompat;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 20/12/20
 */
public class OptionActivity extends LifecycleLoggingActivity {

    public static final int MINIMUM_FORWARD_SECONDS = 1;
    public static final int MAXIMUM_FORWARD_SECONDS = 300;

    public static final int MINIMUM_TIME_BEFORE_SLEEP = 1;
    public static final int MAXIMUM_TIME_BEFORE_SLEEP = 60*24;


    public static final int  theme_01 = R.style.Theme_BookPlayer_Gray;
    public static final int  theme_02 = R.style.Theme_BookPlayer_Purple;
    public static final int  theme_03 = R.style.Theme_BookPlayer_Green;
    public static final int  theme_04 = R.style.Theme_BookPlayer_Pink;
    public static final int  theme_05 = R.style.Theme_BookPlayer_Blue;
    public static final int  theme_06 = R.style.Theme_BookPlayer_Orange;

    private int color_01, color_02, color_03, color_04, color_05, color_06; //look useless, the Object array could get rid of it....


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
    CheckBox chk_rewind_after_pause;
    CheckBox chk_copy_file;
    private PermissionRequest mPermissionRequest;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_options); //trigers AutofillManager notifyValueChanged  ignoring on state UNKNOWN  (pollute log in Android 12)

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
        chk_delete_source_file = findViewById(R.id.chk_delete_source_file_zip);
        chk_visualizer_on = findViewById(R.id.chk_visualizer_on);
        tx_Visualizer_on = findViewById(R.id.tx_Visualizer_on);
        btn_Color_01 = findViewById(R.id.btn_color_01);
        btn_Color_02 = findViewById(R.id.btn_color_02);
        btn_Color_03 = findViewById(R.id.btn_color_03);
        btn_Color_04 = findViewById(R.id.btn_color_04);
        btn_Color_05 = findViewById(R.id.btn_color_05);
        btn_Color_06 = findViewById(R.id.btn_color_06);
        chk_rewind_after_pause = findViewById(R.id.chk_rewind_after_pause);
        chk_copy_file = findViewById(R.id.chk_copy_file);

        et_timeBeforeSleep.setText(String.valueOf(Option.getTimeBeforeSleep(this)));
        et_ForwardSeconds.setText(String.valueOf(Option.get_ForwardSeconds(this)));

        chk_copyZip.setChecked(Option.getCopyZipLocal(this));
        chk_copyZip.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setCopyZipLocal(this, isChecked));

        chk_UnZip.setChecked(Option.getUnZipLocal(this));
        chk_UnZip.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setUnZipLocal(this, isChecked));

        chk_ScreenLock.setChecked(Option.getScreenOrientationLock(this));
        chk_ScreenLock.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setScreenOrientationLock(this, isChecked));

        chk_MailMethod.setChecked(Option.getMailMethod(this));
        chk_MailMethod.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setMailMethod(this, isChecked));

        chk_beep_chapter.setChecked(Option.getBeepChapter(this));
        chk_beep_chapter.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setBeepChapter(this, isChecked));

        chk_beep_bookend.setChecked(Option.getBeepBookEnd(this));
        chk_beep_bookend.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setBeepBookEnd(this, isChecked));

        chk_beep_autostop.setChecked(Option.getBeepAutoStop(this));
        chk_beep_autostop.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setBeepAutoStop(this, isChecked));

        chk_delete_source_file.setChecked(Option.getDeleteSourceFile(this));
        chk_delete_source_file.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.option_alert_delete_source_file_title))
                        .setMessage(getString(R.string.option_alert_delete_source_file_message))
                        .setCancelable(false)
                        .setPositiveButton("ok", (dialog, which) -> Option.setDeleteSourceFile(this, true))
                        .setNegativeButton("cancel", (dialogInterface, i) -> {})
                        .show();
            } else {
                Option.setDeleteSourceFile(this, false);
            }
        });

        chk_visualizer_on.setChecked(Option.getVisualizerOn(this));
        chk_visualizer_on.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Option.setVisualizerOn(this, isChecked);
            if (isChecked && !isRecordAudioPermissionGranted(this)) {
                myLog("checkBox ticked and permission not granted => requesting");
                requestPermissions();
            }
        });

        chk_rewind_after_pause.setChecked(Option.getRewindAfterPause(this));
        chk_rewind_after_pause.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setRewindAfterPause(this, isChecked));

        chk_copy_file.setChecked(Option.getCopyFile(this));
        chk_copy_file.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setCopyFile(this, isChecked));


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
            int mainColor = getPrimaryColorFromTheme(this, themeId);
            button.setBackgroundColor(mainColor);
            button.setOnClickListener(v -> changeBaseTheme(themeId));
        }

        setVisualizerPermissionText();

        if (getIntent().getBooleanExtra("CopyFileSetRed", false)) {
            TextView tv = findViewById(R.id.txtCopyFileHead);
            tv.setTextColor(Color.RED);
        }

        // TODO : allows options
        chk_UnZip.setEnabled(false);
        chk_copyZip.setEnabled(false);
        findViewById(R.id.ll_ZipOptions_vert).setVisibility(View.INVISIBLE);

    }

    private void setVisualizerPermissionText() {
        String txt;
        txt = getString(R.string.option_visualizer_text_01) + "<br><i>" + getString(R.string.option_visualizer_text_02);
        if (isRecordAudioPermissionGranted(this)) {
            txt = txt + ": <font color='green'>" + getString(R.string.option_visualizer_permissions_granted) + "</font></i>";
        } else {
            txt = txt + ": <font color='red'>" + getString(R.string.option_visualizer_permissions_denied_01) + "</font><br>" + getString(R.string.option_visualizer_permissions_denied_02) + "</i>";
        }
        tx_Visualizer_on.setText(Html.fromHtml(txt, Html.FROM_HTML_MODE_LEGACY));;
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
        Option.setTimeBeforeSleep(this, i);
    }

    private void saveForwardSeconds() {
        String str = et_ForwardSeconds.getText().toString().trim();
        if (str.equals("")) str = String.valueOf(DEFAULT_FORWARD_SECONDS);
        int i = Integer.parseInt(str);
        if (i < MINIMUM_FORWARD_SECONDS | i > MAXIMUM_FORWARD_SECONDS) {
            i = DEFAULT_FORWARD_SECONDS;
            myLongToast("out of bounds - must be between 1 and 300");
        }
        Option.set_ForwardSeconds(this, i);
    }

    @Override
    protected void onDestroy() {
        saveForwardSeconds();
        saveTimeBeforeSleep();
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
                .setPositiveButton("App Info", (dialog, which) -> {
                    openAppSettingsOnPhone();
                })
                .setNegativeButton("Cancel", null)
                .show();
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



    // ***********************************
    //           THEMES - COLORS
    // ***********************************
    public int getPrimaryColorFromTheme(Context context, @StyleRes int themeResId) {
        // Create a new theme based on the specified theme resource ID
        Resources.Theme theme = context.getResources().newTheme();
        theme.applyStyle(themeResId, true);

        // Obtain the colorPrimary attribute
        TypedArray typedArray = theme.obtainStyledAttributes(new int[]{androidx.appcompat.R.attr.colorPrimary});
        int primaryColor = typedArray.getColor(0, ContextCompat.getColor(context, android.R.color.black)); // Option to black if not found
        typedArray.recycle(); // Always recycle the TypedArray

        return primaryColor;
    }
    private void changeBaseTheme(int new_base_theme) {
        myLog("new Base theme is [" + new_base_theme + "]" );
        Option.setTheme(this, new_base_theme);
        this.getSharedPreferences(Option.SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit().putBoolean("ACTIVITY_OPTION_HAS_RESULT", true).apply(); //trick to reload MainActivity if color change
        recreate();
    }

    @Override
    public void finish() { //needed because of recreate()
        if (this.getSharedPreferences(Option.SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).getBoolean("ACTIVITY_OPTION_HAS_RESULT", false)) { //trick to reload MainActivity if color change
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
