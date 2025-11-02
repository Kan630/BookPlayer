package com.driot.bookplayer.activities;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.myLogEE;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.myToastE;

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
import com.driot.bookplayer.objects.radio.RadioFavoriteItem;
import com.driot.bookplayer.objects.radio.RadioResultsViewModel;
import com.driot.bookplayer.objects.radio.RadioBrowserRepository;
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
        progressBar = findViewById(R.id.progressBar);

        InsetHelper.applyTopInsetsTo(this, findViewById(R.id.rootLayout));
        InsetHelper.applyBottomInsetsForScrollable(this, findViewById(R.id.recyclerView));

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
                repo.resolveUrl(f.stationuuid, new Callback<>() {
                    @Override public void onResponse(
                            Call<com.driot.bookplayer.objects.radio.UrlResolve> call,
                            Response<com.driot.bookplayer.objects.radio.UrlResolve> rsp
                    ) {
                        progressBar.setVisibility(View.GONE);
                        if (rsp.isSuccessful() && rsp.body() != null && rsp.body().url != null && !rsp.body().url.isEmpty()) {
                            myLogI("resolveUrl success : " + rsp.body().url);
                            String stream = rsp.body().url;
                            Intent i = new Intent(RadioFavoritesActivity.this, PlayActivity.class);
                            i.putExtra("streamUrl", stream);
                            i.putExtra("title", f.name);
                            startActivity(i);
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
                // Remove and refresh
                viewModel.toggleFavorite(RadioFavoritesActivity.this, toStationStub(f));
            }
        });
        recyclerView.setAdapter(adapter);

        // repo for resolveUrl
        repo = new com.driot.bookplayer.objects.radio.RadioBrowserRepository(
                this
                , false // TODO true async
                , com.driot.bookplayer.global.Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL
        );

        progressBar.setVisibility(View.VISIBLE);
        viewModel.getFavoriteItems().observe(this, favorites -> {
            progressBar.setVisibility(View.GONE);
            adapter.setItems(favorites);
        });
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
