package com.driot.bookplayer.activities;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.CleanMemoryRVAdapter;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.objects.FolderWithSummary;
import com.driot.bookplayer.utils.Tonio;

import java.io.File;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class CleanMemoryActivity extends BaseBottomNavActivity implements CleanMemoryRVAdapter.OnDeleteClickListener {
    private CleanMemoryRVAdapter cacheFilesAdapter;
    private CleanMemoryViewModel cacheFilesViewModel;
    private RadioGroup storageSelector;
    private RadioButton radioInternal, radioSdCard;
    private View progressContainer;
    private ProgressBar progressBar;
    private TextView progressScanMessage;
    private TextView statsTextView;
    private RecyclerView recyclerViewCacheFiles;
    private TextView emptyListMessage;
    private final Handler refreshElapsedHandler = new Handler(Looper.getMainLooper());
    private Runnable refreshElapsedRunnable;
    private final Runnable showMessageAfterOneSecRunnable = () -> {
        if (!Boolean.TRUE.equals(cacheFilesViewModel.getIsRefreshing().getValue())) return;
        progressScanMessage.setVisibility(View.VISIBLE);
        startRefreshElapsedTimer();
    };

    @Override protected int getNavId() { return R.id.nav_settings; } //TODO change to correct one after migrating menu items
    @Override protected int getLayoutResId() { return R.layout.activity_clean_memory; }
    @Override protected boolean enableOngoingTaskOverlay() { return true; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        recyclerViewCacheFiles = findViewById(R.id.recyclerView_cacheFiles);
        emptyListMessage = findViewById(R.id.empty_list_message);

        statsTextView = findViewById(R.id.cachefiles_stats_text);

        progressContainer = findViewById(R.id.progress_container);
        progressBar = findViewById(R.id.progress_cache_loading);
        progressScanMessage = findViewById(R.id.progress_scan_message);
        progressContainer.bringToFront();

        ImageButton btnRefresh = findViewById(R.id.btn_clean_memory_refresh);
        btnRefresh.setOnClickListener(v -> {
            myLogI("---- USER TOGGLE REFRESH BUTTON ----");
            cacheFilesViewModel.refreshStorageCache(this, cacheFilesViewModel.isUsingInternal());
        });

        cacheFilesViewModel = new ViewModelProvider(this).get(CleanMemoryViewModel.class);

        cacheFilesAdapter = new CleanMemoryRVAdapter(this, this);
        recyclerViewCacheFiles.setAdapter(cacheFilesAdapter);
        recyclerViewCacheFiles.setLayoutManager(new LinearLayoutManager(this));

        // Set up observers
        cacheFilesViewModel.getEnrichedFolders().observe(this, fileWithSummaries -> {
            myLogD("Enriched list updated: " + (fileWithSummaries != null ? fileWithSummaries.size() : 0));
            cacheFilesAdapter.setFilesWithSummary(fileWithSummaries);
            updateEmptyListVisibility(fileWithSummaries);
        });

        cacheFilesViewModel.getTotalAudioSizeMB().observe(this, audioMB -> {
            long audioMBToShow = Boolean.TRUE.equals(cacheFilesViewModel.getIsRefreshing().getValue()) ? -1L : (audioMB != null ? audioMB : -1L);
            FillTextViewMemoryStats(audioMBToShow,
                    StorageHelper.getAvailableStorageMB(this, cacheFilesViewModel.isUsingInternal()),
                    StorageHelper.getTotalStorageMB(this, cacheFilesViewModel.isUsingInternal()),
                    null
            );
        });

        cacheFilesViewModel.getIsLoading().observe(this, isLoading -> {
            updateProgressVisibility();
        });

        cacheFilesViewModel.getIsRefreshing().observe(this, isRefreshing -> {
            if (Boolean.TRUE.equals(isRefreshing)) {
                recyclerViewCacheFiles.setVisibility(View.GONE);
                progressScanMessage.setVisibility(View.GONE); // show only after 1 sec (see startRefreshElapsedTimer)
                cancelShowMessageAfterDelay();
                refreshElapsedHandler.postDelayed(showMessageAfterOneSecRunnable, 1000);
                // Hide "No books in BookPlayer..." while scanning
                updateEmptyListVisibility(cacheFilesViewModel.getEnrichedFolders().getValue());
                // Show "..." for audios in app while scanning
                FillTextViewMemoryStats(-1L,
                        StorageHelper.getAvailableStorageMB(this, cacheFilesViewModel.isUsingInternal()),
                        StorageHelper.getTotalStorageMB(this, cacheFilesViewModel.isUsingInternal()),
                        null);
            } else {
                cancelShowMessageAfterDelay();
                stopRefreshElapsedTimer();
                progressScanMessage.setVisibility(View.GONE);
                recyclerViewCacheFiles.setVisibility(View.VISIBLE);
            }
            updateProgressVisibility();
        });

        setupRadioButtons();
    }

    private void updateProgressVisibility() {
        boolean loading = Boolean.TRUE.equals(cacheFilesViewModel.getIsLoading().getValue());
        boolean refreshing = Boolean.TRUE.equals(cacheFilesViewModel.getIsRefreshing().getValue());
        progressContainer.setVisibility(loading || refreshing ? View.VISIBLE : View.GONE);
    }

    private void updateEmptyListVisibility(List<FolderWithSummary> fileWithSummaries) {
        boolean loading = Boolean.TRUE.equals(cacheFilesViewModel.getIsLoading().getValue());
        boolean refreshing = Boolean.TRUE.equals(cacheFilesViewModel.getIsRefreshing().getValue());
        if (loading || refreshing) {
            emptyListMessage.setVisibility(View.GONE);
            return;
        }
        boolean empty = fileWithSummaries == null || fileWithSummaries.isEmpty();
        if (empty) {
            emptyListMessage.setText(cacheFilesViewModel.isUsingInternal()
                    ? getString(R.string.clean_memory_empty_internal)
                    : getString(R.string.clean_memory_empty_sdcard));
            emptyListMessage.setVisibility(View.VISIBLE);
        } else {
            emptyListMessage.setVisibility(View.GONE);
        }
    }

    private void startRefreshElapsedTimer() {
        stopRefreshElapsedTimer();
        Long startTime = cacheFilesViewModel.getRefreshStartTime().getValue();
        if (startTime == null || startTime == 0) return;
        boolean onSdCardView = !cacheFilesViewModel.isUsingInternal();
        refreshElapsedRunnable = new Runnable() {
            @Override
            public void run() {
                if (!Boolean.TRUE.equals(cacheFilesViewModel.getIsRefreshing().getValue())) return;
                long elapsedSec = (System.currentTimeMillis() - startTime) / 1000;
                String msg = getString(R.string.clean_memory_scanning_folders, (int) elapsedSec);
                if (onSdCardView) {
                    msg += "\n\n" + getString(R.string.clean_memory_sdcard_slow);
                }
                progressScanMessage.setText(msg);
                refreshElapsedHandler.postDelayed(this, 1000);
            }
        };
        refreshElapsedRunnable.run();
    }

    private void cancelShowMessageAfterDelay() {
        refreshElapsedHandler.removeCallbacks(showMessageAfterOneSecRunnable);
    }

    private void stopRefreshElapsedTimer() {
        if (refreshElapsedRunnable != null) {
            refreshElapsedHandler.removeCallbacks(refreshElapsedRunnable);
            refreshElapsedRunnable = null;
        }
    }

    private void setupRadioButtons() {
        storageSelector = findViewById(R.id.storage_selector);
        radioInternal   = findViewById(R.id.radio_internal);
        radioSdCard     = findViewById(R.id.radio_sdcard);

        String sdPath = StorageHelper.getSdCardUnzippedFolder(this);
        if (sdPath == null) {
            myLogD("no SD card => hide storageSelector");
            storageSelector.setVisibility(View.GONE);
        }

        storageSelector.setOnCheckedChangeListener((group, checkedId) -> {
            myLogI("---- USER TOGGLE RADIO BUTTON ----");
            boolean useInternal = (checkedId == R.id.radio_internal);

            FillTextViewMemoryStats(
                    -1,
                    StorageHelper.getAvailableStorageMB(this, useInternal),
                    StorageHelper.getTotalStorageMB(this, useInternal),
                    useInternal ? getString(R.string.device) : getString(R.string.SD_card)
            );
            cacheFilesViewModel.setUseInternal(useInternal);
        });

        // ---- default selection: SD if internal has no content and SD has some ----
        boolean sdPresent = (sdPath != null);
        boolean internalHas = hasAnyContent(true);
        boolean sdHas = sdPresent && hasAnyContent(false);

        if (!internalHas && sdHas) {
            myLogD("Internal empty, SD has content -> default to SD view");
            radioSdCard.setChecked(true);     // triggers listener -> setUseInternal(false)
        } else {
            radioInternal.setChecked(true);
        }
/*
        // ---- one-time fallback after first load (in case scanning finishes empty) ----
        final boolean[] triedAutoSwitch = { false };
        cacheFilesViewModel.getEnrichedFiles().observe(this, list -> {
            if (!triedAutoSwitch[0]
                    && cacheFilesViewModel.isUsingInternal()
                    && (list == null || list.isEmpty())) {

                String sd = StorageHelper.getSdCardUnzippedFolder(this);
                if (sd != null && hasAnyContent(false)) {
                    triedAutoSwitch[0] = true;
                    myLogD("Auto-switching to SD (internal list ended up empty).");
                    radioSdCard.setChecked(true);
                }
            }
        });

 */
    }


    private void FillTextViewMemoryStats(long MB_audio, long MB_leftOnDevice, long MB_deviceMemory, String forceLabel) {
        String label;
        if (forceLabel == null) {
            label = cacheFilesViewModel.isUsingInternal() ? getString(R.string.device) : getString(R.string.SD_card);
        } else {
            label = forceLabel;
        }
        String MB_audio_in_app = getString(R.string.MB) + ": " + getString(R.string.audios_in_app);
        String MB_left_on_label = getString(R.string.MB) + ": " + getString(R.string.left_on) + " " + label;
        String MB_label_memory = getString(R.string.MB) + ": " + label + " " + getString(R.string.memory);

        String str_MB_audio = MB_audio >= 0 ? Tonio.formatMemPadding(this.getApplicationContext(), MB_audio) : String.format("%9s", "...");

        String zeText = str_MB_audio + " " + MB_audio_in_app + "\n\n" +
                Tonio.formatMemPadding(this.getApplicationContext(), MB_leftOnDevice) + " " + MB_left_on_label + "\n\n" +
                Tonio.formatMemPadding(this.getApplicationContext(), MB_deviceMemory) + " " + MB_label_memory;

        statsTextView.setText(zeText);
    }


    @Override
    public void onDeleteClick(File file, int position) {
        myLogI("Delete Click on [" + file.getName() + "]");
        new AlertDialog.Builder(this)
                .setTitle(R.string.AskDelete_popupTitle)
                .setMessage(getString(R.string.You_are_about_to_delete_this_audio_book) + ":\n [" + file.getName() + "]\n    " + getString(R.string.are_you_sure))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.Delete), (dialog, which) -> {
                    cacheFilesViewModel.deleteAudio(file);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private boolean hasAnyContent(boolean internal) {
        try {
            String basePath = internal
                    ? getFilesDir().getPath() + "/" + Var.FOLDER_UNZIPPED
                    : StorageHelper.getSdCardUnzippedFolder(this);
            if (basePath == null) return false;

            File base = new File(basePath);
            File[] kids = base.listFiles();
            if (kids == null || kids.length == 0) return false;

            for (File f : kids) {
                if (f.isDirectory()) return true;
                if (f.isFile() && f.length() > 0) return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

}
