package com.driot.bookplayer.radio;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.myLogI;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
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
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.nav.BaseBottomNavActivity;
import com.driot.bookplayer.adapter.FavoritesTouchHelperCallback;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.LoadingProgressHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.helpers.NetworkStatusRowController;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.nav.NavHelper;
import com.driot.bookplayer.player.PlaybackUiBus;
import com.driot.bookplayer.player.PlaybackUiState;
import com.driot.bookplayer.player.PlaybackViewModel;
import com.driot.bookplayer.utils.NetworkStatusViewModel;

import com.driot.bookplayer.db.RadioStation;
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

    private ScaleGestureDetector scaleDetector;

    private final LoadingProgressHelper progressHelper = new LoadingProgressHelper();

    /** Back from favorites goes up to the radio search root, not straight to MainActivity. */
    @Override
    protected Class<? extends com.driot.bookplayer.nav.BaseBottomNavActivity> getSectionParent() {
        return GetRadioActivity.class;
    }

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

        recyclerView = findViewById(R.id.recyclerView);
        InsetHelper.applyInsetsForScrollableBehindNavBar(this, recyclerView);

        progressBar = findViewById(R.id.progressBar);
        tvProgressMessage = findViewById(R.id.tvProgressMessage);
        dropZone = findViewById(R.id.dragDeleteZone);

        View networkRowView = findViewById(R.id.includeNetworkStatus);
        NetworkStatusViewModel netVm = new ViewModelProvider(this).get(NetworkStatusViewModel.class);
        new NetworkStatusRowController(this, networkRowView, this, netVm);
        netVm.getStatus().observe(this, status -> hasInternet = status.hasInternet);

        int minSpan = getResources().getInteger(R.integer.radio_grid_span_min);     // add this to integers.xml
        int maxSpan = getResources().getInteger(R.integer.radio_grid_span_max);     // add this
        int defaultSpan = getResources().getInteger(R.integer.radio_grid_span_station);

        final GridLayoutManager glm = new GridLayoutManager(this, defaultSpan);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return position == 0 ? defaultSpan : 1;
            }
        });
        recyclerView.setLayoutManager(glm);

        recyclerView.addItemDecoration(
                new ViewHelper.SpacesItemDecoration(ViewHelper.dp(this, Var.GRID_LAYOUT_SPACER_RADIO)));

