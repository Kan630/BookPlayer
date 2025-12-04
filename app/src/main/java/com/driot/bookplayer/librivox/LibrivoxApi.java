package com.driot.bookplayer.librivox;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface LibrivoxApi {
    @GET("advancedsearch.php")
    Call<LibrivoxApiResponse> search(@Query("q") String query
            , @Query("fl[]") List<String> fields
            , @Query("rows") int rows
            , @Query("page") int page
            , @Query("output") String output
            , @Query("sort") String sort
    );

    @GET("metadata/{identifier}")
    Call<ItemMetadata> getItemMetadata(@Path("identifier") String identifier);
}