package com.driot.bookplayer.global;

import android.content.Context;
import android.content.SharedPreferences;

import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.utils.KanLogger;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 05/09/21
 */
public class PlayList {

    private static Context appContext;

    private static List<ZikFile> zikFilesList;
    private static int numZikFile=-1; // old numSong

    private static final String PREF_PLAYLIST_STORAGE = "playlist_storage";
    private static final String KEY_ZIKFILES = "zikfiles_list";


    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    public static void setZikFilesList(List<ZikFile> zikFilesList, Context c) {
        PlayList.zikFilesList = zikFilesList;
        myLog("SetZikFileList() .. size = " + zikFilesList.size());
        saveToStorage(zikFilesList);
        myLog("SetZikFileList() .. zikFilesList saved in prefs");
    }

    public static List<ZikFile> getZikFilesList() {
        if (zikFilesList==null) {
            myLog("zikFilesList == null    => try loading from prefs");
            zikFilesList = loadFromStorage();
        }
        if (zikFilesList==null) {
            myLogE("zikFilesList == null .... still !");
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

    private static void saveToStorage(List<ZikFile> list) {
        if (appContext == null) return;
        SharedPreferences prefs = appContext.getSharedPreferences(PREF_PLAYLIST_STORAGE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Gson gson = new Gson();
        String json = gson.toJson(list);
        editor.putString(KEY_ZIKFILES, json);
        editor.apply();
    }

    private static List<ZikFile> loadFromStorage() {
        if (appContext == null) return null;
        SharedPreferences prefs = appContext.getSharedPreferences(PREF_PLAYLIST_STORAGE, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_ZIKFILES, null);
        if (json == null) return null;

        Gson gson = new Gson();
        Type type = new TypeToken<List<ZikFile>>() {}.getType();
        return gson.fromJson(json, type);
    }




    //--- LOG --------------------------
    private static void myLog(String str) { KanLogger.myLog("PlayList", str); }
    private static void myLogE(String str) { KanLogger.myLogE("PlayList", str); }
}
