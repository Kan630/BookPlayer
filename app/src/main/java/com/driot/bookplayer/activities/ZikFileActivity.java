package com.driot.bookplayer.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.adapter.ZikFilesRVAdapter;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.PodcastDao;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.util.HashMap;
import java.util.List;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 30/10/20
 */
public class ZikFileActivity extends LoggingActivity {

    private RecyclerView recyclerView;
    private ZikFilesRVAdapter adapter;

    private Folder folder;
    private List<ZikFile> zikFilesList;
    private HashMap<Integer, Integer> map = new HashMap<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_zikfile);

        recyclerView = findViewById(R.id.recyclerview_zikfiles);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        getDATA();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        getDATA();
    }

    private void getDATA() {
        folder = getIntent().getParcelableExtra("folder");
        if (folder == null) {
            myToastEE(null, "ZikFileActivity : folder == null");
            finish();
            return;
        }
        getZikFilesAndLoadRecyclerView();
        fillHeader();
    }

    private void getZikFilesAndLoadRecyclerView() {
        AppDatabase.databaseReadExecutor.execute(() -> {
            zikFilesList = AppDatabase.getDatabase(this).ZikFileDao().getZikFiles(folder.getId());

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
        textViewTitle.setText(folder.getName());
        ImageView imageView = findViewById(R.id.image);
        if (folder.image != null && !folder.image.isEmpty()) {
            imageView.setImageURI(Uri.parse(folder.image));
            imageView.setVisibility(View.VISIBLE);
        } else {
            imageView.setVisibility(View.GONE);
        }
        imageView.setOnClickListener(view -> {
            myLogI("--- User click header image --");
            goUserClickHeader();
        });
        textViewTitle.setOnClickListener(view -> {
            myLogI("--- User click header text --");
            goUserClickHeader();
        });
    }
    private void goUserClickHeader() {
        if (folder.getSourceLocation().equals(Var.SOURCE_LOCATION_PODCAST)) {
            AppDatabase.databaseReadExecutor.execute(()-> {
                Podcast podcast = AppDatabase.getDatabase(this).PodcastDao().getPodcastByFolderId(folder.getId());
                myLogD("opening PodcastEpisodeActivity for podcast : " + podcast.title);
                startActivity(new Intent(this, PodcastEpisodeActivity.class).putExtra("podcast", podcast));
            });
        }
    }



}
