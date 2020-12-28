package com.driot.bookplayer.utils;

import android.app.Service;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
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
import java.util.Collections;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static com.driot.bookplayer.utils.Tonio.fileExists;
import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Tonio.getFileNameFromPath;
import static com.driot.bookplayer.utils.Tonio.getMimeType;
import static com.driot.bookplayer.utils.Utils.copyStream;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 23/11/20
 */
public class AddResourceService extends Service {

    private final IBinder binder = new AddResourceService.BackgroundBinder();
    static final String TAG = "AddResourceServ.";
    private static final boolean LOG_TRACE = true;

    public static final String NOTIFICATION_ADDRESOURCE_NAME = "NOTIFICATION_ADDRESOURCE_NAME";
    public static final String NOTIFICATION_ADDRESOURCE_PROGRESS = "NOTIFICATION_ADDRESOURCE_PROGRESS";
    public static final String NOTIFICATION_ADDRESOURCE_ERROR = "NOTIFICATION_ADDRESOURCE_ERROR";
    public static final String NOTIFICATION_ADDRESOURCE_END = "NOTIFICATION_ADDRESOURCE_END";

    private boolean ResourceSelected;
    private DocumentFile pickedDir;
    private FolderAttrib myFolder;
    private ZipFile zipFile;
    private ArrayList<String> audioFileArrayList;
    private int[] InsertedFolderId = {0};
    private DocumentFile[] myZikFileList;

    private int nbFileSaved, nbFileToSave;

    @Override
    public void onCreate() {
        super.onCreate();
        myLog("onCreate()");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        myLog("onStartCommand()");
        return START_NOT_STICKY;
    }

    public void init(Uri uri, String type) {
        myLog("init()");
        ResourceSelected = false;
        switch (type) {
            ///---------------------------------------------
            /// FOLDER
            ///---------------------------------------------
            case "Folder":

                pickedDir = DocumentFile.fromTreeUri(this, uri);

                // Si c'est pas un dossier, on prend le dossier parent...
                if (!pickedDir.isDirectory()) {
                    pickedDir = DocumentFile.fromTreeUri(this, uri).getParentFile();
                    myLog("Parent Folder taken in place");
                }

                if (pickedDir != null && pickedDir.isDirectory()) {

                    // constructeur pour mon pti folder
                    myFolder = new FolderAttrib(getApplicationContext(), uri, false);
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
                        ResourceSelected = true;
                    }
                } else {
                    tellError(getString(R.string.Error_Import_IsNotFolder));
                }
                break;
            ///---------------------------------------------
            /// ZIP FILE
            ///---------------------------------------------
            case "ZIP":

                myFolder = new FolderAttrib(getApplicationContext(), uri, true);
                tellName(myFolder.getsFolderName());

                if (myFolder.isFolderKO()) {
                    String error = getString(R.string.Error_Import_ZipFilePathKO);
                    if (myFolder.isLocatedInDownloadFolder())  error += "\n" + getString(R.string.Error_Import_BetterTryNoDownloadFolder);
                    tellError(error);
                } else {
                    boolean OnContinue = true;
                    File fileZipFile = new File(myFolder.getsRealFolderPath());

                    zipFile = null;
                    try {
                        zipFile = new ZipFile(fileZipFile);
                    } catch (Exception e) {
                        tellError(getString(R.string.Error_Import_ParsingZipFile));
                        e.printStackTrace();
                        OnContinue = false;
                    }

                    if (OnContinue) {
                        myLog("ZipFile instancied ok");

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

                            ResourceSelected = true;

                        } catch (Exception e) {
                            tellError(getString(R.string.Error_Import_ParsingZipFile2) + "\n" + getString(R.string.Error_Import_ParsingZipFile_advice));
                            e.printStackTrace();
                        }

                    }
                }
            default:
                myLogE("Incorrect type : " + type);
                //tellEnd("ko");
        }

        if (ResourceSelected) go1();
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
                        tellProgress(8,"Folder Saved - checking files");
                        saveFiles();
                    }
                }, throwable -> {
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
                        txtProgress = progress + "% - reading file n°" + i + "/" + audioFileArrayList.size() + "\n" + getFileNameFromPath(s);
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
                                txtProgress = progress + "% - saving file n°" + i + "/" + audioFileArrayList.size() + "\n" + f.getName();
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
                                tellError("ca chie");
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
                tellError("error getting duration of media, file not exist in path : " + zePath);
            }
        }
        return duration;
    }

    private void tellProgress(int val, String txt) {
        Intent intent = new Intent(NOTIFICATION_ADDRESOURCE_PROGRESS);
        intent.putExtra("progressText",txt);
        intent.putExtra("progress",val);
        sendBroadcast(intent);
        myLog("broadcast progress sent");
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
