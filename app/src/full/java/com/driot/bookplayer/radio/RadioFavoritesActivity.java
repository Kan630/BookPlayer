package com.driot.bookplayer.radio;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.nav.BaseBottomNavActivity;
import com.driot.bookplayer.adapter.FavoritesTouchHelperCallback;
import com.driot.bookplayer.adapter.RadioFavoritesRVAdapter;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.LoadingProgressHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.helpers.NetworkStatusRowController;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.player.PlaybackViewModel;
import com.driot.bookplayer.player.StartPlayHelper;
import com.driot.bookplayer.utils.NetworkStatusViewModel;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class RadioFavoritesActivity extends BaseBottomNavActivity {

    private RadioResultsViewModel viewModel;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvProgressMessage;
    private RadioFavoritesRVAdapter adapter;
    private RadioBrowserRepository repo; // for resolveUrl on play()
    private View dropZone;
    private ItemTouchHelper touchHelper;
    private boolean isHistoryMode = false;

    private final LoadingProgressHelper progressHelper = new LoadingProgressHelper();

    private boolean updatingToggleFromVm = false;

    @Override
    protected int getNavId() {
        return R.id.nav_radio;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_radio_results;
    }

    @Override
    protected boolean enableOngoingTaskOverlay() {
        return true;
    }

    private boolean hasInternet = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        tvProgressMessage = findViewById(R.id.tvProgressMessage);
        dropZone = findViewById(R.id.dragDeleteZone);

        View networkRowView = findViewById(R.id.includeNetworkStatus);
        NetworkStatusViewModel netVm = new ViewModelProvider(this).get(NetworkStatusViewModel.class);
        new NetworkStatusRowController(this, networkRowView, this, netVm);
        netVm.getStatus().observe(this, status -> hasInternet = status.hasInternet);

        int span = getResources().getInteger(R.integer.radio_grid_span);
        GridLayoutManager glm = new GridLayoutManager(this, span);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                // position 0 = header → take the whole row
                return position == 0 ? span : 1;
            }
        });
        recyclerView.setLayoutManager(glm);
        recyclerView.addItemDecoration(
                new ViewHelper.SpacesItemDecoration(ViewHelper.dp(this, Var.GRID_LAYOUT_SPACER_RADIO)));

        viewModel = new ViewModelProvider(this).get(RadioResultsViewModel.class);

        PlaybackViewModel playbackVm = new ViewModelProvider(this).get(PlaybackViewModel.class);
        playbackVm.getState().observe(this, state -> {
            if (state != null)
                adapter.setPlayingRadioStationUuid(state.radioStationUuid);
        });

        // Favorites vs History Toggle
        MaterialButtonToggleGroup group = findViewById(R.id.groupFavoriteVsHistory);
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (!isChecked)
                return; // ignore un-check events

            // IGNORE changes coming from VM/UI syncing
            if (updatingToggleFromVm) {
                myLogD("Toggle change from ViewModel sync, ignoring as user click.");
                return;
            }

            if (checkedId == R.id.btnRadioFavorites) {
                myLogI("--- user clicks favorites ---");
                viewModel.loadFavorites(RadioFavoritesActivity.this);
            } else if (checkedId == R.id.btnRadioHistory) {
                myLogI("--- user clicks history ---");
                viewModel.loadHistory(RadioFavoritesActivity.this);
            }
        });
        viewModel.initMode(this);

        adapter = new RadioFavoritesRVAdapter(new RadioFavoritesRVAdapter.OnActionListener() {
            @Override
            public void onPlay(RadioFavoriteItem f) {
                myLogI("--- user clicks 'favorite/history' radio item --- : " + f.name);

                if (!hasInternet) {
                    myToast(getString(R.string.no_internet_connection));
                    return;
                }

                final boolean renewOnClick = Option.getRadioRenewUrl();
                final boolean hasCachedUrl = f.last_url != null && !f.last_url.isEmpty();

                // -------------------------------------------------------------------------
                // FAST PATH:
                // - We already have a cached URL
                // - AND user did NOT request "renew on click"
                //
                // → Play immediately (no spinner), then refresh URL in background.
                // -------------------------------------------------------------------------
                if (hasCachedUrl && !renewOnClick) {
                    myLogD("Radio: using cached URL, scheduling background renew. last_url = [" + f.last_url + "]");

                    // 1) Immediate playback with cached URL
                    RadioHelper.onRadioFavoriteClick(
                            getApplicationContext(),
                            f,
                            f.last_url,
                            "RadioFavoritesActivity - onPlay() - using cached last_url");

                    // 2) Background best-effort renew (no UI spinner/toasts)
                    repo.resolveUrl(f.stationuuid, new Callback<UrlResolve>() { // RadioBrowser wants us to ping for
                                                                                // their STATS
                        @Override
                        public void onResponse(Call<UrlResolve> call, Response<UrlResolve> rsp) {
                            if (!rsp.isSuccessful() || rsp.body() == null ||
                                    rsp.body().url == null || rsp.body().url.isEmpty()) {
                                myLogW("background resolveUrl: no usable url for " + f.name);
                                return;
                            }
                            String newUrl = rsp.body().url;
                            if (newUrl.equals(f.last_url)) {
                                myLogD("background resolveUrl: url unchanged for " + f.name + " -> " + newUrl);
                                return;
                            }

                            myLogI("background resolveUrl success for " + f.name + " -> " + newUrl);
                            f.last_url = newUrl; // update in-memory item

                            // Persist in Room (see section 2)
                            viewModel.updateFavoriteLastUrl(
                                    getApplicationContext(),
                                    f.stationuuid,
                                    newUrl);
                        }

                        @Override
                        public void onFailure(Call<UrlResolve> call, Throwable t) {
                            myLogW("background resolveUrl failed for " + f.name + " : " + t);
                            // no UI feedback, cached url still works
                        }
                    });

                    return;
                }

                // -------------------------------------------------------------------------
                // SLOW / STRICT PATH:
                // - No cached URL (first time or old data)
                // - OR user option = "always renew URL before play"
                //
                // → Show spinner, wait for resolveUrl, then play.
                // -------------------------------------------------------------------------
                myLog("Option renew Url = " + renewOnClick + ", lastUrl = [" + f.last_url + "]"
                        + " => resolveUrl(" + f.stationuuid + ") - " + f.name);
                setProgressVisible(true, getString(R.string.checking_for_best_mirror));

                repo.resolveUrl(f.stationuuid, new Callback<UrlResolve>() { // RadioBrowser wants us to ping for their
                                                                            // STATS
                    @Override
                    public void onResponse(Call<UrlResolve> call, Response<UrlResolve> rsp) {
                        setProgressVisible(false, null);
                        String stream = null;
                        if (rsp.isSuccessful() && rsp.body() != null &&
                                rsp.body().url != null && !rsp.body().url.isEmpty()) {
                            stream = rsp.body().url;
                            myLogI("resolveUrl success : " + stream);
                            f.last_url = stream;

                            // Persist in Room
                            viewModel.updateFavoriteLastUrl(
                                    getApplicationContext(),
                                    f.stationuuid,
                                    stream);
                        } else if (hasCachedUrl) {
                            // fallback to previous last_url if we had it
                            stream = f.last_url;
                            myLogI("resolveUrl empty, fallback to last_url : " + stream);
                        }

                        if (stream != null) {
                            RadioHelper.onRadioFavoriteClick(
                                    getApplicationContext(),
                                    f,
                                    stream,
                                    "RadioFavoritesActivity - onPlay() - after url renewed");
                        } else {
                            myToastE(getString(R.string.radio_could_not_resolve_url));
                        }
                    }

                    @Override
                    public void onFailure(Call<UrlResolve> call, Throwable t) {
                        setProgressVisible(false, null);
                        if (NetworkHelper.isUnknownHost(t)) {
                            myToastE(getString(R.string.no_internet_connection));
                        } else {
                            myLogEE(t, "resolveUrl failed");
                            myToastE(getString(R.string.error_radio_renew_url));

                            // Fallback to cached URL if any
                            if (f.last_url != null && !f.last_url.isEmpty()) {
                                RadioHelper.onRadioFavoriteClick(
                                        getApplicationContext(),
                                        f,
                                        f.last_url,
                                        "RadioFavoritesActivity - onPlay() - url NOT renewed (fallback)");
                            }
                        }
                    }
                });
            }

            @Override
            public void onUnfavorite(RadioFavoriteItem f) {
                myLogI("--- user Unfavorite radio item --- : " + f.name);
                // Remove and refresh (reuse VM’s toggle which expects a ApiStation)
                viewModel.removeFavoriteUuid(RadioFavoritesActivity.this, f.stationuuid);
            }

            @Override
            public void onPersistOrder(List<RadioFavoriteItem> newOrder) {
                myLogI("--- user change favorite radio station order --- ");
                viewModel.reorderFavorites(RadioFavoritesActivity.this, newOrder);
            }
        });
        recyclerView.setAdapter(adapter);

        // Enable dragging
        FavoritesTouchHelperCallback cb = new FavoritesTouchHelperCallback(recyclerView, dropZone, adapter);
        touchHelper = new ItemTouchHelper(cb);
        touchHelper.attachToRecyclerView(recyclerView);
        adapter.setOnStartDragListener(vh -> {
            // Only allow drag when in favorites (not history)
            if (!isHistoryMode && touchHelper != null) {
                touchHelper.startDrag(vh);
            }
        });

        // repo for resolveUrl
        repo = new RadioBrowserRepository(
                this,
                /* discoverMirrors */ false, // keep async discovery for later if you want
                /* log level */ com.driot.bookplayer.global.Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL);

        viewModel.getShowingHistory().observe(this, isHistory -> {
            boolean history = Boolean.TRUE.equals(isHistory);
            isHistoryMode = history;

            // Select correct toggle button without triggering clicks
            updatingToggleFromVm = true;
            group.check(history ? R.id.btnRadioHistory : R.id.btnRadioFavorites);
            updatingToggleFromVm = false;

            // Drag & drop only in favorites
            if (history) {
                dropZone.setVisibility(View.GONE);
                if (touchHelper != null) {
                    touchHelper.attachToRecyclerView(null); // disable drag
                }
            } else {
                if (touchHelper != null) {
                    touchHelper.attachToRecyclerView(recyclerView); // enable drag
                }
            }

            adapter.setHistoryMode(history);
        });

        setProgressVisible(true, getString(R.string.loading));
        viewModel.getFavoriteItems().observe(this, favorites -> {
            setProgressVisible(false, null);
            adapter.setItems(favorites);
        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        progressHelper.stop();
    }

    private void setProgressVisible(boolean visible, String progressMessage) {
        if (visible) {
            progressBar.setVisibility(View.VISIBLE);
            if (progressMessage != null && !progressMessage.isEmpty()) {
                progressHelper.start(tvProgressMessage, new LoadingProgressHelper.MessageProvider() {
                    @NonNull
                    @Override
                    public String getInitialMessage() {
                        return progressMessage;
                    }

                    @NonNull
                    @Override
                    public String getTickMessage(long elapsedSec) {
                        return progressMessage + " (" + elapsedSec + "s)";
                    }
                });
            } else {
                progressHelper.stop();
            }
        } else {
            progressBar.setVisibility(View.GONE);
            progressHelper.stop();
        }
    }
}
