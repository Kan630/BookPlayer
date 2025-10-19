package com.driot.bookplayer.settings.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.utils.log.LoggingFragment;

public class NetworkSettingsFragment extends LoggingFragment {

    private Spinner spinnerUser;
    private Spinner spinnerAuto;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_network_settings, container, false);

        // Hide local title if embedded
        boolean showLocalTitle = true;
        Bundle args = getArguments();
        if (args != null) showLocalTitle = args.getBoolean("ARG_SHOW_LOCAL_TITLE", true);
        View titleContainer = root.findViewById(R.id.ll_title);
        if (titleContainer != null) titleContainer.setVisibility(showLocalTitle ? View.VISIBLE : View.GONE);

        spinnerUser = root.findViewById(R.id.spinner_download_user);
        spinnerAuto = root.findViewById(R.id.spinner_download_auto);

        setupSpinners();

        return root;
    }

    private void setupSpinners() {
        // Same labels you used before
        String[] options = new String[] {
                getString(R.string.download_any),
                getString(R.string.download_not_roaming),
                getString(R.string.download_bis_unmetered),
        };

        // Manual download spinner
        ArrayAdapter<String> manualAdapter =
                new ArrayAdapter<>(requireContext(), R.layout.spinner_item, options);
        manualAdapter.setDropDownViewResource(R.layout.spinner_item);
        spinnerUser.setAdapter(manualAdapter);
        spinnerUser.setSelection(Option.getNetworkPolicyManualDownload().ordinal(), false);
        spinnerUser.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                Option.setNetworkPolicyManualDownload(NetworkHelper.NetworkPolicyManual.values()[pos]);
                myLog("Option manual download : " + Option.getNetworkPolicyManualDownload());
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Auto download spinner
        ArrayAdapter<String> autoAdapter =
                new ArrayAdapter<>(requireContext(), R.layout.spinner_item, options);
        autoAdapter.setDropDownViewResource(R.layout.spinner_item);
        spinnerAuto.setAdapter(autoAdapter);
        spinnerAuto.setSelection(Option.getNetworkPolicyAutoDownload().ordinal(), false);
        spinnerAuto.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                Option.setNetworkPolicyAutoDownload(NetworkHelper.NetworkPolicyAuto.values()[pos]);
                myLog("Option auto download : " + Option.getNetworkPolicyAutoDownload());
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }
}
