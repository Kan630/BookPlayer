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
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.objects.OngoingTaskHost;
import com.driot.bookplayer.objects.radio.RadioBrowserRepository;
import com.driot.bookplayer.objects.radio.RadioResultsViewModel;
import com.driot.bookplayer.objects.radio.Station;
import com.driot.bookplayer.objects.radio.UrlResolve;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RadioResultsActivity extends LoggingActivity {

    // --- list ---
    private RecyclerView recyclerView;
    private ProgressBar progressBar;

    private RadioResultsViewModel viewModel;
    private RadioBrowserRepository repo;
    private RadioResultRVAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_radio_results);

        // Insets & overlays
        InsetHelper.applyInsetsForScrollableBehindNavBar(this, findViewById(R.id.coordinator_layout));
        //OngoingTaskHost.attach(this, R.id.topOverlayContainer, new Intent(this, AddResourceActivity.class)); //need to display import when browsing radio ? => option !? haha

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

                // Resolve (counts a click on RadioBrowser) then play; fallback to url_resolved
                repo.resolveUrl(s.stationuuid, new Callback<>() {
                    @Override public void onResponse(
                            Call<UrlResolve> call,
                            Response<UrlResolve> rsp
                    ) {
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
                            // Let AudioService + RadioMiniNowPlayingFragment handle playback & UI
                            androidx.core.content.ContextCompat.startForegroundService(
                                    getApplicationContext(),
                                    new Intent(getApplicationContext(), com.driot.bookplayer.player.AudioService.class)
                                            .setAction(com.driot.bookplayer.global.Intents.ACTION_PLAY_RADIO)
                                            .putExtra(com.driot.bookplayer.global.Intents.EXTRA_STREAM_URL, stream)
                                            .putExtra(com.driot.bookplayer.global.Intents.EXTRA_TITLE, s.name)
                                            .putExtra(com.driot.bookplayer.global.Intents.EXTRA_IMAGE_URL, s.favicon)
                                            .putExtra(com.driot.bookplayer.global.Intents.EXTRA_CALLER, "RadioResultsActivity - adapter callback: .onPlay()")
                                            .putExtra(com.driot.bookplayer.global.Intents.EXTRA_FOREGROUND, true)
                            );
                        } else {
                            myToastE(getString(R.string.an_error_occurred));
                        }
                    }

                    @Override public void onFailure(
                            Call<com.driot.bookplayer.objects.radio.UrlResolve> call, Throwable t
                    ) {
                        progressBar.setVisibility(View.GONE);
                        if (NetworkHelper.isUnknownHost(t)) {
                            myToastE(getString(R.string.no_internet_connection));
                        } else {
                            myLogEE(t, "resolveUrl failed");
                            if (s.url_resolved != null && !s.url_resolved.isEmpty()) {
                                // Fallback to stored resolved URL
                                androidx.core.content.ContextCompat.startForegroundService(
                                        getApplicationContext(),
                                        new Intent(getApplicationContext(), com.driot.bookplayer.player.AudioService.class)
                                                .setAction(com.driot.bookplayer.global.Intents.ACTION_PLAY_RADIO)
                                                .putExtra(com.driot.bookplayer.global.Intents.EXTRA_STREAM_URL, s.url_resolved)
                                                .putExtra(com.driot.bookplayer.global.Intents.EXTRA_TITLE, s.name)
                                                .putExtra(com.driot.bookplayer.global.Intents.EXTRA_IMAGE_URL, s.favicon)
                                                .putExtra(com.driot.bookplayer.global.Intents.EXTRA_CALLER, "RadioResultsActivity - adapter fallback: .onPlay()")
                                                .putExtra(com.driot.bookplayer.global.Intents.EXTRA_FOREGROUND, true)
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
        myLog("Searching..." + q + " " + lang + " " + country + " " + tag);
        repo.search(nullIfBlank(q), nullIfBlank(tag), nullIfBlank(country), nullIfBlank(lang), Option.getRadioApiNbResults(), new Callback<>() {
            @Override public void onResponse(Call<List<Station>> call, Response<List<Station>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.body() != null) {
                    if (response.body().isEmpty()) {
                        myToast(getString(R.string.no_result));
                        viewModel.requestFinish();
                    } else {
                        viewModel.setResults(response.body());
                        myLog("radio results = " + response.body().size());
                        adapter.setHeaderCount(getString(R.string.Results_2pt) + " " + response.body().size());
                    }
                } else {
                    myToastE(getString(R.string.an_error_occurred));
                    viewModel.requestFinish();
                }
            }

            @Override public void onFailure(Call<List<Station>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                if (NetworkHelper.isUnknownHost(t)) {
                    myToastE(getString(R.string.no_internet_connection));
                } else {
                    myLogEE(t, "radio search failed");
                    myToastE(getString(R.string.an_error_occurred));
                }
                viewModel.requestFinish();
            }
        });
/*
        // ---- Mini radio fragment (same as Favorites) ----
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.radioMiniContainer, new com.driot.bookplayer.player.RadioMiniNowPlayingFragment())
                    .commitNow();
        }

 */
    }
    private static @Nullable String nullIfBlank(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }
}
