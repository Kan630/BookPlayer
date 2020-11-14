package com.driot.bookplayer.db;

import android.net.Uri;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 14/11/20
 */
public class FolderAttrib {

    private final String sFolderUri;
    private final String sFolderHash;
    private final String sFolderPath;
    private final String sRealFolderPath;
    private final boolean isZipFolder;
    private String sFolderName;

    public FolderAttrib(Uri uri, boolean isZipFolder) {

        this.isZipFolder = isZipFolder;

        sFolderUri = uri.toString();

        sFolderHash = Integer.toString(uri.hashCode());

        sFolderPath = uri.getLastPathSegment();

        sRealFolderPath = "/storage/" + sFolderPath.replace(":","/");

        // nom par défaut = les deux derniers folders :
        // ex  : "S3 - Finances publiques/Audios"
        String str = sFolderPath.replace(":", "/");
        int pos1 = str.lastIndexOf("/");
        if (isZipFolder) {
            sFolderName = str.substring(pos1 + 1).replace(".zip", "").replace(".zip", "").replace("_", " ");
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

    @Override
    public String toString() {
        return "FolderAttrib{" +
                "sFolderUri='" + sFolderUri + '\'' +
                ", sFolderHash='" + sFolderHash + '\'' +
                ", sFolderPath='" + sFolderPath + '\'' +
                ", sFolderName='" + sFolderName + '\'' +
                ", isZipFolder=" + isZipFolder +
                '}';
    }
}
