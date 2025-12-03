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
    private LinearLayout ll_option_radio_sleep_value;

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
