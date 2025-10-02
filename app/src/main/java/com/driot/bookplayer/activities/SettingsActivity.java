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
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.LocaleHelper;
import com.driot.bookplayer.helpers.LanguageHelper;
import com.driot.bookplayer.helpers.TtsHelper;
import com.driot.bookplayer.utils.NetworkUtils;
import com.driot.bookplayer.utils.PermissionRequest;
import com.driot.bookplayer.utils.log.LoggingActivity;


import static com.driot.bookplayer.global.Option.DEFAULT_FORWARD_SECONDS;
import static com.driot.bookplayer.global.Option.DEFAULT_TIME_BEFORE_SLEEP;
import static com.driot.bookplayer.utils.ComponentUtils.setOpenWithProxyEnabled;
import static com.driot.bookplayer.utils.ComponentUtils.setOpenWithProxyEnabled_all;
import static com.driot.bookplayer.utils.PermissionRequest.isRecordAudioPermissionGranted;
import static com.driot.bookplayer.helpers.StorageHelper.isExternalSDCardAvailable;

import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 20/12/20
 */
public class SettingsActivity extends LoggingActivity {

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
    CheckBox chk_start_next_track_at_zero;
    CheckBox chk_stop_audio_if_user_closes_app;
    CheckBox chk_auto_play_on_main_player;
    CheckBox chk_copy_file;
    CheckBox chk_click_visualizer_playpause;
    CheckBox chk_tech_log_file;
    CheckBox chk_open_with;
    CheckBox chk_open_with_all;
    CheckBox chk_split_m4b;
    CheckBox chk_use_sd_card;
    CheckBox chk_create_cover;
    Spinner appLanguageSpinner;
    Spinner ttsVoiceSpinner;
    String lastSavedTtsVoice;
    View advancedOptionsLayout;
    Button btnShowAdvanced;
    Button btnPodcastOptions;
    ScrollView scrollView;
    private Button btnNightMode;
    AutoCloseable ttsHandle;
    private Spinner spFontFamily;
    private SeekBar seekTextSize;
    private TextView tvTextSizeValue;
    private TextView tvPreview;
    private static class FontChoice {
        final String key;   // ex: "sans-serif"
        final String label; // ex: "Sans-serif"
        FontChoice(String key, String label) { this.key = key; this.label = label; }
    }
    private List<FontChoice> fontChoices;


    private PermissionRequest mPermissionRequest;

    private boolean areAdvancedOptionsVisible = false;

    LinearLayout ll_visualizer_on, ll_visualizer_playpause, ll_copy_file, ll_delete_source_file;
    LinearLayout ll_beep_chapter, ll_beep_bookend, ll_beep_autostop;
    LinearLayout ll_rewind_after_pause, ll_start_next_track_at_zero, ll_stop_audio_if_user_closes_app, ll_auto_play_on_main_player;
    LinearLayout ll_tech_log_file, ll_mail_method_default;
    LinearLayout ll_open_with, ll_open_with_all, ll_split_m4b, ll_use_sd_card;
    LinearLayout ll_container_sd_card, ll_create_cover;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings); //trigers AutofillManager notifyValueChanged  ignoring on state UNKNOWN  (pollute log in Android 12)
        InsetHelper.apply(this);

        scrollView = findViewById(R.id.scrollView);
        /*
        InsetHelper.applyEdgeToEdge(
                this,
                scrollView,   // top container (adds status bar height to top padding)
                scrollView,   // bottom container (adds max(nav, IME) to bottom padding)
                scrollView    // content sides (adds left/right nav/gesture insets)
        );
         */

        advancedOptionsLayout = findViewById(R.id.layout_advanced_options);
        et_timeBeforeSleep = findViewById(R.id.etTimeBeforeSleep);
        et_ForwardSeconds = findViewById(R.id.etForwardSeconds);
        chk_create_cover = findViewById(R.id.chk_create_cover);
        ll_create_cover = findViewById(R.id.ll_create_cover);
        chk_visualizer_on = findViewById(R.id.chk_visualizer_on);
        ll_visualizer_on = findViewById(R.id.ll_visualizer_on);
        tx_Visualizer_on = findViewById(R.id.tx_Visualizer_on);
        chk_click_visualizer_playpause = findViewById(R.id.chk_click_visualizer_playpause);
        ll_visualizer_playpause = findViewById(R.id.ll_visualizer_playpause);
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

        et_timeBeforeSleep.setText(String.valueOf(Option.getTimeBeforeSleep()));
        et_ForwardSeconds.setText(String.valueOf(Option.get_ForwardSeconds()));

        ll_container_sd_card = findViewById(R.id.ll_container_sd_card);
        if (isExternalSDCardAvailable(this)) {
            ll_container_sd_card.setVisibility(View.VISIBLE);
            chk_use_sd_card = findViewById(R.id.chk_use_sd_card);
            ll_use_sd_card = findViewById(R.id.ll_use_sd_card);
            chk_use_sd_card.setChecked(Option.getUseSdCard());
            ll_use_sd_card.setOnClickListener(v -> chk_use_sd_card.toggle());
            chk_use_sd_card.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setUseSdCard(isChecked));
        } else {
            ll_container_sd_card.setVisibility(View.GONE);
        }

        ll_create_cover.setOnClickListener(v -> chk_create_cover.toggle());
        chk_create_cover.setChecked(Option.getCreateCover());
        chk_create_cover.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setCreateCover(isChecked));

