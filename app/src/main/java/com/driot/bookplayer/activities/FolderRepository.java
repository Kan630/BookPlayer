package com.driot.bookplayer.activities;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.FolderDao;

import java.util.List;

public class FolderRepository {
    private final FolderDao dao;

    public FolderRepository(Context app) {
        this.dao = DatabaseClient.getInstance(app).getAppDatabase().folderDao();
    }

    public LiveData<List<Folder>> observeAll() {
        return dao.getAllLiveData(); // Room auto-updates
    }
}
