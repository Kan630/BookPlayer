package com.driot.bookplayer.activities;

import static com.driot.bookplayer.global.Var.PODCAST_INDEX_ORG_SINCE;

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

import java.util.Collections;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class PodcastFavoritesActivity extends BaseBottomNavActivity {

    private PodcastSearchResultsViewModel viewModel;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyMessage, tvSearchTerms, tvLanguage, tvResultsCount;
    private PodcastFavoritesRVAdapter adapter;

    @Override
    protected int getNavId() {
        return R.id.nav_podcast;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_podcast_search_result;
    }

    @Override
    protected boolean enableOngoingTaskOverlay() {
        return true;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        recyclerView = findViewById(R.id.recyclerViewPodcast);
        progressBar = findViewById(R.id.progressBarPodcast);
        emptyMessage = findViewById(R.id.podcast_error_message);

        int span = getResources().getInteger(R.integer.classic_grid_span);
        GridLayoutManager glm = new GridLayoutManager(this, span);
        recyclerView.setLayoutManager(glm);
        recyclerView
                .addItemDecoration(new ViewHelper.SpacesItemDecoration(ViewHelper.dp(this, Var.GRID_LAYOUT_SPACER)));

        viewModel = new ViewModelProvider(this).get(PodcastSearchResultsViewModel.class);
        viewModel.getShouldFinish().observe(this, shouldFinish -> {
            if (shouldFinish != null && shouldFinish)
                finish();
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
                        PodcastHelper.checkForNewEpisodesToAutoDownloadForPodcast(this, item, PODCAST_INDEX_ORG_SINCE);
                    });
                });
        recyclerView.setAdapter(adapter);

        viewModel.getFavoritePodcastsLive().observe(this, favorites -> {
            if (favorites == null || favorites.isEmpty()) {
                myToast(getString(com.driot.bookplayer.R.string.no_favorite_podcasts_found));
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
