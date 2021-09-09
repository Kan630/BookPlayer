package com.driot.bookplayer.activities;

import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.PlayList;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static com.driot.tonylib.KanLogger.myLog;
import static com.driot.tonylib.KanLogger.myLogE;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 30/10/20
 */
public class FolderContentActivity extends LifecycleLoggingActivity {

    private RecyclerView recyclerView;
    private ZikFilesAdapter adapter;
    private HashMap<Integer, Integer> map;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_foldercontent);

        recyclerView = findViewById(R.id.recyclerview_zikfiles);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        TextView mTextViewTitle = findViewById(R.id.textViewTitle);
        mTextViewTitle.setText(getIntent().getStringExtra("FolderName"));

        myLog("recyclerview idFolder onCreate - getDATA");
        getDATA();
    }

    private void createMap() {
        map = new HashMap<>();
        for (int i = 0; i < PlayList.getZikFilesList().size(); i++) {
            int id = PlayList.getZikFilesList().get(i).getId(); // id of the model
            map.put(id, i); // i is the position of adapter
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        myLog("recyclerview idFolder onRestart - getDATA");
        getDATA();
    }

    private void getDATA() {
        long idFolder = getIntent().getIntExtra("FolderId",0);
        myLog("recyclerview idFolder = " + idFolder);
        if (idFolder != 0) {
            getZikFiles(idFolder);
        } else {
            myLogE("FolderContentActivity.onCreate id Folder = 0");
        }
    }

    private void goToLastAudio() {
        Date d = new Date(0);
        Date d_max = new Date(0);
        int id_max = 0;
        try {
            for (ZikFile z : PlayList.getZikFilesList()) {
                if (z.getLastaccess() != null) d = z.getLastaccess();
                if (d.after(d_max)) {
                    d_max = d;
                    id_max = z.getId();
                }
            }
            if (id_max != 0) {
                int pos = map.get(id_max);
                if ((pos)>1) pos = pos-1;
                if (id_max != 0) recyclerView.scrollToPosition(pos);
                myLog("scrolling to  :" + map.get(id_max));
            }
        } catch (Exception e) {
            myLogE("goToLastCurrentAudio :" + e.getMessage());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    public void getZikFiles(long idFolder) {

        class GetZikFiles extends AsyncTask<Void, Void, List<ZikFile>> {

            @Override
            protected List<ZikFile> doInBackground(Void... voids) {
                List<ZikFile> zikFilesList = DatabaseClient
                        .getInstance(getApplicationContext())
                        .getAppDatabase()
                        .ZikFileDao()
                        .getZikFiles(idFolder);
                PlayList.setZikFilesList(zikFilesList); // GLOBAL
                return zikFilesList;
            }

            @Override
            protected void onPostExecute(List<ZikFile> zikFiles) {
                super.onPostExecute(zikFiles);
                adapter = new ZikFilesAdapter(FolderContentActivity.this, zikFiles);
                recyclerView.setAdapter(adapter);
                createMap();
                goToLastAudio();
            }
        }

        GetZikFiles gt = new GetZikFiles();
        gt.execute();
    }

    /********************
     *
     * END STUFF
     */

/*
// Save state
private Parcelable recyclerViewState;
        recyclerViewState = recyclerView.getLayoutManager().onSaveInstanceState();

// Restore state
        recyclerView.getLayoutManager().onRestoreInstanceState(recyclerViewState);
*/
}

