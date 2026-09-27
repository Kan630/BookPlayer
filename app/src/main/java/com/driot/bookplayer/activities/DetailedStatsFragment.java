package com.driot.bookplayer.activities;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.podcasts.PodcastHelper;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.adapter.DetailedStatsRVAdapter;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.utils.log.LoggingFragment;

import java.util.List;

/** Per-book/folder breakdown of listening time - same categories as StatsFragment's totals,
 * (see StatsCategoryHelper) but at individual-folder granularity, sorted by time listened.
 * Library-tab destination (was DetailedStatsActivity): a book opens on top of it in the same
 * tab, so back comes back here. */
public class DetailedStatsFragment extends LoggingFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_detailed_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewDetailedStats);
        TextView tvEmpty = view.findViewById(R.id.tvDetailedStatsEmpty);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        DetailedStatsRVAdapter adapter = new DetailedStatsRVAdapter(folder -> {
            myLogI("Item Click on [" + folder.getName() + "]");
            Bundle args = new Bundle();
            args.putLong(Intents.EXTRA_FOLDER_ID, folder.getId());
            args.putParcelable(Intents.EXTRA_FOLDER, folder);
            NavOptions options = new NavOptions.Builder()
                    .setEnterAnim(R.anim.slide_enter_from_right)
                    .setExitAnim(R.anim.slide_exit_to_left)
                    .setPopEnterAnim(R.anim.slide_pop_enter_from_left)
                    .setPopExitAnim(R.anim.slide_pop_exit_to_right)
                    .build();
            Navigation.findNavController(view).navigate(R.id.zikFileFragment, args, options);
        });
        recyclerView.setAdapter(adapter);

        Context app = requireContext().getApplicationContext();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Folder> folders = loadFoldersByTimeListened(app);
            view.post(() -> {
                if (getView() != view) {
                    return; // view destroyed (or replaced) while loading
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
    private static List<Folder> loadFoldersByTimeListened(Context context) {
        List<Folder> result = new java.util.ArrayList<>();
        for (Folder f : AppDatabase.getDatabase(context).folderDao().getAll()) {
            if (Var.SOURCE_LOCATION_PODCAST.equals(f.getSourceLocation()))
                f.timeListened += PodcastHelper.getEpisodeTimeListenedForFolder(context, f.getId());
            if (f.timeListened > 0)
                result.add(f);
        }
        result.sort((a, b) -> Long.compare(b.timeListened, a.timeListened));
        return result;
    }
}
