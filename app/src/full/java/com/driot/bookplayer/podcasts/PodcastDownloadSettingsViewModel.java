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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PodcastDownloadSettingsViewModel extends LoggingAndroidViewModel {
    private final EpisodeDao episodeDao;
    private final PodcastDao podcastDao;

    private final MutableLiveData<Long> totalStorageBytesLive = new MutableLiveData<>();
    private final MutableLiveData<Integer> undownloadedCountLive = new MutableLiveData<>();
    private final MutableLiveData<EpisodeStatusCounts> episodeStatusCountsLive = new MutableLiveData<>();
    // Cumulative size (bytes) of the N newest undownloaded episodes: index i = size of
    // downloading the first (i+1) of them. Lets the UI show "download last N" size instantly
    // as N changes, with no extra query per slider move.
    private final MutableLiveData<long[]> undownloadedSizesPrefixSumLive = new MutableLiveData<>();

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

    public LiveData<EpisodeStatusCounts> getEpisodeStatusCountsLive() {
        return episodeStatusCountsLive;
    }

    public LiveData<long[]> getUndownloadedSizesPrefixSumLive() {
        return undownloadedSizesPrefixSumLive;
    }

    /**
     * Mirrors the 4 visual states of the per-episode download icon in
     * PodcastEpisodeRVAdapter: green (downloaded + tracked in DB), orange (file on disk
     * but no DB row - orphan), blue (never downloaded), maroon (downloaded then deleted).
     */
    public static class EpisodeStatusCounts {
        public int downloadedTracked; // green
        public int orphanOnDisk; // orange
        public int neverDownloaded; // blue
        public int deleted; // maroon
        public int total;
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

            // Single query for all episodes - reused below for both the undownloaded count
            // and the status breakdown, instead of firing a DB/disk lookup per episode.
            List<Episode> all = episodeDao.getAllEpisodesForPodcastNewestFirst(podcast.getId());

            long[] prefixSums = new long[all.size()];
            int undownloaded = 0;
            for (Episode ep : all) {
                if (ep.idZikFile == null) {
                    long previous = (undownloaded == 0) ? 0L : prefixSums[undownloaded - 1];
                    prefixSums[undownloaded] = previous + Math.max(0L, ep.enclosureLength);
                    undownloaded++;
                }
            }
            undownloadedCountLive.postValue(undownloaded);
            undownloadedSizesPrefixSumLive.postValue(java.util.Arrays.copyOf(prefixSums, undownloaded));

            episodeStatusCountsLive.postValue(computeEpisodeStatusCounts(context, podcast, all));
        });
    }

    // idZikFile/date_delete are already-loaded Episode columns, so tracked/deleted are just a
    // field check - no per-episode DB query needed. Only "on disk but not tracked" (orphan) vs
    // "never downloaded" needs the filesystem, so that's resolved via one directory listing per
    // location (internal/SD card) instead of a per-episode File.exists() check.
    private EpisodeStatusCounts computeEpisodeStatusCounts(Context context, Podcast podcast, List<Episode> all) {
        EpisodeStatusCounts counts = new EpisodeStatusCounts();
        counts.total = all.size();

        List<Episode> candidates = new ArrayList<>();
        for (Episode ep : all) {
            if (ep.idZikFile != null) {
                counts.downloadedTracked++;
            } else if (ep.date_delete != null) {
                counts.deleted++;
            } else {
                candidates.add(ep);
            }
        }

        if (!candidates.isEmpty()) {
            Set<String> filesOnDisk = new HashSet<>();
            addFileNames(filesOnDisk, PodcastHelper.buildPodcastPath(context, podcast.title, false));
            addFileNames(filesOnDisk, PodcastHelper.buildPodcastPath(context, podcast.title, true));

            for (Episode ep : candidates) {
                String fileName = PodcastHelper.buildPodcastEpisodeFileName(DisplayableEpisode.fromDatabaseEpisode(ep));
                if (filesOnDisk.contains(fileName)) {
                    counts.orphanOnDisk++;
                } else {
                    counts.neverDownloaded++;
                }
            }
        }
        return counts;
    }

    private static void addFileNames(Set<String> out, File dir) {
        if (dir == null || !dir.isDirectory()) return;
        String[] names = dir.list();
        if (names != null) {
            Collections.addAll(out, names);
        }
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
