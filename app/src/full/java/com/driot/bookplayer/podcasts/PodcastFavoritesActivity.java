package com.driot.bookplayer.podcasts;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.nav.FullActivity;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.ViewHelper;

import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class PodcastFavoritesActivity extends FullActivity {

    private PodcastSearchResultsViewModel viewModel;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyMessage;
    private PodcastFavoritesRVAdapter adapter;

    // Favorites and history are two different Room LiveData queries (see PodcastDao); this
    // Mediator switches which one feeds the list, so the toggle just swaps the source instead of
    // needing separate manually-managed LiveData plumbing.
    private final MediatorLiveData<List<Podcast>> itemsLive = new MediatorLiveData<>();
    private LiveData<List<Podcast>> currentSource;
    private boolean isHistoryMode = false;

    /** Back from favorites goes up to the podcast search root, not straight to MainActivity. */
    @Override
    protected Class<? extends FullActivity> getSectionParent() {
        return GetPodcastActivity.class;
    }

    @Override
    protected int getNavSectionId() {
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

        recyclerView = findViewById(R.id.recyclerViewPodcast);
        InsetHelper.applyInsetsForScrollableInFullActivity(this, recyclerView);
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

        // Known upfront (from the landing-screen setting) so the header's very first bind shows
        // the intended mode immediately, instead of always starting as "Favorites" and correcting
        // itself once the query resolves.
        isHistoryMode = getIntent().getBooleanExtra(Intents.EXTRA_START_IN_HISTORY, false);

        adapter = new PodcastFavoritesRVAdapter(new PodcastFavoritesRVAdapter.OnActionListener() {
            @Override
            public void onItemClick(Podcast item) {
                myLogI(" --- user clicks podcast ---");
                Intent intent = new Intent(PodcastFavoritesActivity.this, PodcastEpisodeActivity.class);
                intent.putExtra("podcast", item);
                startActivity(intent);
            }

            @Override
            public void onToggleFavorites() {
                myLogI("--- user clicks favorites ---");
                setMode(false);
            }

            @Override
            public void onToggleHistory() {
                myLogI("--- user clicks history ---");
                setMode(true);
            }
        }, isHistoryMode);
        recyclerView.setAdapter(adapter);

        itemsLive.observe(this, this::applyResults);
        setMode(isHistoryMode);
    }

    private void setMode(boolean history) {
        isHistoryMode = history;
        if (currentSource != null) {
            itemsLive.removeSource(currentSource);
        }
        currentSource = history ? viewModel.getListenedPodcastsLive() : viewModel.getFavoritePodcastsLive();
        itemsLive.addSource(currentSource, itemsLive::setValue);
    }

    private void applyResults(List<Podcast> podcastList) {
        if (podcastList == null)
            return;
        adapter.setItems(podcastList, isHistoryMode);
        progressBar.setVisibility(View.GONE);
        emptyMessage.setText(isHistoryMode ? R.string.detailed_stats_empty : R.string.no_favorite_podcasts_found);
        emptyMessage.setVisibility(podcastList.isEmpty() ? View.VISIBLE : View.GONE);
    }

}