// Auto download spinner
        String[] autoOptions = new String[] {
                getString(R.string.download_any),
                getString(R.string.download_wifi),
                getString(R.string.download_unmetered),
        };
        ArrayAdapter<String> autoAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, autoOptions);
        autoAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        Spinner spinnerAuto = findViewById(R.id.spinner_download_auto);
        spinnerAuto.setAdapter(autoAdapter);
        spinnerAuto.setSelection(Option.getNetworkPolicyAutoDownload().ordinal());
        spinnerAuto.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                Option.setNetworkPolicyAutoDownload(NetworkUtils.NetworkPolicyAuto.values()[pos]);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
// Manual download spinner
        String[] manualOptions = new String[] {
                getString(R.string.download_never_ask),
                getString(R.string.download_ask_if_not_wifi),
                getString(R.string.download_ask_if_unmetered)
        };
        ArrayAdapter<String> manualAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, manualOptions);
        manualAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        Spinner spinnerUser = findViewById(R.id.spinner_download_user);
        spinnerUser.setAdapter(manualAdapter);
        spinnerUser.setSelection(Option.getNetworkPolicyManualDownload().ordinal());
        spinnerUser.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                Option.setNetworkPolicyManualDownload(NetworkUtils.NetworkPolicyManual.values()[pos]);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        chk_visualizer_on.setChecked(Option.getVisualizerOn());
        ll_visualizer_on.setOnClickListener(v -> chk_visualizer_on.toggle());
        chk_visualizer_on.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Option.setVisualizerOn(isChecked);
            if (isChecked && !isRecordAudioPermissionGranted(this)) {
                myLog("checkBox ticked and permission not granted => requesting");
                requestRecordAudioPermission();
            }
        });
        chk_click_visualizer_playpause.setChecked(Option.getClickVisualizerPlayPause());
        ll_visualizer_playpause.setOnClickListener(v -> chk_click_visualizer_playpause.toggle());
        chk_click_visualizer_playpause.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setClickVisualizerPlayPause(isChecked));

        setVisualizerPermissionText();

