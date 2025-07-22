package com.driot.bookplayer.activities;

import static com.driot.bookplayer.global.Pref.shouldAnimateButtons;
import static com.driot.bookplayer.global.Pref.stopAnimateButtons;
import static com.driot.bookplayer.global.Var.PODCASTINDEXORG_SINCE_DEBUG;
import static com.driot.bookplayer.utils.PodcastIndexHelper.checkForNewEpisodesToAutoDownload;
import static com.driot.bookplayer.utils.TextOptions.parseMaybeHtml;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.PodcastEpisodeRVAdapter;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.PodcastDao;
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

    private Podcast podcast;
    private String title;
    private long feedId;
    private String image;
    private String description;

    private ImageButton btnFavorite, btnAutoDownload;
    private PodcastDao podcastDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_podcast_detail);

        if (shouldAnimateButtons()) {
            animateAttention(findViewById(R.id.btnFavorite));
            animateAttention(findViewById(R.id.btnAutoDownload));
        }

        tvTitle = findViewById(R.id.tvPodcastTitle);
        tvDescription = findViewById(R.id.tvPodcastDescription);
        ivCover = findViewById(R.id.ivPodcastCover);
        recyclerEpisodes = findViewById(R.id.recyclerEpisodes);
        progressBar = findViewById(R.id.progressBarEpisodes);

        podcastDao = AppDatabase.getDatabase(this).PodcastDao();

        podcast = getIntent().getParcelableExtra("podcast");

