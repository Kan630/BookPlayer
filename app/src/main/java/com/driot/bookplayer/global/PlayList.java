package com.driot.bookplayer.global;

import com.driot.bookplayer.db.ZikFile;
import com.driot.tonylib.KanLogger;

import java.util.List;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 05/09/21
 */
public class PlayList {

    private static List<ZikFile> zikFilesList;
    private static int numZikFile=-1; // old numSong

    public static void setZikFilesList(List<ZikFile> zikFilesList) {
        PlayList.zikFilesList = zikFilesList;
        myLog("SetZikFileList() .. size = " + zikFilesList.size());
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
        myLog("SetNumZikFile() - n°" + numZikFile);
    }

    public static ZikFile getZikFile() {
        if (numZikFile>=0) {
            if (!(getZikFilesList()==null)) {
                try {
                    return getZikFilesList().get(numZikFile);
                } catch (Exception e) {
                    myLogE("getZikFile() ERROR - try-catch -- " + e.getMessage());
                    e.printStackTrace();
                    return null;
                }
            } else {
                myLogE("getZikFile() ERROR - zikFilesList is null");
                return null;
            }
        } else {
            myLogE("getZikFile() ERROR - numZikFile = " + numZikFile);
            return null;
        }
    }

    //--- LOG --------------------------
    private static void myLog(String str) { KanLogger.myLog("PlayList", str); }
    private static void myLogE(String str) { KanLogger.myLogE("PlayList", str); }
}
