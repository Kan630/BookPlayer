package com.driot.bookplayer.podcasts;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.utils.LiveCensorshipManager;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class PodcastSearchResultsViewModel extends LoggingAndroidViewModel {

    private final MutableLiveData<List<PodcastFeed>> results = new MutableLiveData<>();
    private final MutableLiveData<Boolean> shouldFinish = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    private String lastQuery, lastLang;
    private LiveData<List<Podcast>> favoritePodcastsLive;

    public PodcastSearchResultsViewModel(@NonNull Application application) {
        super(application);
    }

    // Getters
    public LiveData<List<PodcastFeed>> getResults() {
        return results;
    }

    public LiveData<Boolean> getShouldFinish() {
        return shouldFinish;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public String getLastQuery() {
        return lastQuery;
    }

    public String getLastLang() {
        return lastLang;
    }

    public LiveData<List<Podcast>> getFavoritePodcastsLive() {
        if (favoritePodcastsLive == null) {
            favoritePodcastsLive = AppDatabase.getDatabase(getApplication())
                    .podcastDao()
                    .getFavoritePodcastsLive();
        }
        return favoritePodcastsLive;
    }

    // ============================================================
    // ACTIONS
    // ============================================================

    public void search(String query, String lang) {
        results.setValue(null);

        lastQuery = query;
        lastLang = lang;

        fetchPodcasts(query, lang);
    }

    public void fetchTrending(String lang) {
        results.setValue(null);

        lastQuery = "";
        lastLang = lang;

        isLoading.setValue(true);
        errorMessage.setValue(null);

        PodcastHelper.getTrendingPodcasts(lang, Option.getPodcastIndexOrgApiNbResults(), new PodcastHelper.Callback() {
            @Override
            public void onSuccess(List<PodcastFeed> feeds) {
                isLoading.postValue(false);
                if (feeds != null) {
                    Set<String> censored = LiveCensorshipManager.getCensoredPodcasts(getApplication());
                    Iterator<PodcastFeed> it = feeds.iterator();
                    while (it.hasNext()) {
                        if (LiveCensorshipManager.isCensored(it.next().title, censored)) {
                            it.remove();
                        }
                    }
                    results.postValue(feeds);
                } else {
                    results.postValue(new ArrayList<>());
                }
            }

            @Override
            public void onError(Exception e) {
                isLoading.postValue(false);
                errorMessage.postValue(e.getMessage());
                myLogE(e.getMessage());
            }
        });
    }

    private void fetchPodcasts(String query, String lang) {
        isLoading.setValue(true);
        errorMessage.setValue(null);

        PodcastHelper.searchPodcasts(query, lang, new PodcastHelper.Callback() {
            @Override
            public void onSuccess(List<PodcastFeed> feeds) {
                isLoading.postValue(false);
                if (feeds != null) {
                    Set<String> censored = LiveCensorshipManager.getCensoredPodcasts(getApplication());
                    Iterator<PodcastFeed> it = feeds.iterator();
                    while (it.hasNext()) {
                        if (LiveCensorshipManager.isCensored(it.next().title, censored)) {
                            it.remove();
                        }
                    }
                    results.postValue(feeds);
                } else {
                    results.postValue(new ArrayList<>());
                }
            }

            @Override
            public void onError(Exception e) {
                isLoading.postValue(false);
                errorMessage.postValue(e.getMessage());
                myLogE(e.getMessage());
            }
        });
    }
}
