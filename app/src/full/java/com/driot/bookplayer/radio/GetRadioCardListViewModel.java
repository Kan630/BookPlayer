package com.driot.bookplayer.radio;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.global.Var;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GetRadioCardListViewModel extends AndroidViewModel {

    // --- UI State ---
    public enum LoadingState { IDLE, LOADING, SUCCESS, ERROR }
    private volatile boolean cancelled = false;

    private final MutableLiveData<List<TagItem>> itemsLive = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<TagItem>> filteredItemsLive = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<LoadingState> loadingStateLive = new MutableLiveData<>(LoadingState.IDLE);

    // --- Search ---
    private final MutableLiveData<String> searchQueryLive = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> searchVisibleLive = new MutableLiveData<>(false);

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

    // -------------------------------------------------------------------------
    // Cache seed (call before loadFacetItems to pre-populate from disk)
    // -------------------------------------------------------------------------

    public void seedFromCache(List<TagItem> cached) {
        if (cached != null && !cached.isEmpty()) {
            itemsLive.setValue(cached);
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
                    itemsLive.setValue(rsp.body());
                    applyFilter();
                    RadioCacheHelper.saveCache(getApplication(), currentMode, rsp.body());
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
        if (query == null || query.trim().isEmpty()) {
            filteredItemsLive.setValue(new ArrayList<>(all));
            return;
        }

        String q = query.trim().toLowerCase();
        List<TagItem> filtered = new ArrayList<>();
        for (TagItem item : all) {
            if (item.name != null && item.name.toLowerCase().contains(q)) {
                filtered.add(item);
            }
        }
        filteredItemsLive.setValue(filtered);
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
