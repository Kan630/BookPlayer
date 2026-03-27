package com.driot.bookplayer.radio;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.librivox.LanguageMapper;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GetRadioCardListViewModel extends LoggingAndroidViewModel {

    // --- UI State ---
    public enum LoadingState { IDLE, LOADING, SUCCESS, ERROR }
    private volatile boolean cancelled = false;

    private final MutableLiveData<List<TagItem>> itemsLive = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<TagItem>> filteredItemsLive = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<LoadingState> loadingStateLive = new MutableLiveData<>(LoadingState.IDLE);

    // --- Search ---
    private final MutableLiveData<String> searchQueryLive = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> searchVisibleLive = new MutableLiveData<>(false);

    // --- Sort ---
    private String tagSortMode = "station_count";  // "station_count" | "alpha"
    private String tagSortDir  = "desc";           // "asc" | "desc"

    // --- Internals ---
    private RadioBrowserRepository repo;
    private @GetRadioCardListActivity.FacetMode int currentMode = GetRadioCardListActivity.MODE_TAG;
    private Call<?> pendingCall;

    public GetRadioCardListViewModel(@NonNull Application application) {
        super(application);
    }

    public void init(@GetRadioCardListActivity.FacetMode int mode, RadioBrowserRepository repo) {
        this.currentMode = mode;
        this.repo = repo;
    }

    // -------------------------------------------------------------------------
    // Exposed LiveData
    // -------------------------------------------------------------------------

    public LiveData<List<TagItem>> getFilteredItemsLive() { return filteredItemsLive; }
    public LiveData<LoadingState> getLoadingStateLive()   { return loadingStateLive; }
    public LiveData<String>       getSearchQueryLive()    { return searchQueryLive; }
    public LiveData<Boolean>      getSearchVisibleLive()  { return searchVisibleLive; }

    public String getSortMode() { return tagSortMode; }
    public String getSortDir()  { return tagSortDir; }

    public void setSortOrder(String mode, String dir) {
        tagSortMode = mode;
        tagSortDir  = dir;
        applyFilter();
    }

    // -------------------------------------------------------------------------
    // Cache seed (call before loadFacetItems to pre-populate from disk)
    // -------------------------------------------------------------------------

    public void seedFromCache(List<TagItem> cached) {
        if (cached != null && !cached.isEmpty()) {
            itemsLive.setValue(mergeByIso(cached));
            applyFilter();
        }
    }

    // -------------------------------------------------------------------------
    // Network
    // -------------------------------------------------------------------------

    public void loadFacetItems() {
        if (repo == null) return;

        boolean hasItems = itemsLive.getValue() != null && !itemsLive.getValue().isEmpty();
        if (!hasItems) {
            loadingStateLive.setValue(LoadingState.LOADING);
        }

        Callback<List<TagItem>> callback = new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<TagItem>> call, @NonNull Response<List<TagItem>> rsp) {
                if (cancelled) return;
                if (rsp.isSuccessful() && rsp.body() != null) {
                    List<TagItem> merged = mergeByIso(rsp.body());
                    itemsLive.setValue(merged);
                    applyFilter();
                    RadioCacheHelper.saveCache(getApplication(), currentMode, merged);
                } else {
                    if (itemsLive.getValue() == null || itemsLive.getValue().isEmpty()) {
                        itemsLive.setValue(new ArrayList<>());
                        filteredItemsLive.setValue(new ArrayList<>());
                    }
                }
                loadingStateLive.setValue(LoadingState.SUCCESS);
            }

            @Override
            public void onFailure(@NonNull Call<List<TagItem>> call, @NonNull Throwable t) {
                if (cancelled) return;
                loadingStateLive.setValue(LoadingState.ERROR);
            }
        };

        cancelled = false;
        switch (currentMode) {
            case GetRadioCardListActivity.MODE_COUNTRY:
                repo.getTopCountries(Var.RADIO_LIST_MAX_CARD_ITEM, callback);
                break;
            case GetRadioCardListActivity.MODE_LANGUAGE:
                repo.getTopLanguages(Var.RADIO_LIST_MAX_CARD_ITEM, callback);
                break;
            case GetRadioCardListActivity.MODE_TAG:
            default:
                repo.getTopTags(Var.RADIO_LIST_MAX_CARD_ITEM, callback);
                break;
        }
    }

    // -------------------------------------------------------------------------
    // Search
    // -------------------------------------------------------------------------

    public void setSearchQuery(String query) {
        searchQueryLive.setValue(query == null ? "" : query);
        applyFilter();
    }

    public void setSearchVisible(boolean visible) {
        searchVisibleLive.setValue(visible);
        if (!visible) {
            setSearchQuery("");
        }
    }

    public boolean isSearchVisible() {
        return Boolean.TRUE.equals(searchVisibleLive.getValue());
    }

    private void applyFilter() {
        List<TagItem> all = itemsLive.getValue();
        if (all == null) {
            filteredItemsLive.setValue(new ArrayList<>());
            return;
        }

        String query = searchQueryLive.getValue();
        List<TagItem> result;
        if (query == null || query.trim().isEmpty()) {
            result = new ArrayList<>(all);
        } else {
            String q = query.trim().toLowerCase();
            result = new ArrayList<>();
            for (TagItem item : all) {
                if (item.name != null && item.name.toLowerCase().contains(q)) {
                    result.add(item);
                }
            }
        }

        // Sort
        Comparator<TagItem> comp;
        if ("alpha".equals(tagSortMode)) {
            comp = Comparator.comparing(i -> i.name != null ? i.name.toLowerCase() : "");
            if ("desc".equals(tagSortDir)) comp = comp.reversed();
        } else {
            comp = Comparator.comparingInt(i -> i.stationcount);
            if ("desc".equals(tagSortDir)) comp = ((Comparator<TagItem>) comp).reversed();
        }
        result.sort(comp);

        filteredItemsLive.setValue(result);
    }

    // -------------------------------------------------------------------------
    // Deduplication
    // -------------------------------------------------------------------------

    /**
     * For MODE_LANGUAGE: merges entries that share the same iso_639 code.
     * The API returns items sorted by stationcount desc, so the first entry for
     * each code has the best/most-popular name — duplicates only add their count.
     *
     * Items whose iso_639 is null in the API are resolved via
     * {@link LanguageMapper#resolveIso639} (reads twoLetterCode from MAP), so that e.g.:
     *  - "язык: русский"      → "ru"   → merged with "russian"
     *  - "português brasil"   → "ptbr" → merged with "brazilian portuguese"
     *  - "português (brasil)" → "ptbr" → merged with "brazilian portuguese"
     *  - "bahasa indonesia"   → "id"   → merged with "indonesian"
     *  - "engilsh" (typo)     → "en"   → merged with "english"
     *
     * When a code is resolved this way it is also written back to
     * {@code item.iso_639} so that the adapter can display the correct flag.
     */
    private List<TagItem> mergeByIso(List<TagItem> items) {
        if (currentMode != GetRadioCardListActivity.MODE_LANGUAGE) return items;
        Map<String, TagItem> byIso = new LinkedHashMap<>();
        List<TagItem> noIso = new ArrayList<>();
        for (TagItem item : items) {
            String iso = item.iso_639;
            if (iso == null || iso.isEmpty()) {
                // Try to resolve from the name (typos, non-English, regional variants)
                iso = LanguageMapper.resolveIso639(item.name);
                if (iso != null) {
                    item.iso_639 = iso;  // assign so the flag is shown correctly
                    //myLogD(item.name + ": " + " iso mapped=" + iso);
                }
            }
            if (iso != null && !iso.isEmpty()) {
                TagItem existing = byIso.get(iso);
                if (existing != null) {
                    existing.stationcount += item.stationcount;
                    //myLogI(item.name + ": " + " duplicate of " + existing.name);
                } else {
                    byIso.put(iso, item);
                    //myLog(item.name + ": " + " iso=" + iso);
                }
            } else {
                noIso.add(item);
                //myLog(item.name + ": " + "no iso");
            }
        }
        List<TagItem> result = new ArrayList<>(byIso.values());
        result.addAll(noIso);
        return result;
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelled = true;   // prevents stale callbacks from updating LiveData
    }
}
