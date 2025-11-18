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




        return root;
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
            Executors.newSingleThreadExecutor().execute(() -> {
                Option.setRadioApiNbResults(value);
            });
        }
    }

}
