package com.driot.bookplayer.activities;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Episode;
import com.driot.bookplayer.db.EpisodeDao;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.PodcastDao;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.db.ZikFileDao;
import com.driot.bookplayer.helpers.PodcastHelper;
import com.driot.bookplayer.objects.DisplayableEpisode;
import com.driot.bookplayer.objects.PodcastEpisode;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.util.List;

public class PodcastEpisodeViewModel extends LoggingAndroidViewModel {
    private final ZikFileDao zikFileDao;
    private final EpisodeDao episodeDao;
    private final PodcastDao podcastDao;

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
    public List<Episode> getEpisodesFromDB(int podcastId, boolean sortNewestFirst) {
        if (sortNewestFirst) {
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
