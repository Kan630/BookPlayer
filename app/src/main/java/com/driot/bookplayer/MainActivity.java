package com.driot.bookplayer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.sql.Time;
import java.sql.Date;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;


public class MainActivity extends Activity {

    private RecyclerView recyclerView;
    private FloatingActionButton btn_Add;
    public static final int READ_REQUEST_CODE = 107;

    private long[] InsertedFolderId = {0};

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
                performFileSearch();
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

    public void performFileSearch() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, READ_REQUEST_CODE);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (resultCode == RESULT_OK && requestCode == READ_REQUEST_CODE) {
            Uri treeUri = resultData.getData();
            //File pickedDir = new File(treeUri.getPath());
            DocumentFile pickedDir = DocumentFile.fromTreeUri(this, treeUri);

            // TODO : Si c'est pas un dossier, prendre l'objet parent...

            if (pickedDir.isDirectory()) {

                String fdPath = pickedDir.getUri().getLastPathSegment();
                    // deux derniers folders pour le nom :
                    String str = fdPath;
                    int pos1 = str.lastIndexOf("/");
                    int pos2 =  str.substring(0,pos1).lastIndexOf("/",pos1);
                    String fdName = str.substring(pos2+1);
                String fdUri = pickedDir.getUri().toString();
                String fdHash = Integer.toString(pickedDir.getUri().hashCode());

                Log.d("titi", "folder name : " + pickedDir.getName());
                Log.d("titi", "is Dir : " + pickedDir.isDirectory());
                Log.d("titi", "is File : " + pickedDir.isFile());
                Log.d("titi", "folder parentfile: " + pickedDir.getParentFile());
                Log.d("titi", "folder to string : " + pickedDir.toString());
                Log.d("titi", "folder get path : " + treeUri.getPath());
                Log.d("titi", "treeUri to String : " + treeUri.toString());
                Log.d("titi", "folder get path : " + treeUri.getLastPathSegment());
                Log.d("titi", "folder get path : " + pickedDir.getUri().getPath());
                Log.d("titi", "folder get path : " + pickedDir.getUri().getLastPathSegment());
                Log.d("titi", "folder get path : " + pickedDir.getUri().toString());
                Log.d("titi", "folder get path : " + pickedDir.getUri().hashCode());

                saveFolder(fdName, fdPath, fdUri, fdHash);


                // TODO on pourrait lancer le SaveFile a l'interieur du OnPostExecute du SaveFolder...
                // pour ca faut juste preparer un array avec le nom des fichiers

                Log.d("titi", "INSERTED FOLDER ID : --" + InsertedFolderId[0] + "--");


                // List all existing files inside picked directory

                DocumentFile[] myList = pickedDir.listFiles();
                if (myList.length > 1) {
                    // TODO => trouver un tri qui ne prend pas 7 secondes !!!!
                    /*
                    Arrays.sort(myList, new Comparator<DocumentFile>() {
                        @Override
                        public int compare(DocumentFile object1, DocumentFile object2) {
                            return object1.getName().compareTo(object2.getName());
                        }
                    });
                    */

                    for (DocumentFile file :myList) {
                        //Log.d("titi", "Found file " + file.getName() + " with size " + file.length());
                        saveFile(file.getName(), InsertedFolderId[0]);
                    }
                }
            }

        }
    }


    private void saveFolder(String fdName, String fdPath, String fdUri, String fdHash) {

        final Double sPercent = 0.0;
        final Time sFirstAccess = new Time(System.currentTimeMillis());
        final Time sLastAccess = new Time(System.currentTimeMillis());

        class SaveFolder extends AsyncTask<Void, Void, Void> {

            @Override
            protected Void doInBackground(Void... voids) {

                //creating a Folder
                Folder folder = new Folder();
                folder.setName(fdName);
                folder.setPath(fdPath);
                folder.setUri(fdUri);
                folder.setHash(fdHash);
                folder.setPercentdone(sPercent);
                folder.setFirstaccess(sFirstAccess);
                folder.setLastaccess(sLastAccess);
                folder.setFinished(false);

                //adding to database
                InsertedFolderId[0] = DatabaseClient.getInstance(getApplicationContext()).getAppDatabase()
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

        // TODO check if folder doesn't already exists...
        // TODO get id of created folder

        SaveFolder st = new SaveFolder();
        st.execute();
    }

    private void saveFile(String sZikFileName, long lFolderId) {

        final Double dPercent = 0.0;
        final int iPosition = 0;

        class SaveFile extends AsyncTask<Void, Void, Void> {

            @Override
            protected Void doInBackground(Void... voids) {

                //creating a Folder
                ZikFile zikFile = new ZikFile();
                zikFile.setName(sZikFileName);
                zikFile.setIdFolder(lFolderId);
                zikFile.setPercentdone(dPercent);
                zikFile.setPosition(iPosition);

                //adding to database
                DatabaseClient.getInstance(getApplicationContext()).getAppDatabase()
                        .ZikFileDao()
                        .insert(zikFile);
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                //finish();
            }
        }

        // TODO check if folder doesn't already exists...
        // TODO get id of created folder

        SaveFile st = new SaveFile();
        st.execute();
    }

}
