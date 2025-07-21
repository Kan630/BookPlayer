package com.driot.bookplayer.activities;

import static com.driot.bookplayer.utils.TextOptions.parseMaybeHtml;

import android.app.AlertDialog;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.PodcastEpisodeRVAdapter;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.PodcastDao;
import com.driot.bookplayer.objects.PodcastEpisode;
import com.driot.bookplayer.utils.MaxHeightScrollView;
import com.driot.bookplayer.utils.PodcastIndexHelper;
import com.driot.bookplayer.utils.TextOptions;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.util.List;

public class PodcastDetailActivity extends LoggingActivity {

    private TextView tvTitle, tvDescription;
    private boolean isDescriptionExpanded = false;
    private ImageView ivCover;
    private RecyclerView recyclerEpisodes;
    private ProgressBar progressBar;
    private PodcastEpisodeRVAdapter adapter;

    private String PodcastTitle;
    private long feedId;

    private ImageButton btnFavorite, btnAutoDownload;
    private PodcastDao podcastDao;

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

        feedId = getIntent().getLongExtra("feedId", -1);
        PodcastTitle = getIntent().getStringExtra("title");
        String image = getIntent().getStringExtra("image");
        String description = getIntent().getStringExtra("description");

        tvTitle.setText(PodcastTitle);
        tvDescription.setText(parseMaybeHtml(description));
        Glide.with(this).load(image).into(ivCover);

        if (feedId == -1) {
            myToastE("Error loading episodes. ID=-1");
        } else {
            fetchEpisodes();
        }

        podcastDao = AppDatabase.getDatabase(this).PodcastDao();

        btnFavorite = findViewById(R.id.btnFavorite);
        btnAutoDownload = findViewById(R.id.btnAutoDownload);

        loadInitialState();

        btnFavorite.setOnClickListener(v -> toggleFavorite());
        btnAutoDownload.setOnClickListener(v -> toggleAutoDownload());

        int maxHeightPx = (int) (Resources.getSystem().getDisplayMetrics().heightPixels * 0.1);
        tvDescription.setMaxHeight(maxHeightPx);

        tvDescription.setOnClickListener(v -> {
            showFullDescription(description);
        });
    }

    private void loadInitialState() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Podcast podcast = podcastDao.getPodcastById(feedId);
            runOnUiThread(() -> {
                boolean isFavorite = podcast != null && podcast.isFavorite;
                boolean isAutoDownload = podcast != null && podcast.autoDownload;

                updateFavoriteIcon(isFavorite);
                updateAutoDownloadIcon(isAutoDownload);
                btnAutoDownload.setVisibility(isFavorite ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void toggleFavorite() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Podcast podcast = podcastDao.getPodcastById(feedId);

            if (podcast == null) {
                podcast = new Podcast();
                podcast.feedId = feedId;
                podcast.isFavorite = true;
                podcast.autoDownload = false;
                podcastDao.insert(podcast);
            } else {
                podcast.isFavorite = !podcast.isFavorite;
                if (!podcast.isFavorite) {
                    podcast.autoDownload = false; // reset autoDownload if unfavorited
                } else {
                    myToast(getString(R.string.podcast_favorite_add));
                }
                podcastDao.update(podcast);
            }

            boolean newState = podcast.isFavorite;
            boolean autoDownloadState = podcast.autoDownload;

            runOnUiThread(() -> {
                updateFavoriteIcon(newState);
                updateAutoDownloadIcon(autoDownloadState);
                btnAutoDownload.setVisibility(newState ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void toggleAutoDownload() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Podcast podcast = podcastDao.getPodcastById(feedId);

            if (podcast == null) {
                podcast = new Podcast();
                podcast.feedId = feedId;
                podcast.isFavorite = true; // autoDownload implies favorite
                podcast.autoDownload = true;
                podcastDao.insert(podcast);
            } else {
                podcast.isFavorite = true; // enforce favorite
                podcast.autoDownload = !podcast.autoDownload;
                podcastDao.update(podcast);
            }
            if (podcast.autoDownload) {
                myToast(getString(R.string.podcast_autodownload_add));
            }

            boolean isFavoriteNow = podcast.isFavorite;
            boolean newAutoDownload = podcast.autoDownload;

            runOnUiThread(() -> {
                updateFavoriteIcon(isFavoriteNow);
                updateAutoDownloadIcon(newAutoDownload);
            });
        });
    }

    private void updateFavoriteIcon(boolean isFav) {
        btnFavorite.setImageResource(isFav ? R.drawable.ic_favorite : R.drawable.ic_favorite_no);
    }

    private void updateAutoDownloadIcon(boolean isAuto) {
        btnAutoDownload.setImageResource(isAuto ? R.drawable.ic_download_done_24dp : R.drawable.ic_download_24dp);
    }

    private void fetchEpisodes() {
        progressBar.setVisibility(View.VISIBLE);

        PodcastIndexHelper.getEpisodesByFeedId(feedId, new PodcastIndexHelper.EpisodeCallback() {
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
                    myToastEE(e,"Error loading episodes for " + PodcastTitle + " - feedID = " + feedId );
                    tvDescription.setText("Error loading episodes\n" + e.getMessage());
                });
            }
        });
    }

    private void showFullDescription(String fullText) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Description");
        builder.setMessage(parseMaybeHtml(fullText));
        builder.setPositiveButton("Close", null);
        builder.show();
    }
}
