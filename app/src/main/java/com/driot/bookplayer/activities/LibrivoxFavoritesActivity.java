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
import com.driot.bookplayer.adapter.LibrivoxBookSourceRVAdapter;
import com.driot.bookplayer.db.BookSource;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.librivox.ArchiveItem;
import com.driot.bookplayer.nav.BaseBottomNavActivity;

import java.util.Collections;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class LibrivoxFavoritesActivity extends BaseBottomNavActivity {

    private LibrivoxResultsViewModel viewModel;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private LibrivoxBookSourceRVAdapter adapter;

    @Override
    protected int getNavId() {
        return R.id.nav_library;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_librivox_results;
    }

    @Override
    protected boolean enableOngoingTaskOverlay() {
        return true;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);

        int span = getResources().getInteger(R.integer.classic_grid_span);
        recyclerView.setLayoutManager(new GridLayoutManager(this, span));
        recyclerView.addItemDecoration(
                new ViewHelper.SpacesItemDecoration(ViewHelper.dp(this, Var.GRID_LAYOUT_SPACER)));

        viewModel = new ViewModelProvider(this).get(LibrivoxResultsViewModel.class);

        adapter = new LibrivoxBookSourceRVAdapter(new LibrivoxBookSourceRVAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BookSource item) {
                myLogI("--- user clicks favorite item ----   id = [" + item.repoId + "] - title = [" + item.book_title
                        + "]");
                Intent intent = new Intent(LibrivoxFavoritesActivity.this, LibrivoxDetailActivity.class);
                intent.putExtra("identifier", item.repoId);
                intent.putExtra("title", item.book_title);
                startActivity(intent);
            }

            @Override
            public void onFavoriteClick(BookSource item) {
                myLogI("------- user clicks favorite ------   for [" + item.repoId + "]");
                // We need to map back to ArchiveItem for toggleFavorite if it only accepts
                // ArchiveItem
                // Or better: update toggleFavorite to handle BookSource or use repoId
                // For now, let's create a minimal ArchiveItem as toggleFavorite expects it
                ArchiveItem ai = new ArchiveItem();
                ai.identifier = item.repoId;
                ai.title = item.book_title;
                ai.is_favorite = item.is_favorite;
                viewModel.toggleFavorite(ai);
            }
        });
        recyclerView.setAdapter(adapter);

        progressBar.setVisibility(View.VISIBLE);
        viewModel.getFavoriteBookSourcesLive().observe(this, favorites -> {
            progressBar.setVisibility(View.GONE);
            if (favorites == null || favorites.isEmpty()) {
                adapter.setItems(java.util.Collections.emptyList());
            } else {
                adapter.setItems(favorites);
            }
        });
    }
}
