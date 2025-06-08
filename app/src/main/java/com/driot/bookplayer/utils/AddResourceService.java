package com.driot.bookplayer.utils;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.sqlite.db.SimpleSQLiteQuery;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.LifecycleLoggingService;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.FolderAttrib;
import com.driot.bookplayer.db.FolderDao;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Option;

import java.io.File;
import java.io.IOException;
import java.sql.Date;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

import static com.driot.bookplayer.global.Var.FOLDER_DOWNLOAD;
import static com.driot.bookplayer.global.Var.FOLDER_UNZIPPED;
import static com.driot.bookplayer.global.Var.ONLY_MIME_AUDIO;
import static com.driot.bookplayer.global.Var.PATH_CHECK_AUTOTEST;
import static com.driot.bookplayer.utils.Tonio.formatMem;
import static com.driot.bookplayer.utils.Tonio.formatNameForDisplay;
import static com.driot.bookplayer.utils.Tonio.fileExists;
import static com.driot.bookplayer.utils.Tonio.getFileNameFromPath;
import static com.driot.bookplayer.utils.Tonio.getMimeType;
import static com.driot.bookplayer.utils.Tonio.stripExtension;
import static com.driot.bookplayer.utils.TonioCommonStuff.deleteExtension;
import static com.driot.bookplayer.utils.TonioCommonStuff.extractName;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 23/11/20
 */

// TODO check if Service is Busy before starting another import

