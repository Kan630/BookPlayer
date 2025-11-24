package com.driot.bookplayer.ebooks;

import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.ebooks.gutendex.GutendexApiService;
import com.driot.bookplayer.ebooks.gutendex.GutendexMapper;
import com.driot.bookplayer.ebooks.gutendex.GutendexResponse;
import com.driot.bookplayer.ebooks.gutendex.GutendexBook;
import com.driot.bookplayer.ebooks.gutendex.GutendexPerson;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class GutendexTestActivity extends LoggingActivity {

    private GutendexApiService api;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gutendex_test);

        // --- Build Retrofit ---
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(GutendexApiService.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        api = retrofit.create(GutendexApiService.class);

        // --- UI button ---
        Button btn = findViewById(R.id.btnTestGutendex);
        btn.setOnClickListener(v -> testSearch());
    }

    private void testSearch() {
        myLog("=== Gutendex Test: Searching \"tolstoy\" ===");

        Call<GutendexResponse> call = api.searchBooks(
                "tolstoy",                 // search
                "en",                      // languages
                null,                      // topic
                "application/epub+zip",    // epub only
                null                       // page
        );

        call.enqueue(new Callback<GutendexResponse>() {
            @Override
            public void onResponse(Call<GutendexResponse> call, Response<GutendexResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    myLogEE(null, "Gutendex error HTTP=" + response.code());
                    return;
                }

                GutendexResponse resp = response.body();
                myLog("Total results on server: " + resp.count);
                myLog("Next page: " + resp.next);

                List<GutendexBook> books = resp.results;
                if (books == null || books.isEmpty()) {
                    myLog("No books returned.");
                    return;
                }

                myLog("Books returned in page: " + books.size());

                for (GutendexBook b : books) {
                    myLog("-------------------------------------");
                    myLog("ID: " + b.id);
                    myLog("Title: " + b.title);

                    String authors = GutendexMapper.buildAuthorLine(b);
                    myLog("Authors: " + authors);

                    myLog("Languages: " + b.languages);
                    myLog("Downloads: " + b.download_count);

                    String epubUrl = GutendexMapper.findBestEpubUrl(b);
                    String coverUrl = GutendexMapper.findCoverUrl(b);

                    myLog("Chosen EPUB URL: " + epubUrl);
                    myLog("Chosen cover URL: " + coverUrl);
                }

                // Optional: log quickly what you would download first
                GutendexBook first = books.get(0);
                String firstEpub = GutendexMapper.findBestEpubUrl(first);
                myLog("=== FIRST BOOK TO DOWNLOAD ===");
                myLog("Title: " + first.title);
                myLog("EPUB: " + firstEpub);
            }

            @Override
            public void onFailure(Call<GutendexResponse> call, Throwable t) {
                myLogEE(t, "Gutendex request FAILED: " + t.getMessage());
            }
        });
    }
}
