package com.driot.bookplayer.activities;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.R;
import com.driot.bookplayer.ebooks.EbookItem;
import com.driot.bookplayer.ebooks.gutendex.GutendexApiService;
import com.driot.bookplayer.ebooks.gutendex.GutendexBook;
import com.driot.bookplayer.ebooks.gutendex.GutendexMapper;
import com.driot.bookplayer.ebooks.gutendex.GutendexResponse;
import com.driot.bookplayer.ebooks.gutendex.GutenbergLanguageStore;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.librivox.LanguageMapper;
import com.driot.bookplayer.utils.LiveCensorshipManager;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class EbookResultsViewModel extends LoggingAndroidViewModel {

    private final MutableLiveData<List<EbookItem>> items = new MutableLiveData<>();
    private final MutableLiveData<String> headerCount = new MutableLiveData<>();
    private final MutableLiveData<Boolean> initialLoading = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loadingMore = new MutableLiveData<>();
    private final MutableLiveData<String> emptyMessage = new MutableLiveData<>();

    private String nextPageUrl;
    private int totalCount = 0;
    private boolean isLoadingMoreFlag = false;
    volatile boolean gutendexConnected = false;

    private String query;
    private String lang;
    private String topic;
    private boolean initialized = false;

    private Call<GutendexResponse> pendingCall;

    public EbookResultsViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<EbookItem>> getItems() { return items; }
    public LiveData<String> getHeaderCount() { return headerCount; }
    public LiveData<Boolean> getInitialLoading() { return initialLoading; }
    public LiveData<Boolean> getLoadingMore() { return loadingMore; }
    public LiveData<String> getEmptyMessage() { return emptyMessage; }

    public boolean isGutendexConnected() { return gutendexConnected; }

    public boolean canLoadMore() {
        return !isLoadingMoreFlag && nextPageUrl != null && !nextPageUrl.isEmpty();
    }

    public void fetchIfNeeded(@NonNull String query, @NonNull String lang, @NonNull String topic) {
        if (initialized) return;
        initialized = true;
        this.query = query;
        this.lang = lang;
        this.topic = topic;
        performInitialSearch();
    }

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
                    public void connectionAcquired(@NonNull okhttp3.Call call,
                            @NonNull okhttp3.Connection connection) {
                        super.connectionAcquired(call, connection);
                        gutendexConnected = true;
                        myLogD("Gutendex: Connection acquired.");
                    }
                })
                .build();
    }

    private void performInitialSearch() {
        nextPageUrl = null;
        isLoadingMoreFlag = false;
        gutendexConnected = false;
        totalCount = 0;

        initialLoading.postValue(true);
        emptyMessage.postValue(null);

        OkHttpClient client = buildGutendexClient();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Option.getGutenbergBaseUrl())
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        GutendexApiService api = retrofit.create(GutendexApiService.class);
        String searchParam = query.isEmpty() ? null : query;
        String topicParam = topic.isEmpty() ? null : topic;

        pendingCall = api.searchBooks(searchParam, lang, topicParam, "application/epub+zip", null);
        pendingCall.enqueue(new Callback<GutendexResponse>() {
            @Override
            public void onResponse(Call<GutendexResponse> call, Response<GutendexResponse> response) {
                initialLoading.postValue(false);

                if (!response.isSuccessful() || response.body() == null) {
                    myLogEE(null, "Gutendex invalid response, HTTP=" + response.code());
                    myToastE(getApplication().getString(R.string.an_error_occurred));
                    emptyMessage.postValue(getApplication().getString(R.string.an_error_occurred));
                    initialized = false;
                    return;
                }

                GutendexResponse resp = response.body();
                List<GutendexBook> books = resp.results;

                if (books == null || books.isEmpty()) {
                    myLogW("Gutendex: no books returned.");
                    headerCount.postValue(getApplication().getString(R.string.Results_2pt) + " 0");
                    emptyMessage.postValue(buildEmptyMsg());
                    return;
                }

                List<EbookItem> mapped = mapBooks(books);

                if (mapped.isEmpty()) {
                    myLogW("Gutendex: all results filtered out (no EPUB).");
                    headerCount.postValue(getApplication().getString(R.string.Results_2pt) + " 0");
                    emptyMessage.postValue(buildEmptyMsg());
                    return;
                }

                myLogD("Gutendex: " + mapped.size() + " ebooks with EPUB found (total=" + resp.count + ")");

                // Update language book count in store (only for global language search)
                if (lang != null && !lang.isEmpty() && resp.count >= 0
                        && (query == null || query.isEmpty()) && (topic == null || topic.isEmpty())) {
                    new GutenbergLanguageStore(getApplication())
                            .updateLanguageCompletedCount(lang, resp.count);
                }

                nextPageUrl = rewriteNextUrl(resp.next);
                totalCount = resp.count;
                items.postValue(mapped);
                headerCount.postValue(buildCountText(mapped.size()));
            }

            @Override
            public void onFailure(Call<GutendexResponse> call, Throwable t) {
                if (!call.isCanceled()) {
                    initialLoading.postValue(false);
                    myToastEE(t, getApplication().getString(R.string.an_error_occurred));
                    emptyMessage.postValue(
                            getApplication().getString(R.string.an_error_occurred) + "\n" + t.getMessage());
                    initialized = false;
                }
            }
        });
    }

    public void loadNextPage() {
        if (!canLoadMore()) return;

        isLoadingMoreFlag = true;
        loadingMore.postValue(true);

        OkHttpClient client = buildGutendexClient();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Option.getGutenbergBaseUrl())
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        GutendexApiService api = retrofit.create(GutendexApiService.class);
        pendingCall = api.getPage(nextPageUrl);
        pendingCall.enqueue(new Callback<GutendexResponse>() {
            @Override
            public void onResponse(Call<GutendexResponse> call, Response<GutendexResponse> response) {
                isLoadingMoreFlag = false;
                loadingMore.postValue(false);

                if (!response.isSuccessful() || response.body() == null) {
                    myLogEE(null, "Gutendex invalid response for next page, HTTP=" + response.code());
                    myToastE(getApplication().getString(R.string.an_error_occurred));
                    return;
                }

                GutendexResponse resp = response.body();
                List<GutendexBook> books = resp.results;

                if (books == null || books.isEmpty()) {
                    nextPageUrl = null;
                    return;
                }

                List<EbookItem> mapped = mapBooks(books);

                if (!mapped.isEmpty()) {
                    nextPageUrl = rewriteNextUrl(resp.next);
                    totalCount = resp.count;

                    List<EbookItem> current = items.getValue();
                    List<EbookItem> newList = new ArrayList<>(current != null ? current : new ArrayList<>());
                    newList.addAll(mapped);
                    items.postValue(newList);
                    headerCount.postValue(buildCountText(newList.size()));
                } else {
                    // All filtered, but there might be more pages
                    nextPageUrl = rewriteNextUrl(resp.next);
                    if (canLoadMore()) {
                        loadNextPage();
                    } else {
                        nextPageUrl = null;
                    }
                }
            }

            @Override
            public void onFailure(Call<GutendexResponse> call, Throwable t) {
                if (!call.isCanceled()) {
                    isLoadingMoreFlag = false;
                    loadingMore.postValue(false);
                    myToastEE(t, getApplication().getString(R.string.an_error_occurred));
                }
            }
        });
    }

    private List<EbookItem> mapBooks(List<GutendexBook> books) {
        Set<String> censoredEbooks = LiveCensorshipManager.getCensoredEbooks(getApplication());
        List<EbookItem> mapped = new ArrayList<>();
        for (GutendexBook b : books) {
            if (LiveCensorshipManager.isCensored(b.title, censoredEbooks)) continue;
            String epubUrl = GutendexMapper.findBestEpubUrl(b);
            if (epubUrl == null || epubUrl.isEmpty()) continue;
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
        return mapped;
    }

    private String buildEmptyMsg() {
        if (!topic.isEmpty() && query.isEmpty()) {
            return getApplication().getString(R.string.no_ebooks_found_bookshelf,
                    topic, LanguageMapper.getNameFromTwoLetters(lang));
        } else {
            return getApplication().getString(R.string.no_ebooks_found_search,
                    query, LanguageMapper.getNameFromTwoLetters(lang));
        }
    }

    private String rewriteNextUrl(String next) {
        if (next == null) return null;
        String base = Option.getGutenbergBaseUrl();
        return next.replaceFirst("^https://gutendex\\.com/", base);
    }

    private String buildCountText(int loadedCount) {
        String resultsLabel = getApplication().getString(R.string.Results_2pt);
        if (totalCount > 0 && loadedCount < totalCount) {
            return resultsLabel + formatCount(loadedCount) + " / " + formatCount(totalCount);
        }
        return resultsLabel + formatCount(loadedCount);
    }

    private static String formatCount(long n) {
        return NumberFormat.getNumberInstance(Locale.getDefault()).format(n);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (pendingCall != null) pendingCall.cancel();
    }
}