/*
        if (podcast == null) { //comes from the SearchResult, try to see if in DB
            AppDatabase.databaseWriteExecutor.execute(() -> {
                podcast = podcastDao.getPodcastByFeedId(feedId);  // MAYBE too slow and not really needed....
            }
        }
 */
        if (podcast == null) {
            //not in DB, we just have a PodcastFeed, not a Room Podcast
            //we will only insert in DB if user clicks favorites
            feedId = getIntent().getLongExtra("feedId", -1);
            title = getIntent().getStringExtra("title");
            image = getIntent().getStringExtra("image");
            description = getIntent().getStringExtra("description");
        } else { //already in DB (is a favorite)
            feedId = podcast.feedId;
            title = podcast.title;
            image = podcast.image;
            description = podcast.description;
        }

        recyclerEpisodes.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PodcastEpisodeRVAdapter(this, title);
        recyclerEpisodes.setAdapter(adapter);

        tvTitle.setText(title);
        tvDescription.setText(parseMaybeHtml(description));
        Glide.with(this).load(image).into(ivCover);

        if (feedId == -1) {
            myToastE("Error loading episodes. ID=-1");
        } else {
            fetchEpisodes();
        }

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
        myLog("--- USER CLICKS FAVORITE");
        stopAnimateButtons();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Podcast podcast = podcastDao.getPodcastByFeedId(feedId);

            if (podcast == null) {
                podcast = new Podcast();
                populatePodCast(podcast);
                podcast.autoDownload = false;
                podcastDao.insert(podcast);
            } else {
                podcast.isFavorite = !podcast.isFavorite;
                if (!podcast.isFavorite) {
                    podcast.autoDownload = false; // reset autoDownload if unfavorited
                } else {
                    myLog("---> On");
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
        myLog("--- USER CLICKS AUTO DOWNLOAD");
        stopAnimateButtons();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Podcast podcast = podcastDao.getPodcastByFeedId(feedId);

            if (podcast == null) {
                podcast = new Podcast();
                populatePodCast(podcast);
                podcast.autoDownload = true;
                podcastDao.insert(podcast);
            } else {
                podcast.isFavorite = true; // enforce favorite
                podcast.autoDownload = !podcast.autoDownload;
                podcastDao.update(podcast);
            }
            if (podcast.autoDownload) {
                myLog("---> On");
                myToast(getString(R.string.podcast_autodownload_add));
                downloadAllEpisodesToFolder(podcast, PODCASTINDEXORG_SINCE_DEBUG);
            }

            boolean isFavoriteNow = podcast.isFavorite;
            boolean newAutoDownload = podcast.autoDownload;

            runOnUiThread(() -> {
                updateFavoriteIcon(isFavoriteNow);
                updateAutoDownloadIcon(newAutoDownload);
            });
        });
    }

    private void updateFavoriteIcon(boolean isOn) {
        int colorResId = isOn ? R.color.red_500 : R.color.gray_500;
        btnFavorite.setColorFilter(ContextCompat.getColor(this, colorResId), PorterDuff.Mode.SRC_IN);
    }

    private void updateAutoDownloadIcon(boolean isOn) {
        int colorResId = isOn ? R.color.green_500 : R.color.gray_500;
        btnAutoDownload.setColorFilter(ContextCompat.getColor(this, colorResId), PorterDuff.Mode.SRC_IN);
    }

    private void fetchEpisodes() {
        progressBar.setVisibility(View.VISIBLE);

        PodcastIndexHelper.getEpisodesByFeedId(feedId, PODCASTINDEXORG_SINCE_DEBUG, new PodcastIndexHelper.EpisodeCallback() {
            @Override
            public void onSuccess(List<PodcastEpisode> episodes) {
                for (PodcastEpisode episode : episodes) {
                    episode.podcast = podcast;
                }
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
                    myToastEE(e,"Error loading episodes for " + title + " - feedID = " + feedId );
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

    private void downloadAllEpisodesToFolder(Podcast podcast, long since) {
        checkForNewEpisodesToAutoDownload(this, podcast, since);
    }

    private void startEpisodeDownload(String url, String outputPath) {
        myLog("Starting download: " + url + " to " + outputPath);
        //DownloadService.startDownload(this, url, outputPath); // assuming you already have this
    }

    private void animateAttention(ImageView imageView) {
        float MAX_SIZE =  1.8f;
        int ANIM_TIME = 600;
        int fromColor = ContextCompat.getColor(this, R.color.gray_500);   // original
        int toColor = ContextCompat.getColor(this, R.color.orange_500);  // highlight

        // Apply the initial color filter (optional, if not already set)
        imageView.setColorFilter(fromColor, PorterDuff.Mode.SRC_IN);

        // Color filter animation
        ObjectAnimator colorToHighlight = ObjectAnimator.ofArgb(imageView, "colorFilter", fromColor, toColor);
        ObjectAnimator colorBackToGray = ObjectAnimator.ofArgb(imageView, "colorFilter", toColor, fromColor);

        // Scale animation
        ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(imageView, "scaleX", 1f, MAX_SIZE);
        ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(imageView, "scaleY", 1f, MAX_SIZE);
        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(imageView, "scaleX", MAX_SIZE, 1f);
        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(imageView, "scaleY", MAX_SIZE, 1f);

        // Group animations
        AnimatorSet scaleUp = new AnimatorSet();
        scaleUp.playTogether(scaleUpX, scaleUpY, colorToHighlight);

        AnimatorSet scaleDown = new AnimatorSet();
        scaleDown.playTogether(scaleDownX, scaleDownY, colorBackToGray);

        AnimatorSet flicker = new AnimatorSet();
        flicker.playSequentially(scaleUp, scaleDown);
        flicker.setDuration(ANIM_TIME);

        // reset colors after (should be useless as as soon as the user clicks, there should never be more animation...
        flicker.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    Podcast podcast = podcastDao.getPodcastByFeedId(feedId);
                    if (podcast != null) {
                        boolean isFav = podcast.isFavorite;
                        boolean isAuto = podcast.autoDownload;

                        runOnUiThread(() -> {
                            updateFavoriteIcon(isFav);
                            updateAutoDownloadIcon(isAuto);
                        });
                    }
                });
            }
        });

        flicker.start();
    }

    private void populatePodCast(Podcast podcast) {
        podcast.source = "podcastindex.org";
        podcast.feedId = feedId;
        podcast.title = title;
        podcast.image = image;
        podcast.description = description;
        podcast.isFavorite = true;
        podcast.date_added = System.currentTimeMillis();
    }

}
