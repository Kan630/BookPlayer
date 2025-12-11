package com.driot.bookplayer.activities;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.radio.RadioBrowserRepository;
import com.driot.bookplayer.radio.RadioStation;
import com.driot.bookplayer.radio.RadioStationDao;
import com.driot.bookplayer.radio.Station;
import com.driot.bookplayer.radio.VoteResponse;
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
                Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL
        );
        repo.searchByUuid(stationUuid, new Callback<List<Station>>() {
            @Override
            public void onResponse(Call<List<Station>> call, Response<List<Station>> response) {
                myLogD("refresh station from API - get details - success = " + response.code() + " / " + response.message() + " /");
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Station apiStation = response.body().get(0);

                    //myLog(apiStation.toString().replace(", ", "\n"));

                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        RadioStationDao dao = AppDatabase.getInstance(getApplication()).radioStationDao();
                        RadioStation dbStation = dao.findByUuid(apiStation.stationuuid);

                        if (dbStation == null) {
                            dao.insert(RadioStation.fromStation(apiStation, apiStation.url_resolved));
                        } else {
                            dbStation.url_resolved = apiStation.url_resolved;
                            dbStation.name         = apiStation.name;
                            dbStation.url          = apiStation.url;
                            dbStation.codec        = apiStation.codec;
                            dbStation.bitrate      = apiStation.bitrate;
                            dbStation.hls          = apiStation.hls;
                            dbStation.favicon      = apiStation.favicon;
                            dbStation.country      = apiStation.country;
                            dbStation.countrycode  = apiStation.countrycode;
                            dbStation.language     = apiStation.language;
                            dbStation.tags         = apiStation.tags;
                            dbStation.clickcount   = apiStation.clickcount;
                            dbStation.lastcheckok  = apiStation.lastcheckok;
                            dbStation.state        = apiStation.state;
                            dbStation.iso_3166_2   = apiStation.iso_3166_2;
                            dbStation.votes        = apiStation.votes;
                            dbStation.homepage     = apiStation.homepage;
                            dbStation.date_maj     = System.currentTimeMillis();

                            dao.update(dbStation);
                        }
                    });
                } else {
                    myLogE("empty response body");
                }
            }

            @Override
            public void onFailure(Call<List<Station>> call, Throwable t) {
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
                Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL
        );

        repo.vote(stationUuid, new Callback<VoteResponse>() {
            @Override
            public void onResponse(Call<VoteResponse> call, Response<VoteResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    VoteResponse vr = response.body();
                    myLog("Vote success: ok=" + vr.ok + " message=" + vr.message);

                    if (!vr.ok) {
                        // server says vote failed (rate limit etc)
                        if (vr.message != null && vr.message.toLowerCase().contains("same station")) {
                            myToast("10 min. cooldown - You voted too often for the same station — try again later.");
                        } else {
                            myToast("Vote rejected: " + vr.message);
                        }
                    } else {
                        myToast("Vote sent to server, thank you.");
                        // Optionally: refresh station details from API to update vote count in DB/UI
                        refreshStationFromApi(stationUuid);
                    }
                } else {
                    myLogE("Vote failed, empty response or HTTP error: " + (response != null ? response.code() : "null"));
                    myToast("Error sending vote to server.");
                }
            }

            @Override
            public void onFailure(Call<VoteResponse> call, Throwable t) {
                myLogW("Vote API failed: " + t);
                myToast("Error sending vote to server.");
            }
        });
    }


}
