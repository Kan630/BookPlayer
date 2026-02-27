package com.driot.bookplayer.activities;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.BookSource;
import com.driot.bookplayer.db.BookSourceDao;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.librivox.ArchiveApiResponse;
import com.driot.bookplayer.librivox.ArchiveItem;
import com.driot.bookplayer.librivox.LibrivoxRepository;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import com.driot.bookplayer.utils.LiveCensorshipManager;
import java.util.Set;
import java.util.Iterator;
import retrofit2.Response;

public class LibrivoxResultsViewModel extends LoggingAndroidViewModel {

    private final MutableLiveData<List<ArchiveItem>> results = new MutableLiveData<>();
    private final MutableLiveData<Boolean> shouldFinish = new MutableLiveData<>(false);
    private final MutableLiveData<HeaderStatusData> headerStatus = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    private LiveData<List<ArchiveItem>> favoritesLive;
    private LiveData<List<BookSource>> favoriteBookSourcesLive;
    private LibrivoxRepository repository;

    private String lastQuery = null;
    private String lastLang = null;
    private boolean fetchStarted = false;

    // Pagination for MODE_TRENDING and MODE_LAST_ADDED (archive.org)
    private String lastPagedMode = null; // "MODE_TRENDING" or "MODE_LAST_ADDED"
    private String lastLangCode3 = null;
    private int currentPage = 1;
    private boolean hasMore = false;
    private boolean isLoadingMore = false;
    private final MutableLiveData<Boolean> isLoadingMoreLive = new MutableLiveData<>(false);
    /** Total from first page response (numFound). -1 if unknown. */
    private long pagedTotalCount = -1;

    // Data class for header status
    public static class HeaderStatusData {
        public final int count;
        /** Total matching items from API (e.g. archive.org numFound). -1 if unknown. */
        public final long totalCount;
        public final boolean isFinal;
        public final boolean isMaxReached;
        public final boolean isLoading;
        public final String apiSource; // e.g. "librivox.org"

        public HeaderStatusData(int count, long totalCount, boolean isFinal, boolean isMaxReached,
                boolean isLoading, String apiSource) {
            this.count = count;
            this.totalCount = totalCount;
            this.isFinal = isFinal;
            this.isMaxReached = isMaxReached;
            this.isLoading = isLoading;
            this.apiSource = apiSource;
        }
    }

    public LibrivoxResultsViewModel(@NonNull Application application) {
        super(application);
        repository = new LibrivoxRepository(
                application.getApplicationContext(),
                Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL);
    }

    // Getters
    public LiveData<List<ArchiveItem>> getResults() {
        return results;
    }

    public LiveData<Boolean> getShouldFinish() {
        return shouldFinish;
    }

