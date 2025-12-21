package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.EbookResultRVAdapter;
import com.driot.bookplayer.ebooks.EbookItem;
import com.driot.bookplayer.ebooks.gutendex.GutendexApiService;
import com.driot.bookplayer.ebooks.gutendex.GutendexBook;
import com.driot.bookplayer.ebooks.gutendex.GutendexMapper;
import com.driot.bookplayer.ebooks.gutendex.GutendexResponse;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.ViewHelper;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@AndroidEntryPoint
public class EbookResultsActivity extends BaseBottomNavActivity {

    RecyclerView recyclerView;
    ProgressBar progressBar;
    TextView tvEmptyMessage;

    EbookResultRVAdapter adapter;

    @Override protected int getNavId() { return R.id.nav_add; }
    @Override protected int getLayoutResId() { return R.layout.activity_ebook_results; }
    @Override protected boolean enableOngoingTaskOverlay() { return true; }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        recyclerView    = findViewById(R.id.recyclerView);
        progressBar     = findViewById(R.id.progressBar);
        tvEmptyMessage  = findViewById(R.id.tvEmptyMessage);

        int span = getResources().getInteger(R.integer.classic_grid_span);
        GridLayoutManager glm = new GridLayoutManager(this, span);
        recyclerView.setLayoutManager(glm);
        recyclerView.addItemDecoration(
                new ViewHelper.SpacesItemDecoration(ViewHelper.dp(this, Var.GRID_LAYOUT_SPACER))
        );

        adapter = new EbookResultRVAdapter(item -> {
            myLogI("User clicks ebook item id=[" + item.gutendexId + "] - title=[" + item.title + "]\nurl=[" + item.epubUrl + "]");

            Intent intent = new Intent(EbookResultsActivity.this, EbookDetailActivity.class);
            intent.putExtra("gutendex_id", item.gutendexId);
            intent.putExtra("title", item.title);
            intent.putExtra("authors", item.authors);
            intent.putExtra("language", item.language);
            intent.putExtra("downloads", item.downloadCount);
            intent.putExtra("cover_url", item.coverUrl);
            intent.putExtra("epub_url", item.epubUrl);
            startActivity(intent);
        });

        recyclerView.setAdapter(adapter);

        String query = getIntent().getStringExtra("query");
        String lang  = getIntent().getStringExtra("lang");

        if (lang == null || lang.isEmpty()) {
            myLogE("EbookResultsActivity: missing/empty lang extra");
            finish();
            return;
        }
        if (query == null) query = "";

        // Header text, reuse your strings
        CharSequence searchLine = getString(R.string.Search_2pt)
                + (query.isEmpty() ? getString(R.string.search_nothing_specified) : query);
        CharSequence langLine = getString(R.string.Language_2pt) + lang;
        adapter.setHeader(searchLine, langLine);
        adapter.setHeaderCount(getString(R.string.Results_2pt) + "...");

        myLogD("EbookResultsActivity - query=[" + query + "], lang=[" + lang + "]");

        callGutendex(query, lang);
    }

    private void callGutendex(String query, String lang) {
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        tvEmptyMessage.setVisibility(View.GONE);

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(this::myLog);
        logging.setLevel(Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(GutendexApiService.BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        GutendexApiService api = retrofit.create(GutendexApiService.class);

        String searchParam = query.isEmpty() ? null : query;

        Call<GutendexResponse> call = api.searchBooks(
                searchParam,
                lang,                      // languages
                null,                      // topic
                "application/epub+zip",    // epub only
                null                       // first page
        );

        call.enqueue(new Callback<GutendexResponse>() {
            @Override
            public void onResponse(Call<GutendexResponse> call, Response<GutendexResponse> response) {
                progressBar.setVisibility(View.GONE);

                if (!response.isSuccessful() || response.body() == null) {
                    myLogEE(null, "Gutendex invalid response, HTTP=" + response.code());
                    tvEmptyMessage.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                    myToastE(getString(R.string.an_error_occurred));
                    return;
                }

                GutendexResponse resp = response.body();
                List<GutendexBook> books = resp.results;

                if (books == null || books.isEmpty()) {
                    myLog("Gutendex: no books found for [" + query + "] lang [" + lang + "]");
                    tvEmptyMessage.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                    adapter.setHeaderCount(getString(R.string.Results_2pt) + " 0");
                    return;
                }

                // LOG ALL BOOKS FOUND :
                //for (GutendexBook b : books) { myLogE(b.toString()); }

                List<EbookItem> mapped = new ArrayList<>();
                for (GutendexBook b : books) {
                    String epubUrl  = GutendexMapper.findBestEpubUrl(b);
                    if (epubUrl == null || epubUrl.isEmpty()) {
                        continue; // skip entries without EPUB
                    }
                    String coverUrl = GutendexMapper.findCoverUrl(b);

                    EbookItem item = new EbookItem();
                    item.gutendexId   = b.id;
                    item.title        = b.title;
                    item.authors      = GutendexMapper.buildAuthorLine(b);
                    item.language     = (b.languages != null && !b.languages.isEmpty())
                            ? b.languages.get(0) : "";
                    item.downloadCount = b.download_count;
                    item.coverUrl     = coverUrl;
                    item.epubUrl      = epubUrl;
                    item.isImported   = false; // for now

                    mapped.add(item);
                }

                if (mapped.isEmpty()) {
                    myLogW("Gutendex: all results filtered out (no EPUB).");
                    tvEmptyMessage.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                    adapter.setHeaderCount(getString(R.string.Results_2pt) + " 0");
                    return;
                }

                myLog("Gutendex: " + mapped.size() + " ebooks with EPUB found (total=" + resp.count + ")");
                adapter.setItems(mapped);

                String countText = getString(R.string.nb_of_audios_found) + " : " + mapped.size();
                adapter.setHeaderCount(countText);

                recyclerView.setVisibility(View.VISIBLE);
                tvEmptyMessage.setVisibility(View.GONE);
            }

            @Override
            public void onFailure(Call<GutendexResponse> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                myToastEE(t, getString(R.string.an_error_occurred));
                String errMsg = getString(R.string.an_error_occurred) + "\n" + t.getMessage();
                tvEmptyMessage.setText(errMsg);
                tvEmptyMessage.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            }
        });
    }
}
