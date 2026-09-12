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
import com.driot.bookplayer.global.Pref;
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
                    String current = Option.getAppLanguage();
                    myLogD("App language chosen: value=[" + value + "] current=[" + current + "]");
                    Option.setAppLanguage(value);
                    myLogD("App language persisted, re-read=[" + Option.getAppLanguage() + "]");
                    LocaleHelper.applyAppLocale(value);
                    // Only recreate when language actually changed; avoids recreate on initial spinner set.
                    // Force recreate so UI updates on Oppo/Samsung Android 9–12 where
                    // setApplicationLocales alone often does not trigger recreate.
                    if (!value.equals(current) && getActivity() != null) {
                        // Same signal DesignSettingsFragment uses for theme changes: without this,
                        // only this Settings screen itself picks up the new language (it recreates
                        // itself directly, below) - MainActivity and the rest of the back stack
                        // never get told to refresh, so on API <33 devices where the OS doesn't
                        // auto-recreate the whole task for a per-app locale change (see
                        // LocaleHelper.applyAppLocale - this is exactly the Oppo/older-Samsung
                        // case), navigating back out of Settings lands back in the old language.
                        Pref.setNeedsRecreate(true);
                        myLogD("App language changed, calling activity.recreate()");
                        getActivity().recreate();
                    } else {
                        myLogD("App language unchanged or no activity, skipping recreate()");
                    }
                }, false);

        return root;
    }
}
