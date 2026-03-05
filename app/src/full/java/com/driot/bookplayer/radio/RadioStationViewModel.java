package com.driot.bookplayer.radio;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.RadioStation;
import com.driot.bookplayer.db.RadioStationDao;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RadioStationViewModel extends LoggingAndroidViewModel {

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

    public void refreshStationFromApi(String stationUuid) {
        RadioHelper.fetchAndUpsertStation(getApplication(), stationUuid);
    }

    public void toggleFavorite(@NonNull RadioStation station) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            station.isFavorite = !station.isFavorite;
            radioStationDao.update(station);
            myLog("toggleFavorite: " + station.name + " -> " + station.isFavorite);
        });
    }

    public void voteStation(@NonNull String stationUuid) {
        RadioBrowserRepository repo = new RadioBrowserRepository(
                getApplication(),
                false,
                Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL);

        repo.vote(stationUuid, new Callback<VoteResponse>() {
            @Override
            public void onResponse(Call<VoteResponse> call, Response<VoteResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    VoteResponse vr = response.body();
                    myLog("Vote success: ok=" + vr.ok + " message=" + vr.message);

                    if (!vr.ok) {
                        // server says vote failed (rate limit etc)
                        if (vr.message != null && vr.message.toLowerCase().contains("same station")) {
                            myToast(getApplication().getString(com.driot.bookplayer.R.string.vote_too_many));
                        } else {
                            myToast(getApplication().getString(com.driot.bookplayer.R.string.vote_rejected)
                                    + vr.message);
                        }
                    } else {
                        myToast(getApplication().getString(com.driot.bookplayer.R.string.vote_sent_success));
                        // Optionally: refresh station details from API to update vote count in DB/UI
                        refreshStationFromApi(stationUuid);
                    }
                } else {
                    myLogE("Vote failed, empty response or HTTP error: "
                            + (response != null ? response.code() : "null"));
                    myToast(getApplication().getString(com.driot.bookplayer.R.string.vote_send_error));
                }
            }

            @Override
            public void onFailure(Call<VoteResponse> call, Throwable t) {
                myLogW("Vote API failed: " + t);
                myToast(getApplication().getString(com.driot.bookplayer.R.string.vote_send_error));
            }
        });
    }

}
