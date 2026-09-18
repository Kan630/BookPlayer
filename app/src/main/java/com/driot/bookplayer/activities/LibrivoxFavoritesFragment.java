package com.driot.bookplayer.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.LibrivoxBookSourceRVAdapter;
import com.driot.bookplayer.db.BookSource;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.librivox.ArchiveItem;
import com.driot.bookplayer.utils.log.LoggingFragment;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class LibrivoxFavoritesFragment extends LoggingFragment {

    private LibrivoxResultsViewModel viewModel;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private LibrivoxBookSourceRVAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_librivox_results, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progressBar);

        int span = getResources().getInteger(R.integer.classic_grid_span);
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), span));
        recyclerView.addItemDecoration(
                new ViewHelper.SpacesItemDecoration(ViewHelper.dp(requireContext(), Var.GRID_LAYOUT_SPACER)));

        viewModel = new ViewModelProvider(this).get(LibrivoxResultsViewModel.class);

        adapter = new LibrivoxBookSourceRVAdapter(new LibrivoxBookSourceRVAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BookSource item) {
                myLogI("--- user clicks favorite item ----   id = [" + item.repoId + "] - title = [" + item.book_title
                        + "]");
                Bundle args = new Bundle();
                args.putString("identifier", item.repoId);
                args.putString("title", item.book_title);
                Navigation.findNavController(view).navigate(R.id.librivoxDetailFragment, args);
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
        viewModel.getFavoriteBookSourcesLive().observe(getViewLifecycleOwner(), favorites -> {
            progressBar.setVisibility(View.GONE);
            if (favorites == null || favorites.isEmpty()) {
                adapter.setItems(java.util.Collections.emptyList());
            } else {
                adapter.setItems(favorites);
            }
        });
    }
}
