package com.driot.bookplayer.activities;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.sqlite.db.SimpleSQLiteQuery;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.DatabaseClient;
import com.driot.bookplayer.db.Folder;
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

import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Tonio.getMimeType;
import static com.driot.bookplayer.utils.Utils.animateView;
import static com.driot.bookplayer.utils.Utils.copyStream;
import static com.driot.bookplayer.utils.Utils.retrieveListing;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 08/11/20
 */
public class GetResourceActivity extends Activity {

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
    private String sAddedFolderUri;
    private String sAddedFolderHash;
    private String sAddedFolderName;
    private String sAddedFolderPath;

    private boolean isZipFolder;
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


        bOpenZipFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("application/zip");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, OPEN_ZIP_FILE_REQUEST_CODE);
            }
        });
        bOpenFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                startActivityForResult(intent, OPEN_FOLDER_REQUEST_CODE);
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
        if (resultCode == RESULT_OK && requestCode == OPEN_FOLDER_REQUEST_CODE) {
            isZipFolder = false;
            Uri treeUri = data.getData();
            //File pickedDir = new File(treeUri.getPath());
            pickedDir = DocumentFile.fromTreeUri(this, treeUri);

            // Si c'est pas un dossier, on prend le dossier parent...
            if (!pickedDir.isDirectory()) {
                pickedDir = DocumentFile.fromTreeUri(this, treeUri).getParentFile();
            }

            BuildFolderAttributesFromUri(pickedDir.getUri());

            // On vérifie qu'on a pas deja le loustic
            if (pickedDir.isDirectory()) {
                checkIfAddedFolderExist();
            } else {
                Log.d("toto","Ce n'est pas un dossier");
                Toast.makeText(getApplicationContext(), getString(R.string.Error_MainActivity_IsNotFolder), Toast.LENGTH_SHORT).show();
            }
        } else if (resultCode == RESULT_OK && requestCode == OPEN_ZIP_FILE_REQUEST_CODE) {
            isZipFolder = true;
            //Toast.makeText(getApplicationContext(), "Pas encore disponible !", Toast.LENGTH_SHORT).show();

            Uri uri = data.getData();
            BuildFolderAttributesFromUri(uri);

            // Zip File Stuff
            zipFilePath = uri.getPath().replace(":","/").replace("document","storage");

            File fileZipFile = new File(zipFilePath);
            if (fileZipFile.exists()) { Log.d("toto","ok zip file found : " + zipFilePath);} else {Log.d("toto","KO zip file not found : " + zipFilePath);}

            zipFile = null;
            try {
                zipFile = new ZipFile(zipFilePath);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.d("toto","ZipFile instancied ok");
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
            }

            checkIfFolderAlreadyExist();

            Log.d("toto","--------------------------");

        } else if (resultCode == RESULT_OK && requestCode == DOWNLOAD_BOOK_REQUEST_CODE) {
            Log.d("toto","search done");
        }
    }

    private void BuildFolderAttributesFromUri(Uri uri) {
        // on construit quelques attributs
        sAddedFolderUri = uri.toString();
        sAddedFolderHash = Integer.toString(uri.hashCode());
        // nom par défaut = les deux derniers folders :
        // ex  : "S3 - Finances publiques/Audios"
        sAddedFolderPath = uri.getLastPathSegment();
        String str = sAddedFolderPath.replace(":","/");
        int pos1 = str.lastIndexOf("/");
        if (isZipFolder) {
            sAddedFolderName = str.substring(pos1+1).replace(".zip","").replace(".zip","").replace("_"," ");
        } else {
            Log.d("toto",str);
            if (pos1>-1) {
                int pos2 =  str.substring(0,pos1).lastIndexOf("/",pos1);
                if (pos2>-1) {
                    sAddedFolderName = str.substring(pos2+1);
                } else {
                    sAddedFolderName = str.substring(pos1+1);
                }
            }
        }
    }

    private void checkIfFolderAlreadyExist() {
        Observable.fromCallable(() -> {
            boolean bcheckIfFolderExist = false;
            long lcheckIfFolderExist = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .FolderDao()
                    .folderAlreadyExist(sAddedFolderUri,sAddedFolderHash);
            if (lcheckIfFolderExist>0) { bcheckIfFolderExist = true;}
            return bcheckIfFolderExist;
        })
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((result) -> {
                    if (result) {
                        Log.d("toto","Zip deja importé");
                        Toast.makeText(getApplicationContext(), getString(R.string.Error_MainActivity_FolderAlreadyImported), Toast.LENGTH_SHORT).show();
                    } else {
                        Log.d("toto","ok on continue");
                        saveFolder();
                    }

                });
    }



















    private void checkIfAddedFolderExist() {

        class CheckIfAddedFolderExist extends AsyncTask<Void, Void, Boolean> {

            @Override
            protected Boolean doInBackground(Void... voids) {
                boolean bcheckIfFolderExist = false;
                long lcheckIfFolderExist = DatabaseClient
                        .getInstance(getApplicationContext())
                        .getAppDatabase()
                        .FolderDao()
                        .folderAlreadyExist(sAddedFolderUri,sAddedFolderHash);
                if (lcheckIfFolderExist>0) { bcheckIfFolderExist = true;}
                return bcheckIfFolderExist;
            }

            @Override
            protected void onPostExecute(Boolean bb) {
                super.onPostExecute(bb);
                if (bb) {
                    myZikFileList = pickedDir.listFiles();
                    Log.d("toto","Dossier deja importé");
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
                        Log.d("toto","Pas de medias dans ce dossier");
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
                folder.setName(sAddedFolderName);
                folder.setPath(sAddedFolderPath);
                folder.setUri(sAddedFolderUri);
                folder.setHash(sAddedFolderHash);
                folder.setPercentdone(sPercent);
                folder.setFirstaccess(sFirstAccess);
                folder.setLastaccess(sLastAccess);
                folder.setLastaccessTime(sLastAccessTime);
                folder.setFinished(false);
                folder.setIszipfile(isZipFolder);

                //adding to database
                InsertedFolderId[0] = (int) DatabaseClient.getInstance(getApplicationContext()).getAppDatabase()
                        .FolderDao()
                        .insert(folder);
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                if (isZipFolder) {
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
        Log.d("toto","saving ZipFiles");
        Observable.fromCallable(() -> {
            for (String s : audioFileArrayList) {
                String fileName = s.substring(s.lastIndexOf("/")+1);
                Log.d("toto","Call save " + fileName);
                saveFile(fileName, InsertedFolderId[0]);
            }
            return true;
        })
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((result) -> {
                    Log.d("toto","Zip file have been saved ");
                    updateFolderDuration();
                });

    }

    private void saveFiles() {

        class SaveFiles extends AsyncTask<Void, Void, Void> {

            @Override
            protected Void doInBackground(Void... voids) {
                for (DocumentFile file :myZikFileList) {
                    if (file.getType().equals("audio/mpeg")) {
                        Log.d("toto","saving file " + file.getName());
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
                zikFile.setFolderName(sAddedFolderName);
                zikFile.setPercentdone(dPercent);
                zikFile.setPosition(iPosition);

                String sFolderPath = "/storage/" + sAddedFolderPath.replace(":","/");
                String sFileFullPath = sFolderPath + File.separator + sZikFileName;
                zikFile.setPath(sFolderPath);
                //Log.d("toto","File Full path : " + sFileFullPath);
                File f = new File(sFileFullPath);
                //File file = new File(Uri.parse("/sdcard/lala.txt").getPath());
                zikFile.setSize(f.length());
                try {
                    zikFile.setDuration(getMediaDurationFromPath(sFileFullPath));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                zikFile.setIszipfile(isZipFolder);

                //adding to database
                DatabaseClient.getInstance(getApplicationContext()).getAppDatabase()
                        .ZikFileDao()
                        .insert(zikFile);
                //Log.d("toto","File Added " + zikFile.getName());
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
                Log.d("toto","query has been run : " + strSQL);

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
        if (isZipFolder) {
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
                //Log.d("toto", "ca marche " + duration + " : " + fileName);

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
            mediaMetadataRetriever.setDataSource(zePath);
            duration = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        }
        return duration;
    }



}
