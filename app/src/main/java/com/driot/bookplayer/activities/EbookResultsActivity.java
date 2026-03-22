package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.EbookResultRVAdapter;
import com.driot.bookplayer.ebooks.EbookItem;
import com.driot.bookplayer.ebooks.gutendex.GutendexApiService;
import com.driot.bookplayer.ebooks.gutendex.GutendexBook;
import com.driot.bookplayer.ebooks.gutendex.GutendexMapper;
import com.driot.bookplayer.ebooks.gutendex.GutendexResponse;
import com.driot.bookplayer.ebooks.gutendex.GutenbergLanguageItem;
import com.driot.bookplayer.ebooks.gutendex.GutenbergLanguageStore;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.LanguageHelper;
import com.driot.bookplayer.helpers.LoadingProgressHelper;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.librivox.LanguageMapper;
import com.driot.bookplayer.nav.BaseBottomNavActivity;
import com.driot.bookplayer.utils.LiveCensorshipManager;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import dagger.hilt.android.AndroidEntryPoint;
import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@AndroidEntryPoint
public class EbookResultsActivity extends BaseBottomNavActivity {

    RecyclerView recyclerView;
    ProgressBar progressBar;
    TextView tvProgressMessage;
    TextView tvEmptyMessage;

    private EbookResultRVAdapter adapter;

    private final LoadingProgressHelper progressHelper = new LoadingProgressHelper();

    private String nextPageUrl;
    private boolean isLoadingMore = false;
    private volatile boolean gutendexConnected = false;
    private int totalCount = 0;
    private String query;
    private String lang;
    private String topic; // bookshelf/topic filter

    private static final String STATE_ITEMS = "state_items";
    private static final String STATE_NEXT_PAGE_URL = "state_next_page_url";
    private static final String STATE_TOTAL_COUNT = "state_total_count";
    private static final String STATE_QUERY = "state_query";
    private static final String STATE_LANG = "state_lang";
    private static final String STATE_TOPIC = "state_topic";

    @Override
    protected int getNavId() {
        return R.id.nav_add;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_ebook_results;
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
        tvProgressMessage = findViewById(R.id.tvProgressMessage);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);

        int span = getResources().getInteger(R.integer.classic_grid_span);
        GridLayoutManager glm = new GridLayoutManager(this, span);
        recyclerView.setLayoutManager(glm);
        recyclerView.addItemDecoration(
                new ViewHelper.SpacesItemDecoration(ViewHelper.dp(this, Var.GRID_LAYOUT_SPACER)));

        adapter = new EbookResultRVAdapter(item -> {
            myLogI("User clicks ebook item id=[" + item.gutendexId + "] - title=[" + item.title + "]\nurl=["
                    + item.epubUrl + "]");

            Intent intent = new Intent(EbookResultsActivity.this, EbookDetailActivity.class);
            intent.putExtra("gutendex_id", item.gutendexId);
            intent.putExtra("title", item.title);
            intent.putExtra("authors", item.authors);
            intent.putExtra("language", item.language);
            intent.putExtra("downloads", item.downloadCount);
            intent.putExtra("cover_url", item.coverUrl);
            intent.putExtra("epub_url", item.epubUrl);
            startActivity(intent);
        });

        recyclerView.setAdapter(adapter);

