package com.driot.bookplayer.activities;

import static com.driot.bookplayer.global.Var.PODCASTINDEXORG_API_MAX_RESULTS;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
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
    private TextView emptyMessage, tvSearchTerms, tvLanguage, tvResultsCount;
    private PodcastSearchResultsRVAdapter adapter;

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
        viewModel = new ViewModelProvider(this).get(PodcastSearchResultsViewModel.class);

        viewModel.getShouldFinish().observe(this, shouldFinish -> {
            if (shouldFinish != null && shouldFinish) finish();
        });

        adapter = new PodcastSearchResultsRVAdapter(item -> {
            Intent intent = new Intent(this, PodcastEpisodeActivity.class);
            intent.putExtra("feedId", item.id);
            intent.putExtra("title", item.title);
            intent.putExtra("image", item.image);
            intent.putExtra("description", item.description);
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

        tvSearchTerms.setText(getString(R.string.Search_2pt) + (query.isEmpty() ? getString(R.string.search_nothing_specified_so_trending) : query));
        tvLanguage.setText(getString(R.string.Language_2pt) + lang);
        tvResultsCount.setText(getString(R.string.Results_2pt) + "...");

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
                    runOnUiThread(() -> {
                        handleSuccess(feeds);
                    });
                }

                @Override
                public void onError(Exception e) {
                    handleError(e);
                }
            });
        } else {
            PodcastHelper.getTrendingPodcasts(lang, PODCASTINDEXORG_API_MAX_RESULTS, new PodcastHelper.Callback() {
                @Override
                public void onSuccess(List<PodcastFeed> feeds) {
                    runOnUiThread(() -> {
                        handleSuccess(feeds);
                    });
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> {
                        handleError(e);
                    });
                }
            });

            }
        }

    private void handleSuccess(List<PodcastFeed> feeds) {
        progressBar.setVisibility(View.GONE);
        if (feeds == null || feeds.isEmpty()) {
            emptyMessage.setText(getString(R.string.podcast_no_results));
            emptyMessage.setVisibility(View.VISIBLE);
            emptyMessage.setTextColor(getColor(R.color.orange_500));
            tvResultsCount.setText(getString(R.string.podcast_nb_of_podcast_found) + ": 0");
        } else {
            viewModel.setResults(feeds);
            showResults(feeds);
            tvResultsCount.setText(getString(R.string.podcast_nb_of_podcast_found) + ": " + feeds.size());
        }
    }

    private void handleError(Exception e) {
        progressBar.setVisibility(View.GONE);
        emptyMessage.setVisibility(View.VISIBLE);
        emptyMessage.setText("Error: " + e.getMessage());
        emptyMessage.setTextColor(getColor(R.color.red_500));
        tvResultsCount.setText("Error occurred");
    }

    private void showResults(List<PodcastFeed> feeds) {
        adapter.setItems(feeds);
    }





}

