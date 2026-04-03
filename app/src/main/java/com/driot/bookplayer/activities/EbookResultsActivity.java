package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.EbookResultRVAdapter;
import com.driot.bookplayer.ebooks.gutendex.GutenbergLanguageItem;
import com.driot.bookplayer.ebooks.gutendex.GutenbergLanguageStore;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.LoadingProgressHelper;
import com.driot.bookplayer.helpers.ViewHelper;
import com.driot.bookplayer.librivox.LanguageMapper;
import com.driot.bookplayer.nav.FullActivity;

import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class EbookResultsActivity extends FullActivity {

    RecyclerView recyclerView;
    ProgressBar progressBar;
    TextView tvProgressMessage;
    TextView tvEmptyMessage;

    private EbookResultRVAdapter adapter;
    private EbookResultsViewModel viewModel;

    private final LoadingProgressHelper progressHelper = new LoadingProgressHelper();

    private String query;
    private String lang;
    private String topic;

    @Override
    protected int getNavSectionId() {
        return R.id.nav_add;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_ebook_results;
    }

    @Override
    protected boolean enableOngoingTaskOverlay() {
        return true;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetHelper.apply(this);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        tvProgressMessage = findViewById(R.id.tvProgressMessage);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);

        int span = getResources().getInteger(R.integer.classic_grid_span);
        GridLayoutManager glm = new GridLayoutManager(this, span);
        recyclerView.setLayoutManager(glm);
        recyclerView.addItemDecoration(
                new ViewHelper.SpacesItemDecoration(ViewHelper.dp(this, Var.GRID_LAYOUT_SPACER)));

        adapter = new EbookResultRVAdapter(this, item -> {
            myLogI("User clicks ebook item id=[" + item.gutendexId + "] - title=[" + item.title + "]\nurl=["
                    + item.epubUrl + "]");

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

        // Infinite scroll
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) return;

                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                if (viewModel.canLoadMore()) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5) {
                        viewModel.loadNextPage();
                    }
                }
            }
        });

        // Get params from Intent
        query = getIntent().getStringExtra("query");
        lang = getIntent().getStringExtra("lang");
        topic = getIntent().getStringExtra("topic");

        if (lang == null || lang.isEmpty()) {
            myLogE("EbookResultsActivity: missing/empty lang extra");
            finish();
            return;
        }
        if (query == null) query = "";
        if (topic == null) topic = "";

        // Header (always computed from intent — not affected by rotation)
        String searchLine = getString(R.string.Search_2pt)
                + (query.isEmpty() ? (topic.isEmpty() ? getString(R.string.most_downloaded) : topic) : query);
        String langLine = getLanguageDisplayName(lang);
        adapter.setHeader(searchLine, langLine);
        adapter.setHeaderCount(getString(R.string.Results_2pt) + "...");

        myLogD("EbookResultsActivity - query=[" + query + "], lang=[" + lang + "], topic=[" + topic + "]");

        // ViewModel — survives rotation and nav bar switches
        viewModel = new ViewModelProvider(this).get(EbookResultsViewModel.class);

        viewModel.getInitialLoading().observe(this, loading -> {
            if (Boolean.TRUE.equals(loading)) {
                progressBar.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
                tvEmptyMessage.setVisibility(View.GONE);
                adapter.setLoading(false);
                progressHelper.start(tvProgressMessage, new LoadingProgressHelper.MessageProvider() {
                    @NonNull
                    @Override
                    public String getInitialMessage() {
                        return getString(R.string.gutenberg_contacting);
                    }

                    @NonNull
                    @Override
                    public String getTickMessage(long elapsedSec) {
                        if (viewModel.isGutendexConnected()) {
                            return getString(R.string.wait_elapsed_connected,
                                    getString(R.string.gutenberg_connected),
                                    (int) elapsedSec, Var.GUTENDEX_READ_TIMEOUT_SEC);
                        } else {
                            return getString(R.string.wait_elapsed_connecting,
                                    getString(R.string.gutenberg_contacting),
                                    (int) elapsedSec, Var.GUTENDEX_CONNECT_TIMEOUT_SEC);
                        }
                    }
                });
            } else {
                progressBar.setVisibility(View.GONE);
                progressHelper.stop();
            }
        });

        viewModel.getItems().observe(this, newItems -> {
            if (newItems != null) {
                adapter.setItems(newItems);
                recyclerView.setVisibility(View.VISIBLE);
                tvEmptyMessage.setVisibility(View.GONE);
            }
        });

        viewModel.getHeaderCount().observe(this, count -> {
            if (count != null) adapter.setHeaderCount(count);
        });

        viewModel.getLoadingMore().observe(this, loading -> {
            adapter.setLoading(Boolean.TRUE.equals(loading));
        });

        viewModel.getEmptyMessage().observe(this, msg -> {
            if (msg != null) {
                tvEmptyMessage.setText(msg);
                tvEmptyMessage.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                tvEmptyMessage.setVisibility(View.GONE);
            }
        });

        viewModel.fetchIfNeeded(query, lang, topic);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        progressHelper.stop();
    }

    /**
     * Get the display name for a language code, formatted like LibriVox:
     * nativeName (name) if they differ, or just nativeName if they're the same.
     */
    private String getLanguageDisplayName(String langCode) {
        if (langCode == null || langCode.isEmpty()) {
            return getString(R.string.Language_2pt) + " " + langCode;
        }

        GutenbergLanguageStore store = new GutenbergLanguageStore(this);
        List<GutenbergLanguageItem> languages = store.loadLanguages(R.raw.gutenberg_languages);

        for (GutenbergLanguageItem langItem : languages) {
            if (langCode.equalsIgnoreCase(langItem.code2)) {
                String nativeName = langItem.nativeName != null && !langItem.nativeName.isEmpty()
                        ? langItem.nativeName
                        : langItem.name;
                String displayName = nativeName;
                if (!nativeName.equals(langItem.name)) {
                    displayName = nativeName + " (" + langItem.name + ")";
                }
                return getString(R.string.Language_2pt) + " " + displayName;
            }
        }

        // Fallback: LanguageMapper
        String langName = LanguageMapper.getNameFromTwoLetters(langCode);
        if (langName != null && !langName.equals(langCode)) {
            return getString(R.string.Language_2pt) + " " + langName;
        }

        // Last resort: just show the code
        return getString(R.string.Language_2pt) + " " + langCode;
    }
}
