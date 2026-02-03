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

    /**
     * Triggers Room LiveData invalidation for the Folder table so observers
     * (e.g. MainActivity folder list) get fresh data. Call from a background thread.
     */
    public void invalidateFolder(int folderId) {
        Folder f = dao.getById(folderId);
        if (f != null) {
            dao.updateLastAccess(folderId, f.lLastAccess);
        }
    }

    /**
     * Triggers Room LiveData invalidation when the list changed (e.g. folder deleted).
     * Call from a background thread.
     */
    public void invalidateFoldersList() {
        List<Folder> list = dao.getAll();
        if (!list.isEmpty()) {
            Folder first = list.get(0);
            dao.updateLastAccess(first.getId(), first.lLastAccess);
        }
    }
}
