package com.driot.bookplayer.utils;

import android.app.ActivityManager;
import android.app.DownloadManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;
import androidx.sqlite.db.SimpleSQLiteQuery;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.FolderAttrib;
import com.driot.bookplayer.db.ZikFile;

//import org.apache.commons.io.FileUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Date;
import java.sql.Time;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static android.os.Build.VERSION.SDK_INT;
import static com.driot.bookplayer.activities.OptionActivity.DEFAULT_COPY_ZIP_LOCAL;
import static com.driot.bookplayer.activities.OptionActivity.DEFAULT_UNZIP_LOCAL;
import static com.driot.bookplayer.activities.OptionActivity.SHARED_PREFERENCES_OPTIONS;
import static com.driot.bookplayer.global.Var.FOLDER_MP4;
import static com.driot.bookplayer.global.Var.FOLDER_UNZIPPED;
import static com.driot.bookplayer.global.Var.FOLDER_ZIPPED;
import static com.driot.bookplayer.global.Var.ZIP_SIZE_MAX_COEF;
import static com.driot.bookplayer.utils.Tonio.fileExists;
import static com.driot.bookplayer.utils.Tonio.getAvailableInternalMemorySize;
import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Tonio.getFileNameFromPath;
import static com.driot.bookplayer.utils.Tonio.getMimeType;
import static com.driot.bookplayer.utils.Tonio.stripExtension;
import static com.driot.bookplayer.utils.Tonio.formatMem;
import static com.driot.bookplayer.utils.Utils.copyStream;
import static com.driot.bookplayer.utils.Utils.recursiveRemove;
import static com.driot.bookplayer.utils.Utils.unzip;
import static com.driot.tonylib.KanLogger.myLog;
import static com.driot.tonylib.KanLogger.myLogE;
import static com.driot.tonylib.KanLogger.myToastE;
import static com.driot.tonylib.TonioCommonStuff.MD5;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 23/11/20
 */

// TODO check if Service is Busy before starting another import

public class AddResourceService extends Service {

    public static final int PROGRESS_ZIP_BUFFER_COPY = 1024;
    public static final int PROGRESS_ZIP_START_COPY = 3;
    public static final int PROGRESS_ZIP_END_COPY = 20;
    public static final int PROGRESS_ZIP_START_UNZIP = 20;
    public static final int PROGRESS_ZIP_END_UNZIP = 80;

    private final IBinder binder = new AddResourceService.BackgroundBinder();
    static final String TAG = "AddResourceServ.";
    private static final boolean LOG_TRACE = true;

    public static final String NOTIFICATION_ADDRESOURCE_NAME = "NOTIFICATION_ADDRESOURCE_NAME";
    public static final String NOTIFICATION_ADDRESOURCE_PROGRESS = "NOTIFICATION_ADDRESOURCE_PROGRESS";
    public static final String NOTIFICATION_ADDRESOURCE_ERROR = "NOTIFICATION_ADDRESOURCE_ERROR";
    public static final String NOTIFICATION_ADDRESOURCE_END = "NOTIFICATION_ADDRESOURCE_END";

    private FolderAttrib myFolder;
    private ZipFile zipFile;
    private ArrayList<String> audioFileArrayList;
    private int[] InsertedFolderId = {0};
    private DocumentFile[] myZikFileList;

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

    @Override
    public void onCreate() {
        super.onCreate();
        myLog("onCreate()");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        uri = intent.getParcelableExtra("Uri");
        type =  intent.getStringExtra("type");
        myLog("onStartCommand()");
        return START_NOT_STICKY;
    }


    private boolean populateArrayListOfTracksFromFile() {
        myLog("populateArrayListOfTracksFromFile " + pickedDir.getUri().toString());
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
        myLog("populateArrayListOfTracksFromFolder " + pickedDir.getUri().toString());
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

                myLog("Entry case ZipFile");
                myFolder = new FolderAttrib(getApplicationContext(), uri, true, false);
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
                    if (!copyZipLocal()) {
                        myLogE("copyZipFile KO");
                        return;
                    }
                    // create new ref to new zip file
                    //....
                    finalZipFile = internalZipFile;
                } else {
                    finalZipFile = externalZipFile;
                }

