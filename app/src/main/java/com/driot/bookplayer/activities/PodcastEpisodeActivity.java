package com.driot.bookplayer.activities;

import static com.driot.bookplayer.global.Var.PODCASTINDEXORG_SINCE;
import static com.driot.bookplayer.utils.TextOptions.parseMaybeHtml;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
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
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.objects.DisplayableEpisode;
import com.driot.bookplayer.player.AudioService;
import com.driot.bookplayer.player.PlayList;
import com.driot.bookplayer.objects.PodcastEpisode;
import com.driot.bookplayer.objects.PodcastFeed;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.PodcastHelper;
import com.driot.bookplayer.utils.PodcastDownloadManager;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PodcastEpisodeActivity extends LoggingActivity  implements PodcastEpisodeRVAdapter.EpisodeClickHandler {

    private TextView tvTitle, tvDescription, tvStats, tvToolbarStats;
    private ImageView ivCover, ivMiniCover;
    private RecyclerView recyclerEpisodes;
    private PodcastEpisodeRVAdapter adapter;

    private Podcast podcast;
    private PodcastFeed podcastFeed;

    // ADD these:
    private ImageButton btnFavoriteToolbar, btnAutoDownloadToolbar, btnRefreshToolbar, btnSortToolbar, btnCollapseToolbar;
    private ImageButton btnFavoriteOverlay, btnAutoDownloadOverlay, btnRefreshOverlay, btnSortOverlay, btnCollapseOverlay;

    private PodcastDao podcastDao;

    private PodcastEpisodeViewModel podcastEpisodeViewModel;

    private boolean sortNewestFirst;

    private ExoPlayer exoPlayer;
    private boolean isPlaying = false;

    private DisplayableEpisode currentEpisode;

    private final java.util.Map<String, androidx.lifecycle.Observer<ZikFile>> pendingSwitchObservers = new java.util.HashMap<>();
    private final java.util.Set<Long> enqueuedEpisodeIds = new java.util.HashSet<>();

    // timing / loader
    private long clickStartMs = 0L;

    private View miniPlayer;
    private ImageButton btnMiniPlayPause, btnMiniBack, btnMiniForward;
    private SeekBar seekMini;
    private TextView tvMiniTime;

    private final android.os.Handler miniUi = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable miniTicker;
    private boolean miniUserSeeking = false;
    private ProgressBar progressMini;
    private boolean isExpanded;

    private com.google.android.material.appbar.AppBarLayout appBar;
    private androidx.appcompat.widget.Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_podcast_detail);

        if (Option.getScreenOrientationLock()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        }

        InsetHelper.applyInsetsForScrollableBehindNavBar(this, findViewById(R.id.coordinator_layout));

        tvTitle = findViewById(R.id.tvPodcastTitle);
        tvDescription = findViewById(R.id.tvPodcastDescription);
        tvStats = findViewById(R.id.tvPodcastStat);
        tvToolbarStats = findViewById(R.id.tvToolbarStats);

        ivCover = findViewById(R.id.ivPodcastCover);
        ivMiniCover = findViewById(R.id.ivMiniCover);

        recyclerEpisodes = findViewById(R.id.rvEpisodes);

// TOOLBAR actions
        btnFavoriteToolbar = findViewById(R.id.btnFavoriteToolbar);
        btnAutoDownloadToolbar = findViewById(R.id.btnAutoDownloadToolbar);
        btnRefreshToolbar = findViewById(R.id.btnRefreshToolbar);
        btnSortToolbar = findViewById(R.id.btnSortToolbar);
        btnCollapseToolbar = findViewById(R.id.btnCollapseToolbar);

// OVERLAY actions on top of the big cover
        btnFavoriteOverlay = findViewById(R.id.btnFavoriteOverlay);
        btnAutoDownloadOverlay = findViewById(R.id.btnAutoDownloadOverlay);
        btnRefreshOverlay = findViewById(R.id.btnRefreshOverlay);
        btnSortOverlay = findViewById(R.id.btnSortOverlay);
        btnCollapseOverlay = findViewById(R.id.btnCollapseOverlay);

