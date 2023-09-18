package com.driot.bookplayer.db;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Toast;

import com.driot.bookplayer.R;

import java.io.File;
import java.util.List;

import static com.driot.bookplayer.utils.FileHelper.getRealPathFromURI;
import static com.driot.bookplayer.utils.Tonio.FormatNameForDisplay;
import static com.driot.bookplayer.utils.Tonio.stripFileName;
import static com.driot.tonylib.KanLogger.myLogE;


/**
 * created by Antoine Driot -- antoine.driot.com -- on 14/11/20
 */
public class FolderAttrib {

    private final Uri uri;
    private final boolean isZipFolder;
    private final boolean isSingleFile;
    private boolean isLocatedInDownloadFolder = false;
    private final Context mCtx;

    private boolean FolderKO;

    private final String sFolderUri;
    private final String sFolderHash;
    private String sFolderPath;
    private String sFolderName;
    private final String sFolderName_withUnderscore;

    private final String sRealPathFromUriNew;

    private String sRealFolderPath;

    //public FolderAttrib(Context context, Uri uri, boolean isZipFolder, String forceName) {
    public FolderAttrib(Context context, Uri uri, boolean isZipFolder, boolean isSingleFile) {

        this.uri = uri;
        this.isZipFolder = isZipFolder;
        this.isSingleFile = isSingleFile;
        this.mCtx = context;

        sFolderUri = uri.toString();

        sFolderHash = Integer.toString(uri.hashCode());

        sFolderPath = uri.getLastPathSegment();

        sRealFolderPath="";

        isLocatedInDownloadFolder = false;

        sRealPathFromUriNew = getRealPathFromURI(context,uri);
        myLog("new method to find path : [" + sRealPathFromUriNew + "]");

        myLog(PrintManyPaths());

        // from DOWNLOAD
        if (uri.getAuthority().equals("com.android.providers.downloads.documents")) {
            myLog("location : Download Folder");
            isLocatedInDownloadFolder=true;
            sFolderPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
            if (isZipFolder) {
                String sRealFolderName = getFileName(context,uri);
                sRealFolderPath = sFolderPath + "/" + sRealFolderName;
                sFolderName = FormatNameForDisplay(sRealFolderName);
            } else if (isSingleFile) {
                //String sRealFolderName = getFileName(context,uri);
                sRealFolderPath = sFolderPath;
                sFolderName = FormatNameForDisplay(getFileName(context,uri));
            } else {
                //sRealFolderPath = uri.getLastPathSegment().replace("raw:","");
                String sRealFolderName = getFileName(context,uri);
                sRealFolderPath = sFolderPath + "/" + sRealFolderName;
                sFolderName = FormatNameForDisplay(sRealFolderPath.substring(sRealFolderPath.lastIndexOf("/")+1));
            }

            // from MAIN MEMORY
        } else if (uri.getLastPathSegment().startsWith("primary")) {
            myLog("location : main memory");
            sRealFolderPath = uri.getLastPathSegment()
                    .replace("primary:","/storage/emulated/0/");
            if (isSingleFile) { sRealFolderPath = stripFileName(sRealFolderPath); }

            // from SD CARD
        } else {
            myLog("location : else - sdcard");
            sRealFolderPath = uri.getPath()
                    .replace("document", "storage")
                    .replace("tree", "storage")
                    .replace(":", "/");
            if (isSingleFile) { sRealFolderPath = stripFileName(sRealFolderPath); }
        }

        // controle de l'exitence du fullPath
        File f = new File(sRealFolderPath);
        if (!f.exists())  {
            FolderKO = true;
            myLogE("====== Path cannot be retrieved       ....  error with: --new File("+sRealFolderPath+")--");
        }

        if (isZipFolder) {
            if (!f.isFile())  {
                FolderKO = true;
                myLogE("====== Is not File");
            }
        } else if (!(isSingleFile)) {
            if (!f.isDirectory()) {
                FolderKO = true;
                myLogE("====== Is not Folder");
            }
        }

        if (!isLocatedInDownloadFolder) {
            // nom par défaut = les deux derniers folders :
            // ex  : "S3 - Finances publiques/Audios"
            String str = sFolderPath.replace(":", "/");
            int pos1 = str.lastIndexOf("/");
            if (isZipFolder) {
                sFolderName = FormatNameForDisplay(str.substring(pos1 + 1));
            } else {
                if (pos1 > -1) {
                    int pos2 = str.substring(0, pos1).lastIndexOf("/", pos1);
                    if (pos2 > -1) {
                        sFolderName = FormatNameForDisplay(str.substring(pos2 + 1));
                    } else {
                        sFolderName = FormatNameForDisplay(str.substring(pos1 + 1));
                    }
                } else {
                    // especially when foldername is just a string without slash (Android 11 zip local copy)
                    sFolderName = FormatNameForDisplay(str);
                }
            }
            if (sFolderName.startsWith("Download/")) { sFolderName = sFolderName.substring(9); }
        }
/*
        // TODO : Allow to rename folder ??
        if (forceName.length()>0) {
            sFolderName = forceName;
        }
*/
        sFolderName_withUnderscore = sFolderName.replace(" ", "_");


        myLog("..." + "\n" +
                this + "\n" +
                "...");
    }

