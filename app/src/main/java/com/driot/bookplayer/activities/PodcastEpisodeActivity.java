package com.driot.bookplayer.activities;

import static com.driot.bookplayer.global.Pref.shouldAnimateButtons;
import static com.driot.bookplayer.global.Pref.stopAnimateButtons;
import static com.driot.bookplayer.global.Var.PODCASTINDEXORG_SINCE_DEBUG;
import static com.driot.bookplayer.utils.ImageHelper.processPendingImages;
import static com.driot.bookplayer.utils.PodcastHelper.checkForNewEpisodesToAutoDownload;
import static com.driot.bookplayer.utils.TextOptions.parseMaybeHtml;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.PodcastEpisodeRVAdapter;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.PodcastDao;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.objects.PlayList;
import com.driot.bookplayer.objects.PodcastEpisode;
import com.driot.bookplayer.objects.PodcastFeed;
import com.driot.bookplayer.objects.ViewHelper;
import com.driot.bookplayer.utils.PodcastHelper;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.util.List;

public class PodcastEpisodeActivity extends LoggingActivity {

    private TextView tvTitle, tvDescription;
    private ImageView ivCover;
    private RecyclerView recyclerEpisodes;
    private ProgressBar progressBar;
    private PodcastEpisodeRVAdapter adapter;

    private PodcastFeed podcastFeed;
    private Podcast podcast;

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

        ivCover.setOnClickListener(view -> {
            myLogI("---- USER CLICK IMAGE ----");
            goToPlaySection();
        });

        podcastDao = AppDatabase.getDatabase(this).PodcastDao();

        podcast = getIntent().getParcelableExtra("podcast"); // from Favorites
        podcastFeed = getIntent().getParcelableExtra("podcastFeed"); // from Search

/*
        if (podcast == null) { //comes from the SearchResult, try to see if in DB
            AppDatabase.databaseWriteExecutor.execute(() -> {
                podcast = podcastDao.getPodcastBypodcastFeed.id(podcastFeed.id);  // MAYBE too slow and not really needed....
            }
        }
 */

        if (podcastFeed == null) {
            if (podcast == null) {
                myLogEE(null,"podcast and podcastFeed are null");
                return;
            }
            podcastFeed = new PodcastFeed(
                      podcast.feedId
                    , podcast.title
                    , podcast.image
                    , podcast.description
            );
        }

        recyclerEpisodes.setLayoutManager(new LinearLayoutManager(this));
        PodcastEpisodeViewModel viewModel = new ViewModelProvider(this).get(PodcastEpisodeViewModel.class);
        adapter = new PodcastEpisodeRVAdapter(this, podcast, podcastFeed, viewModel);
        recyclerEpisodes.setAdapter(adapter);

        tvTitle.setText(podcastFeed.title);
        tvDescription.setText(parseMaybeHtml(podcastFeed.description));
        Glide.with(this).load(podcastFeed.image).into(ivCover);

        if (podcastFeed.id == -1) {
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
            ViewHelper.showAlterDialogToDisplayText(this, podcastFeed.description, getString(R.string.Podcast_description));
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
            Podcast podcast = podcastDao.getPodcastByFeedId(podcastFeed.id);

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
                processPendingImages(this);
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
            Podcast podcast = podcastDao.getPodcastByFeedId(podcastFeed.id);

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

        PodcastHelper.getEpisodesByFeedId(podcastFeed.id, PODCASTINDEXORG_SINCE_DEBUG, new PodcastHelper.EpisodeCallback() {
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
                    myToastEE(e,"Error loading episodes for " + podcastFeed.title + " - podcastFeed.id = " + podcastFeed.id );
                    tvDescription.setText("Error loading episodes\n" + e.getMessage());
                });
            }
        });
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
                    Podcast podcast = podcastDao.getPodcastByFeedId(podcastFeed.id);
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
        podcast.feedId = podcastFeed.id;
        podcast.title = podcastFeed.title;
        podcast.image = podcastFeed.image;
        podcast.imageOriginalUrl = podcastFeed.image;
        podcast.description = podcastFeed.description;
        podcast.isFavorite = true;
    }

    private void goToPlaySection() {
        if (podcast == null) {
            AppDatabase.databaseReadExecutor.execute(() -> {
                podcast = AppDatabase.getDatabase(this).PodcastDao().getPodcastByFeedId(podcastFeed.id);
                if (podcast == null) {
                    myLog("podcast == null");
                } else {
                    goToPlaySection2();
                }
            });
        } else {
            goToPlaySection2();
        }
    }
    private void goToPlaySection2() {
        if (podcast != null) {
            if (podcast.idFolder != null && podcast.idFolder > 0) {
                new Thread(() -> {
                    try {
                        Folder folder = AppDatabase.getDatabase(this).FolderDao().getById(podcast.idFolder);
                        if (folder != null) {
                            try {
                                List<ZikFile> zikFilesList = AppDatabase.getDatabase(this).ZikFileDao().getZikFiles(podcast.idFolder);
                                myLogI("nb ZikFiles in that Book : " + zikFilesList.size() + " - [" + folder.getName() + "]");
                                PlayList.create(this, zikFilesList);
                                if (zikFilesList.size() > 1) {
                                    this.startActivity(new Intent(this, ZikFileActivity.class)
                                            .putExtra("FolderId", folder.getId())
                                            .putExtra("FolderName", folder.getName())
                                    );
                                } else if (zikFilesList.size() == 1) {
                                    PlayList.getInstance().setNumZikFile(0);
                                    this.startActivity(new Intent(this, PlayActivity.class).putExtra("ZikFile", zikFilesList.get(0)));
                                } else {
                                    myLogE("no ZikFiles in that folder !");
                                    myToastE(getString(R.string.ErrorCouldNotLoadAudios_emptyfolder));
                                }
                            } catch (Exception e) {
                                myLogEE(e, "error getting nb of ZikFiles");
                                myToastE(getString(R.string.ErrorCouldNotLoadAudios));
                            }
                        }
                    } catch (Exception e) {
                        myLogEE(e, "error getting Folder");
                        myToastE(getString(R.string.ErrorCouldNotLoadAudios));
                    }
                }).start();
            } else {
                myLog("Podcast exist in DB but no Folder exists (nothing downloaded yet)");
            }
        } else {
            myLogE("Podcast == null");
        }
    }
}