// MINI PLAYER
        miniPlayer = findViewById(R.id.miniPlayer);
        btnMiniPlayPause = findViewById(R.id.btnMiniPlayPause);
        btnMiniBack = findViewById(R.id.btnMiniBack);
        btnMiniForward = findViewById(R.id.btnMiniForward);
        seekMini = findViewById(R.id.seekMini);
        tvMiniTime = findViewById(R.id.tvMiniTime);
        progressMini = findViewById(R.id.progressMini);

        appBar = findViewById(R.id.appBar);
        toolbar = findViewById(R.id.toolbar);
        toolbar.setAlpha(0f);
        toolbar.setVisibility(View.INVISIBLE);

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

        boolean isFavorite = podcast != null && podcast.isFavorite;
        boolean isAutoDownload = podcast != null && podcast.autoDownload;

        updateFavoriteIconColor(isFavorite);
        updateAutoDownloadIconColor(isAutoDownload);
        int vis = isFavorite ? View.VISIBLE : View.GONE;
        btnAutoDownloadToolbar.setVisibility(vis);
        btnAutoDownloadOverlay.setVisibility(vis);


        ivCover.setOnClickListener(view -> {
            myLogI("---- USER CLICK IMAGE ----");
            goToPlaySection();
        });
        ivMiniCover.setOnClickListener(view -> {
            myLogI("---- USER CLICK MINI IMAGE ----");
            goToPlaySection();
        });

        tvTitle.setText(podcastFeed.title);
        tvDescription.setText(parseMaybeHtml(podcastFeed.description));
        Glide.with(ivCover.getContext()).load(podcastFeed.image).into(ivCover);
        Glide.with(ivMiniCover.getContext()).load(podcastFeed.image).into(ivMiniCover);

        if (podcastFeed.id == -1) {
            myToastE("Error loading episodes. ID=-1");
        } else {
            fetchEpisodes(false);
        }

        View.OnClickListener favoriteClick = v -> toggleFavorite();
        View.OnClickListener autoDownloadClick = v -> toggleAutoDownload();
        View.OnClickListener refreshClick = v -> {
            FirebaseAnalyticsHelper.tellAnalyticsPodcastRefresh(podcastFeed.title);
            myLogI("-------- USER CLICKS REFRESH -----");
            fetchEpisodes(true); };
        View.OnClickListener sortClick = v -> toggleSort();
        View.OnClickListener collapseClick = v -> toggleCollapse();

// Toolbar
        btnFavoriteToolbar.setOnClickListener(favoriteClick);
        btnAutoDownloadToolbar.setOnClickListener(autoDownloadClick);
        btnRefreshToolbar.setOnClickListener(refreshClick);
        btnSortToolbar.setOnClickListener(sortClick);
        btnCollapseToolbar.setOnClickListener(collapseClick);

// Overlay
        btnFavoriteOverlay.setOnClickListener(favoriteClick);
        btnAutoDownloadOverlay.setOnClickListener(autoDownloadClick);
        btnRefreshOverlay.setOnClickListener(refreshClick);
        btnSortOverlay.setOnClickListener(sortClick);
        btnCollapseOverlay.setOnClickListener(collapseClick);
        appBar.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            // verticalOffset is 0 when fully expanded; negative as you scroll up.
            int range = appBarLayout.getTotalScrollRange();
            float progress = range == 0 ? 0f : Math.min(1f, Math.abs(verticalOffset) / (float) range);

            // Don’t show the bar until the user *started* scrolling a bit
            float showThreshold = 0.06f; // ~6% collapse before we show anything
            if (progress > showThreshold) {
                if (toolbar.getVisibility() != View.VISIBLE) toolbar.setVisibility(View.VISIBLE);
                // Fade from 0 -> 1 between threshold and ~30% collapse
                float alpha = (progress - showThreshold) / (0.30f - showThreshold);
                toolbar.setAlpha(Math.max(0f, Math.min(1f, alpha)));
            } else {
                toolbar.setAlpha(0f);
                toolbar.setVisibility(View.INVISIBLE);
            }
        });


