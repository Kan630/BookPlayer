package com.driot.bookplayer.activities;

import android.os.Bundle;
import android.view.View;
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
import com.driot.bookplayer.utils.StorageHelper;
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

        statsTextView = findViewById(R.id.cachefiles_stats_text);

        progressBar = findViewById(R.id.progress_cache_loading);
        progressBar.bringToFront();

        cacheFilesViewModel = new ViewModelProvider(this).get(CleanMemoryViewModel.class);

        cacheFilesAdapter = new CleanMemoryRVAdapter(this, this);
        RecyclerView recyclerView = findViewById(R.id.recyclerView_cacheFiles);
        recyclerView.setAdapter(cacheFilesAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Set up observers
        cacheFilesViewModel.getEnrichedFiles().observe(this, fileWithSummaries -> {
            myLogD("Enriched list updated: " + fileWithSummaries.size());
            cacheFilesAdapter.setFilesWithSummary(fileWithSummaries);
        });

        cacheFilesViewModel.getMemoryStats().observe(this, updated -> {
            if (Boolean.TRUE.equals(updated)) {
                FillTextViewMemoryStats();
            }
        });

        // Observe loading state
        cacheFilesViewModel.getIsLoading().observe(this, isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            if (isLoading) {
                FillTextViewMemoryStats(); // show base stats immediately when loading starts
            }
        });


        setupRadioButtons();
    }

    private void setupRadioButtons() {
        storageSelector = findViewById(R.id.storage_selector);
        radioInternal = findViewById(R.id.radio_internal);
        radioSdCard = findViewById(R.id.radio_sdcard);

        String sdPath = StorageHelper.getSdCardUnzippedFolder(this);
        if (sdPath == null) {
            myLogD("no SD card => hide storageSelector");
            storageSelector.setVisibility(View.GONE);
        }

        storageSelector.setOnCheckedChangeListener((group, checkedId) -> {
            myLogI("---- USER TOGGLE RADIO BUTTON ----");
            if (checkedId == R.id.radio_internal) {
                myLogD("cacheFilesViewModel.setUseInternal(true);");
                cacheFilesViewModel.setUseInternal(true);
            } else if (checkedId == R.id.radio_sdcard) {
                myLogD("cacheFilesViewModel.setUseInternal(false);");
                cacheFilesViewModel.setUseInternal(false);
            }
        });

        // default selection
        radioInternal.setChecked(true);
    }

    private void FillTextViewMemoryStats() {
        long totalMemory;
        long availableMemory;

        String label = cacheFilesViewModel.isUsingInternal() ? getString(R.string.device) : getString(R.string.SD_card);
        String MB_audio_in_app = getString(R.string.MB) + ": " + getString(R.string.audios_in_app);
        String MB_left_on_label = getString(R.string.MB) + ": " + getString(R.string.left_on) + " " + label;
        String MB_label_memory = getString(R.string.MB) + ": " + label  + " " + getString(R.string.memory);

        if (cacheFilesViewModel.isUsingInternal()) {
            totalMemory = StorageHelper.getTotaLInternalMemorySize() / 1048576L;
            availableMemory = StorageHelper.getAvailableInternalMemorySize() / 1048576L;
        } else {
            totalMemory = StorageHelper.getTotalRemovableSDCardSize(this) / 1048576L;
            availableMemory = StorageHelper.getAvailableRemovableSDCardSize(this) / 1048576L;
        }

        // Always show available and total memory
        String zeText = "..." + "\n\n" +
                Tonio.formatMemPadding(availableMemory) + " " + MB_left_on_label + "\n\n" +
                Tonio.formatMemPadding(totalMemory) + " " + MB_label_memory;

        // Try to add audio size if folder is there
        String path = cacheFilesViewModel.isUsingInternal()
                ? getFilesDir().getPath() + "/unzipped"
                : StorageHelper.getSdCardUnzippedFolder(this);
        if (path != null) {
            long audioSize = Tonio.getFolderSize(path) / 1048576L;
            zeText = Tonio.formatMemPadding(audioSize) + " " +  MB_audio_in_app + "\n\n" +
                    Tonio.formatMemPadding(availableMemory) + " " + MB_left_on_label + "\n\n" +
                    Tonio.formatMemPadding(totalMemory) + " " + MB_label_memory;
        }
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
                    FillTextViewMemoryStats();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }
}
