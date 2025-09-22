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

import java.util.HashMap;
import java.util.List;

public class ZikFileActivity extends LoggingActivity {

    private RecyclerView recyclerView;
    private ZikFilesRVAdapter adapter;

    private Folder folder;
    private int folderId = -1;

    private List<ZikFile> zikFilesList;
    private final HashMap<Integer, Integer> map = new HashMap<>();
    private ImageButton ib_settings;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_zikfile);
        InsetHelper.apply(this);

        recyclerView = findViewById(R.id.recyclerview_zikfiles);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        ib_settings = findViewById(R.id.ib_settings);
        ib_settings.setOnClickListener(view -> {
            myLogI("--- User clicks SETTINGS ---");
            if (folder != null) {
                Intent it = new Intent(this, ModifyFolderActivity.class).putExtra("folder", folder);
                modifyLauncher.launch(it);
            }
        });

        // Read initial folder once, store id, then always reload from DB
        Folder initial = getIntent().getParcelableExtra("folder");
        if (initial == null) {
            myToastEE(null, "ZikFileActivity : folder == null");
            finish();
            return;
        }
        folderId = initial.getId();

        // Load data
        reloadFolderFromDb();          // fills header (or finishes if deleted)
        getZikFilesAndLoadRecyclerView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ensure header is always fresh when coming back to this screen
        reloadFolderFromDb();
        getZikFilesAndLoadRecyclerView();
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

    private void getZikFilesAndLoadRecyclerView() {
        AppDatabase.databaseReadExecutor.execute(() -> {
            zikFilesList = AppDatabase.getDatabase(this).ZikFileDao().getZikFiles(folderId);

            map.clear();
            for (int i = 0; i < zikFilesList.size(); i++) {
                map.put(zikFilesList.get(i).getId(), i);
            }

            runOnUiThread(() -> {
                adapter = new ZikFilesRVAdapter(ZikFileActivity.this, zikFilesList);
                recyclerView.setAdapter(adapter);
                if (!zikFilesList.isEmpty()) {
                    goToLastAudio();
                } else {
                    myLog("Zik file list is empty");
                }
            });
        });
    }

    private void goToLastAudio() {
        long maxTimestamp = 0;
        int idMax = 0;

        for (ZikFile z : zikFilesList) {
            if (z.lLastAccess != null && z.lLastAccess > maxTimestamp) {
                maxTimestamp = z.lLastAccess;
                idMax = z.getId();
            }
        }

        if (idMax != 0 && map.containsKey(idMax)) {
            int pos = Math.max(map.get(idMax) - 1, 0);
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
            imageView.setImageURI(null);                 // force refresh if same URI
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
