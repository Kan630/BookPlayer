package com.driot.bookplayer.radio;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.RadioStation;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.helpers.GridScaleGestureHelper;
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
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingFragment;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Search/browse results grid, reached from GetRadioFragment / GetRadioCardListFragment.
 * Converted from the former RadioResultsActivity - see [[radio_deeplink_applinks_fix]].
 */
@AndroidEntryPoint
public class RadioResultsFragment extends LoggingFragment {

    // --- Views ---
    private View rootView;
    private RecyclerView recyclerView;
    private ProgressBar  progressBar;
    private TextView     tvProgressMessage;
    private ProgressBar  progressBarLoadMore;

    // --- VM / Adapter / Repo ---
    private RadioResultsViewModel viewModel;
    private RadioBrowserRepository repo;
    private RadioResultRVAdapter adapter;

    private final LoadingProgressHelper progressHelper = new LoadingProgressHelper();

    private boolean hasInternet = true;
    private boolean glideIsPaused = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_radio_results, container, false);
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rootView = view;

        // ---- Network status ----
        View networkRowView = view.findViewById(R.id.includeNetworkStatus);
        NetworkStatusViewModel netVm = new ViewModelProvider(this).get(NetworkStatusViewModel.class);
        new NetworkStatusRowController(requireContext(), networkRowView, getViewLifecycleOwner(), netVm);
        netVm.getStatus().observe(getViewLifecycleOwner(), status -> hasInternet = status.hasInternet);

        // ---- Views ----
        recyclerView        = view.findViewById(R.id.recyclerView);
        progressBar         = view.findViewById(R.id.progressBar);
        tvProgressMessage   = view.findViewById(R.id.tvProgressMessage);
        progressBarLoadMore = view.findViewById(R.id.progressBarLoadMore);

        InsetHelper.applyInsetsForScrollableInFullActivity(requireActivity(), recyclerView);

        // ---- Grid layout ----
        int minSpan = getResources().getInteger(R.integer.radio_grid_span_station_min);
        int maxSpan = getResources().getInteger(R.integer.radio_grid_span_station_max);
        int defaultSpan = getResources().getInteger(R.integer.radio_grid_span_station_default);

        final GridLayoutManager glm = new GridLayoutManager(requireContext(), defaultSpan);
        recyclerView.setLayoutManager(glm);

        recyclerView.addItemDecoration(
                new ViewHelper.SpacesItemDecoration(ViewHelper.dp(requireContext(), Var.GRID_LAYOUT_SPACER_RADIO)));

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

        // ---- Adapter ----
        adapter = new RadioResultRVAdapter(new RadioResultRVAdapter.OnActionListener() {
            @Override
            public void onPlay(ApiStation apiStation) {
                myLogI("-------- USER CLICK radio item -------- : " + apiStation.name);
                adapter.setClickedRadioStation(apiStation.stationuuid);

                PlaybackViewModel playbackVm = new ViewModelProvider(RadioResultsFragment.this)
                        .get(PlaybackViewModel.class);
                PlaybackUiState state = playbackVm.getState().getValue();
                if (state != null && Var.PLAY_MODE_RADIO.equals(state.playMode)) {
                    String playingUuid = adapter.getPlayingRadioStationUuid();
                    if (apiStation.stationuuid.equals(playingUuid)) {
                        myLog("already playing => opening detail activity");
                        NavHelper.openRadioStationActivityFromUuid(requireContext(), playingUuid);
                        return;
                    }
                }

                if (!hasInternet) {
                    myToast(getString(R.string.no_internet_connection));
                    return;
                }

                pauseGlideForPlay();
                PlaybackUiBus.get().setLoadPhase(Intents.PHASE_LOADING_RADIO);

                final boolean renewOnClick  = Option.getRadioRenewUrl();
                final boolean hasCachedUrl  = apiStation.url_resolved != null
                        && !apiStation.url_resolved.isEmpty();

                if (hasCachedUrl && !renewOnClick) {
                    myLogD("RadioResults: using cached url_resolved, scheduling background renew. "
                            + "url_resolved=[" + apiStation.url_resolved + "]");
                    final long startTime = System.currentTimeMillis();

                    RadioHelper.play(requireContext().getApplicationContext(), apiStation, apiStation.url_resolved,
                            "RadioResultsFragment - onPlay() - using cached url_resolved");

                    repo.resolveUrl(apiStation.stationuuid, new Callback<UrlResolve>() {
                        @Override
                        public void onResponse(Call<UrlResolve> call, Response<UrlResolve> rsp) {
                            if (!rsp.isSuccessful() || rsp.body() == null
                                    || rsp.body().url == null || rsp.body().url.isEmpty()) {
                                myLogW("RadioResults background resolveUrl: no usable url for ["
                                        + apiStation.name + "] in "
                                        + Tonio.formatHhMmSsMs(System.currentTimeMillis() - startTime));
                                return;
                            }
                            String newUrl = rsp.body().url;
                            if (newUrl.equals(apiStation.url_resolved)) {
                                myLogD("RadioResults background resolveUrl: url unchanged for ["
                                        + apiStation.name + "] -> [" + newUrl + "] in "
                                        + Tonio.formatHhMmSsMs(System.currentTimeMillis() - startTime));
                                return;
                            }
                            myLogI("RadioResults background resolveUrl success for ["
                                    + apiStation.name + "] -> [" + newUrl + "] in "
                                    + Tonio.formatHhMmSsMs(System.currentTimeMillis() - startTime));
                            apiStation.url_resolved = newUrl;
                        }

                        @Override
                        public void onFailure(Call<UrlResolve> call, Throwable t) {
                            myLogW("RadioResults background resolveUrl failed for ["
                                    + apiStation.name + "] : [" + t + "] in "
                                    + Tonio.formatHhMmSsMs(System.currentTimeMillis() - startTime));
                        }
                    });
                    return;
                }

                myLog("RadioResults: Option renew Url = " + renewOnClick
                        + ", url_resolved=[" + apiStation.url_resolved + "]"
                        + " => repo.resolveUrl(" + apiStation.stationuuid + ") - " + apiStation.name);

                progressBar.setVisibility(View.VISIBLE);
                if (tvProgressMessage != null) {
                    progressHelper.start(tvProgressMessage, new LoadingProgressHelper.MessageProvider() {
                        @NonNull @Override public String getInitialMessage() {
                            return getString(R.string.radio_browser_contacting);
                        }
                        @NonNull @Override public String getTickMessage(long elapsedSec) {
                            return getString(R.string.radio_browser_wait_elapsed,
                                    (int) elapsedSec, Var.RADIO_BROWSER_TIMEOUT_SEC);
                        }
                    });
                }
                final long topStart = System.currentTimeMillis();

                repo.resolveUrl(apiStation.stationuuid, new Callback<UrlResolve>() {
                    @Override
                    public void onResponse(Call<UrlResolve> call, Response<UrlResolve> rsp) {
                        progressBar.setVisibility(View.GONE);
                        progressHelper.stop();
                        myLog("radio resolveUrl onResponse in "
                                + (System.currentTimeMillis() - topStart) + "ms.");

                        String stream = null;
                        if (rsp.isSuccessful() && rsp.body() != null
                                && rsp.body().url != null && !rsp.body().url.isEmpty()) {
                            stream = rsp.body().url;
                            myLogI("resolveUrl success: " + stream);
                            apiStation.url_resolved = stream;
                        } else if (apiStation.url_resolved != null
                                && !apiStation.url_resolved.isEmpty()) {
                            myLogI("fallback url_resolved: " + apiStation.url_resolved);
                            stream = apiStation.url_resolved;
                        }

                        if (stream != null) {
                            RadioHelper.play(requireContext().getApplicationContext(), apiStation, stream,
                                    "RadioResultsFragment - onPlay() - after url renewed");
                        } else {
                            resumeGlideAfterPlay();
                            myToastE(getString(R.string.an_error_occurred));
                        }
                    }

                    @Override
                    public void onFailure(Call<UrlResolve> call, Throwable t) {
                        progressBar.setVisibility(View.GONE);
                        progressHelper.stop();

                        if (NetworkHelper.isUnknownHost(t)) {
                            resumeGlideAfterPlay();
                            myToastE(getString(R.string.no_internet_connection));
                        } else {
                            myLogEE(t, "resolveUrl failed");
                            if (apiStation.url_resolved != null
                                    && !apiStation.url_resolved.isEmpty()) {
                                myLogI("fallback url_resolved (failure): " + apiStation.url_resolved);
                                RadioHelper.play(requireContext().getApplicationContext(), apiStation,
                                        apiStation.url_resolved,
                                        "RadioResultsFragment - onPlay() - fallback after failure");
                            } else {
                                resumeGlideAfterPlay();
                                myToastE(getString(R.string.an_error_occurred));
                            }
                        }
                    }
                });
            }

            @Override
            public void onFavorite(ApiStation s) {
                myLogI("--- user set favorite radio item --- : " + s.name);
                viewModel.toggleFavorite(requireContext(), s);
            }
        });

        recyclerView.setAdapter(adapter);

        // ---- Currently playing highlight ----
        PlaybackViewModel playbackVm = new ViewModelProvider(this).get(PlaybackViewModel.class);
        playbackVm.getState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            if (Var.PLAY_MODE_RADIO.equals(state.playMode) && state.trackId > 0) {
                resumeGlideAfterPlay();
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    RadioStation rs = AppDatabase.getDatabase(requireContext().getApplicationContext())
                            .radioStationDao().findById(state.trackId);
                    String uuid = (rs != null) ? rs.stationuuid : null;
                    requireActivity().runOnUiThread(() -> adapter.setPlayingRadioStation(state.trackId, uuid));
                });
            } else {
                adapter.setPlayingRadioStation(-1, null);
            }
        });

        // ---- ViewModel ----
        viewModel = new ViewModelProvider(this).get(RadioResultsViewModel.class);
        adapter.setFaviconCache(viewModel.getFaviconCache());

        viewModel.getHeaderCount().observe(getViewLifecycleOwner(), count -> {
            if (count != null && !count.isEmpty()) adapter.setHeaderCount(count);
        });
        viewModel.loadFavorites(requireContext());
        viewModel.getFavoriteUuids().observe(getViewLifecycleOwner(), uuids -> adapter.setFavorites(uuids));

        viewModel.getResults().observe(getViewLifecycleOwner(), stations -> {
            if (stations != null) {
                int currentAdapterSize = adapter.getItemCount() - 1; // -1 for header
                if (currentAdapterSize == 0 || stations.size() <= currentAdapterSize) {
                    adapter.setItems(stations);
                } else {
                    List<ApiStation> newItems = stations.subList(currentAdapterSize, stations.size());
                    adapter.appendItems(newItems);
                }
            }
        });

        viewModel.getIsInitialLoading().observe(getViewLifecycleOwner(), loading -> {
            boolean isLoading = Boolean.TRUE.equals(loading);
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            if (isLoading) {
                if (tvProgressMessage != null) {
                    progressHelper.start(tvProgressMessage, new LoadingProgressHelper.MessageProvider() {
                        @NonNull @Override public String getInitialMessage() {
                            return getString(R.string.radio_browser_contacting);
                        }
                        @NonNull @Override public String getTickMessage(long elapsedSec) {
                            return getString(R.string.radio_browser_wait_elapsed,
                                    (int) elapsedSec, Var.RADIO_BROWSER_TIMEOUT_SEC);
                        }
                    });
                }
            } else {
                progressHelper.stop();
            }
        });

        viewModel.getIsLoadingMore().observe(getViewLifecycleOwner(), isLoading -> {
            if (progressBarLoadMore != null) {
                progressBarLoadMore.setVisibility(
                        Boolean.TRUE.equals(isLoading) ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.getShouldFinish().observe(getViewLifecycleOwner(), shouldFinish -> {
            if (Boolean.TRUE.equals(shouldFinish)) Navigation.findNavController(rootView).popBackStack();
        });

        // One-shot UI events from ViewModel (toast messages)
        viewModel.getUiEvent().observe(getViewLifecycleOwner(), event -> {
            if (event == null) return;
            switch (event) {
                case NO_RESULT_FINISH:
                    myToast(getString(R.string.no_result));
                    break;
                case NO_INTERNET_FINISH:
                    myToastE(getString(R.string.no_internet_connection));
                    break;
                case NETWORK_ERROR_FINISH:
                    myToastE(getString(R.string.an_error_occurred));
                    break;
            }
        });

        // ---- Infinite scroll ----
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                GridLayoutManager lm = (GridLayoutManager) recyclerView.getLayoutManager();
                if (lm == null) return;
                int visible   = lm.getChildCount();
                int total     = lm.getItemCount();
                int firstVisible = lm.findFirstVisibleItemPosition();
                if (!viewModel.isLoading() && viewModel.hasMore() && hasInternet) {
                    if ((visible + firstVisible) >= total - 5) {
                        viewModel.loadNextPage();
                        myLogI("load next page on scroll");
                    }
                }
            }
        });

        // ---- Arguments ----
        Bundle args = getArguments() != null ? getArguments() : new Bundle();
        String q       = args.getString("query");
        String lang    = args.getString("lang");
        String country = args.getString("country");
        String tag     = args.getString("tag");
        ArrayList<String> langVariants = args.getStringArrayList("lang_variants");

        if (q       == null) q       = "";
        if (lang    == null) lang    = "";
        if (country == null) country = "";
        if (tag     == null) tag     = "";

        String station_search_mode = args.getString(GetRadioFragment.EXTRA_RADIO_STATION_SEARCH_MODE);
        if (station_search_mode == null) station_search_mode = "NO_MODE";

        // ---- Repo (kept for resolveUrl calls in onPlay) ----
        repo = new RadioBrowserRepository(
                requireContext(), false, Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL);
        viewModel.initRepo(repo);

        // ---- Skip search on rotation (ViewModel already has results) ----
        List<ApiStation> existingResults = viewModel.getResults().getValue();
        if (existingResults != null && !existingResults.isEmpty()) {
            myLog("Using existing results from ViewModel (rotation), count: "
                    + existingResults.size());
            return;
        }

        // ---- Validate required params ----
        if (!validateParams(station_search_mode, q, lang, country, tag)) return;

        // ---- Set adapter header (UI-only, stays in Fragment) ----
        adapter.setHeaderCount(getString(R.string.Results_2pt) + "...");
        setAdapterHeader(station_search_mode, q, lang, country, tag);

        // ---- Kick off search (all data logic now in ViewModel) ----
        myLog("API CALL... [" + station_search_mode + "] q=" + q
                + " lang=" + lang + " country=" + country + " tag=" + tag);
        viewModel.search(station_search_mode, q, lang, country, tag, langVariants);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        progressHelper.stop();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /** Returns false (and navigates back) if a required param is missing for the mode. */
    private boolean validateParams(String mode, String q, String lang, String country, String tag) {
        switch (mode) {
            case "MODE_TAG":
                if (tag.isEmpty()) {
                    myToastE(getString(R.string.selected_language_error));
                    Navigation.findNavController(rootView).popBackStack();
                    return false;
                }
                break;
            case "MODE_COUNTRY":
                if (country.isEmpty()) {
                    myToastE(getString(R.string.error_generic));
                    Navigation.findNavController(rootView).popBackStack();
                    return false;
                }
                break;
            case "MODE_LANGUAGE":
                if (lang.isEmpty()) {
                    myToastE(getString(R.string.error_generic));
                    Navigation.findNavController(rootView).popBackStack();
                    return false;
                }
                break;
            case "MODE_SEARCH":
                if (q.isEmpty()) {
                    myToast(getString(R.string.please_type_a_search_string));
                    Navigation.findNavController(rootView).popBackStack();
                    return false;
                }
                break;
        }
        return true;
    }

    private void pauseGlideForPlay() {
        if (!glideIsPaused) {
            Glide.with(requireContext()).pauseRequests();
            glideIsPaused = true;
        }
    }

    private void resumeGlideAfterPlay() {
        if (glideIsPaused) {
            Glide.with(requireContext()).resumeRequests();
            glideIsPaused = false;
        }
    }

    /** Sets the adapter's header search label (UI string, stays in Fragment). */
    private void setAdapterHeader(String mode, String q, String lang, String country, String tag) {
        switch (mode) {
            case "MODE_TOP_VOTE":
                adapter.setHeaderSearch(getString(R.string.Search_2pt) + getString(R.string.top_vote));
                break;
            case "MODE_TOP_CLICK":
                adapter.setHeaderSearch(getString(R.string.Search_2pt) + getString(R.string.top_click));
                break;
            case "MODE_LAST_CLICK":
                adapter.setHeaderSearch(getString(R.string.Search_2pt) + getString(R.string.last_click));
                break;
            case "MODE_LAST_CHANGE":
                adapter.setHeaderSearch(getString(R.string.Search_2pt) + getString(R.string.last_change));
                break;
            case "MODE_TAG":
                adapter.setHeaderSearch(getString(R.string.by_tag) + " : " + tag);
                adapter.setHeaderCountryTag(country);
                break;
            case "MODE_COUNTRY":
                adapter.setHeaderSearch(getString(R.string.by_country) + " : " + country);
                adapter.setHeaderCountryTag(tag);
                break;
            case "MODE_LANGUAGE":
                adapter.setHeaderSearch(getString(R.string.by_language) + " : " + lang);
                adapter.setHeaderLang("");
                break;
            case "MODE_SEARCH":
            default:
                adapter.setHeaderSearch(getString(R.string.by_name) + " : " + q);
                break;
        }
    }
}
