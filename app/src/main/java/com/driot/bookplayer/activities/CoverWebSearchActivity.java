package com.driot.bookplayer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.driot.bookplayer.R;
import com.driot.bookplayer.adapters.CoverResultAdapter;
import com.driot.bookplayer.net.CoverSearchRepository;
import com.driot.bookplayer.objects.CoverResult;
import com.driot.bookplayer.services.DownloadCoverWorker;
import com.driot.bookplayer.utils.log.LoggingActivity;
import java.util.List;
import java.util.concurrent.Executors;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public class CoverWebSearchActivity extends LoggingActivity {
    public static final String EXTRA_FOLDER_ID = "folderId";
    public static final String EXTRA_DEFAULT_TITLE = "defaultTitle";
    public static final int MAX_NB_COVER_SEARCH_RESULT = 24;

    private long folderId;
    private EditText etQuery;
    private CoverResultAdapter adapter;
    private final CoverSearchRepository repo = new CoverSearchRepository();

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cover_web_search);

        folderId = getIntent().getLongExtra(EXTRA_FOLDER_ID, -1L);
        String defaultTitle = getIntent().getStringExtra(EXTRA_DEFAULT_TITLE);
        if (folderId <= 0) { finish(); return; }

        etQuery = findViewById(R.id.etQuery);
        etQuery.setText(defaultTitle != null ? defaultTitle : "");
        RecyclerView rv = findViewById(R.id.rvResults);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CoverResultAdapter(this::onResultClicked);
        rv.setAdapter(adapter);

        etQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch(etQuery.getText().toString().trim());
                return true;
            }
            return false;
        });

        // auto search on open if we have a title
        if (defaultTitle != null && !defaultTitle.isEmpty()) {
            runSearch(defaultTitle);
        }
    }

    private void runSearch(String q) {
        if (q.isEmpty()) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            List<CoverResult> list = repo.search(this, q, MAX_NB_COVER_SEARCH_RESULT);
            runOnUiThread(() -> adapter.submit(list));
        });
    }

    private void onResultClicked(CoverResult r) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm)
                .setMessage(getString(R.string.use_this_image_as_cover))
                .setPositiveButton(android.R.string.ok, (d, w) -> downloadCover(r))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
}