//THEMES

        btnNightMode = findViewById(R.id.btn_night_mode);
        btnNightMode.setText(Option.getNightMode());
        btnNightMode.setOnClickListener(v -> showNightModeChooser());

        themesAndColors = new Object[][] {
                {btn_Color_01, "gray", R.style.Theme_BookPlayer_Gray},
                {btn_Color_02, "purple", R.style.Theme_BookPlayer_Purple},
                {btn_Color_03, "brown", R.style.Theme_BookPlayer_Brown},
                {btn_Color_04, "blue", R.style.Theme_BookPlayer_Blue},
                {btn_Color_05, "cyan", R.style.Theme_BookPlayer_Cyan},
                {btn_Color_06, "turquoise", R.style.Theme_BookPlayer_Turquoise},
                {btn_Color_07, "orange", R.style.Theme_BookPlayer_Orange},
                {btn_Color_08, "yellow", R.style.Theme_BookPlayer_Yellow},
                {btn_Color_09, "yellowDark", R.style.Theme_BookPlayer_YellowDark},
                {btn_Color_10, "red", R.style.Theme_BookPlayer_Red},
                {btn_Color_11, "redDark", R.style.Theme_BookPlayer_RedDark},
                {btn_Color_12, "indigo", R.style.Theme_BookPlayer_Indigo},
                {btn_Color_13, "pinkLight", R.style.Theme_BookPlayer_PinkLight},
                {btn_Color_14, "pink", R.style.Theme_BookPlayer_Pink},
                {btn_Color_15, "pinkDark", R.style.Theme_BookPlayer_PinkDark},
                {btn_Color_16, "greenLight", R.style.Theme_BookPlayer_GreenLight},
                {btn_Color_17, "green", R.style.Theme_BookPlayer_Green},
                {btn_Color_18, "greenDark", R.style.Theme_BookPlayer_GreenDark},
        };

        for (Object[] entry : themesAndColors) {
            ImageButton button = (ImageButton) entry[0];
            String themeKey = (String) entry[1];
            int themeResId = (int) entry[2];

            int mainColor = getPrimaryColorFromTheme(this, themeResId);
            button.setBackgroundColor(mainColor);
            button.setOnClickListener(v -> changeBaseTheme(themeKey));
        }


// --- dans onCreate(...) après setContentView(...) ---
        spFontFamily = findViewById(R.id.sp_font_family);
        seekTextSize = findViewById(R.id.seek_text_size);
        tvTextSizeValue = findViewById(R.id.tv_text_size_value);
        tvPreview = findViewById(R.id.tv_text_preview);

// 1) Prépare liste de polices (toutes existent depuis API 26+)
        fontChoices = new ArrayList<>();
        fontChoices.add(new FontChoice("sans-serif", "Sans-serif"));
        fontChoices.add(new FontChoice("serif", "Serif"));
        fontChoices.add(new FontChoice("monospace", "Monospace"));
        fontChoices.add(new FontChoice("casual", "Casual"));
        fontChoices.add(new FontChoice("cursive", "Cursive"));
        fontChoices.add(new FontChoice("serif-monospace", "Serif Monospace"));
        fontChoices.add(new FontChoice("sans-serif-condensed", "Sans-serif Condensed"));
        fontChoices.add(new FontChoice("sans-serif-medium", "Sans-serif Medium"));
        fontChoices.add(new FontChoice("sans-serif-smallcaps", "Sans-serif Smallcaps"));

// 2) Adapter Spinner
        ArrayAdapter<String> fontAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                toLabels(fontChoices));
        fontAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spFontFamily.setAdapter(fontAdapter);

// 3) Sélectionne valeur sauvegardée
        String savedFamily = Option.getFontFamilyKey();
        int savedIndex = indexOfKey(fontChoices, savedFamily);
        if (savedIndex < 0) savedIndex = 0;
        spFontFamily.setSelection(savedIndex, false);

// 4) SeekBar text size
        float savedSizeSp = Option.getTextSizeSp();
// On mappe le min=12sp → progress=12 ; max=36sp → progress=36
        seekTextSize.setMax((int) Option.MAX_TEXT_SIZE_SP);
        seekTextSize.setProgress((int) Math.max(Option.MIN_TEXT_SIZE_SP, Math.min(Option.MAX_TEXT_SIZE_SP, savedSizeSp)));
        tvTextSizeValue.setText(((int) savedSizeSp) + "sp");

// 5) Applique l’aperçu initial
        applyPreview(savedFamily, savedSizeSp);

