package com.driot.bookplayer.podcasts;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.utils.log.LoggingFragment;

import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Favorites/history list, reached from GetPodcastFragment. Back navigation to the
 * podcast search root is handled automatically by the nav graph back stack (only ever
 * pushed on top of getPodcastFragment), replacing the former getSectionParent() override.
 * Converted from PodcastFavoritesActivity - see [[radio_deeplink_applinks_fix]].
 */
@AndroidEntryPoint
public class PodcastFavoritesFragment extends LoggingFragment {

    private PodcastSearchResultsViewModel viewModel;
    private View rootView;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyMessage;
    private PodcastFavoritesRVAdapter adapter;

    // Favorites and history are two different Room LiveData queries (see PodcastDao); this
    // Mediator switches which one feeds the list, so the toggle just swaps the source instead of
    // needing separate manually-managed LiveData plumbing.
    private final MediatorLiveData<List<Podcast>> itemsLive = new MediatorLiveData<>();
    private LiveData<List<Podcast>> currentSource;
    private boolean isHistoryMode = false;

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

        recyclerView = view.findViewById(R.id.recyclerViewPodcast);
        InsetHelper.applyInsetsForScrollableInFullActivity(requireActivity(), recyclerView);
        progressBar = view.findViewById(R.id.progressBarPodcast);
        emptyMessage = view.findViewById(R.id.podcast_error_message);

        int span = getResources().getInteger(R.integer.classic_grid_span);
        GridLayoutManager glm = new GridLayoutManager(requireContext(), span);
        recyclerView.setLayoutManager(glm);
        recyclerView
                .addItemDecoration(new ViewHelper.SpacesItemDecoration(ViewHelper.dp(requireContext(), Var.GRID_LAYOUT_SPACER)));

        viewModel = new ViewModelProvider(this).get(PodcastSearchResultsViewModel.class);
        viewModel.getShouldFinish().observe(getViewLifecycleOwner(), shouldFinish -> {
            if (shouldFinish != null && shouldFinish)
                Navigation.findNavController(rootView).popBackStack();
        });

        // Known upfront (from the landing-screen setting) so the header's very first bind shows
        // the intended mode immediately, instead of always starting as "Favorites" and correcting
        // itself once the query resolves.
        Bundle args = getArguments() != null ? getArguments() : new Bundle();
        isHistoryMode = args.getBoolean(Intents.EXTRA_START_IN_HISTORY, false);

        adapter = new PodcastFavoritesRVAdapter(new PodcastFavoritesRVAdapter.OnActionListener() {
            @Override
            public void onItemClick(Podcast item) {
                myLogI(" --- user clicks podcast ---");
                Bundle navArgs = new Bundle();
                navArgs.putParcelable("podcast", item);
                Navigation.findNavController(rootView).navigate(R.id.podcastEpisodeFragment, navArgs);
            }

            @Override
            public void onToggleFavorites() {
                myLogI("--- user clicks favorites ---");
                setMode(false);
            }

            @Override
            public void onToggleHistory() {
                myLogI("--- user clicks history ---");
                setMode(true);
            }
        }, isHistoryMode);
        recyclerView.setAdapter(adapter);

        itemsLive.observe(getViewLifecycleOwner(), this::applyResults);
        setMode(isHistoryMode);
    }

    private void setMode(boolean history) {
        isHistoryMode = history;
        if (currentSource != null) {
            itemsLive.removeSource(currentSource);
        }
        currentSource = history ? viewModel.getListenedPodcastsLive() : viewModel.getFavoritePodcastsLive();
        itemsLive.addSource(currentSource, itemsLive::setValue);
    }

    private void applyResults(List<Podcast> podcastList) {
        if (podcastList == null)
            return;
        adapter.setItems(podcastList, isHistoryMode);
        progressBar.setVisibility(View.GONE);
        emptyMessage.setText(isHistoryMode ? R.string.detailed_stats_empty : R.string.no_favorite_podcasts_found);
        emptyMessage.setVisibility(podcastList.isEmpty() ? View.VISIBLE : View.GONE);
    }

}
