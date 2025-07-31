package com.driot.bookplayer.activities;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.db.ZikFileDao;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

public class PodcastEpisodeViewModel extends LoggingAndroidViewModel {
    private final ZikFileDao zikFileDao;

    public PodcastEpisodeViewModel(@NonNull Application application) {
        super(application);
        zikFileDao = AppDatabase.getDatabase(application).ZikFileDao();
    }

    public LiveData<ZikFile> getZikFileLive(String folderName, String fileName) {
        return zikFileDao.getZikFileLive(folderName, fileName);
    }

    public LiveData<Podcast> getPodcastLiveByFeedId(long feedId) {
        return AppDatabase.getDatabase(getApplication()).PodcastDao().getPodcastLiveByFeedId(feedId);
    }
}
