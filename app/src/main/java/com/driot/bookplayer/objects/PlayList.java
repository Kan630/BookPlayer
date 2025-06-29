package com.driot.bookplayer.objects;

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
    private static int numZikFile = -1; // old numSong

    private static final String SHARED_PREFERENCE_CURRENT_PLAYLIST = "SHARED_PREFERENCE_CURRENT_PLAYLIST";
    private static final String KEY_ZIK_FILES_LIST = "KEY_ZIK_FILES_LIST";
    private static final String KEY_ZIK_FILE = "KEY_ZIK_FILE";


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

    public static int getNumZikFile(Context c) {
        if (numZikFile < 0) {
            numZikFile = loadFromStorage(c);
        }
        return numZikFile;
    }

    public static void setNumZikFile(Context c, int numZikFile) {
        PlayList.numZikFile = numZikFile;
        myLog("SetNumZikFile() - n°" + numZikFile);
        saveToStorage(c, numZikFile);
    }

    public static ZikFile getZikFile() {
        if (numZikFile>=0) {
            if (!(getZikFilesList()==null)) {
                try {
                    return getZikFilesList().get(numZikFile);
                } catch (Exception e) {
                    myLogE("getZikFile() ERROR - try-catch -- " + e.getMessage());
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


    // -----------------------------------
    // ...FAIL SAFE STORAGE...
    // -----------------------------------
    private static void saveToStorage(Context c, int numZikFile) {
        c.getSharedPreferences(SHARED_PREFERENCE_CURRENT_PLAYLIST, Context.MODE_PRIVATE).edit().putInt(KEY_ZIK_FILE, numZikFile).apply();
    }
    private static int loadFromStorage(Context c) {
        return c.getSharedPreferences(SHARED_PREFERENCE_CURRENT_PLAYLIST, Context.MODE_PRIVATE).getInt(KEY_ZIK_FILE, -1);
    }
    private static void saveToStorage(List<ZikFile> list) {
        if (appContext == null) myLogE("saveToStorage - null context");
        SharedPreferences prefs = appContext.getSharedPreferences(SHARED_PREFERENCE_CURRENT_PLAYLIST, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Gson gson = new Gson();
        String json = gson.toJson(list);
        editor.putString(KEY_ZIK_FILES_LIST, json);
        editor.apply();
    }
    private static List<ZikFile> loadFromStorage() {
        if (appContext == null) return null;
        SharedPreferences prefs = appContext.getSharedPreferences(SHARED_PREFERENCE_CURRENT_PLAYLIST, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_ZIK_FILES_LIST, null);
        if (json == null) return null;
        Gson gson = new Gson();
        Type type = new TypeToken<List<ZikFile>>() {}.getType();
        return gson.fromJson(json, type);
    }




    //--- LOG --------------------------
    private static void myLog(String str) { KanLogger.myLog("PlayList", str); }
    private static void myLogE(String str) { KanLogger.myLogE("PlayList", str); }
}