// 6) Listeners
        spFontFamily.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String key = fontChoices.get(position).key;
                Option.setFontFamilyKey(key);
                applyPreview(key, Option.getTextSizeSp());
                triggerFolderListReloadOnClose();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        seekTextSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // clamp min
                int clamped = Math.max((int) Option.MIN_TEXT_SIZE_SP, progress);
                tvTextSizeValue.setText(clamped + "sp");
                Option.setTextSizeSp(clamped);
                triggerFolderListReloadOnClose();
                applyPreview(Option.getFontFamilyKey(), clamped);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });


        chk_beep_chapter = findViewById(R.id.chk_beep_chapter);
        chk_beep_bookend = findViewById(R.id.chk_beep_bookend);
        chk_beep_autostop = findViewById(R.id.chk_beep_autostop);
        chk_delete_source_file = findViewById(R.id.chk_delete_source_file);
        chk_rewind_after_pause = findViewById(R.id.chk_rewind_after_pause);
        chk_copy_file = findViewById(R.id.chk_copy_file);
        chk_tech_log_file = findViewById(R.id.chk_tech_log_file);
        chk_open_with = findViewById(R.id.chk_open_with);
        chk_open_with_all = findViewById(R.id.chk_open_with_all);
        chk_split_m4b = findViewById(R.id.chk_split_m4b);
        ll_split_m4b = findViewById(R.id.ll_split_m4b);
        ll_copy_file = findViewById(R.id.ll_copy_file);
        ll_delete_source_file = findViewById(R.id.ll_delete_source_file);
        ll_open_with = findViewById(R.id.ll_open_with);
        ll_open_with_all = findViewById(R.id.ll_open_with_all);
        ll_beep_chapter = findViewById(R.id.ll_beep_chapter);
        ll_beep_bookend = findViewById(R.id.ll_beep_bookend);
        ll_beep_autostop = findViewById(R.id.ll_beep_autostop);
        ll_rewind_after_pause = findViewById(R.id.ll_rewind_after_pause);
        ll_tech_log_file = findViewById(R.id.ll_tech_log_file);

        chk_MailMethod = findViewById(R.id.chk_mail_method_default);
        ll_mail_method_default = findViewById(R.id.ll_mail_method_default);
        chk_MailMethod.setChecked(Option.getMailMethod());
        ll_mail_method_default.setOnClickListener(v -> chk_MailMethod.toggle());
        chk_MailMethod.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setMailMethod(isChecked));

        chk_beep_chapter.setChecked(Option.getBeepChapter());
        ll_beep_chapter.setOnClickListener(v -> chk_beep_chapter.toggle());
        chk_beep_chapter.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setBeepChapter(isChecked));

        chk_beep_bookend.setChecked(Option.getBeepBookEnd());
        ll_beep_bookend.setOnClickListener(v -> chk_beep_bookend.toggle());
        chk_beep_bookend.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setBeepBookEnd(isChecked));

        chk_beep_autostop.setChecked(Option.getBeepAutoStop());
        ll_beep_autostop.setOnClickListener(v -> chk_beep_autostop.toggle());
        chk_beep_autostop.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setBeepAutoStop(isChecked));

        chk_split_m4b.setChecked(Option.getSplitM4b());
        ll_split_m4b.setOnClickListener(v -> chk_split_m4b.toggle());
        chk_split_m4b.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setSplitM4b(isChecked));

        chk_delete_source_file.setChecked(Option.getDeleteSourceFile());
        ll_delete_source_file.setOnClickListener(v -> chk_delete_source_file.toggle());
        chk_delete_source_file.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.option_alert_delete_source_file_title))
                        .setMessage(getString(R.string.option_alert_delete_source_file_message))
                        .setCancelable(false)
                        .setPositiveButton("ok", (dialog, which) -> Option.setDeleteSourceFile(true))
                        .setNegativeButton("cancel", (dialogInterface, i) -> chk_delete_source_file.setChecked(Option.getDeleteSourceFile()))
                        .show();
            } else {
                Option.setDeleteSourceFile(false);
            }
        });

        chk_rewind_after_pause.setChecked(Option.getRewindAfterPause());
        ll_rewind_after_pause.setOnClickListener(v -> chk_rewind_after_pause.toggle());
        chk_rewind_after_pause.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setRewindAfterPause(isChecked));

        chk_start_next_track_at_zero = findViewById(R.id.chk_start_next_track_at_zero);
        ll_start_next_track_at_zero = findViewById(R.id.ll_start_next_track_at_zero);
        chk_start_next_track_at_zero.setChecked(Option.getStartAtZeroNextTrack());
        ll_start_next_track_at_zero.setOnClickListener(v -> chk_start_next_track_at_zero.toggle());
        chk_start_next_track_at_zero.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setStartAtZeroNextTrack(isChecked));

        chk_stop_audio_if_user_closes_app = findViewById(R.id.chk_stop_audio_if_user_closes_app);
        ll_stop_audio_if_user_closes_app = findViewById(R.id.ll_stop_audio_if_user_closes_app);
        chk_stop_audio_if_user_closes_app.setChecked(Option.getStopAudioIfUserClosesApp());
        ll_stop_audio_if_user_closes_app.setOnClickListener(v -> chk_stop_audio_if_user_closes_app.toggle());
        chk_stop_audio_if_user_closes_app.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setStopAudioIfUserClosesApp(isChecked));

        chk_auto_play_on_main_player = findViewById(R.id.chk_auto_play_on_main_player);
        ll_auto_play_on_main_player = findViewById(R.id.ll_auto_play_on_main_player);
        chk_auto_play_on_main_player.setChecked(Option.getAutoPlayOnMainPlayer());
        ll_auto_play_on_main_player.setOnClickListener(v -> chk_auto_play_on_main_player.toggle());
        chk_auto_play_on_main_player.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setAutoPlayOnMainPlayer(isChecked));


        CheckBox chk_automotive_on = findViewById(R.id.chk_automotive_on);
        LinearLayout ll_automotive_on = findViewById(R.id.ll_automotive_on);
        chk_automotive_on.setChecked(Option.getAutomotiveOn());
        ll_automotive_on.setOnClickListener(v -> chk_automotive_on.toggle());
        chk_automotive_on.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setAutomotiveOn(isChecked));

        CheckBox chk_automotive_auto_resume_on_car_connect = findViewById(R.id.chk_automotive_auto_resume_on_car_connect);
        LinearLayout ll_automotive_auto_resume_on_car_connect = findViewById(R.id.ll_automotive_auto_resume_on_car_connect);
        chk_automotive_auto_resume_on_car_connect.setChecked(Option.getAutomotiveAutoResumeOnCarConnect());
        ll_automotive_auto_resume_on_car_connect.setOnClickListener(v -> chk_automotive_auto_resume_on_car_connect.toggle());
        chk_automotive_auto_resume_on_car_connect.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setAutomotiveAutoResumeOnCarConnect(isChecked));

        chk_copy_file.setChecked(Option.getCopyFile());
        ll_copy_file.setOnClickListener(v -> chk_copy_file.toggle());
        chk_copy_file.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setCopyFile(isChecked));

        chk_tech_log_file.setChecked(Option.getTechLog());
        ll_tech_log_file.setOnClickListener(v -> chk_tech_log_file.toggle());
        chk_tech_log_file.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setTechLog(isChecked));

        chk_open_with.setChecked(Option.getOpenWith());
        ll_open_with.setOnClickListener(v -> chk_open_with.toggle());
        chk_open_with.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Option.setOpenWith(isChecked);
            setOpenWithProxyEnabled(this, isChecked);  // dynamically enable/disable the component
        });

        chk_open_with_all.setChecked(Option.getOpenWith_all());
        ll_open_with_all.setOnClickListener(v -> chk_open_with_all.toggle());
        chk_open_with_all.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Option.setOpenWith_all(isChecked);
            setOpenWithProxyEnabled_all(this, isChecked);  // dynamically enable/disable the component
        });

