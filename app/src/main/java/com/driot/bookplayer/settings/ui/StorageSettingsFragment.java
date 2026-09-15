package com.driot.bookplayer.settings.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.helpers.StorageInfoCacheHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingFragment;
import com.driot.bookplayer.widgets.StorageBarView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.slider.Slider;

import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class StorageSettingsFragment extends LoggingFragment {

    private Slider sliderMinFreeStorage;
    private TextView tvMinFreeStorageValue;
    private Slider sliderMinFreeStorageSdCard;
    private TextView tvMinFreeStorageSdCardValue;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_settings_storage, container, false);

        // Hide local title if embedded
        boolean showLocalTitle = true;
        Bundle args = getArguments();
        if (args != null) showLocalTitle = args.getBoolean("ARG_SHOW_LOCAL_TITLE", true);
        View titleContainer = root.findViewById(R.id.ll_title);
        if (titleContainer != null) titleContainer.setVisibility(showLocalTitle ? View.VISIBLE : View.GONE);

        sliderMinFreeStorage = root.findViewById(R.id.slider_min_free_storage_mb);
        tvMinFreeStorageValue = root.findViewById(R.id.tv_min_free_storage_mb_value);
        sliderMinFreeStorageSdCard = root.findViewById(R.id.slider_min_free_storage_sdcard_mb);
        tvMinFreeStorageSdCardValue = root.findViewById(R.id.tv_min_free_storage_sdcard_mb_value);

        boolean hasSdCard = StorageHelper.isExternalSDCardAvailable(requireContext());
        setupUseSdCardCheckbox(root, hasSdCard);
        setupMinFreeStorageSliders(root, hasSdCard);

        return root;
    }

    /** Moved here from Import settings - "which volume to default to" belongs with "how much
     *  space to keep free on it". */
    private void setupUseSdCardCheckbox(View root, boolean hasSdCard) {
        View llContainerSdCard = root.findViewById(R.id.ll_container_sd_card);
        if (!hasSdCard) {
            if (llContainerSdCard != null) llContainerSdCard.setVisibility(View.GONE);
            return;
        }
        if (llContainerSdCard != null) llContainerSdCard.setVisibility(View.VISIBLE);

        MaterialCheckBox chkUseSdCard = root.findViewById(R.id.chk_use_sd_card);
        LinearLayout llUseSdCard = root.findViewById(R.id.ll_use_sd_card);
        if (chkUseSdCard == null || llUseSdCard == null)
            return;
        chkUseSdCard.setChecked(Option.getUseSdCard());
        llUseSdCard.setOnClickListener(v -> chkUseSdCard.toggle());
        chkUseSdCard.setOnCheckedChangeListener((button, checked) -> Option.setUseSdCard(checked));
    }

    /** The SD-card slider only appears (and only needs wiring) when a card is actually present -
     *  the two thresholds are independent since internal and SD capacities can differ wildly. The
     *  "Internal"/"SD card" volume labels (with total/free) only show up alongside it too - with
     *  a single volume there's nothing to disambiguate. */
    private void setupMinFreeStorageSliders(View root, boolean hasSdCard) {
        setupMinFreeStorageSlider(sliderMinFreeStorage, tvMinFreeStorageValue,
                Option::getMinFreeStorageMbForDownload, Option::setMinFreeStorageMbForDownload);
        wireStorageBar(root.findViewById(R.id.storageBarInternal), root.findViewById(R.id.ll_legend_internal),
                sliderMinFreeStorage,
                StorageInfoCacheHelper.getCachedInternalTotal(),
                StorageInfoCacheHelper.getCachedInternalUsedByOthers(),
                StorageInfoCacheHelper.getCachedInternalUsedByBookPlayer(),
                StorageInfoCacheHelper.getCachedInternalApp(),
                StorageInfoCacheHelper.getCachedInternalLinkedAudios());

        View llSdCardSection = root.findViewById(R.id.ll_min_free_storage_sdcard);
        TextView tvVolumeInternal = root.findViewById(R.id.tv_storage_volume_internal);
        if (hasSdCard) {
            if (tvVolumeInternal != null) {
                tvVolumeInternal.setText(formatVolumeInfo(
                        R.string.storage_volume_internal_label,
                        StorageHelper.getTotaLInternalMemorySize(),
                        StorageHelper.getAvailableInternalMemorySize()));
                tvVolumeInternal.setVisibility(View.VISIBLE);
            }
            if (llSdCardSection != null)
                llSdCardSection.setVisibility(View.VISIBLE);
            long sdTotal = StorageHelper.getTotalRemovableSDCardSize(requireContext());
            long sdAvailable = StorageHelper.getAvailableRemovableSDCardSize(requireContext());
            TextView tvVolumeSdCard = root.findViewById(R.id.tv_storage_volume_sdcard);
            if (tvVolumeSdCard != null) {
                tvVolumeSdCard.setText(formatVolumeInfo(
                        R.string.storage_volume_sdcard_label, sdTotal, sdAvailable));
            }
            setupMinFreeStorageSlider(sliderMinFreeStorageSdCard, tvMinFreeStorageSdCardValue,
                    Option::getMinFreeStorageMbForSdCard, Option::setMinFreeStorageMbForSdCard);
            wireStorageBar(root.findViewById(R.id.storageBarSdCard), root.findViewById(R.id.ll_legend_sdcard),
                    sliderMinFreeStorageSdCard,
                    StorageInfoCacheHelper.getCachedSDCardTotal(),
                    StorageInfoCacheHelper.getCachedSDCardUsedByOthers(),
                    StorageInfoCacheHelper.getCachedSDCardUsedByBookPlayer(),
                    0, // app storage is internal-only
                    StorageInfoCacheHelper.getCachedSDCardLinkedAudios());
        }
    }

    private String formatVolumeInfo(int labelResId, long totalBytes, long freeBytes) {
        return getString(R.string.storage_volume_info, getString(labelResId),
                Tonio.getReadableSize(Math.max(0, totalBytes)), Tonio.getReadableSize(Math.max(0, freeBytes)));
    }

    /** Same colored breakdown StatsActivity shows (gray=others, dark blue=app storage,
     *  indigo=BookPlayer audio, green=linked) - read straight from StorageInfoCacheHelper's
     *  synchronous cache rather than recomputing it (StatsViewModel owns the actual
     *  calculation/cache-refresh). The legend is hidden by default and toggled by tapping the
     *  bar. The orange threshold marker tracks the slider live. */
    private void wireStorageBar(@Nullable StorageBarView bar, @Nullable View legend, @Nullable Slider slider,
            long totalBytes, long usedByOthersBytes, long usedByBookPlayerBytes, long appStorageBytes,
            long linkedAudiosBytes) {
        if (bar == null || slider == null || totalBytes <= 0)
            return;
        bar.setStorageValues(totalBytes, usedByOthersBytes, usedByBookPlayerBytes, 0,
                linkedAudiosBytes, appStorageBytes);
        bar.setMinFreeThresholdBytes((long) slider.getValue() * 1024L * 1024L);
        slider.addOnChangeListener((s, value, fromUser) ->
                bar.setMinFreeThresholdBytes((long) value * 1024L * 1024L));
        if (legend != null) {
            bar.setOnClickListener(v -> legend.setVisibility(
                    legend.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
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
