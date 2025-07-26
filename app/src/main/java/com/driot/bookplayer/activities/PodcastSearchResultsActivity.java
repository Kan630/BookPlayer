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
import com.driot.bookplayer.utils.PodcastHelper;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.util.List;

public class PodcastSearchResultsActivity extends LoggingActivity {

    private PodcastSearchResultsViewModel viewModel;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyMessage;
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
        emptyMessage = findViewById(R.id.podcast_empty_message);

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
    }

    private void performSearch(String query, String lang) {
        myLogD("performSearch called with query: " + query + " and lang: " + lang);
        progressBar.setVisibility(View.VISIBLE);
        emptyMessage.setVisibility(View.GONE);

        if (!query.equals("")) {
            PodcastHelper.searchPodcasts(query, lang, new PodcastHelper.Callback() {
                @Override
                public void onSuccess(List<PodcastFeed> feeds) {
                    runOnUiThread(() -> handleSuccess(feeds, query, lang));
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> handleError(e));
                }
            });
        } else {
            PodcastHelper.getTrendingPodcasts(lang, PODCASTINDEXORG_API_MAX_RESULTS, new PodcastHelper.Callback() {
                @Override
                public void onSuccess(List<PodcastFeed> feeds) {
                    runOnUiThread(() -> handleSuccess(feeds, query, lang));
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> handleError(e));
                }
            });
        }
    }

    private void handleSuccess(List<PodcastFeed> feeds, String query, String lang) {
        progressBar.setVisibility(View.GONE);
        if (feeds == null || feeds.isEmpty()) {
            emptyMessage.setText(getString(R.string.podcast_no_results));
            emptyMessage.setVisibility(View.VISIBLE);
            emptyMessage.setTextColor(getColor(R.color.orange_500));
        } else {
            viewModel.setResults(feeds);
            showResults(feeds, query, lang);
        }
    }

    private void handleError(Exception e) {
        progressBar.setVisibility(View.GONE);
        emptyMessage.setVisibility(View.VISIBLE);
        emptyMessage.setText("Error: " + e.getMessage());
        emptyMessage.setTextColor(getColor(R.color.red_500));
    }

    private void showResults(List<PodcastFeed> feeds, String query, String lang) {
        adapter.setHeaderInfo(query, lang, feeds.size());
        adapter.setItems(feeds);
    }
}