/// APP  LANGUAGE
        appLanguageSpinner = findViewById(R.id.spinner_app_language);

        LanguageHelper.setupLanguageSpinner(
                this,
                appLanguageSpinner,
                Option.getAppLanguage(), // "system" | "en" | ...
                LanguageHelper.getAppLanguages(),
                lang -> {
                    String value = lang.twoLetterCode; // "system" or IETF language tag
                    myLogD("App language chosen: " + value);
                    Option.setAppLanguage(value);
                    LocaleHelper.applyAppLocale(value);
                    //recreate(); // activity-level refresh
                },false
        );

/// TTS  VOICE
        ttsVoiceSpinner = findViewById(R.id.spinner_voice_item);
        lastSavedTtsVoice = Option.getTtsVoice();
        ttsHandle = TtsHelper.setupTtsVoiceSpinner(
                this,
                ttsVoiceSpinner,
                lastSavedTtsVoice,
                voice -> {
                    String sel = (voice == null || voice.name == null || voice.name.isEmpty())
                            ? "system" : voice.name;

                    if (!sel.equalsIgnoreCase(lastSavedTtsVoice)) {
                        Option.setTtsVoice(sel);
                        lastSavedTtsVoice = sel;
                        myLog("TTS base voice set to: " + sel + " (" + (voice == null ? "system" : voice.displayName + " / - name = " + voice.name + ")"));
                    }
                }
        );





        try {
            if (getIntent().getBooleanExtra("CopyFileSetRed", false)) {
                TextView tv = findViewById(R.id.txtCopyFileHead);
                tv.setTextColor(Color.RED);
            }
        } catch (Exception e) {
            myLogEE(e, "not sure but you should maybe open the viewstub here....");
        }

        btnShowAdvanced = findViewById(R.id.btn_show_advanced);
        btnShowAdvanced.setOnClickListener(v -> {
            myLogI("--- USER CLICKS SHOW ADVANCED OPTIONS ---");
            toggleAdvancedOptions();
        });
        btnPodcastOptions = findViewById(R.id.btnPodcastSettings);
        btnPodcastOptions.setOnClickListener( v -> {
            myLogI("--- USER CLICKS PODCAST OPTIONS ---");
            Intent intent = new Intent(this, PodcastSettingsActivity.class);
            startActivity(intent);
        });

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN); // Avoid keyboard on opening
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

    private void saveTimeBeforeSleep() {
        String str = et_timeBeforeSleep.getText().toString().trim();
        if (str.isEmpty()) str = String.valueOf(DEFAULT_TIME_BEFORE_SLEEP);
        int i = Integer.parseInt(str);
        if (i < MINIMUM_TIME_BEFORE_SLEEP | i > MAXIMUM_TIME_BEFORE_SLEEP) {
            i = DEFAULT_TIME_BEFORE_SLEEP;
            myLongToast(getString(R.string.option_timeBeforeSleep_outOfBound));
        }
        Option.setTimeBeforeSleep(i);
    }

    private void saveForwardSeconds() {
        String str = et_ForwardSeconds.getText().toString().trim();
        if (str.isEmpty()) str = String.valueOf(DEFAULT_FORWARD_SECONDS);
        int i = Integer.parseInt(str);
        if (i < MINIMUM_FORWARD_SECONDS | i > MAXIMUM_FORWARD_SECONDS) {
            i = DEFAULT_FORWARD_SECONDS;
            myLongToast("out of bounds - must be between 1 and 300");
        }
        Option.set_ForwardSeconds(i);
    }

    @Override
    protected void onDestroy() {
        try { if (ttsHandle != null) ttsHandle.close(); } catch (Exception ignored) {}
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


    private void requestRecordAudioPermission() {
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
                        myLog("RecordAudio Permission Granted");
                    }

                    @Override
                    public void onPermissionsDenied() {
                        myLog("RecordAudio Permission Granted");
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
            myLogEE(e,"openAppSettingsOnPhone()");
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
    private void changeBaseTheme(String new_base_theme) {
        myLog("new Base theme is [" + new_base_theme + "]" );
        Option.setTheme(new_base_theme);
        triggerFolderListReloadOnClose();
        recreate();
    }

    private void triggerFolderListReloadOnClose() {
        this.getSharedPreferences(Option.SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).edit().putBoolean("ACTIVITY_OPTION_HAS_RESULT", true).apply(); //trick to reload MainActivity
    }

    @Override
    public void finish() { //needed because of recreate()
        if (this.getSharedPreferences(Option.SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE).getBoolean("ACTIVITY_OPTION_HAS_RESULT", false)) { //trick to reload MainActivity
            setResult(Activity.RESULT_OK);
        }
        super.finish();
    }

    //// Saving scroll position where reloading activity (after applying theme color change)
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("advanced_visible", areAdvancedOptionsVisible);
        outState.putInt("scroll_position", scrollView.getScrollY());
    }
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        final int scrollPosition = savedInstanceState.getInt("scroll_position");
        areAdvancedOptionsVisible = savedInstanceState.getBoolean("advanced_visible", false);
        if (areAdvancedOptionsVisible) {
            advancedOptionsLayout.setVisibility(View.VISIBLE);
            btnShowAdvanced.setText(getString(R.string.option_hide_advanced_options));
        } else {
            advancedOptionsLayout.setVisibility(View.GONE);
            btnShowAdvanced.setText(getString(R.string.option_show_advanced_options));
        }
        scrollView.post(() -> scrollView.scrollTo(0, scrollPosition));
    }

    @Override
    protected void onResume() {
        super.onResume();
        setVisualizerPermissionText();
    }

    private void toggleAdvancedOptions() {
        if (advancedOptionsLayout.getVisibility() == View.GONE) {
            myLogD("toggleAdvancedOptions -> visible");
            advancedOptionsLayout.setVisibility(View.VISIBLE);
            btnShowAdvanced.setText(getString(R.string.option_hide_advanced_options));

            // Scroll to it
            btnShowAdvanced.post(() -> {
                int y = btnShowAdvanced.getTop();
                scrollView.smoothScrollTo(0, y);
            });

            areAdvancedOptionsVisible = true;
        } else {
            myLogD("toggleAdvancedOptions -> gone");
            advancedOptionsLayout.setVisibility(View.GONE);
            btnShowAdvanced.setText(getString(R.string.option_show_advanced_options));
            areAdvancedOptionsVisible = false;
        }
    }

    private void showNightModeChooser() {
        final String current = Option.getNightMode();
        final CharSequence[] items = new CharSequence[] {
                getString(R.string.option_night_mode_follow_system),
                getString(R.string.option_night_mode_light),
                getString(R.string.option_night_mode_dark)
        };
        int checked = (current.equals("LIGHT")) ? 1 : (current.equals("DARK") ? 2 : 0);

        new AlertDialog.Builder(this)
                .setTitle(R.string.option_night_mode_dialog_title)
                .setSingleChoiceItems(items, checked, (dlg, which) -> {
                    String chosen = (which == 1) ? "LIGHT"
                            : (which == 2) ? "DARK"
                            : "SYSTEM";

                    if (!chosen.equals(current)) {
                        Option.setNightMode(chosen);
                        Option.applyNightMode();                 // apply globally
                        btnNightMode.setText(chosen);  // update label

                        // Recreate like you do for color changes so MainActivity refreshes if needed
                        getSharedPreferences(Option.SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE)
                                .edit().putBoolean("ACTIVITY_OPTION_HAS_RESULT", true).apply();

                        recreate(); // will pick up values-night etc.
                    }
                    dlg.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private List<String> toLabels(List<FontChoice> list) {
        List<String> labels = new ArrayList<>();
        for (FontChoice f : list) labels.add(f.label);
        return labels;
    }

    private int indexOfKey(List<FontChoice> list, String key) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).key.equalsIgnoreCase(key)) return i;
        return -1;
    }

    private void applyPreview(String familyKey, float sizeSp) {
        try {
            tvPreview.setTypeface(android.graphics.Typeface.create(familyKey, android.graphics.Typeface.NORMAL));
            tvPreview.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp);
        } catch (Throwable ignored) {}
    }


}
