package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.LibrivoxResultRVAdapter;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.objects.LibrivoxApiResponse;
import com.driot.bookplayer.objects.LibrivoxApi;
import com.driot.bookplayer.objects.LibrivoxItem;
import com.driot.bookplayer.objects.OngoingTaskHost;
import com.driot.bookplayer.utils.log.LoggingActivity;

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
    LibrivoxResultRVAdapter adapter;

    ProgressBar progressBar;

    public static final String API_SORT = "downloads desc";

    private LibrivoxResultsViewModel viewModel; // ✅ ADDED

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_librivox_results);

        InsetHelper.applyTopInsetsTo(this, findViewById(R.id.rootLayout));
        InsetHelper.applyBottomInsetsForScrollable(this, findViewById(R.id.recyclerView));

        OngoingTaskHost.attach(
                this,
                R.id.topOverlayContainer,
                new Intent(this, AddResourceActivity.class)); // tap => open details


        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);

        int span = getResources().getInteger(R.integer.classic_grid_span);
        GridLayoutManager glm = new GridLayoutManager(this, span);
        recyclerView.setLayoutManager(glm);
        recyclerView.addItemDecoration(new ViewHelper.SpacesItemDecoration(ViewHelper.dp(this, Var.GRID_LAYOUT_SPACER)));

        adapter = new LibrivoxResultRVAdapter(new LibrivoxResultRVAdapter.OnItemClickListener() {
            @Override public void onItemClick(LibrivoxItem item) {
                Intent intent = new Intent(LibrivoxResultsActivity.this, LibrivoxDetailActivity.class);
                intent.putExtra("identifier", item.identifier);
                intent.putExtra("title", item.title);
                startActivity(intent);
            }
            @Override public void onFavoriteClick(LibrivoxItem item) {
                myLogI("------- user clicks favorite ------   for [" + item.identifier + "]");
                viewModel.toggleFavorite(item);
            }
        });
        recyclerView.setAdapter(adapter);

        // ✅ INIT VIEWMODEL
        viewModel = new ViewModelProvider(this).get(LibrivoxResultsViewModel.class);

        // ✅ OBSERVE RESULTS
        viewModel.getResults().observe(this, items -> {
            adapter.setItems(items);
            progressBar.setVisibility(View.GONE);
            String strResultsCount;
            if (items != null && items.size() == Option.getLibrivoxApiNbResults()) {
                strResultsCount = getString(R.string.max_number_of_results_reached) + " (" + items.size() + ")";
            } else {
                strResultsCount =  getString((R.string.nb_of_audios_found)) + " : " + (items == null ? 0 : items.size());
            }
            adapter.setHeaderCount(strResultsCount);
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

        CharSequence searchLine = getString(R.string.Search_2pt)
                + (query.isEmpty() ? getString(R.string.search_nothing_specified) : query);
        CharSequence langLine = getString(R.string.Language_2pt) + lang;
        adapter.setHeader(searchLine, langLine);
        adapter.setHeaderCount(getString(R.string.Results_2pt) + "...");


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
        logging.setLevel(Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL);

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

        String finalFullQuery = fullQuery;
        api.search(fullQuery, fields, Option.getLibrivoxApiNbResults(), 1, "json", API_SORT).enqueue(new Callback<LibrivoxApiResponse>() {
            @Override
            public void onResponse(Call<LibrivoxApiResponse> call, Response<LibrivoxApiResponse> response) {
                //myLog(response.toString());
                progressBar.setVisibility(View.GONE);
                if (response.body() != null && response.body().response != null) {
                    List<LibrivoxItem> results = response.body().response.docs;
                    if (results.isEmpty()) {
                        myToast("[" + lang + "] " + getString(R.string.librivox_no_audiobook_found_for_search) + " [" + query + "]");
                        viewModel.requestFinish();
                    } else {
                        viewModel.enrichWithLocalState(results); // ← merge API + favorites
                        myLog(results.size() + " results found");
                    }
                } else {
                    myLogEE(null, "invalid response body from librivox - " + finalFullQuery);
                    myToastE(getString(R.string.librivox_invalid_response));
                    viewModel.requestFinish(); // ✅ trigger finish
                }
            }

            @Override
            public void onFailure(Call<LibrivoxApiResponse> call, Throwable t) {
                if (NetworkHelper.isUnknownHost(t)) {
                    myToastE(getString(R.string.no_internet_connection));
                } else {
                    myLogEE(t, "librivox api search on Failure - " + finalFullQuery);
                    myToastEE(t, getString(R.string.an_error_occurred));
                }
                progressBar.setVisibility(View.GONE);
                viewModel.requestFinish(); // ✅ trigger finish
            }
        });
    }

}
