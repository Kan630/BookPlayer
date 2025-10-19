package com.driot.bookplayer.settings.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.LanguageHelper;
import com.driot.bookplayer.helpers.LocaleHelper;
import com.driot.bookplayer.objects.LanguageItem;
import com.driot.bookplayer.utils.log.LoggingFragment;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.List;

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
