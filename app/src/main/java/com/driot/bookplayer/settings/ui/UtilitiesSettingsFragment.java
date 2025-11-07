package com.driot.bookplayer.settings.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.LanguageHelper;
import com.driot.bookplayer.helpers.LocaleHelper;
import com.driot.bookplayer.imports.ImportHelper;
import com.driot.bookplayer.utils.log.LoggingFragment;
import com.google.android.material.checkbox.MaterialCheckBox;

public class UtilitiesSettingsFragment extends LoggingFragment {

    private Spinner appLanguageSpinner;
    private MaterialCheckBox chkTechLog, chkMailMethod;
    private LinearLayout llTechLog, llMailMethod;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_utilities_settings, container, false);

        // Hide local title when embedded
        boolean showLocalTitle = true;
        Bundle args = getArguments();
        if (args != null) showLocalTitle = args.getBoolean("ARG_SHOW_LOCAL_TITLE", true);
        View titleContainer = root.findViewById(R.id.ll_title);
        if (titleContainer != null)
            titleContainer.setVisibility(showLocalTitle ? View.VISIBLE : View.GONE);

        chkTechLog = root.findViewById(R.id.chk_tech_log_file);
        llTechLog = root.findViewById(R.id.ll_tech_log_file);
        chkMailMethod = root.findViewById(R.id.chk_mail_method_default);
        llMailMethod = root.findViewById(R.id.ll_mail_method_default);

        // Technical log
        chkTechLog.setChecked(Option.getTechLog());
        llTechLog.setOnClickListener(v -> chkTechLog.toggle());
        chkTechLog.setOnCheckedChangeListener((b, checked) -> Option.setTechLog(checked));

        // Mail method
        chkMailMethod.setChecked(Option.getMailMethod());
        llMailMethod.setOnClickListener(v -> chkMailMethod.toggle());
        chkMailMethod.setOnCheckedChangeListener((b, checked) -> Option.setMailMethod(checked));

        root.findViewById(R.id.btn_reset_settings_values_to_default).setOnClickListener(v -> {
            myLogI("--- user clicks RESET SETTINGS to DEFAULT ---");

            Activity act = requireActivity();

            // 1️⃣  Get the launch intent *before* finishing the activity
            Intent restartIntent = act.getPackageManager()
                    .getLaunchIntentForPackage(act.getPackageName());
            if (restartIntent == null) return; // should never happen
            restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            // 2️⃣  Run the deletion *after* the current Activity is closed,
            //      to avoid any onPause()/onDestroy() code rewriting prefs.
            act.finish(); // close current activity window

            // Give the system a short beat to finish (optional but avoids race conditions)
            act.getWindow().getDecorView().postDelayed(() -> {
                Option.resetToDefaults(requireContext().getApplicationContext());
                startActivity(restartIntent); // reopen app fresh
            }, 150);
        });

        root.findViewById(R.id.btn_reset_settings_values_for_power_user).setOnClickListener(v -> {
            myLogI("--- user clicks RESET SETTINGS for POWER USER ---");

            Activity act = requireActivity();

            // 1️⃣  Get the launch intent *before* finishing the activity
            Intent restartIntent = act.getPackageManager()
                    .getLaunchIntentForPackage(act.getPackageName());
            if (restartIntent == null) return; // should never happen
            restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            // 2️⃣  Run the deletion *after* the current Activity is closed,
            //      to avoid any onPause()/onDestroy() code rewriting prefs.
            act.finish(); // close current activity window

            // Give the system a short beat to finish (optional but avoids race conditions)
            act.getWindow().getDecorView().postDelayed(() -> {
                Option.resetToDefaults(requireContext().getApplicationContext());
                Option.setCopyFile(false);
                Option.setOpenWith_all(true);
                Option.setStopAudioIfUserClosesApp(false);
                Option.setOpenPlayActivity(false);
                startActivity(restartIntent); // reopen app fresh
            }, 150);
        });

        root.findViewById(R.id.btn_reset_app).setOnClickListener(v -> {
            myLogI("--- user clicks RESET APP ---");
            ImportHelper.cancelCurrentImport(requireContext().getApplicationContext());
            ImportHelper.cancelAll_in_DB(requireContext().getApplicationContext());
            myToast("App Reset Done");
        });

        appLanguageSpinner = root.findViewById(R.id.spinner_app_language);

        LanguageHelper.setupLanguageSpinner(
                this.getContext(),
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

        return root;
    }

}