// Load saved value with proper clamping
        int savedSpan = Option.getRadioGridLayoutSpan();
        savedSpan = Math.max(minSpan, Math.min(maxSpan, savedSpan));

        glm.setSpanCount(savedSpan);

        final int[] currentSpan = {savedSpan};

        scaleDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {

                    private float accumulatedScale = 1f;

                    @Override
                    public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
                        accumulatedScale = 1f;
                        return true;
                    }

                    @Override
                    public boolean onScale(@NonNull ScaleGestureDetector detector) {
                        accumulatedScale *= detector.getScaleFactor();

                        // Pinch out → fewer columns (bigger cards)
                        if (accumulatedScale > 1.15f) {
                            int newSpan = currentSpan[0] - 1;
                            newSpan = Math.max(minSpan, Math.min(maxSpan, newSpan));   // proper clamp

                            if (newSpan != currentSpan[0]) {
                                currentSpan[0] = newSpan;
                                applyNewSpan(glm, newSpan);
                                accumulatedScale = 1f;
                            }
                        }
                        // Pinch in → more columns (smaller cards)
                        else if (accumulatedScale < 0.85f) {
                            int newSpan = currentSpan[0] + 1;
                            newSpan = Math.max(minSpan, Math.min(maxSpan, newSpan));   // proper clamp

                            if (newSpan != currentSpan[0]) {
                                currentSpan[0] = newSpan;
                                applyNewSpan(glm, newSpan);
                                accumulatedScale = 1f;
                            }
                        }

                        return true;
                    }

                    @Override
                    public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
                        // Save preference when gesture finishes
                        Option.setRadioGridLayoutSpan(currentSpan[0]);
                        myLogI("Grid span changed to: " + currentSpan[0]);
                    }
                });

        attachTouchLogic();

        viewModel = new ViewModelProvider(this).get(RadioResultsViewModel.class);

        adapter = new RadioFavoritesRVAdapter(new RadioFavoritesRVAdapter.OnActionListener() {
            @Override
            public void onPlay(RadioStation radioStation) {
                myLogI("--- user clicks 'favorite/history' radio item --- : " + radioStation.name);

                // check if already playing ---
                PlaybackViewModel playbackVm = new ViewModelProvider(RadioFavoritesActivity.this)
                        .get(PlaybackViewModel.class);
                PlaybackUiState state = playbackVm.getState().getValue();
                if (state != null && Var.PLAY_MODE_RADIO.equals(state.playMode)) {
                    if (radioStation.id == state.trackId) {
                        myLog("already playing => opening detail activity");
                        NavHelper.openRadioStationActivity(RadioFavoritesActivity.this,
                                state.trackId);
                        return;
                    }
                }

                if (!hasInternet) {
                    myToast(getString(R.string.no_internet_connection));
                    return;
                }

                PlaybackUiBus.get().setLoadPhase(Intents.PHASE_LOADING_RADIO);

                final boolean renewOnClick = Option.getRadioRenewUrl();
                final boolean hasCachedUrl = radioStation.url_resolved != null && !radioStation.url_resolved.isEmpty();

                if (hasCachedUrl && !renewOnClick) {
                    myLogD("Radio: using cached URL, scheduling background renew. url_resolved = ["
                            + radioStation.url_resolved
                            + "]");

                    // 1) Immediate playback with cached URL
                    RadioHelper.play(
                            getApplicationContext(),
                            radioStation,
                            radioStation.url_resolved,
                            "RadioFavoritesActivity - onPlay() - using cached url_resolved");

                    // 2) Background best-effort renew (no UI spinner/toasts)
                    repo.resolveUrl(radioStation.stationuuid, new Callback<UrlResolve>() {
                        @Override
                        public void onResponse(Call<UrlResolve> call, Response<UrlResolve> rsp) {
                            if (!rsp.isSuccessful() || rsp.body() == null ||
                                    rsp.body().url == null || rsp.body().url.isEmpty()) {
                                myLogW("background resolveUrl: no usable url for " + radioStation.name);
                                return;
                            }
                            String newUrl = rsp.body().url;
                            if (newUrl.equals(radioStation.url_resolved)) {
                                myLogD("background resolveUrl: url unchanged for " + radioStation.name + " -> "
                                        + newUrl);
                                return;
                            }

                            myLogI("background resolveUrl success for " + radioStation.name + " -> " + newUrl);
                            radioStation.url_resolved = newUrl; // update in-memory item

                            // Persist in Room
                            viewModel.updateFavoriteLastUrl(
                                    getApplicationContext(),
                                    radioStation.stationuuid,
                                    newUrl);
                        }

                        @Override
                        public void onFailure(Call<UrlResolve> call, Throwable t) {
                            myLogW("background resolveUrl failed for " + radioStation.name + " : " + t);
                        }
                    });

                    return;
                }

                myLog("Option renew Url = " + renewOnClick + ", url_resolved = [" + radioStation.url_resolved + "]"
                        + " => resolveUrl(" + radioStation.stationuuid + ") - " + radioStation.name);
                setProgressVisible(true, getString(R.string.checking_for_best_mirror));

                repo.resolveUrl(radioStation.stationuuid, new Callback<UrlResolve>() {
                    @Override
                    public void onResponse(Call<UrlResolve> call, Response<UrlResolve> rsp) {
                        setProgressVisible(false, null);
                        String stream = null;
                        if (rsp.isSuccessful() && rsp.body() != null &&
                                rsp.body().url != null && !rsp.body().url.isEmpty()) {
                            stream = rsp.body().url;
                            myLogI("resolveUrl success : " + stream);
                            radioStation.url_resolved = stream;

                            // Persist in Room
                            viewModel.updateFavoriteLastUrl(
                                    getApplicationContext(),
                                    radioStation.stationuuid,
                                    stream);
                        } else if (hasCachedUrl) {
                            stream = radioStation.url_resolved;
                            myLogI("resolveUrl empty, fallback to url_resolved : " + stream);
                        }

                        if (stream != null) {
                            RadioHelper.play(
                                    getApplicationContext(),
                                    radioStation,
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

                            if (radioStation.url_resolved != null && !radioStation.url_resolved.isEmpty()) {
                                RadioHelper.play(
                                        getApplicationContext(),
                                        radioStation,
                                        radioStation.url_resolved,
                                        "RadioFavoritesActivity - onPlay() - url NOT renewed (fallback)");
                            }
                        }
                    }
                });
            }

            @Override
            public void onUnfavorite(RadioStation f) {
                myLogI("--- user Unfavorite radio item --- : " + f.name);
                viewModel.removeFavoriteUuid(RadioFavoritesActivity.this, f.stationuuid);
            }

            @Override
            public void onPersistOrder(List<RadioStation> newOrder) {
                myLogI("--- user change favorite radio station order --- ");
                viewModel.reorderFavorites(RadioFavoritesActivity.this, newOrder);
            }

            @Override
            public void onToggleFavorites() {
                myLogI("--- user clicks favorites ---");
                viewModel.loadFavorites(RadioFavoritesActivity.this);
            }

            @Override
            public void onToggleHistory() {
                myLogI("--- user clicks history ---");
                viewModel.loadHistory(RadioFavoritesActivity.this);
            }
        });
        adapter.setFaviconCache(viewModel.getFaviconCache());
        recyclerView.setAdapter(adapter);

        PlaybackViewModel playbackVm = new ViewModelProvider(this).get(PlaybackViewModel.class);
        playbackVm.getState().observe(this, state -> {
            if (state != null)
                adapter.setPlayingRadioStation(state.trackId);
        });

        viewModel.initMode(this);

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

        long autoScrollTrackIdFromIntent = getIntent().getLongExtra(Intents.EXTRA_OPEN_FROM_TRACK_ID, -1);
        final long[] autoScrollTrackId = { autoScrollTrackIdFromIntent };

        setProgressVisible(true, getString(R.string.loading));
        viewModel.getFavoriteItems().observe(this, favorites -> {
            setProgressVisible(false, null);
            adapter.setItems(favorites, isHistoryMode);

            if (autoScrollTrackId[0] > 0) {
                int pos = adapter.getPositionForTrackId(autoScrollTrackId[0]);
                if (pos != RecyclerView.NO_POSITION) {
                    myLogD("Auto-scrolling to Id=" + autoScrollTrackId[0] + " at pos=" + pos);
                    final int scrollPos = pos;
                    recyclerView.postDelayed(() -> {
                        if (!isFinishing()) {
                            recyclerView.smoothScrollToPosition(scrollPos);
                        }
                    }, 300);
                    autoScrollTrackId[0] = -1; // Done
                } else if (!isHistoryMode) {
                    myLogD("Id=" + autoScrollTrackId[0] + " not in favorites, trying history");
                    viewModel.loadHistory(RadioFavoritesActivity.this);
                } else {
                    myLogE("Id=" + autoScrollTrackId[0] + " not found in favorites nor history");
                    autoScrollTrackId[0] = -1; // Not found anywhere
                }
            }
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

    private void attachTouchLogic() {
        recyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                scaleDetector.onTouchEvent(e);

                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    myLogI("TOUCH Reader: ACTION_DOWN " + e.getX() + "," + e.getY());
                } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    myLogI("TOUCH Reader: ACTION_MOVE " + e.getX() + "," + e.getY());
                } else if (e.getAction() == MotionEvent.ACTION_UP) {
                    myLogI("TOUCH Reader: ACTION_UP");
                } else if (e.getAction() == MotionEvent.ACTION_CANCEL) {
                    myLogI("TOUCH Reader: ACTION_CANCEL");
                }
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                scaleDetector.onTouchEvent(e);
            }
        });
    }

    private void applyNewSpan(GridLayoutManager glm, int newSpan) {
        glm.setSpanCount(newSpan);
        updateSpanSizeLookup(glm, newSpan);
        recyclerView.requestLayout();
    }

    private void updateSpanSizeLookup(GridLayoutManager glm, int spanCount) {
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return position == 0 ? spanCount : 1;   // header takes full row
            }
        });
    }}
