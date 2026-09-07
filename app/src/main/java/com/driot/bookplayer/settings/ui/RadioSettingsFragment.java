package com.driot.bookplayer.settings.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.utils.log.LoggingFragment;

import java.util.concurrent.Executors;

public class RadioSettingsFragment extends LoggingFragment {

    private EditText etRadioNbResults;
    private EditText et_option_radio_sleep_value;
    private CheckBox chk_option_radio_sleep_copy;
    private CheckBox chk_option_radio_remove_duplicates;
    private CheckBox chk_option_radio_remove_dubious;
    private LinearLayout ll_option_radio_sleep_value;
    private LinearLayout ll_option_radio_remove_duplicates;
    private LinearLayout ll_option_radio_remove_dubious;


    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_settings_radio, container, false);

        boolean showLocalTitle = true;
        Bundle args = getArguments();
        if (args != null) showLocalTitle = args.getBoolean("ARG_SHOW_LOCAL_TITLE", true);
        View titleContainer = root.findViewById(R.id.ll_title);
        if (titleContainer != null) {
            titleContainer.setVisibility(showLocalTitle ? View.VISIBLE : View.GONE);
        }

        etRadioNbResults = root.findViewById(R.id.et_api_nb_results);
        etRadioNbResults.setText(String.valueOf(Option.getRadioApiNbResults()));

        CheckBox chk_radio_renew_url = root.findViewById(R.id.chk_radio_renew_url);
        LinearLayout ll_radio_renew_url = root.findViewById(R.id.ll_radio_renew_url);
        chk_radio_renew_url.setChecked(Option.getRadioRenewUrl());
        ll_radio_renew_url.setOnClickListener(v -> chk_radio_renew_url.toggle());
        chk_radio_renew_url.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setRadioRenewUrl(isChecked));

        CheckBox chk_radio_use_cloudfare = root.findViewById(R.id.chk_radio_use_cloudfare);
        LinearLayout ll_radio_use_cloudfare = root.findViewById(R.id.ll_radio_use_cloudfare);
        chk_radio_use_cloudfare.setChecked(Option.getRadioUseCloudflare());
        ll_radio_use_cloudfare.setOnClickListener(v -> chk_radio_use_cloudfare.toggle());
        chk_radio_use_cloudfare.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setRadioUseCloudflare(isChecked));

        ll_option_radio_remove_duplicates = root.findViewById(R.id.ll_option_radio_remove_duplicates);
        chk_option_radio_remove_duplicates = root.findViewById(R.id.chk_option_radio_remove_duplicates);
        chk_option_radio_remove_duplicates.setChecked(Option.getRadioRemoveSpamStations());
        ll_option_radio_remove_duplicates.setOnClickListener(v -> chk_option_radio_remove_duplicates.toggle());
        chk_option_radio_remove_duplicates.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setRadioRemoveSpamStations(isChecked));

        ll_option_radio_remove_dubious = root.findViewById(R.id.ll_option_radio_remove_dubious);
        chk_option_radio_remove_dubious = root.findViewById(R.id.chk_option_radio_remove_dubious);
        chk_option_radio_remove_dubious.setChecked(Option.getRadioRemoveDubiousStations());
        ll_option_radio_remove_dubious.setOnClickListener(v -> chk_option_radio_remove_dubious.toggle());
        chk_option_radio_remove_dubious.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setRadioRemoveDubiousStations(isChecked));

        com.google.android.material.button.MaterialButtonToggleGroup groupRadioLandingScreen =
                root.findViewById(R.id.groupRadioLandingScreen);
        int checkedId;
        switch (Option.getRadioLandingScreen()) {
            case Option.RADIO_LANDING_FAVORITES:
                checkedId = R.id.btnRadioLandingFavorites;
                break;
            case Option.RADIO_LANDING_HISTORY:
                checkedId = R.id.btnRadioLandingHistory;
                break;
            default:
                checkedId = R.id.btnRadioLandingSearch;
                break;
        }
        groupRadioLandingScreen.check(checkedId);
        groupRadioLandingScreen.addOnButtonCheckedListener((group, checkedButtonId, isChecked) -> {
            if (!isChecked)
                return;
            if (checkedButtonId == R.id.btnRadioLandingFavorites) {
                Option.setRadioLandingScreen(Option.RADIO_LANDING_FAVORITES);
            } else if (checkedButtonId == R.id.btnRadioLandingHistory) {
                Option.setRadioLandingScreen(Option.RADIO_LANDING_HISTORY);
            } else {
                Option.setRadioLandingScreen(Option.RADIO_LANDING_SEARCH);
            }
        });

        LinearLayout ll_option_radio_recording_enabled = root.findViewById(R.id.ll_option_radio_recording_enabled);
        CheckBox chk_option_radio_recording_enabled = root.findViewById(R.id.chk_option_radio_recording_enabled);
        chk_option_radio_recording_enabled.setChecked(Option.getRadioRecordingEnabled());
        ll_option_radio_recording_enabled.setOnClickListener(v -> chk_option_radio_recording_enabled.toggle());
        chk_option_radio_recording_enabled.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setRadioRecordingEnabled(isChecked));

        LinearLayout ll_option_radio_recording_as_music = root.findViewById(R.id.ll_option_radio_recording_as_music);
        CheckBox chk_option_radio_recording_as_music = root.findViewById(R.id.chk_option_radio_recording_as_music);
        chk_option_radio_recording_as_music.setChecked(Option.getRadioRecordingAsMusic());
        ll_option_radio_recording_as_music.setOnClickListener(v -> chk_option_radio_recording_as_music.toggle());
        chk_option_radio_recording_as_music.setOnCheckedChangeListener((buttonView, isChecked) -> Option.setRadioRecordingAsMusic(isChecked));

        chk_option_radio_sleep_copy = root.findViewById(R.id.chk_option_radio_sleep_copy);
        LinearLayout ll_option_radio_sleep_copy = root.findViewById(R.id.ll_option_radio_sleep_copy);
        chk_option_radio_sleep_copy.setChecked(Option.getRadioSleepCopy());
        ll_option_radio_sleep_copy.setOnClickListener(v -> chk_option_radio_sleep_copy.toggle());
        chk_option_radio_sleep_copy.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Option.setRadioSleepCopy(isChecked);
            rebuildOptionDisplay();
        });

        ll_option_radio_sleep_value = root.findViewById(R.id.ll_option_radio_sleep_value);
        et_option_radio_sleep_value = root.findViewById(R.id.et_option_radio_sleep_value);

        rebuildOptionDisplay();
        return root;
    }

    private void rebuildOptionDisplay() {
        if (chk_option_radio_sleep_copy.isChecked()) {
            et_option_radio_sleep_value.setText(String.valueOf(Option.getTimeBeforeSleep()));
            et_option_radio_sleep_value.setEnabled(false);
            ll_option_radio_sleep_value.setAlpha(0.5f);
            myLogD("rebuildOptionDisplay : enabled false");
        } else {
            et_option_radio_sleep_value.setText(String.valueOf(Option.getTimeBeforeSleepRadio()));
            et_option_radio_sleep_value.setEnabled(true);
            ll_option_radio_sleep_value.setAlpha(1f);
            myLogD("rebuildOptionDisplay : enabled true");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        saveEditTextValues();
    }

    private void saveEditTextValues() {
        if (etRadioNbResults != null) {
            final int value = Option.clampInt(this.getContext(),
                    etRadioNbResults,
                    Var.LIBRIVOX_API_MIN_RESULTS,
                    Var.LIBRIVOX_API_MAX_RESULTS,
                    Option.DEFAULT_LIBRIVOX_API_NB_RESULTS,
                    getString(R.string.radio)
            );
            final int sleep_value = Option.clampInt(this.getContext(),
                    et_option_radio_sleep_value,
                    Option.MIN_TIME_BEFORE_SLEEP,
                    Option.MAX_TIME_BEFORE_SLEEP,
                    Option.DEFAULT_TIME_BEFORE_SLEEP,
                    getString(R.string.radio)
            );
            Executors.newSingleThreadExecutor().execute(() -> {
                Option.setRadioApiNbResults(value);
                Option.setTimeBeforeSleepRadio(sleep_value);
            });
        }
    }

}
