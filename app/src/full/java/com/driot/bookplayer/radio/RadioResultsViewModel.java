package com.driot.bookplayer.radio;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.RadioStation;
import com.driot.bookplayer.db.RadioStationDao;
import com.driot.bookplayer.utils.log.LoggingViewModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RadioResultsViewModel extends LoggingViewModel {

    private final MutableLiveData<List<ApiStation>> results = new MutableLiveData<>();
    private final MutableLiveData<Boolean> shouldFinish = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isLoadingMore = new MutableLiveData<>(false);
    private final MutableLiveData<String> headerCount = new MutableLiveData<>("");

    private final MutableLiveData<Boolean> showingHistory = new MutableLiveData<>(false);
    private boolean historyMode = false;

    private final Map<String, String> faviconCache = new HashMap<>();
    public Map<String, String> getFaviconCache() {
        return faviconCache;
    }

    public LiveData<Boolean> getShowingHistory() {
        return showingHistory;
    }

    public LiveData<Boolean> getIsLoadingMore() {
        return isLoadingMore;
    }

    private String lastQuery = "";
    private String lastLang = "";
    private String lastCountry = "";
    private String lastTag = "";
    private String lastSearchMode = "";
    // Extra language variant names to query alongside the canonical lang (MODE_LANGUAGE only).
    // Index 0 is canonical (already covered by lastLang); index 1..N are aliases.
    private List<String> lastLangVariants = new ArrayList<>();

    // Pagination state
    private int currentOffset = 0;
    private boolean hasMore = true;
    private boolean isLoading = false;

    public LiveData<List<ApiStation>> getResults() {
        return results;
    }

    public LiveData<Boolean> getShouldFinish() {
        return shouldFinish;
    }

    public void setResults(List<ApiStation> apiStations, int rawResponseSize) {
        results.postValue(apiStations);
        // Reset pagination state when setting new results (first page)
        currentOffset = rawResponseSize;
        hasMore = rawResponseSize > 0;
        isLoading = false;
        isLoadingMore.postValue(false);
    }

    public void setHeaderCount(String text) {
        headerCount.postValue(text);
    }

    public LiveData<String> getHeaderCount() {
        return headerCount;
    }

    public void appendResults(List<ApiStation> apiStations, int rawResponseSize) {
        List<ApiStation> current = results.getValue();
        if (current == null) {
            current = new ArrayList<>();
        }
        if (apiStations != null && !apiStations.isEmpty()) {
            // Deduplicate: remove apiStations already in 'current'
            Set<String> existingUuids = new HashSet<>();
            for (ApiStation s : current) {
                if (s.stationuuid != null)
                    existingUuids.add(s.stationuuid);
            }

            List<ApiStation> uniqueApiStations = new ArrayList<>();
            for (ApiStation s : apiStations) {
                if (s.stationuuid != null && !existingUuids.contains(s.stationuuid)) {
                    uniqueApiStations.add(s);
                    existingUuids.add(s.stationuuid); // avoid dupes within the new batch too
                } else {
                    myLogD("dedup removed: name=[" + s.name + "] uuid=[" + s.stationuuid + "]");
                }
            }

            if (!uniqueApiStations.isEmpty()) {
                current.addAll(uniqueApiStations);
            }
        }

        // We increment offset by the number of items the server treated as "sent"
        // even if we filtered some of them out.
        currentOffset += rawResponseSize;

        results.postValue(current);
        isLoading = false;
        isLoadingMore.postValue(false);
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }

    public void requestFinish() {
        shouldFinish.postValue(true);
    }

    public int getCurrentOffset() {
        return currentOffset;
    }

    public boolean hasMore() {
        return hasMore;
    }

    public boolean isLoading() {
        return isLoading;
    }

    public void setLoading(boolean loading) {
        isLoading = loading;
        isLoadingMore.postValue(loading);
    }

    public void setLastSearchMode(String mode) {
        lastSearchMode = mode != null ? mode : "";
    }

    public String getLastSearchMode() {
        return lastSearchMode;
    }

    public void resetPagination() {
        currentOffset = 0;
        hasMore = true;
        isLoading = false;
        isLoadingMore.postValue(false);
    }

    public String getLastQuery() {
        return lastQuery;
    }

    public String getLastLang() {
        return lastLang;
    }

    public String getLastCountry() {
        return lastCountry;
    }

    public String getLastTag() {
        return lastTag;
    }

    public void setLastParams(String query, String lang, String country, String tag) {
        lastQuery = query == null ? "" : query;
        lastLang = lang == null ? "" : lang;
        lastCountry = country == null ? "" : country;
        lastTag = tag == null ? "" : tag;
    }

    public void setLastLangVariants(List<String> variants) {
        lastLangVariants = variants != null ? variants : new ArrayList<>();
    }

    /** Returns alias names at index 1..N (index 0 = canonical, already queried separately). */
    public List<String> getLangAliasesOnly() {
        if (lastLangVariants.size() <= 1) return new ArrayList<>();
        return lastLangVariants.subList(1, lastLangVariants.size());
    }

    // ---- Favorites state exposed to UI ----

    private final MutableLiveData<Set<String>> favoriteUuids = new MutableLiveData<>(new HashSet<>());
    private final MutableLiveData<List<RadioStation>> favoriteItems = new MutableLiveData<>();
    private final MutableLiveData<Boolean> hasFavorites = new MutableLiveData<>(false);

    public LiveData<Set<String>> getFavoriteUuids() {
        return favoriteUuids;
    }

    public LiveData<List<RadioStation>> getFavoriteItems() {
        return favoriteItems;
    }

    // ---- Helpers ----

    private RadioStationDao dao(Context ctx) {
        return AppDatabase.getInstance(ctx.getApplicationContext()).radioStationDao();
    }

    private void copyFromStationToRadioStation(RadioStation r, ApiStation s) {
        r.stationuuid = s.stationuuid;
        r.name = s.name;
        r.url = s.url;
        r.url_resolved = s.url_resolved;
        r.codec = s.codec;
        r.bitrate = s.bitrate;
        r.hls = s.hls;
        r.favicon = s.favicon;
        r.country = s.country;
        r.countrycode = s.countrycode;
        r.language = s.language;
        r.tags = s.tags;
        r.clickcount = s.clickcount;
        r.lastcheckok = s.lastcheckok;

        if (s.favicon != null && s.favicon.startsWith("http")) {
            r.imageOriginalUrl = s.favicon;
        }
        // do not touch date_added / date_last_played / display_order here
    }

    public void initMode(Context ctx) {
        Context appCtx = ctx.getApplicationContext();
        AppDatabase.databaseReadExecutor.execute(() -> {
            RadioStationDao d = dao(appCtx);

            int favCount = d.countFavorites();
            int histCount = d.countHistory();
            myLogD("init - favorites : " + favCount + " - history : " + histCount);

            if (favCount > 0) {
                loadFavorites(appCtx);
            } else if (histCount > 0) {
                loadHistory(appCtx);
            } else {
                loadFavorites(appCtx);
            }
        });
    }

    // ---- Load favorites from Room ----

    public void loadFavorites(Context ctx) {
        historyMode = false; // <--- update field
        showingHistory.postValue(false); // notify UI
        refreshFromDb(ctx, false);
    }

    public void loadHistory(Context ctx) {
        historyMode = true; // <--- update field
        showingHistory.postValue(true); // notify UI
        refreshFromDb(ctx, true);
    }

    // toggle favorite from a ApiStation (used from search results)
    public void toggleFavorite(Context ctx, ApiStation s) {
        Context appCtx = ctx.getApplicationContext();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            RadioStationDao dao = dao(appCtx);
            long now = System.currentTimeMillis();

            RadioStation existing = dao.findByUuid(s.stationuuid);
            if (existing != null) {
                if (existing.isFavorite) {
                    // remove from favorites, keep row for history
                    existing.isFavorite = false;
                    existing.date_maj = now;
                    dao.update(existing);
                } else {
                    // re-favorite an existing station
                    copyFromStationToRadioStation(existing, s);
                    existing.isFavorite = true;
                    existing.date_maj = now;
                    dao.update(existing);
                    // resolve favicon if missing
                    RadioFaviconHelper.resolveAndPersistFavicon(appCtx, existing);
                }
            } else {
                // brand new favorite
                RadioStation r = new RadioStation();
                r.stationuuid = s.stationuuid;
                copyFromStationToRadioStation(r, s);
                r.isFavorite = true;
                r.display_order = 0;
                r.date_added = now;
                r.date_maj = now;
                r.date_last_played = null;
                r.id = dao.insert(r);          // <-- capture id so the object is complete
                // resolve favicon if missing
                RadioFaviconHelper.resolveAndPersistFavicon(appCtx, r);
            }

            refreshFromDb(appCtx, historyMode);
        });
    }

    public void reorderFavorites(Context ctx, List<RadioStation> newOrder) {
        Context appCtx = ctx.getApplicationContext();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            RadioStationDao dao = dao(appCtx);
            int idx = 0;
            for (RadioStation it : newOrder) {
                dao.updateDisplayOrder(it.stationuuid, idx++);
            }
            refreshFromDb(appCtx, historyMode);
        });
    }

    public void removeFavoriteUuid(Context ctx, String uuid) {
        Context appCtx = ctx.getApplicationContext();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            RadioStationDao dao = dao(appCtx);
            RadioStation existing = dao.findByUuid(uuid);
            if (existing != null && existing.isFavorite) {
                existing.isFavorite = false;
                existing.date_maj = System.currentTimeMillis();
                dao.update(existing);
            }
            refreshFromDb(appCtx, historyMode);
        });
    }

    private void refreshFromDb(Context ctx, boolean history) {
        Context appCtx = ctx.getApplicationContext();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            RadioStationDao dao = dao(appCtx);

            myLogD("refresh from DB, history mode=" + history);

            List<RadioStation> rows = history
                    ? dao.getAlreadyPlayed()
                    : dao.getFavorites();

            Set<String> uuids = new HashSet<>();
            List<RadioStation> items = new ArrayList<>(rows.size());

            for (RadioStation r : rows) {
                uuids.add(r.stationuuid);
                items.add(r);
            }

            favoriteUuids.postValue(uuids);
            favoriteItems.postValue(items);
            hasFavorites.postValue(!items.isEmpty());
        });
    }

    public void updateFavoriteLastUrl(@NonNull Context ctx,
            @NonNull String stationuuid,
            @NonNull String newUrl) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase.getDatabase(ctx)
                    .radioStationDao() // or radioFavoriteDao(), adapt to your naming
                    .updateLastUrl(stationuuid, newUrl);
        });
    }
}
