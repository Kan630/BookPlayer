package com.driot.bookplayer.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
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
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.io.File;

public class CleanMemoryActivity extends LoggingActivity implements CleanMemoryRVAdapter.OnDeleteClickListener {
    private CleanMemoryRVAdapter cacheFilesAdapter;
    private CleanMemoryViewModel cacheFilesViewModel;
    private RadioGroup storageSelector;
    private RadioButton radioInternal, radioSdCard;
    private ProgressBar progressBar;
    private TextView statsTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clean_memory);

        RecyclerView recyclerView = findViewById(R.id.recyclerView_cacheFiles);
        LinearLayout mainContent = findViewById(R.id.main_content);
        //InsetHelper.applyInsetsForContentWithScrollableBottom(this, mainContent, recyclerView);
        InsetHelper.apply(this);

        statsTextView = findViewById(R.id.cachefiles_stats_text);

        progressBar = findViewById(R.id.progress_cache_loading);
        progressBar.bringToFront();

        cacheFilesViewModel = new ViewModelProvider(this).get(CleanMemoryViewModel.class);

        cacheFilesAdapter = new CleanMemoryRVAdapter(this, this);
        recyclerView.setAdapter(cacheFilesAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Set up observers
        cacheFilesViewModel.getEnrichedFolders().observe(this, fileWithSummaries -> {
            myLogD("Enriched list updated: " + fileWithSummaries.size());
            cacheFilesAdapter.setFilesWithSummary(fileWithSummaries);
        });

        cacheFilesViewModel.getTotalAudioSizeMB().observe(this, audioMB -> {
            FillTextViewMemoryStats(audioMB,
                    StorageHelper.getAvailableStorageMB(this, cacheFilesViewModel.isUsingInternal()),
                    StorageHelper.getTotalStorageMB(this, cacheFilesViewModel.isUsingInternal()),
                    null
            );
        });

        cacheFilesViewModel.getIsLoading().observe(this, isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });

        setupRadioButtons();
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

        String str_MB_audio = MB_audio > 0 ? Tonio.formatMemPadding(MB_audio) : String.format("%9s", "...");

        String zeText = str_MB_audio + " " + MB_audio_in_app + "\n\n" +
                Tonio.formatMemPadding(MB_leftOnDevice) + " " + MB_left_on_label + "\n\n" +
                Tonio.formatMemPadding(MB_deviceMemory) + " " + MB_label_memory;

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
                .setNegativeButton(getString(R.string.Cancel), null)
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
