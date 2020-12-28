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

import static com.driot.bookplayer.utils.Tonio.FormatNameForDisplay;


/**
 * created by Antoine Driot -- antoine.driot.com -- on 14/11/20
 */
public class FolderAttrib {

    private final Uri uri;
    private final boolean isZipFolder;
    private boolean isLocatedInDownloadFolder = false;
    private Context mCtx;

    private boolean FolderKO;

    private final String sFolderUri;
    private final String sFolderHash;
    private String sFolderPath;
    private String sFolderName;

    private String sRealFolderPath;

    private boolean fromDownloadFolder;

    public FolderAttrib(Context context, Uri uri, boolean isZipFolder) {

        this.uri = uri;
        this.isZipFolder = isZipFolder;
        this.mCtx = context;

        sFolderUri = uri.toString();

        sFolderHash = Integer.toString(uri.hashCode());

        sFolderPath = uri.getLastPathSegment();

        sRealFolderPath="";

        fromDownloadFolder=false;

            // from DOWNLOAD
        if (uri.getAuthority().equals("com.android.providers.downloads.documents")) {
            isLocatedInDownloadFolder=true;
            sFolderPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
            if (isZipFolder) {
                String sRealFolderName = getFileName(context,uri);
                sRealFolderPath = sFolderPath + "/" + sRealFolderName;
                sFolderName = FormatNameForDisplay(sRealFolderName);
            } else {
                sRealFolderPath = uri.getLastPathSegment().replace("raw:","");
                sFolderName = FormatNameForDisplay(sRealFolderPath.substring(sRealFolderPath.lastIndexOf("/")+1));
            }
            fromDownloadFolder=true;

            // from MAIN MEMORY
        } else if (uri.getLastPathSegment().startsWith("primary")) {
            sRealFolderPath = uri.getLastPathSegment()
                    .replace("primary:","/storage/emulated/0/");

            // from SD CARD
        } else {
            sRealFolderPath = uri.getPath()
                    .replace("document", "storage")
                    .replace("tree", "storage")
                    .replace(":", "/");
        }

        // controle de l'exitence du fullPath
        File f = new File(sRealFolderPath);
        if (!f.exists())  {
            FolderKO = true;
            myLog("====== File not exists");
        }

        if (isZipFolder) {
            if (!f.isFile())  {
                FolderKO = true;
                myLog("====== Is not File");
            }
        } else {
            if (!f.isDirectory()) {
                FolderKO = true;
                myLog("====== Is not Folder");
            }
        }

        if (!fromDownloadFolder) {
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
                        sFolderName = str.substring(pos2 + 1);
                    } else {
                        sFolderName = str.substring(pos1 + 1);
                    }
                }
            }
        }

        myLog("..." + "\n" +
                this.toString() + "\n" +
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

    public String getsFolderPath() {
        return sFolderPath;
    }

    public String getsRealFolderPath() {
        return sRealFolderPath;
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
                "sFolderUri          ='" + sFolderUri + '\'' + "\n" +
                "sFolderHash         ='" + sFolderHash + '\'' + "\n" +
                "sFolderPath         ='" + sFolderPath + '\'' + "\n" +
                "sRealFolderPath     ='" + sRealFolderPath + '\'' + "\n" +
                "isZipFolder         =" + isZipFolder + "\n" +
                "isFolderKO          =" + FolderKO + "\n" +
                "sFolderName         ='" + sFolderName + '\'' + "\n" +
                '}';
    }

    public String PrintManyPaths() {
        String ss =
                "..." + "\n" +
                        "uri                : " + uri + "\n" +
                        "uri.getPath        : " + uri.getPath() + "\n" +
                        "uri.getEncodedPath : " + uri.getEncodedPath() + "\n" +
                        "uri.getLastPathSeg : " + uri.getLastPathSegment() + "\n" +
                        "uri.getAuthority   : " + uri.getAuthority() + "\n" +
                        "uri.getHost        : " + uri.getHost() + "\n" +
                "";

        return ss;
    }


    public String getFileName(Context context, Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
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
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
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
