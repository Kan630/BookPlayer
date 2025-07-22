package com.driot.bookplayer.activities;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.objects.PodcastFeed;
import com.driot.bookplayer.utils.log.LoggingViewModel;

import java.util.List;

public class PodcastSearchResultsViewModel extends LoggingViewModel {
    private final MutableLiveData<List<PodcastFeed>> results = new MutableLiveData<>();
    private final MutableLiveData<Boolean> shouldFinish = new MutableLiveData<>();
    private String lastQuery, lastLang;
    private LiveData<List<Podcast>> favoritePodcastsLive;

    public PodcastSearchResultsViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<PodcastFeed>> getResults() { return results; }
    public LiveData<Boolean> getShouldFinish() { return shouldFinish; }
    public void setResults(List<PodcastFeed> data) { results.setValue(data); }
    public void requestFinish() { shouldFinish.setValue(true); }

    public String getLastQuery() { return lastQuery; }
    public String getLastLang() { return lastLang; }
    public void setLastQuery(String q) { lastQuery = q; }
    public void setLastLang(String l) { lastLang = l; }


    public LiveData<List<Podcast>> getFavoritePodcastsLive() {
        if (favoritePodcastsLive == null) {
            favoritePodcastsLive = AppDatabase.getDatabase(getApplication())
                    .PodcastDao()
                    .getFavoritePodcastsLive();
        }
        return favoritePodcastsLive;
    }
}
