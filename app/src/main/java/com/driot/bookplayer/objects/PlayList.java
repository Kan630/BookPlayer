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
 * refactored -- on 2025-07-01
 */
public class PlayList {

    private static PlayList instance;

    private List<ZikFile> zikFilesList;
    private int numZikFile = -1;
    private static Context appContext;

    private static final String SHARED_PREFERENCE_CURRENT_PLAYLIST = "SHARED_PREFERENCE_CURRENT_PLAYLIST";
    private static final String KEY_ZIK_FILES_LIST = "KEY_ZIK_FILES_LIST";
    private static final String KEY_ZIK_FILE = "KEY_ZIK_FILE";


    //  called in : public class MyPersonalApp extends Application
    public static void initContext(Context context) {
        appContext = context.getApplicationContext();
    }

    // --- Constructor ---
    private PlayList(List<ZikFile> zikFilesList) {
        this.zikFilesList = zikFilesList;
    }

    // --- Called ONLY from FolderAdapter ---
    public static void create(Context ctx, List<ZikFile> zikFilesList) {
        myLog("Playlist created");
        appContext = ctx.getApplicationContext();  // not really needed but you never know :-)
        instance = new PlayList(zikFilesList);
        instance.saveToStorage();
    }

    // --- Get instance from anywhere ---
    public static PlayList getInstance() {
        if (instance == null) {
            instance = loadFromStorage();
            if (instance == null) {
                myLogEE(null, "getInstance => PlayList not initialized and no saved instance available.");
                //throw new IllegalStateException("PlayList not initialized and no saved instance available.");
            }
        }
        return instance;
    }

    public void setNumZikFile(int numZikFile) {
        this.numZikFile = numZikFile;
        myLog("setNumZikFile(" + numZikFile + ") - " + getNumSlashTotal());
        saveToStorage();
    }

    public void clear() {
        zikFilesList = null;
        numZikFile = -1;
        clearStorage();
    }

    public void nextTrack() {
        numZikFile = numZikFile + 1;
        saveToStorage();
        myLogD("nextTrack()");
    }

    public int getSize() {
        if (zikFilesList == null) {
            return 0;
        } else {
            return zikFilesList.size();
        }
    }

    public static boolean isAvailable() {
        return instance != null;
    }

    public boolean isLastTrack() {
        return zikFilesList != null && numZikFile + 1 == zikFilesList.size();
    }

    public List<ZikFile> getZikFilesList() {
        if (zikFilesList == null) {
            instance = loadFromStorage();
        }
        if (zikFilesList == null) {
            myLogE("zikFilesList == null .... still !");
        }
        return zikFilesList;
    }

    public int getNumZikFile() {
        return numZikFile;
    }


    public ZikFile getZikFile() {
        if (numZikFile < 0 ) {
            myLogEE(null, "numZikFile < 0");
            return null;
            //throw new IllegalStateException("Could not get audio file from PlayList.");
        } else if (zikFilesList == null) {
            myLogEE(null, "zikFilesList == null");
            //throw new IllegalStateException("Could not get audio file from PlayList.");
            return null;
        } else {
            try {
                return zikFilesList.get(numZikFile);
            } catch (Exception e) {
                myLogEE(e, "zikFilesList.get(" + numZikFile + ") => throw new IllegalStateException");
                //throw new IllegalStateException("Could not get audio file from PlayList.");
                return null;
            }
        }
    }

    // --- Save and load ---
    private void saveToStorage() {
        SharedPreferences prefs = appContext.getSharedPreferences(SHARED_PREFERENCE_CURRENT_PLAYLIST, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String json = new Gson().toJson(zikFilesList);
        editor.putString(KEY_ZIK_FILES_LIST, json);
        editor.putInt(KEY_ZIK_FILE, numZikFile);
        editor.apply();
    }

    private static PlayList loadFromStorage() {
        myLogW("Playlist retreived");
        SharedPreferences prefs = appContext.getApplicationContext().getSharedPreferences(SHARED_PREFERENCE_CURRENT_PLAYLIST, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_ZIK_FILES_LIST, null);
        if (json == null) return null;
        Type type = new TypeToken<List<ZikFile>>() {}.getType();
        List<ZikFile> list = new Gson().fromJson(json, type);
        int index = prefs.getInt(KEY_ZIK_FILE, -1);
        PlayList pl = new PlayList(list);
        pl.numZikFile = index;
        return pl;
    }


    public static void clearStorage() {
        SharedPreferences prefs = appContext.getSharedPreferences(SHARED_PREFERENCE_CURRENT_PLAYLIST, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }





    public String getNumZikFileForDisplay() {
        return Integer.toString(numZikFile + 1);
    }

    public String getNumSlashTotal() {
        return Integer.toString(numZikFile + 1) + "/" + Integer.toString(zikFilesList.size());
    }


    // ----------------------- LOG -----------------------
    private static final String TAG = "PlayList";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }

}
