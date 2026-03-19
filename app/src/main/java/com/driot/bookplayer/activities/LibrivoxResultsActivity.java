package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.LibrivoxResultRVAdapter;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.librivox.ArchiveItem;
import com.driot.bookplayer.librivox.LibrivoxLanguageItem;
import com.driot.bookplayer.nav.BaseBottomNavActivity;

import com.driot.bookplayer.helpers.LoadingProgressHelper;
import android.widget.TextView;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class LibrivoxResultsActivity extends BaseBottomNavActivity {

    private RecyclerView recyclerView;
    private LibrivoxResultRVAdapter adapter;
    private LibrivoxResultsViewModel viewModel;
    private ProgressBar progressBar;
    private ProgressBar progressBarLoadMore;
    private LoadingProgressHelper progressHelper;
    private TextView tvProgressMessage;
    private TextView tvEmptyMessage;

    @Override
    protected int getNavId() {
        return R.id.nav_add;
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        // Initialize views
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        tvProgressMessage = findViewById(R.id.tvProgressMessage);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        progressBarLoadMore = findViewById(R.id.progressBarLoadMore);

        progressHelper = new LoadingProgressHelper();

        // Setup RecyclerView
        int span = getResources().getInteger(R.integer.classic_grid_span);
        GridLayoutManager glm = new GridLayoutManager(this, span);
        recyclerView.setLayoutManager(glm);
        recyclerView.addItemDecoration(
                new ViewHelper.SpacesItemDecoration(ViewHelper.dp(this, Var.GRID_LAYOUT_SPACER)));

        // Setup adapter
        adapter = new LibrivoxResultRVAdapter(new LibrivoxResultRVAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(ArchiveItem item) {
                Intent intent = new Intent(LibrivoxResultsActivity.this, LibrivoxDetailActivity.class);
                intent.putExtra("identifier", item.identifier);
                intent.putExtra("title", item.title);
                startActivity(intent);
            }

            @Override
            public void onFavoriteClick(ArchiveItem item) {
                myLogI("User clicks favorite for [" + item.identifier + "]");
                viewModel.toggleFavorite(item);
            }
        });
        recyclerView.setAdapter(adapter);

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(LibrivoxResultsViewModel.class);

        // Scroll-to-load-more for MODE_TRENDING / MODE_LAST_ADDED
        setupScrollToLoadMore();

        // Setup observers
        setupObservers();

        // Get search parameters
        Intent intent = getIntent();
        String mode = intent.getStringExtra("mode");
        String query = intent.getStringExtra("query");
        String genre = intent.getStringExtra("genre");
        String author = intent.getStringExtra("author");
        LibrivoxLanguageItem selectedLanguageItem = (LibrivoxLanguageItem) intent
                .getSerializableExtra(Intents.EXTRA_LIBRIVOX_LANGUAGE_ITEM);

        // Validate parameters
        if (mode == null)
            mode = "MODE_SEARCH";
        if (query == null)
            query = "";

        if (selectedLanguageItem == null || selectedLanguageItem.name == null || selectedLanguageItem.name.isEmpty()) {
            myLogEE(null, "Bad arguments: lang is null/empty");
            finish();
            return;
        }

        // Setup header
        setupHeader(mode, query, genre, author, selectedLanguageItem);

        // Check cache (only for MODE_SEARCH)
        boolean canUseCache = "MODE_SEARCH".equals(mode);
        if (canUseCache
                && viewModel.getResults().getValue() != null
                && query.equals(viewModel.getLastQuery())
                && selectedLanguageItem.code2.equals(viewModel.getLastLang())) {
            myLogI("Using cached results (MODE_SEARCH)");
            return;
        }

        myLogI("No cache, querying (mode=" + mode + ")");

        // Store last search
        viewModel.setLastQuery(query);
        viewModel.setLastLang(selectedLanguageItem.code2);

        // Trigger appropriate search
        triggerSearch(mode, query, genre, author, selectedLanguageItem);
    }

    private void setupObservers() {
        // Observe loading state
        viewModel.getIsLoading().observe(this, isLoading -> {
            if (Boolean.TRUE.equals(isLoading)) {
                tvEmptyMessage.setVisibility(View.GONE);
                progressBar.setVisibility(View.VISIBLE);
                progressHelper.start(tvProgressMessage, new LoadingProgressHelper.MessageProvider() {
                    @NonNull
                    @Override
                    public String getInitialMessage() {
                        LibrivoxResultsViewModel.HeaderStatusData status = viewModel.getHeaderStatus().getValue();
                        String source = (status != null && status.apiSource != null) ? status.apiSource : "archive.org";
                        return getString(R.string.getting_first_results_from) + " " + source + "…";
                    }

                    @NonNull
                    @Override
                    public String getTickMessage(long elapsedSec) {
                        LibrivoxResultsViewModel.HeaderStatusData status = viewModel.getHeaderStatus().getValue();
                        String source = (status != null && status.apiSource != null) ? status.apiSource : "archive.org";
                        return getString(R.string.getting_first_results_from) + " " + source + "…\n"
                                + elapsedSec + " " + getString(R.string.sec) + " " + getString(R.string.elapsed);
                    }
                });
            } else {
                progressBar.setVisibility(View.GONE);
                progressHelper.stop();
            }
        });

        // Observe results (replace on first load, append on pagination)
        viewModel.getResults().observe(this, items -> {
            if (items == null)
                return;
            if (!items.isEmpty()) {
                tvEmptyMessage.setVisibility(View.GONE);
            }
            int currentAdapterSize = adapter.getItemCount() - 1; // -1 for header
            if (currentAdapterSize == 0 || items.size() <= currentAdapterSize) {
                adapter.setItems(items);
            } else {
                List<ArchiveItem> newItems = items.subList(currentAdapterSize, items.size());
                adapter.appendItems(newItems);
            }
        });

        // Observe header status (handles both simple and paged results)
        viewModel.getHeaderStatus().observe(this, statusData -> {
            if (statusData == null)
                return;

            String status;
            if (statusData.isLoading) {
                if (statusData.count == 0) {
                    // First fetch
                    status = getString(R.string.getting_first_results_from) + " " + statusData.apiSource + "…";
                } else {
                    // Subsequent pages
                    if (statusData.totalCount >= 0) {
                        status = getString(R.string.Results_2pt) + getString(R.string.librivox_books_loaded_of,
                                formatCount(statusData.count), formatCount(statusData.totalCount))
                                + " (" + getString(R.string.getting_more_from) + " " + statusData.apiSource + "…)";
                    } else {
                        status = getString(R.string.Results_2pt)
                                + getString(R.string.librivox_books_loaded, formatCount(statusData.count))
                                + " (" + getString(R.string.getting_more_from) + " " + statusData.apiSource + "…)";
                    }
                }
            } else {
                if (statusData.totalCount >= 0) {
                    // Show "Results: XX / YY books" when total is known (locale-formatted)
                    status = getString(R.string.Results_2pt) + getString(R.string.librivox_books_loaded_of,
                            formatCount(statusData.count), formatCount(statusData.totalCount));
                } else {
                    // Fallback: "Results: XX books loaded"
                    status = getString(R.string.Results_2pt)
                            + getString(R.string.librivox_books_loaded, formatCount(statusData.count));
                }
            }

            adapter.setHeaderCount(status);
        });

        // Observe errors
        viewModel.getErrorMessage().observe(this, errorMsg -> {
            if (errorMsg == null || errorMsg.isEmpty())
                return;

            if (errorMsg.startsWith("no_results_")) {
                String[] parts = errorMsg.split(":", 2);
                String type = parts[0].replace("no_results_", "");
                String detail = parts.length > 1 ? parts[1] : "";

                String finalMsg = "";
                switch (type) {
                    case "genre":
                        finalMsg = getString(R.string.librivox_no_audiobook_found_in_genre)
                                + " [" + detail + "]";
                        break;
                    case "search":
                        finalMsg = getString(R.string.librivox_no_audiobook_found_for_search)
                                + " [" + detail + "]";
                        break;
                    default:
                        finalMsg = getString(R.string.no_results_found);
                }
                tvEmptyMessage.setText(finalMsg);
                tvEmptyMessage.setVisibility(View.VISIBLE);

            } else if (errorMsg.startsWith("error:")) {
                String msg = errorMsg.substring(6);
                String finalError = "";
                if (NetworkHelper.isUnknownHost(new Exception(msg))) {
                    finalError = getString(R.string.no_internet_connection);
                } else if ("invalid_response".equals(msg)) {
                    finalError = getString(R.string.librivox_invalid_response);
                } else if ("archive_org_offline".equals(msg)) {
                    finalError = getString(R.string.archive_org_offline);
                } else {
                    finalError = getString(R.string.an_error_occurred);
                }
                tvEmptyMessage.setText(finalError);
                tvEmptyMessage.setVisibility(View.VISIBLE);
                tvEmptyMessage.setTextColor( ContextCompat.getColor(this, android.R.color.holo_red_dark));
            }
        });

        // Observe finish request
        viewModel.getShouldFinish().observe(this, shouldFinish -> {
            if (shouldFinish != null && shouldFinish) {
                finish();
            }
        });
        viewModel.getIsLoadingMore().observe(this, isLoadingMore -> {
            if (progressBarLoadMore != null) {
                progressBarLoadMore.setVisibility(Boolean.TRUE.equals(isLoadingMore) ? View.VISIBLE : View.GONE);
            }
        });
    }

    /** Locale-aware number formatting (e.g. 60399 → "60,399" or "60 399"). */
    private static String formatCount(long n) {
        return NumberFormat.getNumberInstance(Locale.getDefault()).format(n);
    }

    /**
     * Load next page when user scrolls near bottom (MODE_TRENDING / MODE_LAST_ADDED
     * only).
     */
    private void setupScrollToLoadMore() {
        GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager == null)
            return;
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                GridLayoutManager lm = (GridLayoutManager) recyclerView.getLayoutManager();
                if (lm == null)
                    return;
                int visibleItemCount = lm.getChildCount();
                int totalItemCount = lm.getItemCount();
                int firstVisibleItemPosition = lm.findFirstVisibleItemPosition();
                // Load more when user is near the bottom (within 5 items)
                if (!viewModel.isLoadingMore() && viewModel.hasMore()) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5) {
                        viewModel.loadNextPage();
                    }
                }
            }
        });
    }

    private void setupHeader(String mode, String query, String genre, String author,
            LibrivoxLanguageItem langItem) {
        myLog(langItem.toString());

        String langLine = getString(R.string.Language_2pt) + langItem.nativeName
                + (langItem.nativeName.equals(langItem.name)
                        ? ""
                        : " (" + langItem.name + ")");
        String searchLine;

        switch (mode) {
            case "MODE_TRENDING":
                searchLine = getString(R.string.Search_2pt) + getString(R.string.most_downloaded);
                break;

            case "MODE_LAST_ADDED":
                searchLine = getString(R.string.Search_2pt) + getString(R.string.last_added);
                break;

            case "MODE_GENRE":
                searchLine = getString(R.string.by_genre) + " : " + (genre == null ? "" : genre);
                langLine = null;
                break;

            case "MODE_SEARCH":
            default:
                if (query.isEmpty()) {
                    searchLine = getString(R.string.Search_2pt)
                            + getString(R.string.search_nothing_specified);
                } else {
                    searchLine = getString(R.string.Search_2pt) + query;
                }
                break;
        }

        adapter.setHeader(searchLine, langLine);
        adapter.setHeaderCount(getString(R.string.Results_2pt) + "...");
    }

    private void triggerSearch(String mode, String query, String genre, String author,
            LibrivoxLanguageItem langItem) {
        switch (mode) {
            case "MODE_TRENDING":
                myLogD("TRENDING mode → mostDownloadedByLang()");
                viewModel.searchTrending(langItem.code3);
                break;

            case "MODE_LAST_ADDED":
                myLogD("MODE_LAST_ADDED → mostRecentlyAddedByLang()");
                viewModel.searchLastAdded(langItem.code3);
                break;

            case "MODE_GENRE":
                if (genre == null || genre.trim().isEmpty()) {
                    myLogEE(null, "MODE_GENRE with empty genre");
                    myToastE(getString(R.string.error_generic));
                    viewModel.requestFinish();
                    return;
                }
                myLogD("GENRE mode → LibriVox API (genre=" + genre + ")");
                viewModel.searchByGenre(genre, langItem.code3);
                break;

            case "MODE_SEARCH":
            default:
                myLogD("SEARCH mode → searchByQueryAndLang()");
                viewModel.searchByQuery(query, langItem.code3);
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressHelper != null) {
            progressHelper.stop();
        }
    }

}