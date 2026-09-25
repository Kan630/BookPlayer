package com.driot.bookplayer.podcasts;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.PodcastDao;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.NetworkStatusRowController;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.utils.NetworkStatusViewModel;
import com.driot.bookplayer.utils.log.LoggingFragment;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Search/browse results grid, reached from GetPodcastFragment. Converted from the
 * former PodcastSearchResultsActivity - see [[radio_deeplink_applinks_fix]].
 */
@AndroidEntryPoint
public class PodcastSearchResultsFragment extends LoggingFragment {

    private View rootView;
    private PodcastSearchResultsViewModel viewModel;
    private ProgressBar progressBar;
    private TextView errorMessage;
    private PodcastSearchResultsRVAdapter adapter;
    Podcast podcast;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_podcast_search_result, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rootView = view;

        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewPodcast);
        InsetHelper.applyInsetsForScrollableInFullActivity(requireActivity(), recyclerView);

        View networkRowView = view.findViewById(R.id.includeNetworkStatus);
        NetworkStatusViewModel netVm = new ViewModelProvider(this).get(NetworkStatusViewModel.class);
        new NetworkStatusRowController(requireContext(), networkRowView, getViewLifecycleOwner(), netVm);

        progressBar = view.findViewById(R.id.progressBarPodcast);
        errorMessage = view.findViewById(R.id.podcast_error_message);

        int span = getResources().getInteger(R.integer.classic_grid_span);
        GridLayoutManager glm = new GridLayoutManager(requireContext(), span);
        // Position 0 is the adapter's header (title + result count): full width on multi-column grids.
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return position == 0 ? span : 1;
            }
        });
        recyclerView.setLayoutManager(glm);
        recyclerView
                .addItemDecoration(new ViewHelper.SpacesItemDecoration(ViewHelper.dp(requireContext(), Var.GRID_LAYOUT_SPACER)));

        viewModel = new ViewModelProvider(this).get(PodcastSearchResultsViewModel.class);

        // Observers
        viewModel.getResults().observe(getViewLifecycleOwner(), feeds -> {
            if (feeds != null) {
                adapter.setItems(feeds);

                // Construct Header Strings
                String q = viewModel.getLastQuery();
                String queryStr = getString(R.string.Search_2pt)
                        + (q == null || q.isEmpty() ? getString(R.string.Trending) : q);

                com.driot.bookplayer.objects.LanguageItem langItem = com.driot.bookplayer.helpers.LanguageHelper
                        .getLanguageForPodcastsByCode(viewModel.getLastLang());
                String langStr = getString(R.string.Language_2pt) + (langItem != null ? langItem.displayName : "");

                String countStr = getString(R.string.Results_2pt) + feeds.size();
                if (feeds.size() == Option.getPodcastIndexOrgApiNbResults()) {
                    countStr += " (" + getString(R.string.max_number_of_results_reached) + ")";
                }

                adapter.setHeaderInfo(queryStr, langStr, countStr);
            }
        });

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                progressBar.setVisibility(View.GONE);
                errorMessage.setVisibility(View.VISIBLE);
                errorMessage.setText(getString(R.string.error_label_multiline) + error);
                errorMessage.setTextColor(requireContext().getColor(R.color.orange_500));
            } else {
                errorMessage.setVisibility(View.GONE);
            }
        });

        viewModel.getShouldFinish().observe(getViewLifecycleOwner(), shouldFinish -> {
            if (shouldFinish != null && shouldFinish)
                Navigation.findNavController(rootView).popBackStack();
        });

        adapter = new PodcastSearchResultsRVAdapter(podcastFeed -> {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                PodcastDao dao = AppDatabase.getDatabase(requireContext()).podcastDao();
                podcast = dao.getPodcastByFeedId(podcastFeed.id);
                if (podcast == null) {
                    podcast = PodcastHelper.fromPodcastFeed(podcastFeed);
                    dao.insert(podcast);
                }

                requireActivity().runOnUiThread(() -> {
                    Bundle args = new Bundle();
                    args.putParcelable("podcast", podcast);
                    Navigation.findNavController(rootView).navigate(R.id.podcastEpisodeFragment, args);
                });
            });
        });

        recyclerView.setAdapter(adapter);

        viewModel.getFavoritePodcastsLive().observe(getViewLifecycleOwner(), favorites -> {
            adapter.setFavorites(favorites);
        });

        viewModel.getListenedPodcastsLive().observe(getViewLifecycleOwner(), history -> {
            adapter.setHistory(history);
        });

        Bundle args = getArguments() != null ? getArguments() : new Bundle();
        String query = args.getString("query");
        String lang = args.getString("lang");

        if (savedInstanceState == null) {
            searchPodcasts(query, lang);
        } else {
            // Rotation: ViewModel retains state.
            // Just ensure if empty we retry?
            if (viewModel.getResults().getValue() == null) {
                searchPodcasts(query, lang);
            }
        }
    }

    private void searchPodcasts(String query, String lang) {
        if (query == null && lang == null) { // Trending might have empty query
            // Fallback if needed
        }

        // If ViewModel already has data for this query, don't re-search
        if (viewModel.getResults().getValue() != null
                && (query == null || query.equals(viewModel.getLastQuery()))
                && (lang == null || lang.equals(viewModel.getLastLang()))) {
            return;
        }

        if (query != null && !query.isEmpty()) {
            viewModel.search(query, lang);
        } else {
            viewModel.fetchTrending(lang);
        }
    }

}
