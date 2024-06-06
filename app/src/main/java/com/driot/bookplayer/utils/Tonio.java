package com.driot.bookplayer.utils;

import static com.driot.bookplayer.utils.Tonio2.removeLongDuplicates;
import static com.driot.tonylib.KanLogger.myLog;
import static com.driot.tonylib.KanLogger.myLogE;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.driot.tonylib.KanLogger;

import java.io.File;
import java.sql.Date;
import java.sql.Time;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;



/**
 * created by Antoine Driot -- antoine.driot.com -- on 31/10/20
 */
public class Tonio {
    private static final String LOG_PREFIX = "Tonio.java";


    public static String FormatTime(double doubleTime) {
        return FormatTime(doubleTime,false);
    }

    public static String FormatTime(double doubleTime, boolean doDisplaySec) {
        String s;
        long sec,min,hou;
        if (doubleTime>0) {

            hou = TimeUnit.MILLISECONDS.toHours((long) doubleTime);
            min = TimeUnit.MILLISECONDS.toMinutes((long) doubleTime)-TimeUnit.HOURS.toMinutes(hou);
            sec = TimeUnit.MILLISECONDS.toSeconds((long) doubleTime)-TimeUnit.HOURS.toSeconds(hou)-TimeUnit.MINUTES.toSeconds(min);
            if (hou !=0) {
                if (doDisplaySec) {
                    s = String.format(Locale.getDefault(),"%dh %dm %ds", hou, min, sec);
                } else {
                    s = String.format(Locale.getDefault(),"%dh %dm", hou, min);
                }
            } else if (min !=0) {
                s = String.format(Locale.getDefault(), "%dm %ds", min, sec);
            } else {
                s = String.format(Locale.getDefault(), "%ds", sec);
            }

        } else {
            s = "";
        }
        return s;
    }


    public static String FormatPercentStringForSpeed(Double d) {
        String str = "";
        if (d != null) {
            str = Math.round(d) + "%";
        }
        return str;
    }

