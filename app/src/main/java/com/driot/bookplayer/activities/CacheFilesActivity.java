package com.driot.bookplayer.activities;

import static com.driot.bookplayer.utils.Tonio.formatMem;
import static com.driot.bookplayer.utils.Tonio.getAvailableInternalMemorySize;
import static com.driot.bookplayer.utils.Tonio.getFolderSize;
import static com.driot.bookplayer.utils.Tonio.getTotaLInternalMemorySize;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.adapter.CacheFilesRVAdapter;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.io.File;
import java.util.List;


/**
 * created by Antoine Driot -- antoine.driot.com -- on 26/05/2024
 *
 * Cleaning the app cache
 *
 * implement OneDeleteClickListener because : event is in adapter, confirmation message in activity, deletion in viewmodel
 */
public class CacheFilesActivity extends LoggingActivity implements CacheFilesRVAdapter.OnDeleteClickListener {
    private CacheFilesRVAdapter cacheFilesAdapter;

    private CacheFilesViewModel cacheFilesViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_cache_files);

        cacheFilesViewModel = new ViewModelProvider(this).get(CacheFilesViewModel.class);

// get the data in DB (async) -- LiveData stored in ViewModel
        final Observer<List<ZikFile>> myObserverOnDB = distinctZikFilePaths -> {
            myLogD("LiveData 'onChange()' observed - count of distinct path in DB = " + distinctZikFilePaths.size());
            cacheFilesAdapter.setDistinctZikFilePaths(distinctZikFilePaths);
        };
        cacheFilesViewModel.getFilesOnDb().observe(this, myObserverOnDB);

//get the data on DISK
        final Observer<List<File>> myObserverOnDisk = filesOnDisk -> {
            myLogD("LiveData 'onChange()' observed - count of distinct folders on Disk = " + filesOnDisk.size());
            cacheFilesAdapter.setFilesOnDisk(filesOnDisk);
        };
        cacheFilesViewModel.getFilesOnDisk().observe(this, myObserverOnDisk);

//RecyclerView
        cacheFilesAdapter = new CacheFilesRVAdapter(this, this);
        RecyclerView recyclerView = findViewById(R.id.recyclerView_cacheFiles);
        recyclerView.setAdapter(cacheFilesAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

// Memory stats at the top
        cacheFilesViewModel.getMemoryStats().observe(this, updated -> {
            if (updated != null && updated) {
                FillTextViewMemoryStats();
                // Reset the value so we don't trigger again unnecessarily
                //cacheFilesViewModel.getMemoryStats().setValue(false); // should use MutableLiveData for this
            }
        });
    }

    private void FillTextViewMemoryStats() {
        long currentAudiosSize = getFolderSize(this.getFilesDir().getPath() + "/unzipped") / 1048576L;
        long totalMemory = getTotaLInternalMemorySize() / 1048576L;
        long availableMegs2 = getAvailableInternalMemorySize() / 1048576L;
        String zeText = formatMem(currentAudiosSize) + " " + getString(R.string.MB_audios_in_app) + "\n" + "\n" +
                        formatMem(availableMegs2) + " " + getString(R.string.MB_left_on_device) + "\n" + "\n" +
                        formatMem(totalMemory) + " " + getString(R.string.MB_device_memory2);
        TextView tv_txt = findViewById(R.id.cachefiles_stats_text);
        tv_txt.setText(zeText);
        myLogD("FillTextViewMemoryStats()\n" + zeText);
    }

    public void onDeleteClick(File file, int position) {
        myLogI("Delete Click on [" + file.getName() + "]");
        new AlertDialog.Builder(this)
                .setTitle(R.string.AskDelete_popupTitle)
                .setMessage(getString(R.string.CacheFiles_AskDeleteAudioBook) + ":\n [" + file.getName() + "]\n    " + getString(R.string.are_you_sure))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.Delete), (dialog, which) -> cacheFilesViewModel.deleteAudio(file))
                .setNegativeButton(getString(R.string.Cancel), (dialogInterface, i) -> {
                })
                .show();
    }
}
