package com.driot.bookplayer.activities;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.driot.bookplayer.db.ItemMetadata;

public class LibrivoxDetailViewModel extends AndroidViewModel {

    public final MutableLiveData<ItemMetadata> metadata = new MutableLiveData<>();
    public final MutableLiveData<Long> zipFileSizeBytes = new MutableLiveData<>();
    public final MutableLiveData<Boolean> zipExists = new MutableLiveData<>();

    public String identifier;
    public String title;

    public LibrivoxDetailViewModel(@NonNull Application application) {
        super(application);
    }
}
