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

    private static final String LOG_FILE_FOLDER = "log";
    private static final String LOG_FILE_NAME = "kanlog3";
    private static final String USER_LOG_FILE_FOLDER = "log";
    private static final String USER_LOG_FILE_NAME = "kanlog";
    private static final boolean LOG_THEM_ALL = false;

    public static final String[] MD5_MY_PHONE = {
             "5ef4fa41375ff615e0fd81940d929294" //"HUAWEI/POT-LX1EEA/HWPOT-H:10/HUAWEIPOT-L21/10.0.0.238C431:user/release-keys"
            ,"540eb3b4c6a4140193519e66f9cc29e4" //"Logicom/Le_Hello/Le_Hello:7.0/NRD90M/1527151208:user/release-keys"
            ,"dc785c43b8f9a6fcefc067a0050cb370" //"samsung/j5y17ltexx/j5y17lte:9/PPR1.180610.011/J530FXXS7CTF1:user/release-keys"
            ,"56db55f6c978892e2f3a55563fcb6f80" //"OPPO/CPH2065EEA/OP4BDCL1:11/RP1A.200720.011/1718335401248:user/release-keys"
            ,"f440eedc21b7e92490e9ad90e4a93215" //"OPPO/CPH2065EEA/OP4BDCL1:11/RP1A.200720.011/1624562435949:user/release-keys" //oppo-cph2065-P7LFRGOFKVKRLNPF
    };

    private static Context appContext;

    public static void setContext(Context c) {
        KanLogger.appContext = c;
    }

    public static void myLogInFile(String str) {
        myLog(str);
    }

    public static void myLog(String str) {
        if (TextUtils.isEmpty(str)) {str = "...";}
        writeToLogFile(str);
        if (isMyPhoneDev()) {
            Log.d("toto", str);
        } else {
            if (LOG_THEM_ALL) Log.d("", str);
        }
    }

    public static void myLogE(String str) {
        if (TextUtils.isEmpty(str)) {str = "...";}
        if (isMyPhoneDev()) {
            writeToLogFile("ERR: " + str);
            Log.e("toto", str);
        } else {
            if (LOG_THEM_ALL) Log.e("", str);
        }
    }

    public static void myToast(String str) {
        Toast.makeText(appContext, str, Toast.LENGTH_SHORT).show();
        myLog(str);
    }

    public static void myLongToast(String str) {
        Toast.makeText(appContext, str, Toast.LENGTH_LONG).show();
        myLog(str);
    }

    public static void myToastE(String str) {
        Toast.makeText(appContext, str, Toast.LENGTH_SHORT).show();
        myLogE(str);
    }

    public static void myLongToastE(String str) {
        Toast.makeText(appContext, str, Toast.LENGTH_LONG).show();
        myLogE(str);
    }

    private static boolean isMyPhoneDev() {
        boolean ret = false;
        //return MD5_MY_PHONE.equals(MD5(Build.FINGERPRINT));
        String strToCheck = MD5(Build.FINGERPRINT);
        if (strToCheck != null) {
            for (String s : MD5_MY_PHONE) {
                if (s.contains(strToCheck)) {
                    ret = true;
                    break;
                }
            }
        }
        return ret;
    }






    private static void writeToLogFile(String message)
    {
        if (appContext != null) {
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
            String fileName = LOG_FILE_NAME + "_" + date + ".txt";
            try {
                //FileOutputStream fileOutputStream = appContext.openFileOutput( fileName, Context.MODE_PRIVATE + Context.MODE_APPEND);
                File dir = new File(appContext.getFilesDir(), LOG_FILE_FOLDER);
                dir.mkdirs();
                FileOutputStream fileOutputStream = new FileOutputStream(new File(dir, fileName),true);

                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream);
                outputStreamWriter.write(time + " " + message + "\n");
                outputStreamWriter.close();

            } catch(FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
