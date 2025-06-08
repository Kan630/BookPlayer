package com.driot.bookplayer.db;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.FileUtils;
import com.driot.bookplayer.utils.KanLogger;

import java.io.File;
import java.util.List;

import static com.driot.bookplayer.utils.Tonio.formatNameForDisplay;
import static com.driot.bookplayer.utils.Tonio.getLastFolder;
import static com.driot.bookplayer.utils.Tonio.getSubFolders;
import static com.driot.bookplayer.utils.Tonio.stripFileName;


/**
 * created by Antoine Driot -- antoine.driot.com -- on 14/11/20
 */
public class FolderAttrib {

// constructor args
    private final Context mCtx;
    private final Uri uri;
    private final boolean isSingleFile;
    private final boolean internalCopy;
    private final String zeType;

    // Folder Path and display name
    private String sFolderPath;
    private String sFolderName;

    // some internal stuff
    private boolean FolderKO;


    // Constructor
    public FolderAttrib(Context context, Uri uri, boolean internalCopy, String zeType) { //String forceName ?
        myLog("-----------------------------------------------------"
                + "\nFolderAttrib    (constructor)"
                + "\nUri : [" + uri.toString() + "]"
                + "\nType : " + zeType + "    -    internalCopy : " + internalCopy
                + "\n-----------------------------------------------------");

        this.uri = uri;
        this.mCtx = context;
        this.internalCopy = internalCopy;
        this.zeType = zeType;
        this.isSingleFile = !zeType.equals("Folder");

        //myLog(PrintManyPaths());

        // ******************************************
        // getting FolderPath
        // ******************************************

            // from DOWNLOAD
        if (uri.getAuthority().equals("com.android.providers.downloads.documents")) {
            myLog("location : Download Folder");
            sFolderPath =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath()
                    + getSubFolders("/Download", uri.getPath()) ;
            if (!isSingleFile) {
                sFolderPath = sFolderPath + "/" + getFileName();
            }

            // from MAIN MEMORY
        } else if (uri.getLastPathSegment().startsWith("primary")) {
            myLog("location : main memory");
            sFolderPath = uri.getLastPathSegment()
                    .replace("primary:","/storage/emulated/0/");
            if (isSingleFile) { sFolderPath = stripFileName(sFolderPath); }

            // from MAIN MEMORY old
        } else if (uri.getLastPathSegment().startsWith("com.google.android.apps.docs.storage")) {
            myLog("location : main memory old");
            sFolderPath = uri.getLastPathSegment()
                    .replace("0000-0000:","/storage/0000-0000/");
            if (isSingleFile) { sFolderPath = stripFileName(sFolderPath); }

            // from google drive
        } else if (uri.getAuthority().equals("com.google.android.apps.docs.storage")) {
            myLog("location : Google Drive");
            sFolderPath = "***"; // cannot be null...

            // from SD CARD
        } else {
            myLog("location : else - sdcard");
            sFolderPath = uri.getPath();
            if (sFolderPath != null) {
                sFolderPath = sFolderPath.replace("document", "storage")
                        .replace("tree", "storage")
                        .replace(":", "/");
            }
            if (isSingleFile) { sFolderPath = stripFileName(sFolderPath); }
        }

        File f = null;

        if (sFolderPath != null) {
            f = new File(sFolderPath);
        }

        if (f == null || !f.exists()) {
            myLog("Shit, still nothing..., let's try the new function.... FileUtils.getRealPathFromURI");
            //let's try the new function....
            String realPath = FileUtils.getRealPathFromURI(mCtx, uri);
            sFolderPath = stripFileName(realPath);
            if (sFolderPath != null) {
                f = new File(sFolderPath);
            }
        }

        if (f == null) {
            myLogE("f == null");
        }


        // controle de l'existence du fullPath
        if (f == null || !f.exists()) {
            FolderKO = true;
            myLogE("====== Path cannot be retrieved       ....  error with: --new File("+sFolderPath+")--");
        }

        if (!(isSingleFile)) {
            if (f == null || !f.isDirectory()) {
                FolderKO = true;
                myLogE("====== Is not Folder");
            }
        }

        // ******************************************
        // getting Folder Name
        // ******************************************

        if (isSingleFile) {
            if (internalCopy) {
                sFolderName = formatNameForDisplay(getFileName());
            } else {
                sFolderName = formatNameForDisplay(getLastFolder(sFolderPath) + "/" + getFileName());
            }
        } else {
            // nom par défaut = les deux derniers folders :
            // ex  : "S3 - Finances publiques/Audios"
            String str = sFolderPath.replace(":", "/");
            int pos1 = str.lastIndexOf("/");
            if (pos1 > -1) {
                int pos2 = str.substring(0, pos1).lastIndexOf("/", pos1);
                if (pos2 > -1) {
                    sFolderName = formatNameForDisplay(str.substring(pos2 + 1));
                } else {
                    sFolderName = formatNameForDisplay(str.substring(pos1 + 1));
                }
            } else {
                // especially when foldername is just a string without slash (Android 11 zip local copy)
                sFolderName = formatNameForDisplay(str);
            }
        }
        if (sFolderName.startsWith("Download/")) { sFolderName = sFolderName.substring(9); }
        if (sFolderName.startsWith("unzipped/")) { sFolderName = sFolderName.substring(9); }

        if (internalCopy && zeType.equals("Folder")) {
            sFolderName = sFolderName.replace("/"," - "); // Pas de sous dossier en interne
        }

        // display all bunch of values = implicit getString
        myLog("..." + "\n" + this + "\n" + "...");
    }

