package com.driot.bookplayer.utils;

import static com.driot.bookplayer.utils.Tonio2.removeLongDuplicates;
import static com.driot.bookplayer.utils.KanLogger.myLog;
import static com.driot.bookplayer.utils.KanLogger.myLogE;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.sql.Date;
import java.sql.Time;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;



/**
 * created by Antoine Driot -- antoine.driot.com -- on 31/10/20
 */
public class Tonio {
    private static final String LOG_PREFIX = "Tonio.java";


    public static String formatTime(double doubleTime) {
        return formatTime(doubleTime,false);
    }

    public static String formatTime(double doubleTime, boolean doDisplaySec) {
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

    public static String FormatPercentStringForVolume(Double d) {
        String str = "";
        if (d != null) {
            str = Math.round(d) + "%";
        }
        if (str.equals("-100%")) {
            str = "...";
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
                SimpleDateFormat outFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
                s = outFormat.format(d);
            } else {
                //give date :
                SimpleDateFormat simpleDate =  new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                s = simpleDate.format(d);
            }
        } else {
            s = " ";
        }
        return s;
    }

    public static String formatLastAccessInDays(Date lastAccessDate) {
        String zeReturn;
        if (lastAccessDate == null) {
            zeReturn = "Never accessed";
        }

        Date currentDate = new Date(System.currentTimeMillis());

        long diffInMillis = currentDate.getTime() - lastAccessDate.getTime();
        long diffInDays = diffInMillis / (1000 * 60 * 60 * 24);

        if (diffInDays > 400) {
            int years = (int) (diffInDays / 365);
            zeReturn = years + (years == 1 ? " year ago" : " years ago");
        } else if (diffInDays > 50) {
            int months = (int) (diffInDays / 30);
            zeReturn = months + (months == 1 ? " month ago" : " months ago");
        } else {
            zeReturn = diffInDays + (diffInDays == 1 ? " day ago" : " days ago");
        }
        return zeReturn;
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
        int lastIndex = -1;
        if (fullPath != null) {
            lastIndex = fullPath.lastIndexOf(File.separator);
        }
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

    public static String getLastFolder(@NonNull String strFolderPath) {
        try {
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
        } catch (Exception e) {
            myLogE("getLastFolder()... " + e.getMessage());
            return "";
        }
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

    public static String getSourceLocation(Uri uri) {
        if (!Objects.isNull(uri) &&  !Objects.isNull(uri.getAuthority())) {
            String uriAuthority = uri.getAuthority();
            Set<String> cloudAuthorities = new HashSet<>();
            cloudAuthorities.add("com.google.android.apps.docs.storage"); // Google Drive
            cloudAuthorities.add("com.microsoft.skydrive.content");       // OneDrive
            cloudAuthorities.add("com.microsoft.skydrive.content.StorageAccessProvider");       // OneDrive
            cloudAuthorities.add("com.dropbox.product.android.dbapp.document_provider.documents");  // DropBox
            if (uriAuthority != null && cloudAuthorities.contains(uriAuthority)) {
                return "cloud";
            } else {
                return "local";
            }
        }
        return "xxx";
    }

    public static String getMimeType(Context context, Uri uri) {
        // Try content resolver first
        String mime = null;

        try {
            mime = context.getContentResolver().getType(uri);
        } catch (Exception ignored) {}

        // If null, fallback using extension
        if (mime == null) {
            String extension = null;

            // Try to get file name from uri
            if ("content".equals(uri.getScheme())) {
                Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
                if (cursor != null) {
                    try {
                        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            String fileName = cursor.getString(nameIndex);
                            int dotIndex = fileName.lastIndexOf('.');
                            if (dotIndex >= 0) {
                                extension = fileName.substring(dotIndex + 1).toLowerCase();
                            }
                        }
                    } finally {
                        cursor.close();
                    }
                }
            } else if ("file".equals(uri.getScheme())) {
                String path = uri.getPath();
                if (path != null) {
                    int dotIndex = path.lastIndexOf('.');
                    if (dotIndex >= 0) {
                        extension = path.substring(dotIndex + 1).toLowerCase();
                    }
                }
            }

            // Use extension to guess mime type
            if (extension != null) {
                mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                if (mime == null && extension.equals("m4b")) {
                    mime = "audio/m4b"; // custom fallback for m4b
                }
            }
        }

        return mime;
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

    public static String getFileNameFromUri(Context c, @NonNull Uri uri) {
            String name = null;

            // Try modern way first
            if ("content".equals(uri.getScheme())) {
                try (Cursor cursor = c.getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (index != -1) {
                            name = cursor.getString(index);
                        }
                    }
                } catch (Exception e) {
                    myLogE("Modern filename fetch failed: " + e.getMessage());
                }
            }

            // Fallback for Android 8
            if (name == null) {
                name = getFileNameFromMediaUri(c, uri);
            }

            return name;
        }

    public static String getFileNameFromMediaUri(Context c, @NonNull Uri uri) {
        try {
            String[] projection = { MediaStore.MediaColumns.DATA };
            Cursor cursor = c.getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null) {
                try {
                    int index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                    if (cursor.moveToFirst()) {
                        String filePath = cursor.getString(index);
                        return new File(filePath).getName();
                    }
                } catch (Exception e) {
                    myLogE("getFileNameFromMediaUri failed: " + e.getMessage());
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            myLogE("getFileNameFromMediaUri failed: " + e.getMessage());
        }
        return uri.getLastPathSegment(); // Fallback
    }
/* Old Code...
    // from FileHelper... used to copy zip locally in Android 11+
    private static String getContentName(ContentResolver resolver, Uri uri) {
        Cursor cursor = resolver.query(uri, null, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();
            int nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
            if (nameIndex >= 0) {
                String name = cursor.getString(nameIndex);
                cursor.close();
                return name;
            }
        }
        return null;
    }

 */



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
        return formatMem(mem,9);
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
