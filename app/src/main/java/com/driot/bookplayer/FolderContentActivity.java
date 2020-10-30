package com.driot.bookplayer;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 30/10/20
 */
public class FolderContentActivity extends Activity {

    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_foldercontent);

        recyclerView = findViewById(R.id.recyclerview_zikfiles);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        long idFolder = 1;
        getZikFiles(idFolder);
    }

    private void getZikFiles(long idFolder) {

        class GetZikFiles extends AsyncTask<Void, Void, List<ZikFile>> {

            @Override
            protected List<ZikFile> doInBackground(Void... voids) {
                List<ZikFile> zikFilesList = DatabaseClient
                        .getInstance(getApplicationContext())
                        .getAppDatabase()
                        .ZikFileDao()
                        .getZikFiles(idFolder);
                return zikFilesList;
            }

            @Override
            protected void onPostExecute(List<ZikFile> zikFiles) {
                super.onPostExecute(zikFiles);
                ZikFilesAdapter adapter = new ZikFilesAdapter(FolderContentActivity.this, zikFiles);
                recyclerView.setAdapter(adapter);
            }
        }

        GetZikFiles gt = new GetZikFiles();
        gt.execute();
    }
}
