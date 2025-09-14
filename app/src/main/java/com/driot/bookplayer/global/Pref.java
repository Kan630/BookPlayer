package com.driot.bookplayer.global;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 06/06/2025
 */

import static android.content.Context.MODE_PRIVATE;

import static com.driot.bookplayer.global.Var.PODCAST_DETAIL_ANIMATION_COUNT;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.util.Base64;

import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.objects.MyAudioMetadata;
import com.driot.bookplayer.utils.KanLogger;

public class Pref {


    private static final String SHARED_PREFERENCES_DIVERSE = "SHARED_PREFERENCES_DIVERSE";

    private static final String SHARED_PREFERENCE_INTROCUT = "SHARED_PREFERENCE_INTROCUT";
    private static final String SHARED_PREFERENCE_TTS_LANG = "SHARED_PREFERENCE_TTS_LANG";

    private static final String SHARED_PREFERENCES_DOWNLOAD = "SHARED_PREFERENCES_DOWNLOAD";
    private static final String KEY_LOAD_BOOK_TASK_STATE = "loadBookTaskState";

    private static Context appContext;
    private static android.content.SharedPreferences prefs;
    public static void init(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE);
    }



    /////////////////// HAS BEEN PAUSED FOR  ///////////////////
    public static void setPauseTime(long value) {prefs.edit().putLong("PAUSE_TIME", value).apply();}
    public static void setPauseTime() {prefs.edit().putLong("PAUSE_TIME", System.currentTimeMillis()).apply();myLog("pause time set");}
    public static long getPauseTime() {return prefs.getLong("PAUSE_TIME", 0);}





    /////////////////// OPEN WITH ... LAST IMPORTED FILE ///////////////////
    public static void set_Last_OpenWith_FileUri(Context c, String last_uri) {c.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE).edit().putString("LAST_URI",last_uri).apply();}
    public static String get_Last_OpenWith_FileUri(Context c) {return c.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE).getString("LAST_URI", "none");}

    public static void set_Last_OpenWith_File_Time(Context c) {c.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE).edit().putLong("LAST_URI_TIME",System.currentTimeMillis()).apply();}
    public static long get_Last_OpenWith_File_Time(Context c) {return c.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE).getLong("LAST_URI_TIME", 0);}




    public static void saveIntroCutToPref(Context c, int idFolder, int introCut) {
        try {
            SharedPreferences.Editor editor = c.getSharedPreferences(SHARED_PREFERENCE_INTROCUT, MODE_PRIVATE).edit();
            editor.putInt(Integer.toString(idFolder), introCut).apply();
        } catch (Exception e) {
            myLogEE(e,"error saving introCut in prefs");
        }
    }

    public static int getIntroCutFromPref(Context c, int idFolder) {
        try {
            SharedPreferences prefs = c.getSharedPreferences(SHARED_PREFERENCE_INTROCUT, MODE_PRIVATE);
            return prefs.getInt(String.valueOf(idFolder), 0);
        } catch (Exception e) {
            myLogEE(e,"error getting introCut from prefs");
            return 0;
        }
    }


    public static void setBookTtsLanguage(Context c, int idFolder, String twoLetterCodeOrSystem) {
        try {
            c.getSharedPreferences(SHARED_PREFERENCE_TTS_LANG, MODE_PRIVATE)
                    .edit()
                    .putString(String.valueOf(idFolder), twoLetterCodeOrSystem)
                    .apply();
        } catch (Exception e) {
            myLogEE(e, "error saving book TTS language in prefs");
        }
    }

    public static String getBookTtsLanguage(Context c, int idFolder) {
        try {
            SharedPreferences prefs = c.getSharedPreferences(SHARED_PREFERENCE_TTS_LANG, MODE_PRIVATE);
            return prefs.getString(String.valueOf(idFolder), Option.getTtsLanguage());
        } catch (Exception e) {
            myLogEE(e, "error getting book TTS language from prefs");
            return Option.getTtsLanguage();
        }
    }



    public static void setLoadBookTaskState(LoadBookTaskState loadBookTaskState) {
        Parcel parcel = Parcel.obtain();
        loadBookTaskState.writeToParcel(parcel, 0);
        byte[] bytes = parcel.marshall();
        parcel.recycle();

        String encoded = Base64.encodeToString(bytes, Base64.DEFAULT);

        SharedPreferences prefs = appContext.getSharedPreferences(SHARED_PREFERENCES_DOWNLOAD, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LOAD_BOOK_TASK_STATE, encoded).apply();
    }

    public static LoadBookTaskState getLoadBookTaskState() {
        SharedPreferences prefs = appContext.getSharedPreferences(SHARED_PREFERENCES_DOWNLOAD, Context.MODE_PRIVATE);
        String encoded = prefs.getString(KEY_LOAD_BOOK_TASK_STATE, null);

        if (encoded == null) return null;

        Parcel parcel = Parcel.obtain();
        try {
            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
            parcel.unmarshall(bytes, 0, bytes.length);
            parcel.setDataPosition(0);

            // This may throw if the blob was saved with an older class layout
            return LoadBookTaskState.CREATOR.createFromParcel(parcel);

        } catch (Throwable t) {
            // Incompatible or corrupt state → reset it
            prefs.edit().remove(KEY_LOAD_BOOK_TASK_STATE).apply();
            return null;

        } finally {
            parcel.recycle();
        }
    }

    public static void clearLoadBookTaskState(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFERENCES_DOWNLOAD, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_LOAD_BOOK_TASK_STATE).apply();
    }


    public static void saveAudioMetadata(MyAudioMetadata metadata) {
        Parcel parcel = Parcel.obtain();
        metadata.writeToParcel(parcel, 0);
        byte[] bytes = parcel.marshall();
        parcel.recycle();
        String encoded = Base64.encodeToString(bytes, Base64.DEFAULT);
        SharedPreferences prefs = appContext.getSharedPreferences(SHARED_PREFERENCES_DOWNLOAD, Context.MODE_PRIVATE);
        prefs.edit().putString("KEY_AUDIO_METADATA", encoded).apply();
    }
    public static MyAudioMetadata loadAudioMetadata() {
        SharedPreferences prefs = appContext.getSharedPreferences(SHARED_PREFERENCES_DOWNLOAD, Context.MODE_PRIVATE);
        String encoded = prefs.getString("KEY_AUDIO_METADATA", null);
        if (encoded == null) return null;
        byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
        Parcel parcel = Parcel.obtain();
        parcel.unmarshall(bytes, 0, bytes.length);
        parcel.setDataPosition(0);
        MyAudioMetadata metadata = MyAudioMetadata.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        return metadata;
    }
    public static void clearAudioMetadata() {
        SharedPreferences prefs = appContext.getSharedPreferences(SHARED_PREFERENCES_DOWNLOAD, Context.MODE_PRIVATE);
        prefs.edit().remove("KEY_AUDIO_METADATA").apply();
    }






    /////////////////// LANGUAGE SPINNER ///////////////////
    public static void set_Audio_Language_Librivox(Context c, String audioLanguage) {c.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE).edit().putString("AUDIO_LANGUAGE_LIBRIVOX",audioLanguage).apply();}
    public static String get_Audio_Language_Librivox(Context c) {return c.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE).getString("AUDIO_LANGUAGE_LIBRIVOX", "eng");}
    public static void set_Audio_Language_Podcast(Context c, String audioLanguage) {c.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE).edit().putString("AUDIO_LANGUAGE_PODCAST",audioLanguage).apply();}
    public static String get_Audio_Language_Podcast(Context c) {return c.getSharedPreferences(SHARED_PREFERENCES_DIVERSE, MODE_PRIVATE).getString("AUDIO_LANGUAGE_PODCAST", "en");}





    /////////////////// PODCAST DETAIL FAVORITE and AUTODOWNLOAD animations ///////////////////
    public enum AnimatedButton {
        FAVORITE,
        AUTO_DOWNLOAD
    }
    public static boolean shouldAnimateButtons(AnimatedButton button) {
        int opens = prefs.getInt("ANIMATE_BUTTON_COUNT_" + button, 0);
        if (opens < PODCAST_DETAIL_ANIMATION_COUNT) {
            prefs.edit().putInt("ANIMATE_BUTTON_COUNT_" + button, opens + 1).apply();
            return true;
        }
        return false;
    }
    public static void stopAnimateButtons(AnimatedButton button) {
        prefs.edit().putInt("ANIMATE_BUTTON_COUNT_" + button, PODCAST_DETAIL_ANIMATION_COUNT).apply();
    }


    /////////////////// LAST PODCAST API CHECK  ///////////////////
    public static void setLastCheck(long value) {prefs.edit().putLong("LAST_PODCASTINDEXORG_API_AUTO_CHECK_TIMESTAMP", value).apply();}
    public static long getLastCheck() {return prefs.getLong("LAST_PODCASTINDEXORG_API_AUTO_CHECK_TIMESTAMP", Option.getPodcastAutoDownloadDelayBetweenChecks());}
    public static boolean shouldCheckApiForAutoDownload() {
        long lastCheck = getLastCheck();
        long now = System.currentTimeMillis();
        long diffInMinutes = (now - lastCheck) / (60 * 1000);
        long minDelayBetweenCheck = (long) Option.getPodcastAutoDownloadDelayBetweenChecks();
        if (now - lastCheck > minDelayBetweenCheck * 60 * 1000) {
            setLastCheck(now);
            myLogD("shouldCheckApiForAutoDownload() => true  -  last check = " + diffInMinutes + " min ago...   (min delay = " + minDelayBetweenCheck + " min.)");
            return true;
        } else {
            myLogD("shouldCheckApiForAutoDownload() => false  -  last check = " + diffInMinutes + " min ago...   (min delay = " + minDelayBetweenCheck + " min.)");
            return false;
        }
    }

    // ----------------------- LOG -----------------------
    private static final String TAG = "Pref";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }
}
