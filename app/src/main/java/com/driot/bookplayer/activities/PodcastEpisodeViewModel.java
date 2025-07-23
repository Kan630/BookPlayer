package com.driot.bookplayer.activities;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.db.ZikFileDao;
import com.driot.bookplayer.utils.log.LoggingViewModel;

import java.util.List;

public class PodcastEpisodeViewModel extends LoggingViewModel {
    private final ZikFileDao zikFileDao;

    public PodcastEpisodeViewModel(@NonNull Application application) {
        super(application);
        zikFileDao = AppDatabase.getDatabase(application).ZikFileDao();
    }

    public LiveData<ZikFile> getZikFileLive(String folderName, String fileName) {
        return zikFileDao.getZikFileLive(folderName, fileName);
    }
}
