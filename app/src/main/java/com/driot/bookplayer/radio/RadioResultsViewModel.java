package com.driot.bookplayer.radio;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RadioResultsViewModel extends ViewModel {

    private final MutableLiveData<List<Station>> results = new MutableLiveData<>();
    private final MutableLiveData<Boolean> shouldFinish = new MutableLiveData<>(false);

    private String lastQuery = "";
    private String lastLang = "";
    private String lastCountry = "";
    private String lastTag = "";

    public LiveData<List<Station>> getResults() { return results; }
    public LiveData<Boolean> getShouldFinish() { return shouldFinish; }

    public void setResults(List<Station> stations) { results.postValue(stations); }
    public void requestFinish() { shouldFinish.postValue(true); }

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

    // fields:
    private final MutableLiveData<Set<String>> favoriteUuids = new MutableLiveData<>(new HashSet<>());
    private final MutableLiveData<List<RadioFavoriteItem>> favoriteItems = new MutableLiveData<>(Collections.emptyList());

    // expose:
    public LiveData<Set<String>> getFavoriteUuids() { return favoriteUuids; }
    public LiveData<List<RadioFavoriteItem>> getFavoriteItems() { return favoriteItems; }

    // init/load favorites:
    public void loadFavorites(Context ctx) {
        RadioFavoritesStore store = new RadioFavoritesStore(ctx.getApplicationContext());
        favoriteUuids.postValue(store.getAllUuids());
        favoriteItems.postValue(store.getAll());
    }

    // toggle favorite from a Station
    public void toggleFavorite(Context ctx, Station s) {
        RadioFavoritesStore store = new RadioFavoritesStore(ctx.getApplicationContext());
        if (store.isFavorite(s.stationuuid)) {
            store.remove(s.stationuuid);
        } else {
            store.add(RadioFavoriteItem.fromStation(s));
        }
        favoriteUuids.postValue(store.getAllUuids());
        favoriteItems.postValue(store.getAll());
    }


    public void reorderFavorites(Context ctx, List<RadioFavoriteItem> newOrder) {
        RadioFavoritesStore store = new RadioFavoritesStore(ctx.getApplicationContext());
        List<String> uuids = new ArrayList<>(newOrder.size());
        for (RadioFavoriteItem it : newOrder) uuids.add(it.stationuuid);
        store.reorderByUuidList(uuids);
        // refresh Livedata from store
        favoriteUuids.postValue(store.getAllUuids());
        favoriteItems.postValue(store.getAll());
    }

    public void removeFavoriteUuid(Context ctx, String uuid) {
        RadioFavoritesStore store = new RadioFavoritesStore(ctx.getApplicationContext());
        store.removeUuid(uuid);
        favoriteUuids.postValue(store.getAllUuids());
        favoriteItems.postValue(store.getAll());
    }

}