        // Add scroll listener for infinite scrolling
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null)
                    return;

                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                // Check if we've scrolled near the bottom (within last 5 items)
                if (!isLoadingMore && nextPageUrl != null && !nextPageUrl.isEmpty()) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5) {
                        loadNextPage();
                    }
                }
            }
        });

        // Get query, lang, and topic from Intent (or saved state if available)
        if (savedInstanceState != null) {
            query = savedInstanceState.getString(STATE_QUERY);
            lang = savedInstanceState.getString(STATE_LANG);
            topic = savedInstanceState.getString(STATE_TOPIC);
        }
        if (query == null)
            query = getIntent().getStringExtra("query");
        if (lang == null)
            lang = getIntent().getStringExtra("lang");
        if (topic == null)
            topic = getIntent().getStringExtra("topic");

        if (lang == null || lang.isEmpty()) {
            myLogE("EbookResultsActivity: missing/empty lang extra");
            finish();
            return;
        }
        if (query == null)
            query = "";
        if (topic == null)
            topic = "";

        // Header text, reuse your strings
        String searchLine = getString(R.string.Search_2pt)
                + (query.isEmpty() ? (topic.isEmpty() ? getString(R.string.most_downloaded) : topic) : query);

        // Get language name from Gutenberg languages (like LibriVox does)
        String langLine = getLanguageDisplayName(lang);
        adapter.setHeader(searchLine, langLine);
        adapter.setHeaderCount(getString(R.string.Results_2pt) + "...");

        myLogD("EbookResultsActivity - query=[" + query + "], lang=[" + lang + "], topic=[" + topic + "]");

        // Restore state if available (e.g., after screen rotation)
        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_ITEMS)) {
            ArrayList<EbookItem> savedItems = savedInstanceState.getParcelableArrayList(STATE_ITEMS);
            if (savedItems != null && !savedItems.isEmpty()) {
                nextPageUrl = savedInstanceState.getString(STATE_NEXT_PAGE_URL);
                totalCount = savedInstanceState.getInt(STATE_TOTAL_COUNT, 0);
                topic = savedInstanceState.getString(STATE_TOPIC);

                adapter.setItems(savedItems);
                updateCountDisplay(savedItems.size());
                recyclerView.setVisibility(View.VISIBLE);
                tvEmptyMessage.setVisibility(View.GONE);

                myLogD("EbookResultsActivity - Restored state with " + savedItems.size() + " items");
                return; // Don't make API call
            }
        }

        // No saved state, make API call
        callGutendex(query, lang, topic);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        progressHelper.stop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // Save current state to prevent re-querying on rotation
        List<EbookItem> items = adapter.getItems();

        if (items != null && !items.isEmpty()) {
            outState.putParcelableArrayList(STATE_ITEMS, new ArrayList<>(items));
            outState.putString(STATE_NEXT_PAGE_URL, nextPageUrl);
            outState.putInt(STATE_TOTAL_COUNT, totalCount);
            outState.putString(STATE_QUERY, query);
            outState.putString(STATE_LANG, lang);
            outState.putString(STATE_TOPIC, topic);
            myLogD("EbookResultsActivity - Saved state with " + items.size() + " items");
        }
    }

    private void callGutendex(String query, String lang, String topic) {
        // Reset pagination state
        nextPageUrl = null;
        isLoadingMore = false;
        gutendexConnected = false;
        totalCount = 0;
        adapter.setLoading(false);

        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        tvEmptyMessage.setVisibility(View.GONE);

        progressHelper.start(tvProgressMessage, new LoadingProgressHelper.MessageProvider() {
            @NonNull
            @Override
            public String getInitialMessage() {
                return getString(R.string.gutenberg_contacting);
            }

            @NonNull
            @Override
            public String getTickMessage(long elapsedSec) {
                if (gutendexConnected) {
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

        performInitialSearch();
    }

    // Ticker logic removed and replaced by progressHelper

    /**
     * Gutendex can be slow (e.g. "most downloaded"); use longer timeouts and one
     * retry on failure.
     */

    private OkHttpClient buildGutendexClient() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(this::myLog);
        logging.setLevel(Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL);
        return new OkHttpClient.Builder()
                .connectTimeout(Var.GUTENDEX_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(Var.GUTENDEX_READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .eventListener(new EventListener() {
                    @Override
                    public void connectionAcquired(@NonNull okhttp3.Call call, @NonNull okhttp3.Connection connection) {
                        super.connectionAcquired(call, connection);
                        gutendexConnected = true;
                        myLogD("Gutendex: Connection acquired.");
                    }
                })
                .build();
    }

    private void performInitialSearch() {
        OkHttpClient client = buildGutendexClient();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Option.getGutenbergBaseUrl())
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        GutendexApiService api = retrofit.create(GutendexApiService.class);
        String searchParam = query.isEmpty() ? null : query;
        String topicParam = topic.isEmpty() ? null : topic;

        Call<GutendexResponse> call = api.searchBooks(
                searchParam,
                lang,
                topicParam,
                "application/epub+zip",
                null);

        call.enqueue(new Callback<GutendexResponse>() {
            @Override
            public void onResponse(Call<GutendexResponse> call, Response<GutendexResponse> response) {
                progressBar.setVisibility(View.GONE);
                progressHelper.stop();

                if (!response.isSuccessful() || response.body() == null) {
                    myLogEE(null, "Gutendex invalid response, HTTP=" + response.code());
                    tvEmptyMessage.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                    myToastE(getString(R.string.an_error_occurred));
                    return;
                }

                GutendexResponse resp = response.body();
                List<GutendexBook> books = resp.results;

                if (books == null || books.isEmpty()) {
                    String errMsg;
                    if (!topic.isEmpty() && query.isEmpty()) {
                        errMsg = getString(R.string.no_ebooks_found_bookshelf, topic,
                                LanguageMapper.getNameFromTwoLetters(lang));
                    } else {
                        errMsg = getString(R.string.no_ebooks_found_search, query,
                                LanguageMapper.getNameFromTwoLetters(lang));
                    }
                    myLogW(errMsg);
                    tvEmptyMessage.setText(errMsg);
                    tvEmptyMessage.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                    adapter.setHeaderCount(getString(R.string.Results_2pt) + " 0");
                    return;
                }

                Set<String> censoredEbooks = LiveCensorshipManager.getCensoredEbooks(getApplicationContext());

                List<EbookItem> mapped = new ArrayList<>();
                for (GutendexBook b : books) {
                    if (LiveCensorshipManager.isCensored(b.title, censoredEbooks)) {
                        continue;
                    }

                    String epubUrl = GutendexMapper.findBestEpubUrl(b);
                    if (epubUrl == null || epubUrl.isEmpty())
                        continue;
                    String coverUrl = GutendexMapper.findCoverUrl(b);
                    EbookItem item = new EbookItem();
                    item.gutendexId = b.id;
                    item.title = b.title;
                    item.authors = GutendexMapper.buildAuthorLine(b);
                    item.language = (b.languages != null && !b.languages.isEmpty()) ? b.languages.get(0) : "";
                    item.downloadCount = b.download_count;
                    item.coverUrl = coverUrl;
                    item.epubUrl = epubUrl;
                    item.isImported = false;
                    mapped.add(item);
                }

                if (mapped.isEmpty()) {
                    myLogW("Gutendex: all results filtered out (no EPUB).");
                    String errMsg = !topic.isEmpty() && query.isEmpty()
                            ? getString(R.string.no_ebooks_found_bookshelf, topic,
                                    LanguageMapper.getNameFromTwoLetters(lang))
                            : getString(R.string.no_ebooks_found_search, query,
                                    LanguageMapper.getNameFromTwoLetters(lang));
                    tvEmptyMessage.setText(errMsg);
                    tvEmptyMessage.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                    adapter.setHeaderCount(getString(R.string.Results_2pt) + " 0");
                    return;
                }

                myLog("Gutendex: " + mapped.size() + " ebooks with EPUB found (total=" + resp.count + ")");
                
                // Update language book count in store (only if global search for this lang)
                if (lang != null && !lang.isEmpty() && resp.count >= 0 
                        && (query == null || query.isEmpty()) && (topic == null || topic.isEmpty())) {
                    new GutenbergLanguageStore(getApplicationContext())
                            .updateLanguageCompletedCount(lang, resp.count);
                }

                nextPageUrl = resp.next;
                totalCount = resp.count;
                adapter.setItems(mapped);
                updateCountDisplay(mapped.size());
                recyclerView.setVisibility(View.VISIBLE);
                tvEmptyMessage.setVisibility(View.GONE);
            }

            @Override
            public void onFailure(Call<GutendexResponse> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                progressHelper.stop();
                myToastEE(t, getString(R.string.an_error_occurred));
                String errMsg = getString(R.string.an_error_occurred) + "\n" + t.getMessage();
                tvEmptyMessage.setText(errMsg);
                tvEmptyMessage.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            }
        });
    }

    private void loadNextPage() {
        if (isLoadingMore || nextPageUrl == null || nextPageUrl.isEmpty()) {
            return;
        }

        isLoadingMore = true;
        adapter.setLoading(true);

        OkHttpClient client = buildGutendexClient();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Option.getGutenbergBaseUrl())
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        GutendexApiService api = retrofit.create(GutendexApiService.class);

        Call<GutendexResponse> call = api.getPage(nextPageUrl);

        call.enqueue(new Callback<GutendexResponse>() {
            @Override
            public void onResponse(Call<GutendexResponse> call, Response<GutendexResponse> response) {
                isLoadingMore = false;
                adapter.setLoading(false);

                if (!response.isSuccessful() || response.body() == null) {
                    myLogEE(null, "Gutendex invalid response for next page, HTTP=" + response.code());
                    myToastE(getString(R.string.an_error_occurred));
                    return;
                }

                GutendexResponse resp = response.body();
                List<GutendexBook> books = resp.results;

                if (books == null || books.isEmpty()) {
                    // No more books
                    nextPageUrl = null;
                    return;
                }

                Set<String> censoredEbooks = LiveCensorshipManager.getCensoredEbooks(getApplicationContext());

                List<EbookItem> mapped = new ArrayList<>();
                for (GutendexBook b : books) {
                    if (LiveCensorshipManager.isCensored(b.title, censoredEbooks)) {
                        continue;
                    }

                    String epubUrl = GutendexMapper.findBestEpubUrl(b);
                    if (epubUrl == null || epubUrl.isEmpty()) {
                        continue; // skip entries without EPUB
                    }
                    String coverUrl = GutendexMapper.findCoverUrl(b);

                    EbookItem item = new EbookItem();
                    item.gutendexId = b.id;
                    item.title = b.title;
                    item.authors = GutendexMapper.buildAuthorLine(b);
                    item.language = (b.languages != null && !b.languages.isEmpty())
                            ? b.languages.get(0)
                            : "";
                    item.downloadCount = b.download_count;
                    item.coverUrl = coverUrl;
                    item.epubUrl = epubUrl;
                    item.isImported = false; // for now

                    mapped.add(item);
                }

                if (!mapped.isEmpty()) {
                    // Update pagination info
                    nextPageUrl = resp.next;
                    totalCount = resp.count;

                    // Append new items
                    adapter.addItems(mapped);
                    updateCountDisplay(adapter.getItemCountExcludingHeader());
                } else {
                    // All filtered out, but there might be more pages
                    nextPageUrl = resp.next;
                    if (nextPageUrl != null && !nextPageUrl.isEmpty()) {
                        // Try loading next page immediately if current page had no valid EPUBs
                        loadNextPage();
                    } else {
                        nextPageUrl = null;
                    }
                }
            }

            @Override
            public void onFailure(Call<GutendexResponse> call, Throwable t) {
                isLoadingMore = false;
                adapter.setLoading(false);
                myToastEE(t, getString(R.string.an_error_occurred));
            }
        });
    }

    private void updateCountDisplay(int loadedCount) {
        String countText;
        if (totalCount > 0 && loadedCount < totalCount) {
            countText = getString(R.string.Results_2pt)
                    + formatCount(loadedCount) + " / " + formatCount(totalCount);
        } else {
            countText = getString(R.string.Results_2pt) + formatCount(loadedCount);
        }
        adapter.setHeaderCount(countText);
    }

    /** Locale-aware number formatting (e.g. 60399 → "60,399" or "60 399"). */
    private static String formatCount(long n) {
        return NumberFormat.getNumberInstance(Locale.getDefault()).format(n);
    }

    /**
     * Get the display name for a language code, formatted like LibriVox:
     * nativeName (name) if they differ, or just nativeName if they're the same.
     */
    private String getLanguageDisplayName(String langCode) {
        if (langCode == null || langCode.isEmpty()) {
            return getString(R.string.Language_2pt) + " " + langCode;
        }

        // Load Gutenberg languages and find matching one
        GutenbergLanguageStore store = new GutenbergLanguageStore(this);
        List<GutenbergLanguageItem> languages = store.loadLanguages(R.raw.gutenberg_languages);

        for (GutenbergLanguageItem langItem : languages) {
            if (langCode.equalsIgnoreCase(langItem.code2)) {
                // Format like LibriVox: nativeName (name) if different, or just nativeName
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

        // Fallback: use LanguageMapper to get name from code
        String langName = LanguageMapper.getNameFromTwoLetters(langCode);
        if (langName != null && !langName.equals(langCode)) {
            return getString(R.string.Language_2pt) + " " + langName;
        }

        // Last resort: just show the code
        return getString(R.string.Language_2pt) + " " + langCode;
    }
}
