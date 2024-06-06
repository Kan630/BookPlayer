package com.driot.bookplayer.utils;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.provider.MediaStore;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.sqlite.db.SimpleSQLiteQuery;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.FolderAttrib;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Option;
import com.driot.tonylib.KanLogger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Date;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Collections;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static com.driot.bookplayer.global.Var.FOLDER_UNZIPPED;
import static com.driot.bookplayer.global.Var.PATH_CHECK_AUTOTEST;
import static com.driot.bookplayer.utils.Tonio.formatNameForDisplay;
import static com.driot.bookplayer.utils.Tonio.fileExists;
import static com.driot.bookplayer.utils.Tonio.getFileNameFromPath;
import static com.driot.bookplayer.utils.Tonio.stripExtension;
import static com.driot.tonylib.TonioCommonStuff.deleteExtension;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 23/11/20
 */

// TODO check if Service is Busy before starting another import

public class AddResourceService
        extends Service
        implements CopyFileService.Callbacks, UnzipService.Callbacks
{

    AddResourceService.Callbacks mCallBacks;

    CopyFileService mCopyFileService;
    Boolean mCopyFileServiceBound;
    boolean boundToCopyFileService;

    UnzipService mUnzipService;
    Boolean mUnzipServiceBound;
    boolean boundToUnzipService;

    /**
     * steps are
     *   1  Init
     *   2  Scan for Audio Files
     *   3  Check Folder already exist
     *   4  Check Available space
     *   5  Copy
     *   6  Unzip
     *   7  Get File Duration
     *   8  finish
     */
    public static final int[] PROGRESS_ZIP_COPY = {5, 5, 10, 20, 25, 50, 75, 90};
    public static final int[] PROGRESS_ZIP_NOCOPY = {5, 5, 10, 20, 25, 50, 75, 90};
    public static final int[] PROGRESS_FILE_COPY = {5, 5, 15, 30, 45, 90, 90, 95};
    public static final int[] PROGRESS_FILE_NOCOPY = {5, 20, 40, 60, 90, 90, 90, 95};
    public static final int[] PROGRESS_FOLDER_COPY = {5, 5, 15, 30, 45, 90, 90, 95};
    public static final int[] PROGRESS_FOLDER_NOCOPY = {5, 20, 30, 40, 40, 40, 50, 95};
    public static int[] PROGRESS;
/*
    public static final int PROGRESS_CHECK_FOLDER_EXIST_ZIP = 1;
    public static final int PROGRESS_COPY_START = 3;
    public static final int PROGRESS_COPY_END = 20;
    public static final int PROGRESS_UNZIP_START = 20;
    public static final int PROGRESS_UNZIP_END = 80;
    public static final int PROGRESS_SORTING_ZIP = 80;
    public static final int PROGRESS_SAVE_DB_START_ZIP = 90;
    public static final int PROGRESS_SAVE_DB_END_ZIP = 100;

    public static final int PROGRESS_CHECKING_NO_ZIP = 10;
    public static final int PROGRESS_SORTING_NO_ZIP = 20;
    public static final int PROGRESS_CHECK_FOLDER_EXIST_NO_ZIP = 30;
    public static final int PROGRESS_SAVE_DB_START_NO_ZIP = 30;
    public static final int PROGRESS_SAVE_DB_END_NO_ZIP = 100;

 */

    public static final String PROGRESS_SORTING_TEXT = "listing and sorting Tracks";


    private final IBinder binder = new AddResourceServiceBackgroundBinder();

    private FolderAttrib myFolder;
    private ArrayList<String> audioFileArrayList;
    private final int[] InsertedFolderId = {0};

    private boolean comingFromZip = false;

    private int nbFileSaved, nbFileToSave;

    private Uri uri_given;
    private String type_given;
    private String destinationFolderName;
    private String destinationFolderPath;
    private String zipDestinationFolderPath;
    private String zipDestinationFolderName;

    private String fullPath;

    public static boolean isBusy;

    // Callbacks
    //-----------------------------
    public interface Callbacks{
        void updateProgress(String progressText, int progressVal);
        void updateError(String errorText);
        void updateEnd();
        void tellHeader(String txt);
        void tellNonBlockingError(String txt);
    }
    public void registerClient(Activity activity){
        this.mCallBacks = (AddResourceService.Callbacks)activity; // done in onServiceConnected()
    }

    // binder
    //-----------------------------
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        myLog("onBind()    intent:" + intent.getDataString());
        return binder;
    }
    @Override
    public boolean onUnbind(Intent intent) {
        myLog("onUnBind()    intent:" + intent.getDataString());
        try {
            if (mUnzipServiceBound != null && mUnzipServiceBound) unbindService(unzipServiceConnection);
            mUnzipServiceBound = false;
        } catch (Exception e) {
            myLogE("onUnbind - error unbindService Unzip : " + e.getMessage());
            e.printStackTrace();
        }
        try {
            if (mCopyFileServiceBound != null && mCopyFileServiceBound) unbindService(copyFileServiceConnection);
            mCopyFileServiceBound = false;
        } catch (Exception e) {
            myLogE("onUnbind - error unbindService CopyFile : " + e.getMessage());
            e.printStackTrace();
        }
        return super.onUnbind(intent);
    }
    public class AddResourceServiceBackgroundBinder extends Binder {
        public AddResourceService getService() {
            myLog("class AddResourceService BackgroundBinder.getService(); ");
            return AddResourceService.this;
        }
    }
    // services
    //-----------------------------
    private final ServiceConnection copyFileServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            myLog("copyFileServiceConnection - onServiceConnected");
            CopyFileService.CopyFileServiceBackgroundBinder binder = (CopyFileService.CopyFileServiceBackgroundBinder) service;
            mCopyFileService = binder.getService();
            mCopyFileService.registerClient(AddResourceService.this);
            mCopyFileServiceBound = true;
            myLog("copyFileServiceConnection - launch init()");
            mCopyFileService.init();
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            myLog("copyFileServiceConnection - OnServiceDisconnected");
            if (mCopyFileServiceBound != null && mCopyFileServiceBound) {
                mCopyFileService.unbindService(copyFileServiceConnection);
                mCopyFileServiceBound = false;
            }
        }
    };
    private final ServiceConnection unzipServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            myLog("unzipServiceConnection - onServiceConnected");
            UnzipService.UnzipServiceBackgroundBinder binder = (UnzipService.UnzipServiceBackgroundBinder) service;
            mUnzipService = binder.getService();
            mUnzipService.registerClient(AddResourceService.this);
            mUnzipServiceBound = true;
            myLog("unzipServiceConnection - launch init()");
            mUnzipService.init();
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            myLog("unzipServiceConnection - OnServiceDisconnected");
            if (mUnzipServiceBound != null && mUnzipServiceBound) {
                mUnzipService.unbindService(unzipServiceConnection);
                mUnzipServiceBound = false;
            }
        }
    };
    private void launchCopyFileService(Uri uri, String destinationFolderPath, String destinationFileName, String type) {
        myLog("launchCopyFileService - prepare intent");
        Intent intentCopyFileService = new Intent(this, CopyFileService.class);
        intentCopyFileService.putExtra("Uri", uri);
        intentCopyFileService.putExtra("destinationFolderPath", destinationFolderPath);
        intentCopyFileService.putExtra("destinationFileName", destinationFileName);
        intentCopyFileService.putExtra("type", type);
        //copiedZipFileFullPath = destinationFolderPath + "/" + destinationFileName;
        boundToCopyFileService = false;
        try {
            boundToCopyFileService = bindService(intentCopyFileService, copyFileServiceConnection, Context.BIND_AUTO_CREATE); //error Log : Activity XXX has leaked ServiceConnection
        } catch (Exception e) {
            myLogE("ERROR bind to Service in launchCopyFileService ");
            myLogE(e.getMessage());
        }
        mCallBacks.tellHeader(destinationFileName);
        myLog("call start & bind to copyFileService from launchCopyFileService - bound result :" + boundToCopyFileService + "");
    }
    private void launchUnzipService(String zipFilePath, String destinationFolderPath) {
        myLog("launchUnzipService");
        Intent intentUnzipService = new Intent(this, UnzipService.class);
        intentUnzipService.putExtra("zipFilePath", zipFilePath);
        intentUnzipService.putExtra("destinationFolderPath", destinationFolderPath);
        boundToUnzipService = false;
        try {
            boundToUnzipService = bindService(intentUnzipService, unzipServiceConnection, Context.BIND_AUTO_CREATE); //error Log : Activity XXX has leaked ServiceConnection
        } catch (Exception e) {
            myLogE("ERROR bind to Service in launchUnzipService ");
            myLogE(e.getMessage());
        }
        myLog("call start & bind to unzipService from launchUnzipService - bound result :" + boundToUnzipService + "");
    }

    // native methods
    //-----------------------------
    @Override
    public void onCreate() {
        myLog("onCreate()");
        super.onCreate();
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        uri_given = intent.getParcelableExtra("Uri");
        type_given =  intent.getStringExtra("type");
        myLog("onStartCommand() - setting module private var from intent extras");
        return START_NOT_STICKY;
    }






        // single file
    ///////////////////////////
    private void populateArrayListOfTracksFromFile(DocumentFile dfPickedDir) {
        myLog("populateArrayListOfTracksFromFile [" + dfPickedDir.getUri() + "] - single file");

        //resetting uri
        Uri uri;
        uri = dfPickedDir.getUri();

        if (dfPickedDir != null && !(dfPickedDir.isDirectory())) {

            // constructeur pour mon pti folder
            myFolder = new FolderAttrib(getApplicationContext(), uri, Option.getCopyFile(this), type_given);
            if (myFolder.getFolderName()==null) {
                tellError("Error while creating record, cancelling operation");
            }
            mCallBacks.tellHeader(myFolder.getFolderName());

            if (myFolder.isFolderKO()) {
                String error = getString(R.string.Error_Import_FilePathKO);
                tellError(error);
            } else {
                myLog("file ok");

                audioFileArrayList = new ArrayList<>();

                addAudioFileUnique(dfPickedDir);

            }
        } else {
            tellError(getString(R.string.Error_Import_IsNotFile));
        }
        goFolder();
    }

    private void populateArrayListOfTracksFromFolder(DocumentFile dfPickedDir, boolean comingFromZip) {
        myLog("populateArrayListOfTracksFromFolder - DocumentFile [" + dfPickedDir.toString() + "]");
        tellProgress(PROGRESS[1], "analysing folder content...");
        //resetting uri
        Uri uri;
        if (comingFromZip) {
            uri = dfPickedDir.getUri();
        } else {
            //uri = uri_given;
            uri = dfPickedDir.getUri();
        }

        myLog("populateArrayListOfTracksFromFolder - New Uri deducted [" + uri.toString() + "]");

        // Si c'est pas un dossier, on prend le dossier parent...
        if (!dfPickedDir.isDirectory()) {
            DocumentFile df0 = DocumentFile.fromTreeUri(this, uri);
            if (df0 != null) {
                dfPickedDir = df0.getParentFile();
            } else {
                tellError("could not get parent directory");
            }
            myLog("Parent Folder taken in place");
        }

        if (dfPickedDir != null && dfPickedDir.isDirectory()) {

            // constructeur pour mon pti folder
            myFolder = new FolderAttrib(getApplicationContext(), uri, Option.getCopyFile(this), type_given);
            if (myFolder.getFolderName()==null) {
                tellError("Error while creating record, cancelling operation");
            }
            mCallBacks.tellHeader(myFolder.getFolderName());

            if (myFolder.isFolderKO()) {
                String error = getString(R.string.Error_Import_FolderPathKO);
                if (myFolder.isLocatedInDownloadFolder())  error += "... " + getString(R.string.Error_Import_BetterTryNoDownloadFolder);
                tellError(error);
            } else {
                myLog("myFolder constructor ok - [" + myFolder.getFolderName() + "]");
                myLog("running recursive scan for audio file in a background thread");

                audioFileArrayList = new ArrayList<>();

                DocumentFile finalDfPickedDir = dfPickedDir; //thread needs 'final' arg
                Thread backgroundThread = new Thread(() -> {
                    addAudioFileRecursive(finalDfPickedDir);
                    myLog("addAudioFileRecursive done, sorting now...");
                    Collections.sort(audioFileArrayList, new Utils.AlphanumericComparator());

                    if (audioFileArrayList.size()==0) {
                        myLog("No File found in directory : [" + finalDfPickedDir.getName() + ']');
                    } else {
                        myLog(audioFileArrayList.size() + " files found in directory : [" + finalDfPickedDir.getName() + ']');
                    }
                    goFolder();
                });
                backgroundThread.start();
            }
        } else {
            tellError(getString(R.string.Error_Import_IsNotFolder));
        }
    }

    private void addAudioFileUnique(DocumentFile df) {
        myLog("* New Audio File : [" +  df.getName() + ']');
        audioFileArrayList.add(df.getName());
    }
    private void addAudioFileRecursive(DocumentFile f0) {
        addAudioFileRecursive(f0,"");
    }
    private void addAudioFileRecursive(DocumentFile f0, String recursivFolder) {
        String l_audioFilePath;
        for (DocumentFile f1 : f0.listFiles()) {
            if (f1.isDirectory()) {
                addAudioFileRecursive(f1,recursivFolder + f1.getName() + '/');
            } else {
                if (f1.getType() != null) {
                    if (f1.getType().equals("audio/mpeg") || f1.getType().equals("audio/mp4")) {
                        l_audioFilePath = recursivFolder + f1.getName();
                        myLog("* New Audio File : [" + l_audioFilePath + ']');
                        tellProgress(20, "Scanning for Audio Files..... \n[" +  l_audioFilePath + ']');
                        audioFileArrayList.add(l_audioFilePath);
                    }
                }
            }
        }
    }

    ///////////////////////////////////////
    // INIT
    ///////////////////////////////////////

    public void init() {
        myLog("....");
        myLog("....");
        myLog("*********************************************************************************************************");
        myLog("init() - ** type = " + type_given + " **");
        myLog("init() - ** uri = " + uri_given.toString() + " **");
        myLog("*********************************************************************************************************");
        isBusy = true;
        DocumentFile dfPickedDir;
        String mime = null;


        if (Option.getCopyFile(this)) {

        }

        switch (type_given) {
            ///---------------------------------------------
            /// FILE
            ///---------------------------------------------
            case "File":
                PROGRESS = Option.getCopyFile(this) ? PROGRESS_FILE_COPY : PROGRESS_FILE_NOCOPY;
                try {
                    dfPickedDir = DocumentFile.fromSingleUri(this, uri_given);
                } catch (Exception e) {
                    tellError("error getting DocumentFile.fromSingleUri : " + e.getMessage());
                    break;
                }
                if (dfPickedDir == null) {
                    tellError("error getting DocumentFile.fromSingleUri");
                    break;
                }
                try {
                    mime = dfPickedDir.getType();
                } catch (Exception e) {
                    tellError("Mime Type could not be found");
                    break;
                }
                if (mime == null) {
                    tellError("Mime Type could not be found");
                    break;
                }

            // ok mime found
                if (mime.equals("audio/mp4")) { //   application/mp4   .m4b

                    myLog("MP4 : [" + dfPickedDir.getType() + "]");

                    populateArrayListOfTracksFromFile(dfPickedDir);
/*
                    //TODO : maybe you can just unzip it...
                    
                    // TODO
                    /// EXPLODE MP4 in MP3s in local folder...
                    /// then use the import folder thing
                    destinationFolder = getFilesDir().getAbsolutePath() + "/" + FOLDER_MP4 + "/" + dfPickedDir.getName(); // myFolder.getsFolderName_withUnderscore();
                    destinationFolder = destinationFolder.replace(".m4b","");
                    myLog("destinationFolder : [" + destinationFolder + "]");
                    String sourceFile = dfPickedDir.getUri().getPath();
                    myLog("sourceFile : [" + sourceFile + "]");
                    AudioSplitter.splitM4BToMP3(sourceFile, destinationFolder);


                    localUnzipFolder = new File(destinationFolder);
                    futureUri = Uri.fromFile(localUnzipFolder).toString();
                    String destinationPath = getFilesDir().getAbsolutePath() + "/" + FOLDER_MP4 + "/" + myFolder.getsFolderName_withUnderscore() + ".zip";
                    internalZipFile = new File(destinationPath);
                    finalLocalFolder = localUnzipFolder;
                    resourceSelected = populateArrayListOfTracksFromFolder();
                    */

                } else if (mime.startsWith("audio/")) {     // audio/mpeg

                    myLog("not MP4 : [" + mime + "]");
                    populateArrayListOfTracksFromFile(dfPickedDir);

                } else {
                    tellError("Not an audio ?   ... This MIME type is not supported : [" + mime + "]");
                    break;
                }
                break;



            ///---------------------------------------------
            /// FOLDER
            ///---------------------------------------------
            case "Folder":
                PROGRESS = Option.getCopyFile(this) ? PROGRESS_FOLDER_COPY : PROGRESS_FOLDER_NOCOPY;

                // TODO First thing : check if folder already exists, now checked after scan of files, just before DB insertion
                //checkIfFolderAlreadyExist2();

                tellProgress(PROGRESS[0], "checking Folder.");
                try {
                    dfPickedDir = DocumentFile.fromTreeUri(this, uri_given);
                } catch (Exception e) {
                    tellError("Error reading picked Folder.... DocumentFile.fromTreeUri : " + e.getMessage());
                    break;
                }
                tellProgress(PROGRESS[1], PROGRESS_SORTING_TEXT);
                if (dfPickedDir == null) {
                    tellError("Error reading picked Folder... dfPickedDir is null");
                } else {
                    populateArrayListOfTracksFromFolder(dfPickedDir, false);
                }
                break;

            ///---------------------------------------------
            /// ZIP FILE
            ///---------------------------------------------
            case "ZIP":
                PROGRESS = Option.getCopyFile(this) ? PROGRESS_ZIP_COPY : PROGRESS_ZIP_NOCOPY;
                comingFromZip = true;
                myLog("ZIP : copy locally before everything else");
                myLog("Picked Uri = [" + uri_given.toString() + "]");

                // get the folder name = the zip file true Name without extension
                destinationFolderName = "";
                if (uri_given.getPath().contains(PATH_CHECK_AUTOTEST)) {  // <-- autotest
                    destinationFolderName =  formatNameForDisplay(getFileNameFromPath(uri_given.getPath()));
                } else {
                    try {
                        InputStream uriInputStream = this.getContentResolver().openInputStream(uri_given);
                        if (uriInputStream != null) {
                            destinationFolderName = getContentName(this.getContentResolver(), uri_given);
                            if (destinationFolderName == null) {
                                tellError("could not get name of zipfile");
                            }
                            myLog("destinationFolderName = [" + destinationFolderName + "] - with getContentName(getContentResolver...");
                            destinationFolderName = pruneZipFileName(destinationFolderName);
                            uriInputStream.close();
                        } else {
                            tellError("Could not open input stream from selected Uri [" + uri_given.toString() + "]");
                        }
                    } catch (Exception e) {
                        tellError("error dealing with selected Uri : " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                // check Not Already Imported
                //*****************************
                myLog("Checking Folder doesn't already exist in DB : " + destinationFolderName);
                Observable.fromCallable(() -> {
                    boolean bcheckIfFolderExist = false;
                    long lcheckIfFolderExist = DatabaseClient
                            .getInstance(getApplicationContext())
                            .getAppDatabase()
                            .FolderDao()
                            .folderAlreadyExist_checkFolderName(destinationFolderName);
                    if (lcheckIfFolderExist>0) { bcheckIfFolderExist = true;}
                    return bcheckIfFolderExist;
                    //TODO if only name the same, just import with a new name... but is this possible ? wanted ?
                }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(result -> {
                    if (result) {
                        myLogE("KO, folder does already exist in DB : [" + destinationFolderName + "]");
                        tellError(getString(R.string.Error_Import_FolderAlreadyImported) + "  [" + destinationFolderName + "]");
                    } else {
                        myLog("OK, folder doesn't already exist in DB");
                        tellProgress(PROGRESS[2], getResources().getString(R.string.Import_Progress_check_not_already_imported));
                        copyFileLocal(uri_given
                                , getFilesDir().getAbsolutePath() + "/" + FOLDER_UNZIPPED + "/" + destinationFolderName
                                , destinationFolderName + ".zip"
                                , type_given
                                ); //launch a service, next step through callbacks
                    }
                }, throwable -> {
                    tellError(getResources().getString(R.string.Error_Import_checking_Folder_Exists) + " : [" + throwable.getMessage() + "]");
                });
                return;
        default:
                myLogE("Incorrect type : **" + type_given + "**");
        }
    }

    private void goFolder() {
        if (audioFileArrayList != null) {
            if (audioFileArrayList.size() == 0) {
                tellError(getString(R.string.Error_Import_NoMediaInFolder));
            } else {
                myLog(audioFileArrayList.size() + " " + getString(R.string.Import_nMediaInFolder));
                checkIfFolderAlreadyExist();
            }
        } else {
            tellError(getString(R.string.Error_Import_NoMediaInFolder));
        }
    }

    private void checkIfFolderAlreadyExist() {
        tellProgress(PROGRESS[2], getResources().getString(R.string.Import_Progress_check_not_already_imported));
        myLog("checkIfFolderAlreadyExist() - FolderName = [" + myFolder.getFolderName() + "]");
        Observable.fromCallable(() -> {
            boolean bcheckIfFolderExist = false;
            long lcheckIfFolderExist = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .FolderDao()
                    .folderAlreadyExist_checkFolderName(myFolder.getFolderName());
            if (lcheckIfFolderExist>0) { bcheckIfFolderExist = true;}
            return bcheckIfFolderExist;
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(result -> {
            if (result) {
                tellError(getString(R.string.Error_Import_FolderAlreadyImported));
            } else {
                myLog("ok on continue -       (folder does not already exist)");
                copyFolder();
            }
        }, throwable -> {
            tellError(getResources().getString(R.string.Error_Import_checking_Folder_Exists) + " - folderName = [" + myFolder.getFolderName() + "] - error message : [" + throwable.getMessage() + "]");
        });
    }
    private void copyFolder() {
        if (type_given.equals("ZIP")) {
            // Has already been copied and unzipped...
            myFolder.setForceFolderPath(zipDestinationFolderPath);
            saveFolder();
        } else {
            if (Option.getCopyFile(this)) {
                tellProgress(PROGRESS[3], "preparing Folder copy...");
                if (type_given.equals("Folder")) {
                    String folderPath = getFilesDir().getAbsolutePath() + "/" + FOLDER_UNZIPPED + "/" + myFolder.getFolderName();
                    String fileName = myFolder.getFileName(this);
                    fullPath = folderPath;
                    myLog("**** fullPath = [" + fullPath + "]");
                    copyFileLocal(myFolder.getUri()
                            , folderPath
                            , "tutu" // fileName needed to check if already exist in DB
                            , type_given);
                } else if (type_given.equals("File")) {
                    String folderPath = getFilesDir().getAbsolutePath() + "/" + FOLDER_UNZIPPED + "/" + myFolder.getFolderName();
                    String fileName = myFolder.getFileName(this);
                    fullPath = folderPath + "/" + fileName;
                    myLog("**** fullPath = [" + fullPath + "]");
                    copyFileLocal(myFolder.getUri()
                            , folderPath
                            , fileName
                            , type_given
                    );
                } else {
                    myLogE("Wrong file type : " + type_given);
                }
            } else {
                saveFolder();
            }
        }
    }



    private void copyFileLocal(Uri uri, String destinationFolderPath, String destinationName, String type_given) {
        if (type_given.equals("ZIP")) { //reset variable because was done in observable stuff
            this.zipDestinationFolderPath = destinationFolderPath;
            this.zipDestinationFolderName = destinationName;
        }
        long fileSize = -1L;
        File externalFile = new File(uri.getPath());
        if (externalFile.exists()) fileSize = externalFile.length();
        if (fileSize > 0) {
            myLog("ze Size : " + fileSize);
        } else {
            myLog("ERR : Cannot Check Size .... Size = " + fileSize + " .... Never Mind... let's copy");
        }
        myLog("Future Folder Path : [" + destinationFolderPath + "]");
        myLog("call to launchCopyFileService " +
                "\n.   from Uri [" + uri + "] " +
                "\n.   to Folder [" + destinationFolderPath + "] " +
                "\n.   with Name [" + destinationName + "]" +
                "\n.   for type = [" + type_given + "]");
        launchCopyFileService(uri, destinationFolderPath, destinationName, type_given);
    }

    private void unzipZipLocal() {
        String zeZipFilePath = zipDestinationFolderPath + "/" + zipDestinationFolderName;
        String zeDestinationFolderPath = zipDestinationFolderPath;
        myLog("Launching Unzip service with arguments" +
                "\n.    ZipFilePath = [" + zeZipFilePath + "]" +
                "\n.    DestinationFolderPath = [" + zeDestinationFolderPath + "]"
        );
        launchUnzipService(zeZipFilePath, zeDestinationFolderPath);
    }

    private void saveFolder() {
        tellProgress(PROGRESS[6], "preparing Folder copy...");

        final Time sFirstAccess = new Time(System.currentTimeMillis());
        final Date sLastAccess = new Date(System.currentTimeMillis());
        final Time sLastAccessTime = new Time(System.currentTimeMillis());

        Observable.fromCallable(() -> {
            //creating a Folder
            Folder folder = new Folder();
            folder.setName(myFolder.getFolderName());
            folder.setPath(myFolder.getFolderPath());
            //folder.setUri(myFolder.getUriString()); //2023-10-22
            folder.setUri("tototititata/dksjgf"); //2024-06-05
            folder.setHash("0"); //2023-10-22 code removed
            folder.setPercentdone(0.0);
            folder.setFirstaccess(sFirstAccess);
            folder.setLastaccess(sLastAccess);
            folder.setLastaccessTime(sLastAccessTime);
            folder.setFinished(false);
            folder.setIszipfile(false); //2023-10-22 code removed for live zip reading

            //adding to database
            InsertedFolderId[0] = (int) DatabaseClient.getInstance(getApplicationContext()).getAppDatabase()
                    .FolderDao()
                    .insert(folder);
            return true;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((result) -> {
                    if (result) {
                        myLog("Folder Saved in DB - checking files");
                        tellProgress(PROGRESS[6],getResources().getString(R.string.Import_Progress_checkingFiles));
                        saveFiles();
                    }
                }, throwable -> {
                    myLogE("creating Folder in DB : " + throwable.getMessage());
                    tellError(getResources().getString(R.string.Error_Import_Creating_Folders) + " for path : " + throwable.getMessage());
                })
        ;
    }

    private void saveFiles() {
        nbFileToSave = audioFileArrayList.size();
        nbFileSaved = 0;
        Thread one;
        one = new Thread(() -> {
            int i = 0;
            int progress;
            String txtProgress;
            for (String s : audioFileArrayList) {
                i++;
                progress = (int) PROGRESS[6] + (i * 100 / audioFileArrayList.size())*(PROGRESS[7]-PROGRESS[6])/100;
                txtProgress = progress + "% - " + getString(R.string.Add_resource_reading_file) + " n°" + i + "/" + audioFileArrayList.size() + "\n" + getFileNameFromPath(s);
                myLog("Registering file [" + s + "]");
                saveFile(s, InsertedFolderId[0], i);
                tellProgress(progress,txtProgress);
            }
        });
        one.start();
    }

    private void saveFile(String sZikFileName, int mFolderId, int zeorder) {
        // creating file
        ZikFile zikFile = new ZikFile();
        zikFile.setName(sZikFileName);
        zikFile.setDisplayName(formatNameForDisplay(sZikFileName));
        zikFile.setIdFolder(mFolderId);
        zikFile.setZeorder(zeorder);
        zikFile.setFolderName(myFolder.getFolderName());
        zikFile.setPercentdone(0.0);
        zikFile.setPosition(0);
        zikFile.setPath(myFolder.getFolderPath());
        zikFile.setIszipfile(false); //2023-10-22 code removed for live zip reading

        // get Media Duration
        //--------------------------------
        String sFileFullPath;

        if (myFolder.isSingleFile()) {
            sFileFullPath = myFolder.getFolderPath() + File.separator + sZikFileName;
        } else {
            sFileFullPath = myFolder.getFolderPath() + File.separator + sZikFileName;
        }
        try {
            myLog("Get Media Duration : " + sFileFullPath);
            zikFile.setDuration(getMediaDurationFromPath(sFileFullPath));
        } catch (IOException e) {
            myLogE("Error getting media duration : " + e.getMessage());
        }

        if (zikFile.getDuration() == 0) {
            myLog("File Not Added.... (Duration = 0)");
            nbFileSaved++;
            if (nbFileSaved == nbFileToSave) {
                myLog("*************************** All files have been processed. -- duration=0");
                updateFolderDuration();
            }
        } else {
            Observable.fromCallable(() -> {
                        //adding to database
                        return DatabaseClient
                                .getInstance(getApplicationContext())
                                .getAppDatabase()
                                .ZikFileDao()
                                .insert(zikFile);

                    })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((result) -> {
                                if (result != null) {
                                    myLog("File Added.... SQL result (=id) = [" + result + "]");
                                    nbFileSaved++;
                                    if (nbFileSaved == nbFileToSave) {
                                        myLog("*************************** All files have been processed. -- OK");
                                        updateFolderDuration();
                                        if (Option.getDeleteSourceFile(this) && type_given=="ZIP") {
                                            deleteSourceFile();
                                        }
                                    }
                                } else {
                                    tellError("error saving ZikFile in DB");
                                }
                            }, throwable -> {
                                tellError(getResources().getString(R.string.Error_Import_saving_file_DB) + sZikFileName + " : " + throwable.getMessage());
                            }
                    );
        }
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
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    myLog("Folder Duration Updated : runRawSQL result = " + result);
                    tellEnd();
                }, throwable -> {
                    myLogE("Error in Folder Duration Update. " + throwable.getMessage());
                    tellNonBlockingError(getResources().getString(R.string.Error_Import_computing_folder_duration) + " : " +  throwable.getMessage());
                    tellEnd();
                });
    }

    private void deleteSourceFile() {
        myLog("deleteSourceFile() - uri = [" + uri_given + "] [" + type_given + "]");
        DocumentFile dfPickedDir = null;
        if (type_given.equals("File") || type_given.equals("ZIP")) {
            try {
                dfPickedDir = DocumentFile.fromSingleUri(this, uri_given);
            } catch (Exception e) {
                tellError("error getting DocumentFile.fromSingleUri : " + e.getMessage());
            }
        } else if (type_given.equals("Folder")) {
            try {
                dfPickedDir = DocumentFile.fromTreeUri(this, uri_given);
            } catch (Exception e) {
                tellError("Error reading picked Folder.... DocumentFile.fromTreeUri : " + e.getMessage());
            }
        } else {
            myLogE("Incorrect type : **" + type_given + "**");
        }
        if (!(dfPickedDir == null)) {
            boolean okDelete = dfPickedDir.delete();
            if (okDelete) {
                myLog("source file deletion ok");
            } else {
                myLogE("Error during source file deletion");
            }
        } else {
            myLogE("deleteSourceFile() => could not get ref to picked file");
        }
    }

    // DUREE AUDIO
    private long getMediaDurationFromPath(String zePath) throws IOException {
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        long duration = 0;
        if (fileExists(zePath)) {
            try {
                mediaMetadataRetriever.setDataSource(zePath);
                duration = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            } catch (Exception e) {
                e.printStackTrace();
                tellError(getResources().getString(R.string.Error_Import_track_duration_extraction) + " // path : " + zePath);
                myLogE("error getting duration of media : " + e.getMessage() + " for " + zePath);
            }
        } else {
            tellError(getResources().getString(R.string.Error_Import_track_duration_nofile) + " // path : " + zePath);
            myLogE("error getting duration of media, file does not exist in path : " + zePath);
        }
        myLogD("duration for [" + zePath + "] is " + duration);
        return duration;
    }

    public void tellProgress(int progressVal,String progressText) {
        myLogD("tellProgress : " + progressVal + " - " + progressText);
        mCallBacks.updateProgress(progressText, progressVal);
    }
    private void tellEnd() {
        mCallBacks.updateEnd();
        isBusy = false;
        myLog("killing Service");
        stopSelf();
    }
    private void tellError(String txt) {
        mCallBacks.updateError(txt);
        myLogE("tellError... [" + txt + "]");
        isBusy = false;
        myLog("tellError... killing Service");
        stopSelf();
    }


    // from FileHelper... used to copy zip locally in Android 11+
    private static String getContentName(ContentResolver resolver, Uri uri) {
        Cursor cursor = resolver.query(uri, null, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();
            int nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
            if (nameIndex >= 0) {
                String name = cursor.getString(nameIndex);
                cursor.close();
                return name;
            }
        }
        return null;
    }
    private String pruneZipFileName(String destinationFolderName) {
        String tmp = destinationFolderName;
        tmp = tmp.replace(" (1)","");
        tmp = tmp.replace(" (2)","");
        tmp = tmp.replace(" (3)","");
        tmp = tmp.replace(":"," ");
        tmp = deleteExtension(tmp);
        if (tmp !=  destinationFolderName) {
            myLog("destinationFolderName has been pruned : [" + tmp + "]");
        }
        return tmp;
    }

    private static String getsFolderName_withUnderscore_fromZipFileName(String zipFileName) {
        String tmp = zipFileName.replace(" ","_");
        tmp = stripExtension(tmp);
        return tmp;
    }

    /**
     **********************************
     *    COPY CALLBACKS received
     *********************************
     */
    @Override
    public void tellProgressClient_fromCopy(String progressText, int progressVal) {
        tellProgress(PROGRESS[4] + progressVal * (PROGRESS[5] - PROGRESS[4]) / 100, progressText);
    }
    @Override
    public void tellEndClient_fromCopy() {
        myLog("Copyfile tell End " + type_given);
        if (type_given.equals("ZIP")) {
            myLog("launch unzipZipLocal()");
            unzipZipLocal();
        } else {
            myFolder = new FolderAttrib(this, Uri.fromFile(new File(fullPath)), Option.getCopyFile(this), type_given);
            saveFolder();
        }

    }
    @Override
    public void tellErrorClient_fromCopy(String errorText) {
        myLogE("Copyfile tell Error");
        tellError(errorText);
    }
    /**
     **********************************
     *    UNZIP CALLBACKS received
     *********************************
     */
    @Override
    public void tellProgressClient_fromUnzip(String progressText, int progressVal) {
        tellProgress(PROGRESS[5] + progressVal * (PROGRESS[6] - PROGRESS[5]) / 100, progressText);
    }
    @Override
    public void tellErrorClient_fromUnzip(String errorText) {
        myLogE("Unzip service tell Error");
        tellError(errorText);
    }
    @Override
    public void tellEndClient_fromUnzip(String destinationFolderPath) {
        myLog("Unzip Service tells End : [" + destinationFolderPath + "]");
        tellProgress(PROGRESS[6], PROGRESS_SORTING_TEXT);
        DocumentFile dfPickedDir;
        try {
            dfPickedDir = DocumentFile.fromFile(new File(destinationFolderPath));
        } catch (Exception e) {
            myLogE("error getting DocumentFile.fromFile : " + e.getMessage());
            return;
        }
        populateArrayListOfTracksFromFolder(dfPickedDir, true);
    }
    /**
     **********************************
     *    CALLBACKS sent
     *********************************
     */
    @Override
    public void tellNonBlockingError(String txt) {
        mCallBacks.tellNonBlockingError(txt);
    }

    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }

}
