package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.RadioResultRVAdapter;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.helpers.NetworkStatusRowController;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.player.MediaService;
import com.driot.bookplayer.player.StartPlayHelper;
import com.driot.bookplayer.radio.RadioBrowserRepository;
import com.driot.bookplayer.radio.RadioResultsViewModel;
import com.driot.bookplayer.radio.Station;
import com.driot.bookplayer.radio.UrlResolve;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RadioResultsActivity extends BaseBottomNavActivity {

    // --- list ---
    private RecyclerView recyclerView;
    private ProgressBar progressBar;

    private RadioResultsViewModel viewModel;
    private RadioBrowserRepository repo;
    private RadioResultRVAdapter adapter;

    private NetworkStatusRowController networkStatusController;

    @Override protected int getNavId() { return R.id.nav_radio; }
    @Override protected int getLayoutResId() { return R.layout.activity_radio_results; }
    @Override protected boolean enableOngoingTaskOverlay() { return true; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        View networkRow = findViewById(R.id.includeNetworkStatus);
        networkStatusController = new NetworkStatusRowController(this, networkRow);

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

        // ---- adapter with play + favorite ----
        adapter = new RadioResultRVAdapter(new RadioResultRVAdapter.OnActionListener() {
            @Override public void onPlay(Station s) {
                myLogI("-------- USER CLICK radio item -------- : " + s.name);
                progressBar.setVisibility(View.VISIBLE);
                myLog("main progressbar true, api call resolve url from stationuuid");

                // Resolve (counts a click on RadioBrowser) then play; fallback to url_resolved
                final long topStart = System.currentTimeMillis();
                repo.resolveUrl(s.stationuuid, new Callback<>() {
                    @Override public void onResponse(
                            Call<UrlResolve> call,
                            Response<UrlResolve> rsp
                    ) {
                        myLog("main progressbar false, api call onResponse in " + (System.currentTimeMillis()-topStart) + "ms.");
                        progressBar.setVisibility(View.GONE);

                        String stream = null;
                        if (rsp.isSuccessful() && rsp.body() != null && rsp.body().url != null && !rsp.body().url.isEmpty()) {
                            myLogI("resolveUrl success : " + rsp.body().url);
                            stream = rsp.body().url;
                        } else if (s.url_resolved != null && !s.url_resolved.isEmpty()) {
                            myLogI("fallback url_resolved : " + s.url_resolved);
                            stream = s.url_resolved;
                        }

                        if (stream != null) {
                            StartPlayHelper.onRadioClick(getApplicationContext(), s, stream, "RadioResultsActivity - adapter callback: .onPlay()");
                        } else {
                            myToastE(getString(R.string.an_error_occurred));
                        }
                    }

                    @Override public void onFailure(
                            Call<UrlResolve> call, Throwable t
                    ) {
                        progressBar.setVisibility(View.GONE);
                        if (NetworkHelper.isUnknownHost(t)) {
                            myToastE(getString(R.string.no_internet_connection));
                        } else {
                            myLogEE(t, "resolveUrl failed");
                            if (s.url_resolved != null && !s.url_resolved.isEmpty()) {
                                // Fallback to stored resolved URL
                                myLogI("fallback url_resolved : " + s.url_resolved);
                                StartPlayHelper.onRadioClick(getApplicationContext(), s, s.url_resolved, "RadioResultsActivity - adapter callback: .onPlay() - fallback url_resolved");
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

            case "MODE_TRENDING":
                repo.topVoted(Option.getRadioApiNbResults(), resultsCb("trending"));
                adapter.setHeader(getString(R.string.Search_2pt) + " " + getString(R.string.trending_radios));
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
    private static @Nullable String nullIfBlank(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }


    private String safe(String s) { return s == null ? "" : s.trim(); }

    private Callback<List<Station>> resultsCb(String source) {
        return new Callback<>() {
            @Override public void onResponse(Call<List<Station>> call, Response<List<Station>> rsp) {
                progressBar.setVisibility(View.GONE);
                List<Station> body = rsp.body();
                if (rsp.isSuccessful() && body != null && !body.isEmpty()) {
                    viewModel.setResults(body);
                    adapter.setHeaderCount(getString(R.string.Results_2pt) + " " + body.size());
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
    @Override
    protected void onStart() {
        super.onStart();
        if (networkStatusController != null) {
            networkStatusController.start();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (networkStatusController != null) {
            networkStatusController.stop();
        }
    }
}
