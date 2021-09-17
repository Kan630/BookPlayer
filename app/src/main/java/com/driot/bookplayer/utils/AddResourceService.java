package com.driot.bookplayer.utils;

import android.app.ActivityManager;
import android.app.DownloadManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
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

import org.apache.commons.io.FileUtils;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static android.os.Build.VERSION.SDK_INT;
import static com.driot.bookplayer.global.Var.FOLDER_UNZIPPED;
import static com.driot.bookplayer.global.Var.ZIP_SIZE_MAX_COEF;
import static com.driot.bookplayer.utils.Tonio.fileExists;
import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Tonio.getFileNameFromPath;
import static com.driot.bookplayer.utils.Tonio.getMimeType;
import static com.driot.bookplayer.utils.Tonio.stripExtension;
import static com.driot.bookplayer.utils.Utils.copyStream;
import static com.driot.bookplayer.utils.Utils.unzip;
import static com.driot.tonylib.KanLogger.myLog;
import static com.driot.tonylib.KanLogger.myLogE;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 23/11/20
 */
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

    private boolean populateArrayListOfTracks(DocumentFile pickedDir,String forceName) {
        myLog("populateArrayListOfTracks " + pickedDir.getUri().toString());
        boolean resourceSelected = false;

        uri = pickedDir.getUri();

        // Si c'est pas un dossier, on prend le dossier parent...
        if (!pickedDir.isDirectory()) {
            pickedDir = DocumentFile.fromTreeUri(this, uri).getParentFile();
            myLog("Parent Folder taken in place");
        }

        if (pickedDir != null && pickedDir.isDirectory()) {

            // constructeur pour mon pti folder
            myFolder = new FolderAttrib(getApplicationContext(), uri, false,forceName);
            tellName(myFolder.getsFolderName());

            if (myFolder.isFolderKO()) {
                String error = getString(R.string.Error_Import_FolderPathKO);
                if (myFolder.isLocatedInDownloadFolder())  error += "/n" + getString(R.string.Error_Import_BetterTryNoDownloadFolder);
                tellError(error);
            } else {
                myLog("folder ok");

                audioFileArrayList = new ArrayList<String>();
                myZikFileList = pickedDir.listFiles();
                if (myZikFileList.length > 0) {
                    for (DocumentFile f : myZikFileList) { //check myZikFileList.length > 0 ??
                        if (f.getType() != null) {
                            if (f.getType().equals("audio/mpeg")) {
                                myLog(f.getName());
                                audioFileArrayList.add(f.getName()); //this adds an element to the list.
                            }
                        }
                    }
                }
                resourceSelected = true;
            }
        } else {
            tellError(getString(R.string.Error_Import_IsNotFolder));
        }
        return resourceSelected;
    }

    public void init() {
        myLog("init() - **" + type + "**");
        final DocumentFile[] pickedDir = new DocumentFile[1];
        final boolean[] resourceSelected = {false};

        // TODO First thing check if folder already exists

        switch (type) {
            ///---------------------------------------------
            /// FOLDER
            ///---------------------------------------------
            case "Folder":
                try {
                    pickedDir[0] = DocumentFile.fromTreeUri(this, uri);
                } catch (Exception e) {
                    myLogE("populateArrayListOfTracks " + e.getMessage());
                    break;
                }
                resourceSelected[0] = populateArrayListOfTracks(pickedDir[0],"");
                break;

            ///---------------------------------------------
            /// ZIP FILE
            ///---------------------------------------------
            case "ZIP":

                myLog("Entry case ZipFile");
                myFolder = new FolderAttrib(getApplicationContext(), uri, true,"");
                tellName(myFolder.getsFolderName());

                if (myFolder.isFolderKO()) {
                    myLogE("myFolder.isFolderKO()");
                    String error = getString(R.string.Error_Import_ZipFilePathKO);
                    if (myFolder.isLocatedInDownloadFolder())  error += "\n" + getString(R.string.Error_Import_BetterTryNoDownloadFolder);
                    tellError(error);
                } else {
                    final File[] fileZipFile = {new File(myFolder.getsRealFolderPath())};

                    ////////////////////////////////////////////////////////////////////////////////////////////////////////
                    ////////////////////////////////////////////////////////////////////////////////////////////////////////
                    ////////////////////////////////////////////////////////////////////////////////////////////////////////
                    //// ANDROID 11 (R and up)
                    if (SDK_INT >= Build.VERSION_CODES.R) {
                        myLog("====> Android 11");
                        //String destinationPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/TongueTwister/tt_1A.3gp";

                        // == Make Folder
                        String destinationFolder = getFilesDir().getAbsolutePath() + "/" + FOLDER_UNZIPPED;
                        final File[] folder = {new File(destinationFolder)};
                        folder[0].mkdir();

                        // == Make File
                        String destinationPath = destinationFolder + "/" + myFolder.getsFolderName() + ".zip";
                        File destination = new File(destinationPath);

                        // == Checking memory
                        int file_size = Integer.parseInt(String.valueOf(fileZipFile[0].length()/1024/1024));
                        long availableMegs = fileZipFile[0].getUsableSpace() / 1048576L;
                        myLog("file size : " + file_size + "Mo // " + "available memory : " + availableMegs + " Mo");

                        if (file_size*ZIP_SIZE_MAX_COEF>availableMegs) {
                            tellError(getResources().getString(R.string.Error_Import_NotEnoughMemory_line1) + "\n"
                                    + getResources().getString(R.string.Error_Import_NotEnoughMemory_line2) + availableMegs + "Mo" + "\n"
                                    + getResources().getString(R.string.Error_Import_NotEnoughMemory_line3) + file_size + "Mo" + "\n"
                                    + getResources().getString(R.string.Error_Import_NotEnoughMemory_line4_1) + ZIP_SIZE_MAX_COEF + getResources().getString(R.string.Error_Import_NotEnoughMemory_line4_2) + "\n"
                                    + "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line5)
                            );
                            return;
                        }

                        ////////////////////////////////////////////////////////////////////////////////////////
                        // put in thread or Activity will freeze
                        ////////////////////////////////////////////////////////////////////////////////////////
                        Thread one;
                        one = new Thread() {
                            @Override
                            public void run() {
                                ContentResolver resolver = getContentResolver();
                                InputStream is = null;
                                tellProgress(PROGRESS_ZIP_START_COPY,getResources().getString(R.string.Import_Progress_copying_zip_file)
                                        + "\n"
                                        + "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line3) + file_size + "Mo"
                                        + "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line2) + availableMegs + "Mo"
                                );
                                int nbBuffCopied = 0;
                                // copy of Zip file
                                try {
                                    is = resolver.openInputStream(uri);
                                    myLog("okay stream in");

                                    try {
                                        OutputStream out = new FileOutputStream(destination);
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
                                                    int nbMoCopied = nbBuffCopied*PROGRESS_ZIP_BUFFER_COPY/1024/1024;
                                                    double progressValue = (double) nbMoCopied / (file_size) * 100;
                                                    tellProgress( PROGRESS_ZIP_START_COPY + (int)progressValue*(PROGRESS_ZIP_END_COPY-PROGRESS_ZIP_START_COPY)/100,
                                                            getResources().getString(R.string.Import_Progress_copying_zip_file)
                                                                    + "\n"
                                                                    + "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line3) + nbMoCopied + "Mo/" + file_size + "Mo"
                                                                    + "\n" + getResources().getString(R.string.Error_Import_NotEnoughMemory_line2) + availableMegs + "Mo"
                                                    );
                                                }

                                            }
                                            myLog("okay stream write");
                                        } catch (Exception e) {
                                            myLogE("1 Copy of ZIP file from External Dir to Internal Dir failed.  -  " + e.getMessage());
                                            e.printStackTrace();
                                        } finally {
                                            out.close();
                                        }
                                    } catch (Exception e) {
                                        myLogE("2 Copy of ZIP file from External Dir to Internal Dir failed.  -  " + e.getMessage());
                                        e.printStackTrace();
                                    } finally {
                                        is.close();
                                    }
                                } catch (Exception e) {
                                    myLogE("ca chie a la lecture");
                                    myLogE(e.getMessage());
                                }

                                myLog("file has been copied from " + uri.toString() + " to " + destination);



                                fileZipFile[0] = new File(destination.getAbsolutePath());
                                folder[0] = new File(stripExtension(fileZipFile[0].getAbsolutePath().replace(" ","_")));

                                ////////////////////////////////////////////////////////////////////////////////
                                /// unzipping....
                                ////////////////////////////////////////////////////////////////////////////////
                                //tellProgress(40,getResources().getString(R.string.Import_Progress_unzipping_file));
                                //unzip(fileZipFile[0], folder[0]);

                                try {
                                    File targetDirectory = folder[0];
                                    File zipFile = fileZipFile[0];
                                    ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)));
                                    myLog("unzipping in : " + targetDirectory);
                                    myLog("unzipping in : " + targetDirectory.getName());

                                    // check number of file in zip
                                    int nbZip;
                                    try {
                                        ZipFile zf= new ZipFile(destination.getAbsolutePath());
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

                                            if (ze.getName().equals(targetDirectory.getName()+"/")) {
                                                //bypass if zip contains only folder with same name at first level
                                                targetDirectory = new File(targetDirectory.getParent());
                                                myLog("unzipping : bypassing first directory");

                                            } else {
                                                numCurZip = numCurZip + 1;
                                                tellProgress(PROGRESS_ZIP_START_UNZIP + numCurZip/nbZip*(PROGRESS_ZIP_END_UNZIP-PROGRESS_ZIP_START_UNZIP) ,
                                                        getResources().getString(R.string.Import_Progress_unzipping_file)
                                                                + "\n" + "\n" + numCurZip + "/" + nbZip + " : " + ze.getName());

                                                File file = new File(targetDirectory, ze.getName());
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
                                        }
                                    } finally {
                                        zis.close();
                                    }
                                    ////////////////////////////////////////////////////////////////////////////////
                                    ////////////////////////////////////////////////////////////////////////////////

                                } catch (Exception e) {
                                    tellError(getResources().getString(R.string.Error_Import_UnableToUnzip_line1) + " : " + e.getMessage()
                                    + "\n" + "\n" + getResources().getString(R.string.Error_Import_UnableToUnzip_line2));
                                    return;
                                } finally {
                                    fileZipFile[0].delete();
                                    myLog("unzip done in folder, zip file deleted");
                                }
                                myLog("file has been unzipped");

                                try {
                                    pickedDir[0] = DocumentFile.fromFile(folder[0]);
                                } catch (Exception e) {
                                    myLogE("Error DocumentFile.fromFile " + e.getMessage());
                                    //break;
                                }
                                resourceSelected[0] = populateArrayListOfTracks(pickedDir[0], folder[0].getName());

                                if (resourceSelected[0]) go1();
                                ////////////////////////////////////////////////////////////////////////////////////////
                                ////////////////////////////////////////////////////////////////////////////////////////
                            }
                        };
                        one.start();

                        return;

                    } else {
                        myLog("===>  Android 10 or less");
                    }
                    ////////////////////////////////////////////////////////////////////////////////////////////////////////
                    ////////////////////////////////////////////////////////////////////////////////////////////////////////
                    ////////////////////////////////////////////////////////////////////////////////////////////////////////

                    // ancien code, avant Android 11, gestion de la lecture de fichiers ZIP...
                    zipFile = null;
                    try {
                        zipFile = new ZipFile(fileZipFile[0]);
                    } catch (Exception e) {
                        myLogE("new ZipFile() KO");
                        tellError(getString(R.string.Error_Import_ParsingZipFile2) + "\n" + getString(R.string.Error_Import_ParsingZipFile_advice));
                        //tellError(getString(R.string.Error_Import_ParsingZipFile) + "  --  " + e.getMessage());
                        e.printStackTrace();
                        break;
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
                                if (getMimeType(s).equals("audio/mpeg")) {
                                    myLog(s);
                                    audioFileArrayList.add(s); //this adds an element to the list.
                                }
                            }
                        }
                        Collections.sort(audioFileArrayList);

                        resourceSelected[0] = true;
                        break;

                    } catch (Exception e) {
                        tellError(getString(R.string.Error_Import_ParsingZipFile2) + "\n" + getString(R.string.Error_Import_ParsingZipFile_advice));
                        e.printStackTrace();
                    }
                }
            default:
                myLogE("Incorrect type : **" + type + "**");
                //tellEnd("ko");
        }

        if (resourceSelected[0]) go1();
    }


    private void go1() {
        myLog("go1");
        //myResource = new Resource(myFolder);
        goFolder();
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
                myLog("ok on continue");
                tellProgress(5,"Check Folder not already imported");
                saveFolder();
            }
        }, throwable -> {
            tellError("ERROR checkIfFolderAlreadyExist : " + throwable.getMessage());
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
                        myLog("Folder Saved - checking files");
                        tellProgress(8,"Folder Saved - checking files");
                        saveFiles();
                    }
                }, throwable -> {
                    myLogE("create Folder : " + throwable.getMessage());
                    tellError("create Folder : " + throwable.getMessage());
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
                //ZIP
                if (myFolder.isZipFolder()) {
                    for (String s : audioFileArrayList) {
                        i++;
                        progress = (int) i * 100 / audioFileArrayList.size();
                        txtProgress = progress + "% - " + getString(R.string.Add_resource_reading_file) + " n°" + i + "/" + audioFileArrayList.size() + "\n" + getFileNameFromPath(s);
                        myLog("Call save " + s);
                        saveFile(s, InsertedFolderId[0], progress, txtProgress);

                        tellProgress(progress,txtProgress);
                    }
                    //FOLDER
                } else {
                    for (DocumentFile f :myZikFileList) {
                        if (f.getType() != null) {
                            if (f.getType().equals("audio/mpeg")) {
                                i++;
                                progress = (int) i * 100 / audioFileArrayList.size();
                                txtProgress = progress + "% - scanning file n°" + i + "/" + audioFileArrayList.size() + "\n" + f.getName();
                                myLog("saving file " + f.getName());
                                saveFile(f.getName(), InsertedFolderId[0], progress, txtProgress);

                                tellProgress(progress,txtProgress);
                            }
                        }
                    }
                }
            }
        };
        one.start();
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
                                    myLog("All files have been saved ");
                                    updateFolderDuration();
                                }
                            } else {
                                tellError("error saving ZikFile in DB");
                            }
                        }, throwable -> {
                            tellError("Saving File " + sZikFileName + " : " + throwable.getMessage());
                        }
                );
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
                    tellError("ERROR updateFolderDuration : " + throwable.getMessage());
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
                mediaMetadataRetriever.setDataSource(zePath);
                duration = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            } else {
                tellError("error getting duration of media, file does not exist in path : " + zePath);
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
                myLog("broadcast progress sent " + val + " - " + txt);
         //   }
        //}, 0);
    }

    private void tellEnd() {
        Intent intent = new Intent(NOTIFICATION_ADDRESOURCE_END);
        intent.putExtra("ok",true);
        sendBroadcast(intent);
        myLog("broadcast end sent");
        stopSelf();
    }

    private void tellEnd(String KO_message) { // my first overload... ^^
        Intent intent = new Intent(NOTIFICATION_ADDRESOURCE_END);
        intent.putExtra("message",KO_message);
        sendBroadcast(intent);
        myLog("broadcast end sent");
        stopSelf();
    }

    private void tellError(String txt) {
        Intent intent = new Intent(NOTIFICATION_ADDRESOURCE_ERROR);
        intent.putExtra("message",txt);
        sendBroadcast(intent);
        myLogE("broadcast error sent :" + txt);
    }

    private void tellName(String txt) {
        Intent intent = new Intent(NOTIFICATION_ADDRESOURCE_NAME);
        intent.putExtra("name",txt);
        sendBroadcast(intent);
        myLog("broadcast name sent :" + txt);
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
