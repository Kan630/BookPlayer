package com.driot.bookplayer.radio;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.driot.bookplayer.db.AppDatabase;
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
        repo.searchByUuid(stationUuid, new Callback<List<Station>>() {
            @Override
            public void onResponse(Call<List<Station>> call, Response<List<Station>> response) {
                myLogD("refresh station from API - get details - success = " + response.code() + " / "
                        + response.message() + " /");
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Station apiStation = response.body().get(0);

                    // myLog(apiStation.toString().replace(", ", "\n"));

                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        RadioStationDao dao = AppDatabase.getInstance(getApplication()).radioStationDao();
                        RadioStation dbStation = dao.findByUuid(apiStation.stationuuid);

                        if (dbStation == null) {
                            dao.insert(RadioStation.fromStation(apiStation, apiStation.url_resolved));
                        } else {
                            dbStation.url_resolved = apiStation.url_resolved;
                            dbStation.name = apiStation.name;
                            dbStation.url = apiStation.url;
                            dbStation.codec = apiStation.codec;
                            dbStation.bitrate = apiStation.bitrate;
                            dbStation.hls = apiStation.hls;

                            // Protect local favicon with deep comparison
                            boolean isLocalFavicon = dbStation.favicon != null && !dbStation.favicon.startsWith("http");
                            if (isLocalFavicon) {
                                // Deep comparison: check if remote image is actually different
                                if (apiStation.favicon != null && !apiStation.favicon.isEmpty()) {
                                    byte[] remoteBytes = com.driot.bookplayer.helpers.NetworkHelper
                                            .fetchBytesWithHttpsFallbackForImage(apiStation.favicon);
                                    if (remoteBytes != null) {
                                        byte[] localBytes = null;
                                        try {
                                            java.io.File localFile = new java.io.File(dbStation.favicon);
                                            if (localFile.exists()) {
                                                java.io.InputStream in = new java.io.FileInputStream(localFile);
                                                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                                                byte[] buf = new byte[8192];
                                                int n;
                                                while ((n = in.read(buf)) != -1) {
                                                    out.write(buf, 0, n);
                                                }
                                                in.close();
                                                localBytes = out.toByteArray();
                                            }
                                        } catch (Exception e) {
                                            myLogW("Failed to read local favicon for comparison: " + e);
                                        }

                                        if (localBytes != null) {
                                            String remoteHash = com.driot.bookplayer.helpers.ImageHelper
                                                    .shortHash(remoteBytes);
                                            String localHash = com.driot.bookplayer.helpers.ImageHelper
                                                    .shortHash(localBytes);

                                            // Only update if content is different
                                            if (!remoteHash.equals(localHash)) {
                                                myLogW("Favicon changed (content diff), updating to remote: "
                                                        + apiStation.favicon);
                                                dbStation.favicon = apiStation.favicon;
                                            } else {
                                                myLogD("Favicon content identical, keeping local path.");
                                            }
                                        } else {
                                            // Local file issue, fallback to remote
                                            dbStation.favicon = apiStation.favicon;
                                        }
                                    }
                                }
                            } else {
                                // Not local, standard update
                                dbStation.favicon = apiStation.favicon;
                            }

                            if (apiStation.favicon != null && apiStation.favicon.startsWith("http")) {
                                dbStation.imageOriginalUrl = apiStation.favicon;
                            }

                            dbStation.country = apiStation.country;
                            dbStation.countrycode = apiStation.countrycode;
                            dbStation.language = apiStation.language;
                            dbStation.tags = apiStation.tags;
                            dbStation.clickcount = apiStation.clickcount;
                            dbStation.lastcheckok = apiStation.lastcheckok;
                            dbStation.state = apiStation.state;
                            dbStation.iso_3166_2 = apiStation.iso_3166_2;
                            dbStation.votes = apiStation.votes;
                            dbStation.homepage = apiStation.homepage;
                            dbStation.date_maj = System.currentTimeMillis();

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