// PLAYER
        exoPlayer = new ExoPlayer.Builder(this).build();
        exoPlayer.addListener(new androidx.media3.common.Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                boolean playWhenReady = exoPlayer.getPlayWhenReady();
                switch (state) {
                    case androidx.media3.common.Player.STATE_BUFFERING:
                        myLogD("STATE_BUFFERING");
                        break;

                    case androidx.media3.common.Player.STATE_READY:
                        myLogD("STATE_READY");
                        // If we were showing spinner, hide it now; audio may be audible imminently.
                        break;

                    case androidx.media3.common.Player.STATE_ENDED:
                        myLogD("STATE_ENDED");
                        setMiniLoading(false);
                        setMiniPlayIcon(false);
                        playNextInList();
                        break;

                    case androidx.media3.common.Player.STATE_IDLE:
                        myLogD("STATE_IDLE");
                        setMiniLoading(false);
                        setMiniPlayIcon(false);
                        break;
                }

                if (state == androidx.media3.common.Player.STATE_READY && playWhenReady) {
                    long dt = android.os.SystemClock.elapsedRealtime() - clickStartMs;
                    myLogD("PLAYER READY: " + dt + " ms after click");
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                myLog("onIsPlayingChanged");
                if (isPlaying) {
                    long dt = android.os.SystemClock.elapsedRealtime() - clickStartMs;
                    myLog("AUDIO STARTED: " + dt + " ms after click");
                    setMiniLoading(false);
                }
                setMiniPlayIcon(isPlaying);
            }

            @Override
            public void onPlayerError(@NonNull androidx.media3.common.PlaybackException error) {
                myLogE("onPlayerError " + error.getMessage());
                myToastE(error.getMessage());
                setMiniLoading(false);
                setMiniPlayIcon(false);
            }
        });


        // If you have a mini PlayerView:
        // PlayerView pv = findViewById(R.id.playerViewMini);
        // pv.setPlayer(player);

        btnMiniBack.setOnClickListener(v -> { if (exoPlayer != null) exoPlayer.seekTo(Math.max(0, exoPlayer.getCurrentPosition() - 15_000)); });
        btnMiniForward.setOnClickListener(v -> { if (exoPlayer != null) exoPlayer.seekTo(exoPlayer.getCurrentPosition() + 15_000); });

        btnMiniPlayPause.setOnClickListener(v -> {
            if (exoPlayer == null) return;
            if (exoPlayer.isPlaying()) {
                exoPlayer.pause();
            } else {
                exoPlayer.play();
            }
        });

        seekMini.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser || exoPlayer == null || miniUserSeeking) return;
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { miniUserSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                setMiniLoading(true);
                if (exoPlayer != null) {
                    long dur = exoPlayer.getDuration();
                    if (dur > 0) {
                        long pos = (dur * sb.getProgress()) / sb.getMax();
                        exoPlayer.seekTo(pos);
                    }
                }
                miniUserSeeking = false;
            }
        });

        isExpanded = Option.getPodcastEpisodesDescriptionExpand();
        adapter.setShowDescriptions(isExpanded);
        animateDescriptionHeight(tvDescription, isExpanded);
        updateCollapseIcon();
    }

    @Override protected void onDestroy() {
        if (exoPlayer != null) { exoPlayer.release(); exoPlayer = null; }
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
                btnAutoDownloadOverlay.setVisibility(favoriteState ? View.VISIBLE : View.GONE);
                btnAutoDownloadToolbar.setVisibility(favoriteState ? View.VISIBLE : View.GONE);
            });
            ImageHelper.processPendingImages(this);
            FirebaseAnalyticsHelper.tellAnalyticsPodcastFavorite(podcast.title, podcast.language);
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
                FirebaseAnalyticsHelper.tellAnalyticsPodcastAutoDownload(podcast.title, podcast.language);
            }
            runOnUiThread(() -> {
                updateAutoDownloadIconColor(podcast.autoDownload);
            });
        });
    }

    private void toggleSort() {
        sortNewestFirst = !sortNewestFirst;
        myLogI("-------- USER CLICKS SORT --  sortNewestFirst= " + sortNewestFirst);
        PodcastHelper.updateSortNewestTop(this, podcast.feedId, sortNewestFirst);
        AppDatabase.databaseReadExecutor.execute(() -> {
            List<Episode> dbEpisodes = podcastEpisodeViewModel.getEpisodesFromDB(podcast.getId(), sortNewestFirst);
            List<DisplayableEpisode> sortedList = DisplayableEpisode.fromEpisodeList(dbEpisodes);
            runOnUiThread(() -> {
                adapter.setItems(sortedList);
                adapter.notifyDataSetChanged();
            });
        });
    }

    private void toggleCollapse() {
        isExpanded = !isExpanded;
        myLogI("-------- USER CLICKS COLLAPSE --  isExpanded= " + isExpanded);
        Option.setPodcastEpisodesDescriptionExpand(isExpanded);
        animateDescriptionHeight(tvDescription, isExpanded);
        adapter.setShowDescriptions(isExpanded);  // show/hide item descriptions
        updateCollapseIcon();
    }




    private void updateFavoriteIconColor(boolean isOn) {
        int colorResId = isOn ? android.R.color.holo_red_light : R.color.gray_500;
        int color = ContextCompat.getColor(this, colorResId);
        if (btnFavoriteToolbar != null) btnFavoriteToolbar.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        if (btnFavoriteOverlay != null) btnFavoriteOverlay.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    private void updateAutoDownloadIconColor(boolean isOn) {
        int colorResId = isOn ? R.color.green_300 : R.color.gray_500;
        int color = ContextCompat.getColor(this, colorResId);
        if (btnAutoDownloadToolbar != null) btnAutoDownloadToolbar.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        if (btnAutoDownloadOverlay != null) btnAutoDownloadOverlay.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }


    private void fetchEpisodes(boolean isRefresh) {
        myLogD("fetchEpisodes " + (isRefresh ? "refresh" : "no refresh"));
        long nbEpisodeFull = 0;

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
        if (podcast == null) {
            myLogE("Podcast == null");
            return;
        }
        if (podcast.idFolder == null || podcast.idFolder <= 0) {
            myLog("Podcast exist in DB but no Folder exists (nothing downloaded yet)");
            return;
        }

        new Thread(() -> {
            try {
                Folder folder = AppDatabase.getDatabase(getApplicationContext())
                        .FolderDao().getById(podcast.idFolder);
                if (folder == null) return;

                List<ZikFile> zikFilesList = AppDatabase.getDatabase(getApplicationContext())
                        .ZikFileDao().getZikFiles(podcast.idFolder);

                myLogI("nb ZikFiles in that Book : " + zikFilesList.size() + " - [" + folder.getName() + "]");

                PlayList.create(this, zikFilesList);

                // Switch to main thread for any UI / navigation
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (!zikFilesList.isEmpty()) {
                        closeExoPlayer();
                        startActivity(new Intent(this, ZikFileActivity.class).putExtra("folder", folder));
                    } else {
                        myLogE("no ZikFiles in that folder !");
                        myToastE(getString(R.string.ErrorCouldNotLoadAudios_emptyfolder)); // main thread
                    }
                });

            } catch (Exception e) {
                myLogEE(e, "error getting Folder/ZikFiles");
                runOnUiThread(() -> myToastEE(null, getString(R.string.ErrorCouldNotLoadAudios)));
            }
        }).start();
    }


    private void updateAdapter(List<DisplayableEpisode> displayableEpisodeList) {
        adapter.setItems(displayableEpisodeList);
        adapter.notifyDataSetChanged();
        String tvStatsText = displayableEpisodeList.size() + "." + getString(R.string.ep);
        tvStats.setText(tvStatsText);
        tvToolbarStats.setText(tvStatsText);
    }

    private void playEpisode(DisplayableEpisode ep) {
        if (ep == null) return;

        stopAudioServiceIfRunning();

        // ExoPlayer
        currentEpisode = ep;
        beginStartupTiming();
        setMiniLoading(true);

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH) // pour musique: MUSIC ; pour podcast/voix: SPEECH (meilleur ducking)
                .build();
        exoPlayer.setAudioAttributes(attrs, /*handleAudioFocus=*/ true);

        exoPlayer.setMediaItem(MediaItem.fromUri(ep.enclosureUrl));
        exoPlayer.prepare();
        exoPlayer.play();

        isPlaying = true;
        adapter.setCurrentlyPlayingEpisodeId(ep.idEpisode);
        showMini(ep);
    }

    // ROW CLICK CALLBACK
    @Override public void onPlayEpisode(DisplayableEpisode ep) {
        myLogD("onPlayEpisode [" + ep.title + "]");
        if (currentEpisode != null && currentEpisode.idEpisode == ep.idEpisode) {
            // Same episode toggled
            if (exoPlayer != null && exoPlayer.isPlaying()) {
                exoPlayer.pause();
                isPlaying = false;
            } else if (exoPlayer != null) {
                // resume (no spinner here; RESUME is near-instant)
                exoPlayer.play();
                isPlaying = true;
            }
        } else {
            // Different episode → fresh play, show spinner
            FirebaseAnalyticsHelper.tellAnalyticsStartStreaming(ep.title);
            playEpisode(ep);
        }
    }

    @Override
    public void onOpenLocalEpisode(ZikFile zikFile) {
        closeExoPlayer();
        stopAudioServiceIfRunning();

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
        if (Option.getNetworkPolicyManualDownload().equals(NetworkHelper.NetworkPolicyManual.NETWORK_POLICY_UNMETERED) && NetworkHelper.isUnmeteredConnected(this)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.download_warning_title_unmetered)
                    .setMessage(R.string.download_warning_message_unmetered)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        proceedWithDownload(podcastFeed.title, ep, podcastFeed.id);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                PodcastHelper.addPodcastToDB(this, podcastFeed);
            });
            proceedWithDownload(podcastFeed.title, ep, podcastFeed.id);

        }
    }

    private void proceedWithDownload(String futureFolderName, DisplayableEpisode displayableEpisode, long feedId) {
        File targetFolder = PodcastHelper.buildPodcastPath(this, futureFolderName);
        if (!targetFolder.exists()) targetFolder.mkdirs();
        List<PodcastEpisode> singleList = new ArrayList<>();
        singleList.add(displayableEpisode.toPodcastEpisode());
        PodcastDownloadManager.enqueueDownloads(this, feedId, singleList, targetFolder, null);
    }


    private void beginStartupTiming() {
        clickStartMs = android.os.SystemClock.elapsedRealtime();
        btnMiniPlayPause.setImageDrawable(AppCompatResources.getDrawable(PodcastEpisodeActivity.this,R.drawable.ic_media_cloud_sync_24));
    }

    private void showMini(DisplayableEpisode ep) {
        if (miniPlayer.getVisibility() != View.VISIBLE) miniPlayer.setVisibility(View.VISIBLE);
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
                if (exoPlayer != null && !miniUserSeeking) {
                    long pos = exoPlayer.getCurrentPosition();
                    long dur = exoPlayer.getDuration();
                    if (dur > 0) {
                        int prog = (int) ((pos * seekMini.getMax()) / dur);
                        seekMini.setProgress(prog);
                        String timeString = Tonio.formatMmSs(pos) + " / " + Tonio.formatMmSs(dur);
                        tvMiniTime.setText(timeString);
                    } else {
                        seekMini.setProgress(0);
                        tvMiniTime.setText("--:-- / --:--");
                    }
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


    private void setImageBtnPlayPauseIsPlaying(boolean isPlaying) {
        btnMiniPlayPause.setImageDrawable(AppCompatResources.getDrawable(PodcastEpisodeActivity.this,
                isPlaying ? R.drawable.ic_media_pause_24 : R.drawable.ic_media_play_24));
        myLog("setImageBtnPlayPauseIsPlaying " + isPlaying);
    }
    // Show/hide the tiny spinner and swap the play/pause button visibility.
    private void setMiniLoading(boolean loading) {
        if (loading) {
            myLogD("set buffer icon ON");
            if (progressMini != null) progressMini.setVisibility(View.VISIBLE);
            if (btnMiniPlayPause != null) btnMiniPlayPause.setVisibility(View.INVISIBLE);
        } else {
            myLogD("set buffer icon OFF");
            if (progressMini != null) progressMini.setVisibility(View.GONE);
            if (btnMiniPlayPause != null) btnMiniPlayPause.setVisibility(View.VISIBLE);
        }
    }

    private void setMiniPlayIcon(boolean isPlaying) {
        btnMiniPlayPause.setImageDrawable(
                AppCompatResources.getDrawable(
                        this,
                        isPlaying ? R.drawable.ic_media_pause_24 : R.drawable.ic_media_play_24
                )
        );
    }

    private void updateCollapseIcon() {
        btnCollapseOverlay.setImageDrawable(
                AppCompatResources.getDrawable(
                        this,
                        isExpanded ? R.drawable.ic_content_collapse_24 : R.drawable.ic_content_expand_24
                )
        );
        btnCollapseToolbar.setImageDrawable(
                AppCompatResources.getDrawable(
                        this,
                        isExpanded ? R.drawable.ic_content_collapse_24 : R.drawable.ic_content_expand_24
                )
        );
    }

    /** Animate tv height: 0 -> wrap content (expand) or current -> 0 (collapse). */
    private void animateDescriptionHeight(TextView tv, boolean expand) {
        tv.clearAnimation();

        // Run after layout to have a stable width
        tv.post(() -> {
            if (expand) {
                // Ensure the view is visible before measuring
                tv.setVisibility(View.VISIBLE);

                // Measure with the real laid-out width
                int width = tv.getWidth();
                if (width == 0) {
                    // fallback to parent width if needed
                    View parent = (View) tv.getParent();
                    width = parent.getWidth();
                }
                int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
                int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                tv.measure(widthSpec, heightSpec);
                final int target = tv.getMeasuredHeight();

                // Start from 0 height
                ViewGroup.LayoutParams lp = tv.getLayoutParams();
                lp.height = 0;
                tv.setLayoutParams(lp);

                ValueAnimator va = ValueAnimator.ofInt(0, target);
                va.setDuration(250);
                va.addUpdateListener(a -> {
                    lp.height = (int) a.getAnimatedValue();
                    tv.setLayoutParams(lp);
                });
                va.addListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        // Let layout reflow naturally going forward
                        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        tv.setLayoutParams(lp);
                    }
                });
                va.start();

            } else {
                final int start = tv.getHeight();
                final ViewGroup.LayoutParams lp = tv.getLayoutParams();

                ValueAnimator va = ValueAnimator.ofInt(start, 0);
                va.setDuration(200);
                va.addUpdateListener(a -> {
                    lp.height = (int) a.getAnimatedValue();
                    tv.setLayoutParams(lp);
                });
                va.addListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        tv.setVisibility(View.GONE);
                        // Reset so next expand measures correctly
                        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        tv.setLayoutParams(lp);
                    }
                });
                va.start();
            }
        });
    }


    private void closeExoPlayer() {
        if (exoPlayer != null) {
            try {
                exoPlayer.stop();             // stop playback immediately
                exoPlayer.clearMediaItems();  // remove the streamed item
            } catch (Exception ignored) {}
        }
        isPlaying = false;
        setMiniLoading(false);
        setMiniPlayIcon(false);
        currentEpisode = null;
        if (adapter != null) adapter.setCurrentlyPlayingEpisodeId(null); // remove highlight
        hideMini();
    }

    private void stopAudioServiceIfRunning() {
        if (AudioService.isRunning) {
            Intent cmd = new Intent(this, AudioService.class)
                    .setAction(AudioService.ACTION_CMD)
                    .putExtra(AudioService.EXTRA_CMD, AudioService.CMD_STOP);
            try {
                // App au premier plan → safe, pas de règle des 5s
                startService(cmd);
            } catch (IllegalStateException e) {
                // Si jamais l’app est en arrière-plan, au pire on force l’arrêt
                stopService(new Intent(this, AudioService.class));
            }
        }
    }
}
