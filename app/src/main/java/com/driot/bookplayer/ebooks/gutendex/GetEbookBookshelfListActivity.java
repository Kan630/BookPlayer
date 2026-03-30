// GetEbookBookshelfListActivity.java
package com.driot.bookplayer.ebooks.gutendex;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.nav.BaseBottomNavActivity;
import com.driot.bookplayer.activities.EbookResultsActivity;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.ViewHelper;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class GetEbookBookshelfListActivity extends BaseBottomNavActivity {

    private RecyclerView rv;
    private ProgressBar progress;
    private EbookBookshelfCardAdapter adapter;
    private String lang; // language filter

    public static void start(Context ctx, String lang) {
        Intent i = new Intent(ctx, GetEbookBookshelfListActivity.class)
                .putExtra("lang", lang);
        ctx.startActivity(i);
    }

    @Override protected int getNavSectionId() { return R.id.nav_add; }
    @Override protected int getLayoutResId() { return R.layout.activity_get_librivox_by_facet; }
    @Override protected boolean enableOngoingTaskOverlay() { return true; }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        InsetHelper.apply(this);

        rv = findViewById(R.id.recyclerView);
        progress = findViewById(R.id.progressBar);

        lang = getIntent().getStringExtra("lang");
        if (lang == null || lang.isEmpty()) {
            myLogE("GetEbookBookshelfListActivity: missing/empty lang extra");
            finish();
            return;
        }

        if (rv != null) {
            int span = getResources().getInteger(R.integer.classic_grid_span);
            GridLayoutManager glm = new GridLayoutManager(this, span);
            rv.setLayoutManager(glm);
            rv.setHasFixedSize(true);
            rv.addItemDecoration(new ViewHelper.SpacesItemDecoration(ViewHelper.dp(this, Var.GRID_LAYOUT_SPACER)));
        }
        adapter = new EbookBookshelfCardAdapter(item -> {
            myLogI("--- user clicks bookshelf " + item.name);
            if (!NetworkHelper.isConnected(this)) {
                myToastE(getString(R.string.no_internet_connection));
                return;
            }
            openEbookResultsForBookshelf(item.name);
        });
        rv.setAdapter(adapter);

        loadBookshelves();
    }

    private void loadBookshelves() {
        progress.setVisibility(View.VISIBLE);
        List<GutenbergBookshelf> all = GutenbergBookshelfStore.getBookshelves(this);
        List<GutenbergBookshelfItem> out = new ArrayList<>();
        for (GutenbergBookshelf b : all) {
            out.add(new GutenbergBookshelfItem(b.name, b.count));
        }
        progress.setVisibility(View.GONE);
        adapter.setItems(out);
    }

    private void openEbookResultsForBookshelf(String bookshelf) {
        Intent intent = new Intent(this, EbookResultsActivity.class);
        intent.putExtra("query", ""); // no search query
        intent.putExtra("lang", lang);
        intent.putExtra("topic", bookshelf); // use topic parameter for bookshelf
        startActivity(intent);
    }
}
