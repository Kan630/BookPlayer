package com.driot.bookplayer.services;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.objects.AudioFileInfo;
import com.driot.bookplayer.utils.FileUtils;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggingService;
import com.driot.bookplayer.utils.StorageHelper;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.sql.Date;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Objects;

import static com.driot.bookplayer.db.Sql.updateFolderTable;
import static com.driot.bookplayer.global.Pref.getLoadBookTaskState;
import static com.driot.bookplayer.global.Pref.setLoadBookTaskState;
import static com.driot.bookplayer.global.Var.ONLY_MIME_AUDIO;
import static com.driot.bookplayer.global.Var.PATH_CHECK_AUTOTEST;
import static com.driot.bookplayer.global.Var.SUPPORTED_AUDIO_EXTENSIONS;
import static com.driot.bookplayer.global.Var.SUPPORTED_COVER_PICTURE_EXTENSIONS;
import static com.driot.bookplayer.utils.Tonio.formatMemPadding;
import static com.driot.bookplayer.utils.Tonio.formatNameForDisplay;
import static com.driot.bookplayer.utils.Tonio.fileExists;
import static com.driot.bookplayer.utils.Tonio.formatTime;
import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Tonio.getFileNameFromPath;
import static com.driot.bookplayer.utils.TonioCommonStuff.deleteExtension;
import static com.driot.bookplayer.utils.WorkFlow.clearDownloadFinished;
import static com.driot.bookplayer.utils.WorkFlow.setWorkFlowFinished;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 23/11/20
 */

// TODO check if Service is Busy before starting another import

public class AddResourceService
        extends LoggingService
        implements CopyFileService.Callbacks, UnzipService.Callbacks, DownloadService.Callbacks, SplitM4bService.Callbacks
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

    SplitM4bService mSplitM4bService;
    Boolean mSplitM4bServiceBound;
    boolean boundToSplitM4bService;

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
            , "Checking audio hasn't already been added"
            , "Check enough space on Disk"
            , "Copy"
            , "Unzip"
            , "Duration"
            , "End"
    };
    public static int[] PROGRESS = PROGRESS_DOWNLOAD;

    private enum SaveResult {SUCCESS, SKIPPED, FAILED}

    private final IBinder binder = new AddResourceServiceBackgroundBinder();

    private ArrayList<AudioFileInfo> audioFileArrayList;
    
    private long fullFolderSize; //to make storage space checks

    private int nbFileScan; // global because recursive method

    LoadBookTaskState bookState;
    
    private Uri uri_dynamic;
    private String type_dynamic;

    private String future_DB_folder_path = "-o-";

    private String destinationFolderName;

    public static boolean isBusy;

    private String lastMessage = "";
    private int lastProgress = -1;

    // Callbacks
    //-----------------------------
    public interface Callbacks{
        void tellProgress(String progressText, int progressVal);
        void tellWarning(String txt);
        void tellNonBlockingError(String txt);
        void tellError(String errorText);
        void tellEnd();
    }
    public void registerClient(Callbacks callbacks) {
        this.mCallBacks = callbacks;
    }

    //used for JobService who seems to not like Callbacks...
    @Override
    public void onCreate() {
        super.onCreate();
        LocalBroadcastManager.getInstance(this).registerReceiver(downloadReceiver, new IntentFilter("BOOKPLAYER_DOWNLOAD_FINISHED"));
        LocalBroadcastManager.getInstance(this).registerReceiver(downloadReceiver, new IntentFilter("BOOKPLAYER_DOWNLOAD_ERROR"));
    }
    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(downloadReceiver);
        isBusy = false;
        super.onDestroy();
    }

    // binder
    //-----------------------------
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        myLogD("onBind()    intent:" + intent.getDataString());
        return binder;
    }
    @Override
    public boolean onUnbind(Intent intent) {
        myLogD("onUnBind()    intent:" + intent.getDataString());
        try {
            if (mUnzipServiceBound != null && mUnzipServiceBound) unbindService(unzipServiceConnection);
            mUnzipServiceBound = false;
        } catch (Exception e) {
            myLogEE(e,"onUnbind - error unbindService Unzip");
        }
        try {
            if (mCopyFileServiceBound != null && mCopyFileServiceBound) unbindService(copyFileServiceConnection);
            mCopyFileServiceBound = false;
        } catch (Exception e) {
            myLogEE(e,"onUnbind - error unbindService CopyFile");
            e.printStackTrace();
        }
        try {
            if (mDownloadServiceBound != null && mDownloadServiceBound) unbindService(downloadServiceConnection);
            mDownloadServiceBound = false;
        } catch (Exception e) {
            myLogEE(e,"onUnbind - error unbindService Download");
            e.printStackTrace();
        }
        try {
            if (mSplitM4bServiceBound != null && mSplitM4bServiceBound) unbindService(splitM4bServiceConnection);
            mSplitM4bServiceBound = false;
        } catch (Exception e) {
            myLogEE(e,"onUnbind - error unbindService Split M4B");
            e.printStackTrace();
        }
        return super.onUnbind(intent);
    }
    public class AddResourceServiceBackgroundBinder extends Binder {
        public AddResourceService getService() {
            myLogD("AddResourceServiceBackgroundBinder returning service instance; ");
            return AddResourceService.this;
        }
    }
    // services
    //-----------------------------
