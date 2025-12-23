package com.driot.bookplayer.radio;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.BaseBottomNavActivity;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.ViewHelper;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class GetRadioCardListActivity extends BaseBottomNavActivity {

    public static final String EXTRA_FACET_MODE = "EXTRA_FACET_MODE";
    public static final int MODE_TAG      = 0;
    public static final int MODE_COUNTRY  = 1;
    public static final int MODE_LANGUAGE = 2;

    @IntDef({MODE_TAG, MODE_COUNTRY, MODE_LANGUAGE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface FacetMode {}

    private RecyclerView rv;
    private ProgressBar progress;
    private TagCardAdapter adapter;
    private RadioBrowserRepository repo;

    public static void start(Context ctx, @FacetMode int mode) {
        Intent i = new Intent(ctx, GetRadioCardListActivity.class)
                .putExtra(EXTRA_FACET_MODE, mode);
        ctx.startActivity(i);
    }

    @Override protected int getNavId() { return R.id.nav_radio; }
    @Override protected int getLayoutResId() { return R.layout.activity_get_radio_by_tag; }
    @Override protected boolean enableOngoingTaskOverlay() { return true; }
    
    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        rv = findViewById(R.id.recyclerView);
        progress = findViewById(R.id.progressBar);

        int span = getResources().getInteger(R.integer.radio_grid_span);
        if (span < 2) span = 2;
        GridLayoutManager glm = new GridLayoutManager(this, span);
        rv.setLayoutManager(glm);
        rv.addItemDecoration(
                new ViewHelper.SpacesItemDecoration(ViewHelper.dp(this, Var.GRID_LAYOUT_SPACER))
        );

        repo = new RadioBrowserRepository(
                this,
                /*discoverMirrors*/ false,
                /*log level*/ Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL
        );

        adapter = new TagCardAdapter(tagItem -> {
            myLogI("---- user clicks facet item: " + tagItem.name);
            // Route by current facet mode:
            @FacetMode int mode = getIntent().getIntExtra(EXTRA_FACET_MODE, MODE_TAG);
            Intent i = new Intent(this, RadioResultsActivity.class);
            switch (mode) {
                case MODE_COUNTRY:
                    i.putExtra(GetRadioActivity.EXTRA_RADIO_STATION_SEARCH_MODE, "MODE_COUNTRY")
                            .putExtra("country", tagItem.name)     // e.g. "FR"
                            .putExtra("lang", "")
                            .putExtra("tag", "")
                            .putExtra("query", "");
                    break;
                case MODE_LANGUAGE:
                    i.putExtra(GetRadioActivity.EXTRA_RADIO_STATION_SEARCH_MODE, "MODE_LANGUAGE")
                            .putExtra("lang", tagItem.name)        // e.g. "fr"
                            .putExtra("country", "")
                            .putExtra("tag", "")
                            .putExtra("query", "");
                    break;
                case MODE_TAG:
                default:
                    i.putExtra(GetRadioActivity.EXTRA_RADIO_STATION_SEARCH_MODE, "MODE_TAG")
                            .putExtra("tag", tagItem.name)         // e.g. "jazz"
                            .putExtra("lang", "")
                            .putExtra("country", "")
                            .putExtra("query", "");
                    break;
            }
            startActivity(i);
        });
        rv.setAdapter(adapter);

        loadFacetItems(getIntent().getIntExtra(EXTRA_FACET_MODE, MODE_TAG));
    }

    private void loadFacetItems(@FacetMode int mode) {
        progress.setVisibility(View.VISIBLE);
        // Reuse your existing repo list endpoints.
        // We’ll standardize them into TagItem(name, count, imageUrl?) for the adapter.

        switch (mode) {
            case MODE_COUNTRY:
                // If you already have: repo.getTopCountries(int limit, Callback<List<TagItem>> cb)
                // otherwise map your Country model to TagItem(name=ISO2 code / display).
                repo.getTopCountries(Var.RADIO_LIST_MAX_CARD_ITEM, new Callback<>() {
                    @Override public void onResponse(Call<List<TagItem>> call, Response<List<TagItem>> rsp) {
                        progress.setVisibility(View.GONE);
                        if (rsp.isSuccessful() && rsp.body() != null) {
                            adapter.setItems(rsp.body());
                        } else {
                            adapter.setItems(new ArrayList<>());
                        }
                    }
                    @Override public void onFailure(Call<List<TagItem>> call, Throwable t) {
                        progress.setVisibility(View.GONE);
                        adapter.setItems(new ArrayList<>());
                        myLogEE(t, "getTopCountries failed");
                    }
                });
                break;

            case MODE_LANGUAGE:
                // If you already have: repo.getTopLanguages(int limit, Callback<List<TagItem>> cb)
                repo.getTopLanguages(Var.RADIO_LIST_MAX_CARD_ITEM, new Callback<>() {
                    @Override public void onResponse(Call<List<TagItem>> call, Response<List<TagItem>> rsp) {
                        progress.setVisibility(View.GONE);
                        if (rsp.isSuccessful() && rsp.body() != null) {
                            adapter.setItems(rsp.body());
                        } else {
                            adapter.setItems(new ArrayList<>());
                        }
                    }
                    @Override public void onFailure(Call<List<TagItem>> call, Throwable t) {
                        progress.setVisibility(View.GONE);
                        adapter.setItems(new ArrayList<>());
                        myLogEE(t, "getTopLanguages failed");
                    }
                });
                break;

            case MODE_TAG:
            default:
                repo.getTopTags(Var.RADIO_LIST_MAX_CARD_ITEM, new Callback<>() {
                    @Override public void onResponse(Call<List<TagItem>> call, Response<List<TagItem>> rsp) {
                        progress.setVisibility(View.GONE);
                        if (rsp.isSuccessful() && rsp.body() != null) {
                            adapter.setItems(rsp.body());
                        } else {
                            adapter.setItems(new ArrayList<>());
                        }
                    }
                    @Override public void onFailure(Call<List<TagItem>> call, Throwable t) {
                        progress.setVisibility(View.GONE);
                        adapter.setItems(new ArrayList<>());
                        myLogEE(t, "getTopTags failed");
                    }
                });
                break;
        }
    }
}
