package com.driot.bookplayer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

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
    private DocumentFile[] myZikFileList;
    private Handler myHandler = new Handler();;

    private DocumentFile pickedDir;
    private String sAddedFolderUri;
    private String sAddedFolderHash;
    private String sAddedFolderName;
    private String sAddedFolderPath;


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

    private void checkIfAddedFolderExist() {

        class CheckIfAddedFolderExist extends AsyncTask<Void, Void, Boolean> {

            @Override
            protected Boolean doInBackground(Void... voids) {
                boolean bcheckIfFolderExist = false;
                long lcheckIfFolderExist = DatabaseClient
                        .getInstance(getApplicationContext())
                        .getAppDatabase()
                        .FolderDao()
                        .folderAlreadyExist(sAddedFolderUri,sAddedFolderHash);
                if (lcheckIfFolderExist>0) { bcheckIfFolderExist = true;}
                return bcheckIfFolderExist;
            }

            @Override
            protected void onPostExecute(Boolean bb) {
                super.onPostExecute(bb);
                if (bb) {
                    Toast.makeText(MainActivity.this, "Ce dossier a déjà été importé", Toast.LENGTH_SHORT);
                } else {
                    // TODO vérifier que au moins un fichier mp3
                    myZikFileList = pickedDir.listFiles();
                    if (myZikFileList.length > 0) {
                        saveFolder();
                    }
                }
            }
        }
        CheckIfAddedFolderExist gt = new CheckIfAddedFolderExist();
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
            pickedDir = DocumentFile.fromTreeUri(this, treeUri);

            // Si c'est pas un dossier, on prend le dossier parent...
            if (!pickedDir.isDirectory()) {
                pickedDir = DocumentFile.fromTreeUri(this, treeUri).getParentFile();
            }

            // on construit quelques attributs
            sAddedFolderUri = pickedDir.getUri().toString();
            sAddedFolderHash = Integer.toString(pickedDir.getUri().hashCode());
            // nom par défaut = les deux derniers folders :
            // ex  : "S3 - Finances publiques/Audios"
            sAddedFolderPath = pickedDir.getUri().getLastPathSegment();
            String str = sAddedFolderPath;
            int pos1 = str.lastIndexOf("/");
            int pos2 =  str.substring(0,pos1).lastIndexOf("/",pos1);
            sAddedFolderName = str.substring(pos2+1);

            // On vérifie qu'on a pas deja le loustic

            if (pickedDir.isDirectory()) {
                Log.d("titi", "treeUri get path : " + treeUri.getPath());
                Log.d("titi", "treeUri get path : " + treeUri.toString());
                Log.d("titi", "folder name : " + pickedDir.getName());
                Log.d("titi", "folder name : " + pickedDir.toString());
                checkIfAddedFolderExist();
            } else {
                Toast.makeText(getBaseContext(), "Le dossier a importé n'est pas reconnu", Toast.LENGTH_SHORT);
            }
        }
    }


    private void saveFolder() {

        final Double sPercent = 0.0;
        final Time sFirstAccess = new Time(System.currentTimeMillis());
        final Time sLastAccess = new Time(System.currentTimeMillis());

        class SaveFolder extends AsyncTask<Void, Void, Void> {

            @Override
            protected Void doInBackground(Void... voids) {

                //creating a Folder
                Folder folder = new Folder();
                folder.setName(sAddedFolderName);
                folder.setPath(sAddedFolderPath);
                folder.setUri(sAddedFolderUri);
                folder.setHash(sAddedFolderHash);
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
                for (DocumentFile file :myZikFileList) {
                    saveFile(file.getName(), InsertedFolderId[0]);
                }
            }
        }

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
                String sFilePath = "/storage/" + sAddedFolderPath.replace(":","/");
                zikFile.setPath(sFilePath);

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

                // refresh screen
                myHandler.postDelayed(MainActivity.this::getFolders, 100);
            }
        }

        SaveFile st = new SaveFile();
        st.execute();
    }

}