//COPY FILE
    private final ServiceConnection copyFileServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            myLogD("copyFileServiceConnection - onServiceConnected : [" + className.toString() + "]");
            CopyFileService.CopyFileServiceBackgroundBinder binder = (CopyFileService.CopyFileServiceBackgroundBinder) service;
            mCopyFileService = binder.getService();
            mCopyFileService.registerClient(AddResourceService.this);
            mCopyFileServiceBound = true;
            myLog("copyFileServiceConnection - launch init()");
            mCopyFileService.init();
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            myLogD("copyFileServiceConnection - OnServiceDisconnected : [" + arg0.toString() + "]");
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
        boundToCopyFileService = false;
        try {
            boundToCopyFileService = bindService(intentCopyFileService, copyFileServiceConnection, Context.BIND_AUTO_CREATE); //error Log : Activity XXX has leaked ServiceConnection
        } catch (Exception e) {
            myLogEE(e,"ERROR bind to Service in launchCopyFileService");
        }
        myLogD("call start & bind to copyFileService from launchCopyFileService - bound result :" + boundToCopyFileService);
    }
// UNZIP
    private final ServiceConnection unzipServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            myLogD("unzipServiceConnection - onServiceConnected : [" + className.toString() + "]");
            UnzipService.UnzipServiceBackgroundBinder binder = (UnzipService.UnzipServiceBackgroundBinder) service;
            mUnzipService = binder.getService();
            mUnzipService.registerClient(AddResourceService.this);
            mUnzipServiceBound = true;
            myLogD("unzipServiceConnection - launch init()");
            mUnzipService.init();
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            myLogD("unzipServiceConnection - OnServiceDisconnected : [" + arg0.toString() + "]");
            if (mUnzipServiceBound != null && mUnzipServiceBound) {
                mUnzipService.unbindService(unzipServiceConnection);
                mUnzipServiceBound = false;
            }
        }
    };
    private void launchUnzipService(String zipFilePath, String destinationFolderPath) {
        myLogD("launchUnzipService()");
        Intent intentUnzipService = new Intent(this, UnzipService.class);
        intentUnzipService.putExtra("zipFilePath", zipFilePath);
        intentUnzipService.putExtra("destinationFolderPath", destinationFolderPath);
        boundToUnzipService = false;
        try {
            boundToUnzipService = bindService(intentUnzipService, unzipServiceConnection, Context.BIND_AUTO_CREATE); //error Log : Activity XXX has leaked ServiceConnection
        } catch (Exception e) {
            myLogEE(e,"ERROR bind to Service in launchUnzipService");
        }
        myLog("call start & bind to unzipService from launchUnzipService - bound result :" + boundToUnzipService);
    }
// M4B
    private final ServiceConnection splitM4bServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            myLogD("SplitM4bServiceConnection - onServiceConnected : [" + className.toString() + "]");
            SplitM4bService.SplitM4bServiceBackgroundBinder binder = (SplitM4bService.SplitM4bServiceBackgroundBinder) service;
            mSplitM4bService = binder.getService();
            mSplitM4bService.registerClient(AddResourceService.this);
            mSplitM4bServiceBound = true;
            myLogD("SplitM4bServiceConnection - launch init()");
            mSplitM4bService.init();
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            myLogD("SplitM4bServiceConnection - OnServiceDisconnected : [" + arg0.toString() + "]");
            if (mSplitM4bServiceBound != null && mSplitM4bServiceBound) {
                mSplitM4bService.unbindService(splitM4bServiceConnection);
                mSplitM4bServiceBound = false;
            }
        }
    };
    private void launchSplitM4bService(String m4bFilePath, String destinationFolderPath) {
        myLogD("launchSplitM4bService()");
        Intent intentSplitM4bService = new Intent(this, SplitM4bService.class);
        intentSplitM4bService.putExtra("m4bFilePath", m4bFilePath);
        intentSplitM4bService.putExtra("destinationFolderPath", destinationFolderPath);
        boundToSplitM4bService = false;
        try {
            boundToSplitM4bService = bindService(intentSplitM4bService, splitM4bServiceConnection, Context.BIND_AUTO_CREATE); //error Log : Activity XXX has leaked ServiceConnection
        } catch (Exception e) {
            myLogEE(e,"ERROR bind to Service in launchsplitM4bService");
        }
        myLogD("call start & bind to splitM4bService from launchsplitM4bService - bound result :" + boundToSplitM4bService);
    }
