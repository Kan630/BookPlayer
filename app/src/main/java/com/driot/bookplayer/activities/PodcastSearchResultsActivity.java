package com.driot.bookplayer.activities;

import static com.driot.bookplayer.global.Var.PODCASTINDEXORG_API_MAX_RESULTS;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.PodcastSearchResultsRVAdapter;
import com.driot.bookplayer.objects.PodcastFeed;
import com.driot.bookplayer.helpers.AnalyticsHelper;
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



    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_podcastsearchresult);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.recyclerViewPodcast), (v, insets) -> {
            Insets systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, systemInsets.top, 0, systemInsets.bottom);
            return insets;
        });

        recyclerView = findViewById(R.id.recyclerViewPodcast);
        progressBar = findViewById(R.id.progressBarPodcast);
        errorMessage = findViewById(R.id.podcast_error_message);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        viewModel = new ViewModelProvider(this).get(PodcastSearchResultsViewModel.class);

        viewModel.getShouldFinish().observe(this, shouldFinish -> {
            if (shouldFinish != null && shouldFinish) finish();
        });

        adapter = new PodcastSearchResultsRVAdapter(item -> {
            Intent intent = new Intent(this, PodcastEpisodeActivity.class);
            intent.putExtra("podcastFeed", item);
            startActivity(intent);
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
        AnalyticsHelper.tellAnalyticsPodcastSearch(this, query, lang);
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
            PodcastHelper.getTrendingPodcasts(lang, PODCASTINDEXORG_API_MAX_RESULTS, callback);
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
