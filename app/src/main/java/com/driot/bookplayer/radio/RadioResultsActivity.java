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
import com.driot.bookplayer.player.StartPlayHelper;
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

    private RadioResultsViewModel viewModel;
    private RadioBrowserRepository repo;
    private RadioResultRVAdapter adapter;

    @Override protected int getNavId() { return R.id.nav_radio; }
    @Override protected int getLayoutResId() { return R.layout.activity_radio_results; }
    @Override protected boolean enableOngoingTaskOverlay() { return true; }

    private boolean hasInternet = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        View networkRowView = findViewById(R.id.includeNetworkStatus);
        NetworkStatusViewModel netVm = new ViewModelProvider(this).get(NetworkStatusViewModel.class);
        new NetworkStatusRowController(this, networkRowView, this, netVm);
        netVm.getStatus().observe(this, status -> hasInternet = status.hasInternet );

        findViewById(R.id.groupFavoriteVsHistory).setVisibility(View.GONE);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar  = findViewById(R.id.progressBar);

        // ---- grid span (header full width) ----
        int span = getResources().getInteger(R.integer.radio_grid_span);
        GridLayoutManager glm = new GridLayoutManager(this, span);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                return position == 0 ? span : 1;
            }
        });
        recyclerView.setLayoutManager(glm);
        recyclerView.addItemDecoration(
                new ViewHelper.SpacesItemDecoration(ViewHelper.dp(this, Var.GRID_LAYOUT_SPACER))
        );

        adapter = new RadioResultRVAdapter(new RadioResultRVAdapter.OnActionListener() {
            @Override public void onPlay(Station s) {
                myLogI("-------- USER CLICK radio item -------- : " + s.name);

                if (!hasInternet) {
                    myToast(getString(R.string.no_internet_connection));
                    return;
                }

                final boolean renewOnClick = Option.getRadioRenewUrl();
                final boolean hasCachedUrl = s.url_resolved != null && !s.url_resolved.isEmpty();

                // ---------------------------------------------------------------------
                // FAST PATH:
                //   - We already have url_resolved
                //   - AND user did NOT ask "renew URL on click"
                //
                // → Play immediately, then background-renew without blocking the user.
                // ---------------------------------------------------------------------
                if (hasCachedUrl && !renewOnClick) {
                    myLogD("RadioResults: using cached url_resolved, scheduling background renew. url_resolved = [" + s.url_resolved + "]");
                    final long startTime = System.currentTimeMillis();

                    // 1) Immediate playback
                    StartPlayHelper.onRadioClick(
                            getApplicationContext(),
                            s,
                            s.url_resolved,
                            "RadioResultsActivity - onPlay() - using cached url_resolved"
                    );

                    // 2) Background renewal (no spinner / no toast)
                    repo.resolveUrl(s.stationuuid, new Callback<UrlResolve>() {
                        @Override
                        public void onResponse(Call<UrlResolve> call, Response<UrlResolve> rsp) {
                            if (!rsp.isSuccessful() ||
                                    rsp.body() == null ||
                                    rsp.body().url == null ||
                                    rsp.body().url.isEmpty()) {
                                myLogW("RadioResults background resolveUrl: no usable url for [" + s.name + "] in " + Tonio.formatHhMmSsMs(System.currentTimeMillis()-startTime));
                                return;
                            }

                            String newUrl = rsp.body().url;
                            if (newUrl.equals(s.url_resolved)) {
                                myLogD("RadioResults background resolveUrl: url unchanged for [" + s.name + "] -> [" + newUrl + "] in " + Tonio.formatHhMmSsMs(System.currentTimeMillis()-startTime));
                                return;
                            }

                            myLogI("RadioResults background resolveUrl success for [" + s.name + "] -> [" + newUrl + "] in " + Tonio.formatHhMmSsMs(System.currentTimeMillis()-startTime));
                            s.url_resolved = newUrl; // update in-memory

                            // Optional: persist in Room if you track stations there.
                            // For example, if you add this to your ViewModel:
                            // viewModel.updateResolvedUrl(getApplicationContext(), s.stationuuid, newUrl);
                        }

                        @Override
                        public void onFailure(Call<UrlResolve> call, Throwable t) {
                            myLogW("RadioResults background resolveUrl failed for [" + s.name + "] : [" + t + "] in " + Tonio.formatHhMmSsMs(System.currentTimeMillis()-startTime));
                            // Silent failure, cached url still works.
                        }
                    });

                    return;
                }

                // ---------------------------------------------------------------------
                // STRICT PATH:
                //   - No cached url_resolved (first click)
                //   - OR "always renew URL" option enabled
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
                                    "RadioResultsActivity - onPlay() - after url renewed"
                            );
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
                                        "RadioResultsActivity - onPlay() - fallback url_resolved after failure"
                                );
                            } else {
                                myToastE(getString(R.string.an_error_occurred));
                            }
                        }
                    }
                });
            }

            @Override public void onFavorite(Station s) {
                myLogI("--- user set favorite radio item --- : " + s.name);
                viewModel.toggleFavorite(RadioResultsActivity.this, s);
            }
        });

        recyclerView.setAdapter(adapter);

        // ---- VM + favorites ----
        viewModel = new ViewModelProvider(this).get(RadioResultsViewModel.class);
        viewModel.loadFavorites(this);
        viewModel.getFavoriteUuids().observe(this, uuids -> adapter.setFavorites(uuids));
        viewModel.getResults().observe(this, stations -> {
            adapter.setItems(stations);
            progressBar.setVisibility(View.GONE);
        });
        viewModel.getShouldFinish().observe(this, shouldFinish -> {
            if (Boolean.TRUE.equals(shouldFinish)) finish();
        });

        // ---- Read intent params ----
        String q       = getIntent().getStringExtra("query");        // station name substring
        String lang    = getIntent().getStringExtra("lang");         // e.g., "fr"
        String country = getIntent().getStringExtra("country");      // e.g., "FR"
        String tag     = getIntent().getStringExtra("tag");          // e.g., "jazz"

        if (q == null) q = "";
        if (lang == null) lang = "";
        if (country == null) country = "";
        if (tag == null) tag = "";

        viewModel.setLastParams(q, lang, country, tag);

        // ---- Header text (optional, like Librivox) ----
        String headerSearch = getString(R.string.Search_2pt)
                + (q.isEmpty() ? getString(R.string.search_nothing_specified) : q);
        String headerLang   = getString(R.string.Language_2pt) + lang;
        String headerCountryTag = (country.isEmpty() && tag.isEmpty()) ? "" : (country + (tag.isEmpty() ? "" : " • " + tag));
        adapter.setHeader(headerSearch, headerLang, headerCountryTag);
        adapter.setHeaderCount(getString(R.string.Results_2pt) + "...");

        // ---- Repo ----
        repo = new RadioBrowserRepository(
                this,
                /* discoverMirrors */ false, // keep async version for later
                /* log level */ Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL
        );

        // ---- Search ----
        progressBar.setVisibility(View.VISIBLE);
        String station_search_mode = getIntent().getStringExtra(GetRadioActivity.EXTRA_RADIO_STATION_SEARCH_MODE);
        if (station_search_mode==null) station_search_mode = "NO_MODE";

        myLog("API CALL...[" + station_search_mode + "] - q=" + q + " - lang=" + lang + " - country=" + country + " - tag=" + tag);

        switch (station_search_mode) {

            case "MODE_TOP_VOTE":
                repo.topVoted(Option.getRadioApiNbResults(), resultsCb("topVote"));
                adapter.setHeader(getString(R.string.Search_2pt) + " " + getString(R.string.top_vote));
                break;

            case "MODE_TOP_CLICK":
                repo.topClicked(Option.getRadioApiNbResults(), resultsCb("topClick"));
                adapter.setHeader(getString(R.string.Search_2pt) + " " + getString(R.string.top_click));
                break;

            case "MODE_LAST_CLICK":
                repo.lastClicked(Option.getRadioApiNbResults(), resultsCb("lastClick"));
                adapter.setHeader(getString(R.string.Search_2pt) + " " + getString(R.string.last_click));
                break;

            case "MODE_LAST_CHANGE":
                repo.lastChanged(Option.getRadioApiNbResults(), resultsCb("lastChange"));
                adapter.setHeader(getString(R.string.Search_2pt) + " " + getString(R.string.last_change));
                break;

            case "MODE_TAG":
                if (tag.isEmpty()) {
                    myToastE(getString(R.string.selected_language_error)); // or a “tag missing” message
                    finish();
                    return;
                }
                repo.byTag(tag, Option.getRadioApiNbResults(), resultsCb("tag"));
                adapter.setHeader(getString(R.string.by_tag) + " : " + tag);
                break;

            case "MODE_COUNTRY":
                if (country.isEmpty()) {
                    myToastE(getString(R.string.error_generic));
                    finish();
                    return;
                }
                repo.byCountry(country, Option.getRadioApiNbResults(), resultsCb("country"));
                adapter.setHeader(getString(R.string.by_country) + " : " + country);
                break;

            case "MODE_LANGUAGE":
                if (lang.isEmpty()) {
                    myToastE(getString(R.string.error_generic));
                    finish();
                    return;
                }
                repo.byLanguage(lang, Option.getRadioApiNbResults(), resultsCb("language"));
                adapter.setHeader(getString(R.string.by_language) + " : " + lang);
                break;

            case "MODE_SEARCH":
            default:
                if (q.isEmpty()) {
                    myToastE(getString(R.string.error_generic));
                    finish();
                    return;
                }
                repo.byName(q, Option.getRadioApiNbResults(), resultsCb("byname"));
                adapter.setHeader(getString(R.string.by_name) + " : " + q);

                //TODO maybe later put spinner back... not very useful right now
                //repo.search(q, nullIfBlank(tag), country, lang, Option.getRadioApiNbResults(), resultsCb("search"));
                /*
                // If you have a combined search, call that; otherwise choose a best-effort:
                if (!q.isEmpty()) {
                    repo.byName(q, Option.getRadioApiNbResults(), resultsCb("name"));
                } else if (!tag.isEmpty()) {
                    repo.byTag(tag, Option.getRadioApiNbResults(), resultsCb("tag"));
                } else if (!country.isEmpty()) {
                    repo.byCountry(country, Option.getRadioApiNbResults(), resultsCb("country"));
                } else if (!lang.isEmpty()) {
                    repo.byLanguage(lang, Option.getRadioApiNbResults(), resultsCb("language"));
                } else {
                    // fallback to trending if truly nothing specified
                    repo.topVoted(Option.getRadioApiNbResults(), resultsCb("trending"));
                }
                 */

                break;
        }

    }

    private Callback<List<Station>> resultsCb(String source) {
        return new Callback<>() {
            @Override public void onResponse(Call<List<Station>> call, Response<List<Station>> rsp) {
                progressBar.setVisibility(View.GONE);
                List<Station> body = rsp.body();
                if (rsp.isSuccessful() && body != null && !body.isEmpty()) {

                    String headerTxt = "";
                    if (Option.getRadioRemoveSpamStations()) {
                        int nbRemoved = 0;
                        Set<String> removedNames = new HashSet<>();
                        Map<String, Integer> countMap = new HashMap<>();

                        Iterator<Station> iterator = body.iterator();
                        while (iterator.hasNext()) {
                            Station s = iterator.next();
                            if (s.name == null) continue;

                            // Clean name: trim only for logging & blacklist
                            String trimmedName = s.name.trim();

                            // Skip blacklisted stations  --> WORKING but not used right now, so commented for speed
                            /*
                            if (Var.RADIO_STATION_BLACKLIST.contains(trimmedName)) {
                                iterator.remove();
                                nbRemoved++;
                                removedNames.add(trimmedName);
                                continue;
                            }
                             */

                            // Normalize name for duplicate detection: keep only alphanumeric, lowercase
                            String normalizedName = trimmedName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();

                            // Safe unboxing
                            Integer countObj = countMap.get(normalizedName);
                            int count = countObj != null ? countObj : 0;

                            if (count >= Var.RADIO_STATION_MAX_DUPLICATES) {
                                iterator.remove();
                                nbRemoved++;
                                removedNames.add(trimmedName); // log original trimmed name
                            } else {
                                countMap.put(normalizedName, count + 1);
                            }
                        }

                        if (nbRemoved > 0) {
                            headerTxt = "    (" + nbRemoved + " " + getString(R.string.spam_fake_stations_removed) + " : " + removedNames + ")";
                            myLog(nbRemoved + " stations removed");
                            myLog("Removed station names: " + removedNames);
                        }
                    }

                    viewModel.setResults(body);
                    adapter.setHeaderCount(getString(R.string.Results_2pt) + " " + body.size() + headerTxt);
                    myLog("radio results (" + source + ") = " + body.size());
                } else {
                    myToast(getString(R.string.no_result));
                    viewModel.requestFinish();
                }
            }
            @Override public void onFailure(Call<List<Station>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                if (NetworkHelper.isUnknownHost(t)) {
                    myToastE(getString(R.string.no_internet_connection));
                } else {
                    myLogEE(t, "radio search failed (" + source + ")");
                    myToastE(getString(R.string.an_error_occurred));
                }
                viewModel.requestFinish();
            }
        };
    }
}
