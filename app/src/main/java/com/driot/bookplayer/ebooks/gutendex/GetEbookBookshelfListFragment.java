package com.driot.bookplayer.ebooks.gutendex;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.utils.log.LoggingFragment;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class GetEbookBookshelfListFragment extends LoggingFragment {

    private RecyclerView rv;
    private ProgressBar progress;
    private EbookBookshelfCardAdapter adapter;
    private String lang; // language filter

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
        progress = view.findViewById(R.id.progressBar);

        Bundle args = getArguments();
        lang = args != null ? args.getString("lang") : null;
        if (lang == null || lang.isEmpty()) {
            myLogE("GetEbookBookshelfListFragment: missing/empty lang extra");
            Navigation.findNavController(view).popBackStack();
            return;
        }

        if (rv != null) {
            int span = getResources().getInteger(R.integer.classic_grid_span);
            GridLayoutManager glm = new GridLayoutManager(requireContext(), span);
            rv.setLayoutManager(glm);
            rv.setHasFixedSize(true);
            rv.addItemDecoration(new ViewHelper.SpacesItemDecoration(ViewHelper.dp(requireContext(), Var.GRID_LAYOUT_SPACER)));
        }
        adapter = new EbookBookshelfCardAdapter(item -> {
            myLogI("--- user clicks bookshelf " + item.name);
            if (!NetworkHelper.isConnected(requireContext())) {
                myToastE(getString(R.string.no_internet_connection));
                return;
            }
            openEbookResultsForBookshelf(view, item.name);
        });
        rv.setAdapter(adapter);

        loadBookshelves();
    }

    private void loadBookshelves() {
        progress.setVisibility(View.VISIBLE);
        List<GutenbergBookshelf> all = GutenbergBookshelfStore.getBookshelves(requireContext());
        List<GutenbergBookshelfItem> out = new ArrayList<>();
        for (GutenbergBookshelf b : all) {
            out.add(new GutenbergBookshelfItem(b.name, b.count));
        }
        progress.setVisibility(View.GONE);
        adapter.setItems(out);
    }

    private void openEbookResultsForBookshelf(View view, String bookshelf) {
        Bundle args = new Bundle();
        args.putString("query", ""); // no search query
        args.putString("lang", lang);
        args.putString("topic", bookshelf); // use topic parameter for bookshelf
        Navigation.findNavController(view).navigate(com.driot.bookplayer.R.id.ebookResultsFragment, args);
    }
}