//DOWNLOAD
    private final ServiceConnection downloadServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            myLogD("downloadServiceConnection - onServiceConnected : [" + className.toString() + "]");
            DownloadService.DownloadServiceBackgroundBinder binder = (DownloadService.DownloadServiceBackgroundBinder) service;
            mDownloadService = binder.getService();
            mDownloadService.registerClient(AddResourceService.this);
            mDownloadServiceBound = true;
            myLogD("downloadServiceServiceConnection - launch init()");
            mDownloadService.init();
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            myLogD("downloadServiceConnection - OnServiceDisconnected : [" + arg0.toString() + "]");
            if (mDownloadServiceBound != null && mDownloadServiceBound) {
                mDownloadService.unbindService(downloadServiceConnection);
                mDownloadServiceBound = false;
            }
        }
    };
    private void launchDownloadService() {
        myLogD("launchDownloadService()");
        Intent intentDownloadService = new Intent(this, DownloadService.class);
        boundToDownloadService = false;
        try {
            boundToDownloadService = bindService(intentDownloadService, downloadServiceConnection, Context.BIND_AUTO_CREATE); //error Log : Activity XXX has leaked ServiceConnection
        } catch (Exception e) {
            myLogEE(e,"ERROR bind to Service in launchDownloadService");
        }
        myLogD("call start & bind to downloadService from launchDownloadService - bound result :" + boundToDownloadService );
    }

    // native methods
    //-----------------------------

    private void initVars(LoadBookTaskState state) {
        myLogD("initVars");
        if (state != null) {
            bookState = state;
            set_uri_dynamic(state.originalUri);
            set_type_dynamic(state.dynamicType);
        } else {
            myLogEE(null, "initVars state is null");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        myLogD("onStartCommand()");

        initAddResourceService();
        //LoadBookTaskState state = intent.getParcelableExtra("LoadBookTaskState");
        //initVars(state);

        return START_NOT_STICKY;
    }

    ///////////////////////////////////////
    // INIT
    ///////////////////////////////////////

    public void initAddResourceService() {

        if (isBusy) {
            myLog("service already running, skipping init()");
            return;
        }
        isBusy = true;

        LoadBookTaskState state = getLoadBookTaskState();
        initVars(state);

        if (state != null) {
            if (state.downloadedFileReady && state.downloadedFilePath != null) {
                myLog("back with finished Downloaded File");
                downloadService_tellEnd(state.downloadedFilePath);
                return;
            }
        } else {
            tellError("LoadBookTaskState = null");
            return;
        }

        if (type_dynamic ==null || uri_dynamic ==null) {myLogEE(null,"init() - args=null");tellError("Init failed, args are null");return;}

        myLogD("....");
        myLogD("....");
        myLog("*********************************************************************************************************");
        myLog("*********************************************************************************************************");
        myLog("** title =            " + bookState.title + " **");
        myLog("** futureFolderName = " + bookState.futureFolderName + " **");
        myLog("** futureFolderPath = " + bookState.futureFolderPath + " **");
        myLog("** original uri =  " + bookState.originalUri + " **");
        myLog("** original type = " + bookState.dynamicType + " **");
        myLog("** uri =   " + uri_dynamic + " **");
        myLog("** type =  " + type_dynamic + " **");
        myLog("*********************************************************************************************************");
        myLog(bookState.toString().replace(",", "\n"));
        myLog("*********************************************************************************************************");
        myLog("*********************************************************************************************************");


        PROGRESS = PROGRESS_DOWNLOAD; // dummy progress, before real init
        tellProgress(PROGRESS[0], PROGRESS_TEXT[0]);

        ///---------------------------------------------
        /// DOWNLOAD
        ///---------------------------------------------
        if (uri_dynamic.toString().startsWith("http")) {
            bookState.downloadFileUrl = uri_dynamic.toString();
            bookState.downloadDestinationFolder = StorageHelper.getDownloadFolderPath(this);
            bookState.onGoingLoading = true;
            setLoadBookTaskState(bookState);
            new Thread(this::launchDownloadService).start();
            return;
        }

        switch (type_dynamic) {
            ///---------------------------------------------
            /// FILE
            ///---------------------------------------------
            case "File":
            case "M4B":
                PROGRESS = bookState.optionCopy ? PROGRESS_FILE_COPY : PROGRESS_FILE_NOCOPY;


                ///---------------------------------------------
                /// M4B FILE
                ///---------------------------------------------
                if (bookState.fileExtension.equals("m4b")) {
                    if (bookState.optionSplit) {
                        set_type_dynamic("M4B");
                        PROGRESS = bookState.optionCopy ? PROGRESS_ZIP_COPY : PROGRESS_ZIP_NOCOPY;
                        myLog("M4B : copy locally before everything else");
                        myLog("Picked Uri = [" + uri_dynamic.toString() + "]");
                        new Thread(() -> {
                            tellProgress(PROGRESS[3], PROGRESS_TEXT[3]);
                            copyFileLocal(bookState.futureFolderPath
                                    , bookState.futureFolderName + ".m4b"
                            ); //launch a service, NEXT STEP through CALLBACKS
                        }).start();
                        return;

                    } else {
                        myLog("Option Split M4B disabled");
                        DocumentFile dfPickedFile = DocumentFile.fromSingleUri(this, uri_dynamic);
                        populateArrayListOfTracksFromFile(dfPickedFile);
                    }

                } else if (bookState.mimeType.equals("application/zip") || bookState.fileExtension.equals("zip")) {
                    myLog("=> ZIP <=");
                    set_type_dynamic("ZIP");
                    goZipCase();
                    return;

                } else if (bookState.mimeType.startsWith(ONLY_MIME_AUDIO) || SUPPORTED_AUDIO_EXTENSIONS.contains(bookState.fileExtension)) {

                    if (bookState.sourceLocation.equals("cloud")) {
                        new Thread(() -> {
                            tellProgress(PROGRESS[3], PROGRESS_TEXT[1] + " ...reading on cloud");
                            copyFileLocal(bookState.futureFolderPath
                                    , bookState.futureFolderName
                            );
                            //launch a service, NEXT STEP through CALLBACKS
                        }).start();
                        return;

                    } else {
                        DocumentFile dfPickedFile = DocumentFile.fromSingleUri(this, uri_dynamic);
                        populateArrayListOfTracksFromFile(dfPickedFile);
                    }
                } else {
                    tellError( getString(R.string.Error_Import_NotAnAudio) + "...  " + getString(R.string.Error_Import_TypeNotSupported) + " [" + bookState.mimeType + "] - [" + bookState.fileExtension + "]");
                    break;
                }
                break;



            ///---------------------------------------------
            /// FOLDER
            ///---------------------------------------------
            case "Folder":
                PROGRESS = bookState.optionCopy ? PROGRESS_FOLDER_COPY : PROGRESS_FOLDER_NOCOPY;

                tellProgress(PROGRESS[1], PROGRESS_TEXT[1]);
                DocumentFile dfPickedDir;
                try {
                    //dfPickedDir = DocumentFile.fromSingleUri(this, uri_given);
                    dfPickedDir = DocumentFile.fromTreeUri(this, uri_dynamic);
                } catch (Exception e) {
                    myLogEE(e,"Error reading picked Folder.... DocumentFile.fromTreeUri");
                    tellError(getString(R.string.Error_Import_CannotReadFolder));
                    break;
                }
                populateArrayListOfTracksFromFolder(dfPickedDir);
                break;

            ///---------------------------------------------
            /// ZIP FILE
            ///---------------------------------------------
            case "ZIP":
                goZipCase();
                break;
        default:
                myLogEE(null,"Incorrect type : **" + type_dynamic + "**");
        }
    }
    /// ///////// END INIT



    // single file
    ///////////////////////////
    private void populateArrayListOfTracksFromFile(DocumentFile dfPickedDir) {
        myLog("populateArrayListOfTracksFromFile [" + dfPickedDir.getUri() + "] - single file");

        //mCallBacks.tellHeader(myFolder.getFolderName());

        if (dfPickedDir != null && !(dfPickedDir.isDirectory())) {
            audioFileArrayList = new ArrayList<>();
            addAudioFileUnique(dfPickedDir);
            goFolder();
        } else {
            tellError(getString(R.string.Error_Import_IsNotFile));
        }

    }

    private void populateArrayListOfTracksFromFolder(DocumentFile dfPickedDir) {
        if (dfPickedDir == null) {
            myLogEE(null,"populateArrayListOfTracksFromFolder - dfPickedDir == null");
            tellError(getString(R.string.Error_Import_CannotReadFolder));
            return;
        }
        if (!dfPickedDir.isDirectory()) {
            myLogEE(null,"populateArrayListOfTracksFromFolder - dfPickedDir is not directory");
            tellError(getString(R.string.Error_Import_IsNotFolder));
            return;
        }

        myLog("populateArrayListOfTracksFromFolder - DocumentFile [" + dfPickedDir + "]");
        tellProgress(PROGRESS[2], PROGRESS_TEXT[2]);


        //mCallBacks.tellHeader(myFolder.getFolderName());

        myLog("running recursive scan for audio file in a background thread");

        audioFileArrayList = new ArrayList<>();

        DocumentFile finalDfPickedDir = dfPickedDir; //thread needs 'final' arg
        Thread backgroundThread = new Thread(() -> {
            addAudioFileRecursive(finalDfPickedDir);

            myLogD("addAudioFileRecursive done, sorting now...");
            audioFileArrayList.sort(new Utils.AlphanumericComparator());

            if (audioFileArrayList.isEmpty()) {
                myLog("No File found in directory : [" + finalDfPickedDir.getName() + ']');
            } else {
                myLog(audioFileArrayList.size() + " files found in directory : [" + finalDfPickedDir.getName() + ']');
                myLog("Full directory size : [" + formatMemPadding(fullFolderSize/1024/1024,0) + " Mo]");
                myLogD("-----------------------------");
            }
            goFolder();
        });
        try {
            backgroundThread.start();
        } catch (Throwable t) {
            String strErr = "Error while listing audio files";
            if (t instanceof OutOfMemoryError && t.getMessage() != null && t.getMessage().contains("pthread_create")) {
                myLogEE(t,"addAudioFileRecursive : Too many threads or not enough native memory");
                strErr = getString(R.string.Error_Import_OutOfMemory)
                        + "\n" + getString(R.string.Error_Import_This_folder_may_contain_too_many_books);
            } else {
                myLogEE(t,"addAudioFileRecursive");
                strErr = strErr + "\n" + t.getMessage();
            }
            tellError(strErr);
        }
    }

    private void addAudioFileUnique(DocumentFile df) {
        myLogD("* New Audio File : [" +  df.getName() + ']');
        long duration = getMediaDurationFromUri(this, df.getUri());
        myLogD("* Duration : [" +  formatTime(duration) + ']');
        audioFileArrayList.add(new AudioFileInfo(df.getName(), duration));
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
        boolean hadImageBefore = bookState.imagePath != null; //dont look in subDir if image found at top dir
        for (DocumentFile f1 : f0.listFiles()) {
            if (f1.isDirectory()) {
                myLog("increase recursive depth for Directory : [" + f1.getName() + "]");
                addAudioFileRecursive(f1,recursivFolder + f1.getName() + '/');
            } else {
                String fileName = Objects.toString(f1.getName());
                String fileExtension = getExtension(fileName);
                String mimeType = Objects.toString(f1.getType());
                myLogD("* Checking File : [" + fileExtension + "] . [" + fileName + "] - mime = [" + mimeType + "] - subfolder : [" + recursivFolder + "]");
                if (mimeType.startsWith(ONLY_MIME_AUDIO) || SUPPORTED_AUDIO_EXTENSIONS.contains(fileExtension)) {
                    nbFileScan = nbFileScan + 1;
                    l_audioFilePath = recursivFolder + f1.getName();
                    l_audioSize = f1.length();
                    myLogD("* New Audio File : [" + l_audioFilePath + "] - size = [" + l_audioSize + "]");
                    long duration = getMediaDurationFromUri(this, f1.getUri());
                    myLogD("* Duration : [" +  formatTime(duration) + ']');
                    double progress = (double) nbFileScan%10/10;
                    tellProgress((int) (PROGRESS[2] + (PROGRESS[3] - PROGRESS[2]) * progress), "Scanning for Audio Files..... \n[" +  l_audioFilePath + ']');
                    audioFileArrayList.add(new AudioFileInfo(l_audioFilePath, duration));
                    fullFolderSize = fullFolderSize + l_audioSize;
                } else if (!hadImageBefore && SUPPORTED_COVER_PICTURE_EXTENSIONS.contains(fileExtension)) {
                    long imageSize = f1.length();
                    if (bookState.imagePath == null || imageSize > FileUtils.getFileSize(this, Uri.parse(bookState.imagePath))) {
                        myLogD("New biggest Picture Found, size = [" + Tonio.formatMemPadding(imageSize) + "] - [" + f1.getUri() + "]");
                        bookState.imagePath = f1.getUri().toString();
                        hadImageBefore = true;
                    }
                }
            }
        }
    }

    private void goZipCase() {
        PROGRESS = bookState.optionCopy ? PROGRESS_ZIP_COPY : PROGRESS_ZIP_NOCOPY;
        myLog("ZIP : copy locally before everything else");
        myLog("Picked Uri = [" + uri_dynamic.toString() + "]");

        // get the folder name = the zip file true Name without extension
        destinationFolderName = "";
        if (uri_dynamic.getPath().contains(PATH_CHECK_AUTOTEST)) {  // <-- autotest
            destinationFolderName =  formatNameForDisplay(getFileNameFromPath(uri_dynamic.getPath()));
        } else {
            destinationFolderName = bookState.futureFolderName;;
        }

        // check Not Already Imported
        //*****************************
        myLog("Checking Folder doesn't already exist in DB (pre-check Zip) : " + destinationFolderName);
        new Thread(() -> {
            long lCheck = AppDatabase.getDatabase(this).FolderDao().folderAlreadyExist_checkFolderName(destinationFolderName);
            if (lCheck>0) {
                myLogW("KO, folder does already exist in DB : [" + destinationFolderName + "]");
                tellError(getString(R.string.Error_Import_FolderAlreadyImported) + "  [" + destinationFolderName + "]");
            } else {
                myLog("OK, folder doesn't already exist in DB");
                tellProgress(PROGRESS[3], PROGRESS_TEXT[3]);
                copyFileLocal(bookState.futureFolderPath
                        , destinationFolderName + ".zip"
                ); //launch the service, NEXT STEP through CALLBACKS
            }
        }).start();
    }

    private void goFolder() {
        if (audioFileArrayList != null) {
            if (audioFileArrayList.isEmpty()) {
                tellError(getString(R.string.Error_Import_NoMediaInFolder));
            } else {
                myLog(audioFileArrayList.size() + " " + getString(R.string.Import_nMediaInFolder));
                //checkIfFolderAlreadyExist_inDB();
                copyFolder();
            }
        } else {
            tellError(getString(R.string.Error_Import_NoMediaInFolder));
        }
    }
/*
    private void checkIfFolderAlreadyExist_inDB() {
        tellProgress(PROGRESS[3], PROGRESS_TEXT[3]);
        myLog("checkIfFolderAlreadyExist() - FolderName = [" + future_DB_folder_uri + "]");
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
                tellError(getString(R.string.Technical_Error) + "...  " + "checkIfFolderAlreadyExist() - " + e.getMessage());
            }
        }).start();

    }
 */

    private void copyFolder() {
        myLog("copyFolder()");
        if (type_dynamic.equals("ZIP") || (type_dynamic.equals("M4B") && bookState.optionSplit)) {
            myLog("=> Has already been copied during unzipped or split...");
            future_DB_folder_path = bookState.futureFolderPath;
            saveFolder();
        } else {
            if (bookState.optionCopy) {
                future_DB_folder_path = bookState.futureFolderPath;
                tellProgress(PROGRESS[4], PROGRESS_TEXT[4]);
                if (type_dynamic.equals("Folder")) {
                    myLog("=> copyFile Folder...");
                    copyFileLocal(bookState.futureFolderPath);
                } else if (type_dynamic.equals("File") || type_dynamic.equals("M4B")) {
                    myLog("=> copyFile Single File...");
                    future_DB_folder_path = bookState.futureFolderPath;
                    copyFileLocal(future_DB_folder_path
                            , bookState.originalFile
                    );
                } else {
                    tellError(getString(R.string.Technical_Error) + "...  " + "Wrong file type : " + type_dynamic);
                }
            } else {
                future_DB_folder_path = uri_dynamic.toString();
                //future_DB_folder_uri = uri_given.getPath();
                saveFolder();
            }
        }
    }


    private void saveFolder() {
        tellProgress(PROGRESS[7], PROGRESS_TEXT[7]);

        final Time timeNow = new Time(System.currentTimeMillis());
        final Date dateNow = new Date(System.currentTimeMillis());

        Folder folder = new Folder();
        folder.setName(bookState.title);
        folder.setPath(future_DB_folder_path);
        folder.setUri(future_DB_folder_path); //2023-10-22 deprecated
        folder.setHash("0"); //2023-10-22 deprecated
        folder.setPercentdone(0.0);
        folder.setFinished(false);
        folder.setIszipfile(false); //2023-10-22 deprecated (live zip reading - code has been removed)
        folder.setOriginalHash(bookState.originalHash);
        folder.setSourceLocation(bookState.sourceLocation);
        folder.date_added = System.currentTimeMillis();
        folder.image = bookState.imagePath;
        folder.lLastAccess = System.currentTimeMillis(); //used to sort the Book on the main page

        new Thread(() -> {
            int insertedFolderId = (int) DatabaseClient.getInstance(this).getAppDatabase().FolderDao().insert(folder);
            myLog("Folder Saved in DB, ID=[" + insertedFolderId + "] - [" + bookState.title + "]");
            tellProgress(PROGRESS[7], PROGRESS_TEXT[7]);
            saveFiles(insertedFolderId);
        }).start();
    }

    private void saveFiles(int insertedFolderId) {
        if (audioFileArrayList == null) {
            tellError("audioFileArrayList is null");
            return;
        }

        int total = audioFileArrayList.size();
        int saved = 0;
        int skipped = 0;
        int failed = 0;

        for (int i = 0; i < total; i++) {
            AudioFileInfo info = audioFileArrayList.get(i);
            int zeOrder = saved + 1;

            int progress = PROGRESS[7] + ((i + 1) * 100 / total) * (PROGRESS[8] - PROGRESS[7]) / 100;
            String txtProgress = progress + "% - " + getString(R.string.Add_resource_reading_file) +
                    " n°" + i + 1 + "/" + total + "\n" + getFileNameFromPath(info.getFileName());

            myLog("Registering track [" + info.getFileName() + "]");
            SaveResult result = saveSingleFile(info, insertedFolderId, zeOrder);
            tellProgress(progress, txtProgress);

            switch (result) {
                case SUCCESS: saved++; break;
                case SKIPPED: skipped++; break;
                case FAILED: failed++; break;
            }
        }

        myLogD("🎧 Import Summary:");
        myLogD("✔️ Saved:   " + saved);
        myLogD("⏭️ Skipped: " + skipped);
        myLogD("❌ Failed:  " + failed);

        // All files done
        myLogD("******************************************************************************************************************");
        myLogD("******************************************************************************************************************");
        myLogD("***************************      All files have been processed. -- OK      ***************************************");
        myLogD("******************************************************************************************************************");
        myLogD("******************************************************************************************************************");
        updateFolderTable(this, insertedFolderId);

        myLogD("deleting source ??"
                + "\nOption CopyFile : " + bookState.optionCopy + "  -  is a ZIP : " + type_dynamic.equals("ZIP")
                + "\nOption DeleteSourceFile : " + bookState.optionDelete);
        if ((bookState.optionCopy || "ZIP".equals(type_dynamic)) && bookState.optionDelete) {
            deleteSourceFile();
        }
        tellEnd();
    }
    private SaveResult saveSingleFile(AudioFileInfo info, int folderId, int zeOrder) {
        ZikFile file = new ZikFile();
        file.setName(info.getFileName());
        file.setDisplayName(formatNameForDisplay(info.getFileName()));
        file.setIdFolder(folderId);
        file.setZeorder(zeOrder);
        file.setFolderName(bookState.title);
        file.setPercentdone(0.0);
        file.setPosition(0);
        file.setPath(future_DB_folder_path);
        file.setIszipfile(false);
        file.setFinished(false);
        file.setDuration(info.getDuration());
        file.date_added = System.currentTimeMillis();

        if (file.getDuration() == 0) {
            myLog("⏭️ Skipped: duration = 0 → " + info.getFileName());
            return SaveResult.SKIPPED;
        }

        long id = AppDatabase.getDatabase(this).ZikFileDao().insert(file);
        if (id > 0) {
            myLog("✔️ ZikFile inserted: id = " + id);
            return SaveResult.SUCCESS;
        } else {
            myLogE("❌ DB insert failed for: " + info.getFileName());
            tellError(getString(R.string.Error_Import_CannotSaveInDB) + " [" + info.getFileName() + "]");
            return SaveResult.FAILED;
        }
    }

    private void deleteSourceFile() {
        myLog("deleteSourceFile() - uri = [" + uri_dynamic + "] [" + type_dynamic + "]");
        DocumentFile dfPickedDir = null;
        if (type_dynamic.equals("File") || type_dynamic.equals("ZIP")) {
            try {
                dfPickedDir = DocumentFile.fromSingleUri(this, uri_dynamic);
            } catch (Exception e) {
                myLogEE(e,"deleting - error getting DocumentFile.fromSingleUri");
                tellError(getString(R.string.Error_Import_CannotDeleteSource));
            }
        } else if (type_dynamic.equals("Folder")) {
            try {
                dfPickedDir = DocumentFile.fromTreeUri(this, uri_dynamic);
            } catch (Exception e) {
                myLogEE(e,"deleting - error getting DocumentFile.fromTreeUri");
                tellError(getString(R.string.Error_Import_CannotDeleteSource));
            }
        } else {
            myLogEE(null,"Incorrect type : **" + type_dynamic + "**");
        }
        if (!(dfPickedDir == null)) {
            boolean okDelete = dfPickedDir.delete();
            if (okDelete) {
                myLogD("source file deletion ok");
            } else {
                myLogEE(null,"Error during source file deletion");
            }
        } else {
            myLogEE(null,"deleteSourceFile() => could not get ref to picked file");
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
                e.printStackTrace(); // could be access right : Permission to access file: /storage/emulated/0/Audiobooks/Folder Fun letters/ازة-بالقراءات-العش.mp3 is denied
                tellError(getResources().getString(R.string.Error_Import_track_duration_extraction) + " // path : " + zePath);
                myLogEE(e,"error getting duration of media for " + zePath);
            }
        } else {
            tellError(getResources().getString(R.string.Error_Import_track_duration_nofile) + " // path : " + zePath);
            myLogEE(null,"error getting duration of media, file does not exist in path : " + zePath);
        }
        //myLogD("duration for [" + zePath + "] is " + duration);
        return duration;
    }
    private long getMediaDurationFromUri(Context context, Uri uri) {
        long duration = 0;
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(context, uri);
            String durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            duration = Long.parseLong(durStr);
        } catch (Exception e) {
            tellError(getResources().getString(R.string.Error_Import_track_duration_extraction) + " // uri: " + uri);
            myLogEE(e,"error getting duration of media for uri: [" + uri + "]");
        }
        //myLogD("duration for [" + uri + "] is " + duration);
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


    private void copyFileLocal(String destinationFullPath) {
        File destFile = new File(destinationFullPath);
        String destinationFolderPath = destFile.getParent();
        String destinationName = destFile.getName();
        copyFileLocal(destinationFolderPath, destinationName);
    }

    private void copyFileLocal(String destinationFolderPath, String destinationName) {
        boolean checkSize = true;
        long forceSize = -1;
        if ("Folder".equals(type_dynamic)) {
            forceSize = fullFolderSize;
        }
        myLog("Future Folder Path : [" + destinationFolderPath + "]");
        launchCopyFileService(uri_dynamic, destinationFolderPath, destinationName, type_dynamic, checkSize, forceSize);
    }

    private void proceedAfterCopyLocal(String localCopyFullPath) {
        bookState = Pref.getLoadBookTaskState();
        myLog("proceedAfterCopyLocal() - Type : [" + type_dynamic + "]"
                + "\nsourceLocation = [" + bookState.sourceLocation + "]"
                + "\n localCopyFullPath = [" + localCopyFullPath + "]");

        if (getExtension(localCopyFullPath).equals("zip")) {
            set_type_dynamic("ZIP");
        }

        if (type_dynamic.equals("ZIP")) {
            launchUnzipService(localCopyFullPath, bookState.futureFolderPath);

        } else if (type_dynamic.equals("M4B") && bookState.optionSplit) {
            launchSplitM4bService(localCopyFullPath, bookState.futureFolderPath);

        } else {
            if (!Objects.isNull(bookState.sourceLocation) && (bookState.sourceLocation.equals("cloud") || bookState.sourceLocation.equals("web"))) {
                myLog("from cloud/web");
                future_DB_folder_path = bookState.futureFolderPath;
                set_uri_dynamic(Uri.fromFile(new File(localCopyFullPath)));
                audioFileArrayList = new ArrayList<>();

                myLogD("* adding file : [" +  future_DB_folder_path + ']');
                long duration = 0;
                try {
                    duration = getMediaDurationFromPath(future_DB_folder_path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                myLogD("* Duration : [" +  duration + ']');
                audioFileArrayList.add(new AudioFileInfo(future_DB_folder_path, duration));

            } else {
                myLog("from other locations");
                future_DB_folder_path = bookState.futureFolderPath;
                set_uri_dynamic(Uri.fromFile(new File(localCopyFullPath)));
            }
            saveFolder();
        }
    }
    // -----------------------------------------------------------------------------------------------------------------------------------------------------------
    // -----------------------------------------------------------------------------------------------------------------------------------------------------------
    // -----------------------------------------------------------------------------------------------------------------------------------------------------------

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
        proceedAfterCopyLocal(downloadedFileFullPath);
        clearDownloadFinished(this);
    }
    @Override
    public void downloadService_tellError(String errorText) {
        myLogW("downloadService_tellError [" + errorText + "]");
        //clearDownloadFinished(this);
        tellNonBlockingError(errorText);
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
    public void copyFileService_tellEnd(String destinationFolderPath, String destinationFolderName) {
        myLog("Copyfile tell End for type : " + type_dynamic);
        if (Objects.equals(type_dynamic, "Folder")) {
            proceedAfterCopyLocal(destinationFolderPath);
        } else {
            proceedAfterCopyLocal(destinationFolderPath + "/" + destinationFolderName);
        }
    }
    @Override
    public void copyFileService_tellError(String errorText) {
        myLogW("Copyfile tell Error");
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
        myLogW("Unzip service tell Error");
        tellError(errorText);
    }
    @Override
    public void unzipService_tellNonBlockingError(String errorText) {
        myLog("Unzip service tell Non Blocking Error");
        tellNonBlockingError(errorText);
    }
    @Override
    public void unzipService_tellEnd(String destinationFolderPath) {
        myLog("Unzip Service tells End : [" + destinationFolderPath + "]");
        tellProgress(PROGRESS[7], PROGRESS_TEXT[7]);
        DocumentFile dfPickedDir;
        try {
            dfPickedDir = DocumentFile.fromFile(new File(destinationFolderPath));
        } catch (Exception e) {
            myLogEE(e,"error getting DocumentFile.fromFile");
            return;
        }
        populateArrayListOfTracksFromFolder(dfPickedDir);
    }
    /**
     **********************************
     *    SPLIT M4B CALLBACKS received
     *********************************
     */
    @Override
    public void splitM4bService_tellProgress(String progressText, int progressVal) {
        tellProgress(PROGRESS[6] + progressVal * (PROGRESS[7] - PROGRESS[6]) / 100, progressText);
    }
    @Override
    public void splitM4bService_tellError(String errorText) {
        myLogW("SplitM4b service tell Error");
        tellError(errorText);
    }
    @Override
    public void splitM4bService_tellNonBlockingError(String errorText) {
        myLog("SplitM4b service tell Non Blocking Error");
        tellNonBlockingError(errorText);
    }
    @Override
    public void splitM4bService_tellWarning(String warningText) {
        myLog("SplitM4b service tell Non Blocking Error");
        tellWarning(warningText);
    }
    @Override
    public void splitM4bService_tellEnd(String destinationFolderPath) {
        myLog("SplitM4b Service tells End : [" + destinationFolderPath + "]");
        tellProgress(PROGRESS[7], PROGRESS_TEXT[7]);
        DocumentFile dfPickedDir;
        try {
            dfPickedDir = DocumentFile.fromFile(new File(destinationFolderPath));
        } catch (Exception e) {
            myLogEE(e,"error getting DocumentFile.fromFile");
            return;
        }
        populateArrayListOfTracksFromFolder(dfPickedDir);
    }
    /**
     **********************************
     *    CALLBACKS sent
     *********************************
     */
    public void tellProgressNoLog(int progressVal,String progressText) {
        notifyProgress(progressText, progressVal);
    }
    public void tellProgress(int progressVal,String progressText) {
        myLogD("tellProgress : " + progressVal + " - " + progressText.replace("\n"," ## "));
        notifyProgress(progressText, progressVal);
    }
    private void tellEnd() {
        setWorkFlowFinished(this);
        notifyEnd();
        isBusy = false;
        myLog("killing Service");
        stopSelf();
    }
    private void tellError(String txt) {
        setWorkFlowFinished(this);
        notifyError(txt);
        myLogW("tellError... [" + txt + "]");
        isBusy = false;
        myLog("tellError... killing Service");
        stopSelf();
    }
    public void tellNonBlockingError(String txt) {
        myLogD("NonBlockingError : " + txt);
        notifyNonBlockingError(txt);
    }
    public void tellWarning(String txt) {
        myLogD("Warning : " + txt);
        notifyWarning(txt);
    }
    /**
     **********************************
     *    DOWNLOAD JOB.SERVICE BROADCASTS
     *********************************
     */
    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String audioBookTitle = intent.getStringExtra("audioBookTitle");
            switch (Objects.toString(intent.getAction())) {
                case "BOOKPLAYER_DOWNLOAD_FINISHED":
                    String filePath = intent.getStringExtra("downloadedFileFullPath");
                    if (filePath != null) {
                        downloadService_tellEnd(filePath);
                    }
                    break;
                case "BOOKPLAYER_DOWNLOAD_ERROR":
                    String errorText = intent.getStringExtra("errorText");
                    downloadService_tellError(errorText);
                    break;
            }
        }
    };

    private void set_type_dynamic(String newType) {
        myLog("type_dynamic : " + type_dynamic + " => " + newType);
        type_dynamic = newType;
    }
    private void set_uri_dynamic(Uri newUri) {
        myLog("uri_dynamic : " + uri_dynamic + " => " + newUri);
        uri_dynamic = newUri;
    }

    private void notifyProgress(String msg, int progress) {
        lastMessage = msg;
        lastProgress = progress;
        if (mCallBacks != null) {
            mCallBacks.tellProgress(msg, progress);
        } else {
            myLog("notifyProgress skipped (no callback yet)");
        }
    }

    private void notifyEnd() {
        if (mCallBacks != null) {
            mCallBacks.tellEnd();
        } else {
            myLog("notifyEnd skipped (no callback yet)");
        }
    }

    private void notifyError(String errorMsg) {
        if (mCallBacks != null) {
            mCallBacks.tellError(errorMsg);
        } else {
            myLog("notifyError skipped (no callback yet)");
        }
    }
    public void notifyNonBlockingError(String txt) {
        if (mCallBacks != null) {
            mCallBacks.tellNonBlockingError(txt);
        } else {
            myLog("notifyNonBlockingError skipped (no callback yet)");
        }
    }

    public void notifyWarning(String txt) {
        if (mCallBacks != null) {
            mCallBacks.tellWarning(txt);
        } else {
            myLog("notifyWarning skipped (no callback yet)");
        }
    }
}
