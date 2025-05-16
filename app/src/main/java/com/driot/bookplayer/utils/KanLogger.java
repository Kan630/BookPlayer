package com.driot.bookplayer.utils;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static com.driot.bookplayer.utils.TonioCommonStuff.MD5;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 20/12/20
 *
 * Utility Class = Helper class = contains just static methods => cannot be instantiated
 */
public class KanLogger {


    private static final boolean LOG_DEBUG = true;


    private static final String PREFIX_DELETE = "com.driot.bookplayer.";
    private static final String LOG_FILE_FOLDER = "log";
    private static final String LOG_FILE_NAME = "kanlog";
    private static final String USER_LOG_FILE_FOLDER = "log";
    private static final String USER_LOG_FILE_NAME = "kanlog";
    private static final String kanLogger_TAG = "toto KanLogger";
    private static final boolean LOG_THEM_ALL = true;

    private static final String LOG_PREFIX = "toto";

    @SuppressWarnings("SpellCheckingInspection")
    public static final String[] MD5_MY_PHONE = {""
            ,"eb621dde2a2672e66b6e6ef5acbbbb99" //"OPPO/CPH2065EEA/OP4BDCL1:11/RP1A.200720.011/1629728339857:user/release-keys"
            ,"a35ba9d541e15b9ff7b017b7fef54430" // Redmi/veux_eea/veux:13/TKQ1.221114.001/V816.0.1.0.TKCEUXM:user/release-keys
            ,"dabe9f1966e715f8d0cdf81561647f7c" // OPPO/CPH2065EEA/OP4BDCL1:12/SP1A.210812.016/Q.GDPR.132c99b-1ff9d:user/release-keys
            ,"177d74d79a466e1713c2d0bac3a533cb" // samsung/gta8wifieea/gta8wifi:14/UP1A.231005.007/X200XXS3DXD5:user/release-keys
    };


    // TODO use the same logic as Playlist Context init, coupled with MyPersonalApp class


    /////////////////////////////////
    /// CONTEXT - needed for * Writing log files * Toasts *
    /////////////////////////////////-----------------------------------------------------------
    private static Context appContext;
    private static Context getMyAppContext() {
        if (KanLogger.appContext != null) {
            return KanLogger.appContext;
        } else {
            Log.e(kanLogger_TAG, "getMyAppContext is null.");
            return null;
        }
    }
    //public static void setKanContext(Context c) {    // Now using MyPersonalApp
    //    KanLogger.appContext = c;
    //}
    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    /////////////////////////////////
    /// IS DEV
    /////////////////////////////////-----------------------------------------------------------
    public static boolean isMyPhoneDev() {
        boolean ret = false;
        String strToCheck = MD5(Build.FINGERPRINT);
        if (strToCheck != null) {
            for (String s : MD5_MY_PHONE) {
                if (s.contains(strToCheck)) {
                    ret = true;
                    break;
                }
            }
        }
        //Log.d("toto", "IsMyPhoneDev : End");
        return ret;
    }

    /////////////////////////////////
    /// LOG
    /////////////////////////////////-----------------------------------------------------------
    public static void myLogInFile(String str) {
        myLog(str);
    }

    public static void myLog(String str) {
        myLog("",str);
    }
    public static void myLog(String prefix, String str) {
        prefix = prefix.replace(PREFIX_DELETE,"");
        if (TextUtils.isEmpty(str)) {str = "...";}
        if (isMyPhoneDev()) {
            writeToLogFile(str);
            Log.d(LOG_PREFIX + " " + prefix, str);
        } else {
            if (LOG_THEM_ALL) Log.d(prefix, str);
        }
    }

    public static void myLogI(String str) {
        myLogI("",str);
    }
    public static void myLogI(String prefix, String str) {
        prefix = prefix.replace(PREFIX_DELETE,"");
        if (TextUtils.isEmpty(str)) {str = "...";}
        if (isMyPhoneDev()) {
            writeToLogFile(str);
            Log.i(LOG_PREFIX + " " + prefix, str);
        } else {
            if (LOG_THEM_ALL) Log.i(prefix, str);
        }
    }

    public static void myLogD(String str)  { myLogD("",str); }
    public static void myLogD(String prefix, String str) {
        prefix = prefix.replace(PREFIX_DELETE,"");
        if (LOG_DEBUG && isMyPhoneDev()) {
            writeToLogFile(str);
            Log.d(LOG_PREFIX + "D " + prefix, str);
        }
    }

    public static void myLogE(String str) {
        myLogE("",str);
    }
    public static void myLogE(String prefix, String str) {
        prefix = prefix.replace(PREFIX_DELETE,"");
        if (TextUtils.isEmpty(str)) {str = "...";}
        if (isMyPhoneDev()) {
            writeToLogFile(prefix + ".ERR: " + str);
            Log.e(LOG_PREFIX + " " + prefix, str);
        } else {
            if (LOG_THEM_ALL) Log.e("", str);
        }
    }




    /////////////////////////////////
    /// TOAST
    /////////////////////////////////-----------------------------------------------------------
    public static void myToast(String str) {
        myLog("TOASTING : " + str);
        if (getMyAppContext() != null) {
            Toast.makeText(getMyAppContext(), str, Toast.LENGTH_SHORT).show();
        }
    }

    public static void myLongToast(String str) {
        myLog("TOASTING : " + str);
        if (getMyAppContext() != null) {
            Toast.makeText(getMyAppContext(), str, Toast.LENGTH_LONG).show();
        }
    }

    public static void myToastE(String str) {
        myLogE("TOASTING : " + str);
        if (getMyAppContext() != null) {
            Toast.makeText(getMyAppContext(), str, Toast.LENGTH_SHORT).show();
        }
    }

    public static void myLongToastE(String str) {
        myLogE("TOASTING : " + str);
        if (getMyAppContext() != null) {
            Toast.makeText(getMyAppContext(), str, Toast.LENGTH_LONG).show();
        }
    }



    /////////////////////////////////
    /// LOG FILES
    /////////////////////////////////-----------------------------------------------------------
    private static void writeToLogFile(String message)
    {
        if (getMyAppContext() != null) {
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
            //String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
            String fileName = LOG_FILE_NAME + "_" + date + ".txt";
            try {
                //FileOutputStream fileOutputStream = getMyAppContext.openFileOutput( fileName, Context.MODE_PRIVATE + Context.MODE_APPEND);
                File dir = new File(getMyAppContext().getFilesDir(), LOG_FILE_FOLDER);
                dir.mkdirs();
                FileOutputStream fileOutputStream = new FileOutputStream(new File(dir, fileName),true);

                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream);
                outputStreamWriter.write(time + " " + message + "\n");
                outputStreamWriter.close();

            } catch(FileNotFoundException e) {
                e.printStackTrace();
                Log.e(kanLogger_TAG, "writeToLogFile() : [" + e.getMessage() + "]");
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(kanLogger_TAG, "writeToLogFile() : [" + e.getMessage() + "]");
            }
        } else {
            Log.e(kanLogger_TAG, "writeToLogFile() KO : getMyAppContext is null");
        }
    }

}
