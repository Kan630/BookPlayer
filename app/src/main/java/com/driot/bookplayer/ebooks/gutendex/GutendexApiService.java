// GutendexApiService.java
package com.driot.bookplayer.ebooks.gutendex;

import com.driot.bookplayer.BuildConfig;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Url;

public interface GutendexApiService {

    // Main search endpoint: /books?search=...&languages=en,fr&page=1
    @GET("books")
    Call<GutendexResponse> searchBooks(
            @Query("search") String search,
            @Query("languages") String languages,
            @Query("topic") String topic,
            @Query("mime_type") String mimeType,
            @Query("page") Integer page);

    // Single book by id: /books/{id}
    @GET("books/{id}")
    Call<GutendexBook> getBook(@Path("id") int id);

    // Follow "next"/"previous" absolute URLs from API
    @GET
    Call<GutendexResponse> getPage(@Url String absoluteUrl);
}
