package com.driot.bookplayer.podcasts;

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
import com.driot.bookplayer.nav.FullActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.PodcastDao;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.NetworkStatusRowController;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.utils.NetworkStatusViewModel;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class PodcastSearchResultsActivity extends FullActivity {

    private PodcastSearchResultsViewModel viewModel;
    private ProgressBar progressBar;
    private TextView errorMessage;
    private PodcastSearchResultsRVAdapter adapter;
    Podcast podcast;

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

        RecyclerView recyclerView = findViewById(R.id.recyclerViewPodcast);

        View networkRowView = findViewById(R.id.includeNetworkStatus);
        NetworkStatusViewModel netVm = new ViewModelProvider(this).get(NetworkStatusViewModel.class);
        new NetworkStatusRowController(this, networkRowView, this, netVm);

        progressBar = findViewById(R.id.progressBarPodcast);
        errorMessage = findViewById(R.id.podcast_error_message);

        int span = getResources().getInteger(R.integer.classic_grid_span);
        GridLayoutManager glm = new GridLayoutManager(this, span);
        recyclerView.setLayoutManager(glm);
        recyclerView
                .addItemDecoration(new ViewHelper.SpacesItemDecoration(ViewHelper.dp(this, Var.GRID_LAYOUT_SPACER)));

        viewModel = new ViewModelProvider(this).get(PodcastSearchResultsViewModel.class);

        // Observers
        viewModel.getResults().observe(this, feeds -> {
            if (feeds != null) {
                adapter.setItems(feeds);

                // Construct Header Strings
                String q = viewModel.getLastQuery();
                String queryStr = getString(R.string.Search_2pt)
                        + (q == null || q.isEmpty() ? getString(R.string.Trending) : q);

                com.driot.bookplayer.objects.LanguageItem langItem = com.driot.bookplayer.helpers.LanguageHelper
                        .getLanguageForPodcastsByCode(viewModel.getLastLang());
                String langStr = getString(R.string.Language_2pt) + (langItem != null ? langItem.displayName : "");

                String countStr = getString(R.string.Results_2pt) + feeds.size();
                if (feeds.size() == Option.getPodcastIndexOrgApiNbResults()) {
                    countStr += " (" + getString(R.string.max_number_of_results_reached) + ")";
                }

                adapter.setHeaderInfo(queryStr, langStr, countStr);
            }
        });

        viewModel.getIsLoading().observe(this, isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                progressBar.setVisibility(View.GONE);
                errorMessage.setVisibility(View.VISIBLE);
                errorMessage.setText(getString(com.driot.bookplayer.R.string.error_label_multiline) + error);
                errorMessage.setTextColor(getColor(R.color.orange_500));
            } else {
                errorMessage.setVisibility(View.GONE);
            }
        });

        viewModel.getShouldFinish().observe(this, shouldFinish -> {
            if (shouldFinish != null && shouldFinish)
                finish();
        });

        adapter = new PodcastSearchResultsRVAdapter(podcastFeed -> {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                PodcastDao dao = AppDatabase.getDatabase(this).podcastDao();
                podcast = dao.getPodcastByFeedId(podcastFeed.id);
                if (podcast == null) {
                    podcast = PodcastHelper.fromPodcastFeed(podcastFeed);
                    dao.insert(podcast);
                }

                runOnUiThread(() -> {
                    Intent intent = new Intent(this, PodcastEpisodeActivity.class);
                    intent.putExtra("podcast", podcast);
                    startActivity(intent);
                });
            });
        });

        recyclerView.setAdapter(adapter);

        viewModel.getFavoritePodcastsLive().observe(this, favorites -> {
            adapter.setFavorites(favorites);
        });

        String query = getIntent().getStringExtra("query");
        String lang = getIntent().getStringExtra("lang");

        if (savedInstanceState == null) {
            searchPodcasts(query, lang);
        } else {
            // Rotation: ViewModel retains state.
            // Just ensure if empty we retry?
            if (viewModel.getResults().getValue() == null) {
                searchPodcasts(query, lang);
            }
        }
    }

    private void searchPodcasts(String query, String lang) {
        if (query == null && lang == null) { // Trending might have empty query
            // Fallback if needed
        }

        // If ViewModel already has data for this query, don't re-search
        if (viewModel.getResults().getValue() != null
                && (query == null || query.equals(viewModel.getLastQuery()))
                && (lang == null || lang.equals(viewModel.getLastLang()))) {
            return;
        }

        if (query != null && !query.isEmpty()) {
            viewModel.search(query, lang);
        } else {
            viewModel.fetchTrending(lang);
        }
    }

}
