package com.driot.tonylib;

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

import static com.driot.tonylib.TonioCommonStuff.MD5;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 20/12/20
 *
 * Utility Class = Helper class = contains just static methods => cannot be instanciated
 */
public class KanLogger {

    private static final String PREFIX_DELETE = "com.driot.bookplayer.";
    private static final String LOG_FILE_FOLDER = "log";
    private static final String LOG_FILE_NAME = "kanlog";
    private static final String USER_LOG_FILE_FOLDER = "log";
    private static final String USER_LOG_FILE_NAME = "kanlog";
    private static final String kanLogger_TAG = "toto KanLogger";
    private static final boolean LOG_THEM_ALL = true;
    private static final boolean LOG_DEBUG = true;

    public static final String[] MD5_MY_PHONE = {
             "5ef4fa41375ff615e0fd81940d929294" //"HUAWEI/POT-LX1EEA/HWPOT-H:10/HUAWEIPOT-L21/10.0.0.238C431:user/release-keys"
            ,"540eb3b4c6a4140193519e66f9cc29e4" //"Logicom/Le_Hello/Le_Hello:7.0/NRD90M/1527151208:user/release-keys"
            ,"dc785c43b8f9a6fcefc067a0050cb370" //"samsung/j5y17ltexx/j5y17lte:9/PPR1.180610.011/J530FXXS7CTF1:user/release-keys"
            ,"ed8acdf5617d368dce4175a6597197a2" //"samsung/j5y17ltexx/j5y17lte:9/PPR1.180610.011/J530FXXS9CUE5:user/release-keys" // 09/09/2021
            ,"56db55f6c978892e2f3a55563fcb6f80" //"OPPO/CPH2065EEA/OP4BDCL1:11/RP1A.200720.011/1718335401248:user/release-keys"
            ,"f440eedc21b7e92490e9ad90e4a93215" //"OPPO/CPH2065EEA/OP4BDCL1:11/RP1A.200720.011/1624562435949:user/release-keys" //oppo-cph2065-P7LFRGOFKVKRLNPF
            ,"eb621dde2a2672e66b6e6ef5acbbbb99" //"OPPO/CPH2065EEA/OP4BDCL1:11/RP1A.200720.011/1629728339857:user/release-keys"
            ,"3dcf828fa24be7a49a361c4f4ba3dfd4" // RedMi sept 2023
            ,"3a53d8836f50d0826c99bca3900fbc24" // RedMi sept 2023, after upate
            ,"a35ba9d541e15b9ff7b017b7fef54430" // Redmi/veux_eea/veux:13/TKQ1.221114.001/V816.0.1.0.TKCEUXM:user/release-keys
    };


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
    public static void setKanContext(Context c) {
        KanLogger.appContext = c;
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
            Log.d("toto " + prefix, str);
        } else {
            if (LOG_THEM_ALL) Log.d(prefix, str);
        }
    }

    public static void myLogD(String str)  { myLogD("",str); }
    public static void myLogD(String prefix, String str) {
        prefix = prefix.replace(PREFIX_DELETE,"");
        if (LOG_DEBUG && isMyPhoneDev()) {
            writeToLogFile(str);
            Log.d("totoD " + prefix, str);
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
            Log.e("toto " + prefix, str);
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
