package com.driot.bookplayer.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.ZikFile;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.sql.Date;
import java.sql.Time;
import java.util.List;


public class MainActivity extends LifecycleLoggingActivity {

    static final String TAG = "MainActivity.java";
    private RecyclerView recyclerView;
    private FloatingActionButton btn_Add;
    public static final int READ_REQUEST_CODE = 107;

    private int[] InsertedFolderId = {0};
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

        checkPermissions();
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


    /********************************************************************************
     * ******************************************************************************
     ***        AJOUT NOUVEAU DOSSIER
     ********************************************************************************
     ********************************************************************************
     */

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
            String str = sAddedFolderPath.replace(":","/");
            Log.d("toto",str);
            int pos1 = str.lastIndexOf("/");
            if (pos1>-1) {
                int pos2 =  str.substring(0,pos1).lastIndexOf("/",pos1);
                if (pos2>-1) {
                    sAddedFolderName = str.substring(pos2+1);
                } else {
                    sAddedFolderName = str.substring(pos1+1);
                }
            }

            // On vérifie qu'on a pas deja le loustic
            if (pickedDir.isDirectory()) {
                /*
                Log.d("titi", "treeUri get path : " + treeUri.getPath());
                Log.d("titi", "treeUri get path : " + treeUri.toString());
                Log.d("titi", "folder name : " + pickedDir.getName());
                Log.d("titi", "folder name : " + pickedDir.toString());
                */
                checkIfAddedFolderExist();
            } else {
                Log.d("toto","Ce n'est pas un dossier");
                Toast.makeText(getApplicationContext(), getString(R.string.Error_MainActivity_IsNotFolder), Toast.LENGTH_SHORT).show();
            }
        }
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
                    myZikFileList = pickedDir.listFiles();
                    Log.d("toto","Dossier deja importé");
                    Toast.makeText(MainActivity.this, getString(R.string.Error_MainActivity_FolderAlreadyImported), Toast.LENGTH_SHORT).show();
                } else {
                    // on vérifie que le dossier contient au moins un fichier media
                    myZikFileList = pickedDir.listFiles();
                    boolean atLeastOneMedia = false;
                    if (myZikFileList.length > 0) {
                        for (DocumentFile f:myZikFileList) { //check myZikFileList.length > 0 ??
                            if (f.getType() != null) {
                                if (f.getType().equals("audio/mpeg")) {
                                    atLeastOneMedia = true;
                                }
                            }
                        }
                    }
                    if (!atLeastOneMedia) {
                        Log.d("toto","Pas de medias dans ce dossier");
                        Toast.makeText(MainActivity.this, getString(R.string.Error_MainActivity_NoMediaInFolder), Toast.LENGTH_SHORT).show();
                    } else {
                        saveFolder();
                    }
                }
            }
        }
        CheckIfAddedFolderExist gt = new CheckIfAddedFolderExist();
        gt.execute();
    }

    private void saveFolder() {

        final Double sPercent = 0.0;
        final Time sFirstAccess = new Time(System.currentTimeMillis());
        final Date sLastAccess = new Date(System.currentTimeMillis());
        final Time sLastAccessTime = new Time(System.currentTimeMillis());

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
                folder.setLastaccessTime(sLastAccessTime);
                folder.setFinished(false);

                //adding to database
                InsertedFolderId[0] = (int) DatabaseClient.getInstance(getApplicationContext()).getAppDatabase()
                        .FolderDao()
                        .insert(folder);
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                //finish();
                for (DocumentFile file :myZikFileList) {
                    if (file.getType().equals("audio/mpeg")) {
                        saveFile(file.getName(), InsertedFolderId[0]);
                    }
                }
            }
        }

        SaveFolder st = new SaveFolder();
        st.execute();
    }

    private void saveFile(String sZikFileName, int mFolderId) {

        final Double dPercent = 0.0;
        final int iPosition = 0;

        class SaveFile extends AsyncTask<Void, Void, Void> {

            @Override
            protected Void doInBackground(Void... voids) {

                //creating a Folder
                ZikFile zikFile = new ZikFile();
                zikFile.setName(sZikFileName);
                zikFile.setIdFolder(mFolderId);
                zikFile.setFolderName(sAddedFolderName);
                zikFile.setPercentdone(dPercent);
                zikFile.setPosition(iPosition);

                String sFolderPath = "/storage/" + sAddedFolderPath.replace(":","/");
                String sFileFullPath = sFolderPath + File.separator + sZikFileName;
                zikFile.setPath(sFolderPath);
                File f = new File(sFileFullPath);
                //File file = new File(Uri.parse("/sdcard/lala.txt").getPath());
                zikFile.setSize(f.length());
                zikFile.setDuration(getMediaDurationFromPath(sFileFullPath));

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

    /********************
     *
     * END STUFF
     */

    private void myLog(String str) {
        //String TAG = this.getClass().getName().substring(this.getClass().getName().lastIndexOf(".")+1);
        Log.d("titi " + TAG + " ",str);
        System.out.println(str);
    }

    // PERMISSIONS
    private void checkPermissions() {
        int REQUEST_READ_SD_CARD=1;
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},REQUEST_READ_SD_CARD);
        }
    }

    // DUREE AUDIO
    private static long getMediaDurationFromPath(String zePath) {
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        mediaMetadataRetriever.setDataSource(zePath);
        return Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
    }
}
