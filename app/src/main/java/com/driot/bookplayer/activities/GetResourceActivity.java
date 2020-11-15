package com.driot.bookplayer.activities;

import android.Manifest;
import android.animation.ObjectAnimator;
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
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.sqlite.db.SimpleSQLiteQuery;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.FolderAttrib;
import com.driot.bookplayer.db.ZikFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Date;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static com.driot.bookplayer.utils.Tonio.fileExists;
import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Tonio.getFileNameFromPath;
import static com.driot.bookplayer.utils.Tonio.getMimeType;
import static com.driot.bookplayer.utils.Utils.animateView;
import static com.driot.bookplayer.utils.Utils.copyStream;
import static com.driot.bookplayer.utils.Utils.retrieveListing;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 08/11/20
 */
public class GetResourceActivity extends ActivityBase {

    private static final int OPEN_ZIP_FILE_REQUEST_CODE = 24;
    private static final int OPEN_FOLDER_REQUEST_CODE = 25;

    private View progressBarOverlay;
    private ProgressBar progressBar;
    private TextView progressBarText;
    private Button bOpenFolder;
    private Button bOpenZipFile;
    private Button bSearchLibrivox;
    private Button bSearchLitteratureaudio;

    public static final int DELAY_ANIMATION = 500;
    private int[] InsertedFolderId = {0};
    private DocumentFile[] myZikFileList;

    private Handler myHandler = new Handler();;

    private DocumentFile pickedDir;

    private FolderAttrib myFolder;

