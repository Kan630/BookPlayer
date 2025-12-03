package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

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
import com.driot.bookplayer.librivox.LibrivoxRepository;
import com.driot.bookplayer.librivox.LibrivoxApiResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LibrivoxResultsActivity extends BaseBottomNavActivity {

    RecyclerView recyclerView;
    LibrivoxResultRVAdapter adapter;

    ProgressBar progressBar;

    public static final String API_SORT = "downloads desc";

    private LibrivoxResultsViewModel viewModel;

    @Override protected int getNavId() { return R.id.nav_add; }
    @Override protected int getLayoutResId() { return R.layout.activity_librivox_results; }
    @Override protected boolean enableOngoingTaskOverlay() { return true; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);

        int span = getResources().getInteger(R.integer.classic_grid_span);
        GridLayoutManager glm = new GridLayoutManager(this, span);
        recyclerView.setLayoutManager(glm);
        recyclerView.addItemDecoration(
                new ViewHelper.SpacesItemDecoration(ViewHelper.dp(this, Var.GRID_LAYOUT_SPACER))
        );

        adapter = new LibrivoxResultRVAdapter(new LibrivoxResultRVAdapter.OnItemClickListener() {
            @Override public void onItemClick(ArchiveItem item) {
                Intent intent = new Intent(LibrivoxResultsActivity.this, LibrivoxDetailActivity.class);
                intent.putExtra("identifier", item.identifier);
                intent.putExtra("title", item.title);
                startActivity(intent);
            }
            @Override public void onFavoriteClick(ArchiveItem item) {
                myLogI("------- user clicks favorite ------   for [" + item.identifier + "]");
                viewModel.toggleFavorite(item);
            }
        });
        recyclerView.setAdapter(adapter);

        // ✅ INIT VIEWMODEL
        viewModel = new ViewModelProvider(this).get(LibrivoxResultsViewModel.class);

        // ✅ OBSERVE RESULTS
        viewModel.getResults().observe(this, items -> {
            adapter.setItems(items);
            progressBar.setVisibility(View.GONE);
            String strResultsCount;
            if (items != null && items.size() == Option.getLibrivoxApiNbResults()) {
                strResultsCount = getString(R.string.max_number_of_results_reached) + " (" + items.size() + ")";
            } else {
                strResultsCount = getString((R.string.nb_of_audios_found)) + " : " + (items == null ? 0 : items.size());
            }
            adapter.setHeaderCount(strResultsCount);
        });

        // ✅ OBSERVE FINISH REQUEST
        viewModel.getShouldFinish().observe(this, shouldFinish -> {
            if (shouldFinish != null && shouldFinish) finish();
        });

        // 🔄 GET SEARCH PARAMS
        Intent intent = getIntent();
        String mode  = intent.getStringExtra("mode"); // "MODE_SEARCH", "MODE_TRENDING", "MODE_GENRE", "MODE_AUTHOR"....
        String query = intent.getStringExtra("query");
        String genre  = intent.getStringExtra("genre");  // for MODE_GENRE
        String author = intent.getStringExtra("author"); // for MODE_AUTHOR
        LibrivoxLanguageItem selectedLanguageItem = (LibrivoxLanguageItem) intent.getSerializableExtra(Intents.EXTRA_LIBRIVOX_LANGUAGE_ITEM);

        if (mode == null) mode = "MODE_SEARCH";
        if (query == null) query = "";
        if (selectedLanguageItem == null || String.valueOf(selectedLanguageItem.name).isEmpty()) {
            myLogEE(null, "bad arguments: lang is null/empty");
            finish();
            return;
        }

        // 🔤 Header lines depending on mode
        myLog(selectedLanguageItem.toString());
        CharSequence langLine = getString(R.string.Language_2pt) + selectedLanguageItem.nativeName + (selectedLanguageItem.nativeName.equals(selectedLanguageItem.name) ? "" : " (" + selectedLanguageItem.name + ")");
        CharSequence searchLine;
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
            case "MODE_AUTHOR":
                searchLine = getString(R.string.by_author) + " : " + (author == null ? "" : author);
                break;
            case "MODE_SEARCH":
            default:
                if (query.isEmpty()) {
                    searchLine = getString(R.string.Search_2pt) + getString(R.string.search_nothing_specified);
                } else {
                    searchLine = getString(R.string.Search_2pt) + query;
                }
                break;
        }
        adapter.setHeader(searchLine, langLine);
        adapter.setHeaderCount(getString(R.string.Results_2pt) + "...");

        // 🔄 Check cache
        // Only safe for "normal" search; facet/trending results depend on mode/genre/author.
        boolean canUseCache = "MODE_SEARCH".equals(mode);
        if (canUseCache &&
                viewModel.getResults().getValue() != null &&
                query.equals(viewModel.getLastQuery()) &&
                selectedLanguageItem.code2.equals(viewModel.getLastLang())) {
            myLogI("Using cached results (MODE_SEARCH)");
            return;
        } else {
            myLogI("No cache, let's query again (mode=" + mode + ")");
        }

        // ✅ Store last search (only query/lang – mode is currently ignored in VM)
        viewModel.setLastQuery(query);
        viewModel.setLastLang(selectedLanguageItem.code2);

        LibrivoxRepository repo = new LibrivoxRepository(this, Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL);

        progressBar.setVisibility(View.VISIBLE);

        // Common error handler for archive.org callbacks
        String finalQuery = query;
        Callback<LibrivoxApiResponse> cbArchive = new Callback<>() {
            @Override
            public void onResponse(Call<LibrivoxApiResponse> call, Response<LibrivoxApiResponse> response) {
                progressBar.setVisibility(View.GONE);
                if (response.body() != null && response.body().response != null) {
                    List<ArchiveItem> results = response.body().response.docs;
                    if (results.isEmpty()) {
                        myToast("[" + selectedLanguageItem.name + "] "
                                + getString(R.string.librivox_no_audiobook_found_for_search)
                                + " [" + finalQuery + "]");
                        viewModel.requestFinish();
                    } else {
                        viewModel.enrichWithLocalState(results);
                        myLog(results.size() + " results found");
                    }
                } else {
                    myLogEE(null, "invalid response body from librivox");
                    myToastE(getString(R.string.librivox_invalid_response));
                    viewModel.requestFinish();
                }
            }

            @Override
            public void onFailure(Call<LibrivoxApiResponse> call, Throwable t) {
                if (NetworkHelper.isUnknownHost(t)) {
                    myToastE(getString(R.string.no_internet_connection));
                    myLogW(t.toString());
                } else {
                    myLogEE(t, "librivox api search on Failure");
                    myToastEE(t, getString(R.string.an_error_occurred));
                }
                progressBar.setVisibility(View.GONE);
                viewModel.requestFinish();
            }
        };

        // --- Route by mode ---
        switch (mode) {
            case "MODE_TRENDING":
                myLogD("LibrivoxResultsActivity: TRENDING mode → mostDownloadedByLang()");
                repo.mostDownloadedByLang(selectedLanguageItem.code3, Option.getLibrivoxApiNbResults(), cbArchive);
                break;

            case "MODE_LAST_ADDED":
                myLogD("LibrivoxResultsActivity: MODE_LAST_ADDED → mostDownloadedByLang()");
                repo.mostRecentlyAddedByLang(selectedLanguageItem.code3, Option.getLibrivoxApiNbResults(), cbArchive);
                break;

            case "MODE_GENRE": {
                if (genre == null || genre.trim().isEmpty()) {
                    myLogEE(null, "MODE_GENRE with empty genre");
                    myToastE(getString(R.string.error_generic));
                    viewModel.requestFinish();
                    return;
                }

                myLogD("LibrivoxResultsActivity: GENRE mode → LibriVox API (genre=" + genre + ")");

                final String fLang  = selectedLanguageItem.code3;
                final String fGenre = genre;
                int limit = Option.getLibrivoxApiNbResults();

                Callback<List<ArchiveItem>> cbGenre = new Callback<>() {
                    @Override
                    public void onResponse(Call<List<ArchiveItem>> call,
                                           Response<List<ArchiveItem>> response) {
                        progressBar.setVisibility(View.GONE);
                        List<ArchiveItem> apiItems = response.body();
                        if (apiItems == null || apiItems.isEmpty()) {
                            String msg = getString(R.string.librivox_no_audiobook_found_in_genre) + " [" + fGenre + "]"; //"[" + fLang + "] "
                            myToast(msg);
                            viewModel.requestFinish();
                        } else {
                            viewModel.enrichWithLocalState(apiItems);
                            myLog(apiItems.size() + " results found (LibriVox API / genre)");
                        }
                    }

                    @Override
                    public void onFailure(Call<List<ArchiveItem>> call, Throwable t) {
                        progressBar.setVisibility(View.GONE);
                        if (NetworkHelper.isUnknownHost(t)) {
                            myToastE(getString(R.string.no_internet_connection));
                            myLogW(t.toString());
                        } else {
                            myLogEE(t, "LibriVox API genre search on Failure");
                            myToastEE(t, getString(R.string.an_error_occurred));
                        }
                        viewModel.requestFinish();
                    }
                };

                repo.searchArchiveItemsByGenreAndLangLibrivox(
                        fLang,
                        false,
                        fGenre,
                        limit,
                        cbGenre
                );
                break;
            }

            case "MODE_AUTHOR":
                if (author == null || author.trim().isEmpty()) {
                    myLogEE(null, "MODE_AUTHOR with empty author");
                    myToastE(getString(R.string.error_generic));
                    viewModel.requestFinish();
                    return;
                }
                myLogD("LibrivoxResultsActivity: AUTHOR mode → mostDownloadedByAuthor(" + author + ")");
                repo.mostDownloadedByAuthor(selectedLanguageItem.code3, author, Option.getLibrivoxApiNbResults(), cbArchive);
                break;

            case "MODE_SEARCH":
            default:
                myLogD("LibrivoxResultsActivity: SEARCH mode → searchByQueryAndLang()");
                repo.searchByQueryAndLang(query, selectedLanguageItem.code3, Option.getLibrivoxApiNbResults(), cbArchive);
                break;
        }
    }
}
