package com.driot.bookplayer.activities;

import android.os.Bundle;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.adapter.ZikFilesAdapter;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.objects.PlayList;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 30/10/20
 */
public class ZikFileActivity extends LoggingActivity {

    private RecyclerView recyclerView;
    private ZikFilesAdapter adapter;
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
        List<ZikFile> zikFilesList = PlayList.getZikFilesList(); // TODO already gathered via getZikFiles() through DAO, why ask it again through Playlist ?
        if (zikFilesList != null && zikFilesList.size() != 0) {
            for (int i = 0; i < PlayList.getZikFilesList().size(); i++) {
                int id = PlayList.getZikFilesList().get(i).getId(); // id of the model
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
            myLogE("getDATA() - id Folder = 0");
        }
    }

    // scroll to specific position in recyclerview
    private void goToLastAudio() {
        Date d = new Date(0);
        Date d_max = new Date(0);
        int id_max = 0;
        List<ZikFile> zikFilesList = PlayList.getZikFilesList(); // TODO already gathered via getZikFiles() through DAO, why ask it again through Playlist
        if (zikFilesList != null && map != null) {
            try {
                for (ZikFile z : PlayList.getZikFilesList()) {
                    if (z.getLastaccess() != null) d = z.getLastaccess();
                    if (d.after(d_max)) {
                        d_max = d;
                        id_max = z.getId();
                    }
                }
                if (id_max != 0) {
                    Integer posInteger = map.get(id_max);
                    if (posInteger != null) {
                        int pos = posInteger;
                        if ((pos)>1) pos = pos-1;
                        recyclerView.scrollToPosition(pos);
                        myLog("scrolling to  :" + map.get(id_max));
                    }
                }
            } catch (Exception e) {
                myLogE("goToLastCurrentAudio() - " + e.getMessage());
            }
        }
    }

    public void getZikFiles(long idFolder) {
        new Thread(() -> {
            List<ZikFile> zikFilesList = AppDatabase.getDatabase(this).ZikFileDao().getZikFiles(idFolder);
            runOnUiThread(() -> {
                adapter = new ZikFilesAdapter(ZikFileActivity.this, zikFilesList);
                recyclerView.setAdapter(adapter);
                if (zikFilesList != null) {
                    createMap();
                    goToLastAudio();
                } else {
                    myLogE("getZikFiles() - error :  zikFilesList == null");
                }

            });
        }).start();
    }
}

