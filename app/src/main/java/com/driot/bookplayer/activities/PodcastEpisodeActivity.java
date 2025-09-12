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
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;

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
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.PodcastHelper;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.io.File;
import java.util.List;

public class PodcastEpisodeActivity extends LoggingActivity  implements PodcastEpisodeRVAdapter.EpisodeClickHandler {

    private TextView tvTitle, tvDescription, tvStats;
    private ImageView ivCover;
    private RecyclerView recyclerEpisodes;
    private ProgressBar progressBar;
    private PodcastEpisodeRVAdapter adapter;

    private Podcast podcast;
    private PodcastFeed podcastFeed;

    private ImageButton btnFavorite, btnAutoDownload, btnRefresh, btnSort, btnPlayPause;
    private TextView labelFavorite, labelAutoDownload, labelAutoDelete;
    private PodcastDao podcastDao;

    private PodcastEpisodeViewModel podcastEpisodeViewModel;

    private boolean sortNewestFirst;

    private ExoPlayer player;
    private boolean switchedToLocal = false;
    private boolean isPlaying = false;
    private boolean downloadWhilePlaying = true;

    private DisplayableEpisode currentEpisode;

    private final java.util.Map<String, androidx.lifecycle.Observer<ZikFile>> pendingSwitchObservers = new java.util.HashMap<>();
    private final java.util.Set<Long> enqueuedEpisodeIds = new java.util.HashSet<>();

    // timing / loader
    private long clickStartMs = 0L;
    private boolean waitingForStart = false;
    private android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable loaderTicker;
    private ProgressBar progressBarPlayer; // small loader near play button

    private View miniPlayer;
    private ImageButton btnMiniPlayPause, btnMiniBack, btnMiniForward;
    private SeekBar seekMini;
    private TextView tvMiniTitle, tvMiniTime;

