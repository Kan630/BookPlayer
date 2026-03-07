package com.driot.bookplayer.settings.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.utils.log.LoggingFragment;
import com.google.android.material.checkbox.MaterialCheckBox;

import android.widget.LinearLayout;

public class AutomotiveSettingsFragment extends LoggingFragment {

    private LinearLayout llAutomotiveOn;
    private MaterialCheckBox chkAutomotiveOn;

    private LinearLayout llLetCarAutoplay;
    private MaterialCheckBox chkLetCarAutoplay;

    private LinearLayout llAutoResumeOnConnect;
    private MaterialCheckBox chkAutoResumeOnConnect;

    private LinearLayout llShowRadios;
    private MaterialCheckBox chkShowRadios;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_settings_automotive, container, false);

        // Hide local title when embedded
        boolean showLocalTitle = true;
        Bundle args = getArguments();
        if (args != null)
            showLocalTitle = args.getBoolean("ARG_SHOW_LOCAL_TITLE", true);
        View titleContainer = root.findViewById(R.id.ll_title);
        if (titleContainer != null)
            titleContainer.setVisibility(showLocalTitle ? View.VISIBLE : View.GONE);

        // Bind views
        llAutomotiveOn = root.findViewById(R.id.ll_automotive_on);
        chkAutomotiveOn = root.findViewById(R.id.chk_automotive_on);

        llLetCarAutoplay = root.findViewById(R.id.ll_automotive_let_car_autoplay);
        chkLetCarAutoplay = root.findViewById(R.id.chk_automotive_let_car_autoplay);

        llAutoResumeOnConnect = root.findViewById(R.id.ll_automotive_auto_resume_on_car_connect);
        chkAutoResumeOnConnect = root.findViewById(R.id.chk_automotive_auto_resume_on_car_connect);

        llShowRadios = root.findViewById(R.id.ll_automotive_show_radios);
        chkShowRadios = root.findViewById(R.id.chk_automotive_show_radios);

        // Initial states
        chkAutomotiveOn.setChecked(Option.getAutomotiveOn());
        chkLetCarAutoplay.setChecked(Option.getAutomotiveLetCarAutoplay());
        chkAutoResumeOnConnect.setChecked(Option.getAutomotiveAutoResumeOnCarConnect());
        chkShowRadios.setChecked(Option.getAutomotiveShowRadios());

        // Click-to-toggle rows
        llAutomotiveOn.setOnClickListener(v -> chkAutomotiveOn.toggle());
        llLetCarAutoplay.setOnClickListener(v -> chkLetCarAutoplay.toggle());
        llAutoResumeOnConnect.setOnClickListener(v -> chkAutoResumeOnConnect.toggle());
        llShowRadios.setOnClickListener(v -> chkShowRadios.toggle());

        // Listeners
        chkAutomotiveOn.setOnCheckedChangeListener((button, checked) -> {
            Option.setAutomotiveOn(checked);
            applyEnableState();
        });

        chkLetCarAutoplay.setOnCheckedChangeListener((button, checked) -> {
            Option.setAutomotiveLetCarAutoplay(checked);
            applyChildEnableState();
        });

        chkAutoResumeOnConnect.setOnCheckedChangeListener((button, checked) -> {
            Option.setAutomotiveAutoResumeOnCarConnect(checked);
        });

        chkShowRadios.setOnCheckedChangeListener((button, checked) -> {
            Option.setAutomotiveShowRadios(checked);
        });

        // Apply enable/disable rules once
        applyEnableState();
        applyChildEnableState();

        return root;
    }

    // Enable/disable the lower rows when Automotive is ON/OFF
    private void applyEnableState() {
        boolean automotiveOn = chkAutomotiveOn.isChecked();
        setRowEnabled(llLetCarAutoplay, chkLetCarAutoplay, automotiveOn);
        applyChildEnableState();
    }

    // Enable/disable the child row based on parent “let car autoplay”
    private void applyChildEnableState() {
        boolean automotiveOn = chkAutomotiveOn.isChecked();
        boolean parentOn = chkLetCarAutoplay.isChecked();

        if (parentOn && automotiveOn) {
            // Force checked and disable interaction
            chkAutoResumeOnConnect.setChecked(true);
            setRowEnabled(llAutoResumeOnConnect, chkAutoResumeOnConnect, false);
        } else {
            // Re-enable for user control
            setRowEnabled(llAutoResumeOnConnect, chkAutoResumeOnConnect, automotiveOn);
        }
    }

    private void setRowEnabled(View row, View control, boolean enabled) {
        row.setEnabled(enabled);
        control.setEnabled(enabled);
        row.setAlpha(enabled ? 1f : 0.5f);
    }

}