    public LiveData<HeaderStatusData> getHeaderStatus() {
        return headerStatus;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<Boolean> getIsLoadingMore() {
        return isLoadingMoreLive;
    }

    public boolean hasMore() {
        return hasMore;
    }

    public boolean isLoadingMore() {
        return isLoadingMore;
    }

    public void requestFinish() {
        shouldFinish.setValue(true);
    }

    public String getLastQuery() {
        return lastQuery;
    }

    public void setLastQuery(String lastQuery) {
        this.lastQuery = lastQuery;
    }

    public String getLastLang() {
        return lastLang;
    }

    public void setLastLang(String lastLang) {
        this.lastLang = lastLang;
    }

    public LiveData<List<ArchiveItem>> getFavoriteLibrivoxsLive() {
        if (favoritesLive == null) {
            AppDatabase db = AppDatabase.getDatabase(getApplication());
            favoritesLive = db.bookSourceDao()
                    .getFavoriteLibrivoxItems(Var.REPO_TYPE_AUDIOBOOK, Var.REPO_NAME_LIBRIVOX);
        }
        return favoritesLive;
    }

    public LiveData<List<BookSource>> getFavoriteBookSourcesLive() {
        if (favoriteBookSourcesLive == null) {
            AppDatabase db = AppDatabase.getDatabase(getApplication());
            favoriteBookSourcesLive = db.bookSourceDao()
                    .getFavoriteLibrivoxBookSourcesLive(Var.REPO_TYPE_AUDIOBOOK, Var.REPO_NAME_LIBRIVOX);
        }
        return favoriteBookSourcesLive;
    }

    // ============================================================
    // PUBLIC METHODS TO TRIGGER SEARCHES
    // ============================================================

    public void searchByQuery(String query, String langCode3) {
        isLoading.setValue(true);
        fetchStarted = false;

        repository.searchByQueryAndLang(
                query,
                langCode3,
                Option.getLibrivoxApiNbResults(),
                createArchiveCallback("search", query));
    }

    public void searchTrending(String langCode3) {
        isLoading.setValue(true);
        fetchStarted = false;
        lastPagedMode = "MODE_TRENDING";
        lastLangCode3 = langCode3;
        currentPage = 1;
        hasMore = false;
        isLoadingMore = false;
        isLoadingMoreLive.setValue(false);

        repository.mostDownloadedByLang(
                langCode3,
                Option.getLibrivoxApiNbResults(),
                1,
                createArchiveCallbackFirstPage("trending", null));
    }

    public void searchLastAdded(String langCode3) {
        isLoading.setValue(true);
        fetchStarted = false;
        lastPagedMode = "MODE_LAST_ADDED";
        lastLangCode3 = langCode3;
        currentPage = 1;
        hasMore = false;
        isLoadingMore = false;
        isLoadingMoreLive.setValue(false);

        repository.mostRecentlyAddedByLang(
                langCode3,
                Option.getLibrivoxApiNbResults(),
                1,
                createArchiveCallbackFirstPage("last added", null));
    }

    /**
     * Load next page for MODE_TRENDING or MODE_LAST_ADDED. No-op if not paged mode
     * or no more pages.
     */
    public void loadNextPage() {
        if (lastPagedMode == null || lastLangCode3 == null || isLoadingMore || !hasMore) {
            return;
        }
        isLoadingMore = true;
        isLoadingMoreLive.setValue(true);
        int pageSize = Option.getLibrivoxApiNbResults();
        int pageToFetch = currentPage;

        Callback<ArchiveApiResponse> cb = new Callback<ArchiveApiResponse>() {
            @Override
            public void onResponse(Call<ArchiveApiResponse> call, Response<ArchiveApiResponse> response) {
                isLoadingMore = false;
                isLoadingMoreLive.postValue(false);
                if (response.body() == null || response.body().response == null) {
                    hasMore = false;
                    return;
                }
                List<ArchiveItem> newItems = response.body().response.docs;
                if (newItems == null)
                    newItems = new ArrayList<>();
                for (ArchiveItem it : newItems) {
                    if (it.imageRemote == null && it.identifier != null) {
                        it.imageRemote = "https://archive.org/services/img/" + it.identifier;
                    }
                }
                if (newItems.isEmpty()) {
                    hasMore = false;
                    return;
                }

                Set<String> censored = LiveCensorshipManager.getCensoredLibrivox(getApplication());
                Iterator<ArchiveItem> iterator = newItems.iterator();
                while (iterator.hasNext()) {
                    ArchiveItem item = iterator.next();
                    if (LiveCensorshipManager.isCensored(item.title, censored)) {
                        iterator.remove();
                    }
                }

                List<ArchiveItem> current = results.getValue();
                if (current == null)
                    current = new ArrayList<>();
                List<ArchiveItem> merged = new ArrayList<>(current);
                merged.addAll(newItems);
                currentPage++;
                hasMore = newItems.size() >= pageSize;
                enrichWithLocalState(merged);
                updateSimpleSearchHeader(merged, pagedTotalCount);
            }

            @Override
            public void onFailure(Call<ArchiveApiResponse> call, Throwable t) {
                isLoadingMore = false;
                isLoadingMoreLive.postValue(false);
                myLogEE(t, "Librivox loadNextPage failed");
            }
        };

        if ("MODE_TRENDING".equals(lastPagedMode)) {
            repository.mostDownloadedByLang(lastLangCode3, pageSize, pageToFetch, cb);
        } else {
            repository.mostRecentlyAddedByLang(lastLangCode3, pageSize, pageToFetch, cb);
        }
    }

    public void searchByAuthor(String author, String langCode3) {
        isLoading.setValue(true);
        fetchStarted = false;

        repository.mostDownloadedByAuthor(
                langCode3,
                author,
                Option.getLibrivoxApiNbResults(),
                createArchiveCallback("author", author));
    }

    public void searchByGenre(String genre, String langCode3) {
        isLoading.setValue(true);
        fetchStarted = false;
        updateHeaderStatus(null, false, "librivox.org");

        repository.searchArchiveItemsByGenreAndLangLibrivox(
                langCode3,
                false,
                genre,
                Option.getLibrivoxApiNbResults(),
                new LibrivoxRepository.PagedResultCallback<ArchiveItem>() {
                    @Override
                    public void onPageReceived(List<ArchiveItem> items, boolean isFinalPage) {
                        isLoading.postValue(false);

                        int nbCollected = items != null ? items.size() : 0;

                        if ((items == null || items.isEmpty()) && nbCollected == 0) {
                            errorMessage.postValue("no_results_genre:" + genre);
                            requestFinish();
                            return;
                        }

                        if (items != null) {
                            Set<String> censored = LiveCensorshipManager.getCensoredLibrivox(getApplication());
                            Iterator<ArchiveItem> iterator = items.iterator();
                            while (iterator.hasNext()) {
                                ArchiveItem item = iterator.next();
                                if (LiveCensorshipManager.isCensored(item.title, censored)) {
                                    iterator.remove();
                                }
                            }
                        }

                        enrichWithLocalState(items);
                        updateHeaderStatus(items, isFinalPage, "librivox.org");

                        myLogD("onPageReceived [GENRE] - items=" + nbCollected
                                + " - isFinalPage=" + isFinalPage);

                        if (isFinalPage) {
                            myLogI("✅ FINAL PAGE DETECTED - " + nbCollected + " total books");
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        isLoading.postValue(false);
                        int nbCollected = (results.getValue() == null)
                                ? 0
                                : results.getValue().size();

                        myLogEE(t, "Genre search error");
                        errorMessage.postValue("error:" + t.getMessage());

                        if (nbCollected == 0) {
                            requestFinish();
                        }
                    }
                });
    }

    // ============================================================
    // PRIVATE HELPER METHODS
    // ============================================================

    /**
     * Callback for first page (search by query, or first page of trending/last
     * added).
     */
    private Callback<ArchiveApiResponse> createArchiveCallback(String searchType, String query) {
        return new Callback<ArchiveApiResponse>() {
            @Override
            public void onResponse(Call<ArchiveApiResponse> call, Response<ArchiveApiResponse> response) {
                isLoading.postValue(false);

                if (response.body() != null && response.body().response != null) {
                    List<ArchiveItem> items = response.body().response.docs;

                    if (items.isEmpty()) {
                        String msg = "no_results_" + searchType + (query != null ? ":" + query : "");
                        errorMessage.postValue(msg);
                        requestFinish();
                    } else {
                        Set<String> censored = LiveCensorshipManager.getCensoredLibrivox(getApplication());
                        Iterator<ArchiveItem> iterator = items.iterator();
                        while (iterator.hasNext()) {
                            ArchiveItem item = iterator.next();
                            if (LiveCensorshipManager.isCensored(item.title, censored)) {
                                iterator.remove();
                            }
                        }

                        long total = response.body().response.numFound >= 0
                                ? response.body().response.numFound
                                : -1;
                        for (ArchiveItem it : items) {
                            if (it.imageRemote == null && it.identifier != null) {
                                it.imageRemote = "https://archive.org/services/img/" + it.identifier;
                            }
                        }
                        enrichWithLocalState(items);
                        updateSimpleSearchHeader(items, total);
                        myLog(items.size() + " results found for " + searchType);
                    }
                } else {
                    myLogEE(null, "Invalid response body from archive.org");
                    errorMessage.postValue("error:invalid_response");
                    requestFinish();
                }
            }

            @Override
            public void onFailure(Call<ArchiveApiResponse> call, Throwable t) {
                isLoading.postValue(false);
                myLogEE(t, searchType + " search failure");
                errorMessage.postValue("error:" + t.getMessage());
                requestFinish();
            }
        };
    }

    /**
     * Callback for first page of MODE_TRENDING / MODE_LAST_ADDED (enables
     * pagination).
     */
    private Callback<ArchiveApiResponse> createArchiveCallbackFirstPage(String searchType, String query) {
        return new Callback<ArchiveApiResponse>() {
            @Override
            public void onResponse(Call<ArchiveApiResponse> call, Response<ArchiveApiResponse> response) {
                isLoading.postValue(false);

                if (response.body() != null && response.body().response != null) {
                    List<ArchiveItem> items = response.body().response.docs;

                    if (items.isEmpty()) {
                        String msg = "no_results_" + searchType + (query != null ? ":" + query : "");
                        errorMessage.postValue(msg);
                        requestFinish();
                    } else {
                        Set<String> censored = LiveCensorshipManager.getCensoredLibrivox(getApplication());
                        Iterator<ArchiveItem> iterator = items.iterator();
                        while (iterator.hasNext()) {
                            ArchiveItem item = iterator.next();
                            if (LiveCensorshipManager.isCensored(item.title, censored)) {
                                iterator.remove();
                            }
                        }

                        int pageSize = Option.getLibrivoxApiNbResults();
                        currentPage = 2;
                        hasMore = items.size() >= pageSize;
                        long total = response.body().response.numFound >= 0
                                ? response.body().response.numFound
                                : -1;
                        pagedTotalCount = total;
                        for (ArchiveItem it : items) {
                            if (it.imageRemote == null && it.identifier != null) {
                                it.imageRemote = "https://archive.org/services/img/" + it.identifier;
                            }
                        }
                        enrichWithLocalState(items);
                        updateSimpleSearchHeader(items, total);
                        myLog(items.size() + " results found for " + searchType + ", hasMore=" + hasMore + ", total="
                                + total);
                    }
                } else {
                    myLogEE(null, "Invalid response body from archive.org");
                    errorMessage.postValue("error:invalid_response");
                    requestFinish();
                }
            }

            @Override
            public void onFailure(Call<ArchiveApiResponse> call, Throwable t) {
                isLoading.postValue(false);
                myLogEE(t, searchType + " search failure");
                errorMessage.postValue("error:" + t.getMessage());
                requestFinish();
            }
        };
    }

    private void updateSimpleSearchHeader(List<ArchiveItem> items) {
        updateSimpleSearchHeader(items, -1);
    }

    private void updateSimpleSearchHeader(List<ArchiveItem> items, long totalCount) {
        int count = items == null ? 0 : items.size();
        boolean isMaxReached = count >= Option.getLibrivoxApiNbResults();
        if (totalCount < 0 && lastPagedMode != null)
            totalCount = pagedTotalCount;
        headerStatus.postValue(new HeaderStatusData(count, totalCount, true, isMaxReached, false, "archive.org"));
    }

    private void updateHeaderStatus(List<ArchiveItem> currentList, boolean isFinal, String webApi) {
        int count = currentList != null ? currentList.size() : 0;
        boolean isMaxReached = count >= Option.getLibrivoxApiNbResults();
        boolean isLoading = !fetchStarted || !isFinal;

        fetchStarted = true;
        headerStatus.postValue(new HeaderStatusData(count, -1, isFinal, isMaxReached, isLoading, webApi));
    }

    public void enrichWithLocalState(List<ArchiveItem> apiItems) {
        if (apiItems == null || apiItems.isEmpty()) {
            results.postValue(apiItems);
            return;
        }

        AppDatabase.databaseWriteExecutor.execute(() -> {
            BookSourceDao dao = AppDatabase.getDatabase(getApplication()).bookSourceDao();
            HashMap<String, BookSourceDao.RepoStateRow> map = new HashMap<>();

            // Handle SQLite variable limit (999) by batching
            final int BATCH_SIZE = 900;
            for (int i = 0; i < apiItems.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, apiItems.size());
                List<String> batchIds = new ArrayList<>();
                for (int j = i; j < end; j++) {
                    batchIds.add(apiItems.get(j).identifier);
                }

                List<BookSourceDao.RepoStateRow> rows = dao.getStateFor(
                        Var.REPO_TYPE_AUDIOBOOK,
                        Var.REPO_NAME_LIBRIVOX,
                        batchIds);
                for (BookSourceDao.RepoStateRow r : rows) {
                    map.put(r.repoId, r);
                }
            }

            // Apply state to all items
            for (ArchiveItem it : apiItems) {
                BookSourceDao.RepoStateRow st = map.get(it.identifier);
                if (st != null) {
                    it.is_favorite = st.is_favorite;
                    it.idFolder = st.idFolder;
                } else {
                    it.is_favorite = false;
                    it.idFolder = null;
                }
            }

            results.postValue(apiItems);
        });
    }

    public void toggleFavorite(ArchiveItem archiveItem) {
        if (archiveItem == null)
            return;
        boolean newFav = !archiveItem.is_favorite;

        AppDatabase.databaseWriteExecutor.execute(() -> {
            long now = System.currentTimeMillis();
            BookSourceDao dao = AppDatabase.getDatabase(getApplication()).bookSourceDao();

            int updated = dao.updateFavoriteFlag(
                    Var.REPO_TYPE_AUDIOBOOK,
                    Var.REPO_NAME_LIBRIVOX,
                    archiveItem.identifier,
                    newFav,
                    now);

            if (updated == 0 && newFav) {
                String url = "https://archive.org/details/" + archiveItem.identifier;
                BookSource bs = new BookSource(
                        archiveItem.title != null ? archiveItem.title : "",
                        url,
                        Var.REPO_TYPE_AUDIOBOOK,
                        Var.REPO_NAME_LIBRIVOX,
                        archiveItem.identifier,
                        null,
                        archiveItem.imageRemote,
                        null);
                bs.is_favorite = true;
                bs.date_add = now;
                bs.date_maj = now;
                bs.imageRemote = archiveItem.imageRemote;
                bs.source_size = archiveItem.source_size;
                AppDatabase.getDatabase(getApplication()).bookSourceDao().upsert(bs);
            }

            // Update current list for snappy UI
            List<ArchiveItem> cur = results.getValue();
            if (cur != null) {
                for (ArchiveItem it : cur) {
                    if (it.identifier.equals(archiveItem.identifier)) {
                        it.is_favorite = newFav;
                        break;
                    }
                }
                results.postValue(new ArrayList<>(cur));
            }
        });
    }
}