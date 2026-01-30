package com.driot.bookplayer.settings.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.utils.log.LoggingFragment;
import com.google.android.material.checkbox.MaterialCheckBox;

public class MassiveImportSettingsFragment extends LoggingFragment {

    private MaterialCheckBox chkDisplayStorageBar;
    private LinearLayout llDisplayStorageBar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_settings_massive_import, container, false);

        boolean showLocalTitle = true;
        Bundle args = getArguments();
        if (args != null) showLocalTitle = args.getBoolean("ARG_SHOW_LOCAL_TITLE", true);
        View titleContainer = root.findViewById(R.id.ll_title);
        if (titleContainer != null) {
            titleContainer.setVisibility(showLocalTitle ? View.VISIBLE : View.GONE);
        }

        chkDisplayStorageBar = root.findViewById(R.id.chk_display_storage_bar);
        llDisplayStorageBar = root.findViewById(R.id.ll_display_storage_bar);
        chkDisplayStorageBar.setChecked(Option.getMassImportDisplayStorageBar());
        llDisplayStorageBar.setOnClickListener(v -> chkDisplayStorageBar.toggle());
        chkDisplayStorageBar.setOnCheckedChangeListener((buttonView, isChecked) ->
                Option.setMassImportDisplayStorageBar(isChecked));

        return root;
    }
}
