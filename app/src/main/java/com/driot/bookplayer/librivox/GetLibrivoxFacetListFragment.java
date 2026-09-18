package com.driot.bookplayer.librivox;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.LoadingProgressHelper;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.utils.log.LoggingFragment;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class GetLibrivoxFacetListFragment extends LoggingFragment {

    public static final String EXTRA_FACET_MODE = "EXTRA_FACET_MODE";

    public static final int MODE_GENRE = 0;

    private RecyclerView rv;
    private LoadingProgressHelper progressHelper;
    private TextView tvProgressMessage;
    private LibrivoxFacetCardAdapter adapter;

    private LibrivoxLanguageItem librivoxLanguageItem; // language filter (may be null/empty)

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_get_librivox_by_facet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rv = view.findViewById(R.id.recyclerView);
        tvProgressMessage = view.findViewById(R.id.tvProgressMessage);
        progressHelper = new LoadingProgressHelper();

        Bundle args = getArguments();
        librivoxLanguageItem = args != null
                ? (LibrivoxLanguageItem) args.getSerializable(Intents.EXTRA_LIBRIVOX_LANGUAGE_ITEM)
                : null;

        if (rv != null) {
            int span = getResources().getInteger(R.integer.classic_grid_span);
            GridLayoutManager glm = new GridLayoutManager(requireContext(), span);
            rv.setLayoutManager(glm);
            rv.setHasFixedSize(true);
            rv.addItemDecoration(new ViewHelper.SpacesItemDecoration(ViewHelper.dp(requireContext(), Var.GRID_LAYOUT_SPACER)));
        }
        adapter = new LibrivoxFacetCardAdapter(item -> {
            myLogI("--- user clicks genre " + item.name);
            if (!NetworkHelper.isConnected(requireContext())) {
                myToastE(getString(R.string.no_internet_connection));
                return;
            }
            openLibrivoxResultsForGenre(view, item.name);
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

        List<LibrivoxGenre> all = LibrivoxGenreStore.getGenres(requireContext());
        List<LibrivoxFacetItem> out = new ArrayList<>();
        for (LibrivoxGenre g : all) {
            out.add(new LibrivoxFacetItem(g.name, g.count));
        }
        progressHelper.stop();
        adapter.setItems(out);
    }

    private void openLibrivoxResultsForGenre(View view, String genre) {
        Bundle args = new Bundle();
        args.putString("mode", "MODE_GENRE");
        args.putString("query", ""); // query not used in TRENDING
        args.putString("genre", genre);
        args.putSerializable(Intents.EXTRA_LIBRIVOX_LANGUAGE_ITEM, librivoxLanguageItem);
        Navigation.findNavController(view).navigate(com.driot.bookplayer.R.id.librivoxResultsFragment, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (progressHelper != null) {
            progressHelper.stop();
        }
    }

}
