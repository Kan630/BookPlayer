package com.driot.bookplayer.settings.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.LanguageHelper;
import com.driot.bookplayer.helpers.LocaleHelper;
import com.driot.bookplayer.utils.log.LoggingFragment;

public class LanguageSettingsFragment extends LoggingFragment {

    private Spinner appLanguageSpinner;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_settings_language, container, false);

        boolean showLocalTitle = true;
        Bundle args = getArguments();
        if (args != null) showLocalTitle = args.getBoolean("ARG_SHOW_LOCAL_TITLE", true);
        View titleContainer = root.findViewById(R.id.ll_title);
        if (titleContainer != null) {
            titleContainer.setVisibility(showLocalTitle ? View.VISIBLE : View.GONE);
        }

        appLanguageSpinner = root.findViewById(R.id.spinner_app_language);

        LanguageHelper.setupLanguageSpinner(
                requireContext(),
                appLanguageSpinner,
                Option.getAppLanguage(),
                LanguageHelper.getAppLanguages(),
                lang -> {
                    String value = lang.twoLetterCode;
                    myLogD("App language chosen: " + value);
                    Option.setAppLanguage(value);
                    LocaleHelper.applyAppLocale(value);
                }, false);

        return root;
    }
}
