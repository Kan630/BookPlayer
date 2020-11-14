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
import android.widget.Button;
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
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipFile;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static com.driot.bookplayer.utils.Tonio.fileExists;
import static com.driot.bookplayer.utils.Tonio.getExtension;
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
    private static final int DOWNLOAD_BOOK_REQUEST_CODE = 26;

    private View progressOverlay;
    public static final int DELAY_ANIMATION = 200;
    private int[] InsertedFolderId = {0};
    private DocumentFile[] myZikFileList;
    private String[] filePathList;

    private Handler myHandler = new Handler();;

    private DocumentFile pickedDir;

    private FolderAttrib myFolder;

    private String zipFilePath;
    private ZipFile zipFile;
    private ArrayList<String> audioFileArrayList;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_getressource);

        Button bOpenFolder = findViewById(R.id.bOpenFolder);
        Button bOpenZipFile = findViewById(R.id.bOpenZipFile);
        Button bSearchLibrivox = findViewById(R.id.bSearchLibrivox);
        Button bSearchLitteratureaudio = findViewById(R.id.bSearchLitteratureaudio);
        progressOverlay = findViewById(R.id.progress_overlay);

/*
        if (!checkPermissionsReadStorage()) {
            bOpenFolder.setEnabled(false);
            bOpenZipFile.setEnabled(false);
        }
*/

        bOpenZipFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (checkIfPermissionsReadStorage()) {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.setType("application/zip");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(intent, OPEN_ZIP_FILE_REQUEST_CODE);
                } else {
                    Toast.makeText(getApplicationContext(),getString(R.string.permissions_denied_sorry_cannot), Toast.LENGTH_SHORT).show();
                    myLog(getString(R.string.permissions_denied_sorry_cannot));
                }
            }
        });
        bOpenFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (checkIfPermissionsReadStorage()) {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    startActivityForResult(intent, OPEN_FOLDER_REQUEST_CODE);
                } else {
                    Toast.makeText(getApplicationContext(),getString(R.string.permissions_denied_sorry_cannot), Toast.LENGTH_SHORT).show();
                    myLog(getString(R.string.permissions_denied_sorry_cannot));
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

            if (pickedDir.isDirectory()) {

                // constructeur pour mon pti folder
                myFolder = new FolderAttrib(data.getData(), false);
                myLog(myFolder.toString());
                //BuildFolderAttributesFromUri(pickedDir.getUri());
                

                audioFileArrayList = new ArrayList<String>();
                myZikFileList = pickedDir.listFiles();
                if (myZikFileList.length > 0) {
                    for (DocumentFile f:myZikFileList) { //check myZikFileList.length > 0 ??
                        if (f.getType() != null) {
                            if (f.getType().equals("audio/mpeg")) {
                                audioFileArrayList.add(f.getName()); //this adds an element to the list.
                            }
                        }
                    }
                }

                // TODO se servir de audioFileArrayList et pas de DocumentFile for les folders

                if (audioFileArrayList.size() == 0) {
                    Toast.makeText(getApplicationContext(), "Aucun fichier audio trouvé dans le dossier", Toast.LENGTH_SHORT).show();
                    myLog("Aucun fichier audio trouvé dans le dossier");
                } else {
                    Toast.makeText(getApplicationContext(), audioFileArrayList.size() + " fichiers audios ont été trouvés dans le dossier", Toast.LENGTH_SHORT).show();
                    myLog(audioFileArrayList.size() + " fichiers audios ont été trouvés dans le dossier");
                    checkIfAddedFolderExist();
                }

            } else {
                myLog("Ce n'est pas un dossier");
                Toast.makeText(getApplicationContext(), getString(R.string.Error_MainActivity_IsNotFolder), Toast.LENGTH_SHORT).show();
            }

            ///---------------------------------------------
            /// ZIP FILE
            ///---------------------------------------------

        } else if (resultCode == RESULT_OK && requestCode == OPEN_ZIP_FILE_REQUEST_CODE) {
            //Toast.makeText(getApplicationContext(), "Pas encore disponible !", Toast.LENGTH_SHORT).show();

            Uri uri = data.getData();
            //BuildFolderAttributesFromUri(uri);
            myFolder = new FolderAttrib(uri,true);
            myLog(myFolder.toString());

            // Zip File Stuff
            zipFilePath = uri.getPath().replace(":","/").replace("document","storage");

            File fileZipFile = new File(zipFilePath);
            if (fileZipFile.exists()) { myLog("ok zip file found : " + zipFilePath);} else {myLog("KO zip file not found : " + zipFilePath);}

            zipFile = null;
            try {
                zipFile = new ZipFile(zipFilePath);
            } catch (IOException e) {
                e.printStackTrace();
            }
            myLog("ZipFile instancied ok");
            //for (Enumeration e = zipFile.entries(); e.hasMoreElements();) {
            //    ZipEntry entry = (ZipEntry) e.nextElement();
            //    System.out.println(entry);

            HashMap<String, List<String>> zipFileListing;
            zipFileListing = retrieveListing(fileZipFile);
            filePathList = zipFileListing.get("root").toArray(new String[0]);

            // filter audio file
            audioFileArrayList = new ArrayList<String>();
            for (String s : filePathList) {
                String mimeType = getMimeType(new File(s));
                if (mimeType.equals("audio/mpeg")) {
                    audioFileArrayList.add(s); //this adds an element to the list.
                }
            }

            if (audioFileArrayList.size() == 0) {
                Toast.makeText(getApplicationContext(), "Aucun fichier audio trouvé dans le fichier zip", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), audioFileArrayList.size() + " fichiers audios ont été trouvés dans le zip", Toast.LENGTH_SHORT).show();
                checkIfFolderAlreadyExist();
            }
        }
    }


    // ZIP
    private void checkIfFolderAlreadyExist() {
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
                        myLog("Zip deja importé");
                        Toast.makeText(getApplicationContext(), getString(R.string.Error_MainActivity_FolderAlreadyImported), Toast.LENGTH_SHORT).show();
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
                    myZikFileList = pickedDir.listFiles();
                    myLog("Dossier deja importé");
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
                        myLog("Pas de medias dans ce dossier");
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
                folder.setName(myFolder.getsFolderName());
                folder.setName(myFolder.getsFolderPath());
                folder.setName(myFolder.getsFolderUri());
                folder.setName(myFolder.getsFolderHash());
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
            for (String s : audioFileArrayList) {
                String fileName = s.substring(s.lastIndexOf("/")+1);
                myLog("Call save " + fileName);
                saveFile(fileName, InsertedFolderId[0]);
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
                for (DocumentFile file :myZikFileList) {
                    if (file.getType().equals("audio/mpeg")) {
                        myLog("saving file " + file.getName());
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
                zikFile.setFolderName(myFolder.getsFolderName());
                zikFile.setPercentdone(dPercent);
                zikFile.setPosition(iPosition);

                zikFile.setPath(myFolder.getsRealFolderPath());
                String sFileFullPath = myFolder.getsRealFolderPath() + File.separator + sZikFileName;

                myLog("File Full path : " + sFileFullPath);
                File f = new File(sFileFullPath);
                //File file = new File(Uri.parse("/sdcard/lala.txt").getPath());
                zikFile.setSize(f.length());
                myLog("File length : " + f.length());
                try {
                    // probleme de permission ?
                    zikFile.setDuration(getMediaDurationFromPath(sFileFullPath));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                zikFile.setIszipfile(myFolder.isZipFolder());

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

                // Hide animation:
                animateView(progressOverlay, View.GONE, 0, DELAY_ANIMATION);
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

                String fileName = zePath.substring(zePath.lastIndexOf("/")+1);
                inputStream = zipFile.getInputStream(zipFile.getEntry(fileName));
                File f = File.createTempFile("_AUDIO_", getExtension(fileName));
                f.deleteOnExit();
                out = new FileOutputStream(f);
                copyStream(inputStream,out);

                mediaMetadataRetriever.setDataSource(f.getPath());
                duration = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));

                f.delete();
                //myLog( "ca marche " + duration + " : " + fileName);

            } catch (IOException ioException) {
                ioException.printStackTrace();
            } finally {
                if (mediaMetadataRetriever != null) {
                    mediaMetadataRetriever.release();
                }
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

    protected void myLog(String str) {
        Log.d("toto getResAct ", str);
        System.out.println(str);
    }


}
