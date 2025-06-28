package com.driot.bookplayer.activities;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.driot.bookplayer.db.LibrivoxItem;

import java.util.List;

public class LibrivoxResultsViewModel extends ViewModel {

    private final MutableLiveData<List<LibrivoxItem>> results = new MutableLiveData<>();
    private final MutableLiveData<Boolean> shouldFinish = new MutableLiveData<>(false);

    private String lastQuery = null;
    private String lastLang = null;

    public LiveData<List<LibrivoxItem>> getResults() {
        return results;
    }

    public void setResults(List<LibrivoxItem> items) {
        results.setValue(items);
    }

    public LiveData<Boolean> getShouldFinish() {
        return shouldFinish;
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
}
