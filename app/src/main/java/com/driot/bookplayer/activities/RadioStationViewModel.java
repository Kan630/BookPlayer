package com.driot.bookplayer.activities;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.radio.RadioStation;
import com.driot.bookplayer.radio.RadioStationDao;

public class RadioStationViewModel extends AndroidViewModel {

    private final RadioStationDao radioStationDao;
    private LiveData<RadioStation> radioStationLiveData;

    public RadioStationViewModel(@NonNull Application app) {
        super(app);
        radioStationDao = AppDatabase.getInstance(app).radioStationDao();
    }

    public void loadStation(String stationUuid) {
        radioStationLiveData = radioStationDao.getLiveDataByUuid(stationUuid);
    }

    public LiveData<RadioStation> getStation() {
        return radioStationLiveData;
    }
}
