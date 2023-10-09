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
import android.os.Build;
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

import static android.os.Build.VERSION.SDK_INT;
import static com.driot.bookplayer.activities.OptionActivity.DEFAULT_COPY_ZIP_LOCAL;
import static com.driot.bookplayer.activities.OptionActivity.DEFAULT_UNZIP_LOCAL;
import static com.driot.bookplayer.activities.OptionActivity.SHARED_PREFERENCES_OPTIONS;
import static com.driot.bookplayer.global.Var.FOLDER_UNZIPPED;
import static com.driot.bookplayer.global.Var.FOLDER_ZIPPED;
import static com.driot.bookplayer.utils.Tonio.FormatNameForDisplay;
import static com.driot.bookplayer.utils.Tonio.fileExists;
import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Tonio.getFileNameFromPath;
import static com.driot.bookplayer.utils.Tonio.getMimeType;
import static com.driot.bookplayer.utils.Tonio.stripExtension;
import static com.driot.bookplayer.utils.Utils.copyStream;
import static com.driot.tonylib.KanLogger.myToastE;
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

    private String copiedZipFileFullPath;
    private String zipFileFullPath;

    public static final int PROGRESS_COPY_START = 3;
    public static final int PROGRESS_COPY_END = 20;
    public static final int PROGRESS_UNZIP_START = 20;
    public static final int PROGRESS_UNZIP_END = 80;

    private final IBinder binder = new AddResourceServiceBackgroundBinder();

    public static final String NOTIFICATION_ADDRESOURCE_NAME = "NOTIFICATION_ADDRESOURCE_NAME";
    public static final String NOTIFICATION_ADDRESOURCE_PROGRESS = "NOTIFICATION_ADDRESOURCE_PROGRESS";
    public static final String NOTIFICATION_ADDRESOURCE_ERROR = "NOTIFICATION_ADDRESOURCE_ERROR";
    public static final String NOTIFICATION_ADDRESOURCE_END = "NOTIFICATION_ADDRESOURCE_END";

    private FolderAttrib myFolder;
    private ZipFile zipFile;
    private ArrayList<String> audioFileArrayList;
    private final int[] InsertedFolderId = {0};

    private int nbFileSaved, nbFileToSave;

    private Uri uri;
    private String type;

    private DocumentFile pickedDir;
    private boolean resourceSelected;

    private File externalZipFile;
    private File internalZipFile;
    private File finalZipFile;
    private File localUnzipFolder;
    private File finalLocalFolder;
    private String destinationFolder;

    private boolean Zip_DoCopylocal;
    private boolean Zip_DoUnzip;

    public static boolean isBusy;

    // Callbacks
    //-----------------------------
    public interface Callbacks{
        void updateProgress(String progressText, int progressVal);
        void updateError(String errorText);
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
        return super.onUnbind(intent);
    }
    public class AddResourceServiceBackgroundBinder extends Binder {
        public AddResourceService getService() {
            myLog("class AddResourceService BackgroundBinder.getService(); ");
            return AddResourceService.this;
        }
    }
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
/*
    private final BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            int progressOverall = 0;
            switch (intent.getAction()) {
*/
/*
                case NOTIFICATION_UNZIP_SERVICE_PROGRESS:
                    progressOverall = PROGRESS_UNZIP_START + intent.getIntExtra("progressVal", 0) * (PROGRESS_UNZIP_END - PROGRESS_UNZIP_START) ;
                    tellProgress(progressOverall, intent.getStringExtra("progressText"));
                    myLog("NOTIFICATION_UNZIP_SERVICE_PROGRESS : " + progressOverall);
                    break;

                case NOTIFICATION_COPYFILE_SERVICE_PROGRESS:
                    progressOverall = PROGRESS_COPY_START + intent.getIntExtra("progressVal", 0) * (PROGRESS_COPY_END - PROGRESS_COPY_START) ;
                    tellProgress(progressOverall, intent.getStringExtra("progressText"));
                    myLog("NOTIFICATION_COPYFILE_SERVICE_PROGRESS : " + progressOverall);
                    break;
*/
    /*
                case NOTIFICATION_COPYFILE_SERVICE_ERROR:
                    myLog("broadcast received " + NOTIFICATION_COPYFILE_SERVICE_ERROR);
                    tellError(intent.getStringExtra("errorText"));
                    stopSelf();
                    break;

                case NOTIFICATION_COPYFILE_SERVICE_END:
                    myLog("broadcast received " + NOTIFICATION_COPYFILE_SERVICE_END);
                    if (intent.getBooleanExtra("ok",false)) {
                        //myToast("getString(R.string.CopyFile_Success)");
                        //AddResourceActivity.this.setResult(Activity.RESULT_OK);
                        tellProgress(PROGRESS_COPY_END, intent.getStringExtra("progressText"));
                        unzipZipLocal();
                    } else {
                        tellError("NOTIFICATION_COPYFILE_SERVICE_END : Shit Happens");
*/
                        /*
                        String message = intent.getStringExtra("message");
                        if (!message.equals("")) {
                            myToast(message);
                        } else {
                            myToast("IMPORT CANCELLED !");
                        }

                         */
    /*
                    }
                    break;
                    //finish();

                case NOTIFICATION_UNZIP_SERVICE_ERROR:
                    myLog("broadcast received " + NOTIFICATION_UNZIP_SERVICE_ERROR);
                    tellError(intent.getStringExtra("errorText"));
                    stopSelf();
                    break;

                case NOTIFICATION_UNZIP_SERVICE_END:
                    myLog("broadcast received " + NOTIFICATION_UNZIP_SERVICE_END);
                    if (intent.getBooleanExtra("ok",false)) {
                        tellProgress(PROGRESS_UNZIP_END, intent.getStringExtra("progressText"));
                        unzipZipLocal();
                    } else {
                        tellError("NOTIFICATION_UNZIP_SERVICE_END : Shit Happens");
                    }
                    break;

            }
        }
    };
*/
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
            mCopyFileServiceBound = false;
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
            mCopyFileServiceBound = false;
        }

    };

    private void launchCopyFileService(Uri uri, String destinationFolderPath, String destinationFileName) {
        myLog("launchCopyFileService");
        //Intent intentCopyFileService = new Intent(AddResourceService.this, CopyFileService.class);
        Intent intentCopyFileService = new Intent(this, CopyFileService.class);
        intentCopyFileService.putExtra("Uri", uri);
        intentCopyFileService.putExtra("destinationFolderPath", destinationFolderPath);
        intentCopyFileService.putExtra("destinationFileName", destinationFileName);
        copiedZipFileFullPath = destinationFolderPath + "/" + destinationFileName;
        startService(intentCopyFileService);
        boundToCopyFileService = false;
        try {
            boundToCopyFileService = bindService(intentCopyFileService, copyFileServiceConnection, Context.BIND_AUTO_CREATE); //error Log : Activity XXX has leaked ServiceConnection
        } catch (Exception e) {
            myLogE("ERROR bind to Service in launchCopyFileService ");
            myLogE(e.getMessage());
        }
        myLog("call start & bind to copyFileService from launchCopyFileService - bound result :" + boundToCopyFileService + "");
    }

    private void launchUnzipService(String zipFilePath, String destinationFolderPath) {
        myLog("launchUnzipService");
        Intent intentUnzipService = new Intent(this, UnzipService.class);
        intentUnzipService.putExtra("zipFilePath", zipFilePath);
        intentUnzipService.putExtra("destinationFolderPath", destinationFolderPath);
        startService(intentUnzipService);

        boundToUnzipService = false;
        try {
            boundToUnzipService = bindService(intentUnzipService, unzipServiceConnection, Context.BIND_AUTO_CREATE); //error Log : Activity XXX has leaked ServiceConnection
        } catch (Exception e) {
            myLogE("ERROR bind to Service in launchUnzipService ");
            myLogE(e.getMessage());
        }
        myLog("call start & bind to unzipService from launchUnzipService - bound result :" + boundToUnzipService + "");
    }


        // single file
    ///////////////////////////
    private boolean populateArrayListOfTracksFromFile() {
        myLog("populateArrayListOfTracksFromFile [" + pickedDir.getUri() + "] - single file");
        boolean resourceSelected = false;

        uri = pickedDir.getUri();

        try { myLog(DocumentFile.fromTreeUri(this, uri).getParentFile().getName()); }
        catch (Exception e) {
            myLogE("ko " + e.getMessage());
        }

        if (pickedDir != null && !(pickedDir.isDirectory())) {

            // constructeur pour mon pti folder
            myFolder = new FolderAttrib(getApplicationContext(), uri, false, true);
            if (myFolder.getsFolderName()==null) {
                tellError("Error while creating record, cancelling operation");
                return false;
            }
            tellName(myFolder.getsFolderName());

            if (myFolder.isFolderKO()) {
                String error = getString(R.string.Error_Import_FilePathKO);
                //if (myFolder.isLocatedInDownloadFolder())  error += "/n" + getString(R.string.Error_Import_BetterTryNoDownloadFolder);
                tellError(error);
            } else {
                myLog("file ok");

                audioFileArrayList = new ArrayList<String>();

                addAudioFileUnique(pickedDir);

                resourceSelected = true;
            }
        } else {
            tellError(getString(R.string.Error_Import_IsNotFile));
        }
        return resourceSelected;
    }


    private boolean populateArrayListOfTracksFromZipFile() {
        boolean resourceSelected = false;

        zipFile = null;
        try {
            zipFile = new ZipFile(finalZipFile);
        } catch (Exception e) {
            myLogE("new ZipFile() KO");
            tellError(getString(R.string.Error_Import_ParsingZipFile2) + "\n" + getString(R.string.Error_Import_ParsingZipFile_advice));
            e.printStackTrace();
            return false;
        }

        myLog("ZipFile instantiated ok");

        audioFileArrayList = new ArrayList<String>();
        ArrayList<String> zipFileListing;
        zipFileListing = new ArrayList<String>();

        try {
            for (Enumeration e = zipFile.entries(); e.hasMoreElements(); ) {
                ZipEntry entry = (ZipEntry) e.nextElement();
                if (!entry.isDirectory()) {
                    String zeName = entry.getName();
                    zipFileListing.add(zeName);
                }
            }
            if (zipFileListing.size() != 0) {
                // filter audio file
                for (String s : zipFileListing) {
                    if (getMimeType(s).equals("audio/mpeg") || getMimeType(s).equals("audio/mp4")) {
                        myLog("adding to audioFileArrayList, audio file : [" + s + ']');
                        audioFileArrayList.add(s);
                    }
                }
            }
            Collections.sort(audioFileArrayList, new Utils.AlphanumericComparator());

            resourceSelected = true;

        } catch (Exception e) {
            tellError(getString(R.string.Error_Import_ParsingZipFile2) + "\n" + getString(R.string.Error_Import_ParsingZipFile_advice));
            e.printStackTrace();
        }
        return resourceSelected;
    }


    private boolean populateArrayListOfTracksFromFolder() {
        myLog("populateArrayListOfTracksFromFolder " + pickedDir.getUri());
        boolean resourceSelected = false;

        uri = pickedDir.getUri();

        // Si c'est pas un dossier, on prend le dossier parent...
        if (!pickedDir.isDirectory()) {
            pickedDir = DocumentFile.fromTreeUri(this, uri).getParentFile();
            myLog("Parent Folder taken in place");
        }

        if (pickedDir != null && pickedDir.isDirectory()) {

            // constructeur pour mon pti folder
            myFolder = new FolderAttrib(getApplicationContext(), uri, false, false);
            if (myFolder.getsFolderName()==null) {
                tellError("Error while creating record, cancelling operation");
                return false;
            }
            tellName(myFolder.getsFolderName());

            if (myFolder.isFolderKO()) {
                String error = getString(R.string.Error_Import_FolderPathKO);
                if (myFolder.isLocatedInDownloadFolder())  error += "... " + getString(R.string.Error_Import_BetterTryNoDownloadFolder);
                tellError(error);
            } else {
                myLog("folder ok");
                myLog("scanning for audio file recursively from folder [" + pickedDir.getName() + "]");

                audioFileArrayList = new ArrayList<String>();

                addAudioFileRecursive(pickedDir);

                Collections.sort(audioFileArrayList, new Utils.AlphanumericComparator());

                if (audioFileArrayList.size()==0) {
                    myLog("No File found in directory : [" + pickedDir.getName() + ']');
                } else {
                    myLog(audioFileArrayList.size() + " files found in directory : [" + pickedDir.getName() + ']');
                }

                resourceSelected = true;
            }
        } else {
            tellError(getString(R.string.Error_Import_IsNotFolder));
        }
        return resourceSelected;
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
        String mime;
        resourceSelected = false;

        /*
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_COPYFILE_SERVICE_ERROR));
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_COPYFILE_SERVICE_END));
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_COPYFILE_SERVICE_PROGRESS));
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_UNZIP_SERVICE_ERROR));
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_UNZIP_SERVICE_END));
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_UNZIP_SERVICE_PROGRESS));
         */
        //registerReceiver(receiver, new IntentFilter(NOTIFICATION_COPYFILE_SERVICE_END));

        switch (type) {
            ///---------------------------------------------
            /// FILE
            ///---------------------------------------------
            case "File":
                try {
                    pickedDir = DocumentFile.fromSingleUri(this, uri);
                } catch (Exception e) {
                    myToastE("error getting DocumentFile.fromSingleUri : " + e.getMessage());
                    break;
                }
                try {
                    mime = pickedDir.getType();
                } catch (Exception e) {
                    myToastE("Mime Type could not be found");
                    break;
                }

                if (mime.equals("audio/mp4")) { //   application/mp4

                    myLog("MP4 : [" + pickedDir.getType() + "]");
                    resourceSelected = populateArrayListOfTracksFromFile();
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
                    resourceSelected = populateArrayListOfTracksFromFile();

                } else {
                    myToastE("This MIME type is not supported : [" + mime + "]");
                }
                break;



            ///---------------------------------------------
            /// FOLDER
            ///---------------------------------------------
            case "Folder":
                // TODO First thing : check if folder already exists, now checked after scan of files, just before DB insertion
                //checkIfFolderAlreadyExist2();

                try {
                    pickedDir = DocumentFile.fromTreeUri(this, uri);

                } catch (Exception e) {
                    myLogE("error getting DocumentFile.fromTreeUri : " + e.getMessage());
                    break;
                }
                resourceSelected = populateArrayListOfTracksFromFolder();
                break;

            ///---------------------------------------------
            /// ZIP FILE
            ///---------------------------------------------
            case "ZIP":
                /// multiples possibilities
                //          lecture direct du zip tel quel (KO android 11)
                ///         unzip en local, et lecture local folder (KO android 11)
                ///         copy zip en local et lecture directe
                ///         copy zip en local, unzip en local, et lecture local folder

                ////*****************************************************************************************************
                //if Android >= 11, on copie direct en local avant toute chose.
                if (Build.VERSION.SDK_INT >= 30) {
                    myLog("Android >= 11, copy locally before everything else");
                    String newPathFromCopy = "";
                    myLog("Android >= 11, Picked Uri = [" + uri.toString() + "]");
                    String zipFileName = "";
                    try {
                        InputStream attachment = this.getContentResolver().openInputStream(uri);
                        if (attachment != null) {
                            zipFileName = getContentName(this.getContentResolver(), uri);
                            if (zipFileName != null) {
                                myLog("Android >= 11, zipFileName = [" + zipFileName + "]");
                            }
                            attachment.close();
                        } else {
                            tellError("Could not open input stream from selected Uri [" + uri.toString() + "]");
                        }
                    } catch (Exception e) {
                        tellError("error dealing with selected Uri : " + e.getMessage());
                        e.printStackTrace();
                    }

                    // check Not Already Imported
                    //*****************************
                    String localFolderName = getsFolderName_withUnderscore_fromZipFileName(zipFileName);
                    myLog("Android >= 11, Future Folder Name : [" + localFolderName + "]");
                    String destinationFolderPath = getFilesDir().getAbsolutePath() + "/" + FOLDER_UNZIPPED + "/" + localFolderName;
                    myLog("Android >= 11, Future Folder Path : [" + destinationFolderPath + "]");
                    localUnzipFolder = new File(destinationFolderPath);
                    String futureUri = Uri.fromFile(localUnzipFolder).toString();
                    myLog("Android >= 11, futureUri = [" + futureUri + "]");

                    internalZipFile = new File(destinationFolderPath + "/" + localFolderName + ".zip");
                    finalLocalFolder = new File(destinationFolderPath);
                    finalZipFile = internalZipFile;
                    destinationFolder = destinationFolderPath;
                    checkIfFolderAlreadyExist3(futureUri, localFolderName); //TODO without underscores ???
                    return;
                    // c'est la fin, on passe a continue3 si c'est good
                    // (pour eviter le W/MIUIScout App:APP_SCOUT_WARNING et le HANG) on doit faire le lourd dans un backgroundThread (meme depuis un service... car il s'execute de base sur le MainUi)
        }
/*
                myLog("Entry case ZipFile");
                myFolder = new FolderAttrib(getApplicationContext(), uri, true, false);
                myLog("Entry case ZipFile 2");
                tellName(myFolder.getsFolderName());

                if (myFolder.isFolderKO()) {
                    myLogE("myFolder.isFolderKO()");
                    String error = getString(R.string.Error_Import_ZipFilePathKO);
                    if (myFolder.isLocatedInDownloadFolder())  error += "\n" + getString(R.string.Error_Import_BetterTryNoDownloadFolder);
                    tellError(error);
                    return;
                }

                externalZipFile = new File(myFolder.getsRealFolderPath());

                // get options values (DoCopy, DoUnzip)  from shared preferences
                loadOptionValues();

                //// ANDROID 11 (R and up)
                if (SDK_INT >= Build.VERSION_CODES.R) {
                    Zip_DoCopylocal = true;
                    myLog("====> Android 11 ====> copy in local app folder (and override user option if any)");
                }

                myLog("Do Copy locally : " + Zip_DoCopylocal);
                myLog("Do Unzip : " + Zip_DoUnzip);

                //------------------------------------------------------
                // find future location of tracks (to check if already imported)
                //------------------------------------------------------
                String futureUri;

                // copy and unzip, lecture des mp3 from internal memory
                if (Zip_DoUnzip) {
                    destinationFolder = getFilesDir().getAbsolutePath() + "/" + FOLDER_UNZIPPED + "/" + myFolder.getsFolderName_withUnderscore();
                    localUnzipFolder = new File(destinationFolder);
                    futureUri = Uri.fromFile(localUnzipFolder).toString();
                    String destinationPath = getFilesDir().getAbsolutePath() + "/" + FOLDER_UNZIPPED + "/" + myFolder.getsFolderName_withUnderscore() + ".zip";
                    internalZipFile = new File(destinationPath);
                    finalLocalFolder = localUnzipFolder;
                } else {

                    // copy but no unzip, lecture directe du zip from internal memory
                    if (Zip_DoCopylocal) {
                        String destinationPath = getFilesDir().getAbsolutePath() + "/" + FOLDER_ZIPPED + "/" + myFolder.getsFolderName_withUnderscore() + ".zip";
                        internalZipFile = new File(destinationPath);
                        futureUri = Uri.fromFile(internalZipFile).toString();
                        finalLocalFolder = new File(getFilesDir().getAbsolutePath() + "/" + FOLDER_ZIPPED);

                        // ni copy, ni unzip, lecture directe du zip from external memory
                    } else {
                        futureUri = myFolder.getsFolderUri();
                    }
                }

                // == check if already imported
                checkIfFolderAlreadyExist2(futureUri);
                break;
*/
            default:
                myLogE("Incorrect type : **" + type + "**");
        }

        if (resourceSelected) go1();
    }

    private void continueAfterCheck() {
        myLog("continueAfterCheck, launching Thread");
        Thread thread_one;
        thread_one = new Thread() {
            @Override
            public void run() {
                if (Zip_DoCopylocal) {
                    copyZipLocal();
/*
                    if (!copyZipLocal()) {
                    {
                        myLogE("copyZipFile KO");
                        //tellError(getResources().getString(R.string.Error_Import_Copying_zip_local));
                        return;
                    }
                    // create new ref to new zip file
                    //....

 */
                    finalZipFile = internalZipFile;
                } else {
                    finalZipFile = externalZipFile;
                }

                if (Zip_DoUnzip) {
                    unzipZipLocal();
/*
                    if (!unzipZipLocal()) {
                        myLogE("Unzip File KO");
                        return;
                    }

 */
                    // check le delete sur internalZikFile

                    // create new ref to new set of files
                    try {
                        pickedDir = DocumentFile.fromFile(localUnzipFolder);
                    } catch (Exception e) {
                        myLogE("Error DocumentFile.fromFile " + e.getMessage());
                        pickedDir = null;
                    }

                    resourceSelected = populateArrayListOfTracksFromFolder();

                    if (resourceSelected && pickedDir != null) {
                        go1();
                    } else {
                        tellError(getResources().getString(R.string.Error_Import_general_error));
                        //TODO killLocalUnzipFolder();
                    }

                } else {
                    // no unzip, link direct on zip file
                    resourceSelected = populateArrayListOfTracksFromZipFile();
                    if (resourceSelected) {
                        go1();
                    } else {
                        tellError(getResources().getString(R.string.Error_Import_general_error));
                        //TODO killLocalZipFile();
                    }
                }
            }
        };
        thread_one.start();
    }

    private void go1() {
        myLog("go1");
        //Thread thread_one;
        //thread_one = new Thread() {
        //    @Override
        //    public void run() {
                goFolder();
        //    }
        //};
        //thread_one.start();
    }

    private void goFolder() {
        if (audioFileArrayList.size() == 0) {
            tellError(getString(R.string.Error_Import_NoMediaInFolder));
        } else {
            myLog(audioFileArrayList.size() + " " + getString(R.string.Import_nMediaInFolder));
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
                tellEnd(getString(R.string.Error_Import_FolderAlreadyImported));
            } else {
                myLog("ok on continue -       (folder does not already exist)");
                tellProgress(5,getResources().getString(R.string.Import_Progress_check_not_already_imported));
                saveFolder();
            }
        }, throwable -> {
            tellError(getResources().getString(R.string.Error_Import_checking_Folder_Exists) + throwable.getMessage());
        });
    }

    private void checkIfFolderAlreadyExist2(String uriFolder) {
        // For Android 11 zip file copied in local folder
        myLog("Checking uri doesn't exist in DB : " + uriFolder);
        Observable.fromCallable(() -> {
            boolean bcheckIfFolderExist = false;
            long lcheckIfFolderExist = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .FolderDao()
                    .folderAlreadyExist2(uriFolder);
            if (lcheckIfFolderExist>0) { bcheckIfFolderExist = true;}
            return bcheckIfFolderExist;
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(result -> {
            if (result) {
                tellError(getString(R.string.Error_Import_FolderAlreadyImported));
            } else {
                myLog("Ok, folder doesn't already exist in DB");
                tellProgress(1,getResources().getString(R.string.Import_Progress_check_not_already_imported));
                continueAfterCheck();
                //Android11();
            }
        }, throwable -> {
            tellError(getResources().getString(R.string.Error_Import_checking_Folder_Exists) + throwable.getMessage());
        });
    }

    private void checkIfFolderAlreadyExist3(String uriFolder, String folderName) {
        // For Android 11 zip file copied in local folder
        myLog("3.Checking uri doesn't exist in DB : " + uriFolder);
        Observable.fromCallable(() -> {
            boolean bcheckIfFolderExist = false;
            long lcheckIfFolderExist = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .FolderDao()
                    .folderAlreadyExist3(uriFolder, folderName);
            if (lcheckIfFolderExist>0) { bcheckIfFolderExist = true;}
            return bcheckIfFolderExist;
            //TODO if only name the same, just import with a new name... but is this possible ? wanted ?
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(result -> {
            if (result) {
                myLog("KO, folder does already exist in DB");
                tellError(getString(R.string.Error_Import_FolderAlreadyImported));
            } else {
                myLog("OK, folder doesn't already exist in DB");
                tellProgress(1,getResources().getString(R.string.Import_Progress_check_not_already_imported));
                copyZipLocal();
            }
        }, throwable -> {
            tellError(getResources().getString(R.string.Error_Import_checking_Folder_Exists) + throwable.getMessage());
        });
    }

    private void copyZipLocal() {
        Long zipFileSize = -1L;
        String originalZipFilePath = uri.getPath();
        String destinationFolderPathForZip = finalLocalFolder.getPath();
        String destinationFileNameForZip = extractName(originalZipFilePath);
        copiedZipFileFullPath = destinationFolderPathForZip + "/" + destinationFileNameForZip;

        if (originalZipFilePath != null) externalZipFile = new File(originalZipFilePath);
        if (externalZipFile.exists()) zipFileSize = externalZipFile.length();
        if (zipFileSize > 0) {
            myLog("ze Size : " + zipFileSize);
        } else {
            myLog("ERR : Cannot Check Size .... Size = " + zipFileSize + " .... Never Mind... let's copy");
        }
        myLog("\nfrom " + externalZipFile + " \nto " + internalZipFile + " \nusing " + finalLocalFolder);

        launchCopyFileService(uri, destinationFolderPathForZip, destinationFileNameForZip);
    }

    private void unzipZipLocal() {
        String destinationFolderPathForMp3 = finalLocalFolder.getPath();
        //getFilesDir().getAbsolutePath() + "/" + FOLDER_UNZIPPED;
        if (!Objects.equals(copiedZipFileFullPath, "")) {
            zipFileFullPath = copiedZipFileFullPath;
        }
        myLog("copiedZipFileFullPath : " + copiedZipFileFullPath);
        myLog("zipFileFullPath : " + zipFileFullPath);
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
                        tellProgress(8,getResources().getString(R.string.Import_Progress_checkingFiles));
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
        one = new Thread() {
            @Override
            public void run() {
                int i = 0;
                int progress = 0;
                String txtProgress = "";
                for (String s : audioFileArrayList) {
                    i++;
                    progress = (int) i * 100 / audioFileArrayList.size();
                    txtProgress = progress + "% - " + getString(R.string.Add_resource_reading_file) + " n°" + i + "/" + audioFileArrayList.size() + "\n" + getFileNameFromPath(s);
                    myLog("Registering file [" + s + "]");
                    saveFile(s, InsertedFolderId[0], i);
                    tellProgress(progress,txtProgress);
                }
            }
        };
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
                    tellError(getResources().getString(R.string.Error_Import_computing_folder_duration) + " : " +  throwable.getMessage());
                });
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
        }
        return duration;
    }

    public void tellProgress(int progressVal,String progressText) {
        //new Handler(getApplicationContext().getMainLooper()).postDelayed(new Runnable(){
        //    @Override
        //    public void run(){
        //myLog("test callbacks " + progressText.substring(0,10) + " - " + progressVal);
        mCallBacks.updateProgress(progressText, progressVal);
        /*
        Intent intent = new Intent(NOTIFICATION_ADDRESOURCE_PROGRESS);
                intent.putExtra("progressText",progressText);
                intent.putExtra("progressVal",progressVal); //entre 0 et 100
                sendBroadcast(intent);

         */
                //myLog("broadcast progress sent " + val + " - " + txt);
         //   }
        //}, 0);
    }

    private void tellEnd() {
        Intent intent = new Intent(NOTIFICATION_ADDRESOURCE_END);
        intent.putExtra("ok",true);
        sendBroadcast(intent);
        myLog("broadcast end sent");
        isBusy = false;
        stopSelf();
    }

    private void tellEnd(String KO_message) { // my first overload... ^^
        Intent intent = new Intent(NOTIFICATION_ADDRESOURCE_END);
        intent.putExtra("message",KO_message);
        sendBroadcast(intent);
        myLog("broadcast end sent");
        isBusy = false;
        stopSelf();
    }

    private void tellError(String txt) {
        mCallBacks.updateError(txt);
        Intent intent = new Intent(NOTIFICATION_ADDRESOURCE_ERROR);
        intent.putExtra("message",txt);
        sendBroadcast(intent);
        myLogE("broadcast error sent :" + txt);
        isBusy = false;
        stopSelf();
    }

    private void tellName(String txt) {
        Intent intent = new Intent(NOTIFICATION_ADDRESOURCE_NAME);
        intent.putExtra("name",txt);
        sendBroadcast(intent);
        myLog("broadcast name sent :" + txt);
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

    private static String getsFolderName_withUnderscore_fromZipFileName(String zipFileName) {
        String tmp = zipFileName.replace(" ","_");
        tmp = stripExtension(tmp);
        return tmp;
    }

    //-----------------------------
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
        myLog("tell End - go unzip");
        unzipZipLocal();
    }
    @Override
    public void tellErrorClient_fromCopy(String errorText) {
        tellError(errorText);
        myLogE("tell Error Copy");
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
        myLogE("tell Error unzip");
    }
    @Override
    public void tellEndClient_fromUnzip() {
        myLog("tell End - go do something else");
        startOverCaseFolder();
    }

}
