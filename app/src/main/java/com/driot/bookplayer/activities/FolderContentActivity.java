package com.driot.bookplayer.activities;

import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.TextView;

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

    private RecyclerView recyclerView;
    private TextView mTextViewTitle;
    List<ZikFile> currentZikFileList;
    private ZikFilesAdapter adapter;
    private int verticalOffset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_foldercontent);

        mTextViewTitle = findViewById(R.id.textViewTitle);
        recyclerView = findViewById(R.id.recyclerview_zikfiles);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        long idFolder = getIntent().getIntExtra("FolderId",0);
        mTextViewTitle.setText(getIntent().getStringExtra("FolderName"));
        myLog("recyclerview idFolder = " + idFolder);
        if (idFolder != 0) {
            getZikFiles(idFolder);
            myLog("recyclerview drawing through setAdapter");
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        //TODO put position just after 100%....

        long idFolder = getIntent().getIntExtra("FolderId",0);
        myLog("recyclerview idFolder on restart = " + idFolder);
        if (idFolder != 0) {
            getZikFiles(idFolder);
            myLog("recyclerview drawing through setAdapter on restart");
        }
        myLog("recyclerview position = " + verticalOffset);
        //recyclerView.smoothScrollToPosition(30); //11

    }

    @Override
    protected void onStop() {
        super.onStop();
        verticalOffset = recyclerView.computeVerticalScrollOffset();
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
                currentZikFileList = zikFilesList;
                return zikFilesList;
            }

            @Override
            protected void onPostExecute(List<ZikFile> zikFiles) {
                super.onPostExecute(zikFiles);
                adapter = new ZikFilesAdapter(FolderContentActivity.this, zikFiles);
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

/*
// Save state
private Parcelable recyclerViewState;
        recyclerViewState = recyclerView.getLayoutManager().onSaveInstanceState();

// Restore state
        recyclerView.getLayoutManager().onRestoreInstanceState(recyclerViewState);
*/
}

