package com.driot.bookplayer.activities;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.sqlite.db.SimpleSQLiteQuery;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.FolderAttrib;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.utils.PermissionRequest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Date;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.CompletableObserver;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static com.driot.bookplayer.utils.Tonio.fileExists;
import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Tonio.getFileNameFromPath;
import static com.driot.bookplayer.utils.Tonio.getMimeType;
import static com.driot.bookplayer.utils.Utils.animateView;
import static com.driot.bookplayer.utils.Utils.copyStream;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 08/11/20
 */
public class GetResourceActivity extends LifecycleLoggingActivity {

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

    private PermissionRequest mPermissionRequest;

    private int nbFileSaved, nbFileToSave;

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

                    goFolder();
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
                        }
                    }

                    if (zipFileListing.size() != 0) {
                        // filter audio file
                        for (String s : zipFileListing) {
                            if (getMimeType(s).equals("audio/mpeg")) {
                                myLog(s);
                                audioFileArrayList.add(s); //this adds an element to the list.
                            }
                        }
                    }

                    goFolder();

                }
            }
        }
    }

    private void goFolder() {
        if (audioFileArrayList.size() == 0) {
            myToast(getString(R.string.Error_Import_NoMediaInFolder));
        } else {
            myToast(audioFileArrayList.size() + " " + getString(R.string.Import_nMediaInFolder));
            checkIfFolderAlreadyExist();
        }
    }

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
                        myToast(getString(R.string.Error_Import_FolderAlreadyImported));
                    } else {
                        myLog("ok on continue");
                        saveFolder();
                    }
                });
    }


    private void saveFolder() {
        ShowProgress();
        final Time sFirstAccess = new Time(System.currentTimeMillis());
        final Date sLastAccess = new Date(System.currentTimeMillis());
        final Time sLastAccessTime = new Time(System.currentTimeMillis());

        Observable.fromCallable(() -> {
            //creating a Folder
            Folder folder = new Folder();
            folder.setName(myFolder.getsFolderName());
            folder.setPath(myFolder.getsFolderPath());
            folder.setUri(myFolder.getsFolderUri());
            folder.setHash(myFolder.getsFolderHash());
            folder.setPercentdone(0.0);
            folder.setFirstaccess(sFirstAccess);
            folder.setLastaccess(sLastAccess);
            folder.setLastaccessTime(sLastAccessTime);
            folder.setFinished(false);
            folder.setIszipfile(myFolder.isZipFolder());

            //adding to database
            InsertedFolderId[0] = (int) DatabaseClient.getInstance(getApplicationContext()).getAppDatabase()
                    .FolderDao()
                    .insert(folder);
            return true;
        })
                .subscribeOn(Schedulers.newThread())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe((result) -> {
            if (result) {
                if (myFolder.isZipFolder()) {
                    saveZipFiles();
                } else {
                    saveFiles();
                }
            }
        })
        ;
    }

    private void saveZipFiles() {
        nbFileToSave=audioFileArrayList.size();nbFileSaved=0;
        int i = 0; int progress = 0; String txtProgress = "";
        for (String s : audioFileArrayList) {
            i++;
            progress = (int) i * 100 / audioFileArrayList.size();
            txtProgress = progress + "% - reading file n°" + i + "/" + audioFileArrayList.size() + "\n" + getFileNameFromPath(s);
            progressBar.setProgress(progress);
            progressBarText.setText(txtProgress);
            myLog("Call save " + s);
            saveFile(s, InsertedFolderId[0], progress, txtProgress);
        }
    }

    private void saveFiles() {
        nbFileToSave=audioFileArrayList.size();nbFileSaved=0;
        int i = 0; int progress = 0; String txtProgress = "";
        for (DocumentFile f :myZikFileList) {
            if (f.getType() != null) {
                if (f.getType().equals("audio/mpeg")) {
                    i++;
                    progress = (int) i*100/audioFileArrayList.size();
                    txtProgress = progress + "% - saving file n°" + i + "/" + audioFileArrayList.size() + "\n" + f.getName();
                    progressBar.setProgress(progress);
                    progressBarText.setText(txtProgress);
                    myLog("saving file " + f.getName());
                    saveFile(f.getName(), InsertedFolderId[0], progress, txtProgress);
                }
            }
        }
    }

    private void saveFile(String sZikFileName, int mFolderId, int progress, String txtProgress) {
            // creating file
        ZikFile zikFile = new ZikFile();
        zikFile.setName(sZikFileName);
        zikFile.setIdFolder(mFolderId);
        zikFile.setFolderName(myFolder.getsFolderName());
        zikFile.setPercentdone(0.0);
        zikFile.setPosition(0);
        zikFile.setPath(myFolder.getsRealFolderPath());
        zikFile.setIszipfile(myFolder.isZipFolder());

        // get Media Duration
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
/*
        Completable.fromAction(new Action() {
            @Override
            public void run() throws Throwable {
                DatabaseClient
                        .getInstance(getApplicationContext())
                        .getAppDatabase()
                        .ZikFileDao()
                        .insert(zikFile);
            }
*/
        Observable.fromCallable(() -> {
            //adding to database
            return DatabaseClient
                        .getInstance(getApplicationContext())
                        .getAppDatabase()
                        .ZikFileDao()
                        .insert(zikFile);

        })
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((result) -> {
                    if (result != null) {
                        myLog("File Added - SQL result = " + result);
                        nbFileSaved++;
                        if (nbFileSaved == nbFileToSave) {
                            myLog("All files have been saved ");
                            updateFolderDuration();
                        }
                    } else {
                        myLog("ca chie");
                    }
                }, throwable -> Log.e("toto", "Throwable2 " + throwable.getMessage()));
    }


    private void updateFolderDuration() {
        Observable.fromCallable(() -> {
            String strSQL = "UPDATE Folder " +
                    " SET duration = (SELECT SUM(duration) " +
                    " FROM ZikFile " +
                    " WHERE Folder.id = ZikFile.idFolder )";
            SimpleSQLiteQuery query = new SimpleSQLiteQuery(strSQL);
            return DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .FolderDao()
                    .runRawSql(query);
        })
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    myLog("Folder Duration Updated : runRawSQL result = " + result);
                    HideProgress();
                    finish();
                }, throwable -> Log.e("toto", "Throwable " + throwable.getMessage()));
    }


    // DUREE AUDIO
    private long getMediaDurationFromPath(String zePath) throws IOException {
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        long duration = 0;
        if (myFolder.isZipFolder()) {
            InputStream inputStream = null;
            FileOutputStream out = null;
            try {
                inputStream = zipFile.getInputStream(zipFile.getEntry(zePath));
                File f = File.createTempFile("_AUDIO_", getExtension(zePath));
                f.deleteOnExit();
                out = new FileOutputStream(f);
                copyStream(inputStream,out);

                mediaMetadataRetriever.setDataSource(f.getPath());
                duration = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));

                f.delete();

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

    private void ShowProgress() {
        //animateView(progressOverlay, View.VISIBLE, 0.4f, DELAY_ANIMATION);
        animateView(progressBarOverlay, View.VISIBLE, 1, DELAY_ANIMATION);
        progressBarOverlay.setVisibility(View.VISIBLE);
        progressBarOverlay.bringToFront();
        bOpenFolder.setVisibility(View.INVISIBLE);
        bOpenZipFile.setVisibility(View.INVISIBLE);
        bSearchLibrivox.setVisibility(View.INVISIBLE);
        bSearchLitteratureaudio.setVisibility(View.INVISIBLE);
        progressBar.setProgress(2);
        progressBarText.setText("init");
    }

    private void HideProgress() {
        animateView(progressBarOverlay, View.GONE, 0, DELAY_ANIMATION);
    }



    private void myToast(String str) {
        myLog(str);
        Toast.makeText(getApplicationContext(),str,Toast.LENGTH_SHORT).show();
    }

    /**
     * Handle the onPostCreate() hook to call permission helper to handle all
     * permission requests using the API 23 permission model framework.
     * <p>
     * The framework will callback to request this application to provide a
     * descriptive reason for the permission request that is then displayed to
     * the user. The user has the opportunity to grant or deny the permission
     * request. The callback is also handled automatically by the permission
     * helper class.
     *
     * @param savedInstanceState A saved state or null.
     */
    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        checkPermissionsReadStorage2();
        super.onPostCreate(savedInstanceState);
    }

    private void checkPermissionsReadStorage2() {
        // Submit a permission request to ensure that this app has the
        // required permissions for writing and reading external storage.
        mPermissionRequest = PermissionRequest
                .with(this)
                .permissions(Manifest.permission.READ_EXTERNAL_STORAGE)
                //Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .rationale(R.string.permission_read_write_rationale)
                //.granted(R.string.permission_read_write_granted)  // Tonio no need to display message if granted OK
                .denied(R.string.permission_read_write_denied)
                .snackbar((ViewGroup)findViewById(android.R.id.content))
                .submit();
    }

    /**
     * API 23 (M) callback received when a permissions request has been
     * completed. Redirect callback to permission helper.
     */
    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        // Redirect hook call to permission helper method.
        if (mPermissionRequest != null) {
            mPermissionRequest.onRequestPermissionsResult(requestCode,
                    permissions,
                    grantResults);
            mPermissionRequest = null; // request no longer needed
        }
    }

}
