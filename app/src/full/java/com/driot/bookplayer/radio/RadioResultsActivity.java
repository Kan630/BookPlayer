package com.driot.bookplayer.radio;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.RadioStation;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.nav.BaseBottomNavActivity;
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
import com.driot.bookplayer.utils.LiveCensorshipManager;
import com.driot.bookplayer.utils.NetworkStatusViewModel;
import com.driot.bookplayer.utils.Tonio;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class RadioResultsActivity extends BaseBottomNavActivity {

    // --- list ---
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvProgressMessage;
    private ProgressBar progressBarLoadMore;

    private RadioResultsViewModel viewModel;
    private RadioBrowserRepository repo;
    private RadioResultRVAdapter adapter;

    private final LoadingProgressHelper progressHelper = new LoadingProgressHelper();

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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View networkRowView = findViewById(R.id.includeNetworkStatus);
        NetworkStatusViewModel netVm = new ViewModelProvider(this).get(NetworkStatusViewModel.class);
        new NetworkStatusRowController(this, networkRowView, this, netVm);
        netVm.getStatus().observe(this, status -> hasInternet = status.hasInternet);

        //findViewById(R.id.groupFavoriteVsHistory).setVisibility(View.GONE);

        recyclerView = findViewById(R.id.recyclerView);
        InsetHelper.applyInsetsForScrollableBehindNavBar(this, recyclerView);
        
        progressBar = findViewById(R.id.progressBar);
        tvProgressMessage = findViewById(R.id.tvProgressMessage);
        progressBarLoadMore = findViewById(R.id.progressBarLoadMore);

        // ---- grid span (header full width) ----
        int span = getResources().getInteger(R.integer.radio_grid_span_station);
        GridLayoutManager glm = new GridLayoutManager(this, span);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return position == 0 ? span : 1;
            }
        });
        recyclerView.setLayoutManager(glm);
        recyclerView.addItemDecoration(
                new ViewHelper.SpacesItemDecoration(ViewHelper.dp(this, Var.GRID_LAYOUT_SPACER)));

        adapter = new RadioResultRVAdapter(new RadioResultRVAdapter.OnActionListener() {
            @Override
            public void onPlay(ApiStation apiStation) {
                myLogI("-------- USER CLICK radio item -------- : " + apiStation.name);
                adapter.setClickedRadioStation(apiStation.stationuuid);

                // check if already playing ---
                PlaybackViewModel playbackVm = new ViewModelProvider(RadioResultsActivity.this)
                        .get(PlaybackViewModel.class);
                PlaybackUiState state = playbackVm.getState().getValue();
                if (state != null && Var.PLAY_MODE_RADIO.equals(state.playMode)) {
                    String playingUuid = adapter.getPlayingRadioStationUuid();
                    if (apiStation.stationuuid.equals(playingUuid)) {
                        myLog("already playing => opening detail activity");
                        NavHelper.openRadioStationActivityFromUuid(RadioResultsActivity.this,
                                playingUuid);
                        return;
                    }
                }

                if (!hasInternet) {
                    myToast(getString(R.string.no_internet_connection));
                    return;
                }

                PlaybackUiBus.get().setLoadPhase(Intents.PHASE_LOADING_RADIO);

                final boolean renewOnClick = Option.getRadioRenewUrl();
                final boolean hasCachedUrl = apiStation.url_resolved != null && !apiStation.url_resolved.isEmpty();

                if (hasCachedUrl && !renewOnClick) {
                    myLogD("RadioResults: using cached url_resolved, scheduling background renew. url_resolved = ["
                            + apiStation.url_resolved + "]");
                    final long startTime = System.currentTimeMillis();

                    RadioHelper.play(getApplicationContext(), apiStation, apiStation.url_resolved,
                            "RadioResultsActivity - onPlay() - using cached url_resolved");

                    repo.resolveUrl(apiStation.stationuuid, new Callback<UrlResolve>() {
                        @Override
                        public void onResponse(Call<UrlResolve> call, Response<UrlResolve> rsp) {
                            if (!rsp.isSuccessful() ||
                                    rsp.body() == null ||
                                    rsp.body().url == null ||
                                    rsp.body().url.isEmpty()) {
                                myLogW("RadioResults background resolveUrl: no usable url for [" + apiStation.name
                                        + "] in "
                                        + Tonio.formatHhMmSsMs(System.currentTimeMillis() - startTime));
                                return;
                            }

                            String newUrl = rsp.body().url;
                            if (newUrl.equals(apiStation.url_resolved)) {
                                myLogD("RadioResults background resolveUrl: url unchanged for [" + apiStation.name
                                        + "] -> ["
                                        + newUrl + "] in "
                                        + Tonio.formatHhMmSsMs(System.currentTimeMillis() - startTime));
                                return;
                            }

                            myLogI("RadioResults background resolveUrl success for [" + apiStation.name + "] -> ["
                                    + newUrl
                                    + "] in " + Tonio.formatHhMmSsMs(System.currentTimeMillis() - startTime));
                            apiStation.url_resolved = newUrl;
                        }

                        @Override
                        public void onFailure(Call<UrlResolve> call, Throwable t) {
                            myLogW("RadioResults background resolveUrl failed for [" + apiStation.name + "] : [" + t
                                    + "] in "
                                    + Tonio.formatHhMmSsMs(System.currentTimeMillis() - startTime));
                        }
                    });

                    return;
                }

                myLog("RadioResults: Option renew Url = " + renewOnClick
                        + ", url_resolved = [" + apiStation.url_resolved + "]"
                        + " => repo.resolveUrl(" + apiStation.stationuuid + ") - " + apiStation.name);

                progressBar.setVisibility(View.VISIBLE);
                if (tvProgressMessage != null) {
                    progressHelper.start(tvProgressMessage, new LoadingProgressHelper.MessageProvider() {
                        @NonNull
                        @Override
                        public String getInitialMessage() {
                            return getString(R.string.radio_browser_contacting);
                        }

                        @NonNull
                        @Override
                        public String getTickMessage(long elapsedSec) {
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
                        myLog("radio resolveUrl onResponse in " + (System.currentTimeMillis() - topStart) + "ms.");

                        String stream = null;
                        if (rsp.isSuccessful() &&
                                rsp.body() != null &&
                                rsp.body().url != null &&
                                !rsp.body().url.isEmpty()) {

                            stream = rsp.body().url;
                            myLogI("resolveUrl success : " + stream);
                            apiStation.url_resolved = stream;

                        } else if (apiStation.url_resolved != null && !apiStation.url_resolved.isEmpty()) {
                            myLogI("fallback url_resolved : " + apiStation.url_resolved);
                            stream = apiStation.url_resolved;
                        }

                        if (stream != null) {
                            RadioHelper.play(getApplicationContext(), apiStation, stream,
                                    "RadioResultsActivity - onPlay() - after url renewed");
                        } else {
                            myToastE(getString(R.string.an_error_occurred));
                        }
                    }

                    @Override
                    public void onFailure(Call<UrlResolve> call, Throwable t) {
                        progressBar.setVisibility(View.GONE);
                        progressHelper.stop();

                        if (NetworkHelper.isUnknownHost(t)) {
                            myToastE(getString(R.string.no_internet_connection));
                        } else {
                            myLogEE(t, "resolveUrl failed");
                            if (apiStation.url_resolved != null && !apiStation.url_resolved.isEmpty()) {
                                myLogI("fallback url_resolved (failure) : " + apiStation.url_resolved);
                                RadioHelper.play(getApplicationContext(), apiStation, apiStation.url_resolved,
                                        "RadioResultsActivity - onPlay() - fallback url_resolved after failure");
                            } else {
                                myToastE(getString(R.string.an_error_occurred));
                            }
                        }
                    }
                });
            }

            @Override
            public void onFavorite(ApiStation s) {
                myLogI("--- user set favorite radio item --- : " + s.name);
                viewModel.toggleFavorite(RadioResultsActivity.this, s);
            }
        });

        recyclerView.setAdapter(adapter);

        // Highlight currently playing radio station
        PlaybackViewModel playbackVm = new ViewModelProvider(this).get(PlaybackViewModel.class);
        playbackVm.getState().observe(this, state -> {
            if (state == null)
                return;
            if (Var.PLAY_MODE_RADIO.equals(state.playMode) && state.trackId > 0) {
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    RadioStation rs = AppDatabase.getDatabase(getApplicationContext()).radioStationDao()
                            .findById(state.trackId);
                    String uuid = (rs != null) ? rs.stationuuid : null;
                    runOnUiThread(() -> adapter.setPlayingRadioStation(state.trackId, uuid));
                });
            } else {
                adapter.setPlayingRadioStation(-1, null);
            }
        });

        // ---- VM + favorites ----
        viewModel = new ViewModelProvider(this).get(RadioResultsViewModel.class);
        adapter.setFaviconCache(viewModel.getFaviconCache());
        viewModel.getHeaderCount().observe(this, count -> {
            if (count != null && !count.isEmpty()) {
                adapter.setHeaderCount(count);
            }
        });
        viewModel.loadFavorites(this);
        viewModel.getFavoriteUuids().observe(this, uuids -> adapter.setFavorites(uuids));
        viewModel.getResults().observe(this, stations -> {
            if (stations != null) {
                int currentAdapterSize = adapter.getItemCount() - 1; // -1 for header
                if (currentAdapterSize == 0 || stations.size() <= currentAdapterSize) {
                    // Initial load or reset - replace all items
                    adapter.setItems(stations);
                } else {
                    // Pagination - only append new items
                    List<ApiStation> newItems = stations.subList(currentAdapterSize, stations.size());
                    adapter.appendItems(newItems);
                }
            }
            progressBar.setVisibility(View.GONE);
            progressHelper.stop();
        });
        viewModel.getIsLoadingMore().observe(this, isLoading -> {
            if (progressBarLoadMore != null) {
                progressBarLoadMore.setVisibility(Boolean.TRUE.equals(isLoading) ? View.VISIBLE : View.GONE);
            }
        });
        viewModel.getShouldFinish().observe(this, shouldFinish -> {
            if (Boolean.TRUE.equals(shouldFinish))
                finish();
        });

        // Add scroll listener for infinite scrolling
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                    // Load more when user is near the bottom (within 5 items)
                    if (!viewModel.isLoading() && viewModel.hasMore() && hasInternet) {
                        if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5) {
                            loadNextPage();
                        }
                    }
                }
            }
        });

        // ---- Read intent params ----
        String q = getIntent().getStringExtra("query"); // station name substring
        String lang = getIntent().getStringExtra("lang"); // e.g., "fr"
        String country = getIntent().getStringExtra("country"); // e.g., "FR"
        String tag = getIntent().getStringExtra("tag"); // e.g., "jazz"
        myLogIntentExtras(getIntent(), "Radio Query");

        if (q == null)
            q = "";
        if (lang == null)
            lang = "";
        if (country == null)
            country = "";
        if (tag == null)
            tag = "";

        viewModel.setLastParams(q, lang, country, tag);
        adapter.setHeaderCount(getString(R.string.Results_2pt) + "...");

        // ---- Repo ----
        repo = new RadioBrowserRepository(
                this,
                /* discoverMirrors */ false, // keep async version for later
                /* log level */ Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL);

        // ---- Search ----
        String station_search_mode = getIntent().getStringExtra(GetRadioActivity.EXTRA_RADIO_STATION_SEARCH_MODE);
        if (station_search_mode == null)
            station_search_mode = "NO_MODE";
        viewModel.setLastSearchMode(station_search_mode);

        // Check if we already have results (orientation change scenario)
        List<ApiStation> existingResults = viewModel.getResults().getValue();
        if (existingResults != null && !existingResults.isEmpty()) {
            // We have existing results, don't reload - observer will update adapter
            myLog("Using existing results from ViewModel (orientation change), count: " + existingResults.size());
            progressBar.setVisibility(View.GONE);
            progressHelper.stop();
            // Don't return - let the observer handle it, but skip the API call below
            // Actually, we should return to avoid making the API call
            return;
        }

        // No existing results, perform initial search
        viewModel.resetPagination();
        viewModel.setLoading(true);
        progressBar.setVisibility(View.VISIBLE);
        if (tvProgressMessage != null) {
            progressHelper.start(tvProgressMessage, new LoadingProgressHelper.MessageProvider() {
                @NonNull
                @Override
                public String getInitialMessage() {
                    return getString(R.string.radio_browser_contacting);
                }

                @NonNull
                @Override
                public String getTickMessage(long elapsedSec) {
                    return getString(R.string.radio_browser_wait_elapsed,
                            (int) elapsedSec, Var.RADIO_BROWSER_TIMEOUT_SEC);
                }
            });
        }

        myLog("API CALL...[" + station_search_mode + "] - q=" + q + " - lang=" + lang + " - country=" + country
                + " - tag=" + tag);

        switch (station_search_mode) {

            case "MODE_TOP_VOTE":
                repo.topVoted(Option.getRadioApiNbResults(), resultsCb("topVote"));
                adapter.setHeaderSearch(getString(R.string.Search_2pt) + getString(R.string.top_vote));
                break;

            case "MODE_TOP_CLICK":
                repo.topClicked(Option.getRadioApiNbResults(), resultsCb("topClick"));
                adapter.setHeaderSearch(getString(R.string.Search_2pt) + getString(R.string.top_click));
                break;

            case "MODE_LAST_CLICK":
                repo.lastClicked(Option.getRadioApiNbResults(), resultsCb("lastClick"));
                adapter.setHeaderSearch(getString(R.string.Search_2pt) + getString(R.string.last_click));
                break;

            case "MODE_LAST_CHANGE":
                repo.lastChanged(Option.getRadioApiNbResults(), resultsCb("lastChange"));
                adapter.setHeaderSearch(getString(R.string.Search_2pt) + getString(R.string.last_change));
                break;

            case "MODE_TAG":
                if (tag.isEmpty()) {
                    myToastE(getString(R.string.selected_language_error)); // or a “tag missing” message
                    finish();
                    return;
                }
                repo.byTag(tag, Option.getRadioApiNbResults(), resultsCb("tag"));
                adapter.setHeaderSearch(getString(R.string.by_tag) + " : " + tag);
                adapter.setHeaderCountryTag(country);
                break;

            case "MODE_COUNTRY":
                if (country.isEmpty()) {
                    myToastE(getString(R.string.error_generic));
                    finish();
                    return;
                }
                repo.byCountry(country, Option.getRadioApiNbResults(), resultsCb("country"));
                adapter.setHeaderSearch(getString(R.string.by_country) + " : " + country);
                adapter.setHeaderCountryTag(tag);
                break;

            case "MODE_LANGUAGE":
                if (lang.isEmpty()) {
                    myToastE(getString(R.string.error_generic));
                    finish();
                    return;
                }
                repo.byLanguage(lang, Option.getRadioApiNbResults(), resultsCb("language"));
                adapter.setHeaderSearch(getString(R.string.by_language) + " : " + lang);
                adapter.setHeaderLang("");
                break;

            case "MODE_SEARCH":
            default:
                if (q.isEmpty()) {
                    myToast(getString(R.string.please_type_a_search_string));
                    finish();
                    return;
                }
                repo.byName(q, Option.getRadioApiNbResults(), resultsCb("byname"));
                adapter.setHeaderSearch(getString(R.string.by_name) + " : " + q);

                // TODO maybe later put spinner back... not very useful right now
                // repo.search(q, nullIfBlank(tag), country, lang,
                // Option.getRadioApiNbResults(), resultsCb("search"));
                /*
                 * // If you have a combined search, call that; otherwise choose a best-effort:
                 * if (!q.isEmpty()) {
                 * repo.byName(q, Option.getRadioApiNbResults(), resultsCb("name"));
                 * } else if (!tag.isEmpty()) {
                 * repo.byTag(tag, Option.getRadioApiNbResults(), resultsCb("tag"));
                 * } else if (!country.isEmpty()) {
                 * repo.byCountry(country, Option.getRadioApiNbResults(), resultsCb("country"));
                 * } else if (!lang.isEmpty()) {
                 * repo.byLanguage(lang, Option.getRadioApiNbResults(), resultsCb("language"));
                 * } else {
                 * // fallback to trending if truly nothing specified
                 * repo.topVoted(Option.getRadioApiNbResults(), resultsCb("trending"));
                 * }
                 */

                break;
        }

    }

    private void loadNextPage() {
        if (viewModel.isLoading() || !viewModel.hasMore() || !hasInternet) {
            return;
        }

        viewModel.setLoading(true);
        String searchMode = viewModel.getLastSearchMode();
        if (searchMode == null || searchMode.isEmpty()) {
            searchMode = "NO_MODE";
        }

        String q = viewModel.getLastQuery();
        String lang = viewModel.getLastLang();
        String country = viewModel.getLastCountry();
        String tag = viewModel.getLastTag();
        int offset = viewModel.getCurrentOffset();
        int limit = Option.getRadioApiNbResults();

        myLog("Loading next page - offset: " + offset + ", mode: " + searchMode);

        switch (searchMode) {
            case "MODE_TOP_VOTE":
                repo.topVoted(limit, offset, resultsCb("topVote", true));
                break;
            case "MODE_TOP_CLICK":
                repo.topClicked(limit, offset, resultsCb("topClick", true));
                break;
            case "MODE_LAST_CLICK":
                repo.lastClicked(limit, offset, resultsCb("lastClick", true));
                break;
            case "MODE_LAST_CHANGE":
                repo.lastChanged(limit, offset, resultsCb("lastChange", true));
                break;
            case "MODE_TAG":
                if (!tag.isEmpty()) {
                    repo.byTag(tag, limit, offset, resultsCb("tag", true));
                }
                break;
            case "MODE_COUNTRY":
                if (!country.isEmpty()) {
                    repo.byCountry(country, limit, offset, resultsCb("country", true));
                }
                break;
            case "MODE_LANGUAGE":
                if (!lang.isEmpty()) {
                    repo.byLanguage(lang, limit, offset, resultsCb("language", true));
                }
                break;
            case "MODE_SEARCH":
            default:
                if (!q.isEmpty()) {
                    repo.byName(q, limit, offset, resultsCb("byname", true));
                }
                break;
        }
    }

    private Callback<List<ApiStation>> resultsCb(String source) {
        return resultsCb(source, false);
    }

    private Callback<List<ApiStation>> resultsCb(String source, boolean isPagination) {
        return new Callback<>() {
            @Override
            public void onResponse(Call<List<ApiStation>> call, Response<List<ApiStation>> rsp) {
                if (!isPagination) {
                    progressBar.setVisibility(View.GONE);
                    progressHelper.stop();
                }
                List<ApiStation> body = rsp.body();
                if (rsp.isSuccessful() && body != null && !body.isEmpty()) {
                    int rawSize = body.size();
                    boolean serverHasMorePages = rawSize >= Option.getRadioApiNbResults();
                    myLog("serverHasMorePages : " + serverHasMorePages + " (rawSize=" + rawSize + ")");

                    Set<String> censoredRadios = LiveCensorshipManager.getCensoredRadios(getApplicationContext());

                    String headerTxt = "";
                    boolean removeDubious = Option.getRadioRemoveDubiousStations();
                    boolean removeDuplicates = Option.getRadioRemoveSpamStations();
                    int nbRemovedDuplicates = 0;
                    int nbRemovedDubious = 0;
                    Set<String> removedNamesDuplicates = new HashSet<>();
                    Set<String> removedNamesDubious = new HashSet<>();

                    Map<String, Integer> countMap = new HashMap<>();
                    Iterator<ApiStation> iterator = body.iterator();
                    while (iterator.hasNext()) {
                        ApiStation s = iterator.next();
                        if (s.name == null)
                            continue;
                        String trimmedName = s.name.toLowerCase().replaceAll("[^a-z0-9]", "");
                        if (LiveCensorshipManager.isCensoredAlreadyTrimmed(trimmedName, censoredRadios)) {
                            myLogW("[" + s.name + "] is censored.     trimmedName=" + trimmedName);
                            iterator.remove();
                            continue;
                        }
                        if (removeDubious && Var.RADIO_STATION_BLACKLIST_LOWERCASE.contains(trimmedName)) {
                            iterator.remove();
                            nbRemovedDubious++;
                            // removedNamesDubious.add(s.name); //don't display dubious names for now
                            continue;
                        }
                        if (removeDuplicates) {
                            Integer countObj = countMap.get(trimmedName);
                            int count = countObj != null ? countObj : 0;
                            if (count >= Var.RADIO_STATION_MAX_DUPLICATES) {
                                iterator.remove();
                                nbRemovedDuplicates++;
                                removedNamesDuplicates.add(s.name);
                            } else {
                                countMap.put(trimmedName, count + 1);
                            }
                        }
                    }

                    if (nbRemovedDuplicates > 0) {
                        headerTxt = "    (" + nbRemovedDuplicates + " "
                                + getString(R.string.spam_fake_stations_removed) + " : " + removedNamesDuplicates
                                + ")";
                        myLog(nbRemovedDuplicates + " stations removed (duplicates)");
                        myLog("Removed duplicate names: " + removedNamesDuplicates);
                    }
                    if (nbRemovedDubious > 0) {
                        headerTxt = headerTxt + "    (" + nbRemovedDubious + " "
                                + getString(R.string.dubious_stations_removed) + " : " + removedNamesDubious + ")";
                        myLog(nbRemovedDubious + " stations removed (dubious)");
                        myLog("Removed dubious names: " + removedNamesDubious);
                    }

                    if (isPagination) {
                        viewModel.appendResults(body, rawSize);
                        viewModel.setHasMore(serverHasMorePages);

                        List<ApiStation> allResults = viewModel.getResults().getValue();
                        if (allResults != null) {
                            viewModel.setHeaderCount(
                                    getString(R.string.Results_2pt) + allResults.size() + headerTxt);
                        }
                        myLog("radio pagination (" + source + ") = " + body.size() + " new items, total: "
                                + (allResults != null ? allResults.size() : 0));
                    } else {
                        viewModel.setResults(body, rawSize);
                        viewModel.setHasMore(serverHasMorePages);
                        viewModel.setHeaderCount(getString(R.string.Results_2pt) + body.size() + headerTxt);
                        myLog("radio results (" + source + ") = " + body.size());
                    }
                } else {
                    if (!isPagination) {
                        myToast(getString(R.string.no_result));
                        viewModel.requestFinish();
                    } else {
                        // No more results for pagination
                        viewModel.setLoading(false);
                        viewModel.setHasMore(false);
                    }
                }
            }

            @Override
            public void onFailure(Call<List<ApiStation>> call, Throwable t) {
                if (!isPagination) {
                    progressBar.setVisibility(View.GONE);
                    progressHelper.stop();
                }
                viewModel.setLoading(false);
                if (NetworkHelper.isUnknownHost(t)) {
                    if (!isPagination) {
                        myToastE(getString(R.string.no_internet_connection));
                    }
                } else {
                    myLogEE(t, "radio search failed (" + source + ")");
                    if (!isPagination) {
                        myToastE(getString(R.string.an_error_occurred));
                        viewModel.requestFinish();
                    }
                }
            }
        };
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        progressHelper.stop();
    }
}
