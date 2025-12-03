package com.driot.bookplayer.librivox;

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
import com.driot.bookplayer.activities.LibrivoxResultsActivity;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.ViewHelper;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GetLibrivoxFacetListActivity extends BaseBottomNavActivity {

    public static final String EXTRA_FACET_MODE = "EXTRA_FACET_MODE";

    public static final int MODE_GENRE  = 0;
    public static final int MODE_AUTHOR = 1; // we’ll wire this later

    @IntDef({MODE_GENRE, MODE_AUTHOR})
    @Retention(RetentionPolicy.SOURCE)
    public @interface FacetMode {}

    private RecyclerView rv;
    private ProgressBar progress;
    private LibrivoxFacetCardAdapter adapter;
    private LibrivoxRepository repo;

    private LibrivoxLanguageItem librivoxLanguageItem; // language filter (may be null/empty)

    // --- Public helper: open for genres ---
    public static void startForGenres(Context ctx, LibrivoxLanguageItem librivoxLanguageItem) {
        Intent i = new Intent(ctx, GetLibrivoxFacetListActivity.class)
                .putExtra(EXTRA_FACET_MODE, MODE_GENRE)
                .putExtra(Intents.EXTRA_LIBRIVOX_LANGUAGE_ITEM, librivoxLanguageItem);
        ctx.startActivity(i);
    }

    // (Later you can add: startForAuthors(Context, String lang) similarly)

    @Override protected int getNavId() { return R.id.nav_add; }
    @Override protected int getLayoutResId() { return R.layout.activity_get_librivox_by_facet; }
    @Override protected boolean enableOngoingTaskOverlay() { return true; }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        rv = findViewById(R.id.recyclerView);
        progress = findViewById(R.id.progressBar);

        librivoxLanguageItem = (LibrivoxLanguageItem) getIntent().getSerializableExtra(Intents.EXTRA_LIBRIVOX_LANGUAGE_ITEM);

        int span = getResources().getInteger(R.integer.classic_grid_span);
        if (span < 2) span = 2;
        GridLayoutManager glm = new GridLayoutManager(this, span);
        rv.setLayoutManager(glm);
        rv.addItemDecoration(
                new ViewHelper.SpacesItemDecoration(ViewHelper.dp(this, Var.GRID_LAYOUT_SPACER))
        );

        adapter = new LibrivoxFacetCardAdapter(item -> {
            // User clicked a ROOT GENRE → open subgenre screen
            myLogI("--- user clicks genre " + item.name);
            openLibrivoxResultsForGenre(item.name);
        });

        rv.setAdapter(adapter);

        loadRootGenres();

    }


    private void loadFacetItems(@FacetMode int mode) {
        progress.setVisibility(View.VISIBLE);

        switch (mode) {
            case MODE_AUTHOR:
                // TODO next step: listTopAuthors(lang, …)
                // For now, just show empty list
                progress.setVisibility(View.GONE);
                adapter.setItems(new ArrayList<>());
                myLogW("GetLibrivoxFacetListActivity: MODE_AUTHOR not implemented yet");
                break;

            case MODE_GENRE:
            default:
                repo.listTopGenres(librivoxLanguageItem.code3, Var.LIBRIVOX_LIST_MAX_CARD_ITEM, new Callback<>() {
                    @Override
                    public void onResponse(Call<List<LibrivoxFacetItem>> call,
                                           Response<List<LibrivoxFacetItem>> rsp) {
                        progress.setVisibility(View.GONE);
                        if (rsp.isSuccessful() && rsp.body() != null) {
                            adapter.setItems(rsp.body());
                        } else {
                            adapter.setItems(new ArrayList<>());
                            myLogEE(null, "listTopGenres empty/invalid response");
                        }
                    }

                    @Override
                    public void onFailure(Call<List<LibrivoxFacetItem>> call, Throwable t) {
                        progress.setVisibility(View.GONE);
                        adapter.setItems(new ArrayList<>());
                        myLogEE(t, "listTopGenres failed");
                    }
                });
                break;
        }
    }

    private void loadRootGenres() {
        progress.setVisibility(View.VISIBLE);
        List<LibrivoxGenre> all = LibrivoxGenreStore.getGenres(this);
        List<LibrivoxFacetItem> out = new ArrayList<>();
        for (LibrivoxGenre g : all) {
            out.add(new LibrivoxFacetItem(g.name, g.count));
        }
        progress.setVisibility(View.GONE);
        adapter.setItems(out);
    }

    private void openLibrivoxResultsForGenre(String genre) {
        Intent intent = new Intent(this, LibrivoxResultsActivity.class);
        intent.putExtra("mode",  "MODE_GENRE");
        intent.putExtra("query", "");   // query not used in TRENDING
        intent.putExtra("genre", genre);
        intent.putExtra(Intents.EXTRA_LIBRIVOX_LANGUAGE_ITEM,  librivoxLanguageItem);
        startActivity(intent);
    }


}