    public String getFolderName() { return sFolderName; }
    public String getFolderPath() { return sFolderPath; } // Dao
    public void setForceFolderPath(String newPath) {
        myLog("Forcing Folder Path = [" + newPath + "]");
        this.sFolderPath = newPath; // in case of unzipped files...
    }
    public boolean isFolderKO() {
        return FolderKO;
    }
    public boolean isSingleFile() {
        return isSingleFile;
    }
    public Uri getUri() {
        return uri;
    }
    public String getUriString() {
        return uri.toString();
    }
    public boolean isLocatedInDownloadFolder() {
        if (uri.getPath().toLowerCase().contains("/download")) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "FolderAttrib{" + "\n" +
                "Type                ='" + zeType + '\'' + "\n" +
                "CopyFile            ='" + internalCopy + '\'' + "\n" +
                "isSingleFile        ='" + isSingleFile + '\'' + "\n" +
                ".........................." + "\n" +
                "uri                 ='" + uri + '\'' + "\n" +
                "uri.getAuthority    ='" + uri.getAuthority() + '\'' + "\n" +
                "uri.getPath         ='" + uri.getPath() + '\'' + "\n" +
                "uri.getFragment     ='" + uri.getFragment() + '\'' + "\n" +
                "uri.getPathSegments ='" + uri.getPathSegments() + '\'' + "\n" +
                "uri.getLastPathSeg  ='" + uri.getLastPathSegment() + '\'' + "\n" +
                ".........................." + "\n" +
                "sFolderPath         ='" + sFolderPath + '\'' + "\n" +
                "sFolderName         ='" + sFolderName + '\'' + "\n" +
                "isFolderKO          =" + FolderKO + "\n" +
                '}';
    }

    public String PrintManyPaths() {
        List<String> segments = uri.getPathSegments();
        StringBuilder ss =
                new StringBuilder("..." + "\n" +
                        "uri                : " + uri + "\n" +
                        "uri.getPath        : " + uri.getPath() + "\n" +
                        "uri.getEncodedPath : " + uri.getEncodedPath() + "\n" +
                        "uri.getLastPathSeg : " + uri.getLastPathSegment() + "\n" +
                        "uri.getAuthority   : " + uri.getAuthority() + "\n" +
                        "uri.getHost        : " + uri.getHost() + "\n" +
                        "uri.getLastPathSeg : " + uri.getPathSegments() + "\n" +
                        "");
        for (int i = 0; i < segments.size() - 1; i++) {
            ss.append("uri.getPathSegment(").append(i).append(") : ").append(segments.get(i)).append("\n");
        }

        return ss.toString();
    }



    private String getFileName() {
        return getFileName(mCtx);
    }
    public String getFileName(Context context) {
        myLog("getFileName() start");
        int tmp_int;
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    tmp_int = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (tmp_int < 0) {
                        myLogE("cannot get path/name of file with ...   context.getContentResolver().query(uri...");
                    } else {
                        result = cursor.getString(tmp_int);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                myToast(context.getString(R.string.Error_Getting_Ressource_Name_from_Download_Folder));
            } finally {
                if (cursor != null) cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                if (result.endsWith("/")) {
                    result = result.substring(0, result.length() - 1);
                }
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        }
        if (result == null) {
            myLogE("FolderAttrib.getFileName -- " + uri.getPath());
        } else {
            myLog("FolderAttrib.getFileName : [" + result + "]");
        }
        return result;
    }

    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    private void myToast(String str) { KanLogger.myToast(str); }



}
