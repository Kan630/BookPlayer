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
import com.driot.bookplayer.adapter.RadioFavoritesRVAdapter;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.objects.OngoingTaskHost;
import com.driot.bookplayer.objects.radio.RadioBrowserRepository;
import com.driot.bookplayer.objects.radio.RadioFavoriteItem;
import com.driot.bookplayer.objects.radio.RadioResultsViewModel;
import com.driot.bookplayer.utils.log.LoggingActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RadioFavoritesActivity extends LoggingActivity {

    private RadioResultsViewModel viewModel;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private RadioFavoritesRVAdapter adapter;
    private RadioBrowserRepository repo; // for resolveUrl on play()

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_radio_results);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar  = findViewById(R.id.progressBar);

        InsetHelper.applyInsetsForScrollableBehindNavBar(this, findViewById(R.id.coordinator_layout));
        OngoingTaskHost.attach(this, R.id.topOverlayContainer, new Intent(this, AddResourceActivity.class));

        int span = getResources().getInteger(R.integer.radio_grid_span);
        GridLayoutManager glm = new GridLayoutManager(this, span);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                // position 0 = header → take the whole row
                return position == 0 ? span : 1;
            }
        });
        recyclerView.setLayoutManager(glm);
        recyclerView.addItemDecoration(
                new ViewHelper.SpacesItemDecoration(ViewHelper.dp(this, Var.GRID_LAYOUT_SPACER))
        );

        viewModel = new ViewModelProvider(this).get(RadioResultsViewModel.class);
        viewModel.loadFavorites(this);

        adapter = new RadioFavoritesRVAdapter(new RadioFavoritesRVAdapter.OnActionListener() {
            @Override public void onPlay(RadioFavoriteItem f) {
                myLogI("--- user clicks radio item --- : " + f.name);
                progressBar.setVisibility(View.VISIBLE);

                // Resolve (counts a click on RadioBrowser) then play; fallback to stored url_resolved if you add it later
                repo.resolveUrl(f.stationuuid, new Callback<>() {
                    @Override public void onResponse(
                            Call<com.driot.bookplayer.objects.radio.UrlResolve> call,
                            Response<com.driot.bookplayer.objects.radio.UrlResolve> rsp
                    ) {
                        progressBar.setVisibility(View.GONE);
                        String stream = null;
                        if (rsp.isSuccessful() && rsp.body() != null && rsp.body().url != null && !rsp.body().url.isEmpty()) {
                            myLogI("resolveUrl success : " + rsp.body().url);
                            stream = rsp.body().url;
                        }

                        if (stream != null) {
                            // Stream with ExoPlayer via RadioMiniPlayer (no PlayActivity)
                            androidx.core.content.ContextCompat.startForegroundService(
                                    getApplicationContext(),
                                    new Intent(getApplicationContext(), com.driot.bookplayer.player.AudioService.class)
                                            .setAction(com.driot.bookplayer.global.Intents.ACTION_PLAY_RADIO)
                                            .putExtra(com.driot.bookplayer.global.Intents.EXTRA_STREAM_URL, stream)
                                            .putExtra(com.driot.bookplayer.global.Intents.EXTRA_TITLE, f.name)
                                            .putExtra(com.driot.bookplayer.global.Intents.EXTRA_IMAGE_URL, f.favicon)
                                            .putExtra(com.driot.bookplayer.global.Intents.EXTRA_CALLER, "RadioFavoritesActivity - adapter callback: .onPlay()")
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
                            myToastE(getString(R.string.an_error_occurred));
                        }
                    }
                });
            }

            @Override public void onUnfavorite(RadioFavoriteItem f) {
                myLogI("--- user Unfavorite radio item --- : " + f.name);
                // Remove and refresh (reuse VM’s toggle which expects a Station)
                viewModel.toggleFavorite(RadioFavoritesActivity.this, toStationStub(f));
            }
        });
        recyclerView.setAdapter(adapter);

        // repo for resolveUrl
        repo = new com.driot.bookplayer.objects.radio.RadioBrowserRepository(
                this,
                /* discoverMirrors */ false, // keep async discovery for later if you want
                /* log level */ com.driot.bookplayer.global.Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL
        );

        progressBar.setVisibility(View.VISIBLE);
        viewModel.getFavoriteItems().observe(this, favorites -> {
            progressBar.setVisibility(View.GONE);
            adapter.setItems(favorites);
        });
/*
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.radioMiniContainer, new com.driot.bookplayer.player.RadioMiniNowPlayingFragment())
                    .commitNow();
        }

 */
    }

    /** Minimal Station stub so we can reuse toggleFavorite() which expects a Station. */
    private com.driot.bookplayer.objects.radio.Station toStationStub(RadioFavoriteItem f) {
        com.driot.bookplayer.objects.radio.Station s = new com.driot.bookplayer.objects.radio.Station();
        s.stationuuid = f.stationuuid;
        s.name = f.name;
        s.favicon = f.favicon;
        s.codec = f.codec;
        s.bitrate = f.bitrate;
        s.country = f.country;
        s.language = f.language;
        s.tags = f.tags;
        return s;
    }

}
