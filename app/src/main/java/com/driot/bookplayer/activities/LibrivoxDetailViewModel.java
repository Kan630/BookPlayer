package com.driot.bookplayer.activities;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.librivox.ItemMetadata;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

public class LibrivoxDetailViewModel extends LoggingAndroidViewModel {

    public final MutableLiveData<ItemMetadata> metadata = new MutableLiveData<>();
    public final MutableLiveData<Long> zipFileSizeBytes = new MutableLiveData<>();
    public final MutableLiveData<String> download_link = new MutableLiveData<>();
    public final MutableLiveData<Folder> existingFolder = new MutableLiveData<>();

    public String identifier;
    public String title;

    public LibrivoxDetailViewModel(@NonNull Application application) {
        super(application);
    }
}
