package com.driot.bookplayer.radio;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface RadioBrowserApi {

    // ---- Discovery ----
    @GET("json/servers")
    Call<List<ServerInfo>> getServers();  // mirror list

    // ---- Station search ----
    // https://<mirror>.api.radio-browser.info/json/stations/search?name=...&tag=...&countrycode=...&language=...&order=...&reverse=true&limit=...&hidebroken=true
    @GET("json/stations/search")
    Call<List<Station>> searchStations(
            @Query("name") String name,
            @Query("tag") String tag,
            @Query("countrycode") String countryCode,
            @Query("language") String language,
            @Query("order") String order,     // e.g. "clickcount"
            @Query("reverse") boolean reverse,
            @Query("limit") int limit,
            @Query("hidebroken") boolean hideBroken
    );

    // ---- Lists ----
    @GET("json/stations/topvote")
    Call<List<Station>> topVoted(
            @Query("limit") int limit,
            @Query("hidebroken") boolean hideBroken
    );

    // ---- Lists ----
    @GET("json/stations/bytag/{tag}")
    Call<List<Station>> byTag(
            @Path("tag") String tag,
            @Query("limit") int limit,
            @Query("order") String order,
            @Query("hidebroken") boolean hideBroken
    );

    @GET("json/stations/byname/{name}")
    Call<List<Station>> byName(
            @Path("name") String name,
            @Query("limit") int limit,
            @Query("order") String order,
            @Query("hidebroken") boolean hideBroken
    );


    // ---- “Click + resolve” recommended path ----
    @GET("json/url/{stationuuid}")
    Call<UrlResolve> resolveUrl(@Path("stationuuid") String stationUuid);

    @GET("json/tags")
    Call<List<TagItem>> getTags(
            @Query("order") String order,        // "stationcount"
            @Query("reverse") boolean reverse,   // true
            @Query("limit") int limit            // e.g. 18
    );
}
