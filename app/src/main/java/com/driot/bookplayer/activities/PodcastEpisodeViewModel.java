package com.driot.bookplayer.activities;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Episode;
import com.driot.bookplayer.db.EpisodeDao;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.db.ZikFileDao;
import com.driot.bookplayer.objects.PodcastEpisode;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.util.ArrayList;
import java.util.List;

public class PodcastEpisodeViewModel extends LoggingAndroidViewModel {
    private final ZikFileDao zikFileDao;
    private final EpisodeDao episodeDao;

    public PodcastEpisodeViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getDatabase(application);
        zikFileDao = db.ZikFileDao();
        episodeDao = db.EpisodeDao();
    }

    public LiveData<ZikFile> getZikFileLive(String folderName, String fileName) {
        return zikFileDao.getZikFileLive(folderName, fileName);
    }

    public LiveData<Podcast> getPodcastLiveByFeedId(long feedId) {
        return AppDatabase.getDatabase(getApplication()).PodcastDao().getPodcastLiveByFeedId(feedId);
    }


    public void insertEpisodes(List<PodcastEpisode> podcastEpisodes, long idPodcast) {
        new Thread(() -> {
            List<Episode> toSave = convertToEpisodes(podcastEpisodes, idPodcast);
            for (Episode ep : toSave) {
                myLogD("EpisodeInsertDebug - Inserting episode: idPodcast=" + ep.idPodcast + ", idZikFile=" + ep.idZikFile);
            }
            episodeDao.insertAll(toSave);
        }).start();
    }

    private List<Episode> convertToEpisodes(List<PodcastEpisode> podcastEpisodes, long idPodcast) {
        long now = System.currentTimeMillis();
        List<Episode> result = new ArrayList<>();
        for (PodcastEpisode pe : podcastEpisodes) {
            Episode ep = new Episode();
            ep.idPodcast = idPodcast;
            ep.date_add = now;
            // You could map more data from PodcastEpisode if needed
            result.add(ep);
        }
        return result;
    }
}
