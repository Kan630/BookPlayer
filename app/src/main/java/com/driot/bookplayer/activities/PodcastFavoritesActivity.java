package com.driot.bookplayer.activities;

import static com.driot.bookplayer.global.Var.PODCASTINDEXORG_SINCE_DEBUG;
import static com.driot.bookplayer.utils.PodcastIndexHelper.checkForNewEpisodesToAutoDownload;

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
import com.driot.bookplayer.adapter.PodcastFavoritesRVAdapter;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.objects.PodcastFeed;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PodcastFavoritesActivity extends LoggingActivity {

    private PodcastSearchResultsViewModel viewModel;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyMessage, tvSearchTerms, tvLanguage, tvResultsCount;
    private PodcastFavoritesRVAdapter adapter;

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
        viewModel = new ViewModelProvider(this).get(PodcastSearchResultsViewModel.class);

        viewModel.getShouldFinish().observe(this, shouldFinish -> {
            if (shouldFinish != null && shouldFinish) finish();
        });

        adapter = new PodcastFavoritesRVAdapter(
                item -> {
                    Intent intent = new Intent(this, PodcastDetailActivity.class);
                    intent.putExtra("podcast", item);
                    startActivity(intent);
                },
                (item, newState) -> {
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        AppDatabase.getDatabase(this)
                                .PodcastDao()
                                .updateAutoDownloadStatus_fromFeedId(item.feedId, newState);
                        checkForNewEpisodesToAutoDownload(this, item, PODCASTINDEXORG_SINCE_DEBUG);
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
                displayResults(favorites);
            }
        });
    }

    private void displayResults(List<Podcast> podcastList) {
        adapter.setItems(podcastList);
        adapter.notifyDataSetChanged();
        tvSearchTerms.setText(podcastList.size() + " " + getString(R.string.Favorites));
        tvLanguage.setVisibility(View.GONE);
        tvResultsCount.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        emptyMessage.setVisibility(podcastList.isEmpty() ? View.VISIBLE : View.GONE);
    }



    }

