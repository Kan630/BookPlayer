package com.driot.bookplayer.activities;

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
import com.driot.bookplayer.adapter.EbookResultRVAdapter;
import com.driot.bookplayer.ebooks.gutendex.GutenbergLanguageItem;
import com.driot.bookplayer.ebooks.gutendex.GutenbergLanguageStore;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.LoadingProgressHelper;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.librivox.LanguageMapper;
import com.driot.bookplayer.utils.log.LoggingFragment;

import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class EbookResultsFragment extends LoggingFragment {

    RecyclerView recyclerView;
    ProgressBar progressBar;
    TextView tvProgressMessage;
    TextView tvEmptyMessage;

    private EbookResultRVAdapter adapter;
    private EbookResultsViewModel viewModel;

    private final LoadingProgressHelper progressHelper = new LoadingProgressHelper();

    private String query;
    private String lang;
    private String topic;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_ebook_results, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progressBar);
        tvProgressMessage = view.findViewById(R.id.tvProgressMessage);
        tvEmptyMessage = view.findViewById(R.id.tvEmptyMessage);

        int span = getResources().getInteger(R.integer.classic_grid_span);
        GridLayoutManager glm = new GridLayoutManager(requireContext(), span);
        recyclerView.setLayoutManager(glm);
        recyclerView.addItemDecoration(
                new ViewHelper.SpacesItemDecoration(ViewHelper.dp(requireContext(), Var.GRID_LAYOUT_SPACER)));

        adapter = new EbookResultRVAdapter(requireContext(), item -> {
            myLogI("User clicks ebook item id=[" + item.gutendexId + "] - title=[" + item.title + "]\nurl=["
                    + item.epubUrl + "]");

            Bundle args = new Bundle();
            args.putInt("gutendex_id", item.gutendexId);
            args.putString("title", item.title);
            args.putString("authors", item.authors);
            args.putString("language", item.language);
            args.putInt("downloads", item.downloadCount);
            args.putString("cover_url", item.coverUrl);
            args.putString("epub_url", item.epubUrl);
            Navigation.findNavController(view).navigate(R.id.ebookDetailFragment, args);
        });

        recyclerView.setAdapter(adapter);

        adapter.setFooterMessageProvider(new LoadingProgressHelper.MessageProvider() {
            @NonNull
            @Override
            public String getInitialMessage() {
                return buildFetchingNextPageMessage();
            }

            @NonNull
            @Override
            public String getTickMessage(long elapsedSec) {
                if (elapsedSec < Var.GUTENBERG_FOOTER_SLOW_HINT_SEC) {
                    return buildFetchingNextPageMessage();
                }
                return buildFetchingNextPageMessage() + "\n\n" + getString(R.string.gutenberg_slow_hint);
            }
        });

        // Infinite scroll
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) return;

                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                if (viewModel.canLoadMore()) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5) {
                        viewModel.loadNextPage();
                    }
                }
            }
        });

        // Get params from arguments
        Bundle args = getArguments();
        query = args != null ? args.getString("query") : null;
        lang = args != null ? args.getString("lang") : null;
        topic = args != null ? args.getString("topic") : null;

        if (lang == null || lang.isEmpty()) {
            myLogE("EbookResultsFragment: missing/empty lang extra");
            Navigation.findNavController(view).popBackStack();
            return;
        }
        if (query == null) query = "";
        if (topic == null) topic = "";

        // Header (always computed from arguments — not affected by rotation)
        String searchLine = getString(R.string.Search_2pt)
                + (query.isEmpty() ? (topic.isEmpty() ? getString(R.string.most_downloaded) : topic) : query);
        String langLine = getLanguageDisplayName(lang);
        adapter.setHeader(searchLine, langLine);
        adapter.setHeaderCount(getString(R.string.Results_2pt) + "...");

        myLogD("EbookResultsFragment - query=[" + query + "], lang=[" + lang + "], topic=[" + topic + "]");

        // ViewModel — survives rotation and nav bar switches
        viewModel = new ViewModelProvider(this).get(EbookResultsViewModel.class);

        viewModel.getInitialLoading().observe(getViewLifecycleOwner(), loading -> {
            if (Boolean.TRUE.equals(loading)) {
                progressBar.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
                tvEmptyMessage.setVisibility(View.GONE);
                adapter.setLoading(false);
                progressHelper.start(tvProgressMessage, new LoadingProgressHelper.MessageProvider() {
                    @NonNull
                    @Override
                    public String getInitialMessage() {
                        return getString(R.string.gutenberg_contacting);
                    }

                    @NonNull
                    @Override
                    public String getTickMessage(long elapsedSec) {
                        if (viewModel.isGutendexConnected()) {
                            return getString(R.string.wait_elapsed_connected,
                                    getString(R.string.gutenberg_connected),
                                    (int) elapsedSec, Var.GUTENDEX_READ_TIMEOUT_SEC);
                        } else {
                            return getString(R.string.wait_elapsed_connecting,
                                    getString(R.string.gutenberg_contacting),
                                    (int) elapsedSec, Var.GUTENDEX_CONNECT_TIMEOUT_SEC);
                        }
                    }
                });
            } else {
                progressBar.setVisibility(View.GONE);
                progressHelper.stop();
            }
        });

        viewModel.getItems().observe(getViewLifecycleOwner(), newItems -> {
            if (newItems != null) {
                adapter.setItems(newItems);
                recyclerView.setVisibility(View.VISIBLE);
                tvEmptyMessage.setVisibility(View.GONE);
            }
        });

        viewModel.getHeaderCount().observe(getViewLifecycleOwner(), count -> {
            if (count != null) adapter.setHeaderCount(count);
        });

        viewModel.getLoadingMore().observe(getViewLifecycleOwner(), loading -> {
            adapter.setLoading(Boolean.TRUE.equals(loading));
        });

        viewModel.getEmptyMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) {
                tvEmptyMessage.setText(msg);
                tvEmptyMessage.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                tvEmptyMessage.setVisibility(View.GONE);
            }
        });

        viewModel.fetchIfNeeded(query, lang, topic);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        progressHelper.stop();
    }

    private String buildFetchingNextPageMessage() {
        int total = viewModel.getTotalCount();
        int nextBatch = viewModel.getNextBatchSize();

        if (total > 0 && nextBatch > 0) {
            return getString(R.string.gutenberg_fetching_next_page_full,
                    viewModel.getFormattedLoadedCount(), viewModel.getFormattedTotalCount(),
                    viewModel.getFormattedNextBatchSize());
        }
        if (total > 0) {
            return getString(R.string.gutenberg_fetching_next_page, viewModel.getFormattedTotalCount());
        }
        return getString(R.string.gutenberg_fetching_next_page_unknown);
    }

    /**
     * Get the display name for a language code, formatted like LibriVox:
     * nativeName (name) if they differ, or just nativeName if they're the same.
     */
    private String getLanguageDisplayName(String langCode) {
        if (langCode == null || langCode.isEmpty()) {
            return getString(R.string.Language_2pt) + " " + langCode;
        }

        GutenbergLanguageStore store = new GutenbergLanguageStore(requireContext());
        List<GutenbergLanguageItem> languages = store.loadLanguages(R.raw.gutenberg_languages);

        for (GutenbergLanguageItem langItem : languages) {
            if (langCode.equalsIgnoreCase(langItem.code2)) {
                String nativeName = langItem.nativeName != null && !langItem.nativeName.isEmpty()
                        ? langItem.nativeName
                        : langItem.name;
                String displayName = nativeName;
                if (!nativeName.equals(langItem.name)) {
                    displayName = nativeName + " (" + langItem.name + ")";
                }
                return getString(R.string.Language_2pt) + " " + displayName;
            }
        }

        // Fallback: LanguageMapper
        String langName = LanguageMapper.getNameFromTwoLetters(langCode);
        if (langName != null && !langName.equals(langCode)) {
            return getString(R.string.Language_2pt) + " " + langName;
        }

        // Last resort: just show the code
        return getString(R.string.Language_2pt) + " " + langCode;
    }
}
