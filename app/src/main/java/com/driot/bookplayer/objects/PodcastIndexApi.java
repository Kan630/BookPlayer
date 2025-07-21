package com.driot.bookplayer.objects;

import static com.driot.bookplayer.global.Var.PODCASTINDEXORG_API_KEY;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Query;

public interface PodcastIndexApi {
    @Headers({
            "User-Agent: BookPlayer/1.0",
            "X-Auth-Key: " + PODCASTINDEXORG_API_KEY, // Replace with your real keys
            "X-Auth-Date: PLACEHOLDER"       // Will be replaced dynamically
    })
    @GET("search/byterm")
    Call<PodcastIndexResponse> searchPodcasts(
            @Query("q") String query,
            @Query("max") int max,
            @Query("lang") String language
    );

    /*
    Call<PodcastIndexResponse> searchByTerm(
            @Query("q") String query
    );

     */

}
