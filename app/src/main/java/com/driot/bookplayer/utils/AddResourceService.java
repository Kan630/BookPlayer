package com.driot.bookplayer.utils;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
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
import com.driot.tonylib.KanLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Date;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static com.driot.bookplayer.activities.OptionActivity.DEFAULT_COPY_ZIP_LOCAL;
import static com.driot.bookplayer.activities.OptionActivity.DEFAULT_UNZIP_LOCAL;
import static com.driot.bookplayer.activities.OptionActivity.SHARED_PREFERENCES_OPTIONS;
import static com.driot.bookplayer.global.Var.FOLDER_UNZIPPED;
import static com.driot.bookplayer.utils.Tonio.FormatNameForDisplay;
import static com.driot.bookplayer.utils.Tonio.fileExists;
import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Tonio.getFileNameFromPath;
import static com.driot.bookplayer.utils.Tonio.getMimeType;
import static com.driot.bookplayer.utils.Tonio.stripExtension;
import static com.driot.bookplayer.utils.Utils.copyStream;
import static com.driot.tonylib.TonioCommonStuff.deleteExtention;
import static com.driot.tonylib.TonioCommonStuff.extractName;

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


    private final IBinder binder = new AddResourceServiceBackgroundBinder();

    private FolderAttrib myFolder;
    private ArrayList<String> audioFileArrayList;
    private final int[] InsertedFolderId = {0};

    private boolean comingFromZip = false;

    private int nbFileSaved, nbFileToSave;

    private Uri uri;
    private String type;

    private DocumentFile pickedDir;

    private String destinationFolderName;
    private String destinationFolderPath;

    private boolean Zip_DoCopylocal;
    private boolean Zip_DoUnzip;

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
        this.mCallBacks = (AddResourceService.Callbacks)activity;
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
    private void launchCopyFileService(Uri uri, String destinationFolderPath, String destinationFileName) {
        myLog("launchCopyFileService - prepare intent");
        Intent intentCopyFileService = new Intent(this, CopyFileService.class);
        intentCopyFileService.putExtra("Uri", uri);
        intentCopyFileService.putExtra("destinationFolderPath", destinationFolderPath);
        intentCopyFileService.putExtra("destinationFileName", destinationFileName);
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
        uri = intent.getParcelableExtra("Uri");
        type =  intent.getStringExtra("type");
        myLog("onStartCommand()");
        return START_NOT_STICKY;
    }






        // single file
    ///////////////////////////
    private void populateArrayListOfTracksFromFile() {
        myLog("populateArrayListOfTracksFromFile [" + pickedDir.getUri() + "] - single file");

        uri = pickedDir.getUri();

        if (pickedDir != null && !(pickedDir.isDirectory())) {

            // constructeur pour mon pti folder
            myFolder = new FolderAttrib(getApplicationContext(), uri, false, true);
            if (myFolder.getsFolderName()==null) {
                tellError("Error while creating record, cancelling operation");
            }
            mCallBacks.tellHeader(myFolder.getsFolderName());

            if (myFolder.isFolderKO()) {
                String error = getString(R.string.Error_Import_FilePathKO);
                tellError(error);
            } else {
                myLog("file ok");

                audioFileArrayList = new ArrayList<>();

                addAudioFileUnique(pickedDir);

            }
        } else {
            tellError(getString(R.string.Error_Import_IsNotFile));
        }
        goFolder();
    }

    private void populateArrayListOfTracksFromFolder() {
        myLog("populateArrayListOfTracksFromFolder " + pickedDir.getUri());
        if (comingFromZip) {
            tellProgress(PROGRESS_SORTING_ZIP, "sorting Tracks");
        } else {
            tellProgress(PROGRESS_SORTING_NO_ZIP, "sorting Tracks");
        }

        uri = pickedDir.getUri();

        // Si c'est pas un dossier, on prend le dossier parent...
        if (!pickedDir.isDirectory()) {
            DocumentFile df0 = DocumentFile.fromTreeUri(this, uri);
            if (df0 != null) {
                pickedDir = df0.getParentFile();
            } else {
                tellError("could not get parent directory");
            }
            myLog("Parent Folder taken in place");
        }

        if (pickedDir != null && pickedDir.isDirectory()) {

            // constructeur pour mon pti folder
            myFolder = new FolderAttrib(getApplicationContext(), uri, false, false);
            if (myFolder.getsFolderName()==null) {
                tellError("Error while creating record, cancelling operation");
            }
            mCallBacks.tellHeader(myFolder.getsFolderName());

            if (myFolder.isFolderKO()) {
                String error = getString(R.string.Error_Import_FolderPathKO);
                if (myFolder.isLocatedInDownloadFolder())  error += "... " + getString(R.string.Error_Import_BetterTryNoDownloadFolder);
                tellError(error);
            } else {
                myLog("folder ok");
                myLog("scanning for audio file recursively from folder [" + pickedDir.getName() + "]");

                audioFileArrayList = new ArrayList<>();

                Thread backgroundThread = new Thread(() -> {
                    addAudioFileRecursive(pickedDir);
                    Collections.sort(audioFileArrayList, new Utils.AlphanumericComparator());

                    if (audioFileArrayList.size()==0) {
                        myLog("No File found in directory : [" + pickedDir.getName() + ']');
                    } else {
                        myLog(audioFileArrayList.size() + " files found in directory : [" + pickedDir.getName() + ']');
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
        myLog("init() - **" + type + "**");
        isBusy = true;
        String mime = null;

        switch (type) {
            ///---------------------------------------------
            /// FILE
            ///---------------------------------------------
            case "File":
                try {
                    pickedDir = DocumentFile.fromSingleUri(this, uri);
                } catch (Exception e) {
                    tellError("error getting DocumentFile.fromSingleUri : " + e.getMessage());
                    break;
                }
                if (pickedDir == null) {
                    tellError("error getting DocumentFile.fromSingleUri");
                    break;
                }
                try {
                    mime = pickedDir.getType();
                } catch (Exception e) {
                    tellError("Mime Type could not be found");
                    break;
                }
                if (mime == null) {
                    tellError("Mime Type could not be found");
                    break;
                }
                if (mime.equals("audio/mp4")) { //   application/mp4

                    myLog("MP4 : [" + pickedDir.getType() + "]");
                    populateArrayListOfTracksFromFile();
/*
                    // TODO
                    /// EXPLODE MP4 in MP3s in local folder...
                    /// then use the import folder thing
                    destinationFolder = getFilesDir().getAbsolutePath() + "/" + FOLDER_MP4 + "/" + pickedDir.getName(); // myFolder.getsFolderName_withUnderscore();
                    destinationFolder = destinationFolder.replace(".m4b","");
                    myLog("destinationFolder : [" + destinationFolder + "]");
                    String sourceFile = pickedDir.getUri().getPath();
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
                    populateArrayListOfTracksFromFile();

                } else {
                    tellError("Not an audio ?   ... This MIME type is not supported : [" + mime + "]");
                    break;
                }
                break;



            ///---------------------------------------------
            /// FOLDER
            ///---------------------------------------------
            case "Folder":
                // TODO First thing : check if folder already exists, now checked after scan of files, just before DB insertion
                //checkIfFolderAlreadyExist2();

                tellProgress(PROGRESS_CHECKING_NO_ZIP, "checking Folder.");
                try {
                    pickedDir = DocumentFile.fromTreeUri(this, uri);

                } catch (Exception e) {
                    tellError("error getting DocumentFile.fromTreeUri : " + e.getMessage());
                    break;
                }
                tellProgress(PROGRESS_SORTING_NO_ZIP, "listing Tracks");
                populateArrayListOfTracksFromFolder();
                break;

            ///---------------------------------------------
            /// ZIP FILE
            ///---------------------------------------------
            case "ZIP":
                comingFromZip = true;
                /// multiples possibilities
                //          lecture direct du zip tel quel (KO android 11)
                ///         unzip en local, et lecture local folder (KO android 11)
                ///         copy zip en local et lecture directe
                ///         copy zip en local, unzip en local, et lecture local folder

                ////*****************************************************************************************************
                //if Android >= 11, on copie direct en local avant toute chose.
                //if (Build.VERSION.SDK_INT >= 30) {
                myLog("copy locally before everything else");
                myLog("Picked Uri = [" + uri.toString() + "]");
                // get the folder name = the zip file true Name without extension
                destinationFolderName = "";
                try {
                    InputStream uriInputStream = this.getContentResolver().openInputStream(uri);
                    if (uriInputStream != null) {
                        destinationFolderName = getContentName(this.getContentResolver(), uri);
                        if (destinationFolderName == null) { tellError("could not get name of zipfile"); }
                        myLog("destinationFolderName = [" + destinationFolderName + "] - with getContentName(getContentResolver...");
                        destinationFolderName = pruneZipFileName(destinationFolderName);
                        uriInputStream.close();
                    } else {
                        tellError("Could not open input stream from selected Uri [" + uri.toString() + "]");
                    }
                } catch (Exception e) {
                    tellError("error dealing with selected Uri : " + e.getMessage());
                    e.printStackTrace();
                }

                // check Not Already Imported
                //*****************************
                checkIfFolderAlreadyExist_fromFolderName(destinationFolderName); //TODO without underscores ???
                return;
                // c'est la fin, on passe a continue3 si c'est good
                // (pour eviter le W/MIUIScout App:APP_SCOUT_WARNING et le HANG) on doit faire le lourd dans un backgroundThread (meme depuis un service... car il s'execute de base sur le MainUi)
            //}
        default:
                myLogE("Incorrect type : **" + type + "**");
        }
    }

    private void goFolder() {
        if (audioFileArrayList.size() == 0) {
            tellError(getString(R.string.Error_Import_NoMediaInFolder));
        } else {
            myLog(audioFileArrayList.size() + " " + getString(R.string.Import_nMediaInFolder));
            if (comingFromZip) {
                tellProgress(PROGRESS_SORTING_ZIP, "checking if not already imported");
            } else {
                tellProgress(PROGRESS_SORTING_NO_ZIP, "checking if not already imported");
            }
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
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(result -> {
            if (result) {
                tellError(getString(R.string.Error_Import_FolderAlreadyImported));
            } else {
                myLog("ok on continue -       (folder does not already exist)");
                if (!comingFromZip) {
                    tellProgress(PROGRESS_CHECK_FOLDER_EXIST_NO_ZIP,getResources().getString(R.string.Import_Progress_check_not_already_imported));
                }
                saveFolder();
            }
        }, throwable -> {
            tellError(getResources().getString(R.string.Error_Import_checking_Folder_Exists) + throwable.getMessage());
        });
    }

    private void checkIfFolderAlreadyExist_fromFolderName(String folderName) {
        // For Android 11 zip file copied in local folder
        myLog("Checking Folder doesn't already exist in DB : " + folderName);
        Observable.fromCallable(() -> {
            boolean bcheckIfFolderExist = false;
            long lcheckIfFolderExist = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .FolderDao()
                    .folderAlreadyExist_checkFolderName(folderName);
            if (lcheckIfFolderExist>0) { bcheckIfFolderExist = true;}
            return bcheckIfFolderExist;
            //TODO if only name the same, just import with a new name... but is this possible ? wanted ?
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(result -> {
            if (result) {
                myLog("KO, folder does already exist in DB");
                tellError(getString(R.string.Error_Import_FolderAlreadyImported));
            } else {
                myLog("OK, folder doesn't already exist in DB");
                tellProgress(PROGRESS_CHECK_FOLDER_EXIST_ZIP,getResources().getString(R.string.Import_Progress_check_not_already_imported));
                copyZipLocal();
            }
        }, throwable -> {
            tellError(getResources().getString(R.string.Error_Import_checking_Folder_Exists) + throwable.getMessage());
        });
    }

    private void copyZipLocal() {
        long zipFileSize = -1L;

        File externalZipFile = new File(uri.getPath());
        if (externalZipFile.exists()) zipFileSize = externalZipFile.length();
        if (zipFileSize > 0) {
            myLog("ze Size : " + zipFileSize);
        } else {
            myLog("ERR : Cannot Check Size .... Size = " + zipFileSize + " .... Never Mind... let's copy");
        }

        destinationFolderPath = getFilesDir().getAbsolutePath() + "/" + FOLDER_UNZIPPED + "/" + destinationFolderName;
        myLog("Future Folder Path : [" + destinationFolderPath + "]");

        myLog("call to launchCopyFileService \nfrom Uri [" + uri + "] \nto Folder [" + destinationFolderPath + "] \nwith Name [" + destinationFolderName + ".zip" + "]");
        launchCopyFileService(uri, destinationFolderPath, destinationFolderName + ".zip");
    }

    private void unzipZipLocal() {
        String destinationFolderPathForMp3 = destinationFolderPath;
        String zipFileFullPath = destinationFolderPath + "/" + destinationFolderName + ".zip";
        launchUnzipService(zipFileFullPath, destinationFolderPathForMp3);
    }

    private void saveFolder() {

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
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((result) -> {
                    if (result) {
                        myLog("Folder Saved in DB - checking files");
                        tellProgress(-1,getResources().getString(R.string.Import_Progress_checkingFiles));
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
                if (comingFromZip) {
                    progress = (int) PROGRESS_SAVE_DB_START_ZIP + (i * 100 / audioFileArrayList.size())*(PROGRESS_SAVE_DB_END_ZIP-PROGRESS_SAVE_DB_START_ZIP)/100;
                } else {
                    progress = (int) PROGRESS_SAVE_DB_START_NO_ZIP + (i * 100 / audioFileArrayList.size())*(PROGRESS_SAVE_DB_END_NO_ZIP-PROGRESS_SAVE_DB_START_NO_ZIP)/100;
                }
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
        zikFile.setDisplayName(FormatNameForDisplay(sZikFileName));
        zikFile.setIdFolder(mFolderId);
        zikFile.setZeorder(zeorder);
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
            myLogE("Error getting media duration : " + e.getMessage());
        }

        if (zikFile.getDuration() == 0) {
            myLog("File Not Added.... (Duration = 0)");
            nbFileSaved++;
            if (nbFileSaved == nbFileToSave) {
                myLog("************All files have been processed. dur0");
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
                                    myLog("File Added - SQL result = " + result);
                                    nbFileSaved++;
                                    if (nbFileSaved == nbFileToSave) {
                                        myLog("************All files have been processed.");
                                        updateFolderDuration();
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
        return duration;
    }

    public void tellProgress(int progressVal,String progressText) {
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
        myLogE("tellError : [" + txt + "]");
        isBusy = false;
        myLog("killing Service");
        stopSelf();
    }


    private void loadOptionValues() {
        SharedPreferences prefs = this.getSharedPreferences(SHARED_PREFERENCES_OPTIONS, MODE_PRIVATE);
        Zip_DoUnzip = prefs.getBoolean("UNZIP_LOCAL", DEFAULT_UNZIP_LOCAL);
        Zip_DoCopylocal = prefs.getBoolean("COPY_ZIP_LOCAL", DEFAULT_COPY_ZIP_LOCAL);
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
        tmp = deleteExtention(tmp);
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

    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }


    // Copy CallBacks
    //---------------------
    @Override
    public void tellProgressClient_fromCopy(String progressText, int progressVal) {
        tellProgress(PROGRESS_COPY_START + progressVal * (PROGRESS_COPY_END - PROGRESS_COPY_START) / 100, progressText);
    }
    @Override
    public void tellEndClient_fromCopy() {
        myLog("Copyfile tell End - go unzip");
        unzipZipLocal();
    }
    @Override
    public void tellErrorClient_fromCopy(String errorText) {
        myLogE("Copyfile tell Error");
        tellError(errorText);
    }
    // Unzip CallBacks
    //---------------------
    @Override
    public void tellProgressClient_fromUnzip(String progressText, int progressVal) {
        tellProgress(PROGRESS_UNZIP_START + progressVal * (PROGRESS_UNZIP_END - PROGRESS_UNZIP_START) / 100, progressText);
    }
    @Override
    public void tellErrorClient_fromUnzip(String errorText) {
        tellError(errorText);
        myLogE("Unzip tell Error");
    }
    @Override
    public void tellEndClient_fromUnzip(String destinationFolderPath) {
        myLog("Unzip tell End - go do something else");
        try {
            pickedDir = DocumentFile.fromFile(new File(destinationFolderPath));
        } catch (Exception e) {
            myLogE("error getting DocumentFile.fromFile : " + e.getMessage());
        }
        populateArrayListOfTracksFromFolder();
    }
    @Override
    public void tellNonBlockingError(String txt) {
        mCallBacks.tellNonBlockingError(txt);
    }

}
