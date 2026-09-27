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
import com.driot.bookplayer.podcasts.PodcastHelper;
import com.driot.bookplayer.global.Var;
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
            startActivity(new Intent(this, MainActivity.class)
                    .putExtra(Intents.EXTRA_FOLDER, folder)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        });
        recyclerView.setAdapter(adapter);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Folder> folders = loadFoldersByTimeListened();
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

    // Folder.timeListened only sums the tracks still there. A podcast folder also counts the time
    // kept on its episodes (deleted downloads, streaming), so deleting an episode doesn't make its
    // listening vanish from here. In-memory only, the Folder rows are not changed. Off main thread.
    private List<Folder> loadFoldersByTimeListened() {
        List<Folder> result = new java.util.ArrayList<>();
        for (Folder f : AppDatabase.getDatabase(this).folderDao().getAll()) {
            if (Var.SOURCE_LOCATION_PODCAST.equals(f.getSourceLocation()))
                f.timeListened += PodcastHelper.getEpisodeTimeListenedForFolder(this, f.getId());
            if (f.timeListened > 0)
                result.add(f);
        }
        result.sort((a, b) -> Long.compare(b.timeListened, a.timeListened));
        return result;
    }
}
