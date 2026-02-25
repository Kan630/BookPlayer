package com.driot.bookplayer.radio;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.BaseBottomNavActivity;
import com.driot.bookplayer.adapter.RadioResultRVAdapter;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.helpers.NetworkStatusRowController;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.player.PlaybackViewModel;
import com.driot.bookplayer.player.StartPlayHelper;
import com.driot.bookplayer.utils.NetworkStatusViewModel;
import com.driot.bookplayer.utils.Tonio;

import java.util.ArrayList;
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
    private ProgressBar progressBarLoadMore;

    private RadioResultsViewModel viewModel;
    private RadioBrowserRepository repo;
    private RadioResultRVAdapter adapter;

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
        InsetHelper.apply(this);

        View networkRowView = findViewById(R.id.includeNetworkStatus);
        NetworkStatusViewModel netVm = new ViewModelProvider(this).get(NetworkStatusViewModel.class);
        new NetworkStatusRowController(this, networkRowView, this, netVm);
        netVm.getStatus().observe(this, status -> hasInternet = status.hasInternet);

        findViewById(R.id.groupFavoriteVsHistory).setVisibility(View.GONE);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        progressBarLoadMore = findViewById(R.id.progressBarLoadMore);

        // ---- grid span (header full width) ----
        int span = getResources().getInteger(R.integer.radio_grid_span);
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
            public void onPlay(Station s) {
                myLogI("-------- USER CLICK radio item -------- : " + s.name);

                if (!hasInternet) {
                    myToast(getString(R.string.no_internet_connection));
                    return;
                }

                final boolean renewOnClick = Option.getRadioRenewUrl();
                final boolean hasCachedUrl = s.url_resolved != null && !s.url_resolved.isEmpty();

                // ---------------------------------------------------------------------
                // FAST PATH:
                // - We already have url_resolved
                // - AND user did NOT ask "renew URL on click"
                //
                // → Play immediately, then background-renew without blocking the user.
                // ---------------------------------------------------------------------
                if (hasCachedUrl && !renewOnClick) {
                    myLogD("RadioResults: using cached url_resolved, scheduling background renew. url_resolved = ["
                            + s.url_resolved + "]");
                    final long startTime = System.currentTimeMillis();

                    // 1) Immediate playback
                    StartPlayHelper.onRadioClick(
                            getApplicationContext(),
                            s,
                            s.url_resolved,
                            "RadioResultsActivity - onPlay() - using cached url_resolved");

                    // 2) Background renewal (no spinner / no toast)
                    repo.resolveUrl(s.stationuuid, new Callback<UrlResolve>() {
                        @Override
                        public void onResponse(Call<UrlResolve> call, Response<UrlResolve> rsp) {
                            if (!rsp.isSuccessful() ||
                                    rsp.body() == null ||
                                    rsp.body().url == null ||
                                    rsp.body().url.isEmpty()) {
                                myLogW("RadioResults background resolveUrl: no usable url for [" + s.name + "] in "
                                        + Tonio.formatHhMmSsMs(System.currentTimeMillis() - startTime));
                                return;
                            }

                            String newUrl = rsp.body().url;
                            if (newUrl.equals(s.url_resolved)) {
                                myLogD("RadioResults background resolveUrl: url unchanged for [" + s.name + "] -> ["
                                        + newUrl + "] in "
                                        + Tonio.formatHhMmSsMs(System.currentTimeMillis() - startTime));
                                return;
                            }

                            myLogI("RadioResults background resolveUrl success for [" + s.name + "] -> [" + newUrl
                                    + "] in " + Tonio.formatHhMmSsMs(System.currentTimeMillis() - startTime));
                            s.url_resolved = newUrl; // update in-memory

                            // Optional: persist in Room if you track stations there.
                            // For example, if you add this to your ViewModel:
                            // viewModel.updateResolvedUrl(getApplicationContext(), s.stationuuid, newUrl);
                        }

                        @Override
                        public void onFailure(Call<UrlResolve> call, Throwable t) {
                            myLogW("RadioResults background resolveUrl failed for [" + s.name + "] : [" + t + "] in "
                                    + Tonio.formatHhMmSsMs(System.currentTimeMillis() - startTime));
                            // Silent failure, cached url still works.
                        }
                    });

                    return;
                }

                // ---------------------------------------------------------------------
                // STRICT PATH:
                // - No cached url_resolved (first click)
                // - OR "always renew URL" option enabled
                //
                // → Show spinner, wait for resolveUrl, then play.
                // ---------------------------------------------------------------------
                myLog("RadioResults: Option renew Url = " + renewOnClick
                        + ", url_resolved = [" + s.url_resolved + "]"
                        + " => repo.resolveUrl(" + s.stationuuid + ") - " + s.name);

                progressBar.setVisibility(View.VISIBLE);
                final long topStart = System.currentTimeMillis();

                repo.resolveUrl(s.stationuuid, new Callback<UrlResolve>() {
                    @Override
                    public void onResponse(Call<UrlResolve> call, Response<UrlResolve> rsp) {
                        progressBar.setVisibility(View.GONE);
                        myLog("radio resolveUrl onResponse in " + (System.currentTimeMillis() - topStart) + "ms.");

                        String stream = null;
                        if (rsp.isSuccessful() &&
                                rsp.body() != null &&
                                rsp.body().url != null &&
                                !rsp.body().url.isEmpty()) {

                            stream = rsp.body().url;
                            myLogI("resolveUrl success : " + stream);

                            // cache it in the Station object
                            s.url_resolved = stream;

                            // Optional: persist in Room via VM
                            // viewModel.updateResolvedUrl(getApplicationContext(), s.stationuuid, stream);

                        } else if (s.url_resolved != null && !s.url_resolved.isEmpty()) {
                            // fallback to previous cached/known resolved URL
                            myLogI("fallback url_resolved : " + s.url_resolved);
                            stream = s.url_resolved;
                        }

                        if (stream != null) {
                            StartPlayHelper.onRadioClick(
                                    getApplicationContext(),
                                    s,
                                    stream,
                                    "RadioResultsActivity - onPlay() - after url renewed");
                        } else {
                            myToastE(getString(R.string.an_error_occurred));
                        }
                    }

                    @Override
                    public void onFailure(Call<UrlResolve> call, Throwable t) {
                        progressBar.setVisibility(View.GONE);

                        if (NetworkHelper.isUnknownHost(t)) {
                            myToastE(getString(R.string.no_internet_connection));
                        } else {
                            myLogEE(t, "resolveUrl failed");
                            // Fallback to cached url_resolved if we have one
                            if (s.url_resolved != null && !s.url_resolved.isEmpty()) {
                                myLogI("fallback url_resolved (failure) : " + s.url_resolved);
                                StartPlayHelper.onRadioClick(
                                        getApplicationContext(),
                                        s,
                                        s.url_resolved,
                                        "RadioResultsActivity - onPlay() - fallback url_resolved after failure");
                            } else {
                                myToastE(getString(R.string.an_error_occurred));
                            }
                        }
                    }
                });
            }

            @Override
            public void onFavorite(Station s) {
                myLogI("--- user set favorite radio item --- : " + s.name);
                viewModel.toggleFavorite(RadioResultsActivity.this, s);
            }
        });

        recyclerView.setAdapter(adapter);

        // Highlight currently playing radio station
        PlaybackViewModel playbackVm = new ViewModelProvider(this).get(PlaybackViewModel.class);
        playbackVm.getState().observe(this, state -> {
            if (state != null) {
                adapter.setPlayingRadioStationUuid(state.radioStationUuid);
            }
        });

        // ---- VM + favorites ----
        viewModel = new ViewModelProvider(this).get(RadioResultsViewModel.class);
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
                    List<Station> newItems = stations.subList(currentAdapterSize, stations.size());
                    adapter.appendItems(newItems);
                }
            }
            progressBar.setVisibility(View.GONE);
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

        if (q == null)
            q = "";
        if (lang == null)
            lang = "";
        if (country == null)
            country = "";
        if (tag == null)
            tag = "";

        viewModel.setLastParams(q, lang, country, tag);

        // ---- Header text (optional, like Librivox) ----
        String headerSearch = getString(R.string.Search_2pt)
                + (q.isEmpty() ? getString(R.string.search_nothing_specified) : q);
        String headerLang = getString(R.string.Language_2pt) + lang;
        String headerCountryTag = (country.isEmpty() && tag.isEmpty()) ? ""
                : (country + (tag.isEmpty() ? "" : " • " + tag));
        adapter.setHeader(headerSearch, headerLang, headerCountryTag);
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
        List<Station> existingResults = viewModel.getResults().getValue();
        if (existingResults != null && !existingResults.isEmpty()) {
            // We have existing results, don't reload - observer will update adapter
            myLog("Using existing results from ViewModel (orientation change), count: " + existingResults.size());
            progressBar.setVisibility(View.GONE);
            // Don't return - let the observer handle it, but skip the API call below
            // Actually, we should return to avoid making the API call
            return;
        }

        // No existing results, perform initial search
        progressBar.setVisibility(View.VISIBLE);
        viewModel.resetPagination();
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
                break;

            case "MODE_COUNTRY":
                if (country.isEmpty()) {
                    myToastE(getString(R.string.error_generic));
                    finish();
                    return;
                }
                repo.byCountry(country, Option.getRadioApiNbResults(), resultsCb("country"));
                adapter.setHeaderSearch(getString(R.string.by_country) + " : " + country);
                break;

            case "MODE_LANGUAGE":
                if (lang.isEmpty()) {
                    myToastE(getString(R.string.error_generic));
                    finish();
                    return;
                }
                repo.byLanguage(lang, Option.getRadioApiNbResults(), resultsCb("language"));
                adapter.setHeaderSearch(getString(R.string.by_language) + " : " + lang);
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

    private Callback<List<Station>> resultsCb(String source) {
        return resultsCb(source, false);
    }

    private Callback<List<Station>> resultsCb(String source, boolean isPagination) {
        return new Callback<>() {
            @Override
            public void onResponse(Call<List<Station>> call, Response<List<Station>> rsp) {
                if (!isPagination) {
                    progressBar.setVisibility(View.GONE);
                }
                List<Station> body = rsp.body();
                if (rsp.isSuccessful() && body != null && !body.isEmpty()) {

                    String headerTxt = "";
                    boolean removeDubious = Option.getRadioRemoveDubiousStations();
                    boolean removeDuplicates = Option.getRadioRemoveSpamStations();
                    if (removeDubious || removeDuplicates) {
                        int nbRemovedDuplicates = 0;
                        int nbRemovedDubious = 0;
                        Set<String> removedNamesDuplicates = new HashSet<>();
                        Set<String> removedNamesDubious = new HashSet<>();
                        Map<String, Integer> countMap = new HashMap<>();

                        Iterator<Station> iterator = body.iterator();
                        while (iterator.hasNext()) {
                            Station s = iterator.next();
                            if (s.name == null)
                                continue;

                            String trimmedName = s.name.toLowerCase().replaceAll("[^a-z]", "");

                            boolean isCensored = false;
                            for (String censoredStation : Var.RADIO_STATION_CENSORED_LOWERCASE) {
                                if (trimmedName.contains(censoredStation)) {
                                    isCensored = true;
                                    break;
                                }
                            }
                            if (isCensored) {
                                iterator.remove();
                                continue;
                            }

                            if (removeDubious && Var.RADIO_STATION_BLACKLIST_LOWERCASE.contains(trimmedName)) {
                                iterator.remove();
                                nbRemovedDubious++;
                                removedNamesDubious.add(trimmedName);
                                continue;
                            }

                            if (removeDuplicates) {
                                String normalizedName = trimmedName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
                                Integer countObj = countMap.get(normalizedName);
                                int count = countObj != null ? countObj : 0;
                                if (count >= Var.RADIO_STATION_MAX_DUPLICATES) {
                                    iterator.remove();
                                    nbRemovedDuplicates++;
                                    removedNamesDuplicates.add(trimmedName);
                                } else {
                                    countMap.put(normalizedName, count + 1);
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
                    }

                    if (isPagination) {
                        viewModel.appendResults(body);
                        // If we got fewer results than requested, no more pages
                        if (body.size() < Option.getRadioApiNbResults()) {
                            viewModel.setLoading(false);
                            // hasMore will be set to false in appendResults if body is empty
                            // but we should also check if size < limit
                            // Actually, let's add a method to explicitly set hasMore
                        }
                        List<Station> allResults = viewModel.getResults().getValue();
                        if (allResults != null) {
                            viewModel.setHeaderCount(
                                    getString(R.string.Results_2pt) + allResults.size() + headerTxt);
                        }
                        myLog("radio pagination (" + source + ") = " + body.size() + " new items, total: "
                                + (allResults != null ? allResults.size() : 0));
                    } else {
                        viewModel.setResults(body);
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
                        // Empty response means no more
                        List<Station> empty = new ArrayList<>();
                        viewModel.appendResults(empty); // This will set hasMore to false
                    }
                }
            }

            @Override
            public void onFailure(Call<List<Station>> call, Throwable t) {
                if (!isPagination) {
                    progressBar.setVisibility(View.GONE);
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
}
