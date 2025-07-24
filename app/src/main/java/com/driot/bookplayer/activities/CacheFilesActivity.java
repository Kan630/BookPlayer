package com.driot.bookplayer.activities;

import static com.driot.bookplayer.utils.StorageHelper.getAvailableInternalMemorySize;
import static com.driot.bookplayer.utils.StorageHelper.getTotaLInternalMemorySize;
import static com.driot.bookplayer.utils.Tonio.formatMem;
import static com.driot.bookplayer.utils.Tonio.getFolderSize;

import android.os.Bundle;
import android.os.StatFs;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.CacheFilesRVAdapter;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.utils.StorageHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.io.File;
import java.util.List;

public class CacheFilesActivity extends LoggingActivity implements CacheFilesRVAdapter.OnDeleteClickListener {
    private CacheFilesRVAdapter cacheFilesAdapter;
    private CacheFilesViewModel cacheFilesViewModel;
    private RadioGroup storageSelector;
    private RadioButton radioInternal, radioSdCard;
    private ProgressBar progressBar;
    private TextView statsTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cache_files);

        statsTextView = findViewById(R.id.cachefiles_stats_text);

        progressBar = findViewById(R.id.progress_cache_loading);
        progressBar.bringToFront();

        cacheFilesViewModel = new ViewModelProvider(this).get(CacheFilesViewModel.class);

        cacheFilesAdapter = new CacheFilesRVAdapter(this, this);
        RecyclerView recyclerView = findViewById(R.id.recyclerView_cacheFiles);
        recyclerView.setAdapter(cacheFilesAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Set up observers
        cacheFilesViewModel.getFilesOnDb().observe(this, zikFiles -> {
            myLogD("DB files updated: " + zikFiles.size());
            cacheFilesAdapter.setDistinctZikFilePaths(zikFiles);
        });

        cacheFilesViewModel.getFilesOnDisk().observe(this, filesOnDisk -> {
            myLogD("Disk files updated: " + filesOnDisk.size());
            cacheFilesAdapter.setFilesOnDisk(filesOnDisk);
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
            myLogI("no SD card");
            radioSdCard.setEnabled(false);
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
        String MB_left_on_label = getString(R.string.MB) + ": " + getString(R.string.left_on) + " " + label;
        String MB_label_memory = getString(R.string.MB) + ": " + label  + " " + getString(R.string.left_on);
        String MB_audio_in_app = getString(R.string.MB) + ": " + getString(R.string.audios_in_app);

        if (cacheFilesViewModel.isUsingInternal()) {
            totalMemory = StorageHelper.getTotaLInternalMemorySize() / 1048576L;
            availableMemory = StorageHelper.getAvailableInternalMemorySize() / 1048576L;
        } else {
            totalMemory = StorageHelper.getTotalRemovableSDCardSize(this) / 1048576L;
            availableMemory = StorageHelper.getAvailableRemovableSDCardSize(this) / 1048576L;
        }

        // Always show available and total memory
        String zeText = "..." + "\n\n" +
                Tonio.formatMem(availableMemory) + " " + MB_left_on_label + "\n\n" +
                Tonio.formatMem(totalMemory) + " " + MB_label_memory;

        // Try to add audio size if folder is there
        String path = cacheFilesViewModel.isUsingInternal()
                ? getFilesDir().getPath() + "/unzipped"
                : StorageHelper.getSdCardUnzippedFolder(this);
        if (path != null) {
            long audioSize = Tonio.getFolderSize(path) / 1048576L;
            zeText = Tonio.formatMem(audioSize) + " " +  MB_audio_in_app + "\n\n" +
                    Tonio.formatMem(availableMemory) + " " + MB_left_on_label + "\n\n" +
                    Tonio.formatMem(totalMemory) + " " + MB_label_memory;
        }
        statsTextView.setText(zeText);
    }


    @Override
    public void onDeleteClick(File file, int position) {
        myLogI("Delete Click on [" + file.getName() + "]");
        new AlertDialog.Builder(this)
                .setTitle(R.string.AskDelete_popupTitle)
                .setMessage(getString(R.string.CacheFiles_AskDeleteAudioBook) + ":\n [" + file.getName() + "]\n    " + getString(R.string.are_you_sure))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.Delete), (dialog, which) -> cacheFilesViewModel.deleteAudio(file))
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }
}
