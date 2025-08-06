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
import com.driot.bookplayer.objects.PodcastEpisode;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.util.ArrayList;
import java.util.List;

public class PodcastEpisodeViewModel extends LoggingAndroidViewModel {
    private final ZikFileDao zikFileDao;
    private final EpisodeDao episodeDao;
    private final PodcastDao podcastDao;

    public PodcastEpisodeViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getDatabase(application);
        zikFileDao = db.ZikFileDao();
        episodeDao = db.EpisodeDao();
        podcastDao = db.PodcastDao();
    }

    public LiveData<ZikFile> getZikFileLive(String folderName, String fileName) {
        return zikFileDao.getZikFileLive(folderName, fileName);
    }

    public LiveData<Podcast> getPodcastLiveByFeedId(long feedId) {
        return AppDatabase.getDatabase(getApplication()).PodcastDao().getPodcastLiveByFeedId(feedId);
    }


    public void insertEpisodes(List<PodcastEpisode> podcastEpisodes, long podcastFeedId) {
        new Thread(() -> {
            int podcastId = podcastDao.getPodcastByFeedId(podcastFeedId).getId();
            List<Episode> toSave = PodcastHelper.convertToEpisodes(podcastEpisodes, podcastId);
            episodeDao.insertAll(toSave);
        }).start();
    }


}