                if (Zip_DoUnzip) {
                    if (!unzipZipLocal()) {
                        myLogE("Unzip File KO");
                        return;
                    }
                    // chekc le delete sur internalZikFile

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
                        killLocalUnzipFolder();
                    }

                } else {
                    // no unzip, link direct on zip file
                    resourceSelected = populateArrayListOfTracksFromZipFile();
                    if (resourceSelected) {
                        go1();
                    } else {
                        tellError(getResources().getString(R.string.Error_Import_general_error));
                        killLocalZipFile();
                    }
                }
            }
        };
        thread_one.start();
    }

    private boolean copyZipLocal() {
        myLog("copyZipLocal - from externalZipFile to internalZipFile");

        // == Make Folder
        try {
            if (!finalLocalFolder.exists()) {
                if (!finalLocalFolder.mkdirs()) {
                    tellError(getResources().getString(R.string.Error_Import_Creating_Folders) + " for path : " + finalLocalFolder);
                    return false;
                }
            }
        } catch (Exception e) {
            tellError(getResources().getString(R.string.Error_Import_Creating_Folders));
            return false;
        }
        myLog("copyZipLocal - okay folder");

        // == Checking memory before copy
        int file_size = Integer.parseInt(String.valueOf(externalZipFile.length() / 1024 / 1024));
        long availableMegs = externalZipFile.getUsableSpace() / 1048576L;
        long availableMegs2 = getAvailableInternalMemorySize() / 1048576L;
        myLog("file size : " + file_size + "Mo" + "\navailable memory : " + availableMegs + " Mo" + "\navailable memory2 : " + availableMegs2 + " Mo");

        if (file_size * ZIP_SIZE_MAX_COEF > availableMegs2) {
            tellError(getResources().getString(R.string.Error_Import_NotEnoughMemory_line1) + "\n"
                    + getResources().getString(R.string.Error_Import_NotEnoughMemory_line2_1) + formatMem(availableMegs)  + "Mo" + "\n"
                    + getResources().getString(R.string.Error_Import_NotEnoughMemory_line2_2) + formatMem( availableMegs2) + "Mo" + "\n"
                    + getResources().getString(R.string.Error_Import_NotEnoughMemory_line3) + file_size + "Mo" + "\n"
                    + getResources().getString(R.string.Error_Import_NotEnoughMemory_line4_1) + ZIP_SIZE_MAX_COEF + getResources().getString(R.string.Error_Import_NotEnoughMemory_line4_2) + "\n"
                    + "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line5)
            );
            return false;
        }
        //if (true) return false;

        ContentResolver resolver = getContentResolver();
        InputStream is = null;
        tellProgress(PROGRESS_ZIP_START_COPY, getResources().getString(R.string.Import_Progress_copying_zip_file)
                + "\n"
                + "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line3) + file_size + "Mo"
                + "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line2_1) + formatMem(availableMegs) + "Mo"
                + "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line2_2) + formatMem(availableMegs2) + "Mo"
        );
        int nbBuffCopied = 0;
        ////////////////////////////////////////////////////////////////////////////////////////
        // copy of Zip file
        ////////////////////////////////////////////////////////////////////////////////////////
        try {
            is = resolver.openInputStream(uri);
            myLog("okay stream in");

            try {
                OutputStream out = new FileOutputStream(internalZipFile);
                myLog("okay stream out");

                try {
                    // Transfer bytes from in to out
                    byte[] buf = new byte[PROGRESS_ZIP_BUFFER_COPY];
                    int len;
                    while ((len = is.read(buf)) > 0) {
                        nbBuffCopied++;
                        out.write(buf, 0, len);

                        //display progress
                        if (nbBuffCopied % 1024 == 0) {
                            int nbMoCopied = nbBuffCopied * PROGRESS_ZIP_BUFFER_COPY / 1024 / 1024;
                            double progressValue = (double) nbMoCopied / (file_size) * 100;
                            tellProgress(PROGRESS_ZIP_START_COPY + (int) progressValue * (PROGRESS_ZIP_END_COPY - PROGRESS_ZIP_START_COPY) / 100,
                                    getResources().getString(R.string.Import_Progress_copying_zip_file)
                                            + "\n"
                                            + "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line3) + nbMoCopied + "Mo/" + file_size + "Mo"
                                            + "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line2_1) + formatMem(availableMegs) + "Mo"
                                            + "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line2_2) + formatMem(availableMegs2) + "Mo"
                            );
                        }

                    }
                    myLog("okay stream write");
                } catch (Exception e) {
                    myLogE("1 Copy of ZIP file from External Dir to Internal Dir failed.  -  " + e.getMessage());
                    e.printStackTrace();
                    return false;
                } finally {
                    out.close();
                }
            } catch (Exception e) {
                myLogE("2 Copy of ZIP file from External Dir to Internal Dir failed.  -  " + e.getMessage());
                e.printStackTrace();
                return false;
            } finally {
                is.close();
            }
        } catch (Exception e) {
            myLogE("ca chie a la lecture");
            myLogE(e.getMessage());
            return false;
        }

        myLog("file has been copied \nfrom " + uri.toString() + " \nto " + internalZipFile);
        myLog("file has been copied \nfrom " + externalZipFile + " \nto " + internalZipFile);
        return true;

    }

    private boolean unzipZipLocal() {
        ////////////////////////////////////////////////////////////////////////////////
        /// unzipping....
        ////////////////////////////////////////////////////////////////////////////////
        //tellProgress(40,getResources().getString(R.string.Import_Progress_unzipping_file));
        //unzip(externalZipFile[0], folder[0]);
        try {
            ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(finalZipFile)));
            myLog("unzipping in : " + localUnzipFolder);
            myLog("unzipping in : " + localUnzipFolder.getName());

            // check number of file in zip
            int nbZip;
            try {
                ZipFile zf = new ZipFile(finalZipFile.getAbsolutePath());
                nbZip = zf.size();
            } catch (Exception e) {
                myLogE("Couln't count element of zip file");
                nbZip = 10;
            }
            myLog("Zip file has : " + nbZip + " entries");

            int numCurZip = 0;

            try {
                ZipEntry ze;
                int count;
                byte[] buffer = new byte[8192];

                while ((ze = zis.getNextEntry()) != null) {
                    myLog("unzipping : " + ze.getName());

                    //bypass if zip contains only folder with same name at first level (doublons de dossier enchevetrés)
                    if (ze.getName().equals(localUnzipFolder.getName() + "/")) {
                        localUnzipFolder = new File(localUnzipFolder.getParent()); //attention faut le remettre à localUnzipFolder = new File(destinationFolder); après
                        myLog("unzipping : bypassing first directory");

                    } else {
                        numCurZip = numCurZip + 1;
                        double zeProgress = PROGRESS_ZIP_START_UNZIP + (double) numCurZip / nbZip * (PROGRESS_ZIP_END_UNZIP - PROGRESS_ZIP_START_UNZIP);
                        tellProgress((int) zeProgress,
                                getResources().getString(R.string.Import_Progress_unzipping_file) + numCurZip + "/" + nbZip
                                        + "\n" + "\n" + ze.getName());

                        File file = new File(localUnzipFolder, ze.getName());
                        File dir = ze.isDirectory() ? file : file.getParentFile();

                        if (!dir.isDirectory() && !dir.mkdirs())
                            throw new FileNotFoundException("Failed to ensure directory: " +
                                    dir.getAbsolutePath());
                        if (ze.isDirectory())
                            continue;
                        FileOutputStream fout = new FileOutputStream(file);
                        try {
                            while ((count = zis.read(buffer)) != -1)
                                fout.write(buffer, 0, count);
                        } finally {
                            fout.close();
                        }

                    }
        /* if time should be restored as well
        long time = ze.getTime();
        if (time > 0)
            file.setLastModified(time);
        */
                } // end du while
            } finally {
                zis.close();
                localUnzipFolder = new File(destinationFolder); // on reaffecte a la bonne valeur
            }
            ////////////////////////////////////////////////////////////////////////////////
            ////////////////////////////////////////////////////////////////////////////////

        } catch (Exception e) {
            tellError(getResources().getString(R.string.Error_Import_UnableToUnzip_line1) + " : " + e.getMessage()
                    + "\n" + "\n" + getResources().getString(R.string.Error_Import_UnableToUnzip_line2));
            e.printStackTrace();
            killLocalUnzipFolder(); //delete files after error
            return false;
        } finally {
            if (internalZipFile.delete()) {
                myLog("unzip done in folder, internal zip file deleted");
            } else {
                myLogE("unzip done in folder, ERROR deleting internal zip file");
            }
        }
        myLog("file has been unzipped");
        return true;
    }

    private void killLocalUnzipFolder() {
        if (!(recursiveRemove(localUnzipFolder))) {
            myLogE("killLocalUnzipFolder, AfterError, recursiveRemove(localUnzipFolder) KO");
        }
    }

    private void killLocalZipFile() {
        if (!internalZipFile.delete()) {
            myLogE("killLocalZipFile, AfterError");
        }
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
                    saveFile(s, InsertedFolderId[0], i, progress, txtProgress);
                    tellProgress(progress,txtProgress);
                }
            }
        };
        one.start();
    }

    private void saveFile(String sZikFileName, int mFolderId, int zeorder, int progress, String txtProgress) {
        // creating file
        ZikFile zikFile = new ZikFile();
        zikFile.setName(sZikFileName);
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
        //} else if (myFolder.isSingleFile()) {
        //    sFileFullPath = myFolder.getsRealFolderPath();
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

    public void tellProgress(int val, String txt) {
        //new Handler(getApplicationContext().getMainLooper()).postDelayed(new Runnable(){
        //    @Override
        //    public void run(){
                Intent intent = new Intent(NOTIFICATION_ADDRESOURCE_PROGRESS);
                intent.putExtra("progressText",txt);
                intent.putExtra("progress",val);
                sendBroadcast(intent);
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

    // binder
    //-----------------------------
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        myLog("onBind()");
        return binder;
    }
    public class BackgroundBinder extends Binder {
        public AddResourceService getService() {
            return AddResourceService.this;
        }
    }
    //-----------------------------

    private void myLog(String str) {
        if (LOG_TRACE) { Log.d("toto " + TAG + " ",str); }
    }
    private void myLogE(String str) {
        Log.e("toto " + TAG + " ",str);
    }

}
