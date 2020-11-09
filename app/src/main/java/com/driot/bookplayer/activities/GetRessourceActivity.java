package com.driot.bookplayer.activities;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.sqlite.db.SimpleSQLiteQuery;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.ZikFile;

import java.io.File;
import java.sql.Date;
import java.sql.Time;

import static com.driot.bookplayer.utils.Utils.animateView;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 08/11/20
 */
public class GetRessourceActivity extends Activity {

    private static final int OPEN_ZIP_FILE_REQUEST_CODE = 24;
    private static final int OPEN_FOLDER_REQUEST_CODE = 25;

    private View progressOverlay;
    public static final int DELAY_ANIMATION = 200;
    private int[] InsertedFolderId = {0};
    private DocumentFile[] myZikFileList;
    private Handler myHandler = new Handler();;

    private DocumentFile pickedDir;
    private String sAddedFolderUri;
    private String sAddedFolderHash;
    private String sAddedFolderName;
    private String sAddedFolderPath;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_getressource);

        Button bOpenFolder = findViewById(R.id.bOpenFolder);
        Button bOpenZipFile = findViewById(R.id.bOpenZipFile);
        progressOverlay = findViewById(R.id.progress_overlay);


        bOpenZipFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("application/zip");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, OPEN_ZIP_FILE_REQUEST_CODE);
            }
        });
        bOpenFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                startActivityForResult(intent, OPEN_FOLDER_REQUEST_CODE);
            }
        });

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == OPEN_FOLDER_REQUEST_CODE) {
            Uri treeUri = data.getData();
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
                checkIfAddedFolderExist();
            } else {
                Log.d("toto","Ce n'est pas un dossier");
                Toast.makeText(getApplicationContext(), getString(R.string.Error_MainActivity_IsNotFolder), Toast.LENGTH_SHORT).show();
            }
        } else if (resultCode == RESULT_OK && requestCode == OPEN_ZIP_FILE_REQUEST_CODE) {
            Toast.makeText(getApplicationContext(), "Pas encore disponible !", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(getApplicationContext(), getString(R.string.Error_MainActivity_FolderAlreadyImported), Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(getApplicationContext(), getString(R.string.Error_MainActivity_NoMediaInFolder), Toast.LENGTH_SHORT).show();
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

        // Show progress overlay (with animation):
        animateView(progressOverlay, View.VISIBLE, 0.4f, DELAY_ANIMATION);

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
                saveFiles();
            }
        }

        SaveFolder st = new SaveFolder();
        st.execute();
    }

    private void saveFiles() {

        class SaveFiles extends AsyncTask<Void, Void, Void> {

            @Override
            protected Void doInBackground(Void... voids) {
                for (DocumentFile file :myZikFileList) {
                    if (file.getType().equals("audio/mpeg")) {
                        saveFile(file.getName(), InsertedFolderId[0]);
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                updateFolderDuration();
            }
        }

        SaveFiles st = new SaveFiles();
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
                Log.d("toto","File Added " + zikFile.getName());
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
            }
        }

        SaveFile st = new SaveFile();
        st.execute();
    }

    private void updateFolderDuration() {

        String strSQL = "UPDATE Folder " +
                " SET duration = (SELECT SUM(duration) " +
                " FROM ZikFile " +
                " WHERE Folder.id = ZikFile.idFolder )";

        class UpdateFolderDuration extends AsyncTask<Void, Void, Void> {

            @Override
            protected Void doInBackground(Void... voids) {

                SimpleSQLiteQuery query = new SimpleSQLiteQuery(strSQL);
                DatabaseClient
                        .getInstance(getApplicationContext())
                        .getAppDatabase()
                        .FolderDao()
                        .runRawSql(query);
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                Log.d("toto","run query " + strSQL);

                // Hide animation:
                animateView(progressOverlay, View.GONE, 0, DELAY_ANIMATION);
                // refresh screen
                Intent intent = new Intent(getApplicationContext(),MainActivity.class);
                startActivity(intent);
                // myHandler.postDelayed(GetRessourceActivity.this::getFolders, 100);
            }
        }

        UpdateFolderDuration gt = new UpdateFolderDuration();
        gt.execute();
    }


    // DUREE AUDIO
    private static long getMediaDurationFromPath(String zePath) {
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        mediaMetadataRetriever.setDataSource(zePath);
        return Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
    }



}
