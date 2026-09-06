package com.driot.bookplayer.podcasts;

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

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.nav.FullActivity;
import com.driot.bookplayer.activities.ZikFileActivity;
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
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.player.MediaService;
import com.driot.bookplayer.player.PlayList;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.player.StartPlayHelper;
import com.google.android.material.appbar.AppBarLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class PodcastEpisodeActivity extends FullActivity
        implements PodcastEpisodeRVAdapter.EpisodeClickHandler {

    private TextView tvTitle, tvDescription, tvApiWarning, tvStats, tvToolbarStats, tvSearchStat;
    private ImageView ivCover, ivMiniCover;
    private RecyclerView recyclerEpisodes;
    private PodcastEpisodeRVAdapter adapter;

    private Podcast podcast;
    private PodcastFeed podcastFeed;

    // ADD these:
    private ImageButton btnFavoriteToolbar, btnAutoDownloadToolbar, btnSearchToolbar, btnRefreshToolbar, btnSortToolbar,
            btnFilterToolbar, btnCollapseToolbar;
    private ImageButton btnFavoriteOverlay, btnAutoDownloadOverlay, btnSearchOverlay, btnRefreshOverlay, btnSortOverlay,
            btnFilterOverlay, btnCollapseOverlay;

    private PodcastDao podcastDao;

    private PodcastEpisodeViewModel podcastEpisodeViewModel;

    private List<DisplayableEpisode> allEpisodes = new ArrayList<>();

    private boolean isExpanded;
    private boolean sortNewestFirst;
    private DisplayableEpisode currentEpisode;
    private java.util.Set<Long> enqueuedEpisodeIds;

    private LinearLayout layoutSearch;
    private android.widget.EditText etSearch;
    private android.widget.CheckBox cbSearchInDescription;
    private ImageButton btnClearSearch;

    private int backPressCount = 0;
    private long lastBackPressTime = 0;

    private AppBarLayout appBar;
    private Toolbar toolbar;

    @Override
    protected int getNavSectionId() {
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
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("search_visible", layoutSearch.getVisibility() == View.VISIBLE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        // Registered after super.onCreate() so it fires before BaseActivity's own back
        // callback (OnBackPressedDispatcher runs the most-recently-added callback first).
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (layoutSearch != null && layoutSearch.getVisibility() == View.VISIBLE) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastBackPressTime < 5000) {
                        backPressCount++;
                    } else {
                        backPressCount = 1;
                    }
                    lastBackPressTime = currentTime;

                    if (backPressCount >= 3) {
                        setEnabled(false);
                        getOnBackPressedDispatcher().onBackPressed();
                    } else {
                        toggleSearch();
                    }
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        if (Option.getScreenOrientationLock()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        }

        tvTitle = findViewById(R.id.tvPodcastTitle);
        tvDescription = findViewById(R.id.tvPodcastDescription);
        tvApiWarning = findViewById(R.id.tvApiWarning);
        tvApiWarning.setVisibility(View.GONE);
        tvStats = findViewById(R.id.tvPodcastStat);
        tvToolbarStats = findViewById(R.id.tvToolbarStats);
        tvSearchStat = findViewById(R.id.tvSearchStat);

        ivCover = findViewById(R.id.ivPodcastCover);
        ivMiniCover = findViewById(R.id.ivMiniCover);

        recyclerEpisodes = findViewById(R.id.rvEpisodes);

        // TOOLBAR actions
        btnFavoriteToolbar = findViewById(R.id.btnFavoriteToolbar);
        btnAutoDownloadToolbar = findViewById(R.id.btnAutoDownloadToolbar);
        btnSearchToolbar = findViewById(R.id.btnSearchToolbar);
        btnRefreshToolbar = findViewById(R.id.btnRefreshToolbar);
        btnSortToolbar = findViewById(R.id.btnSortToolbar);
        btnFilterToolbar = findViewById(R.id.btnFilterToolbar);
        btnCollapseToolbar = findViewById(R.id.btnCollapseToolbar);

        // OVERLAY actions on top of the big cover
        btnFavoriteOverlay = findViewById(R.id.btnFavoriteOverlay);
        btnAutoDownloadOverlay = findViewById(R.id.btnAutoDownloadOverlay);
        btnSearchOverlay = findViewById(R.id.btnSearchOverlay);
        btnRefreshOverlay = findViewById(R.id.btnRefreshOverlay);
        btnSortOverlay = findViewById(R.id.btnSortOverlay);
        btnFilterOverlay = findViewById(R.id.btnFilterOverlay);
        btnCollapseOverlay = findViewById(R.id.btnCollapseOverlay);

        layoutSearch = findViewById(R.id.layoutSearch);
        layoutSearch.setVisibility(View.GONE);
        etSearch = findViewById(R.id.etSearch);
        cbSearchInDescription = findViewById(R.id.cbSearchInDescription);
        btnClearSearch = findViewById(R.id.btnClearSearch);

        appBar = findViewById(R.id.appBar);
        toolbar = findViewById(R.id.toolbar);
        toolbar.setAlpha(0f);
        toolbar.setVisibility(View.INVISIBLE);

        podcastDao = AppDatabase.getDatabase(this).podcastDao();

        podcast = getIntent().getParcelableExtra("podcast");

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

        podcastEpisodeViewModel = new ViewModelProvider(this).get(PodcastEpisodeViewModel.class);

        // Sync initial state to ViewModel if not already set
        if (podcastEpisodeViewModel.getSortNewestFirstLive().getValue() == null) {
            podcastEpisodeViewModel.getSortNewestFirstLive().setValue(sortNewestFirst);
        }
        if (podcastEpisodeViewModel.getIsExpandedLive().getValue() == null) {
            podcastEpisodeViewModel.getIsExpandedLive().setValue(Option.getPodcastEpisodesDescriptionExpand());
        }
        enqueuedEpisodeIds = podcastEpisodeViewModel.getEnqueuedEpisodeIds();
        podcastEpisodeViewModel.getPodcastLiveByFeedId(podcastFeed.id).observe(this, updatedPodcast -> {
            if (updatedPodcast != null) {
                this.podcast = updatedPodcast;
                // Auto-download can now also be toggled from PodcastDownloadSettingsActivity,
                // so keep the icon in sync whenever the Podcast row changes.
                updateAutoDownloadIconColor(updatedPodcast.autoDownload);
                if (adapter != null) {
                    adapter.setEpisodeCoverStatus(updatedPodcast.episodeCoverStatus);
                }
            }
        });

        podcastEpisodeViewModel.getEpisodesLive().observe(this, episodes -> {
            if (episodes != null) {
                updateAdapter(episodes);
            }
        });

        podcastEpisodeViewModel.getSearchQueryLive().observe(this, q -> {
            if (!etSearch.getText().toString().equals(q)) {
                etSearch.setText(q);
            }
        });

        podcastEpisodeViewModel.getSearchInDescriptionLive().observe(this, inDesc -> {
            if (cbSearchInDescription.isChecked() != inDesc) {
                cbSearchInDescription.setChecked(inDesc);
            }
        });

        podcastEpisodeViewModel.getSortNewestFirstLive().observe(this, sort -> {
            this.sortNewestFirst = sort;
        });

        podcastEpisodeViewModel.getShowOnlyNeverDownloadedLive().observe(this, showOnlyNeverDownloaded -> {
            updateFilterIconColor(Boolean.TRUE.equals(showOnlyNeverDownloaded));
            filterAndUpdateList();
        });

        podcastEpisodeViewModel.getIsExpandedLive().observe(this, expanded -> {
            if (this.isExpanded != expanded) {
                this.isExpanded = expanded;
                animateDescriptionHeight(tvDescription, expanded);
                adapter.setShowDescriptions(expanded);
                updateCollapseIcon();
            }
        });

        podcastEpisodeViewModel.getCurrentEpisodeLive().observe(this, ep -> {
            this.currentEpisode = ep;
        });

        podcastEpisodeViewModel.getToastMessageLive().observe(this, msg -> {
            if (msg != null) {
                myToast(msg);
            }
        });

        podcastEpisodeViewModel.getApiErrorLive().observe(this, error -> {
            if (error != null) {
                tvApiWarning.setText(getString(R.string.podcast_api_unavailable_fallback));
                tvApiWarning.setVisibility(View.VISIBLE);
            } else {
                tvApiWarning.setVisibility(View.GONE);
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
        updateFilterIconColor(Boolean.TRUE.equals(podcastEpisodeViewModel.getShowOnlyNeverDownloadedLive().getValue()));

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
            if (podcastEpisodeViewModel.getEpisodesLive().getValue() == null) {
                podcastEpisodeViewModel.fetchEpisodes(this, podcast, false);
            }
        }

        View.OnClickListener favoriteClick = v -> toggleFavorite();
        View.OnClickListener autoDownloadClick = v -> startActivity(
                new Intent(this, PodcastDownloadSettingsActivity.class).putExtra("podcast", podcast));
        View.OnClickListener filterClick = v -> toggleNeverDownloadedFilter();
        View.OnClickListener searchClick = v -> toggleSearch();
        View.OnClickListener refreshClick = v -> {
            FirebaseAnalyticsHelper.tellAnalyticsPodcastRefresh(podcastFeed.title);
            myLogI("-------- USER CLICKS REFRESH -----");
            podcastEpisodeViewModel.fetchEpisodes(this, podcast, true);
        };
        View.OnClickListener sortClick = v -> toggleSort();
        View.OnClickListener collapseClick = v -> toggleCollapse();

        // Toolbar
        btnFavoriteToolbar.setOnClickListener(favoriteClick);
        btnAutoDownloadToolbar.setOnClickListener(autoDownloadClick);
        btnSearchToolbar.setOnClickListener(searchClick);
        btnRefreshToolbar.setOnClickListener(refreshClick);
        btnSortToolbar.setOnClickListener(sortClick);
        btnFilterToolbar.setOnClickListener(filterClick);
        btnCollapseToolbar.setOnClickListener(collapseClick);

        // Overlay
        btnFavoriteOverlay.setOnClickListener(favoriteClick);
        btnAutoDownloadOverlay.setOnClickListener(autoDownloadClick);
        btnSearchOverlay.setOnClickListener(searchClick);
        btnRefreshOverlay.setOnClickListener(refreshClick);
        btnSortOverlay.setOnClickListener(sortClick);
        btnFilterOverlay.setOnClickListener(filterClick);
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

        isExpanded = podcastEpisodeViewModel.getIsExpandedLive().getValue();
        adapter.setShowDescriptions(isExpanded);
        // animateDescriptionHeight(tvDescription, isExpanded); // Initial state set
        // without animation
        tvDescription.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        updateCollapseIcon();

        setupSearchListeners();

        if (savedInstanceState != null) {
            boolean searchVisible = savedInstanceState.getBoolean("search_visible", false);
            if (searchVisible) {
                toggleSearch();
            }
        }
    }

    private void setupSearchListeners() {
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                podcastEpisodeViewModel.getSearchQueryLive().setValue(s.toString());
                filterAndUpdateList();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });

        cbSearchInDescription.setOnCheckedChangeListener((buttonView, isChecked) -> {
            podcastEpisodeViewModel.getSearchInDescriptionLive().setValue(isChecked);
            filterAndUpdateList();
        });

        btnClearSearch.setOnClickListener(v -> {
            toggleSearch();
        });
    }

    private void toggleSearch() {
        View bottomNav = findViewById(R.id.bottomNav);
        View miniNowPlaying = findViewById(R.id.miniNowPlaying);
        View collapsingHeader = findViewById(R.id.collapsing);

        if (layoutSearch.getVisibility() == View.VISIBLE) {
            layoutSearch.setVisibility(View.GONE);
            if (bottomNav != null)
                bottomNav.setVisibility(View.VISIBLE);
            if (miniNowPlaying != null)
                miniNowPlaying.setVisibility(View.VISIBLE);
            if (collapsingHeader != null)
                collapsingHeader.setVisibility(View.VISIBLE);

            podcastEpisodeViewModel.getSearchQueryLive().setValue("");
            etSearch.setText("");
            ViewHelper.hideKeyboard(this, etSearch);
            filterAndUpdateList();

            backPressCount = 0;
            lastBackPressTime = 0;
        } else {
            layoutSearch.setVisibility(View.VISIBLE);
            if (bottomNav != null)
                bottomNav.setVisibility(View.GONE);
            if (miniNowPlaying != null)
                miniNowPlaying.setVisibility(View.GONE);
            if (collapsingHeader != null)
                collapsingHeader.setVisibility(View.GONE);

            etSearch.setText(podcastEpisodeViewModel.getSearchQueryLive().getValue());
            cbSearchInDescription
                    .setChecked(Boolean.TRUE.equals(podcastEpisodeViewModel.getSearchInDescriptionLive().getValue()));
            etSearch.requestFocus();
            ViewHelper.showKeyboard(this, etSearch);
        }
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
            if (podcast.isFavorite) {
                myToast(getString(R.string.podcast_favorite_add));
            }
            // Auto-download no longer requires favoriting first (and isn't reset by
            // unfavoriting) - the two are only linked the other way, in setAutoDownload().
            podcastDao.update(podcast);

            boolean favoriteState = podcast.isFavorite;

            runOnUiThread(() -> updateFavoriteIconColor(favoriteState));
            ImageHelper.processPendingImages(this, System.currentTimeMillis(), "podcast episode activity toggle favorites");
            FirebaseAnalyticsHelper.tellAnalyticsPodcastFavorite(podcast.title, podcast.language);
        });
    }

    private void toggleNeverDownloadedFilter() {
        boolean newValue = !Boolean.TRUE.equals(podcastEpisodeViewModel.getShowOnlyNeverDownloadedLive().getValue());
        myLogI("-------- USER CLICKS FILTER (never downloaded) -- newValue= " + newValue);
        podcastEpisodeViewModel.getShowOnlyNeverDownloadedLive().setValue(newValue);

        // Episodes downloaded via the per-item download button only update their idZikFile in
        // the DB and in the adapter's own per-item live ZikFile observer - the allEpisodes list
        // held here is never refreshed for that, so it can still show a since-downloaded episode
        // as never-downloaded. Re-fetch from DB on toggle, same fix toggleSort() already uses.
        AppDatabase.databaseReadExecutor.execute(() -> {
            List<Episode> dbEpisodes = podcastEpisodeViewModel.getEpisodesFromDB(podcast.getId());
            List<DisplayableEpisode> refreshed = DisplayableEpisode.fromEpisodeList(dbEpisodes);
            runOnUiThread(() -> updateAdapter(refreshed));
        });
    }

    private void toggleSort() {
        boolean newSort = !sortNewestFirst;
        myLogI("-------- USER CLICKS SORT --  sortNewestFirst= " + newSort);
        podcastEpisodeViewModel.getSortNewestFirstLive().setValue(newSort);
        AppDatabase.databaseReadExecutor.execute(() -> {
            List<Episode> dbEpisodes = podcastEpisodeViewModel.toggleSortAndGetEpisodesFromDB(podcast.getId());
            List<DisplayableEpisode> sortedList = DisplayableEpisode.fromEpisodeList(dbEpisodes);
            runOnUiThread(() -> {
                updateAdapter(sortedList);
            });
        });
    }

    private void toggleCollapse() {
        boolean newExpanded = !isExpanded;
        myLogI("-------- USER CLICKS COLLAPSE --  isExpanded= " + newExpanded);
        Option.setPodcastEpisodesDescriptionExpand(newExpanded);
        podcastEpisodeViewModel.getIsExpandedLive().setValue(newExpanded);
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

    private void updateFilterIconColor(boolean isOn) {
        int colorResId = isOn ? R.color.green_300 : R.color.gray_500;
        int color = ContextCompat.getColor(this, colorResId);
        if (btnFilterToolbar != null)
            btnFilterToolbar.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        if (btnFilterOverlay != null)
            btnFilterOverlay.setColorFilter(color, PorterDuff.Mode.SRC_IN);
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
        String query = podcastEpisodeViewModel.getSearchQueryLive().getValue();
        boolean inDesc = Boolean.TRUE.equals(podcastEpisodeViewModel.getSearchInDescriptionLive().getValue());

        if (query == null || query.trim().isEmpty()) {
            filtered.addAll(allEpisodes);
        } else {
            String q = query.toLowerCase(Locale.getDefault());
            for (DisplayableEpisode ep : allEpisodes) {
                if (ep.title != null && ep.title.toLowerCase(Locale.getDefault()).contains(q)) {
                    filtered.add(ep);
                    continue;
                }
                if (inDesc && ep.description != null
                        && ep.description.toLowerCase(Locale.getDefault()).contains(q)) {
                    filtered.add(ep);
                }
            }
        }

        if (Boolean.TRUE.equals(podcastEpisodeViewModel.getShowOnlyNeverDownloadedLive().getValue())) {
            List<DisplayableEpisode> neverDownloaded = new ArrayList<>();
            for (DisplayableEpisode ep : filtered) {
                if (ep.idZikFile == null && ep.date_delete == null) {
                    neverDownloaded.add(ep);
                }
            }
            filtered = neverDownloaded;
        }

        adapter.setItems(filtered);
        adapter.notifyDataSetChanged();
        String tvStatsText = filtered.size() + "." + getString(R.string.ep);
        tvStats.setText(tvStatsText);
        tvToolbarStats.setText(tvStatsText);
        if (tvSearchStat != null)
            tvSearchStat.setText(tvStatsText);
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
        podcastEpisodeViewModel.getCurrentEpisodeLive().setValue(ep);
    }

    private void playEpisode(DisplayableEpisode ep) {
        if (ep == null)
            return;
        boolean online = NetworkHelper.isConnected(this);
        if (!online) {
            myToast(getString(R.string.no_internet_connection));
            return;
        }
        PodcastHelper.onPodcastClick(getApplicationContext(), ep, podcast,
                "PodcastEpisodesActivity - adapter callback: .onPlayEpisode()");

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

}
