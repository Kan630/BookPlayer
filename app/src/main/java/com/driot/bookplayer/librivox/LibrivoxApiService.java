package com.driot.bookplayer.librivox;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface LibrivoxApiService {

    // Example:
    // https://librivox.org/api/feed/audiobooks/?genre=Action%20%26%20Adventure&extended=1&format=json&limit=50&offset=0
    @GET("api/feed/audiobooks/")
    Call<LibrivoxBooksResponse> getAudiobooks(
            @Query("language") String language,
            @Query("genre") String genre,      // nullable
            @Query("author") String author,    // nullable
            @Query("title") String title,      // nullable
            @Query("since") Long since,        // nullable unix ts
            @Query("extended") Integer extended,
            @Query("fields") String fields,    // e.g. "{id,title,language,genres,url_iarchive}"
            @Query("limit") Integer limit,
            @Query("offset") Integer offset,
            @Query("format") String format     // always "json"
    );
}
