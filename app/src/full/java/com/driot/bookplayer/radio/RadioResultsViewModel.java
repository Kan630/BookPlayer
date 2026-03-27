package com.driot.bookplayer.radio;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.RadioStation;
import com.driot.bookplayer.db.RadioStationDao;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.utils.LiveCensorshipManager;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RadioResultsViewModel extends LoggingAndroidViewModel {

    // ---- One-shot UI events (Activity observes and maps to toasts / finish) ----
    public enum UiEvent { NO_RESULT_FINISH, NETWORK_ERROR_FINISH, NO_INTERNET_FINISH }

    private final MutableLiveData<UiEvent> uiEvent = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isInitialLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isLoadingMore = new MutableLiveData<>(false);

    private final MutableLiveData<List<ApiStation>> results = new MutableLiveData<>();
    private final MutableLiveData<Boolean> shouldFinish = new MutableLiveData<>(false);
    private final MutableLiveData<String> headerCount = new MutableLiveData<>("");

    private final MutableLiveData<Boolean> showingHistory = new MutableLiveData<>(false);
    private boolean historyMode = false;

    private final Map<String, String> faviconCache = new HashMap<>();

    // ---- Search params ----
    private String lastQuery = "";
    private String lastLang = "";
    private String lastCountry = "";
    private String lastTag = "";
    private String lastSearchMode = "";
    // Extra language variant names to query alongside the canonical lang (MODE_LANGUAGE only).
    // Index 0 is canonical (already covered by lastLang); index 1..N are aliases.
    private List<String> lastLangVariants = new ArrayList<>();

    // ---- Pagination state ----
    private int currentOffset = 0;
    private boolean hasMore = true;
    private boolean isLoading = false;

    // ---- Repository (set once via initRepo) ----
    private RadioBrowserRepository repo;

    public RadioResultsViewModel(@NonNull Application application) {
        super(application);
    }

    /** Must be called once (in Activity.onCreate) before any search. */
    public void initRepo(RadioBrowserRepository repo) {
        this.repo = repo;
    }

    // =========================================================================
    // Exposed LiveData
    // =========================================================================

    public LiveData<UiEvent>          getUiEvent()          { return uiEvent; }
    public LiveData<Boolean>          getIsInitialLoading() { return isInitialLoading; }
    public LiveData<Boolean>          getIsLoadingMore()    { return isLoadingMore; }
    public LiveData<List<ApiStation>> getResults()          { return results; }
    public LiveData<Boolean>          getShouldFinish()     { return shouldFinish; }
    public LiveData<String>           getHeaderCount()      { return headerCount; }
    public LiveData<Boolean>          getShowingHistory()   { return showingHistory; }
    public Map<String, String>        getFaviconCache()     { return faviconCache; }

    // =========================================================================
    // Search entry-points (called from Activity)
    // =========================================================================

    /**
     * Starts a fresh search. Clears previous results and resets pagination.
     * Call this only when there are no existing results (i.e. not on rotation).
     */
    public void search(String mode, String q, String lang, String country, String tag,
                       @Nullable List<String> langVariants) {
        if (repo == null) return;
        setLastParams(q, lang, country, tag);
        setLastSearchMode(mode);
        if (langVariants != null) setLastLangVariants(langVariants);
        resetPagination();
        isLoading = true;   // block scroll listener from triggering pagination before first response
        isInitialLoading.setValue(true);
        doSearch(false);
    }

    /**
     * Loads the next page using the stored search params and current offset.
     * No-ops if already loading, no more pages, or no repo.
     */
    public void loadNextPage() {
        if (isLoading || !hasMore || repo == null) return;
        setLoading(true);
        doSearch(true);
    }

    // =========================================================================
    // Internal: dispatch to the right repo call
    // =========================================================================

    /**
     * Single switch that drives both initial load (offset=null) and
     * pagination (offset=currentOffset). Avoids duplicating the switch.
     */
    private void doSearch(boolean isPagination) {
        int limit = Option.getRadioApiNbResults();
        Integer offset = isPagination ? currentOffset : null;
        Callback<List<ApiStation>> cb = resultsCb(isPagination);

        switch (lastSearchMode) {
            case "MODE_TOP_VOTE":
                repo.topVoted(limit, offset, cb);
                break;
            case "MODE_TOP_CLICK":
                repo.topClicked(limit, offset, cb);
                break;
            case "MODE_LAST_CLICK":
                repo.lastClicked(limit, offset, cb);
                break;
            case "MODE_LAST_CHANGE":
                repo.lastChanged(limit, offset, cb);
                break;
            case "MODE_TAG":
                if (!lastTag.isEmpty()) repo.byTag(lastTag, limit, offset, cb);
                break;
            case "MODE_COUNTRY":
                if (!lastCountry.isEmpty()) repo.byCountry(lastCountry, limit, offset, cb);
                break;
            case "MODE_LANGUAGE":
                if (!lastLang.isEmpty()) repo.byLanguageExact(lastLang, limit, offset, cb);
                break;
            case "MODE_SEARCH":
            default:
                if (!lastQuery.isEmpty()) repo.byName(lastQuery, limit, offset, cb);
                break;
        }
    }

    // =========================================================================
    // Callbacks
    // =========================================================================

    private Callback<List<ApiStation>> resultsCb(boolean isPagination) {
        return new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<ApiStation>> call,
                                   @NonNull Response<List<ApiStation>> rsp) {
                if (!isPagination) isInitialLoading.postValue(false);

                List<ApiStation> body = rsp.body();
                if (rsp.isSuccessful() && body != null && !body.isEmpty()) {
                    int rawSize = body.size();
                    boolean serverHasMorePages = rawSize >= Option.getRadioApiNbResults();
                    myLogD("serverHasMorePages: " + serverHasMorePages + " (rawSize=" + rawSize + ")");

                    FilterResult fr = filterStations(body);
                    String headerExtra = buildFilterHeaderText(fr);

                    if (isPagination) {
                        appendResults(body, rawSize);
                        setHasMore(serverHasMorePages);
                        List<ApiStation> all = results.getValue();
                        setHeaderCount(str(R.string.Results_2pt)
                                + (all != null ? all.size() : body.size()) + headerExtra);
                        myLog("radio pagination (" + lastSearchMode + ") = " + body.size()
                                + " new, total: " + (all != null ? all.size() : 0));
                    } else {
                        setResults(body, rawSize);
                        setHasMore(serverHasMorePages);
                        setHeaderCount(str(R.string.Results_2pt) + body.size() + headerExtra);
                        myLog("radio results (" + lastSearchMode + ") = " + body.size());

                        // For language mode: fire one exact-match call per alias variant.
                        if ("MODE_LANGUAGE".equals(lastSearchMode)) {
                            List<String> aliases = getLangAliasesOnly();
                            myLog("lang aliases to query: " + aliases);
                            for (String alias : aliases) {
                                repo.byLanguageExact(alias, Option.getRadioApiNbResults(),
                                        null, aliasResultsCb(alias));
                            }
                        }
                    }
                } else {
                    if (!isPagination) {
                        uiEvent.postValue(UiEvent.NO_RESULT_FINISH);
                        requestFinish();
                    } else {
                        setLoading(false);
                        setHasMore(false);
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<ApiStation>> call, @NonNull Throwable t) {
                if (!isPagination) isInitialLoading.postValue(false);
                setLoading(false);
                if (!isPagination) {
                    if (NetworkHelper.isUnknownHost(t)) {
                        uiEvent.postValue(UiEvent.NO_INTERNET_FINISH);
                    } else {
                        myLogEE(t, "radio search failed (" + lastSearchMode + ")");
                        uiEvent.postValue(UiEvent.NETWORK_ERROR_FINISH);
                    }
                    requestFinish();
                } else {
                    myLogW("radio pagination failed (" + lastSearchMode + "): " + t.getMessage());
                }
            }
        };
    }

    /**
     * Callback for alias language queries (e.g. "português brasil" when browsing
     * "brazilian portuguese"). Uses exact-match endpoint to avoid false positives
     * (e.g. "brasil" substring-matching "portugues do brasil"). Results are appended
     * and deduplicated by uuid; pagination is not needed since exact matches are bounded.
     */
    private Callback<List<ApiStation>> aliasResultsCb(String alias) {
        return new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<ApiStation>> call,
                                   @NonNull Response<List<ApiStation>> rsp) {
                List<ApiStation> body = rsp.body();
                if (rsp.isSuccessful() && body != null && !body.isEmpty()) {
                    int received = body.size();
                    int dedupCount = appendResultsFromAlias(body); // does NOT touch currentOffset
                    int added = received - dedupCount;
                    boolean hitLimit = received >= Option.getRadioApiNbResults();
                    myLogD("alias lang [" + alias + "] → " + received + " received, "
                            + added + " added, " + dedupCount + " deduped"
                            + (hitLimit ? " ⚠️ HIT LIMIT" : ""));
                    List<ApiStation> all = results.getValue();
                    if (all != null) setHeaderCount(str(R.string.Results_2pt) + all.size());
                } else {
                    myLogW("alias lang [" + alias + "] → no results");
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<ApiStation>> call, @NonNull Throwable t) {
                myLogW("alias lang [" + alias + "] failed: " + t.getMessage());
            }
        };
    }

    // =========================================================================
    // Station filtering
    // =========================================================================

    private static class FilterResult {
        int nbRemovedDuplicates = 0;
        int nbRemovedDubious    = 0;
        final Set<String> removedNamesDuplicates = new HashSet<>();
    }

    /** Filters {@code body} in-place (removes censored / dubious / spam stations). */
    private FilterResult filterStations(List<ApiStation> body) {
        FilterResult fr = new FilterResult();
        Set<String> censoredRadios = LiveCensorshipManager.getCensoredRadios(getApplication());
        boolean removeDubious    = Option.getRadioRemoveDubiousStations();
        boolean removeDuplicates = Option.getRadioRemoveSpamStations();
        Map<String, Integer> countMap = new HashMap<>();

        Iterator<ApiStation> it = body.iterator();
        while (it.hasNext()) {
            ApiStation s = it.next();
            if (s.name == null) continue;
            String trimmed = s.name.toLowerCase().replaceAll("[^a-z0-9]", "");

            if (LiveCensorshipManager.isCensoredAlreadyTrimmed(trimmed, censoredRadios)) {
                myLogW("[" + s.name + "] is censored. trimmedName=" + trimmed);
                it.remove();
                continue;
            }
            if (removeDubious && Var.RADIO_STATION_BLACKLIST_LOWERCASE.contains(trimmed)) {
                it.remove();
                fr.nbRemovedDubious++;
                continue;
            }
            if (removeDuplicates) {
                Integer countObj = countMap.get(trimmed);
                int count = countObj != null ? countObj : 0;
                if (count >= Var.RADIO_STATION_MAX_DUPLICATES) {
                    it.remove();
                    fr.nbRemovedDuplicates++;
                    fr.removedNamesDuplicates.add(s.name);
                } else {
                    countMap.put(trimmed, count + 1);
                }
            }
        }
        return fr;
    }

    private String buildFilterHeaderText(FilterResult fr) {
        String headerTxt = "";
        if (fr.nbRemovedDuplicates > 0) {
            headerTxt += "    (" + fr.nbRemovedDuplicates + " "
                    + str(R.string.spam_fake_stations_removed)
                    + " : " + fr.removedNamesDuplicates + ")";
            myLog(fr.nbRemovedDuplicates + " stations removed (duplicates)");
            myLog("Removed duplicate names: " + fr.removedNamesDuplicates);
        }
        if (fr.nbRemovedDubious > 0) {
            headerTxt += "    (" + fr.nbRemovedDubious + " "
                    + str(R.string.dubious_stations_removed) + ")";
            myLog(fr.nbRemovedDubious + " stations removed (dubious)");
        }
        return headerTxt;
    }

    /** Shorthand for Application string resource lookup. */
    private String str(int resId) {
        return getApplication().getString(resId);
    }

    // =========================================================================
    // Result state management
    // =========================================================================

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

    public void appendResults(List<ApiStation> apiStations, int rawResponseSize) {
        mergeIntoResults(apiStations);
        // Advance the canonical pagination offset (used by loadNextPage).
        // Only call this for canonical language pages, NOT for alias enrichment calls.
        currentOffset += rawResponseSize;
        isLoading = false;
        isLoadingMore.postValue(false);
    }

    /**
     * Appends stations from a language alias call (e.g. "português brasil" when the
     * canonical is "brazilian portuguese").  Does NOT touch {@code currentOffset} so
     * the canonical pagination cursor stays correct.
     *
     * @return number of stations that were deduplicated (already present)
     */
    public int appendResultsFromAlias(List<ApiStation> apiStations) {
        return mergeIntoResults(apiStations);
        // intentionally no currentOffset change, no isLoading change
    }

    /**
     * Shared dedup + merge logic.
     *
     * @return number of stations rejected as duplicates
     */
    private int mergeIntoResults(List<ApiStation> apiStations) {
        List<ApiStation> current = results.getValue();
        if (current == null) {
            current = new ArrayList<>();
        }
        int dedupCount = 0;
        if (apiStations != null && !apiStations.isEmpty()) {
            Set<String> existingUuids = new HashSet<>();
            for (ApiStation s : current) {
                if (s.stationuuid != null)
                    existingUuids.add(s.stationuuid);
            }
            List<ApiStation> uniqueApiStations = new ArrayList<>();
            for (ApiStation s : apiStations) {
                if (s.stationuuid != null && !existingUuids.contains(s.stationuuid)) {
                    uniqueApiStations.add(s);
                    existingUuids.add(s.stationuuid);
                } else {
                    dedupCount++;
                }
            }
            if (!uniqueApiStations.isEmpty()) {
                current.addAll(uniqueApiStations);
            }
        }
        results.postValue(current);
        return dedupCount;
    }

    // =========================================================================
    // Pagination state
    // =========================================================================

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

    public void resetPagination() {
        currentOffset = 0;
        hasMore = true;
        isLoading = false;
        isLoadingMore.postValue(false);
    }

    // =========================================================================
    // Search params
    // =========================================================================

    public void setLastSearchMode(String mode) {
        lastSearchMode = mode != null ? mode : "";
    }

    public String getLastSearchMode() {
        return lastSearchMode;
    }

    public String getLastQuery()   { return lastQuery; }
    public String getLastLang()    { return lastLang; }
    public String getLastCountry() { return lastCountry; }
    public String getLastTag()     { return lastTag; }

    public void setLastParams(String query, String lang, String country, String tag) {
        lastQuery   = query   == null ? "" : query;
        lastLang    = lang    == null ? "" : lang;
        lastCountry = country == null ? "" : country;
        lastTag     = tag     == null ? "" : tag;
    }

    public void setLastLangVariants(List<String> variants) {
        lastLangVariants = variants != null ? variants : new ArrayList<>();
    }

    /** Returns alias names at index 1..N (index 0 = canonical, already queried separately). */
    public List<String> getLangAliasesOnly() {
        if (lastLangVariants.size() <= 1) return new ArrayList<>();
        return lastLangVariants.subList(1, lastLangVariants.size());
    }

    // =========================================================================
    // Favorites / History state exposed to UI
    // =========================================================================

    private final MutableLiveData<Set<String>>       favoriteUuids = new MutableLiveData<>(new HashSet<>());
    private final MutableLiveData<List<RadioStation>> favoriteItems = new MutableLiveData<>();
    private final MutableLiveData<Boolean>            hasFavorites  = new MutableLiveData<>(false);

    public LiveData<Set<String>>       getFavoriteUuids() { return favoriteUuids; }
    public LiveData<List<RadioStation>> getFavoriteItems() { return favoriteItems; }

    // =========================================================================
    // DB helpers
    // =========================================================================

    private RadioStationDao dao(Context ctx) {
        return AppDatabase.getInstance(ctx.getApplicationContext()).radioStationDao();
    }

    private void copyFromStationToRadioStation(RadioStation r, ApiStation s) {
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

        if (s.favicon != null && s.favicon.startsWith("http")) {
            r.imageOriginalUrl = s.favicon;
        }
        // do not touch date_added / date_last_played / display_order here
    }

    public void initMode(Context ctx) {
        Context appCtx = ctx.getApplicationContext();
        AppDatabase.databaseReadExecutor.execute(() -> {
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
        });
    }

    public void loadFavorites(Context ctx) {
        historyMode = false;
        showingHistory.postValue(false);
        refreshFromDb(ctx, false);
    }

    public void loadHistory(Context ctx) {
        historyMode = true;
        showingHistory.postValue(true);
        refreshFromDb(ctx, true);
    }

    // toggle favorite from an ApiStation (used from search results)
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
                    existing.date_maj   = now;
                    dao.update(existing);
                } else {
                    // re-favorite an existing station
                    copyFromStationToRadioStation(existing, s);
                    existing.isFavorite = true;
                    existing.date_maj   = now;
                    dao.update(existing);
                    RadioFaviconHelper.resolveAndPersistFavicon(appCtx, existing);
                }
            } else {
                // brand new favorite
                RadioStation r = new RadioStation();
                r.stationuuid    = s.stationuuid;
                copyFromStationToRadioStation(r, s);
                r.isFavorite     = true;
                r.display_order  = 0;
                r.date_added     = now;
                r.date_maj       = now;
                r.date_last_played = null;
                r.id = dao.insert(r);
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
                existing.date_maj   = System.currentTimeMillis();
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

            Set<String>       uuids = new HashSet<>();
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
        AppDatabase.databaseWriteExecutor.execute(() ->
                AppDatabase.getDatabase(ctx)
                        .radioStationDao()
                        .updateLastUrl(stationuuid, newUrl));
    }
}
