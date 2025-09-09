package com.driot.bookplayer.objects;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface PodcastIndexApi {

    @GET("search/byterm")
    Call<PodcastIndexResponse> searchPodcasts(
            @Query("q") String query,
            @Query("max") int max,
            @Query("lang") String language
    );


    @GET("podcasts/trending")
    Call<PodcastIndexResponse> getTrendingPodcasts(
            @Query("lang") String lang,
            @Query("max") int max
    );


    @GET("episodes/byfeedid")
    Call<PodcastEpisodeResponse> getEpisodesByFeedId(
            @Query("id") long feedId,
            @Query("since") long since,
            @Query("max") int max,
            @Query("fulltext") boolean fulltext
    );

    @GET("episodes/byfeedid")
    Call<PodcastEpisodeResponse> getLastEpisodesByFeedId(
            @Query("id") long feedId,
            @Query("since") long since,
            @Query("max") int max,
            @Query("newest") boolean newest
    );

}
