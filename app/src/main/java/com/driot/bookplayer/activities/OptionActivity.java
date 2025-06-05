package com.driot.bookplayer.activities;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
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
import android.view.ViewStub;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.utils.PermissionRequest;
import com.driot.bookplayer.utils.KanLogger;

import static com.driot.bookplayer.global.Option.DEFAULT_FORWARD_SECONDS;
import static com.driot.bookplayer.global.Option.DEFAULT_TIME_BEFORE_SLEEP;
import static com.driot.bookplayer.utils.PermissionRequest.isRecordAudioPermissionGranted;

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

    EditText et_timeBeforeSleep;
    EditText et_ForwardSeconds;
    CheckBox chk_MailMethod;
    CheckBox chk_beep_chapter, chk_beep_bookend, chk_beep_autostop;
    CheckBox chk_delete_source_file;
    CheckBox chk_visualizer_on;
    TextView tx_Visualizer_on;
    ImageButton btn_Color_01, btn_Color_02, btn_Color_03, btn_Color_04, btn_Color_05, btn_Color_06;
    ImageButton btn_Color_07, btn_Color_08, btn_Color_09, btn_Color_10, btn_Color_11, btn_Color_12;
    ImageButton btn_Color_13, btn_Color_14, btn_Color_15, btn_Color_16, btn_Color_17, btn_Color_18;
    Object[][] themesAndColors;
    CheckBox chk_rewind_after_pause;
    CheckBox chk_copy_file;
    CheckBox chk_click_visualizer_playpause;
    CheckBox chk_tech_log_file;
    private PermissionRequest mPermissionRequest;

    private boolean areAdvancedOptionsVisible = false;
    private View advancedOptionsView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_options); //trigers AutofillManager notifyValueChanged  ignoring on state UNKNOWN  (pollute log in Android 12)

        et_timeBeforeSleep = findViewById(R.id.etTimeBeforeSleep);
        et_ForwardSeconds = findViewById(R.id.etForwardSeconds);
        chk_visualizer_on = findViewById(R.id.chk_visualizer_on);
        tx_Visualizer_on = findViewById(R.id.tx_Visualizer_on);
        btn_Color_01 = findViewById(R.id.btn_color_01);
        btn_Color_02 = findViewById(R.id.btn_color_02);
        btn_Color_03 = findViewById(R.id.btn_color_03);
        btn_Color_04 = findViewById(R.id.btn_color_04);
        btn_Color_05 = findViewById(R.id.btn_color_05);
        btn_Color_06 = findViewById(R.id.btn_color_06);
        btn_Color_07 = findViewById(R.id.btn_color_07);
        btn_Color_08 = findViewById(R.id.btn_color_08);
        btn_Color_09 = findViewById(R.id.btn_color_09);
        btn_Color_10 = findViewById(R.id.btn_color_10);
        btn_Color_11 = findViewById(R.id.btn_color_11);
        btn_Color_12 = findViewById(R.id.btn_color_12);
        btn_Color_13 = findViewById(R.id.btn_color_13);
        btn_Color_14 = findViewById(R.id.btn_color_14);
        btn_Color_15 = findViewById(R.id.btn_color_15);
        btn_Color_16 = findViewById(R.id.btn_color_16);
        btn_Color_17 = findViewById(R.id.btn_color_17);
        btn_Color_18 = findViewById(R.id.btn_color_18);

        et_timeBeforeSleep.setText(String.valueOf(Option.getTimeBeforeSleep(this)));
        et_ForwardSeconds.setText(String.valueOf(Option.get_ForwardSeconds(this)));

        chk_visualizer_on.setChecked(Option.getVisualizerOn(this));
        chk_visualizer_on.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Option.setVisualizerOn(this, isChecked);
            if (isChecked && !isRecordAudioPermissionGranted(this)) {
                myLog("checkBox ticked and permission not granted => requesting");
                requestPermissions();
            }
        });

        chk_click_visualizer_playpause = findViewById(R.id.chk_click_visualizer_playpause);
        chk_click_visualizer_playpause.setChecked(Option.getClickVisualizerPlayPause(this));
        chk_click_visualizer_playpause.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setClickVisualizerPlayPause(this, isChecked));

        themesAndColors = new Object[][] {
                {btn_Color_01, R.style.Theme_BookPlayer_Gray},
                {btn_Color_02, R.style.Theme_BookPlayer_Purple},
                {btn_Color_03, R.style.Theme_BookPlayer_Brown},
                {btn_Color_04, R.style.Theme_BookPlayer_Blue},
                {btn_Color_05, R.style.Theme_BookPlayer_Cyan},
                {btn_Color_06, R.style.Theme_BookPlayer_Turquoise},
                {btn_Color_07, R.style.Theme_BookPlayer_Orange},
                {btn_Color_08, R.style.Theme_BookPlayer_Yellow},
                {btn_Color_09, R.style.Theme_BookPlayer_YellowDark},
                {btn_Color_10, R.style.Theme_BookPlayer_Red},
                {btn_Color_11, R.style.Theme_BookPlayer_RedDark},
                {btn_Color_12, R.style.Theme_BookPlayer_Indigo},
                {btn_Color_13, R.style.Theme_BookPlayer_PinkLight},
                {btn_Color_14, R.style.Theme_BookPlayer_Pink},
                {btn_Color_15, R.style.Theme_BookPlayer_PinkDark},
                {btn_Color_16, R.style.Theme_BookPlayer_GreenLight},
                {btn_Color_17, R.style.Theme_BookPlayer_Green},
                {btn_Color_18, R.style.Theme_BookPlayer_GreenDark},
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


        Button btnShowAdvanced = findViewById(R.id.btn_show_advanced);
        btnShowAdvanced.setOnClickListener(v -> toggleAdvancedOptions());

    }

    private void initializeAdvancedOptions() {
        chk_MailMethod = findViewById(R.id.chk_mail_method_default);
        chk_beep_chapter = findViewById(R.id.chk_beep_chapter_default);
        chk_beep_bookend = findViewById(R.id.chk_beep_bookend_defaut);
        chk_beep_autostop = findViewById(R.id.chk_beep_autostop_defaut);
        chk_delete_source_file = findViewById(R.id.chk_delete_source_file);
        chk_rewind_after_pause = findViewById(R.id.chk_rewind_after_pause);
        chk_copy_file = findViewById(R.id.chk_copy_file);
        chk_tech_log_file = findViewById(R.id.chk_tech_log_file);

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
        chk_rewind_after_pause.setChecked(Option.getRewindAfterPause(this));
        chk_rewind_after_pause.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setRewindAfterPause(this, isChecked));

        chk_copy_file.setChecked(Option.getCopyFile(this));
        chk_copy_file.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setCopyFile(this, isChecked));

        chk_tech_log_file.setChecked(Option.getTechLog(this));
        chk_tech_log_file.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setTechLog(this, isChecked));

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

    private void toggleAdvancedOptions() {
        ViewStub stub = findViewById(R.id.viewStub_advanced_options);
        Button btnShowAdvanced = findViewById(R.id.btn_show_advanced);
        ScrollView scrollView = findViewById(R.id.scrollView);
        if (!areAdvancedOptionsVisible) {
            if (advancedOptionsView == null) {
                advancedOptionsView = stub.inflate();
                initializeAdvancedOptions();
            }
            advancedOptionsView.setVisibility(View.VISIBLE);
            ((Button) findViewById(R.id.btn_show_advanced)).setText(getString(R.string.option_hide_advanced_options));
            btnShowAdvanced.post(() -> {
                int y = btnShowAdvanced.getTop();
                scrollView.smoothScrollTo(0, y);
            });
        } else {
            if (advancedOptionsView != null) {
                advancedOptionsView.setVisibility(View.GONE);
            }
            ((Button) findViewById(R.id.btn_show_advanced)).setText(getString(R.string.option_show_advanced_options));
            btnShowAdvanced.post(() -> {
                //scrollView.smoothScrollTo(0, 0);
            });
        }
        areAdvancedOptionsVisible = !areAdvancedOptionsVisible;
    }

    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    private void myLongToast(String str) { KanLogger.myLongToast(this.getClass().getName(), str); }
}
