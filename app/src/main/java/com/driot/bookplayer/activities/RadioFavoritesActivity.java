package com.driot.bookplayer.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.FavoritesTouchHelperCallback;
import com.driot.bookplayer.adapter.RadioFavoritesRVAdapter;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.helpers.NetworkStatusRowController;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.player.StartPlayHelper;
import com.driot.bookplayer.radio.RadioBrowserRepository;
import com.driot.bookplayer.radio.RadioFavoriteItem;
import com.driot.bookplayer.radio.RadioResultsViewModel;
import com.driot.bookplayer.radio.UrlResolve;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RadioFavoritesActivity extends BaseBottomNavActivity {

    private RadioResultsViewModel viewModel;
    private RecyclerView recyclerView;
    private RadioFavoritesRVAdapter adapter;
    private RadioBrowserRepository repo; // for resolveUrl on play()
    private View dropZone;
    private ItemTouchHelper touchHelper;

    private NetworkStatusRowController networkStatusController;

    @Override protected int getNavId() { return R.id.nav_radio; }
    @Override protected int getLayoutResId() { return R.layout.activity_radio_results; }
    @Override protected boolean enableOngoingTaskOverlay() { return true; }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        recyclerView = findViewById(R.id.recyclerView);
        dropZone     = findViewById(R.id.dragDeleteZone);

        View networkRow = findViewById(R.id.includeNetworkStatus);
        networkStatusController = new NetworkStatusRowController(this, networkRow);

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

                boolean online = networkStatusController != null && networkStatusController.hasInternet();
                if (!online) {
                    myToast(getString(R.string.no_internet_connection));
                    return;
                }

                if (Option.getRadioRenewUrl() || f.last_url==null || f.last_url.isEmpty()) {
                    myLog("Option renew Url = " + Option.getRadioRenewUrl() + ", lastUrl = [" + f.last_url + "]... => repo.resolveUrl(" + f.stationuuid + ") - " + f.name);
                    setProgressVisible(true, getString(R.string.checking_for_best_mirror));

                    // Resolve (counts a click on RadioBrowser) then play; fallback to stored url_resolved if you add it later
                    repo.resolveUrl(f.stationuuid, new Callback<>() {
                        @Override public void onResponse(
                                Call<UrlResolve> call,
                                Response<UrlResolve> rsp
                        ) {
                            setProgressVisible(false, null);
                            String stream = null;
                            if (rsp.isSuccessful() && rsp.body() != null && rsp.body().url != null && !rsp.body().url.isEmpty()) {
                                stream = rsp.body().url;
                                myLogI("resolveUrl success : " + stream);
                                f.last_url = stream;
                            }

                            if (stream != null) {
                                StartPlayHelper.onRadioFavoriteClick(getApplicationContext(), f, stream, "RadioFavoritesActivity - adapter callback: .onPlay() - after url renewed");
                            } else {
                                myToastE(getString(R.string.an_error_occurred));
                            }
                        }

                        @Override public void onFailure(
                                Call<UrlResolve> call, Throwable t
                        ) {
                            setProgressVisible(false, null);
                            if (NetworkHelper.isUnknownHost(t)) {
                                myToastE(getString(R.string.no_internet_connection));
                            } else {
                                myLogEE(t, "resolveUrl failed");
                                myToastE(getString(R.string.error_radio_renew_url));
                                if (!(f.last_url==null || f.last_url.isEmpty())) {
                                    StartPlayHelper.onRadioFavoriteClick(getApplicationContext(), f, f.last_url,"RadioFavoritesActivity - adapter callback: .onPlay() - url NOT renewed");
                                }
                            }
                        }
                    });
                } else {
                    myLogD("Option renew Url = False");
                    StartPlayHelper.onRadioFavoriteClick(getApplicationContext(), f, f.last_url,"RadioFavoritesActivity - adapter callback: .onPlay() - url NOT renewed");
                }
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

        setProgressVisible(true, getString(R.string.loading));
        viewModel.getFavoriteItems().observe(this, favorites -> {
            setProgressVisible(false, null);
            adapter.setItems(favorites);
        });

    }

    private void setProgressVisible(boolean visible, String progressMessage) {
        ProgressBar progressBar = findViewById(R.id.progressBar);
        TextView progressText = findViewById(R.id.progressText);
        if (visible) {
            progressBar.setVisibility(View.VISIBLE);
            if (progressMessage != null && !progressMessage.isEmpty()) {
                progressText.setText(progressMessage);
                progressText.setVisibility(View.VISIBLE);
            } else {
                progressText.setVisibility(View.GONE);
            }
        } else {
            progressBar.setVisibility(View.GONE);
            progressText.setVisibility(View.GONE);
        }
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
