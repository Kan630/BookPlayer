package com.driot.bookplayer.settings.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.utils.log.LoggingFragment;
import com.google.android.material.slider.Slider;

import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class NetworkSettingsFragment extends LoggingFragment {

    private Spinner spinnerUser;
    private Spinner spinnerAuto;
    private Slider sliderMinFreeStorage;
    private TextView tvMinFreeStorageValue;
    private Slider sliderMinFreeStorageSdCard;
    private TextView tvMinFreeStorageSdCardValue;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_settings_network, container, false);

        // Hide local title if embedded
        boolean showLocalTitle = true;
        Bundle args = getArguments();
        if (args != null) showLocalTitle = args.getBoolean("ARG_SHOW_LOCAL_TITLE", true);
        View titleContainer = root.findViewById(R.id.ll_title);
        if (titleContainer != null) titleContainer.setVisibility(showLocalTitle ? View.VISIBLE : View.GONE);

        spinnerUser = root.findViewById(R.id.spinner_download_user);
        spinnerAuto = root.findViewById(R.id.spinner_download_auto);
        sliderMinFreeStorage = root.findViewById(R.id.slider_min_free_storage_mb);
        tvMinFreeStorageValue = root.findViewById(R.id.tv_min_free_storage_mb_value);
        sliderMinFreeStorageSdCard = root.findViewById(R.id.slider_min_free_storage_sdcard_mb);
        tvMinFreeStorageSdCardValue = root.findViewById(R.id.tv_min_free_storage_sdcard_mb_value);

        setupSpinners();
        setupMinFreeStorageSliders(root);

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

    /** The SD-card slider only appears (and only needs wiring) when a card is actually present -
     *  the two thresholds are independent since internal and SD capacities can differ wildly. */
    private void setupMinFreeStorageSliders(View root) {
        setupMinFreeStorageSlider(sliderMinFreeStorage, tvMinFreeStorageValue,
                Option::getMinFreeStorageMbForDownload, Option::setMinFreeStorageMbForDownload);

        boolean hasSdCard = StorageHelper.isExternalSDCardAvailable(requireContext());
        View llSdCardSection = root.findViewById(R.id.ll_min_free_storage_sdcard);
        TextView tvTitle = root.findViewById(R.id.tv_min_free_storage_title);
        if (hasSdCard) {
            if (tvTitle != null)
                tvTitle.setText(R.string.option_min_free_storage_title_internal);
            if (llSdCardSection != null)
                llSdCardSection.setVisibility(View.VISIBLE);
            setupMinFreeStorageSlider(sliderMinFreeStorageSdCard, tvMinFreeStorageSdCardValue,
                    Option::getMinFreeStorageMbForSdCard, Option::setMinFreeStorageMbForSdCard);
        }
    }

    private void setupMinFreeStorageSlider(Slider slider, TextView valueLabel,
            IntSupplier getter, IntConsumer setter) {
        if (slider == null)
            return;

        int currentMb = getter.getAsInt();
        // Defensive: Slider#setValue() throws if the value is outside [valueFrom, valueTo] or
        // doesn't land exactly on the stepSize grid - clamp/snap here too, not just in Option.
        currentMb = Math.max((int) slider.getValueFrom(), Math.min((int) slider.getValueTo(), currentMb));
        float step = slider.getStepSize();
        if (step > 0) {
            currentMb = (int) (slider.getValueFrom()
                    + Math.round((currentMb - slider.getValueFrom()) / step) * step);
        }
        slider.setValue((float) currentMb);
        if (valueLabel != null)
            valueLabel.setText(formatMb(currentMb));

        slider.addOnChangeListener((s, value, fromUser) -> {
            int mb = (int) value;
            if (valueLabel != null)
                valueLabel.setText(formatMb(mb));
            setter.accept(mb);
        });
    }

    private static String formatMb(int mb) {
        return mb >= 1024
                ? String.format(Locale.getDefault(), "%.1f GB", mb / 1024f)
                : mb + " MB";
    }
}
