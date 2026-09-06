package com.driot.bookplayer.podcasts;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Episode;
import com.driot.bookplayer.db.EpisodeDao;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.PodcastDao;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PodcastDownloadSettingsViewModel extends LoggingAndroidViewModel {
    private final EpisodeDao episodeDao;
    private final PodcastDao podcastDao;

    private final MutableLiveData<Long> totalStorageBytesLive = new MutableLiveData<>();
    private final MutableLiveData<Integer> undownloadedCountLive = new MutableLiveData<>();

    public PodcastDownloadSettingsViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getDatabase(application);
        episodeDao = db.episodeDao();
        podcastDao = db.podcastDao();
    }

    public LiveData<Long> getTotalStorageBytesLive() {
        return totalStorageBytesLive;
    }

    public LiveData<Integer> getUndownloadedCountLive() {
        return undownloadedCountLive;
    }

    // Newest-first list is reused both to count undownloaded episodes and, later, to pick the
    // N most recent ones in downloadLastN() - avoids a second, differently-sorted query.
    private List<Episode> newestFirstUndownloaded(Podcast podcast) {
        List<Episode> all = episodeDao.getAllEpisodesForPodcastNewestFirst(podcast.getId());
        List<Episode> undownloaded = new ArrayList<>();
        for (Episode ep : all) {
            if (ep.idZikFile == null) {
                undownloaded.add(ep);
            }
        }
        return undownloaded;
    }

    public void refreshStats(Context context, Podcast podcast) {
        AppDatabase.databaseReadExecutor.execute(() -> {
            File folder = PodcastHelper.buildPodcastPath(context, podcast);
            long bytes = folder.exists() ? StorageHelper.getFolderSize(folder) : 0L;
            totalStorageBytesLive.postValue(bytes);

            undownloadedCountLive.postValue(newestFirstUndownloaded(podcast).size());
        });
    }

    // Auto-download no longer requires favoriting first. Turning it on favorites the podcast
    // automatically (so it shows up in the favorites list you check); turning it off never
    // unfavorites - the two are otherwise independent.
    public void setAutoDownload(Context context, Podcast podcast, boolean enabled) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Podcast fresh = podcastDao.getPodcastByFeedId(podcast.feedId);
            if (fresh == null) {
                // Reachable now that the settings screen is open regardless of favorite/DB state.
                PodcastHelper.addPodcastToDB(context,
                        new PodcastFeed(podcast.feedId, podcast.title, podcast.image, podcast.description));
                fresh = podcastDao.getPodcastByFeedId(podcast.feedId);
                if (fresh == null) {
                    myLogE("setAutoDownload: could not create Podcast row for feedId=" + podcast.feedId);
                    return;
                }
            }
            fresh.autoDownload = enabled;
            if (enabled) {
                fresh.isFavorite = true;
            }
            podcastDao.update(fresh);
            if (enabled) {
                myLog("Auto-download turned on for [" + fresh.title + "]");
                PodcastHelper.checkForNewEpisodesToAutoDownloadForPodcast(context, fresh, Var.PODCAST_INDEX_ORG_SINCE);
                FirebaseAnalyticsHelper.tellAnalyticsPodcastAutoDownload(fresh.title, fresh.language);
            }
        });
    }

    /** Downloads the N most recently published episodes that have never been downloaded yet. */
    public void downloadLastN(Context context, Podcast podcast, int n, Runnable onStarted) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Episode> undownloaded = newestFirstUndownloaded(podcast);
            List<PodcastEpisode> toDownload = new ArrayList<>();
            for (Episode ep : undownloaded) {
                if (toDownload.size() >= n) break;
                toDownload.add(DisplayableEpisode.fromDatabaseEpisode(ep).toPodcastEpisode());
            }
            if (toDownload.isEmpty()) {
                myLogW("downloadLastN: nothing to download (n=" + n + ")");
                return;
            }

            File targetFolder = PodcastHelper.buildPodcastPath(context, podcast);
            if (!targetFolder.exists())
                targetFolder.mkdirs();

            myLog("downloadLastN: enqueueing " + toDownload.size() + " episode(s) for [" + podcast.title + "]");
            PodcastDownloadManager.enqueueDownloads(context, podcast.feedId, toDownload, targetFolder, null);

            if (onStarted != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(onStarted);
            }
        });
    }
}
