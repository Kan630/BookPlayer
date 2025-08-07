package com.driot.bookplayer.activities;

import static com.driot.bookplayer.global.Var.PODCASTINDEXORG_SINCE;
import static com.driot.bookplayer.utils.TextOptions.parseMaybeHtml;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
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
import com.driot.bookplayer.db.Episode;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.PodcastDao;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.objects.DisplayableEpisode;
import com.driot.bookplayer.objects.PlayList;
import com.driot.bookplayer.objects.PodcastEpisode;
import com.driot.bookplayer.objects.PodcastFeed;
import com.driot.bookplayer.helpers.AnalyticsHelper;
import com.driot.bookplayer.utils.NetworkUtils;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.PodcastHelper;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.util.List;

public class PodcastEpisodeActivity extends LoggingActivity {

    private TextView tvTitle, tvDescription;
    private ImageView ivCover;
    private RecyclerView recyclerEpisodes;
    private ProgressBar progressBar;
    private PodcastEpisodeRVAdapter adapter;

    private Podcast podcast;
    private PodcastFeed podcastFeed;

    private ImageButton btnFavorite, btnAutoDownload;
    private TextView labelFavorite, labelAutoDownload, labelAutoDelete;
    private PodcastDao podcastDao;

    private PodcastEpisodeViewModel podcastEpisodeViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_podcast_detail);

        tvTitle = findViewById(R.id.tvPodcastTitle);
        tvDescription = findViewById(R.id.tvPodcastDescription);
        ivCover = findViewById(R.id.ivPodcastCover);
        recyclerEpisodes = findViewById(R.id.recyclerEpisodes);
        progressBar = findViewById(R.id.progressBarEpisodes);

        btnFavorite = findViewById(R.id.btnFavorite);
        btnAutoDownload = findViewById(R.id.btnAutoDownload);
        labelFavorite = findViewById(R.id.labelFavorite);
        labelAutoDownload = findViewById(R.id.labelAutoDownload);
        labelAutoDelete = findViewById(R.id.labelAutoDelete);

        podcastDao = AppDatabase.getDatabase(this).PodcastDao();

        podcast = getIntent().getParcelableExtra("podcast"); // from Favorites

        if (podcast == null) {
            myLogEE(null,"podcast == null");
            return;
        }
        podcastFeed = new PodcastFeed(
                  podcast.feedId
                , podcast.title
                , podcast.image
                , podcast.description
        );

        podcastEpisodeViewModel = new ViewModelProvider(this).get(PodcastEpisodeViewModel.class);
        podcastEpisodeViewModel.getPodcastLiveByFeedId(podcastFeed.id).observe(this, updatedPodcast -> {
            if (updatedPodcast != null) {
                this.podcast = updatedPodcast;
                // update UI here if needed
            }
        });

        recyclerEpisodes.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PodcastEpisodeRVAdapter(this, podcast, podcastFeed, podcastEpisodeViewModel);
        recyclerEpisodes.setAdapter(adapter);

        labelFavorite.setVisibility(View.GONE);
        labelAutoDownload.setVisibility(View.GONE);
        labelAutoDelete.setVisibility(View.GONE);//not yet implemented
        findViewById(R.id.btnAutoDelete).setVisibility(View.GONE);//not yet implemented

        boolean isFavorite = podcast != null && podcast.isFavorite;
        boolean isAutoDownload = podcast != null && podcast.autoDownload;

        updateFavoriteIconColor(isFavorite);
        updateAutoDownloadIconColor(isAutoDownload);
        btnAutoDownload.setVisibility(isFavorite ? View.VISIBLE : View.GONE);

        if (Pref.shouldAnimateButtons(Pref.AnimatedButton.FAVORITE)) {
            animateAttention(findViewById(R.id.btnFavorite), findViewById(R.id.labelFavorite), getString(R.string.Add_to_favorite), findViewById(R.id.ivPodcastCover));
            animateAttention(findViewById(R.id.btnAutoDownload), findViewById(R.id.labelAutoDownload), getString(R.string.Auto_Download_episodes), findViewById(R.id.ivPodcastCover));
            //animateAttention(findViewById(R.id.btnAutoDelete), findViewById(R.id.labelAutoDelete), getString(R.string.Auto_Delete_episodes), findViewById(R.id.ivPodcastCover));//not yet implemented
        }

        ivCover.setOnClickListener(view -> {
            myLogI("---- USER CLICK IMAGE ----");
            goToPlaySection();
        });


        tvTitle.setText(podcastFeed.title);
        tvDescription.setText(parseMaybeHtml(podcastFeed.description));
        Glide.with(ivCover.getContext()).load(podcastFeed.image).into(ivCover);

        if (podcastFeed.id == -1) {
            myToastE("Error loading episodes. ID=-1");
        } else {
            fetchEpisodes();
        }

        btnFavorite.setOnClickListener(v -> toggleFavorite());
        btnAutoDownload.setOnClickListener(v -> toggleAutoDownload());

        int maxHeightPx = (int) (Resources.getSystem().getDisplayMetrics().heightPixels * 0.1);
        tvDescription.setMaxHeight(maxHeightPx);

        tvDescription.setOnClickListener(v -> {
            ViewHelper.showAlterDialogToDisplayText(this, podcastFeed.description, getString(R.string.Podcast_description));
        });
    }

    private void toggleFavorite() {
        myLogI("--- USER CLICKS set FAVORITE");
        Pref.stopAnimateButtons(Pref.AnimatedButton.FAVORITE);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Podcast podcast = podcastDao.getPodcastByFeedId(podcastFeed.id);

            if (podcast == null) {
                PodcastHelper.addPodcastToDB(this, podcastFeed);
                podcast = AppDatabase.getDatabase(this).PodcastDao().getPodcastByFeedId(podcastFeed.id);
            }

            podcast.isFavorite = !podcast.isFavorite;
            if (!podcast.isFavorite) {
                podcast.autoDownload = false; // reset autoDownload if unfavorited
            } else {
                myToast(getString(R.string.podcast_favorite_add));
            }
            podcastDao.update(podcast);

            boolean favoriteState = podcast.isFavorite;
            boolean autoDownloadState = podcast.autoDownload;

            runOnUiThread(() -> {
                updateFavoriteIconColor(favoriteState);
                updateAutoDownloadIconColor(autoDownloadState);
                btnAutoDownload.setVisibility(favoriteState ? View.VISIBLE : View.GONE);
            });
            ImageHelper.processPendingImages(this);
            AnalyticsHelper.tellAnalyticsPodcastFavorite(this, podcast.title, podcast.language);
        });
    }

    private void toggleAutoDownload() {
        myLogI("--- USER CLICKS set AUTO DOWNLOAD");
        Pref.stopAnimateButtons(Pref.AnimatedButton.AUTO_DOWNLOAD);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Podcast podcast = podcastDao.getPodcastByFeedId(podcastFeed.id);
            podcast.autoDownload = !podcast.autoDownload;
            podcastDao.update(podcast);
            if (podcast.autoDownload) {
                myLog("---> On");
                myToast(getString(R.string.podcast_autodownload_add));
                downloadAllEpisodesToFolder(podcast, PODCASTINDEXORG_SINCE);
            }
            runOnUiThread(() -> {
                updateAutoDownloadIconColor(podcast.autoDownload);
            });
        });
    }


    private void updateFavoriteIconColor(boolean isOn) {
        int colorResId = isOn ? android.R.color.holo_red_light : R.color.gray_500;
        btnFavorite.setColorFilter(ContextCompat.getColor(this, colorResId), PorterDuff.Mode.SRC_IN);
    }

    private void updateAutoDownloadIconColor(boolean isOn) {
        int colorResId = isOn ? R.color.green_300 : R.color.gray_500;
        btnAutoDownload.setColorFilter(ContextCompat.getColor(this, colorResId), PorterDuff.Mode.SRC_IN);
    }

    private void fetchEpisodes() {
        progressBar.setVisibility(View.VISIBLE);

        PodcastHelper.getEpisodesByFeedId(podcast.feedId, PODCASTINDEXORG_SINCE, Var.PODCASTINDEXORG_API_MAX_RESULTS_FOR_EPISODES, new PodcastHelper.EpisodeCallback() {
            @Override
            public void onSuccess(List<PodcastEpisode> apiEpisodes) {
                podcastEpisodeViewModel.insertEpisodes(apiEpisodes, podcast.feedId); // save new ones

                AppDatabase.databaseReadExecutor.execute(() -> {
                    List<Episode> dbEpisodes = podcastEpisodeViewModel.getEpisodesForPodcastSync(podcast.getId());

                    List<DisplayableEpisode> fullList = DisplayableEpisode.mergeDisplayableEpisodes(apiEpisodes, dbEpisodes);
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        adapter.setItems(fullList);
                        adapter.notifyDataSetChanged();
                    });
                });
            }

            @Override
            public void onError(Exception e) {
                // fallback to DB-only
                AppDatabase.databaseReadExecutor.execute(() -> {
                    List<Episode> dbEpisodes = podcastEpisodeViewModel.getEpisodesForPodcastSync(podcast.getId());
                    List<DisplayableEpisode> fallbackList = DisplayableEpisode.fromEpisodeList(dbEpisodes);

                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        adapter.setItems(fallbackList);
                        adapter.notifyDataSetChanged();
                        tvDescription.setTextColor(getColor(R.color.orange_500));
                        tvDescription.setText(getString(R.string.podcast_api_unavailable_fallback));
                    });
                });
            /*
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvDescription.setTextColor(getColor(R.color.orange_500));
                    if (NetworkUtils.isUnknownHost(e)) {
                        tvDescription.setText(getString(R.string.no_internet_connection));
                    } else {
                        myLogEE(e,"Error loading episodes for " + podcastFeed.title + " - podcastFeed.id = " + podcastFeed.id );
                        tvDescription.setText("Error loading episodes\n" + e.getMessage());
                    }
                });

             */
            }
        });
    }

    private void downloadAllEpisodesToFolder(Podcast podcast, long since) {
        PodcastHelper.checkForNewEpisodesToAutoDownloadForPodcast(this, podcast, since);
    }

    private void startEpisodeDownload(String url, String outputPath) {
        myLog("Starting download: " + url + " to " + outputPath);
        //DownloadService.startDownload(this, url, outputPath); // assuming you already have this
    }
    private void animateAttention(ImageView ivIcon, TextView tvIconLabel, String labelText, ImageView podcastImage) {
        float MAX_SIZE = 1.8f;
        int ANIM_TIME = 2000;
        int HALF_TIME = ANIM_TIME / 2;
        int fromColor = ContextCompat.getColor(this, R.color.gray_500);
        int toColor = ContextCompat.getColor(this, android.R.color.holo_blue_bright);
        int ivIConVisibility = ivIcon.getVisibility();

        // Set initial states
        tvIconLabel.setText(labelText);
        tvIconLabel.setAlpha(0f);
        tvIconLabel.setVisibility(View.VISIBLE);
        ivIcon.setVisibility(View.VISIBLE);
        podcastImage.setAlpha(0.2f);
        ivIcon.setColorFilter(fromColor, PorterDuff.Mode.SRC_IN);

        // --- Label fade in and out ---
        ObjectAnimator labelFadeIn = ObjectAnimator.ofFloat(tvIconLabel, "alpha", 0f, 1f);
        labelFadeIn.setDuration(HALF_TIME);

        ObjectAnimator labelFadeOut = ObjectAnimator.ofFloat(tvIconLabel, "alpha", 1f, 0f);
        labelFadeOut.setDuration(HALF_TIME);

        // --- Icon scale and color ---
        ObjectAnimator colorToHighlight = ObjectAnimator.ofArgb(ivIcon, "colorFilter", fromColor, toColor);
        ObjectAnimator colorBackToGray = ObjectAnimator.ofArgb(ivIcon, "colorFilter", toColor, fromColor);
        ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(ivIcon, "scaleX", 1f, MAX_SIZE);
        ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(ivIcon, "scaleY", 1f, MAX_SIZE);
        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(ivIcon, "scaleX", MAX_SIZE, 1f);
        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(ivIcon, "scaleY", MAX_SIZE, 1f);

        AnimatorSet scaleUp = new AnimatorSet();
        scaleUp.playTogether(scaleUpX, scaleUpY, colorToHighlight, labelFadeIn);

        AnimatorSet scaleDown = new AnimatorSet();
        scaleDown.playTogether(scaleDownX, scaleDownY, colorBackToGray, labelFadeOut);

        AnimatorSet fullAnimation = new AnimatorSet();
        fullAnimation.playSequentially(scaleUp, scaleDown);
        fullAnimation.setDuration(ANIM_TIME);

        fullAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                podcastImage.setAlpha(1f); // restore alpha
                ivIcon.setVisibility(ivIConVisibility);

                AppDatabase.databaseWriteExecutor.execute(() -> {
                    Podcast podcast = podcastDao.getPodcastByFeedId(podcastFeed.id);
                    if (podcast != null) {
                        boolean isFav = podcast.isFavorite;
                        boolean isAuto = podcast.autoDownload;

                        runOnUiThread(() -> {
                            updateFavoriteIconColor(isFav);
                            updateAutoDownloadIconColor(isAuto);
                        });
                    }
                });
            }
        });

        fullAnimation.start();
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
                                    this.startActivity(new Intent(this, ZikFileActivity.class).putExtra("folder", folder));
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