    public static String FormatPercentString(Double d) {
        String str;
        if (d != null) {
            if (d == 100.0) {
                str = "100 %";
            } else if (d == 0.0) {
                str = "";
            } else if (d < 10.0) {
                str = d.toString().substring(0, 3);
                str = str + " %";
            } else {
                str = d.toString().substring(0, 2);
                str = str + " %";
            }
        } else {
            str = "";
        }
        return str;
    }
    public static int FormatPercentForProgressBar(Double d) {
        int i;
        if (d != null) {
            i = d.intValue();
            if (i < 0) {
                i = 0;
            }
            if (i > 100) {
                i = 100;
            }
        } else {
            i = 0;
        }
        return i;
    }
    public static double FormatPercentDouble(Double d) {
        if (d != null) {
            d = d * 100;
            if (d < 0) {
                d = 0.0;
            }
            if (d > 100) {
                d = 100.0;
            }
        } else {
            d = 0.0;
        }
        return d;
    }
    public static String FormatLastAccess(Date d, Time t, String NameForYesterday) {
        String s;
        if (d != null && t != null) {
            Date d2 = new Date(System.currentTimeMillis());
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DATE,-1);
            Date d3 = new Date(cal.getTimeInMillis()); // yesterday
            String s1 = d.toString();
            String s2 = d2.toString();
            String s3 = d3.toString();
            // check if date same as today
            if (s1.equals(s2)) {
                // Give time :
                s = t.toString();
                s = s.substring(0, 5);
                // check if yesterday
            } else if (s1.equals(s3)) {
                s = NameForYesterday;
                // give name of the day
            } else if ((d2.getTime()-d.getTime())/ (1000 * 60 * 60 * 24)<7) {
                // give name of the day
                SimpleDateFormat outFormat = new SimpleDateFormat("EEEE");
                s = outFormat.format(d);
            } else {
                //give date :
                SimpleDateFormat simpleDate =  new SimpleDateFormat("yyyy-MM-dd");
                s = simpleDate.format(d);
            }
        } else {
            s = " ";
        }
        return s;
    }

    public static String getFileNameFromPath(String fileName) {
        File file = new File(fileName);
        return file.getName();
    }

    public static String formatNameForDisplay(String s) {
        s = stripExtension(s);
        s = removeLongDuplicates(s,10);
        s = s.replace("_", " ");
        return s;
    }

    public static String FormatNameForDisplay_withUnderscore(String s) {
        s = stripExtension(s);
        s = removeLongDuplicates(s,10);
        s = s.replace(" ", "_");
        return s;
    }

    // Garde que le path
    public static String stripFileName(String fullPath) {
            // Find the last index of the file separator (either backslash or forward slash)
            int lastIndex = fullPath.lastIndexOf(File.separator);
            // Check if a file separator was found and get the directory path
            return (lastIndex != -1) ? fullPath.substring(0, lastIndex) : fullPath;
    }

    public static String stripExtension(String fileName) {
        String s = fileName;
        if (s.indexOf(".") > 0) {
            s = s.substring(0, s.lastIndexOf("."));
        }
        return s;
    }

    public static String getExtension(String fileName) {
        String s = fileName;
        int pos = s.lastIndexOf(".");
        if (pos > 0) {
            s = s.substring(pos+1);
        } else {
            s="";
        }
        return s;
    }

    public static String getSubFolders(String strFrom, String strPath) {
        myLog("getSubFolders key = " + strFrom + " , Path = " + strPath);
        String ret = strPath;
        ret = Tonio.stripFileName(ret);
        int pos = ret.indexOf(strFrom);
        if (pos>0) {
            ret = ret.substring(pos + strFrom.length());
        } else {
            ret = "";
        }
        myLog("getSubFolders => " + ret);
        return ret;
    }

    public static String getLastFolder(String strFolderPath) {
        String ret = strFolderPath;
        myLog("getLastFolder - Path = " + ret);
        int pos = ret.lastIndexOf("/");
        if (pos>0) {
            ret = ret.substring(pos + 1);
        } else {
            ret = "";
        }
        myLog("getLastFolder => " + ret);
        return ret;
    }

    @NonNull
    public static String getMimeType(String fileName) {
        //other possibility : library that read the beggining of file like "Apache Tika"
        //DocumentContract
        //DocumentFile.fromSingleUri(this, uri_given).getType;
        String type;
        final String extension = getExtension(fileName);
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
        if (type == null) type = "*/*";
        return type;
    }

    public static String getMimeType(File f) {
        String m = "*/*";
        try {
            m = DocumentFile.fromFile(f).getType();
        } catch (Exception e) {
            myLogE("getMimeType - " + e.getMessage());
        }
        return m;
    }

    public static boolean fileExists(String filePath) {
        File file = new File(filePath);
        return file.exists();
    }

    public static long getAvailableInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long availableBlocks = stat.getAvailableBlocksLong();
        return availableBlocks * blockSize;
    }

    public static long getTotaLInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long allBlocks = stat.getBlockCountLong();
        return allBlocks * blockSize;
    }

    public static long getAppSize(Context c) {
        long size = 0;
        final PackageManager pm = c.getPackageManager();
        ApplicationInfo applicationInfo;
        try {
            applicationInfo = pm.getApplicationInfo(c.getPackageName(), 0);
            File file = new File(applicationInfo.publicSourceDir);
            size = file.length();
        } catch (Exception e) {
            myLogE("Error getting size taken by app : " + e.getMessage());
            e.printStackTrace();
        }
        return size;
    }

    public static long getFolderSize(String folderName) {
        myLog("getFolderSize for [" + folderName + "]  (from path)");
        return getFolderSize(new File(folderName));
    }

    public   static long getFolderSize(File dir) {
        long size = 0;
        //myLog("getFolderSize for [" + dir.getAbsolutePath() + "]  (from File object)");

        File[] files = dir.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length(); // Get the file size and add it to the total
                } else if (file.isDirectory()) {
                    size += getFolderSize(file); // Recursively calculate the size of subdirectories
                }
            }
        } else {
            myLogE("ko Folder Not Found [" + dir.getAbsolutePath() + "]");
        }
        if (size == 0) {
            myLogE("getFolderSize returns zero size - Seems empty [" + dir.getAbsolutePath() + "]");
        }

        return size;
    }

    public static String formatMem(long mem){
        return formatMem(mem,7);
    }
    public static String formatMem(long mem, int padding){
        // %3s => left padding
        if (padding<1) {
            return String.valueOf(mem);
        } else {
            try {
                return String.format("%" + padding + "s", NumberFormat.getNumberInstance(Locale.getDefault()).format(mem));
            } catch (Exception e) {
                myLogE("formatMem...   " + e.getMessage());
                e.printStackTrace();
                return String.valueOf(mem);
            }
        }
    }



    private static void myLog(String str) { KanLogger.myLog(LOG_PREFIX, str); }
    private static void myLogE(String str) { KanLogger.myLogE(LOG_PREFIX, str); }

}
