package com.driot.bookplayer;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Calendar;
import java.sql.Date;
import java.util.List;


public class MainActivity extends Activity {

    private RecyclerView recyclerView;
    private FloatingActionButton btn_Add;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerview_folders);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        btn_Add = findViewById(R.id.FAB_Add);

        btn_Add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveFolder();
            }
        });

        getFolders();
    }

    private void getFolders() {

        class GetFolders extends AsyncTask<Void, Void, List<Folder>> {

        @Override
        protected List<Folder> doInBackground(Void... voids) {
            List<Folder> folderList = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .FolderDao()
                    .getAll();
            return folderList;
        }

        @Override
        protected void onPostExecute(List<Folder> folders) {
            super.onPostExecute(folders);
            FoldersAdapter adapter = new FoldersAdapter(MainActivity.this, folders);
            recyclerView.setAdapter(adapter);
        }
    }

    GetFolders gt = new GetFolders();
    gt.execute();
    }

    private void saveFolder() {
        final String sName = "toto Folder";
        final Double sPercent = 50.0;
        final Date sFirstAccess = new java.sql.Date(Calendar.getInstance().getTime().getTime());
        final Date sLastAccess = new java.sql.Date(Calendar.getInstance().getTime().getTime());
        // ou new Date(System.currentTimeMillis())

        class SaveFolder extends AsyncTask<Void, Void, Void> {

            @Override
            protected Void doInBackground(Void... voids) {

                //creating a Folder
                Folder folder = new Folder();
                folder.setName(sName);
                folder.setPercentdone(sPercent);
                folder.setFirstaccess(sFirstAccess);
                folder.setLastaccess(sLastAccess);
                folder.setFinished(false);

                //adding to database
                DatabaseClient.getInstance(getApplicationContext()).getAppDatabase()
                        .FolderDao()
                        .insert(folder);
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                //finish();
            }
        }
        SaveFolder st = new SaveFolder();
        st.execute();
    }
}