    private ZipFile zipFile;
    private ArrayList<String> audioFileArrayList;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_getressource);

        bOpenFolder = findViewById(R.id.bOpenFolder);
        bOpenZipFile = findViewById(R.id.bOpenZipFile);
        bSearchLibrivox = findViewById(R.id.bSearchLibrivox);
        bSearchLitteratureaudio = findViewById(R.id.bSearchLitteratureaudio);
        progressBarOverlay = findViewById(R.id.progressBar_overlay);
        progressBar = findViewById(R.id.progressBar);
        progressBarText = findViewById(R.id.progressBarText);

        /*
        if (!checkPermissionsReadStorage()) {
            bOpenFolder.setEnabled(false);
            bOpenZipFile.setEnabled(false);
        }
*/

        // ZIP
        bOpenZipFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (checkIfPermissionsReadStorage()) {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.setType("application/zip");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(intent, OPEN_ZIP_FILE_REQUEST_CODE);
                } else {
                    myToast(getString(R.string.permissions_denied_sorry_cannot));
                }
            }
        });

        // FOLDER
        bOpenFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (checkIfPermissionsReadStorage()) {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    startActivityForResult(intent, OPEN_FOLDER_REQUEST_CODE);
                } else {
                    myToast(getString(R.string.permissions_denied_sorry_cannot));
                }

            }
        });
        bSearchLibrivox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String url = "https://librivox.org/search";
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
                //startActivityForResult(intent, DOWNLOAD_BOOK_REQUEST_CODE);
            }
        });
        bSearchLitteratureaudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String url = "http://www.litteratureaudio.com/classement-de-nos-livres-audio-gratuits-les-plus-apprecies";
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
                //startActivityForResult(intent, DOWNLOAD_BOOK_REQUEST_CODE);
            }
        });

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        ///---------------------------------------------
        /// FOLDER
        ///---------------------------------------------

        if (resultCode == RESULT_OK && requestCode == OPEN_FOLDER_REQUEST_CODE) {

            Uri treeUri = data.getData();
            pickedDir = DocumentFile.fromTreeUri(this, treeUri);

            // Si c'est pas un dossier, on prend le dossier parent...
            if (!pickedDir.isDirectory()) {
                pickedDir = DocumentFile.fromTreeUri(this, treeUri).getParentFile();
                myLog("Parent Folder taken in place");
            }

            if (pickedDir != null && pickedDir.isDirectory()) {

                // constructeur pour mon pti folder
                myFolder = new FolderAttrib(getApplicationContext(), data.getData(), false);
                if (myFolder.isFolderKO()) {
                    myLog("Cannot get Full real path of folder");
                    myToast(getString(R.string.Error_Import_FolderPathKO));
                } else {


                    audioFileArrayList = new ArrayList<String>();
                    myZikFileList = pickedDir.listFiles();
                    if (myZikFileList.length > 0) {
                        for (DocumentFile f:myZikFileList) { //check myZikFileList.length > 0 ??
                            if (f.getType() != null) {
                                if (f.getType().equals("audio/mpeg")) {
                                    myLog(f.getName());
                                    audioFileArrayList.add(f.getName()); //this adds an element to the list.
                                }
                            }
                        }
                    }

                    // TODO se servir de audioFileArrayList et pas de DocumentFile for les folders

                    if (audioFileArrayList.size() == 0) {
                        myToast(getString(R.string.Error_Import_NoMediaInFolder));
                    } else {
                        myToast(audioFileArrayList.size() + " " + getString(R.string.Import_nMediaInFolder));
                        checkIfAddedFolderExist();
                    }
                }

            } else {
                myToast(getString(R.string.Error_Import_IsNotFolder));
            }

            ///---------------------------------------------
            /// ZIP FILE
            ///---------------------------------------------

        } else if (resultCode == RESULT_OK && requestCode == OPEN_ZIP_FILE_REQUEST_CODE) {

            Uri uri = data.getData();
            myFolder = new FolderAttrib(getApplicationContext(), uri,true);

            if (myFolder.isFolderKO()) {
                myToast(getString(R.string.Error_Import_FolderPathKO));
            } else {
                boolean OnContinue=true;
                File fileZipFile = new File(myFolder.getsRealFolderPath());

                zipFile = null;
                try {
                    //zipFile = new ZipFile(zipFilePath);
                    zipFile = new ZipFile(fileZipFile);
                } catch (Exception e) {
                    myToast(getString(R.string.Error_Import_ParsingZipFile));
                    e.printStackTrace();
                    OnContinue=false;
                }

                if (OnContinue) {
                    myLog("ZipFile instancied ok");

                    audioFileArrayList = new ArrayList<String>();
                    ArrayList<String> zipFileListing;
                    zipFileListing = new ArrayList<String>();

                    for (Enumeration e = zipFile.entries(); e.hasMoreElements();) {
                        ZipEntry entry = (ZipEntry) e.nextElement();
                        if (!entry.isDirectory()) {
                            String zeName = entry.getName();
                            zipFileListing.add(zeName);
                            myLog(zeName);
                        }
                    }
                    myLog("----------------");

                    if (zipFileListing.size() != 0) {
                        // filter audio file
                        for (String s : zipFileListing) {
                            if (getMimeType(s).equals("audio/mpeg")) {
                                myLog(s);
                                audioFileArrayList.add(s); //this adds an element to the list.
                            }
                        }
                    }

                    if (audioFileArrayList.size() == 0) {
                        myToast(getString(R.string.Error_Import_NoMediaInFolder));
                    } else {
                        myToast(audioFileArrayList.size() + " " + getString(R.string.Import_nMediaInFolder));
                        checkIfZipFolderAlreadyExist();
                    }

                }
            }
        }
    }


    // ZIP
    private void checkIfZipFolderAlreadyExist() {
        Observable.fromCallable(() -> {
            boolean bcheckIfFolderExist = false;
            long lcheckIfFolderExist = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .FolderDao()
                    .folderAlreadyExist(myFolder.getsFolderUri(),myFolder.getsFolderHash());
            if (lcheckIfFolderExist>0) { bcheckIfFolderExist = true;}
            return bcheckIfFolderExist;
        })
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((result) -> {
                    if (result) {
                        myToast(getString(R.string.Error_Import_FolderAlreadyImported));
                    } else {
                        myLog("ok on continue");
                        saveFolder();
                    }

                });
    }


















    // FOLDER
    private void checkIfAddedFolderExist() {

        class CheckIfAddedFolderExist extends AsyncTask<Void, Void, Boolean> {

            @Override
            protected Boolean doInBackground(Void... voids) {
                boolean bcheckIfFolderExist = false;
                long lcheckIfFolderExist = DatabaseClient
                        .getInstance(getApplicationContext())
                        .getAppDatabase()
                        .FolderDao()
                        .folderAlreadyExist(myFolder.getsFolderUri(),myFolder.getsFolderHash());
                if (lcheckIfFolderExist>0) { bcheckIfFolderExist = true;}
                return bcheckIfFolderExist;
            }

            @Override
            protected void onPostExecute(Boolean bb) {
                super.onPostExecute(bb);
                if (bb) {
                    //myZikFileList = pickedDir.listFiles();
                    myToast(getString(R.string.Error_Import_FolderAlreadyImported));
                } else {
                    /*
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
                        myToast(getString(R.string.Error_Import_NoMediaInFolder));
                    } else {

                     */
                        saveFolder();
                    //}
                }
            }
        }
        CheckIfAddedFolderExist gt = new CheckIfAddedFolderExist();
        gt.execute();
    }

    private void saveFolder() {

        ShowProgress();

        final Double sPercent = 0.0;
        final Time sFirstAccess = new Time(System.currentTimeMillis());
        final Date sLastAccess = new Date(System.currentTimeMillis());
        final Time sLastAccessTime = new Time(System.currentTimeMillis());

        class SaveFolder extends AsyncTask<Void, Void, Void> {

            @Override
            protected Void doInBackground(Void... voids) {

                //creating a Folder
                Folder folder = new Folder();
                folder.setName(myFolder.getsFolderName());
                folder.setPath(myFolder.getsFolderPath());
                folder.setUri(myFolder.getsFolderUri());
                folder.setHash(myFolder.getsFolderHash());
                folder.setPercentdone(sPercent);
                folder.setFirstaccess(sFirstAccess);
                folder.setLastaccess(sLastAccess);
                folder.setLastaccessTime(sLastAccessTime);
                folder.setFinished(false);
                folder.setIszipfile(myFolder.isZipFolder());

                //adding to database
                InsertedFolderId[0] = (int) DatabaseClient.getInstance(getApplicationContext()).getAppDatabase()
                        .FolderDao()
                        .insert(folder);
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                if (myFolder.isZipFolder()) {
                    saveZipFiles();
                } else {
                    saveFiles();
                }
            }
        }

        SaveFolder st = new SaveFolder();
        st.execute();
    }

    private void saveZipFiles() {
        myLog("saving ZipFiles");
        Observable.fromCallable(() -> {
            int i = 0; int progress = 0; String txtProgress = "";
            for (String s : audioFileArrayList) {
                //String fileName = s.substring(s.lastIndexOf("/")+1);
                //saveFile(fileName, InsertedFolderId[0]);
                i++;
                progress = (int) i*100/audioFileArrayList.size();
                txtProgress = progress + "% - reading file n°" + i + "/" + audioFileArrayList.size() + "\n" + getFileNameFromPath(s);
                myLog("Call save " + s);
                saveFile(s, InsertedFolderId[0], progress, txtProgress);
            }
            return true;
        })
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((result) -> {
                    myLog("Zip file have been saved ");
                    updateFolderDuration();
                });

    }

    private void saveFiles() {

        class SaveFiles extends AsyncTask<Void, Void, Void> {

            @Override
            protected Void doInBackground(Void... voids) {
                int i = 0; int progress = 0; String txtProgress = "";
                for (DocumentFile f :myZikFileList) {
                    if (f.getType() != null) {
                        if (f.getType().equals("audio/mpeg")) {
                            //myLog("saving file " + f.getName());
                            i++;
                            progress = (int) i*100/audioFileArrayList.size();
                            txtProgress = progress + "% - saving file n°" + i + "/" + audioFileArrayList.size() + "\n" + f.getName();
                            saveFile(f.getName(), InsertedFolderId[0], progress, txtProgress);
                        }
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

    private void saveFile(String sZikFileName, int mFolderId, int progress, String txtProgress) {

        final Double dPercent = 0.0;
        final int iPosition = 0;

        class SaveFile extends AsyncTask<Void, Void, Void> {

            @Override
            protected Void doInBackground(Void... voids) {

                ZikFile zikFile = new ZikFile();
                zikFile.setName(sZikFileName);
                zikFile.setIdFolder(mFolderId);
                zikFile.setFolderName(myFolder.getsFolderName());
                zikFile.setPercentdone(dPercent);
                zikFile.setPosition(iPosition);
                zikFile.setPath(myFolder.getsRealFolderPath());
                zikFile.setIszipfile(myFolder.isZipFolder());

                //myLog("File Full path : " + sFileFullPath);
                //File f = new File(sFileFullPath);
                //File file = new File(Uri.parse("/sdcard/lala.txt").getPath());
                //zikFile.setSize(f.length()); // pour le zip, faudra faire une enum sur entry..
                //myLog("File length : " + f.length());

                // Media Duration
                //--------------------------------
                String sFileFullPath;
                if (myFolder.isZipFolder()) {
                    sFileFullPath = sZikFileName;
                } else {
                    sFileFullPath = myFolder.getsRealFolderPath() + File.separator + sZikFileName;
                }
                try {
                    myLog("Get Media Duration : " + sFileFullPath);
                    zikFile.setDuration(getMediaDurationFromPath(sFileFullPath));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //--------------------------------

                //adding to database
                DatabaseClient.getInstance(getApplicationContext()).getAppDatabase()
                        .ZikFileDao()
                        .insert(zikFile);
                //myLog("File Added " + zikFile.getName());
                return null;

            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                progressBar.setProgress(progress);
                progressBarText.setText(txtProgress);
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
                myLog("query has been run : " + strSQL);

                HideProgress();

                // refresh screen
                //Intent intent = new Intent(getApplicationContext(),MainActivity.class);
                //startActivity(intent);
                // myHandler.postDelayed(GetRessourceActivity.this::getFolders, 100);
                finish();
            }
        }

        UpdateFolderDuration gt = new UpdateFolderDuration();
        gt.execute();
    }


    // DUREE AUDIO
    private long getMediaDurationFromPath(String zePath) throws IOException {
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        long duration = 0;
        if (myFolder.isZipFolder()) {
            InputStream inputStream = null;
            FileOutputStream out = null;
            try {
                // or use this APK / library / whatever
                // ZipResourceFile expansionFile = new ZipResourceFile(zePath);

                inputStream = zipFile.getInputStream(zipFile.getEntry(zePath));
                File f = File.createTempFile("_AUDIO_", getExtension(zePath));
                f.deleteOnExit();
                out = new FileOutputStream(f);
                copyStream(inputStream,out);

                mediaMetadataRetriever.setDataSource(f.getPath());
                duration = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));

                f.delete();
                //myLog( "ca marche " + duration + " : " + fileName);

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                mediaMetadataRetriever.release();
                if (inputStream != null) {
                    inputStream.close();
                }
                if (out != null) {
                    out.close();
                }
            }
    } else {
            if (fileExists(zePath)) {
                mediaMetadataRetriever.setDataSource(zePath);
                duration = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            } else {
                myLog("error getting duration of media, file not exist in path : " + zePath);
            }
        }
        return duration;
    }

    // PERMISSIONS
    private boolean checkIfPermissionsReadStorage() {
        boolean HasPermission = false;
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE);
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) HasPermission = true;
        return HasPermission;
    }
/*
    private boolean checkPermissionsReadStorage() {
        boolean HasPermission = false;
        int REQUEST_READ_SD_CARD=1;
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},REQUEST_READ_SD_CARD);
        } else {
            HasPermission = true;
        }
        return HasPermission;
    }
*/

    private void ShowProgress() {
        //animateView(progressOverlay, View.VISIBLE, 0.4f, DELAY_ANIMATION);
        animateView(progressBarOverlay, View.VISIBLE, 1, DELAY_ANIMATION);
        progressBarOverlay.setVisibility(View.VISIBLE);
        progressBarOverlay.bringToFront();
        bOpenFolder.setVisibility(View.INVISIBLE);
        bOpenZipFile.setVisibility(View.INVISIBLE);
        bSearchLibrivox.setVisibility(View.INVISIBLE);
        bSearchLitteratureaudio.setVisibility(View.INVISIBLE);
    }

    private void HideProgress() {
        animateView(progressBarOverlay, View.GONE, 0, DELAY_ANIMATION);
/*
        bOpenFolder.setVisibility(View.VISIBLE);
        bOpenZipFile.setVisibility(View.VISIBLE);
        bSearchLibrivox.setVisibility(View.VISIBLE);
        bSearchLitteratureaudio.setVisibility(View.VISIBLE);
*/
    }



    private void myToast(String str) {
        myLog(str);
        Toast.makeText(getApplicationContext(),str,Toast.LENGTH_SHORT).show();
    }

    protected void myLog(String str) {
        Log.d("toto getResAct ", str);
        System.out.println(str);
    }


}
