package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.LibrivoxResultAdapter;
import com.driot.bookplayer.objects.ApiResponse;
import com.driot.bookplayer.objects.LibrivoxApi;
import com.driot.bookplayer.objects.LibrivoxItem;

import java.util.Arrays;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class LibrivoxResultsActivity extends LoggingActivity {

    RecyclerView recyclerView;
    LibrivoxResultAdapter adapter;
    TextView tvSearchTerms, tvLanguage, tvResultsCount;

    ProgressBar progressBar;

    public static final String API_SORT = "downloads desc";
    public static final int API_MAX_RESULTS = 100;

    private LibrivoxResultsViewModel viewModel; // ✅ ADDED

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_librivox_results);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        tvSearchTerms = findViewById(R.id.tvSearchTerms);
        tvLanguage = findViewById(R.id.tvLanguage);
        tvResultsCount = findViewById(R.id.tvResultsCount);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new LibrivoxResultAdapter(item -> {
            Intent intent = new Intent(this, LibrivoxDetailActivity.class);
            intent.putExtra("identifier", item.identifier);
            intent.putExtra("title", item.title);
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);

        // ✅ INIT VIEWMODEL
        viewModel = new ViewModelProvider(this).get(LibrivoxResultsViewModel.class);

        // ✅ OBSERVE RESULTS
        viewModel.getResults().observe(this, items -> {
            adapter.setItems(items);
            progressBar.setVisibility(View.GONE);
            if (items != null && items.size() == API_MAX_RESULTS) {
                tvResultsCount.setText("Max number of results reached (" + items.size() + ")");
            } else {
                tvResultsCount.setText("Nb of audio found: " + (items == null ? 0 : items.size()));
            }
        });

        // ✅ OBSERVE FINISH REQUEST
        viewModel.getShouldFinish().observe(this, shouldFinish -> {
            if (shouldFinish != null && shouldFinish) finish();
        });

        // 🔄 GET SEARCH PARAMS
        String query = getIntent().getStringExtra("query");
        String lang = getIntent().getStringExtra("lang");

        if (query == null || lang == null || lang.isEmpty()) {
            myLogE("bad arguments");
            finish();
            return;
        }

        tvSearchTerms.setText("Search: " + (query.isEmpty() ? "Nothing Specified" : query));
        tvLanguage.setText("Language: " + lang);
        tvResultsCount.setText("Results: ...");

        // 🔄 Check cache
        if (viewModel.getResults().getValue() != null &&
                query.equals(viewModel.getLastQuery()) &&
                lang.equals(viewModel.getLastLang())) {
            myLogI("Using cached results");
            return;
        } else {
            myLogI("No cache, let's query again");
        }

        // ✅ Store last search
        viewModel.setLastQuery(query);
        viewModel.setLastLang(lang);

        // ✅ API call
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(this::myLog);
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://archive.org/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        LibrivoxApi api = retrofit.create(LibrivoxApi.class);

        List<String> fields = Arrays.asList("identifier", "title", "date", "avg_rating", "num_reviews");

        String fullQuery = "collection:librivoxaudio AND language:(" + lang + ")";
        if (!query.isEmpty()) {
            String normalizedQuery = query.toLowerCase().replace(",", "");
            fullQuery += " AND (title:(" + normalizedQuery + ") OR creator:(" + normalizedQuery + "))";
        }

        progressBar.setVisibility(View.VISIBLE);

        api.search(fullQuery, fields, API_MAX_RESULTS, 1, "json", API_SORT).enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(Call<ApiResponse> call, Response<ApiResponse> response) {
                progressBar.setVisibility(View.GONE);
                if (response.body() != null && response.body().response != null) {
                    List<LibrivoxItem> results = response.body().response.docs;
                    if (results.isEmpty()) {
                        myToast("[" + lang + "] " + getString(R.string.librivox_no_audiobook_found_for_search) + " [" + query + "]");
                        viewModel.requestFinish(); // ✅ trigger finish
                    } else {
                        viewModel.setResults(results); // ✅ store results
                        myLog(results.size() + " results found");
                    }
                } else {
                    myToastE(getString(R.string.librivox_invalid_response));
                    viewModel.requestFinish(); // ✅ trigger finish
                }
            }

            @Override
            public void onFailure(Call<ApiResponse> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                t.printStackTrace();
                viewModel.requestFinish(); // ✅ trigger finish
            }
        });
    }

}
