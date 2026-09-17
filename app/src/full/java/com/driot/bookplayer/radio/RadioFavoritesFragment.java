package com.driot.bookplayer.radio;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.helpers.GridScaleGestureHelper;
import com.driot.bookplayer.adapter.FavoritesTouchHelperCallback;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.LoadingProgressHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.helpers.NetworkStatusRowController;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.nav.NavHelper;
import com.driot.bookplayer.player.PlaybackUiBus;
import com.driot.bookplayer.player.PlaybackUiState;
import com.driot.bookplayer.player.PlaybackViewModel;
import com.driot.bookplayer.utils.NetworkStatusViewModel;
import com.driot.bookplayer.utils.log.LoggingFragment;

import com.driot.bookplayer.db.RadioStation;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Favorites/history list, reached from GetRadioFragment. Back navigation to the radio
 * search root is handled automatically by the nav graph back stack (it's only ever
 * pushed on top of getRadioFragment), replacing the former getSectionParent() override.
 * Converted from RadioFavoritesActivity - see [[radio_deeplink_applinks_fix]].
 */
@AndroidEntryPoint
public class RadioFavoritesFragment extends LoggingFragment {

    private RadioResultsViewModel viewModel;
    private View rootView;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvProgressMessage;
    private RadioFavoritesRVAdapter adapter;
    private RadioBrowserRepository repo; // for resolveUrl on play()
    private View dropZone;
    private ItemTouchHelper touchHelper;
    private boolean isHistoryMode = false;

    private final LoadingProgressHelper progressHelper = new LoadingProgressHelper();

    private boolean hasInternet = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_radio_results, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rootView = view;

        Bundle args = getArguments() != null ? getArguments() : new Bundle();

        recyclerView = view.findViewById(R.id.recyclerView);
        InsetHelper.applyInsetsForScrollableInFullActivity(requireActivity(), recyclerView);

        progressBar = view.findViewById(R.id.progressBar);
        tvProgressMessage = view.findViewById(R.id.tvProgressMessage);
        dropZone = view.findViewById(R.id.dragDeleteZone);

        View networkRowView = view.findViewById(R.id.includeNetworkStatus);
        NetworkStatusViewModel netVm = new ViewModelProvider(this).get(NetworkStatusViewModel.class);
        new NetworkStatusRowController(requireContext(), networkRowView, getViewLifecycleOwner(), netVm);
        netVm.getStatus().observe(getViewLifecycleOwner(), status -> hasInternet = status.hasInternet);

        int minSpan = getResources().getInteger(R.integer.radio_grid_span_station_min);
        int maxSpan = getResources().getInteger(R.integer.radio_grid_span_station_max);
        int defaultSpan = getResources().getInteger(R.integer.radio_grid_span_station_default);

        final GridLayoutManager glm = new GridLayoutManager(requireContext(), defaultSpan);
        recyclerView.setLayoutManager(glm);

        recyclerView.addItemDecoration(new ViewHelper.SpacesItemDecoration(ViewHelper.dp(requireContext(), Var.GRID_LAYOUT_SPACER_RADIO)));

