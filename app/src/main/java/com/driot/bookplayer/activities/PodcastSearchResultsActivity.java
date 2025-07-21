package com.driot.bookplayer.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.PodcastResultsRVAdapter;
import com.driot.bookplayer.objects.PodcastFeed;
import com.driot.bookplayer.utils.PodcastIndexHelper;

import java.util.List;

public class PodcastSearchResultsActivity extends ComponentActivity {

    private PodcastSearchViewModel viewModel;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyMessage, tvSearchTerms, tvLanguage, tvResultsCount;
    private PodcastResultsRVAdapter adapter;

    public static final int API_MAX_RESULTS = 100;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_podcastsearchresult);

        recyclerView = findViewById(R.id.recyclerViewPodcast);
        progressBar = findViewById(R.id.progressBarPodcast);
        emptyMessage = findViewById(R.id.podcast_empty_message);
        tvSearchTerms = findViewById(R.id.tvSearchTermsPodcast);
        tvLanguage = findViewById(R.id.tvLanguagePodcast);
        tvResultsCount = findViewById(R.id.tvResultsCountPodcast);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PodcastResultsRVAdapter(item -> {
            // handle click, e.g. open detail or stream podcast
            Toast.makeText(this, "Clicked: " + item.title, Toast.LENGTH_SHORT).show();
        });
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(PodcastSearchViewModel.class);

        viewModel.getResults().observe(this, this::showResults);
        viewModel.getShouldFinish().observe(this, shouldFinish -> {
            if (shouldFinish != null && shouldFinish) finish();
        });

        String query = getIntent().getStringExtra("query");
        String lang = getIntent().getStringExtra("lang");

        if (query == null || lang == null) {
            finish();
            return;
        }

        tvSearchTerms.setText("Search: " + (query.isEmpty() ? "Nothing specified" : query));
        tvLanguage.setText("Language: " + lang);
        tvResultsCount.setText("Results: ...");

        if (viewModel.getResults().getValue() != null &&
                query.equals(viewModel.getLastQuery()) &&
                lang.equals(viewModel.getLastLang())) {
            return;
        }

        viewModel.setLastQuery(query);
        viewModel.setLastLang(lang);
        performSearch(query, lang);
    }

    private void performSearch(String query, String lang) {
        progressBar.setVisibility(View.VISIBLE);
        emptyMessage.setVisibility(View.GONE);

        PodcastIndexHelper.searchPodcasts(query, lang, new PodcastIndexHelper.Callback() {
            @Override
            public void onSuccess(List<PodcastFeed> feeds) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (feeds == null || feeds.isEmpty()) {
                        emptyMessage.setVisibility(View.VISIBLE);
                        viewModel.requestFinish();
                    } else {
                        viewModel.setResults(feeds);
                        tvResultsCount.setText("Nb of podcasts found: " + feeds.size());
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    emptyMessage.setVisibility(View.VISIBLE);
                    emptyMessage.setText("Error: " + e.getMessage());
                    tvResultsCount.setText("Error occurred");
                });
            }
        });
    }

    private void showResults(List<PodcastFeed> feeds) {
        adapter.setItems(feeds);
        adapter.notifyDataSetChanged();
    }
}
