package com.driot.bookplayer.librivox;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.annotation.NonNull;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.nav.FullActivity;
import com.driot.bookplayer.activities.LibrivoxResultsActivity;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.LoadingProgressHelper;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.helpers.NetworkHelper;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class GetLibrivoxFacetListActivity extends FullActivity {

    public static final String EXTRA_FACET_MODE = "EXTRA_FACET_MODE";

    public static final int MODE_GENRE = 0;

    private RecyclerView rv;
    private LoadingProgressHelper progressHelper;
    private TextView tvProgressMessage;
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

    @Override
    protected int getNavSectionId() {
        return R.id.nav_add;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_get_librivox_by_facet;
    }

    @Override
    protected boolean enableOngoingTaskOverlay() {
        return true;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        InsetHelper.apply(this);

        rv = findViewById(R.id.recyclerView);
        tvProgressMessage = findViewById(R.id.tvProgressMessage);
        progressHelper = new LoadingProgressHelper();

        librivoxLanguageItem = (LibrivoxLanguageItem) getIntent()
                .getSerializableExtra(Intents.EXTRA_LIBRIVOX_LANGUAGE_ITEM);

        if (rv != null) {
            int span = getResources().getInteger(R.integer.classic_grid_span);
            GridLayoutManager glm = new GridLayoutManager(this, span);
            rv.setLayoutManager(glm);
            rv.setHasFixedSize(true);
            rv.addItemDecoration(new ViewHelper.SpacesItemDecoration(ViewHelper.dp(this, Var.GRID_LAYOUT_SPACER)));
        }
        adapter = new LibrivoxFacetCardAdapter(item -> {
            myLogI("--- user clicks genre " + item.name);
            if (!NetworkHelper.isConnected(this)) {
                myToastE(getString(R.string.no_internet_connection));
                return;
            }
            openLibrivoxResultsForGenre(item.name);
        });
        rv.setAdapter(adapter);

        loadGenres();

    }

    private void loadGenres() {
        progressHelper.start(tvProgressMessage, new LoadingProgressHelper.MessageProvider() {
            @NonNull
            @Override
            public String getInitialMessage() {
                return getString(R.string.loading_categories);
            }

            @NonNull
            @Override
            public String getTickMessage(long elapsedSec) {
                return getString(R.string.loading_categories) + "\n"
                        + elapsedSec + " " + getString(R.string.sec) + " " + getString(R.string.elapsed);
            }
        });

        List<LibrivoxGenre> all = LibrivoxGenreStore.getGenres(this);
        List<LibrivoxFacetItem> out = new ArrayList<>();
        for (LibrivoxGenre g : all) {
            out.add(new LibrivoxFacetItem(g.name, g.count));
        }
        progressHelper.stop();
        adapter.setItems(out);
    }

    private void openLibrivoxResultsForGenre(String genre) {
        Intent intent = new Intent(this, LibrivoxResultsActivity.class);
        intent.putExtra("mode", "MODE_GENRE");
        intent.putExtra("query", ""); // query not used in TRENDING
        intent.putExtra("genre", genre);
        intent.putExtra(Intents.EXTRA_LIBRIVOX_LANGUAGE_ITEM, librivoxLanguageItem);
        startActivity(intent);
    }

}