    public String getsFolderUri() {
        return sFolderUri;
    }

    public String getsFolderHash() {
        return sFolderHash;
    }

    public String getsFolderName() {
        return sFolderName;
    }

    public String getsFolderName_withUnderscore() {
        return sFolderName_withUnderscore;
    }

    public String getsFolderPath() {
        return sFolderPath;
    }

    public String getsRealFolderPath() {
        return sRealFolderPath;
    }

    public boolean isSingleFile() {
        return isSingleFile;
    }

    public boolean isZipFolder() {
        return isZipFolder;
    }

    public boolean isFolderKO() {
        return FolderKO;
    }

    public boolean isLocatedInDownloadFolder() {
        return isLocatedInDownloadFolder;
    }

    @Override
    public String toString() {
        return "FolderAttrib{" + "\n" +
                "uri                 ='" + uri + '\'' + "\n" +
                "uri.getPath         ='" + uri.getPath() + '\'' + "\n" +
                "uri.getLastPathSeg  =" + uri.getLastPathSegment() + "\n" +
                "uri.getAuthority    =" + uri.getAuthority() + "\n" +
                "uri.getFragment     =" + uri.getFragment() + "\n" +
                "sRealPathFromUriNew ='" + sRealPathFromUriNew + '\'' + "\n" +
                "sFolderUri          ='" + sFolderUri + '\'' + "\n" +
                "sFolderHash         ='" + sFolderHash + '\'' + "\n" +
                "sFolderPath         ='" + sFolderPath + '\'' + "\n" +
                "sRealFolderPath     ='" + sRealFolderPath + '\'' + "\n" +
                "isZipFolder         =" + isZipFolder + "\n" +
                "isSingleFile        =" + isSingleFile + "\n" +
                "isFolderKO          =" + FolderKO + "\n" +
                "sFolderName         ='" + sFolderName + '\'' + "\n" +
                "sFolderName_withUn. ='" + sFolderName_withUnderscore + '\'' + "\n" +
                '}';
    }

    public String PrintManyPaths() {
        List<String> segments = uri.getPathSegments();
        String ss =
                "..." + "\n" +
                        "uri                : " + uri + "\n" +
                        "uri.getPath        : " + uri.getPath() + "\n" +
                        "uri.getEncodedPath : " + uri.getEncodedPath() + "\n" +
                        "uri.getLastPathSeg : " + uri.getLastPathSegment() + "\n" +
                        "uri.getAuthority   : " + uri.getAuthority() + "\n" +
                        "uri.getHost        : " + uri.getHost() + "\n" +
                        "uri.getLastPathSeg : " + uri.getPathSegments() + "\n" +
                "";
        for (int i = 0; i < segments.size() - 1; i++) {
            ss += "uri.getPathSegment("+ i +") : " + segments.get(i) + "\n";
        }

        return ss;
    }


    public String getFileName(Context context, Uri uri) {
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
            if (result.endsWith("/")) {
                result = result.substring(0,result.length()-1);
            }
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        if (result == null) {myLogE("FolderAttrib.getFileName -- " + uri.getPath());}
        else {myLog("FolderAttrib.getFileName : [" + result + "]");}
        return result;
    }

    private void myToast(String str) {
        myLog(str);
        Toast.makeText(mCtx,str,Toast.LENGTH_SHORT).show();
    }

    protected void myLog(String str) {
        Log.d("toto FolderAttrib -- ", str);
        System.out.println(str);
    }


}
