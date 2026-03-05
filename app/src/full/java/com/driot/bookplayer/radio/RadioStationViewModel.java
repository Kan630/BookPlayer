package com.driot.bookplayer.radio;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.RadioStation;
import com.driot.bookplayer.db.RadioStationDao;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.utils.log.LoggingAndroidViewModel;

import java.util.List;

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
        RadioBrowserRepository repo = new RadioBrowserRepository(
                getApplication(),
                false,
                Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL);
        repo.searchByUuid(stationUuid, new Callback<List<ApiStation>>() {
            @Override
            public void onResponse(Call<List<ApiStation>> call, Response<List<ApiStation>> response) {
                myLogD("refresh station from API - get details - success = " + response.code() + " / "
                        + response.message() + " /");
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    ApiStation apiStation = response.body().get(0);

                    // myLog(apiStation.toString().replace(", ", "\n"));

                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        RadioHelper.upsertFromApi(getApplication(), apiStation, apiStation.url_resolved);
                    });
                } else {
                    myLogE("empty response body");
                }
            }

            @Override
            public void onFailure(Call<List<ApiStation>> call, Throwable t) {
                myLogW("refreshStationFromApi station fetch failed: " + t);
            }
        });
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
