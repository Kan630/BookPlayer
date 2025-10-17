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
import com.driot.bookplayer.adapter.LibrivoxFavoritesRVAdapter;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.objects.LibrivoxItem;
import com.driot.bookplayer.objects.OngoingTaskHost;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.util.Collections;

public class LibrivoxFavoritesActivity extends LoggingActivity {

    private LibrivoxResultsViewModel viewModel;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private LibrivoxFavoritesRVAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_librivox_results);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);

        InsetHelper.applyTopInsetsTo(this, findViewById(R.id.rootLayout));
        InsetHelper.applyBottomInsetsForScrollable(this, findViewById(R.id.recyclerView));

        //ongoing book load ?
        OngoingTaskHost.attach(
                this,
                R.id.topOverlayContainer,
                new Intent(this, AddResourceActivity.class)); // tap => open details

        int span = getResources().getInteger(R.integer.classic_grid_span);
        recyclerView.setLayoutManager(new GridLayoutManager(this, span));
        recyclerView.addItemDecoration(
                new ViewHelper.SpacesItemDecoration(ViewHelper.dp(this, Var.GRID_LAYOUT_SPACER))
        );

        viewModel = new ViewModelProvider(this).get(LibrivoxResultsViewModel.class);

        adapter = new LibrivoxFavoritesRVAdapter(new LibrivoxFavoritesRVAdapter.OnItemClickListener() {
            @Override public void onItemClick(LibrivoxItem item) {
                Intent intent = new Intent(LibrivoxFavoritesActivity.this, LibrivoxDetailActivity.class);
                intent.putExtra("identifier", item.identifier);
                intent.putExtra("title", item.title);
                startActivity(intent);
            }

            @Override public void onFavoriteClick(LibrivoxItem item) {
                myLogI("------- user clicks favorite ------   for [" + item.identifier + "]");
                viewModel.toggleFavorite(item);
            }
        });
        recyclerView.setAdapter(adapter);

        progressBar.setVisibility(View.VISIBLE);
        viewModel.getFavoriteLibrivoxsLive().observe(this, favorites -> {
            progressBar.setVisibility(View.GONE);
            if (favorites == null || favorites.isEmpty()) {
                adapter.setItems(Collections.emptyList());
            } else {
                adapter.setItems(favorites);
            }
        });
    }
}
