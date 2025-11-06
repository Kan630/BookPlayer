package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.FavoritesTouchHelperCallback;
import com.driot.bookplayer.adapter.RadioFavoritesRVAdapter;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.objects.OngoingTaskHost;
import com.driot.bookplayer.radio.RadioBrowserRepository;
import com.driot.bookplayer.radio.RadioFavoriteItem;
import com.driot.bookplayer.radio.RadioResultsViewModel;
import com.driot.bookplayer.radio.Station;
import com.driot.bookplayer.radio.UrlResolve;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RadioFavoritesActivity extends LoggingActivity {

    private RadioResultsViewModel viewModel;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private RadioFavoritesRVAdapter adapter;
    private RadioBrowserRepository repo; // for resolveUrl on play()
    private View dropZone;
    private ItemTouchHelper touchHelper;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_radio_results);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar  = findViewById(R.id.progressBar);
        dropZone     = findViewById(R.id.dragDeleteZone);

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
                            Call<UrlResolve> call,
                            Response<UrlResolve> rsp
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
                            Call<UrlResolve> call, Throwable t
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
                viewModel.removeFavoriteUuid(RadioFavoritesActivity.this, f.stationuuid);
            }

            @Override public void onPersistOrder(List<RadioFavoriteItem> newOrder) {
                myLogI("--- user change favorite radio station order --- ");
                viewModel.reorderFavorites(RadioFavoritesActivity.this, newOrder);
            }
        });
        recyclerView.setAdapter(adapter);

        // Enable dragging
        FavoritesTouchHelperCallback cb = new FavoritesTouchHelperCallback(recyclerView, dropZone, adapter);
        touchHelper = new ItemTouchHelper(cb);
        touchHelper.attachToRecyclerView(recyclerView);
        adapter.setOnStartDragListener(vh -> touchHelper.startDrag(vh));

        // repo for resolveUrl
        repo = new RadioBrowserRepository(
                this,
                /* discoverMirrors */ false, // keep async discovery for later if you want
                /* log level */ com.driot.bookplayer.global.Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL
        );

        progressBar.setVisibility(View.VISIBLE);
        viewModel.getFavoriteItems().observe(this, favorites -> {
            progressBar.setVisibility(View.GONE);
            adapter.setItems(favorites);
        });

    }

}
