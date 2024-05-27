package com.driot.bookplayer.activities;

import static com.driot.bookplayer.utils.Tonio.formatMem;
import static com.driot.bookplayer.utils.Tonio.getAvailableInternalMemorySize;
import static com.driot.bookplayer.utils.Tonio.getFolderSize;
import static com.driot.bookplayer.utils.Tonio.getTotaLInternalMemorySize;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.utils.CacheFilesAdapter;
import com.driot.tonylib.KanLogger;

import java.io.File;
import java.util.List;


/**
 * created by Antoine Driot -- antoine.driot.com -- on 26/05/2024
 *
 * Cleaning the cache is key to whatever
 *
 * implement OneDeleteClickListener because : event is in adapter, confirmation message in activity, deletion in viewmodel
 */
public class CacheFilesActivity extends AppCompatActivity implements CacheFilesAdapter.OnDeleteClickListener {
    private CacheFilesAdapter cacheFilesAdapter;

    private CacheFilesViewModel cacheFilesViewModel;

    private List<File> files;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        myLog("onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cache_files);

// get the data in DB (async) -- LiveData stored in ViewModel
        cacheFilesViewModel = new ViewModelProvider(this).get(CacheFilesViewModel.class);
        final Observer<List<ZikFile>> myObserverOnDB = distinctZikFilePaths -> {
            myLog("LiveData 'onChange()' observed - count of distinct path in DB = " + distinctZikFilePaths.size());
            cacheFilesAdapter.setDistinctZikFilePaths(distinctZikFilePaths);
        };
        cacheFilesViewModel.getFilesOnDb().observe(this, myObserverOnDB);

//get the data on DISK
        final Observer<List<File>> myObserverOnDisk = filesOnDisk -> {
            myLog("LiveData 'onChange()' observed - count of distinct folders on Disk = " + filesOnDisk.size());
            cacheFilesAdapter.setFilesOnDisk(filesOnDisk);
        };
        cacheFilesViewModel.getFilesOnDisk().observe(this, myObserverOnDisk);

//RecyclerView
        cacheFilesAdapter = new CacheFilesAdapter(cacheFilesViewModel,this);
        RecyclerView recyclerView = findViewById(R.id.recyclerView_cacheFiles);
        recyclerView.setAdapter(cacheFilesAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        FillTextViewMemoryStats();
    }

    private void FillTextViewMemoryStats() {
        long currentAudiosSize = getFolderSize(this.getFilesDir().getPath() + "/unzipped") / 1048576L;
        long totalMemory = getTotaLInternalMemorySize() / 1048576L;
        long availableMegs2 = getAvailableInternalMemorySize() / 1048576L;
        String zeText = formatMem(currentAudiosSize) + " Mo : taken by books" + "\n" + "\n" +
                        formatMem(availableMegs2) + " Mo : left" + "\n" + "\n" +
                        formatMem(totalMemory) + " Mo : Total phone memory.";
        TextView tv_txt = findViewById(R.id.cachefiles_stats_text);
        tv_txt.setText(zeText);

    }

    public void onDeleteClick(File file) {
        myLog("Delete Click on " + file.getName());
        new AlertDialog.Builder(this)
                .setTitle(R.string.AskDelete_popupTitle)
                .setMessage(R.string.CacheFiles_AskDelete)
                .setCancelable(false)
                .setPositiveButton("ok", (dialog, which) -> cacheFilesViewModel.deleteAudio(file))
                .setNegativeButton("cancel", (dialogInterface, i) -> {
                })
                .show();
        }

    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
}
