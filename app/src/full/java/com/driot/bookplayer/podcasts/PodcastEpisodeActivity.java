package com.driot.bookplayer.podcasts;

import static com.driot.bookplayer.global.Var.PODCAST_INDEX_ORG_SINCE;
import static com.driot.bookplayer.utils.TonioCommonStuff.parseMaybeHtml;

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
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.nav.BaseBottomNavActivity;
import com.driot.bookplayer.activities.ZikFileActivity;
import com.driot.bookplayer.adapter.PodcastEpisodeRVAdapter;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Episode;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.PodcastDao;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.player.MediaService;
import com.driot.bookplayer.player.PlayList;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.player.StartPlayHelper;
import com.google.android.material.appbar.AppBarLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class PodcastEpisodeActivity extends BaseBottomNavActivity
        implements PodcastEpisodeRVAdapter.EpisodeClickHandler {

    private TextView tvTitle, tvDescription, tvStats, tvToolbarStats;
    private ImageView ivCover, ivMiniCover;
    private RecyclerView recyclerEpisodes;
    private PodcastEpisodeRVAdapter adapter;

    private Podcast podcast;
    private PodcastFeed podcastFeed;

    // ADD these:
    private ImageButton btnFavoriteToolbar, btnAutoDownloadToolbar, btnSearchToolbar, btnRefreshToolbar, btnSortToolbar,
            btnCollapseToolbar;
    private ImageButton btnFavoriteOverlay, btnAutoDownloadOverlay, btnSearchOverlay, btnRefreshOverlay, btnSortOverlay,
            btnCollapseOverlay;

    private PodcastDao podcastDao;

    private PodcastEpisodeViewModel podcastEpisodeViewModel;

    private boolean sortNewestFirst;

    private List<DisplayableEpisode> allEpisodes = new ArrayList<>();
    private String currentSearchQuery = "";
    private boolean searchInDescription = false;

    private DisplayableEpisode currentEpisode;

    private final Set<Long> enqueuedEpisodeIds = new HashSet<>();

    private boolean isExpanded;

    private AppBarLayout appBar;
    private Toolbar toolbar;

    @Override
    protected int getNavId() {
        return R.id.nav_podcast;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_podcast_detail;
    }

    @Override
    protected boolean enableOngoingTaskOverlay() {
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        if (Option.getScreenOrientationLock()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        }

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
        btnSearchToolbar = findViewById(R.id.btnSearchToolbar);
        btnRefreshToolbar = findViewById(R.id.btnRefreshToolbar);
        btnSortToolbar = findViewById(R.id.btnSortToolbar);
        btnCollapseToolbar = findViewById(R.id.btnCollapseToolbar);

        // OVERLAY actions on top of the big cover
        btnFavoriteOverlay = findViewById(R.id.btnFavoriteOverlay);
        btnAutoDownloadOverlay = findViewById(R.id.btnAutoDownloadOverlay);
        btnSearchOverlay = findViewById(R.id.btnSearchOverlay);
        btnRefreshOverlay = findViewById(R.id.btnRefreshOverlay);
        btnSortOverlay = findViewById(R.id.btnSortOverlay);
        btnCollapseOverlay = findViewById(R.id.btnCollapseOverlay);

        appBar = findViewById(R.id.appBar);
        toolbar = findViewById(R.id.toolbar);
        toolbar.setAlpha(0f);
        toolbar.setVisibility(View.INVISIBLE);

        podcastDao = AppDatabase.getDatabase(this).podcastDao();

        podcast = getIntent().getParcelableExtra("podcast");
        Folder folder = getIntent().getParcelableExtra("folder");

        if (podcast == null) {
            myToastE("error loading podcast");
            myLogEE(null, "parcelable extra podcast is null");
            finish();
            return;
        }

        sortNewestFirst = podcast.sort_newest_top;
        myLogD("Sort newest first: " + sortNewestFirst);

        podcastFeed = new PodcastFeed(
                podcast.feedId, podcast.title, podcast.image, podcast.description);

        //if the podcast has been saved as a book, the image may have changed.
        if (folder != null && folder.image != null) {
            podcastFeed.image = folder.image;
        }

        podcastEpisodeViewModel = new ViewModelProvider(this).get(PodcastEpisodeViewModel.class);
        podcastEpisodeViewModel.getPodcastLiveByFeedId(podcastFeed.id).observe(this, updatedPodcast -> {
            if (updatedPodcast != null) {
                this.podcast = updatedPodcast;
                // update UI here if needed
            }
        });

        int span = getResources().getInteger(R.integer.classic_grid_span);
        GridLayoutManager glm = new GridLayoutManager(this, span);
        recyclerEpisodes.setLayoutManager(glm);
        recyclerEpisodes
                .addItemDecoration(new ViewHelper.SpacesItemDecoration(ViewHelper.dp(this, Var.GRID_LAYOUT_SPACER)));

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
        Glide.with(ivCover.getContext())
                .load(StorageHelper.checkAndCleanImagePath(ivCover.getContext(), podcastFeed.image)).into(ivCover);
        Glide.with(ivMiniCover.getContext())
                .load(StorageHelper.checkAndCleanImagePath(ivMiniCover.getContext(), podcastFeed.image))
                .into(ivMiniCover);

        if (podcastFeed.id == -1) {
            myToastE("Error loading episodes. ID=-1");
        } else {
            fetchEpisodes(false);
        }

        View.OnClickListener favoriteClick = v -> toggleFavorite();
        View.OnClickListener autoDownloadClick = v -> toggleAutoDownload();
        View.OnClickListener searchClick = v -> showSearchDialog();
        View.OnClickListener refreshClick = v -> {
            FirebaseAnalyticsHelper.tellAnalyticsPodcastRefresh(podcastFeed.title);
            myLogI("-------- USER CLICKS REFRESH -----");
            fetchEpisodes(true);
        };
        View.OnClickListener sortClick = v -> toggleSort();
        View.OnClickListener collapseClick = v -> toggleCollapse();

        // Toolbar
        btnFavoriteToolbar.setOnClickListener(favoriteClick);
        btnAutoDownloadToolbar.setOnClickListener(autoDownloadClick);
        btnSearchToolbar.setOnClickListener(searchClick);
        btnRefreshToolbar.setOnClickListener(refreshClick);
        btnSortToolbar.setOnClickListener(sortClick);
        btnCollapseToolbar.setOnClickListener(collapseClick);

        // Overlay
        btnFavoriteOverlay.setOnClickListener(favoriteClick);
        btnAutoDownloadOverlay.setOnClickListener(autoDownloadClick);
        btnSearchOverlay.setOnClickListener(searchClick);
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
                if (toolbar.getVisibility() != View.VISIBLE)
                    toolbar.setVisibility(View.VISIBLE);
                // Fade from 0 -> 1 between threshold and ~30% collapse
                float alpha = (progress - showThreshold) / (0.30f - showThreshold);
                toolbar.setAlpha(Math.max(0f, Math.min(1f, alpha)));
            } else {
                toolbar.setAlpha(0f);
                toolbar.setVisibility(View.INVISIBLE);
            }
        });

        isExpanded = Option.getPodcastEpisodesDescriptionExpand();
        adapter.setShowDescriptions(isExpanded);
        animateDescriptionHeight(tvDescription, isExpanded);
        updateCollapseIcon();
    }

    private void toggleFavorite() {
        myLogI("--- USER CLICKS set FAVORITE");
        Pref.stopAnimateButtons(Pref.AnimatedButton.FAVORITE);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Podcast podcast = podcastDao.getPodcastByFeedId(podcastFeed.id);

            if (podcast == null) {
                PodcastHelper.addPodcastToDB(this, podcastFeed);
                podcast = AppDatabase.getDatabase(this).podcastDao().getPodcastByFeedId(podcastFeed.id);
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
            ImageHelper.processPendingImages(this, System.currentTimeMillis());
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
                downloadAllEpisodesToFolder(podcast, PODCAST_INDEX_ORG_SINCE);
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
        AppDatabase.databaseReadExecutor.execute(() -> {
            List<Episode> dbEpisodes = podcastEpisodeViewModel.toggleSortAndGetEpisodesFromDB(podcast.getId());
            List<DisplayableEpisode> sortedList = DisplayableEpisode.fromEpisodeList(dbEpisodes);
            runOnUiThread(() -> {
                updateAdapter(sortedList);
            });
        });
    }

    private void toggleCollapse() {
        isExpanded = !isExpanded;
        myLogI("-------- USER CLICKS COLLAPSE --  isExpanded= " + isExpanded);
        Option.setPodcastEpisodesDescriptionExpand(isExpanded);
        animateDescriptionHeight(tvDescription, isExpanded);
        adapter.setShowDescriptions(isExpanded); // show/hide item descriptions
        updateCollapseIcon();
    }

    private void updateFavoriteIconColor(boolean isOn) {
        int colorResId = isOn ? android.R.color.holo_red_light : R.color.gray_500;
        int color = ContextCompat.getColor(this, colorResId);
        if (btnFavoriteToolbar != null)
            btnFavoriteToolbar.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        if (btnFavoriteOverlay != null)
            btnFavoriteOverlay.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    private void updateAutoDownloadIconColor(boolean isOn) {
        int colorResId = isOn ? R.color.green_300 : R.color.gray_500;
        int color = ContextCompat.getColor(this, colorResId);
        if (btnAutoDownloadToolbar != null)
            btnAutoDownloadToolbar.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        if (btnAutoDownloadOverlay != null)
            btnAutoDownloadOverlay.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    // NEW SEARCH DIALOG
    private void showSearchDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.Search);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = ViewHelper.dp(this, 16);
        layout.setPadding(padding, padding / 2, padding, 0);

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(R.string.Search_2pt);
        input.setText(currentSearchQuery);
        input.setSingleLine(true);
        layout.addView(input);

        final android.widget.CheckBox cbDesc = new android.widget.CheckBox(this);
        cbDesc.setText(R.string.Episode_description);
        cbDesc.setChecked(searchInDescription);
        layout.addView(cbDesc);

        builder.setView(layout);

        builder.setPositiveButton(getString(R.string.Close), (dialog, which) -> {
            // just close, search is live
        });

        builder.setNeutralButton(getString(R.string.clear), (dialog, which) -> {
            input.setText("");
        });

        AlertDialog dialog = builder.create();

        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString();
                filterAndUpdateList();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });

        cbDesc.setOnCheckedChangeListener((buttonView, isChecked) -> {
            searchInDescription = isChecked;
            filterAndUpdateList();
        });

        dialog.show();
        // focus input
        input.requestFocus();
    }

    private void fetchEpisodes(boolean isRefresh) {
        myLogD("fetchEpisodes " + (isRefresh ? "refresh" : "no refresh"));
        long nbEpisodeFull = 0;

        // 1) Load DB immediately → optimistic UI
        AppDatabase.databaseReadExecutor.execute(() -> {
            List<Episode> dbEpisodes = podcastEpisodeViewModel.getEpisodesFromDB(podcast.getId());
            myLogD("DB episodes count: " + dbEpisodes.size());
            List<DisplayableEpisode> initial = DisplayableEpisode.fromEpisodeList(dbEpisodes);
            runOnUiThread(() -> {
                updateAdapter(initial);
            });
        });
        if (!isRefresh && podcast.lastCheck > System.currentTimeMillis()
                - 1000 * 60 * Var.PODCAST_INDEX_ORG_API_TIME_BETWEEN_PODCAST_CHECK_IN_MIN) {
            return;
        }

        // 2) Compute "since" from DB and then hit API
        AppDatabase.databaseReadExecutor.execute(() -> {
            long since;
            int maxEpisode;
            if (isRefresh) {
                since = 0;
                maxEpisode = Var.PODCAST_INDEX_ORG_API_MAX_RESULTS_FOR_EPISODES_REFRESH_MODE;
            } else {
                maxEpisode = Var.PODCAST_INDEX_ORG_API_MAX_RESULTS_FOR_EPISODES_NORMAL_MODE;
                Long lastPublished = podcastEpisodeViewModel.getLastPublishedForPodcastSync(podcast.getId()); // epoch
                                                                                                              // seconds
                since = (lastPublished == null) ? 0L : Math.max(0L, lastPublished - 60); // 30j : -(60*60*24*30)
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
                            myLogI("API CALL - returned episodes list size: " + apiEpisodes.size());
                            // 3) Persist new/updated from API
                            podcastEpisodeViewModel.insertEpisodesInDB(apiEpisodes, podcast.feedId);

                            // 4) Refresh DB and merge for display
                            AppDatabase.databaseReadExecutor.execute(() -> {
                                List<Episode> dbEpisodes = podcastEpisodeViewModel.getEpisodesFromDB(podcast.getId());
                                List<DisplayableEpisode> fullList = DisplayableEpisode
                                        .mergeDisplayableEpisodes(apiEpisodes, dbEpisodes);
                                int nbEpisodeFull = fullList.size();
                                myLog("Displayed episodes count: " + nbEpisodeFull);
                                runOnUiThread(() -> {
                                    if (isRefresh) {
                                        myToast(nbEpisodeFull + " " + getString(R.string.episodes));
                                    }
                                    updateAdapter(fullList);
                                });
                            });
                        }

                        @Override
                        public void onError(Exception e) {
                            // Fallback: DB-only (you already showed initial DB result; here we just end the
                            // spinner and warn)
                            myLogE("API CALL ERROR - " + e.getMessage());
                            AppDatabase.databaseReadExecutor.execute(() -> {
                                List<Episode> dbEpisodes = podcastEpisodeViewModel.getEpisodesFromDB(podcast.getId());
                                List<DisplayableEpisode> fallbackList = DisplayableEpisode.fromEpisodeList(dbEpisodes);

                                runOnUiThread(() -> {
                                    updateAdapter(fallbackList);
                                    tvDescription.setTextColor(getColor(R.color.orange_500));
                                    tvDescription.setText(getString(R.string.podcast_api_unavailable_fallback));
                                });
                            });
                        }
                    });
        });
    }

    private void downloadAllEpisodesToFolder(Podcast podcast, long since) {
        PodcastHelper.checkForNewEpisodesToAutoDownloadForPodcast(this, podcast, since);
    }

    private void goToPlaySection() {
        if (podcast == null) {
            myLogEE(null, "goToPlaySection() - podcast is null");
            AppDatabase.databaseReadExecutor.execute(() -> {
                podcast = AppDatabase.getDatabase(this).podcastDao().getPodcastByFeedId(podcastFeed.id);
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
            myLog("Podcast exist in DB but no Folder/savedPodcastBook exists (nothing downloaded yet)");
            return;
        }

        // user click sur image => open "Book"

        AppDatabase.databaseReadExecutor.execute(() -> {
            try {
                Folder folder = AppDatabase.getDatabase(getApplicationContext()).folderDao().getById(podcast.idFolder);
                if (folder == null)
                    return;

                // TODO we need a AudioService.isPlaying
                if (MediaService.isRunning && PlayList.getInstance() != null
                        && PlayList.getInstance().getZikFile() != null
                        && PlayList.getInstance().getZikFile().getIdFolder() == podcast.idFolder) {
                    startActivity(new Intent(this, ZikFileActivity.class).putExtra(Intents.EXTRA_FOLDER, folder));
                } else {
                    List<ZikFile> zikFilesList = AppDatabase.getDatabase(getApplicationContext())
                            .zikFileDao().getZikFiles(podcast.idFolder);

                    myLogI("nb ZikFiles in that Podcast Book : " + zikFilesList.size() + " - [" + folder.getName()
                            + "]");

                    // Switch to main thread for any UI / navigation
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed())
                            return;

                        if (!zikFilesList.isEmpty()) {
                            // closeExoPlayer();
                            startActivity(
                                    new Intent(this, ZikFileActivity.class).putExtra(Intents.EXTRA_FOLDER, folder));
                        } else {
                            myLogE("no ZikFiles in that folder !");
                            myToastE(getString(R.string.ErrorCouldNotLoadAudios_emptyfolder)); // main thread
                        }
                    });
                }
            } catch (Exception e) {
                myLogEE(e, "goToPlaySection2()");
                runOnUiThread(() -> myToastEE(null, getString(R.string.ErrorCouldNotLoadAudios)));
            }
        });
    }

    private void updateAdapter(List<DisplayableEpisode> displayableEpisodeList) {
        this.allEpisodes = displayableEpisodeList;
        filterAndUpdateList();
    }

    private void filterAndUpdateList() {
        List<DisplayableEpisode> filtered = new ArrayList<>();
        if (currentSearchQuery == null || currentSearchQuery.trim().isEmpty()) {
            filtered.addAll(allEpisodes);
        } else {
            String q = currentSearchQuery.toLowerCase(Locale.getDefault());
            for (DisplayableEpisode ep : allEpisodes) {
                if (ep.title != null && ep.title.toLowerCase(Locale.getDefault()).contains(q)) {
                    filtered.add(ep);
                    continue;
                }
                if (searchInDescription && ep.description != null
                        && ep.description.toLowerCase(Locale.getDefault()).contains(q)) {
                    filtered.add(ep);
                }
            }
        }

        adapter.setItems(filtered);
        adapter.notifyDataSetChanged();
        String tvStatsText = filtered.size() + "." + getString(R.string.ep);
        tvStats.setText(tvStatsText);
        tvToolbarStats.setText(tvStatsText);
    }

    // ROW CLICK CALLBACK
    @Override
    public void onPlayEpisode(DisplayableEpisode ep) {
        myLogD("onPlayEpisode [" + ep.title + "]");
        if (currentEpisode != null && currentEpisode.idEpisode == ep.idEpisode) {
            // Same episode toggled
        } else {
            playEpisode(ep);
        }
        currentEpisode = ep;
    }

    private void playEpisode(DisplayableEpisode ep) {
        if (ep == null)
            return;
        boolean online = NetworkHelper.isConnected(this);
        if (!online) {
            myToast(getString(R.string.no_internet_connection));
            return;
        }
        PodcastHelper.onPodcastClick(getApplicationContext(), ep, podcast, "PodcastEpisodesActivity - adapter callback: .onPlayEpisode()");

        adapter.setCurrentlyPlayingEpisodeId(ep.idEpisode);
    }

    @Override
    public void onOpenLocalEpisode(ZikFile zikFile) {
        StartPlayHelper.onZikFileFromPodcast(this, zikFile, this.getClass().getSimpleName() + ".onOpenLocalEpisode()",
                sortNewestFirst);
    }

    @Override
    public void onDownloadEpisode(DisplayableEpisode ep) {
        if (!enqueuedEpisodeIds.add(ep.idEpisode)) {
            myLog("download already enqueued for " + ep.title);
            return;
        }

        if (Option.getNetworkPolicyManualDownload().equals(NetworkHelper.NetworkPolicyManual.NETWORK_POLICY_UNMETERED)
                && !NetworkHelper.isUnmeteredConnected(this)) {
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
        if (!targetFolder.exists())
            targetFolder.mkdirs();
        List<PodcastEpisode> singleList = new ArrayList<>();
        singleList.add(displayableEpisode.toPodcastEpisode());
        PodcastDownloadManager.enqueueDownloads(this, feedId, singleList, targetFolder, null);
    }

    private void updateCollapseIcon() {
        btnCollapseOverlay.setImageDrawable(
                AppCompatResources.getDrawable(
                        this,
                        isExpanded ? R.drawable.ic_content_collapse_24 : R.drawable.ic_content_expand_24));
        btnCollapseToolbar.setImageDrawable(
                AppCompatResources.getDrawable(
                        this,
                        isExpanded ? R.drawable.ic_content_collapse_24 : R.drawable.ic_content_expand_24));
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
                    @Override
                    public void onAnimationEnd(Animator animation) {
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
                    @Override
                    public void onAnimationEnd(Animator animation) {
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

    private void stopAudioServiceIfRunning() {
        if (MediaService.isRunning) {
            Intent intentStopService = new Intent(this, MediaService.class).setAction(Intents.EXTRA_CMD_STOP)
                    .putExtra(Intents.EXTRA_CALLER, this.getClass().getSimpleName());
            try {
                // App au premier plan → safe, pas de règle des 5s
                startService(intentStopService);
            } catch (IllegalStateException e) {
                // Si jamais l’app est en arrière-plan, au pire on force l’arrêt
                myLogEE(e, "startService CMD_STOP failed");
                stopService(new Intent(this, MediaService.class));
            }
        }
    }

}
