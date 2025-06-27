package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.ApiResponse;
import com.driot.bookplayer.db.LibrivoxApi;
import com.driot.bookplayer.db.LibrivoxItem;
import com.driot.bookplayer.utils.KanLogger;
import com.driot.bookplayer.adapter.LibrivoxResultAdapter;

import java.util.Arrays;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class LibrivoxResultsActivity extends AppCompatActivity {
    RecyclerView recyclerView;
    LibrivoxResultAdapter adapter;
    TextView tvSearchTerms, tvLanguage, tvResultsCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_librivox_results);

        recyclerView = findViewById(R.id.recyclerView);
        ProgressBar progressBar = findViewById(R.id.progressBar);
        tvSearchTerms = findViewById(R.id.tvSearchTerms);
        tvLanguage = findViewById(R.id.tvLanguage);
        tvResultsCount = findViewById(R.id.tvResultsCount);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new LibrivoxResultAdapter(item -> {
            // On item click, open detail activity
            Intent intent = new Intent(this, LibrivoxDetailActivity.class);
            intent.putExtra("identifier", item.identifier);
            intent.putExtra("title", item.title);
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);

        String query = getIntent().getStringExtra("query");
        String lang = getIntent().getStringExtra("lang");

        if (query==null || lang==null) {
            myLogE("bad arguments");
            return;
        }

        tvSearchTerms.setText("Search: " + query);
        tvLanguage.setText("Language: " + lang);
        tvResultsCount.setText("Results: ...");

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
            @Override
            public void log(@NonNull String message) {
                myLog(message);  // your custom method
            }
        });

        logging.setLevel(HttpLoggingInterceptor.Level.BODY); // Log full request/response

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://archive.org/")
                .client(client) // <--- custom OkHttp client with logging
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        LibrivoxApi api = retrofit.create(LibrivoxApi.class);

        List<String> fields = Arrays.asList("identifier", "title", "date", "avg_rating", "num_reviews");

        String normalizedQuery = query.toLowerCase().replace(",", "");
        String fullQuery = "collection:librivoxaudio AND language:(" + lang + ") AND (title:(" + normalizedQuery + ") OR creator:(" + normalizedQuery + "))";

        progressBar.setVisibility(View.VISIBLE);
        api.search(fullQuery, fields, 100, 1, "json").enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(Call<ApiResponse> call, Response<ApiResponse> response) {
                progressBar.setVisibility(View.GONE);
                if (response.body() != null && response.body().response != null) {
                    List<LibrivoxItem> results = response.body().response.docs;
                    if (results.isEmpty()) {
                        myToast("No [" + lang + "] audiobook found for search terms [" + query + "]");
                        finish();
                    } else {
                        myLog(results.size() + " results found for search terms [" + query + "] and language: " + lang);
                        adapter.setItems(results);
                        tvResultsCount.setText("Nb of audio found: " + results.size());
                    }
                } else {
                    myToastE("Invalid response");
                    finish();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                t.printStackTrace();
            }
        });
    }



    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogI(String str) { KanLogger.myLogI(this.getClass().getName(), str); }
    private void myToast(String str) { KanLogger.myToast(this.getClass().getName(), str); }
    private void myToastE(String str) { KanLogger.myToastE(this.getClass().getName(), str); }
}
