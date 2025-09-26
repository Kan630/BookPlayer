package com.driot.bookplayer.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.ZikFilesRVAdapter;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.util.List;

public class ZikFileActivity extends LoggingActivity {

    public static final String EXTRA_FOLDER_ID = "extra_folder_id";

    private RecyclerView recyclerView;
    private ZikFilesRVAdapter adapter;

    private Folder folder;
    private int folderId = -1;

    private ImageButton ib_settings;

    // ViewModel that exposes LiveData<List<ZikFile>>
    private ZikFilesViewModel vm;

    // ensure we auto-scroll only once after the first list is loaded
    private boolean didAutoScrollToLast = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_zikfile);
        InsetHelper.apply(this);

        recyclerView = findViewById(R.id.recyclerview_zikfiles);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ZikFilesRVAdapter();
        recyclerView.setAdapter(adapter);

        ib_settings = findViewById(R.id.ib_settings);
        ib_settings.setOnClickListener(view -> {
            myLogI("--- User clicks SETTINGS ---");
            if (folder != null) {
                Intent it = new Intent(this, ModifyFolderActivity.class).putExtra("folder", folder);
                modifyLauncher.launch(it);
            }
        });

        // Read initial folder once; keep only the id and always re-read from DB
        folderId = getIntent().getIntExtra(EXTRA_FOLDER_ID, -1);
        if (!(folderId > 0)) {
            Folder initial = getIntent().getParcelableExtra("folder");
            if (initial == null) {
                myToastEE(null, "ZikFileActivity : folder == null");
                finish();
                return;
            }
            folderId = initial.getId();
        }

        // ViewModel + LiveData observation
        vm = new ViewModelProvider(this).get(ZikFilesViewModel.class);
        vm.getZikFilesLive(folderId).observe(this, list -> {
            if (list == null) return;
            adapter.submitList(list);

            // Auto-scroll only on first load
            if (!didAutoScrollToLast && !list.isEmpty()) {
                didAutoScrollToLast = true;
                scrollToLastPlayed(list);
            }
        });

        // Load header info (folder metadata + image)
        reloadFolderFromDb();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Keep header fresh when returning
        reloadFolderFromDb();
    }

    private void reloadFolderFromDb() {
        if (folderId < 0) {
            finish();
            return;
        }
        AppDatabase.databaseReadExecutor.execute(() -> {
            Folder newest = AppDatabase.getDatabase(this).FolderDao().getById(folderId);
            runOnUiThread(() -> {
                if (newest == null) {
                    myLogI("Folder " + folderId + " was deleted — finishing activity.");
                    finish();
                } else {
                    folder = newest;
                    fillHeader();
                }
            });
        });
    }

    private void scrollToLastPlayed(List<ZikFile> list) {
        long maxTs = 0;
        int targetIndex = -1;

        for (int i = 0; i < list.size(); i++) {
            ZikFile z = list.get(i);
            if (z.lLastAccess != null && z.lLastAccess > maxTs) {
                maxTs = z.lLastAccess;
                targetIndex = i;
            }
        }
        if (targetIndex >= 0) {
            // scroll a little above the last item for context
            int pos = Math.max(targetIndex - 1, 0);
            recyclerView.scrollToPosition(pos);
            myLogD("Scrolling to last played audio at position: " + pos);
        }
    }

    private void fillHeader() {
        TextView textViewTitle = findViewById(R.id.textViewTitle);
        ImageView imageView = findViewById(R.id.image);

        textViewTitle.setText(folder.getName());

        if (folder.image != null && !folder.image.isEmpty()) {
            imageView.setVisibility(ImageView.VISIBLE);
            imageView.setImageURI(null); // force refresh if same URI
            imageView.setImageURI(Uri.parse(folder.image));
            imageView.invalidate();
        } else {
            imageView.setImageDrawable(null);
            imageView.setVisibility(ImageView.GONE);
        }

        imageView.setOnClickListener(v -> {
            myLogI("--- User click header image --");
            goUserClickHeader();
        });
        textViewTitle.setOnClickListener(v -> {
            myLogI("--- User click header text --");
            goUserClickHeader();
        });
    }

    private void goUserClickHeader() {
        if (Var.SOURCE_LOCATION_PODCAST.equals(folder.getSourceLocation())) {
            AppDatabase.databaseReadExecutor.execute(() -> {
                Podcast podcast = AppDatabase.getDatabase(this).PodcastDao().getPodcastByFolderId(folderId);
                if (podcast != null) {
                    myLogD("opening PodcastEpisodeActivity for podcast : " + podcast.title);
                    startActivity(new Intent(this, PodcastEpisodeActivity.class).putExtra("podcast", podcast));
                } else {
                    myLogI("No podcast linked to folder " + folderId);
                }
            });
        }
    }

    private final ActivityResultLauncher<Intent> modifyLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                myLogD("coming back from ModifyFolderActivity");
                // Always reload from DB; if folder was deleted, this will finish().
                reloadFolderFromDb();
            });
}
