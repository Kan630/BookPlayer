package com.driot.bookplayer.activities;

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
import com.driot.bookplayer.adapter.PodcastSearchResultsRVAdapter;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.PodcastDao;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.objects.PodcastFeed;
import com.driot.bookplayer.utils.NetworkUtils;
import com.driot.bookplayer.helpers.PodcastHelper;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.util.List;

public class PodcastSearchResultsActivity extends LoggingActivity {

    private PodcastSearchResultsViewModel viewModel;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView errorMessage;
    private PodcastSearchResultsRVAdapter adapter;
    Podcast podcast;



    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_podcast_search_result);

        recyclerView = findViewById(R.id.recyclerViewPodcast);
        InsetHelper.applyInsetsForScrollableBehindNavBar(this, recyclerView);
        InsetHelper.applyBottomInsetsForScrollable(this, findViewById(R.id.miniNowPlaying));

        progressBar = findViewById(R.id.progressBarPodcast);
        errorMessage = findViewById(R.id.podcast_error_message);

        int span = getResources().getInteger(R.integer.classic_grid_span);
        GridLayoutManager glm = new GridLayoutManager(this, span);
        recyclerView.setLayoutManager(glm);
        recyclerView.addItemDecoration(new ViewHelper.SpacesItemDecoration(ViewHelper.dp(this,Var.GRID_LAYOUT_SPACER)));
        //recyclerView.setLayoutManager(new LinearLayoutManager(this));

        viewModel = new ViewModelProvider(this).get(PodcastSearchResultsViewModel.class);

        viewModel.getShouldFinish().observe(this, shouldFinish -> {
            if (shouldFinish != null && shouldFinish) finish();
        });
        myLogD("hello");
        adapter = new PodcastSearchResultsRVAdapter(podcastFeed -> {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                PodcastDao dao = AppDatabase.getDatabase(this).PodcastDao();
                podcast = dao.getPodcastByFeedId(podcastFeed.id);
                if (podcast == null) {
                    podcast = PodcastHelper.fromPodcastFeed(podcastFeed);
                    dao.insert(podcast);
                    myLogD("podcast inserted " + podcastFeed.id );
                } else {
                    myLogD("podcast exist " + podcastFeed.id );
                }

                // Always navigate on UI thread
                runOnUiThread(() -> {
                    Intent intent = new Intent(this, PodcastEpisodeActivity.class);
                    intent.putExtra("podcast", podcast);
                    startActivity(intent);
                });
            });
        });

        recyclerView.setAdapter(adapter);

        String query = getIntent().getStringExtra("query");
        String lang = getIntent().getStringExtra("lang");
        searchPodcasts(query, lang);
    }

    private void searchPodcasts(String query, String lang) {

        if (query == null || lang == null) {
            finish();
            return;
        }

        if (viewModel.getResults().getValue() != null &&
                query.equals(viewModel.getLastQuery()) &&
                lang.equals(viewModel.getLastLang())) {
            myLogE("ca chie dans la colle");
            return;
        }

        viewModel.setLastQuery(query);
        viewModel.setLastLang(lang);
        performSearch(query, lang);
    }

    private void performSearch(String query, String lang) {
        myLogD("performSearch called with query: [" + query + "] and lang: [" + lang + "]");
        progressBar.setVisibility(View.VISIBLE);
        errorMessage.setVisibility(View.GONE);

        PodcastHelper.Callback callback = new PodcastHelper.Callback() {
            @Override
            public void onSuccess(List<PodcastFeed> feeds) {
                runOnUiThread(() -> {
                    adapter.setHeaderInfo(query, lang, feeds.size());
                    adapter.setItems(feeds);
                    handleSuccess(feeds);
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    adapter.setHeaderInfo(query, lang, 0);
                    handleError(e);
                });
            }
        };

        if (!query.isEmpty()) {
            PodcastHelper.searchPodcasts(query, lang, callback);
        } else {
            PodcastHelper.getTrendingPodcasts(lang, Var.PODCASTINDEXORG_API_MAX_RESULTS_FOR_PODCASTS, callback);
        }
    }

    private void handleSuccess(List<PodcastFeed> feeds) {
        progressBar.setVisibility(View.GONE);
        if (feeds == null || feeds.isEmpty()) {
            errorMessage.setText(getString(R.string.podcast_no_results));
            errorMessage.setVisibility(View.VISIBLE);
            errorMessage.setTextColor(getColor(R.color.orange_500));
        }
    }

    private void handleError(Exception e) {
        progressBar.setVisibility(View.GONE);
        errorMessage.setVisibility(View.VISIBLE);
        errorMessage.setTextColor(getColor(R.color.orange_500));
        if (NetworkUtils.isUnknownHost(e)) {
            myLogE("performSearch - handleError : no_internet_connection");
            errorMessage.setText(getString(R.string.no_internet_connection));
        } else {
            myLogEE(e, "performSearch - handleError");
            errorMessage.setText("Error : \n" + e.getMessage());
        }
    }
}
