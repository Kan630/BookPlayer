package com.driot.bookplayer.objects;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface PodcastIndexApi {
    @GET("api/1.0/search/byterm")
    Call<PodcastIndexResponse> searchPodcasts(
            @Query("q") String query,
            @Query("max") int max,
            @Query("lang") String language
    );
}
