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

public class RepositoriesSettingsFragment extends LoggingFragment {

    private EditText etLibrivoxNbResults;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_settings_repositories, container, false);

        boolean showLocalTitle = true;
        Bundle args = getArguments();
        if (args != null)
            showLocalTitle = args.getBoolean("ARG_SHOW_LOCAL_TITLE", true);
        View titleContainer = root.findViewById(R.id.ll_title);
        if (titleContainer != null) {
            titleContainer.setVisibility(showLocalTitle ? View.VISIBLE : View.GONE);
        }

        etLibrivoxNbResults = root.findViewById(R.id.et_librivox_api_nb_results);
        etLibrivoxNbResults.setText(String.valueOf(Option.getLibrivoxApiNbResults()));

        CheckBox chk_gutenberg_use_cloudfare = root.findViewById(R.id.chk_option_gutenberg_use_cloudfare);
        LinearLayout ll_gutenberg_use_cloudfare = root.findViewById(R.id.ll_option_gutenberg_use_cloudfare);
        chk_gutenberg_use_cloudfare.setChecked(Option.getGutenbergUseCloudflare());
        ll_gutenberg_use_cloudfare.setOnClickListener(v -> chk_gutenberg_use_cloudfare.toggle());
        chk_gutenberg_use_cloudfare
                .setOnCheckedChangeListener((buttonView, isChecked) -> Option.setGutenbergUseCloudflare(isChecked));

        return root;
    }

    @Override
    public void onPause() {
        super.onPause();
        saveEditTextValues();
    }

    private void saveEditTextValues() {
        if (etLibrivoxNbResults != null) {
            final int value = Option.clampInt(this.getContext(),
                    etLibrivoxNbResults,
                    Var.LIBRIVOX_API_MIN_RESULTS,
                    Var.LIBRIVOX_API_MAX_RESULTS,
                    Option.DEFAULT_LIBRIVOX_API_NB_RESULTS,
                    getString(R.string.librivox));
            Executors.newSingleThreadExecutor().execute(() -> {
                Option.setLibrivoxApiNbResults(value);
            });
        }
    }
}
