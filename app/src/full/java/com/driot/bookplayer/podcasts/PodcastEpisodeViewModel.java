package com.driot.bookplayer.podcasts;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Episode;
import com.driot.bookplayer.db.EpisodeDao;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.PodcastDao;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.db.CommonZikFileDao;
import com.driot.bookplayer.db.ZikFileDao;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.util.List;

public class PodcastEpisodeViewModel extends LoggingAndroidViewModel {
    private final ZikFileDao zikFileDao;
    private final EpisodeDao episodeDao;
    private final PodcastDao podcastDao;

    private Boolean last_sort_newest_top;

    public PodcastEpisodeViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getDatabase(application);
        zikFileDao = db.zikFileDao();
        episodeDao = db.episodeDao();
        podcastDao = db.podcastDao();
    }



    // ---------------------------------
    //     DB
    // ---------------------------------

    //insert episodes gotten from api to db
    public void insertEpisodesInDB(List<PodcastEpisode> podcastEpisodes, long podcastFeedId) {
        new Thread(() -> {
            int podcastId = podcastDao.getPodcastByFeedId(podcastFeedId).getId();
            List<Episode> toSave = PodcastHelper.convertToEpisodes(podcastEpisodes, podcastId);
            episodeDao.insertAll(toSave);
        }).start();
    }
    public List<Episode> toggleSortAndGetEpisodesFromDB(int podcastId) {
        Podcast podcast = podcastDao.getById(podcastId);
        boolean sort_newest_top;
        if (podcast != null) {
            sort_newest_top = !podcast.sort_newest_top;
            podcast.sort_newest_top = sort_newest_top;
            podcastDao.update(podcast);
            myLogD("toggle sort, changed in DB,  sort_newest_top: " + sort_newest_top);
        } else {
            if (last_sort_newest_top==null) {
                sort_newest_top = Option.getPodcastEpisodesSortOrder();
                myLogD("toggle sort, Option tells sort_newest_top: " + sort_newest_top);
            } else {
                sort_newest_top = !last_sort_newest_top;
                last_sort_newest_top = sort_newest_top;
                myLogD("toggle sort, lastValue tells sort_newest_top: " + sort_newest_top);
            }
        }
        if (sort_newest_top) {
            return episodeDao.getAllEpisodesForPodcastNewestFirst(podcastId);
        } else {
            return episodeDao.getAllEpisodesForPodcastOldestFirst(podcastId);
        }
    }
    public List<Episode> getEpisodesFromDB(int podcastId) {
        Podcast podcast = podcastDao.getById(podcastId);
        boolean sort_newest_top;
        if (podcast != null) {
            sort_newest_top = podcast.sort_newest_top;
            myLogD("getEpisodesFromDB, DB tells sort_newest_top: " + sort_newest_top);
        } else {
            if (last_sort_newest_top == null) {
                sort_newest_top = Option.getPodcastEpisodesSortOrder();
                myLogD("getEpisodesFromDB, Option tells sort_newest_top: " + sort_newest_top);
            } else {
                sort_newest_top = !last_sort_newest_top;
                last_sort_newest_top = sort_newest_top;
                myLogD("getEpisodesFromDB, lastValue tells sort_newest_top: " + sort_newest_top);
            }
        }
        if (sort_newest_top) {
            return episodeDao.getAllEpisodesForPodcastNewestFirst(podcastId);
        } else {
            return episodeDao.getAllEpisodesForPodcastOldestFirst(podcastId);
        }
    }


    public LiveData<ZikFile> getZikFileLive(String folderName, String fileName) {
        return zikFileDao.getZikFileLive(folderName, fileName);
    }

    public LiveData<Podcast> getPodcastLiveByFeedId(long feedId) {
        return podcastDao.getPodcastLiveByFeedId(feedId);
    }

    public LiveData<ZikFile> getLastListenedZikFileForPodcast(long feedId) {

        LiveData<ZikFile> zf = zikFileDao.getLastListenedZikFileForPodcast(feedId);
        //myLogD("getLastListenedZikFileForPodcast : " + Objects.toString(zf.getValue() != null ? zf.getValue().getName() : null));
        return zf;
    }


    public Long getLastPublishedForPodcastSync(long podcastId) {
        return episodeDao.getMaxDatePublishedForPodcast(podcastId);
    }

    // ---------------------------------
    //     API
    // ---------------------------------

}