public class AddResourceService
        extends LifecycleLoggingService
        implements CopyFileService.Callbacks, UnzipService.Callbacks, DownloadService.Callbacks
{

    AddResourceService.Callbacks mCallBacks;

    CopyFileService mCopyFileService;
    Boolean mCopyFileServiceBound;
    boolean boundToCopyFileService;

    UnzipService mUnzipService;
    Boolean mUnzipServiceBound;
    boolean boundToUnzipService;

    DownloadService mDownloadService;
    Boolean mDownloadServiceBound;
    boolean boundToDownloadService;

    /**
     * steps are
     *   0  Init
     *   1  Download
     *   2  Scan for Audio Files
     *   3  Check Folder already exist
     *   4  Check Available space
     *   5  Copy
     *   6  Unzip
     *   7  Get File Duration
     *   8  finish
     */
    public static final int[] PROGRESS_DOWNLOAD = {2, 10, 50, 55, 65, 65, 65, 90, 99};
    public static final int[] PROGRESS_ZIP_COPY = {5, 5, 5, 10, 20, 25, 50, 75, 90};
    public static final int[] PROGRESS_ZIP_NOCOPY = {5, 5, 5, 10, 20, 25, 50, 75, 90};
    public static final int[] PROGRESS_FILE_COPY = {5, 5, 5, 15, 30, 45, 90, 90, 95};
    public static final int[] PROGRESS_FILE_NOCOPY = {5, 5, 20, 40, 60, 90, 90, 90, 95};
    public static final int[] PROGRESS_FOLDER_COPY = {2, 2, 2, 15, 30, 45, 90, 90, 95};
    public static final int[] PROGRESS_FOLDER_NOCOPY = {5, 5, 20, 30, 40, 40, 40, 50, 95};
    public static final String[] PROGRESS_TEXT = {
              "Initialization"
            , "Download"
            , "listing and sorting Tracks"
            , "Check DB"
            , "Check enough space on Disk"
            , "Copy"
            , "Unzip"
            , "Duration"
            , "End"
    };
    public static int[] PROGRESS;


    private final IBinder binder = new AddResourceServiceBackgroundBinder();

    private FolderAttrib myFolder;
    private ArrayList<String> audioFileArrayList;
    private long fullFolderSize;
    private final int[] InsertedFolderId = {0};
    private int nbFileSaved, nbFileToSave, nbFileScan;

    private Uri uri_given;
    private String url_given;
    private String type_given;
    private String title_given;
    private String destinationFolderName;
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
        try {
            if (mDownloadServiceBound != null && mDownloadServiceBound) unbindService(downloadServiceConnection);
            mDownloadServiceBound = false;
        } catch (Exception e) {
            myLogE("onUnbind - error unbindService Download : " + e.getMessage());
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
//COPY FILE
    private final ServiceConnection copyFileServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            myLog("copyFileServiceConnection - onServiceConnected : [" + className.toString() + "]");
            CopyFileService.CopyFileServiceBackgroundBinder binder = (CopyFileService.CopyFileServiceBackgroundBinder) service;
            mCopyFileService = binder.getService();
            mCopyFileService.registerClient(AddResourceService.this);
            mCopyFileServiceBound = true;
            myLog("copyFileServiceConnection - launch init()");
            mCopyFileService.init();
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            myLog("copyFileServiceConnection - OnServiceDisconnected : [" + arg0.toString() + "]");
            if (mCopyFileServiceBound != null && mCopyFileServiceBound) {
                mCopyFileService.unbindService(copyFileServiceConnection);
                mCopyFileServiceBound = false;
            }
        }
    };
    private void launchCopyFileService(Uri uri, String destinationFolderPath, String destinationFileName, String type, boolean checkSize, long forceSize) {
        myLog("launchCopyFileService()");
        Intent intentCopyFileService = new Intent(this, CopyFileService.class);
        intentCopyFileService.putExtra("Uri", uri);
        intentCopyFileService.putExtra("destinationFolderPath", destinationFolderPath);
        intentCopyFileService.putExtra("destinationFileName", destinationFileName);
        intentCopyFileService.putExtra("type", type);
        intentCopyFileService.putExtra("checkSize", checkSize);
        intentCopyFileService.putExtra("forceSize", forceSize);
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
//UNZIP
    private final ServiceConnection unzipServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            myLog("unzipServiceConnection - onServiceConnected : [" + className.toString() + "]");
            UnzipService.UnzipServiceBackgroundBinder binder = (UnzipService.UnzipServiceBackgroundBinder) service;
            mUnzipService = binder.getService();
            mUnzipService.registerClient(AddResourceService.this);
            mUnzipServiceBound = true;
            myLog("unzipServiceConnection - launch init()");
            mUnzipService.init();
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            myLog("unzipServiceConnection - OnServiceDisconnected : [" + arg0.toString() + "]");
            if (mUnzipServiceBound != null && mUnzipServiceBound) {
                mUnzipService.unbindService(unzipServiceConnection);
                mUnzipServiceBound = false;
            }
        }
    };
    private void launchUnzipService(String zipFilePath, String destinationFolderPath) {
        myLog("launchUnzipService()");
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
//DOWNLOAD
    private final ServiceConnection downloadServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            myLog("downloadServiceConnection - onServiceConnected : [" + className.toString() + "]");
            DownloadService.DownloadServiceBackgroundBinder binder = (DownloadService.DownloadServiceBackgroundBinder) service;
            mDownloadService = binder.getService();
            mDownloadService.registerClient(AddResourceService.this);
            mDownloadServiceBound = true;
            myLog("downloadServiceServiceConnection - launch init()");
            mDownloadService.init();
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            myLog("downloadServiceConnection - OnServiceDisconnected : [" + arg0.toString() + "]");
            if (mDownloadServiceBound != null && mDownloadServiceBound) {
                mDownloadService.unbindService(downloadServiceConnection);
                mDownloadServiceBound = false;
            }
        }
    };
    private void launchDownloadService(String fileUrl, String destinationFolder) {
        myLog("launchDownloadService()");
        Intent intentDownloadService = new Intent(this, DownloadService.class);
        intentDownloadService.putExtra("fileUrl", fileUrl);
        intentDownloadService.putExtra("destinationFolder", destinationFolder);
        boundToDownloadService = false;
        try {
            boundToDownloadService = bindService(intentDownloadService, downloadServiceConnection, Context.BIND_AUTO_CREATE); //error Log : Activity XXX has leaked ServiceConnection
        } catch (Exception e) {
            myLogE("ERROR bind to Service in launchDownloadService ");
            myLogE(e.getMessage());
        }
        myLog("call start & bind to downloadService from launchDownloadService - bound result :" + boundToDownloadService + "");
    }

    // native methods
    //-----------------------------

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        url_given = intent.getStringExtra("url");
        uri_given = intent.getParcelableExtra("uri");
        type_given = intent.getStringExtra("type");
        title_given = intent.getStringExtra("title");
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
                tellError(getString(R.string.Error_Import_CannotParseFile));
                return;
            }
            mCallBacks.tellHeader(myFolder.getFolderName());

            if (myFolder.isFolderKO()) {
                String error = getString(R.string.Error_Import_FilePathKO);
                tellError(error);
                return;
            } else {
                myLog("file ok");

                audioFileArrayList = new ArrayList<>();

                addAudioFileUnique(dfPickedDir);

            }
        } else {
            tellError(getString(R.string.Error_Import_IsNotFile));
            return;
        }
        goFolder();
    }

    private void populateArrayListOfTracksFromFolder(DocumentFile dfPickedDir, boolean comingFromZip) {
        myLog("populateArrayListOfTracksFromFolder - DocumentFile [" + dfPickedDir.toString() + "]");
        tellProgress(PROGRESS[2], PROGRESS_TEXT[2]);
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
                tellError(getString(R.string.Error_Import_CannotGetParentDir));
                return;
            }
            myLog("Parent Folder taken in place");
        }

        if (dfPickedDir != null && dfPickedDir.isDirectory()) {

            // constructeur pour mon pti folder
            myFolder = new FolderAttrib(getApplicationContext(), uri, Option.getCopyFile(this), type_given);
            if (myFolder.getFolderName()==null) {
                tellError(getString(R.string.Error_Import_CannotParseFile));
                return;
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
                        myLog("Full directoy size : [" + formatMem(fullFolderSize/1024/1024,0) + " Mo]");
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
        tellProgress(PROGRESS[2], PROGRESS_TEXT[2]);
        nbFileScan = 0;
        fullFolderSize = 0;
        addAudioFileRecursive(f0,"");
    }
    private void addAudioFileRecursive(DocumentFile f0, String recursivFolder) {
        String l_audioFilePath;
        long l_audioSize;
        for (DocumentFile f1 : f0.listFiles()) {
            if (f1.isDirectory()) {
                addAudioFileRecursive(f1,recursivFolder + f1.getName() + '/');
            } else {
                if (f1.getType() != null) {
                    //if (f1.getType().equals("audio/mpeg") || f1.getType().equals("audio/mp4")) {
                    if (f1.getType().startsWith(ONLY_MIME_AUDIO)) {
                        nbFileScan = nbFileScan + 1;
                        l_audioFilePath = recursivFolder + f1.getName();
                        l_audioSize = f1.length();
                        myLog("* New Audio File : [" + l_audioFilePath + "] - size = [" + l_audioSize + "]");
                        double progress = (double) nbFileScan%10/10;
                        tellProgress((int) (PROGRESS[2] + (PROGRESS[3] - PROGRESS[2]) * progress), "Scanning for Audio Files..... \n[" +  l_audioFilePath + ']');
                        audioFileArrayList.add(l_audioFilePath);
                        fullFolderSize = fullFolderSize + l_audioSize;
                    }
                }
            }
        }
    }

    ///////////////////////////////////////
    // INIT
    ///////////////////////////////////////

    public void init() {
        String strUriLog = uri_given==null ? "null" : uri_given.toString();

        myLog("....");
        myLog("....");
        myLog("*********************************************************************************************************");
        myLog("init() - ** uri = " + strUriLog + " **");
        myLog("init() - ** title = " + title_given + " **");
        myLog("init() - ** type = " + type_given + " **");
        myLog("init() - ** url = " + url_given + " **");
        myLog("*********************************************************************************************************");

        if (type_given==null || (url_given==null && uri_given==null)) {myLogE("init() - args=null");tellError("Init failed, args are null");return;}

        isBusy = true;
        PROGRESS = PROGRESS_DOWNLOAD; // dummy progress, before real init
        tellProgress(PROGRESS[0], PROGRESS_TEXT[0]);

        if (!Objects.equals(url_given, null)) {
            //Check not already imported
            new Thread(() ->  {
                String strFolderName =  stripExtension(getFileNameFromPath(url_given));
                myLog("Checking Folder doesn't already exist in DB (URL init check) : " + strFolderName);
                long folderId = AppDatabase.getDatabase(this).FolderDao().folderAlreadyExist_checkFolderName(strFolderName);
                if (folderId>0) {
                    tellError(getString(R.string.Error_Import_AlreadyImported) + " [" + strFolderName + "]");
                    return;
                }
                PROGRESS = PROGRESS_DOWNLOAD;
                tellProgress(PROGRESS[1], PROGRESS_TEXT[1]);
                launchDownloadService(url_given,getFilesDir().getAbsolutePath() + "/" + FOLDER_DOWNLOAD);
            }).start();
            return;
        }

        DocumentFile dfPickedDir;
        String mime = null;

        switch (type_given) {
            ///---------------------------------------------
            /// FILE
            ///---------------------------------------------
            case "File":
                PROGRESS = Option.getCopyFile(this) ? PROGRESS_FILE_COPY : PROGRESS_FILE_NOCOPY;

                try {
                    dfPickedDir = DocumentFile.fromSingleUri(this, uri_given);
                } catch (Exception e) {
                    myLogE("Error reading picked File.... DocumentFile.fromSingleUri : " + e.getMessage());
                    tellError(getString(R.string.Error_Import_CannotReadFile));
                    break;
                }
                myLog("isVirtual = " + dfPickedDir.isVirtual());

                try {
                    mime = getMimeType(this, uri_given);
                } catch (Exception e) {
                    try {
                        mime = dfPickedDir.getType();
                    } catch (Exception e2) {
                        tellError(getString(R.string.Error_Import_CannotDetermineType) + "...  " + e.getMessage());
                        break;
                    }
                }

                if (mime == null) {
                    myLogE("mime == null");
                    tellError(getString(R.string.Error_Import_CannotDetermineType));
                    break;
                }

                myLog("mime = [" + mime + "]");
                if (mime.equals("audio/mp4")) { myLog("mime => MP4"); } // TODO : Chapter Stuff....


                if (mime.startsWith(ONLY_MIME_AUDIO)) {


                    dfqsdtertaerg

                    String uriAuthority = uri_given.getAuthority();
                    //cloudAuthorities.add("com.google.android.apps.docs.storage"); // Google Drive
                    //cloudAuthorities.add("com.microsoft.skydrive.content");       // OneDrive
                    if (uriAuthority != null && uriAuthority.equals("com.google.android.apps.docs.storage")) {
                        // should copy here first
                        new Thread(() -> {
                            long lCheck = AppDatabase.getDatabase(this).FolderDao().folderAlreadyExist_checkFolderName(destinationFolderName);
                            if (lCheck>0) {
                                myLogE("KO, folder does already exist in DB : [" + destinationFolderName + "]");
                                tellError(getString(R.string.Error_Import_FolderAlreadyImported) + "  [" + destinationFolderName + "]");
                            } else {
                                myLog("OK, folder doesn't already exist in DB");
                                tellProgress(PROGRESS[3], PROGRESS_TEXT[3]);
                                copyFileLocal(uri_given
                                        , getFilesDir().getAbsolutePath() + "/" + FOLDER_UNZIPPED + "/" + destinationFolderName
                                        , destinationFolderName + ".zip"
                                        , type_given
                                ); //launch a service, next step through callbacks
                            }
                        }).start();
                        return;                    }







                    populateArrayListOfTracksFromFile(dfPickedDir);
                } else {
                    tellError( getString(R.string.Error_Import_NotAnAudio) + "...  " + getString(R.string.Error_Import_TypeNotSupported) + " [" + mime + "]");
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

                tellProgress(PROGRESS[1], PROGRESS_TEXT[1]);
                try {
                    dfPickedDir = DocumentFile.fromTreeUri(this, uri_given);
                } catch (Exception e) {
                    myLogE("Error reading picked Folder.... DocumentFile.fromTreeUri : " + e.getMessage());
                    tellError(getString(R.string.Error_Import_CannotReadFolder));
                    break;
                }
                tellProgress(PROGRESS[2], PROGRESS_TEXT[2]);
                if (dfPickedDir == null) {
                    myLogE("dfPickedDir == null");
                    tellError(getString(R.string.Error_Import_CannotReadFolder));
                } else {
                    populateArrayListOfTracksFromFolder(dfPickedDir, false);
                }
                break;

            ///---------------------------------------------
            /// ZIP FILE
            ///---------------------------------------------
            case "ZIP":
                PROGRESS = Option.getCopyFile(this) ? PROGRESS_ZIP_COPY : PROGRESS_ZIP_NOCOPY;
                myLog("ZIP : copy locally before everything else");
                myLog("Picked Uri = [" + uri_given.toString() + "]");

                // get the folder name = the zip file true Name without extension
                destinationFolderName = "";
                if (uri_given.getPath().contains(PATH_CHECK_AUTOTEST)) {  // <-- autotest
                    destinationFolderName =  formatNameForDisplay(getFileNameFromPath(uri_given.getPath()));
                } else {
                    destinationFolderName = title_given;
                }

                // check Not Already Imported
                //*****************************
                myLog("Checking Folder doesn't already exist in DB (pre-check Zip) : " + destinationFolderName);
                new Thread(() -> {
                    long lCheck = AppDatabase.getDatabase(this).FolderDao().folderAlreadyExist_checkFolderName(destinationFolderName);
                    if (lCheck>0) {
                        myLogE("KO, folder does already exist in DB : [" + destinationFolderName + "]");
                        tellError(getString(R.string.Error_Import_FolderAlreadyImported) + "  [" + destinationFolderName + "]");
                    } else {
                        myLog("OK, folder doesn't already exist in DB");
                        tellProgress(PROGRESS[3], PROGRESS_TEXT[3]);
                        copyFileLocal(uri_given
                                , getFilesDir().getAbsolutePath() + "/" + FOLDER_UNZIPPED + "/" + destinationFolderName
                                , destinationFolderName + ".zip"
                                , type_given
                        ); //launch a service, next step through callbacks
                    }
                }).start();
                return;
        default:
                myLogE("Incorrect type : **" + type_given + "**");
        }
    }

    private void goFolder() {
        if (audioFileArrayList != null) {
            if (audioFileArrayList.isEmpty()) {
                tellError(getString(R.string.Error_Import_NoMediaInFolder));
            } else {
                myLog(audioFileArrayList.size() + " " + getString(R.string.Import_nMediaInFolder));
                checkIfFolderAlreadyExist_inDB();
            }
        } else {
            tellError(getString(R.string.Error_Import_NoMediaInFolder));
        }
    }

    private void checkIfFolderAlreadyExist_inDB() {
        tellProgress(PROGRESS[3], PROGRESS_TEXT[3]);
        myLog("checkIfFolderAlreadyExist() - FolderName = [" + myFolder.getFolderName() + "]");
        new Thread(() -> {
            try {
                FolderDao folderDao = AppDatabase.getDatabase(this).FolderDao();
                long lFolderAlreadyExist = folderDao.folderAlreadyExist_checkFolderName(myFolder.getFolderName());
                if (lFolderAlreadyExist>0) {
                    tellError(getString(R.string.Error_Import_FolderAlreadyImported));
                } else {
                    myLog("ok on continue -       (folder does not already exist)");
                    copyFolder();
                }
            } catch (Exception e) {
                myLogE("checkIfFolderAlreadyExist() - " + e.getMessage());
            }
        }).start();

    }
    private void copyFolder() {
        myLog("copyFolder()");
        if (type_given.equals("ZIP")) {
            // Has already been copied and unzipped...
            myFolder.setForceFolderPath(zipDestinationFolderPath);
            saveFolder();
        } else {
            if (Option.getCopyFile(this)) {
                tellProgress(PROGRESS[4], PROGRESS_TEXT[4]);
                if (type_given.equals("Folder")) {
                    String folderPath = getFilesDir().getAbsolutePath() + "/" + FOLDER_UNZIPPED + "/" + myFolder.getFolderName();
                    String fileName = myFolder.getFileName(this);
                    fullPath = folderPath;
                    myLog("**** fullPath = [" + fullPath + "]");
                    copyFileLocal(myFolder.getUri()
                            , folderPath
                            , fileName
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


    private void saveFolder() {
        tellProgress(PROGRESS[7], PROGRESS_TEXT[7]);

        final Time sFirstAccess = new Time(System.currentTimeMillis());
        final Date sLastAccess = new Date(System.currentTimeMillis());
        final Time sLastAccessTime = new Time(System.currentTimeMillis());

        Folder folder = new Folder();
        folder.setName(myFolder.getFolderName());
        folder.setPath(myFolder.getFolderPath());
        folder.setUri("****"); //2023-10-22 deprecated
        folder.setHash("0"); //2023-10-22 deprecated
        folder.setPercentdone(0.0);
        folder.setFirstaccess(sFirstAccess);
        folder.setLastaccess(sLastAccess);
        folder.setLastaccessTime(sLastAccessTime);
        folder.setFinished(false);
        folder.setIszipfile(false); //2023-10-22 deprecated (live zip reading - code has been removed)

        InsertedFolderId[0] = (int) DatabaseClient.getInstance(this).getAppDatabase().FolderDao().insert(folder);
        myLog("Folder Saved in DB, ID=[" + InsertedFolderId[0] + "] - checking files");
        tellProgress(PROGRESS[7], PROGRESS_TEXT[7]);
        saveFiles();
    }

    private void saveFiles() {
        nbFileToSave = audioFileArrayList.size();
        nbFileSaved = 0;
        int i = 0;
        int progress;
        String txtProgress;
        for (String s : audioFileArrayList) {
            i++;
            progress = (int) PROGRESS[7] + (i * 100 / audioFileArrayList.size())*(PROGRESS[8]-PROGRESS[7])/100;
            txtProgress = progress + "% - " + getString(R.string.Add_resource_reading_file) + " n°" + i + "/" + audioFileArrayList.size() + "\n" + getFileNameFromPath(s);
            myLog("Registering track [" + s + "]");
            saveFile(s, InsertedFolderId[0], i);
            tellProgress(progress,txtProgress);
        }
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
                myLog("*************************** All files have been processed. -- last file duration=0");
                updateFolderDuration();
            }
        } else {
            long zikFileId = AppDatabase.getDatabase(this).ZikFileDao().insert(zikFile);
            if (zikFileId>0) {
                myLog("ZikFile Added.... SQL result (=id) = [" + zikFileId + "]");
                nbFileSaved++;
                if (nbFileSaved == nbFileToSave) {
                    myLog("******************************************************************************************************************");
                    myLog("******************************************************************************************************************");
                    myLog("***************************      All files have been processed. -- OK      ***************************************");
                    myLog("******************************************************************************************************************");
                    myLog("******************************************************************************************************************");
                    updateFolderDuration();
                    myLog("deleting source ??"
                            + "\nOption CopyFile : " + Option.getCopyFile(this) + "  -  is a ZIP : " + type_given.equals("ZIP")
                            + "\nOption DeleteSourceFile : " + Option.getDeleteSourceFile(this));
                    if ((Option.getCopyFile(this) || type_given.equals("ZIP")) && Option.getDeleteSourceFile(this)) {
                        myLog("deleting source => YES");
                        deleteSourceFile();
                    }
                }
            } else {
                tellError(getString(R.string.Error_Import_CannotSaveInDB) + " [" + sZikFileName + "]");
            }
        }
    }

    private void updateFolderDuration() {
        String strSQL = "UPDATE Folder " +
                " SET duration = (SELECT SUM(duration) " +
                " FROM ZikFile " +
                " WHERE Folder.id = ZikFile.idFolder )";
        SimpleSQLiteQuery query = new SimpleSQLiteQuery(strSQL);
        try {
            int sqlResult = DatabaseClient.getInstance(getApplicationContext()).getAppDatabase().FolderDao().runRawSql(query);
            myLog("Folder Duration Updated : runRawSQL result = " + sqlResult);
            tellEnd();
        } catch (Exception e) {
            tellNonBlockingError(getResources().getString(R.string.Error_Import_computing_folder_duration) + " : " +  e.getMessage());
            tellEnd();
        }
    }

    private void deleteSourceFile() {
        myLog("deleteSourceFile() - uri = [" + uri_given + "] [" + type_given + "]");
        DocumentFile dfPickedDir = null;
        if (type_given.equals("File") || type_given.equals("ZIP")) {
            try {
                dfPickedDir = DocumentFile.fromSingleUri(this, uri_given);
            } catch (Exception e) {
                myLogE("deleting - error getting DocumentFile.fromSingleUri : " + e.getMessage());
                tellError(getString(R.string.Error_Import_CannotDeleteSource));
            }
        } else if (type_given.equals("Folder")) {
            try {
                dfPickedDir = DocumentFile.fromTreeUri(this, uri_given);
            } catch (Exception e) {
                myLogE("deleting - error getting DocumentFile.fromTreeUri : " + e.getMessage());
                tellError(getString(R.string.Error_Import_CannotDeleteSource));
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
        long duration = 0;
        if (fileExists(zePath)) {
            try {
                MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
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


    private String pruneZipFileName(String destinationFolderName) {
        String tmp = destinationFolderName;
        tmp = tmp.replace(" (1)","");
        tmp = tmp.replace(" (2)","");
        tmp = tmp.replace(" (3)","");
        tmp = tmp.replace(":"," ");
        tmp = deleteExtension(tmp);
        if (!tmp.equals(destinationFolderName)) {
            myLog("destinationFolderName has been pruned : [" + tmp + "]");
        }
        return tmp;
    }

    private void copyFileLocal(Uri uri, String destinationFolderPath, String destinationName, String type_given) {
        if ("ZIP".equals(type_given)) { //reset variable because was done in observable stuff
            this.zipDestinationFolderPath = destinationFolderPath;
            this.zipDestinationFolderName = destinationName;
        }
        boolean checkSize = true;
        long forceSize = -1;
        if ("Folder".equals(type_given)) {
            checkSize = true;
            forceSize = fullFolderSize;
        }

        myLog("Future Folder Path : [" + destinationFolderPath + "]");
        myLog("call to launchCopyFileService " +
                "\n.   from Uri [" + uri + "] " +
                "\n.   to Folder [" + destinationFolderPath + "] " +
                "\n.   with Name [" + destinationName + "]" +
                "\n.   for type = [" + type_given + "]" +
                "\n.   checkSize = [" + checkSize + "]" +
                "\n.   checkSize = [" + forceSize + "]"
        );
        launchCopyFileService(uri, destinationFolderPath, destinationName, type_given, checkSize, forceSize);
    }

    private void unzipZipLocal(String zeZipFilePath, String zeDestinationFolderPath) {
        myLog("Launching Unzip service with arguments" +
                "\n.    ZipFilePath = [" + zeZipFilePath + "]" +
                "\n.    DestinationFolderPath = [" + zeDestinationFolderPath + "]"
        );
        launchUnzipService(zeZipFilePath, zeDestinationFolderPath);
    }

    /**
     **********************************
     *    DOWNLOAD CALLBACKS received
     *********************************
     */
    @Override
    public void downloadService_tellProgress(String progressText, int progressVal) {
        tellProgress(PROGRESS[1] + progressVal * (PROGRESS[2] - PROGRESS[1]) / 100, progressText);
    }
    @Override
    public void downloadService_tellProgressNoLog(String progressText, int progressVal) {
        tellProgressNoLog(PROGRESS[1] + progressVal * (PROGRESS[2] - PROGRESS[1]) / 100, progressText);
    }
    @Override
    public void downloadService_tellEnd(String downloadedFileFullPath) {
        myLog("Download tell End -> [" + downloadedFileFullPath + "]");
        type_given = "ZIP";
        uri_given = Uri.fromFile(new File(downloadedFileFullPath));
        String fileName = deleteExtension(extractName(downloadedFileFullPath));
        this.zipDestinationFolderPath = getFilesDir().getAbsolutePath() + "/" + FOLDER_UNZIPPED + "/" + fileName;
        unzipZipLocal(downloadedFileFullPath, getFilesDir().getAbsolutePath() + "/" + FOLDER_UNZIPPED + "/" + fileName);
    }
    @Override
    public void downloadService_tellError(String errorText) {
        myLogE("Download tell Error");
        tellError(errorText);
    }


    /**
     **********************************
     *    COPY CALLBACKS received
     *********************************
     */
    @Override
    public void copyFileService_tellProgressNoLog(String progressText, int progressVal) {
        tellProgressNoLog(PROGRESS[5] + progressVal * (PROGRESS[6] - PROGRESS[5]) / 100, progressText);
    }
    @Override
    public void copyFileService_tellProgress(String progressText, int progressVal) {
        tellProgress(PROGRESS[5] + progressVal * (PROGRESS[6] - PROGRESS[5]) / 100, progressText);
    }
    @Override
    public void copyFileService_tellEnd() {
        myLog("Copyfile tell End " + type_given);
        if (type_given.equals("ZIP")) {
            myLog("launch unzipZipLocal()");
            unzipZipLocal(zipDestinationFolderPath + "/" + zipDestinationFolderName, zipDestinationFolderPath);
        } else {
            myFolder = new FolderAttrib(this, Uri.fromFile(new File(fullPath)), Option.getCopyFile(this), type_given);
            saveFolder();
        }
    }
    @Override
    public void copyFileService_tellError(String errorText) {
        myLogE("Copyfile tell Error");
        tellError(errorText);
    }
    /**
     **********************************
     *    UNZIP CALLBACKS received
     *********************************
     */
    @Override
    public void unzipService_tellProgress(String progressText, int progressVal) {
        tellProgress(PROGRESS[6] + progressVal * (PROGRESS[7] - PROGRESS[6]) / 100, progressText);
    }
    @Override
    public void unzipService_tellError(String errorText) {
        myLogE("Unzip service tell Error");
        tellError(errorText);
    }
    @Override
    public void unzipService_tellEnd(String destinationFolderPath) {
        myLog("Unzip Service tells End : [" + destinationFolderPath + "]");
        tellProgress(PROGRESS[7], PROGRESS_TEXT[7]);
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
    public void tellProgressNoLog(int progressVal,String progressText) {
        mCallBacks.updateProgress(progressText, progressVal);
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
    @Override
    public void tellNonBlockingError(String txt) {
        mCallBacks.tellNonBlockingError(txt);
    }

    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }

}
