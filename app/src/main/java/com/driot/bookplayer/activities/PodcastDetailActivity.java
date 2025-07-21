package com.driot.bookplayer.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.PodcastEpisodeRVAdapter;
import com.driot.bookplayer.objects.PodcastEpisode;
import com.driot.bookplayer.utils.PodcastIndexHelper;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.util.List;

public class PodcastDetailActivity extends LoggingActivity {

    private TextView tvTitle, tvDescription;
    private ImageView ivCover;
    private RecyclerView recyclerEpisodes;
    private ProgressBar progressBar;
    private PodcastEpisodeRVAdapter adapter;

    private String PodcastTitle;
    private long PodcastFeedId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_podcast_detail);

        tvTitle = findViewById(R.id.tvPodcastTitle);
        tvDescription = findViewById(R.id.tvPodcastDescription);
        ivCover = findViewById(R.id.ivPodcastCover);
        recyclerEpisodes = findViewById(R.id.recyclerEpisodes);
        progressBar = findViewById(R.id.progressBarEpisodes);

        recyclerEpisodes.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PodcastEpisodeRVAdapter(this);
        recyclerEpisodes.setAdapter(adapter);

        PodcastFeedId = getIntent().getLongExtra("feedId", -1);
        PodcastTitle = getIntent().getStringExtra("title");
        String image = getIntent().getStringExtra("image");

        tvTitle.setText(PodcastTitle);
        Glide.with(this).load(image).into(ivCover);

        if (PodcastFeedId == -1) {
            myToastE("Error loading episodes. ID=-1");
        } else {
            fetchEpisodes();
        }
    }

    private void fetchEpisodes() {
        progressBar.setVisibility(View.VISIBLE);

        PodcastIndexHelper.getEpisodesByFeedId(PodcastFeedId, new PodcastIndexHelper.EpisodeCallback() {
            @Override
            public void onSuccess(List<PodcastEpisode> episodes) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    adapter.setItems(episodes);
                    adapter.notifyDataSetChanged();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    myToastEE(e,"Error loading episodes for " + PodcastTitle + " - feedID = " + PodcastFeedId );
                    tvDescription.setText("Error loading episodes\n" + e.getMessage());
                });
            }
        });
    }
}
