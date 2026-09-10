package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.DetailedStatsRVAdapter;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.utils.log.BaseActivity;

import java.util.List;

/** Per-book/folder breakdown of listening time - same categories as StatsActivity's totals,
 * (see StatsCategoryHelper) but at individual-folder granularity, sorted by time listened. */
public class DetailedStatsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detailed_stats);

        RecyclerView recyclerView = findViewById(R.id.recyclerViewDetailedStats);
        TextView tvEmpty = findViewById(R.id.tvDetailedStatsEmpty);

        InsetHelper.applyInsetsForScrollableBehindNavBar(this, recyclerView);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        DetailedStatsRVAdapter adapter = new DetailedStatsRVAdapter(folder -> {
            startActivity(new Intent(this, ZikFileActivity.class)
                    .putExtra(Intents.EXTRA_FOLDER, folder));
        });
        recyclerView.setAdapter(adapter);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Folder> folders = AppDatabase.getDatabase(this).folderDao().getAllByTimeListenedDesc();
            new Handler(Looper.getMainLooper()).post(() -> {
                if (isFinishing()) {
                    return;
                }
                adapter.setItems(folders);
                boolean empty = folders == null || folders.isEmpty();
                recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
                tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            });
        });
    }
}
