// ZikFilesViewModel.java
package com.driot.bookplayer.activities;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.ZikFile;

import java.util.List;

public class ZikFilesViewModel extends AndroidViewModel {
    private final AppDatabase db;
    private LiveData<List<ZikFile>> liveList;

    public ZikFilesViewModel(@NonNull Application app) {
        super(app);
        db = AppDatabase.getDatabase(app);
    }

    public LiveData<List<ZikFile>> getZikFilesLive(int folderId) {
        if (liveList == null) {
            liveList = db.zikFileDao().getZikFilesLive(folderId);
        }
        return liveList;
    }
}
