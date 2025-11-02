package com.driot.bookplayer.objects.radio;

import android.content.Context;

import androidx.annotation.Nullable;

import java.util.List;

import okhttp3.HttpUrl;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public class RadioBrowserRepository {

    private final RadioBrowserApi api;

    public RadioBrowserRepository(Context ctx, boolean discoverMirrors, HttpLoggingInterceptor.Level logLevel) {
        Retrofit retrofit = RadioBrowserServiceFactory.createRetrofit(ctx, discoverMirrors, logLevel);
        this.api = retrofit.create(RadioBrowserApi.class);
    }

    public void search(
            @Nullable String name,
            @Nullable String tag,
            @Nullable String countryCode,
            @Nullable String language,
            int limit,
            Callback<List<Station>> cb
    ) {
        // sensible defaults: order by clickcount desc, hide broken
        api.searchStations(
                emptyIfNull(name),
                emptyIfNull(tag),
                emptyIfNull(countryCode),
                emptyIfNull(language),
                "clickcount",
                true,
                limit,
                true
        ).enqueue(new LoggingCallback<>(cb, "searchStations"));
    }


    public void topVoted(int limit, Callback<List<Station>> cb) {
        api.topVoted(limit, true).enqueue(new LoggingCallback<>(cb, "topVoted"));
    }

    /** Use this when the user taps Play: it increments click stats AND returns a fresh stream URL. */
    public void resolveUrl(String stationUuid, Callback<UrlResolve> cb) {
        api.resolveUrl(stationUuid).enqueue(new LoggingCallback<>(cb, "resolveUrl"));
    }


    private static String emptyIfNull(String s) { return s == null ? "" : s; }

    // Wraps to log success/failure while delegating to caller callback
    private static final class LoggingCallback<T> implements Callback<T> {
        private final Callback<T> delegate;
        private final String label;
        LoggingCallback(Callback<T> d, String l) { delegate = d; label = l; }
        @Override public void onResponse(Call<T> call, Response<T> resp) {
            myLog(label + " → " + resp.code());
            delegate.onResponse(call, resp);
        }
        @Override public void onFailure(Call<T> call, Throwable t) {
            myLogW(label + " failed: " + t);
            delegate.onFailure(call, t);
        }
    }

    public void getTopTags(int limit, Callback<List<TagItem>> cb) {
        api.getTags("stationcount", true, limit)
                .enqueue(new LoggingCallback<>(cb, "getTopTags"));
    }

}
