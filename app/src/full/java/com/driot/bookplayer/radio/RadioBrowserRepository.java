package com.driot.bookplayer.radio;

import android.content.Context;

import androidx.annotation.Nullable;

import java.util.List;

import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import com.driot.bookplayer.global.Option;

public class RadioBrowserRepository {

    private final RadioBrowserApi api;          // direct → RadioBrowser (miroirs)
    @Nullable private final RadioBrowserApi cachedApi;
    
    public RadioBrowserRepository(
            Context ctx
            , boolean discoverMirrors
            , HttpLoggingInterceptor.Level logLevel
    ) {
        Retrofit retrofit = RadioBrowserServiceFactory.createRetrofit(ctx, discoverMirrors, logLevel);
        this.api = retrofit.create(RadioBrowserApi.class);

        if (Option.getRadioUseCloudflare()) {
            Retrofit cfRetrofit = RadioBrowserServiceFactory.createCloudflareRetrofit(logLevel);
            this.cachedApi = cfRetrofit.create(RadioBrowserApi.class);
        } else {
            this.cachedApi = null;
        }        
    }
    private RadioBrowserApi listsApi() {
        return (cachedApi != null) ? cachedApi : api;
    }

    public void search(
            @Nullable String name,
            @Nullable String tag,
            @Nullable String countryCode,
            @Nullable String language,
            int limit,
            Callback<List<ApiStation>> cb
    ) {
        search(name, tag, countryCode, language, limit, null, cb);
    }

    public void search(
            @Nullable String name,
            @Nullable String tag,
            @Nullable String countryCode,
            @Nullable String language,
            int limit,
            @Nullable Integer offset,
            Callback<List<ApiStation>> cb
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
                offset,
                true
        ).enqueue(new LoggingCallback<>(cb, "searchStations"));
    }

    public void vote(String uuid, Callback<VoteResponse> cb) {
        api.vote(uuid).enqueue(new LoggingCallback<VoteResponse>(cb, "vote"));
    }

    public void topVoted(int limit, @Nullable Integer offset, Callback<List<ApiStation>> cb) {
        listsApi().topVoted(limit, offset, true).enqueue(new LoggingCallback<>(cb, "topVoted"));
    }

    public void topClicked(int limit, @Nullable Integer offset, Callback<List<ApiStation>> cb) {
        listsApi().topClicked(limit, offset, true).enqueue(new LoggingCallback<>(cb, "topClicked"));
    }

    public void lastClicked(int limit, @Nullable Integer offset, Callback<List<ApiStation>> cb) {
        listsApi().lastClicked(limit, offset, true).enqueue(new LoggingCallback<>(cb, "lastClicked"));
    }

    public void lastChanged(int limit, @Nullable Integer offset, Callback<List<ApiStation>> cb) {
        listsApi().lastAddedChanged(limit, offset, true).enqueue(new LoggingCallback<>(cb, "lastAddedChanged"));
    }

    public void byTag(String tag, int limit, @Nullable Integer offset, Callback<List<ApiStation>> cb) {
        api.byTag(tag, limit, offset, "votes", true).enqueue(new LoggingCallback<>(cb, "byTag"));
    }

    public void byCountry(String country, int limit, @Nullable Integer offset, Callback<List<ApiStation>> cb) {
        api.byCountry(country, limit, offset, "votes", true).enqueue(new LoggingCallback<>(cb, "byCountry"));
    }

    public void byLanguageExact(String language, int limit, @Nullable Integer offset, Callback<List<ApiStation>> cb) {
        api.byLanguageExact(language, limit, offset, "votes", true)
                .enqueue(new LoggingCallback<>(cb, "byLanguageExact"));
    }

    public void byName(String name, int limit, @Nullable Integer offset, Callback<List<ApiStation>> cb) {
        api.byName(name, limit, offset, "votes", true).enqueue(new LoggingCallback<>(cb, "byName"));
    }

    // ---- LISTES (pays / tags / langues) → passent par Cloudflare si dispo ----

    public void getTopTags(int limit, Callback<List<TagItem>> cb) {
        listsApi()
                .getTags("stationcount", true, limit)
                .enqueue(new LoggingCallback<>(cb, "getTopTags"));
    }

    public void getTopCountries(int limit, Callback<List<TagItem>> cb) {
        listsApi()
                .getCountries("stationcount", true, limit)
                .enqueue(new LoggingCallback<>(cb, "getTopCountries"));
    }

    public void getTopLanguages(int limit, Callback<List<TagItem>> cb) {
        listsApi()
                .getLanguages("stationcount", true, limit)
                .enqueue(new LoggingCallback<>(cb, "getTopLanguages"));
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

    public void searchByUuid(String stationUuid, Callback<List<ApiStation>> cb) {
        api.searchByUuid(stationUuid).enqueue(new LoggingCallback<>(cb, "searchByUuid"));
    }
}