        GridScaleGestureHelper scaleHelper = new GridScaleGestureHelper(
                recyclerView,
                minSpan,
                maxSpan,
                defaultSpan,
                "RADIO_GRID_LAYOUT_SPAN"
        );
        recyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                scaleHelper.onTouchEvent(e);
                return false;   // important: let other listeners (ItemTouchHelper, clicks) still work
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                scaleHelper.onTouchEvent(e);
            }
        });

        viewModel = new ViewModelProvider(this).get(RadioResultsViewModel.class);

        // Known upfront (from the landing-screen setting) so the header's very first bind - before
        // any DB data has loaded - already reflects the intended mode, instead of always starting
        // as "Favorites" and correcting itself a moment later (visible as a brief Favorites flash).
        boolean startInHistory = args.getBoolean(Intents.EXTRA_START_IN_HISTORY, false);
        isHistoryMode = startInHistory;

        adapter = new RadioFavoritesRVAdapter(new RadioFavoritesRVAdapter.OnActionListener() {
            @Override
            public void onPlay(RadioStation radioStation) {
                myLogI("--- user clicks 'favorite/history' radio item --- : " + radioStation.name);

                // check if already playing ---
                PlaybackViewModel playbackVm = new ViewModelProvider(RadioFavoritesFragment.this)
                        .get(PlaybackViewModel.class);
                PlaybackUiState state = playbackVm.getState().getValue();
                if (state != null && Var.PLAY_MODE_RADIO.equals(state.playMode)) {
                    if (radioStation.id == state.trackId) {
                        myLog("already playing => opening detail activity");
                        NavHelper.openRadioStationActivity(requireContext(), state.trackId);
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
                            requireContext().getApplicationContext(),
                            radioStation,
                            radioStation.url_resolved,
                            "RadioFavoritesFragment - onPlay() - using cached url_resolved");

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
                                    requireContext().getApplicationContext(),
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
                                    requireContext().getApplicationContext(),
                                    radioStation.stationuuid,
                                    stream);
                        } else if (hasCachedUrl) {
                            stream = radioStation.url_resolved;
                            myLogI("resolveUrl empty, fallback to url_resolved : " + stream);
                        }

                        if (stream != null) {
                            RadioHelper.play(
                                    requireContext().getApplicationContext(),
                                    radioStation,
                                    stream,
                                    "RadioFavoritesFragment - onPlay() - after url renewed");
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
                                        requireContext().getApplicationContext(),
                                        radioStation,
                                        radioStation.url_resolved,
                                        "RadioFavoritesFragment - onPlay() - url NOT renewed (fallback)");
                            }
                        }
                    }
                });
            }

            @Override
            public void onUnfavorite(RadioStation f) {
                myLogI("--- user Unfavorite radio item --- : " + f.name);
                viewModel.removeFavoriteUuid(requireContext(), f.stationuuid);
            }

            @Override
            public void onPersistOrder(List<RadioStation> newOrder) {
                myLogI("--- user change favorite radio station order --- ");
                viewModel.reorderFavorites(requireContext(), newOrder);
            }

            @Override
            public void onToggleFavorites() {
                myLogI("--- user clicks favorites ---");
                viewModel.loadFavorites(requireContext());
            }

            @Override
            public void onToggleHistory() {
                myLogI("--- user clicks history ---");
                viewModel.loadHistory(requireContext());
            }
        }, startInHistory);
        adapter.setFaviconCache(viewModel.getFaviconCache());
        recyclerView.setAdapter(adapter);

        PlaybackViewModel playbackVm = new ViewModelProvider(this).get(PlaybackViewModel.class);
        playbackVm.getState().observe(getViewLifecycleOwner(), state -> {
            if (state != null)
                adapter.setPlayingRadioStation(state.trackId);
        });

        // initMode() picks favorites-vs-history itself (asynchronously, based on which has rows)
        // - that would race with (and could silently overwrite) an explicit request to land on
        // a specific tab, so skip it entirely when the caller already knows what it wants (the
        // landing screen's now-separate Favorites/History buttons always know exactly what they
        // want, and shouldn't get silently redirected just because that list happens to be empty).
        if (args.getBoolean(Intents.EXTRA_START_IN_HISTORY, false)) {
            myLogI("--- opening directly in history mode (radio landing screen setting) ---");
            viewModel.loadHistory(requireContext());
        } else if (args.getBoolean(Intents.EXTRA_START_IN_FAVORITES, false)) {
            myLogI("--- opening directly in favorites mode (radio landing screen setting) ---");
            viewModel.loadFavorites(requireContext());
        } else {
            viewModel.initMode(requireContext());
        }

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
                requireContext(),
                /* discoverMirrors */ false, // keep async discovery for later if you want
                /* log level */ com.driot.bookplayer.global.Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL);

        viewModel.getShowingHistory().observe(getViewLifecycleOwner(), isHistory -> {
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

        long autoScrollTrackIdFromIntent = args.getLong(Intents.EXTRA_OPEN_FROM_TRACK_ID, -1);
        final long[] autoScrollTrackId = {autoScrollTrackIdFromIntent};

        setProgressVisible(true, getString(R.string.loading));
        viewModel.getFavoriteItems().observe(getViewLifecycleOwner(), favorites -> {
            setProgressVisible(false, null);
            adapter.setItems(favorites, isHistoryMode);

            if (autoScrollTrackId[0] > 0) {
                int pos = adapter.getPositionForTrackId(autoScrollTrackId[0]);
                if (pos != RecyclerView.NO_POSITION) {
                    myLogD("Auto-scrolling to Id=" + autoScrollTrackId[0] + " at pos=" + pos);
                    final int scrollPos = pos;
                    recyclerView.postDelayed(() -> {
                        if (isAdded() && !requireActivity().isFinishing()) {
                            recyclerView.smoothScrollToPosition(scrollPos);
                        }
                    }, 300);
                    autoScrollTrackId[0] = -1; // Done
                } else if (!isHistoryMode) {
                    myLogD("Id=" + autoScrollTrackId[0] + " not in favorites, trying history");
                    viewModel.loadHistory(requireContext());
                } else {
                    myLogE("Id=" + autoScrollTrackId[0] + " not found in favorites nor history");
                    autoScrollTrackId[0] = -1; // Not found anywhere
                }
            }
        });

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
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
