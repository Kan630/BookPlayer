package com.driot.bookplayer.activities;

import static com.driot.bookplayer.global.Var.PODCASTINDEXORG_SINCE;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.PodcastFavoritesRVAdapter;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.PodcastHelper;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.util.Collections;
import java.util.List;

public class PodcastFavoritesActivity extends LoggingActivity {

    private PodcastSearchResultsViewModel viewModel;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyMessage, tvSearchTerms, tvLanguage, tvResultsCount;
    private PodcastFavoritesRVAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_podcast_search_result);

        recyclerView = findViewById(R.id.recyclerViewPodcast);
        InsetHelper.applyInsetsForScrollableBehindNavBar(this, recyclerView);
        InsetHelper.applyBottomInsetsForScrollable(this, findViewById(R.id.miniNowPlaying));

        progressBar = findViewById(R.id.progressBarPodcast);
        emptyMessage = findViewById(R.id.podcast_error_message);

        int span = getResources().getInteger(R.integer.classic_grid_span);
        GridLayoutManager glm = new GridLayoutManager(this, span);
        recyclerView.setLayoutManager(glm);
        recyclerView.addItemDecoration(new ViewHelper.SpacesItemDecoration(ViewHelper.dp(this, Var.GRID_LAYOUT_SPACER)));
        //recyclerView.setLayoutManager(new LinearLayoutManager(this));

        viewModel = new ViewModelProvider(this).get(PodcastSearchResultsViewModel.class);
        viewModel.getShouldFinish().observe(this, shouldFinish -> {
            if (shouldFinish != null && shouldFinish) finish();
        });

        adapter = new PodcastFavoritesRVAdapter(
                item -> {
                    myLogI(" --- user clicks podcast ---");
                    Intent intent = new Intent(this, PodcastEpisodeActivity.class);
                    intent.putExtra("podcast", item);
                    startActivity(intent);
                },
                (item, newState) -> {
                    myLogI(" --- user clicks autodownload ---");
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        AppDatabase.getDatabase(this)
                                .podcastDao()
                                .updateAutoDownloadStatus_fromFeedId(item.feedId, newState);
                        PodcastHelper.checkForNewEpisodesToAutoDownloadForPodcast(this, item, PODCASTINDEXORG_SINCE);
                    });
                }
        );
        recyclerView.setAdapter(adapter);

        viewModel.getFavoritePodcastsLive().observe(this, favorites -> {
            if (favorites == null || favorites.isEmpty()) {
                myToast("No favorite podcasts found");
                adapter.setItems(Collections.emptyList());
                adapter.notifyDataSetChanged();
            } else {
                showResults(favorites, "Favorites", "");
            }
        });
    }

    private void showResults(List<Podcast> podcastList, String query, String lang) {
        adapter.setItems(podcastList);
        adapter.notifyDataSetChanged();
        progressBar.setVisibility(View.GONE);
        emptyMessage.setVisibility(podcastList.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.setHeaderInfo(query, lang, podcastList.size());
    }


}

