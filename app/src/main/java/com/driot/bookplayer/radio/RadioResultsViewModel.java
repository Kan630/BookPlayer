package com.driot.bookplayer.radio;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.utils.log.LoggingViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RadioResultsViewModel extends LoggingViewModel {

    private final MutableLiveData<List<Station>> results = new MutableLiveData<>();
    private final MutableLiveData<Boolean> shouldFinish = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isLoadingMore = new MutableLiveData<>(false);

    private final MutableLiveData<Boolean> showingHistory = new MutableLiveData<>(false);
    private boolean historyMode = false;

    public LiveData<Boolean> getShowingHistory() { return showingHistory; }
    public LiveData<Boolean> getIsLoadingMore() { return isLoadingMore; }

    private String lastQuery = "";
    private String lastLang = "";
    private String lastCountry = "";
    private String lastTag = "";
    private String lastSearchMode = "";
    
    // Pagination state
    private int currentOffset = 0;
    private boolean hasMore = true;
    private boolean isLoading = false;

    public LiveData<List<Station>> getResults() { return results; }
    public LiveData<Boolean> getShouldFinish() { return shouldFinish; }

    public void setResults(List<Station> stations) { 
        results.postValue(stations);
        // Reset pagination state when setting new results (first page)
        currentOffset = stations != null ? stations.size() : 0;
        hasMore = stations != null && stations.size() > 0;
        isLoading = false;
        isLoadingMore.postValue(false);
    }
    
    public void appendResults(List<Station> stations) {
        List<Station> current = results.getValue();
        if (current == null) {
            current = new ArrayList<>();
        }
        if (stations != null && !stations.isEmpty()) {
            current.addAll(stations);
            currentOffset += stations.size();
            // If we got fewer results than requested, assume no more pages
            // Note: This is a heuristic - the actual limit is passed from the activity
            // For now, we'll rely on empty response to indicate no more
            hasMore = true;
        } else {
            hasMore = false; // No more results
        }
        results.postValue(current);
        isLoading = false;
        isLoadingMore.postValue(false);
    }
    
    public void requestFinish() { shouldFinish.postValue(true); }
    
    public int getCurrentOffset() { return currentOffset; }
    public boolean hasMore() { return hasMore; }
    public boolean isLoading() { return isLoading; }
    
    public void setLoading(boolean loading) { 
        isLoading = loading;
        isLoadingMore.postValue(loading);
    }
    
    public void setLastSearchMode(String mode) { lastSearchMode = mode != null ? mode : ""; }
    public String getLastSearchMode() { return lastSearchMode; }
    
    public void resetPagination() {
        currentOffset = 0;
        hasMore = true;
        isLoading = false;
        isLoadingMore.postValue(false);
    }

    public String getLastQuery() { return lastQuery; }
    public String getLastLang() { return lastLang; }
    public String getLastCountry() { return lastCountry; }
    public String getLastTag() { return lastTag; }

    public void setLastParams(String query, String lang, String country, String tag) {
        lastQuery = query == null ? "" : query;
        lastLang = lang == null ? "" : lang;
        lastCountry = country == null ? "" : country;
        lastTag = tag == null ? "" : tag;
    }

    // ---- Favorites state exposed to UI ----

    private final MutableLiveData<Set<String>> favoriteUuids =
            new MutableLiveData<>(new HashSet<>());
    private final MutableLiveData<List<RadioFavoriteItem>> favoriteItems =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> hasFavorites =
            new MutableLiveData<>(false);

    public LiveData<Set<String>> getFavoriteUuids() { return favoriteUuids; }
    public LiveData<List<RadioFavoriteItem>> getFavoriteItems() { return favoriteItems; }

    // ---- Helpers ----

    private RadioStationDao dao(Context ctx) {
        return AppDatabase.getInstance(ctx.getApplicationContext()).radioStationDao();
    }

    private void copyFromStationToRadioStation(RadioStation r, Station s) {
        r.stationuuid  = s.stationuuid;
        r.name         = s.name;
        r.url          = s.url;
        r.url_resolved = s.url_resolved;
        r.codec        = s.codec;
        r.bitrate      = s.bitrate;
        r.hls          = s.hls;
        r.favicon      = s.favicon;
        r.country      = s.country;
        r.countrycode  = s.countrycode;
        r.language     = s.language;
        r.tags         = s.tags;
        r.clickcount   = s.clickcount;
        r.lastcheckok  = s.lastcheckok;
        // do not touch date_added / date_last_played / display_order here
    }


    public void initMode(Context ctx) {
        Context appCtx = ctx.getApplicationContext();
        new Thread(() -> {
            RadioStationDao d = dao(appCtx);

            int favCount  = d.countFavorites();
            int histCount = d.countHistory();
            myLogD("init - favorites : " + favCount + " - history : " + histCount);

            if (favCount > 0) {
                loadFavorites(appCtx);
            } else if (histCount > 0) {
                loadHistory(appCtx);
            } else {
                loadFavorites(appCtx);
            }
        }).start();
    }

    // ---- Load favorites from Room ----

    public void loadFavorites(Context ctx) {
        historyMode = false;                 // <--- update field
        showingHistory.postValue(false);     // notify UI
        refreshFromDb(ctx);
    }

    public void loadHistory(Context ctx) {
        historyMode = true;                  // <--- update field
        showingHistory.postValue(true);      // notify UI
        refreshFromDb(ctx);
    }

    // toggle favorite from a Station (used from search results)
    public void toggleFavorite(Context ctx, Station s) {
        Context appCtx = ctx.getApplicationContext();
        new Thread(() -> {
            RadioStationDao dao = dao(appCtx);
            long now = System.currentTimeMillis();

            RadioStation existing = dao.findByUuid(s.stationuuid);
            if (existing != null) {
                if (existing.isFavorite) {
                    // remove from favorites, keep row for history
                    existing.isFavorite = false;
                    existing.date_maj   = now;
                    dao.update(existing);
                } else {
                    // re-favorite an existing station
                    copyFromStationToRadioStation(existing, s);
                    existing.isFavorite = true;
                    existing.date_maj   = now;
                    dao.update(existing);
                }
            } else {
                // brand new favorite
                RadioStation r = new RadioStation();
                r.stationuuid      = s.stationuuid;
                copyFromStationToRadioStation(r, s);
                r.isFavorite       = true;
                r.display_order    = 0; // new favorites at top; you can tweak this
                r.date_added       = now;
                r.date_maj         = now;
                r.date_last_played = null;

                dao.insert(r);
            }

            refreshFromDb(appCtx);
        }).start();
    }

    public void reorderFavorites(Context ctx, List<RadioFavoriteItem> newOrder) {
        Context appCtx = ctx.getApplicationContext();
        new Thread(() -> {
            RadioStationDao dao = dao(appCtx);
            int idx = 0;
            for (RadioFavoriteItem it : newOrder) {
                dao.updateDisplayOrder(it.stationuuid, idx++);
            }
            refreshFromDb(appCtx);
        }).start();
    }

    public void removeFavoriteUuid(Context ctx, String uuid) {
        Context appCtx = ctx.getApplicationContext();
        new Thread(() -> {
            RadioStationDao dao = dao(appCtx);
            RadioStation existing = dao.findByUuid(uuid);
            if (existing != null && existing.isFavorite) {
                existing.isFavorite = false;
                existing.date_maj   = System.currentTimeMillis();
                dao.update(existing);
            }
            refreshFromDb(appCtx);
        }).start();
    }

    private void refreshFromDb(Context ctx) {
        Context appCtx = ctx.getApplicationContext();
        new Thread(() -> {
            RadioStationDao dao = dao(appCtx);

            boolean history = historyMode;
            myLogD("refresh from DB, history mode=" + history);

            List<RadioStation> rows = history
                    ? dao.getAlreadyPlayed()
                    : dao.getFavorites();

            Set<String> uuids = new HashSet<>();
            List<RadioFavoriteItem> items = new ArrayList<>(rows.size());

            for (RadioStation r : rows) {
                uuids.add(r.stationuuid);
                items.add(RadioFavoriteItem.fromRadioStation(r));
            }

            favoriteUuids.postValue(uuids);
            favoriteItems.postValue(items);
            hasFavorites.postValue(!items.isEmpty());
        }).start();
    }

    public void updateFavoriteLastUrl(@NonNull Context ctx,
                                      @NonNull String stationuuid,
                                      @NonNull String newUrl) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase.getDatabase(ctx)
                    .radioStationDao()          // or radioFavoriteDao(), adapt to your naming
                    .updateLastUrl(stationuuid, newUrl);
        });
    }
}
