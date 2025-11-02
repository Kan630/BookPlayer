package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
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
import com.driot.bookplayer.player.RadioMiniPlayer;
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

    // --- mini player (via RadioMiniPlayer helper) ---
    private View miniPlayer;
    private ImageView ivMiniCover;
    private TextView tvMiniTitle;
    private ProgressBar progressMini;
    private ImageButton btnMiniPlayPause;
    private RadioMiniPlayer mini;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_radio_results);

        InsetHelper.applyTopInsetsTo(this, findViewById(R.id.rootLayout));
        InsetHelper.applyBottomInsetsForScrollable(this, findViewById(R.id.recyclerView));

        OngoingTaskHost.attach(
                this,
                R.id.topOverlayContainer,
                new Intent(this, AddResourceActivity.class)
        );

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

        // ---- mini player views + controller ----
        miniPlayer      = findViewById(R.id.miniPlayer);
        ivMiniCover     = findViewById(R.id.ivMiniCover);
        tvMiniTitle     = findViewById(R.id.tvMiniTitle);
        progressMini    = findViewById(R.id.progressMini);
        btnMiniPlayPause= findViewById(R.id.btnMiniPlayPause);

        mini = new RadioMiniPlayer(
                this, miniPlayer, ivMiniCover, tvMiniTitle, progressMini, btnMiniPlayPause
        );
        mini.setListener(msg -> myToastE(msg));

        // ---- adapter with play + favorite ----
        adapter = new RadioResultRVAdapter(new RadioResultRVAdapter.OnActionListener() {
            @Override public void onPlay(Station s) {
                myLogI("--- user clicks radio item --- : " + s.name);
                // Resolve (counts a click on RadioBrowser) then play; otherwise fallback to url_resolved
                progressBar.setVisibility(View.VISIBLE);
                repo.resolveUrl(s.stationuuid, new Callback<com.driot.bookplayer.objects.radio.UrlResolve>() {
                    @Override public void onResponse(
                            Call<com.driot.bookplayer.objects.radio.UrlResolve> call,
                            Response<com.driot.bookplayer.objects.radio.UrlResolve> rsp
                    ) {
                        progressBar.setVisibility(View.GONE);
                        String stream = null;
                        if (rsp.isSuccessful() && rsp.body() != null && rsp.body().url != null && !rsp.body().url.isEmpty()) {
                            myLogI("rsp.body().url : " + rsp.body().url);
                            stream = rsp.body().url;
                        } else if (s.url_resolved != null && !s.url_resolved.isEmpty()) {
                            myLogI("s.url_resolved : " + s.url_resolved);
                            stream = s.url_resolved;
                        }
                        if (stream != null) {
                            // cover/title in mini bar
                            mini.play(stream, s.name != null ? s.name : "", s.favicon);
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
                                mini.play(s.url_resolved, s.name != null ? s.name : "", s.favicon);
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
        String headerSearch = getString(R.string.Search_2pt) + (q.isEmpty() ? getString(R.string.search_nothing_specified) : q);
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
        repo.search(q, tag, country, lang, Option.getLibrivoxApiNbResults(), new Callback<List<Station>>() {
            @Override public void onResponse(Call<List<Station>> call, Response<List<Station>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.body() != null) {
                    if (response.body().isEmpty()) {
                        myToast(getString(R.string.no_result)); // ensure you have this string
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
    }

    @Override
    protected void onDestroy() {
        if (mini != null) {
            mini.release();
            mini = null;
        }
        super.onDestroy();
    }

    // --- tiny helpers for header/cover preview while resolving (optional) ---
    private void previewMiniBar(String title, String faviconUrl) {
        tvMiniTitle.setText(title != null && !title.isEmpty() ? title : "Radio");
        if (faviconUrl != null && !faviconUrl.isEmpty()) {
            Glide.with(ivMiniCover).load(faviconUrl)
                    .placeholder(R.drawable.ic_radio_24px)
                    .error(R.drawable.ic_radio_24px)
                    .into(ivMiniCover);
        } else {
            ivMiniCover.setImageResource(R.drawable.ic_radio_24px);
        }
        miniPlayer.setVisibility(View.VISIBLE);
        progressMini.setVisibility(View.VISIBLE);
        btnMiniPlayPause.setVisibility(View.INVISIBLE);
    }
}
