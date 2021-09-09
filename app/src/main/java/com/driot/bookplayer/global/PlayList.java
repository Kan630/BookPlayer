package com.driot.bookplayer.global;

import android.content.SharedPreferences;

import com.driot.bookplayer.db.ZikFile;

import java.util.List;

import static com.driot.bookplayer.activities.PlayActivity.SHARED_PREFERENCE_SPEED;
import static com.driot.tonylib.KanLogger.myLog;
import static com.driot.tonylib.KanLogger.myLogE;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 05/09/21
 */
public class PlayList {

    private static List<ZikFile> zikFilesList;
    private static int numZikFile; // old numSong

    private static int idFolder;
    private static int idZikFile;
    private static double positionZikFile;

    public static void setZikFilesList(List<ZikFile> zikFilesList) {
        PlayList.zikFilesList = zikFilesList;
        myLog("PlayList.SetZikFileList() .. size = " + zikFilesList.size());
    }

    public static List<ZikFile> getZikFilesList() {
        if (zikFilesList==null) {
            myLogE("zikFilesList==null");
            return null;
        } else {
            return zikFilesList;
        }
    }

    public static int getNumZikFile() {
        return numZikFile;
    }

    public static void setNumZikFile(int numZikFile) {
        PlayList.numZikFile = numZikFile;
        myLog("PlayList.SetNumZikFile() - n°" + numZikFile);
    }



    public static ZikFile getZikFile() {
        return getZikFilesList().get(numZikFile);
    }
/*
    public static void setZikFile(ZikFile zikFile) {
        // set ZikFileList and numZikFile
        if (!(getZikFile() == zikFile)) {

        }
        return getZikFilesList().get(numZikFile);
    }

 */



    //public PlayList(ZikFile)
/*

    private void saveToPref(double speed) {
        try {
            SharedPreferences.Editor editor = this.getSharedPreferences(SHARED_PREFERENCE_SPEED, MODE_PRIVATE).edit();
            editor.
            editor.putString(String.valueOf(getCurrentZikFile().getIdFolder()),Double.toString(speed)).apply();
        } catch (Exception e) {
            myLogE("AudioService : error saving speed in prefs");
            myLogE(e.getMessage());
        }
    }

    private double getSpeedFromPref() {
        try {
            SharedPreferences prefs = this.getSharedPreferences(SHARED_PREFERENCE_SPEED, MODE_PRIVATE);
            return Double.parseDouble(prefs.getString(String.valueOf(getCurrentZikFile().getIdFolder()), "1.0"));
        } catch (Exception e) {
            myLogE("AudioService : error getting speed from prefs");
            myLogE(e.getMessage());
            return 1.0;
        }
    }
 */

}