    private final android.os.Handler miniUi = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable miniTicker;
    private boolean miniUserSeeking = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_podcast_detail);

        tvTitle = findViewById(R.id.tvPodcastTitle);
        tvDescription = findViewById(R.id.tvPodcastDescription);
        tvStats = findViewById(R.id.tvPodcastStat);

        ivCover = findViewById(R.id.ivPodcastCover);
        recyclerEpisodes = findViewById(R.id.rvEpisodes);
        progressBar = findViewById(R.id.progressBarEpisodes);

        btnFavorite = findViewById(R.id.btnFavorite);
        btnAutoDownload = findViewById(R.id.btnAutoDownload);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnSort = findViewById(R.id.btnSort);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        labelFavorite = findViewById(R.id.labelFavorite);
        labelAutoDownload = findViewById(R.id.labelAutoDownload);
        labelAutoDelete = findViewById(R.id.labelAutoDelete);

        miniPlayer = findViewById(R.id.miniPlayer);
        btnMiniPlayPause = findViewById(R.id.btnMiniPlayPause);
        btnMiniBack = findViewById(R.id.btnMiniBack);
        btnMiniForward = findViewById(R.id.btnMiniForward);
        seekMini = findViewById(R.id.seekMini);
        tvMiniTitle = findViewById(R.id.tvMiniTitle);
        tvMiniTime = findViewById(R.id.tvMiniTime);

        podcastDao = AppDatabase.getDatabase(this).PodcastDao();

        podcast = getIntent().getParcelableExtra("podcast");

        if (podcast == null) {
            myLogEE(null,"podcast == null");
            return;
        }

        sortNewestFirst = podcast.sort_newest_top;
        myLogD("Sort newest first: " + sortNewestFirst);

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
        adapter = new PodcastEpisodeRVAdapter(this, podcastFeed, podcastEpisodeViewModel, this);
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
            fetchEpisodes(false);
        }

        btnFavorite.setOnClickListener(v -> toggleFavorite());
        btnAutoDownload.setOnClickListener(v -> toggleAutoDownload());
        btnRefresh.setOnClickListener(v -> {
            myLogI("-------- USER CLICKS REFRESH");
            fetchEpisodes(true);
        });
        btnSort.setOnClickListener(v -> {
            sortNewestFirst = !sortNewestFirst;
            myLogI("-------- USER CLICKS SORT --  sortNewestFirst= " + sortNewestFirst);

            //save
            PodcastHelper.updateSortNewestTop(this, podcast.feedId, sortNewestFirst);

            //reload
            AppDatabase.databaseReadExecutor.execute(() -> {
                List<Episode> dbEpisodes = podcastEpisodeViewModel.getEpisodesFromDB(podcast.getId(), sortNewestFirst);
                List<DisplayableEpisode> sortedList = DisplayableEpisode.fromEpisodeList(dbEpisodes);
                runOnUiThread(() -> {
                    adapter.setItems(sortedList);
                    adapter.notifyDataSetChanged();
                });
            });
        });
        btnPlayPause.setOnClickListener(v -> {
            boolean wasPlaying = player != null && player.isPlaying();
            myLogI("-------- USER CLICKS PLAY/PAUSE  ------------    isPlaying : " + wasPlaying + " => " + !wasPlaying);

            if (player == null) return;

            if (wasPlaying) {
                // Pause current playback
                player.pause();
                isPlaying = false;
                btnPlayPause.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_media_play_24));
                return;
            }

            // Not currently playing:
            // 1) If we already have something loaded (paused), just resume
            if (player.getMediaItemCount() > 0 && currentEpisode != null) {
                player.play();
                isPlaying = true;
                btnPlayPause.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_media_pause_24));
                return;
            }

            // 2) Nothing loaded yet => play top item in the RecyclerView
            LinearLayoutManager lm = (LinearLayoutManager) recyclerEpisodes.getLayoutManager();
            int pos = (lm != null) ? lm.findFirstCompletelyVisibleItemPosition() : RecyclerView.NO_POSITION;
            if (pos == RecyclerView.NO_POSITION) pos = 0;

            DisplayableEpisode top = adapter.getItem(pos);
            if (top == null) {
                myToastE(getString(R.string.ErrorCouldNotLoadAudios_emptyfolder)); // or a generic "No episodes"
                return;
            }
            beginStartupTiming();
            playEpisode(top);
        });


        int maxHeightPx = (int) (Resources.getSystem().getDisplayMetrics().heightPixels * 0.1);
        tvDescription.setMaxHeight(maxHeightPx);

        tvDescription.setOnClickListener(v -> {
            ViewHelper.showAlterDialogToDisplayText(this, podcastFeed.description, podcastFeed.title);
        });

        player = new ExoPlayer.Builder(this).build();
        player.addListener(new androidx.media3.common.Player.Listener() {
            @Override public void onIsPlayingChanged(boolean isPlaying) {
                if (waitingForStart && isPlaying) {
                    long dt = android.os.SystemClock.elapsedRealtime() - clickStartMs;
                    myLogI("AUDIO STARTED: " + dt + " ms after click");
                    stopLoader();
                }
            }
            @Override public void onPlaybackStateChanged(int state) {
                if (waitingForStart && state == androidx.media3.common.Player.STATE_READY && player.getPlayWhenReady()) {
                    long dt = android.os.SystemClock.elapsedRealtime() - clickStartMs;
                    myLogI("PLAYER READY: " + dt + " ms after click");
                    // We still wait for onIsPlayingChanged(true) to mark actual audio start,
                    // but READY timestamp is useful to log too.
                }
                if (state == androidx.media3.common.Player.STATE_ENDED) {
                    playNextInList();
                }
            }
        });

        progressBarPlayer = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBarPlayer.setIndeterminate(false);
        progressBarPlayer.setMax(100);
        progressBarPlayer.setProgress(0);
        progressBarPlayer.setVisibility(View.GONE);
        ((ViewGroup) btnPlayPause.getParent()).addView(progressBarPlayer,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (4 * getResources().getDisplayMetrics().density)));

        // If you have a mini PlayerView:
        // PlayerView pv = findViewById(R.id.playerViewMini);
        // pv.setPlayer(player);

        btnMiniBack.setOnClickListener(v -> { if (player != null) player.seekTo(Math.max(0, player.getCurrentPosition() - 30_000)); });
        btnMiniForward.setOnClickListener(v -> { if (player != null) player.seekTo(player.getCurrentPosition() + 30_000); });

        btnMiniPlayPause.setOnClickListener(v -> {
            if (player == null) return;
            if (player.isPlaying()) {
                player.pause();
                btnMiniPlayPause.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_media_play_24));
            } else {
                player.play();
                btnMiniPlayPause.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_media_pause_24));
            }
        });

        seekMini.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser || player == null || miniUserSeeking) return;
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { miniUserSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                if (player != null) {
                    long dur = player.getDuration();
                    if (dur > 0) {
                        long pos = (dur * sb.getProgress()) / sb.getMax();
                        player.seekTo(pos);
                    }
                }
                miniUserSeeking = false;
            }
        });


    }

    @Override protected void onDestroy() {
        stopLoader();
        if (player != null) { player.release(); player = null; }
        stopMiniTicker();
        super.onDestroy();
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

    private void fetchEpisodes(boolean isRefresh) {
        myLogD("fetchEpisodes " + (isRefresh ? "refresh" : "no refresh"));
        long nbEpisodeFull = 0;
        progressBar.setVisibility(View.VISIBLE);

        // 1) Load DB immediately → optimistic UI
        AppDatabase.databaseReadExecutor.execute(() -> {
            List<Episode> dbEpisodes = podcastEpisodeViewModel.getEpisodesFromDB(podcast.getId(), sortNewestFirst);
            myLogD("DB episodes count: " + dbEpisodes.size());
            List<DisplayableEpisode> initial = DisplayableEpisode.fromEpisodeList(dbEpisodes);
            runOnUiThread(() -> {
                updateAdapter(initial);
            });
        });
        if (!isRefresh && podcast.lastCheck > System.currentTimeMillis() - 1000 * 60 * Var.PODCASTINDEXORG_API_TIME_BETWEEN_PODCAST_CHECK_IN_MIN) {
            return;
        }

        // 2) Compute "since" from DB and then hit API
        AppDatabase.databaseReadExecutor.execute(() -> {
            long since;
            int maxEpisode;
            if (isRefresh) {
                since = 0;
                maxEpisode = Var.PODCASTINDEXORG_API_MAX_RESULTS_FOR_EPISODES_REFRESH_MODE;
            } else {
                maxEpisode = Var.PODCASTINDEXORG_API_MAX_RESULTS_FOR_EPISODES_NORMAL_MODE;
                Long lastPublished = podcastEpisodeViewModel.getLastPublishedForPodcastSync(podcast.getId()); // epoch seconds
                since = (lastPublished == null) ? 0L : Math.max(0L, lastPublished - 60);  //30j :  -(60*60*24*30)
            }
            PodcastHelper.getEpisodesByFeedId(
                    this,
                    podcast.feedId,
                    since,
                    maxEpisode,
                    true,
                    new PodcastHelper.EpisodeCallback() {
                        @Override
                        public void onSuccess(List<PodcastEpisode> apiEpisodes) {
                            myLogI("API CALL - episodes count: " + apiEpisodes.size());
                            // 3) Persist new/updated from API
                            podcastEpisodeViewModel.insertEpisodesInDB(apiEpisodes, podcast.feedId);

                            // 4) Refresh DB and merge for display
                            AppDatabase.databaseReadExecutor.execute(() -> {
                                List<Episode> dbEpisodes = podcastEpisodeViewModel.getEpisodesFromDB(podcast.getId(), sortNewestFirst);
                                List<DisplayableEpisode> fullList =
                                        DisplayableEpisode.mergeDisplayableEpisodes(apiEpisodes, dbEpisodes);
                                myLogD("DB episodes after insert: " + dbEpisodes.size());
                                int nbEpisodeFull = fullList.size();
                                myLogD("Displayed episodes count: " + nbEpisodeFull);
                                runOnUiThread(() -> {
                                    if (isRefresh) {
                                        myToast(nbEpisodeFull + " " + getString(R.string.episodes) );
                                    }
                                    updateAdapter(fullList);
                                });
                            });
                        }

                        @Override
                        public void onError(Exception e) {
                            // Fallback: DB-only (you already showed initial DB result; here we just end the spinner and warn)
                            myLogE("API CALL ERROR - " + e.getMessage());
                            AppDatabase.databaseReadExecutor.execute(() -> {
                                List<Episode> dbEpisodes = podcastEpisodeViewModel.getEpisodesFromDB(podcast.getId(), sortNewestFirst);
                                List<DisplayableEpisode> fallbackList = DisplayableEpisode.fromEpisodeList(dbEpisodes);

                                runOnUiThread(() -> {
                                    updateAdapter(fallbackList);
                                    tvDescription.setTextColor(getColor(R.color.orange_500));
                                    tvDescription.setText(getString(R.string.podcast_api_unavailable_fallback));
                                });
                            });
                        }
                    }
            );
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
        int toColor = ContextCompat.getColor(this, android.R.color.holo_red_light);
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

    private void updateAdapter(List<DisplayableEpisode> displayableEpisodeList) {
        adapter.setItems(displayableEpisodeList);
        adapter.notifyDataSetChanged();
        String tvStatsText = displayableEpisodeList.size() + "." + getString(R.string.ep);
        tvStats.setText(tvStatsText);
        progressBar.setVisibility(View.GONE);
    }

    private void playEpisode(DisplayableEpisode ep) {
        if (ep == null) return;
        currentEpisode = ep;
        myLog("playEpisode [" + ep.title + "]");
        player.setMediaItem(MediaItem.fromUri(ep.enclosureUrl));
        player.prepare();
        player.play();

        isPlaying = true;
        btnPlayPause.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_media_pause_24));
        adapter.setCurrentlyPlayingEpisodeId(ep.idEpisode);
        showMini(ep);
    }

    // ROW CLICK CALLBACK
    @Override public void onPlayEpisode(DisplayableEpisode ep) {
        myLogD("onPlayEpisode [" + ep.title + "]");
        if (currentEpisode != null && currentEpisode.idEpisode == ep.idEpisode) {
            // Same episode clicked again
            if (player != null && player.isPlaying()) {
                // Pause/stop
                player.pause(); // or player.stop() if you want to reset
                isPlaying = false;
                btnPlayPause.setImageDrawable(
                        AppCompatResources.getDrawable(this, R.drawable.ic_media_play_24)
                );
                myLogD("Paused (same episode clicked again): " + ep.title);
            } else {
                // Resume
                player.play();
                isPlaying = true;
                btnPlayPause.setImageDrawable(
                        AppCompatResources.getDrawable(this, R.drawable.ic_media_pause_24)
                );
                myLogD("Resumed (same episode clicked again): " + ep.title);
            }
        } else {
            // Different episode → play fresh
            beginStartupTiming();
            playEpisode(ep);
        }

        switchedToLocal = false;

        /*
        // 2) optionally download in background
        if (downloadWhilePlaying) {
            File target = PodcastHelper.buildPodcastPath(this, podcastFeed.title);
            if (!target.exists()) target.mkdirs();
            List<PodcastEpisode> one = new ArrayList<>();
            one.add(ep.toPodcastEpisode());
            PodcastDownloadManager.enqueueDownloads(this, podcastFeed.id, one, target, null);

            // 3) observe DB; when local file is ready, switch source *only if stream error or by policy*
            String folder = FileHelper.sanitizeFilename(podcastFeed.title);
            String fileName = PodcastHelper.buildPodcastEpisodeFileName(ep);

            viewModel.getZikFileLive(folder, fileName).observe(this, zf -> {
                if (zf == null || switchedToLocal || player == null) return;
                File local = new File(zf.getFullPath());
                if (!local.exists()) return;

                // hook into errors/buffering to decide when to switch:
                player.addListener(new androidx.media3.common.Player.Listener() {
                    @Override public void onPlayerError(@NonNull PlaybackException error) {
                        // stream died -> switch to local if ready
                        if (!switchedToLocal) switchToLocal(local);
                    }
                });

                // If you prefer: switch immediately when download completes:
                // switchToLocal(local);
            });
        }

 */
    }
    @Override
    public void onOpenLocalEpisode(ZikFile zikFile) {
        // 1) Stop/clear ExoPlayer (so it doesn't keep playing under PlayActivity)
        if (player != null) {
            try {
                player.stop();             // stop playback immediately
                player.clearMediaItems();  // remove the streamed item
            } catch (Exception ignored) {}
        }
        isPlaying = false;
        btnPlayPause.setImageDrawable(
                AppCompatResources.getDrawable(this, R.drawable.ic_media_play_24)
        );
        currentEpisode = null;
        if (adapter != null) adapter.setCurrentlyPlayingEpisodeId(null); // remove highlight

        // 2) Launch PlayActivity with the local file
        // open Play
        new Thread(() -> {
            try {
                myLog("clickOnEpisode : " + zikFile.getDisplayName() + " - " + zikFile.getId() + " - " + zikFile.getName());
                List<ZikFile> zikFilesList;
                if (sortNewestFirst) {
                    zikFilesList = AppDatabase.getDatabase(this).ZikFileDao().getPodcastZikFilesDesc(zikFile.getIdFolder());
                } else {
                    zikFilesList = AppDatabase.getDatabase(this).ZikFileDao().getPodcastZikFilesAsc(zikFile.getIdFolder());
                }
                PlayList.create(this, zikFilesList);
                int rankZikFile = getZikFileRankInFolderSync(zikFilesList, zikFile.getName());
                myLog("rankZikFile = " + rankZikFile);
                if (rankZikFile >= 0 ) {
                    PlayList.getInstance().setNumZikFile(rankZikFile);
                    startActivity(new Intent(this, PlayActivity.class).putExtra("ZikFile", zikFile));
                }
            } catch (Exception e) {
                myLogEE(e, "clickOnEpisode - playThatShit");
            }
        }).start();
    }
    private int getZikFileRankInFolderSync(List<ZikFile> files, String fileName) {
        for (int i = 0; i < files.size(); i++) {
            if (files.get(i).getName().equals(fileName)) {
                return i ;
            }
        }
        return -1; // not found
    }

    @Override
    public void onDownloadEpisode(DisplayableEpisode ep) {
        if (!enqueuedEpisodeIds.add(ep.idEpisode)) {
            myLog("download already enqueued for " + ep.title);
            return;
        }
        // 1) Enqueue download
        File target = PodcastHelper.buildPodcastPath(this, podcastFeed.title);
        if (!target.exists()) target.mkdirs();
        java.util.List<PodcastEpisode> one = new java.util.ArrayList<>();
        one.add(ep.toPodcastEpisode());
        com.driot.bookplayer.utils.PodcastDownloadManager
                .enqueueDownloads(this, podcastFeed.id, one, target, null);

        // 2) Observe DB for completion (file appears)
        String folder = com.driot.bookplayer.helpers.FileHelper.sanitizeFilename(podcastFeed.title);
        String fileName = com.driot.bookplayer.helpers.PodcastHelper.buildPodcastEpisodeFileName(ep);

        androidx.lifecycle.LiveData<ZikFile> live = podcastEpisodeViewModel.getZikFileLive(folder, fileName);
        // Avoid duplicate observers for same file
        if (pendingSwitchObservers.containsKey(fileName)) return;

        androidx.lifecycle.Observer<ZikFile> obs = zf -> {
            if (zf == null) return;
            File local = new File(zf.getPath());
            if (!local.exists()) return;

            // Got it: remove observer
            live.removeObserver(pendingSwitchObservers.get(fileName));
            pendingSwitchObservers.remove(fileName);

            // Switch only if this is the episode currently streaming from Exo
            if (currentEpisode != null && currentEpisode.idEpisode == ep.idEpisode) {
                switchFromStreamToLocalInMini(zf);
                //switchFromStreamToPlayActivity(zf);
            }
        };
        pendingSwitchObservers.put(fileName, obs);
        live.observe(this, obs);
    }
    /*
    private void switchFromStreamToPlayActivity(ZikFile zf) {
        long posMs = (player != null) ? player.getCurrentPosition() : 0L;

        // Stop Exo cleanly
        try {
            if (player != null) {
                player.stop();
                player.clearMediaItems();
            }
        } catch (Exception ignored) {}

        isPlaying = false;
        btnPlayPause.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_media_play_24));
        adapter.setCurrentlyPlayingEpisodeId(null);

        // Launch PlayActivity + (optional) pass resume position
        Intent i = new Intent(this, PlayActivity.class)
                .putExtra("ZikFile", zf)
                .putExtra("resume_position_ms", posMs)
                .putExtra("autoplay", true);
        startActivity(i);
    }

     */
    private void switchFromStreamToLocalInMini(ZikFile zf) {
        File local = new File(zf.getPath());
        long posMs = (player != null) ? player.getCurrentPosition() : 0L;
        if (player != null) {
            player.pause();
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(local)));
            player.prepare();
            player.seekTo(posMs);
            player.play();
        }
        // keep mini bar visible; nothing else to do
    }


    private void switchToLocal(File local) {
        if (player == null) return;
        long pos = player.getCurrentPosition();
        switchedToLocal = true;
        player.pause();
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(local)));
        player.prepare();
        player.seekTo(pos);
        player.play();

        // Optional: now show full controls
        // startActivity(new Intent(this, PlayActivity.class));
    }

    private void beginStartupTiming() {
        clickStartMs = android.os.SystemClock.elapsedRealtime();
        waitingForStart = true;

        // show loader only if >100ms
        ui.postDelayed(() -> {
            if (waitingForStart) {
                progressBarPlayer.setVisibility(View.VISIBLE);
                startLoaderTicker();
            }
        }, 100);
    }

    private void startLoaderTicker() {
        stopLoaderTicker();
        loaderTicker = new Runnable() {
            @Override public void run() {
                if (!waitingForStart) return;
                try {
                    int pct = player != null ? player.getBufferedPercentage() : 0; // 0..100
                    progressBarPlayer.setProgress(Math.max(0, Math.min(100, pct)));
                } catch (Throwable ignored) {}
                ui.postDelayed(this, 150);
            }
        };
        ui.post(loaderTicker);
    }

    private void stopLoaderTicker() {
        if (loaderTicker != null) {
            ui.removeCallbacks(loaderTicker);
            loaderTicker = null;
        }
    }

    private void stopLoader() {
        waitingForStart = false;
        stopLoaderTicker();
        progressBarPlayer.setVisibility(View.GONE);
        progressBarPlayer.setProgress(0);
    }

    private void showMini(DisplayableEpisode ep) {
        if (miniPlayer.getVisibility() != View.VISIBLE) miniPlayer.setVisibility(View.VISIBLE);
        tvMiniTitle.setText(ep.title);
        btnMiniPlayPause.setImageDrawable(AppCompatResources.getDrawable(this,
                player != null && player.isPlaying() ? R.drawable.ic_media_pause_24 : R.drawable.ic_media_play_24));
        startMiniTicker();
    }

    private void hideMini() {
        miniPlayer.setVisibility(View.GONE);
        stopMiniTicker();
    }

    private void startMiniTicker() {
        stopMiniTicker();
        miniTicker = new Runnable() {
            @Override public void run() {
                if (player != null && !miniUserSeeking) {
                    long pos = player.getCurrentPosition();
                    long dur = player.getDuration();
                    if (dur > 0) {
                        int prog = (int) ((pos * seekMini.getMax()) / dur);
                        seekMini.setProgress(prog);
                        tvMiniTime.setText(formatMmSs(pos) + " / " + formatMmSs(dur));
                    } else {
                        seekMini.setProgress(0);
                        tvMiniTime.setText("--:-- / --:--");
                    }
                    btnMiniPlayPause.setImageDrawable(AppCompatResources.getDrawable(PodcastEpisodeActivity.this,
                    player.isPlaying() ? R.drawable.ic_media_pause_24 : R.drawable.ic_media_play_24));
                }
                miniUi.postDelayed(this, 500);
            }
        };
        miniUi.post(miniTicker);
    }

    private void stopMiniTicker() {
        if (miniTicker != null) {
            miniUi.removeCallbacks(miniTicker);
            miniTicker = null;
        }
    }

    private String formatMmSs(long ms) {
        long s = ms / 1000;
        long m = s / 60;
        long sec = s % 60;
        return String.format(java.util.Locale.US, "%d:%02d", m, sec);
    }

    private void playNextInList() {
        if (currentEpisode == null) return;
        int idx = adapter.indexOfEpisodeId(currentEpisode.idEpisode);
        if (idx < 0) return;
        int next = idx + 1;
        if (next >= adapter.getCount()) return;
        DisplayableEpisode ep = adapter.getItem(next);
        if (ep != null) {
            onPlayEpisode(ep); // reuse same pipeline
            // Optionally scroll to make it visible:
            recyclerEpisodes.smoothScrollToPosition(next);
        }
    }

}
