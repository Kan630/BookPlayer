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

    // ---- ApiStation search ----
    // https://<mirror>.api.radio-browser.info/json/stations/search?name=...&tag=...&countrycode=...&language=...&order=...&reverse=true&limit=...&hidebroken=true
    @GET("json/stations/search")
    Call<List<ApiStation>> searchStations(
            @Query("name") String name,
            @Query("tag") String tag,
            @Query("countrycode") String countryCode,
            @Query("language") String language,
            @Query("order") String order,     // e.g. "clickcount"
            @Query("reverse") boolean reverse,
            @Query("limit") int limit,
            @Query("offset") Integer offset,
            @Query("hidebroken") boolean hideBroken
    );

    @GET("json/stations/byname/{name}")
    Call<List<ApiStation>> byName(
            @Path("name") String name,
            @Query("limit") int limit,
            @Query("offset") Integer offset,
            @Query("order") String order,
            @Query("hidebroken") boolean hideBroken
    );

    @GET("json/stations/byuuid")
    Call<List<ApiStation>> searchByUuid(
            @Query("uuids") String stationUuid
    );

    // ---- “Click + resolve” recommended path ----
    @GET("json/url/{stationuuid}")
    Call<UrlResolve> resolveUrl(@Path("stationuuid") String stationUuid);


    // ---- Lists ----
    @GET("json/stations/topclick")
    Call<List<ApiStation>> topClicked(
            @Query("limit") int limit,
            @Query("offset") Integer offset,
            @Query("hidebroken") boolean hideBroken
    );

    @GET("json/stations/topvote")
    Call<List<ApiStation>> topVoted(
            @Query("limit") int limit,
            @Query("offset") Integer offset,
            @Query("hidebroken") boolean hideBroken
    );

    @GET("json/stations/lastclick")
    Call<List<ApiStation>> lastClicked(
            @Query("limit") int limit,
            @Query("offset") Integer offset,
            @Query("hidebroken") boolean hideBroken
    );

    @GET("json/stations/lastchange")
    Call<List<ApiStation>> lastAddedChanged(
            @Query("limit") int limit,
            @Query("offset") Integer offset,
            @Query("hidebroken") boolean hideBroken
    );

    @GET("json/vote/{uuid}")
    Call<VoteResponse> vote(@Path("uuid") String uuid);


    // ---- Lists ----
    @GET("json/stations/bytag/{tag}")
    Call<List<ApiStation>> byTag(
            @Path("tag") String tag,
            @Query("limit") int limit,
            @Query("offset") Integer offset,
            @Query("order") String order,
            @Query("hidebroken") boolean hideBroken
    );
    @GET("json/stations/bycountry/{country}")
    Call<List<ApiStation>> byCountry(
            @Path("country") String country,
            @Query("limit") int limit,
            @Query("offset") Integer offset,
            @Query("order") String order,
            @Query("hidebroken") boolean hideBroken
    );
    @GET("json/stations/bylanguage/{language}")
    Call<List<ApiStation>> byLanguage(
            @Path("language") String language,
            @Query("limit") int limit,
            @Query("offset") Integer offset,
            @Query("order") String order,
            @Query("hidebroken") boolean hideBroken
    );

    @GET("json/stations/bylanguageexact/{language}")
    Call<List<ApiStation>> byLanguageExact(
            @Path("language") String language,
            @Query("limit") int limit,
            @Query("offset") Integer offset,
            @Query("order") String order,
            @Query("hidebroken") boolean hideBroken
    );


    @GET("json/tags")
    Call<List<TagItem>> getTags(
            @Query("order") String order,        // "stationcount"
            @Query("reverse") boolean reverse,   // true
            @Query("limit") int limit            // e.g. 18
    );
    @GET("json/countries")
    Call<List<TagItem>> getCountries(
            @Query("order") String order,        // "stationcount"
            @Query("reverse") boolean reverse,   // true
            @Query("limit") int limit            // e.g. 18
    );
    @GET("json/languages")
    Call<List<TagItem>> getLanguages(
            @Query("order") String order,        // "stationcount"
            @Query("reverse") boolean reverse,   // true
            @Query("limit") int limit            // e.g. 18
    );
}
