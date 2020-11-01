package com.driot.bookplayer.activities;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.ZikFile;

import java.util.List;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 30/10/20
 */
public class FolderContentActivity extends LifecycleLoggingActivity {

    static final String TAG = "FolderContentActivity.java";
    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_foldercontent);

        recyclerView = findViewById(R.id.recyclerview_zikfiles);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        long idFolder = getIntent().getIntExtra("FolderId",0);
        if (idFolder != 0) {
            getZikFiles(idFolder);
        }

        // PERMISSIONS
        int REQUEST_READ_SD_CARD=1;
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},REQUEST_READ_SD_CARD);
        }
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

    /********************
     *
     * END STUFF
     */

    private void myLog(String str) {
        //String TAG = this.getClass().getName().substring(this.getClass().getName().lastIndexOf(".")+1);
        Log.d("titi " + TAG + " ",str);
        System.out.println(str);
    }
}

