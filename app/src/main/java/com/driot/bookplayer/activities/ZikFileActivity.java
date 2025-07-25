package com.driot.bookplayer.activities;

import android.os.Bundle;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.adapter.ZikFilesRVAdapter;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.objects.PlayList;
import com.driot.bookplayer.utils.log.LoggingActivity;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 30/10/20
 */
public class ZikFileActivity extends LoggingActivity {

    private RecyclerView recyclerView;
    private ZikFilesRVAdapter adapter;
    private HashMap<Integer, Integer> map;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_zikfile);

        recyclerView = findViewById(R.id.recyclerview_zikfiles);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        TextView mTextViewTitle = findViewById(R.id.textViewTitle);
        mTextViewTitle.setText(getIntent().getStringExtra("FolderName"));

        myLog("onCreate() - getDATA");
        getDATA();
    }

    private void createMap() {
        map = new HashMap<>();
        //List<ZikFile> zikFilesList = PlayList.getInstance().getZikFilesList(); // TODO already gathered via getZikFiles() through DAO, why ask it again through Playlist ?
        int nbTracks = PlayList.getInstance().getSize();
        if (nbTracks > 0) {
            for (int i = 0; i < nbTracks; i++) {
                int id = PlayList.getInstance().getZikFilesList().get(i).getId(); // id of the model
                map.put(id, i); // i is the position of adapter
            }
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        myLog("onRestart() - getDATA");
        getDATA();
    }

    private void getDATA() {
        long idFolder = getIntent().getIntExtra("FolderId",0);
        myLog("getData() - idFolder = " + idFolder);
        if (idFolder != 0) {
            getZikFiles(idFolder);
        } else {
            myLogEE(null,"getDATA() - id Folder = 0");
        }
    }

    // scroll to specific position in recyclerview
    private void goToLastAudio() {
        long maxTimestamp = 0;
        int id_max = 0;

        List<ZikFile> zikFilesList = PlayList.getInstance().getZikFilesList(); // already available through DAO in some cases
        if (zikFilesList != null && map != null) {
            try {
                for (ZikFile z : zikFilesList) {
                    if (z.lLastAccess != null && (maxTimestamp == 0 || z.lLastAccess > maxTimestamp)) {
                        maxTimestamp = z.lLastAccess;
                        id_max = z.getId();
                    }
                }
                if (id_max != 0) {
                    Integer posInteger = map.get(id_max);
                    if (posInteger != null) {
                        int pos = Math.max(posInteger - 1, 0);
                        recyclerView.scrollToPosition(pos);
                        myLogD("scrolling to: " + pos);
                    }
                }

            } catch (Exception e) {
                myLogEE(e, "goToLastAudio()");
            }
        }
    }

    public void getZikFiles(long idFolder) {
        new Thread(() -> {
            List<ZikFile> zikFilesList = AppDatabase.getDatabase(this).ZikFileDao().getZikFiles(idFolder);
            runOnUiThread(() -> {
                adapter = new ZikFilesRVAdapter(ZikFileActivity.this, zikFilesList);
                recyclerView.setAdapter(adapter);
                if (zikFilesList != null) {
                    createMap();
                    goToLastAudio();
                } else {
                    myLogEE(null,"getZikFiles() - error :  zikFilesList == null");
                }

            });
        }).start();
    }
}

