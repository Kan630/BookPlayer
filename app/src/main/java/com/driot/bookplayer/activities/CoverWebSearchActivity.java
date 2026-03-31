package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.driot.bookplayer.R;
import com.driot.bookplayer.adapters.CoverResultAdapter;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.net.CoverSearchRepository;
import com.driot.bookplayer.objects.CoverResult;
import com.driot.bookplayer.services.DownloadCoverWorker;
import com.driot.bookplayer.utils.MsgBox;
import com.driot.bookplayer.utils.log.BaseActivity;
import java.util.List;
import java.util.concurrent.Executors;

public class CoverWebSearchActivity extends BaseActivity {
    public static final String EXTRA_FOLDER_ID = "folderId";
    public static final String EXTRA_DEFAULT_TITLE = "defaultTitle";
    public static final int MAX_NB_COVER_SEARCH_RESULT = 24;

    private static final int REQ_DOWNLOAD_COVER = 2001;
    private CoverResult pendingCoverResult;

    private long folderId;
    private EditText etQuery;
    private ProgressBar progressBar;
    private CoverResultAdapter adapter;
    private final CoverSearchRepository repo = new CoverSearchRepository(this);

    private ImageButton btnSearch;
    private final java.util.concurrent.ExecutorService searchExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean searching = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cover_web_search);
        InsetHelper.apply(this);

        folderId = getIntent().getLongExtra(EXTRA_FOLDER_ID, -1L);
        String defaultTitle = getIntent().getStringExtra(EXTRA_DEFAULT_TITLE);
        if (folderId <= 0) {
            finish();
            return;
        }

        etQuery = findViewById(R.id.etQuery);
        btnSearch = findViewById(R.id.btnSearch);
        progressBar = findViewById(R.id.progressBar);

        etQuery.setText(defaultTitle != null ? defaultTitle : "");

        RecyclerView rv = findViewById(R.id.rvResults);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CoverResultAdapter(this::onResultClicked);
        rv.setAdapter(adapter);

        // IME action on keyboard
        etQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch(etQuery.getText().toString().trim());
                return true;
            }
            return false;
        });

        // Button click
        btnSearch.setOnClickListener(v -> runSearch(etQuery.getText().toString().trim()));

        // Enable/disable button based on text present (optional)
        btnSearch.setEnabled(!TextUtils.isEmpty(etQuery.getText()));
        etQuery.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                btnSearch.setEnabled(s != null && !s.toString().trim().isEmpty() && !searching);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // Auto search on open if we have a title
        if (defaultTitle != null && !defaultTitle.isEmpty()) {
            runSearch(defaultTitle);
        }
    }

    private void setSearching(boolean isSearching) {
        searching = isSearching;
        progressBar.setVisibility(isSearching ? View.VISIBLE : View.GONE);
        btnSearch.setEnabled(!isSearching && !etQuery.getText().toString().trim().isEmpty());
        etQuery.setEnabled(!isSearching);
    }

    private void runSearch(String q) {
        if (q.isEmpty()) return;

        setSearching(true);
        TextView tvEmpty = findViewById(R.id.tvEmpty);
        tvEmpty.setVisibility(View.GONE);
        adapter.submit(java.util.Collections.emptyList());

        if (!NetworkHelper.isConnected(this)) {
            tvEmpty.setText(R.string.no_internet_connection);
            tvEmpty.setVisibility(View.VISIBLE);
            setSearching(false);
            return;
        }

        repo.searchAsync(this, q, MAX_NB_COVER_SEARCH_RESULT, new CoverSearchRepository.ResultCallback() {
            @Override
            public void onPartialResults(List<CoverResult> newResults) {
                runOnUiThread(() -> {
                    adapter.addResults(newResults);
                    tvEmpty.setVisibility(View.GONE);
                });
            }

            @Override
            public void onComplete() {
                runOnUiThread(() -> {
                    if (adapter.getItemCount() == 0) {
                        tvEmpty.setVisibility(View.VISIBLE);
                    }
                    setSearching(false);
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        searchExecutor.shutdownNow();
    }

    private void onResultClicked(CoverResult r) {
        pendingCoverResult = r;
        MsgBox.ask(this,
                getString(R.string.confirm),
                getString(R.string.use_this_image_as_cover),
                null,
                getString(android.R.string.ok),
                getString(android.R.string.cancel),
                REQ_DOWNLOAD_COVER);
    }

    private void downloadCover(CoverResult r) {
        Data input = new Data.Builder()
                .putLong(DownloadCoverWorker.KEY_FOLDER_ID, folderId)
                .putString(DownloadCoverWorker.KEY_IMAGE_URL, r.imageUrl)
                .build();

        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(DownloadCoverWorker.class)
                .setInputData(input).build();

        WorkManager.getInstance(this).enqueue(req);

        // Send result back so ModifyFolderActivity can refresh preview if needed
        Intent out = new Intent();
        out.putExtra("downloadEnqueued", true);
        setResult(RESULT_OK, out);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DOWNLOAD_COVER && resultCode == RESULT_OK && pendingCoverResult != null) {
            downloadCover(pendingCoverResult);
            pendingCoverResult = null;
        }
    }
}
