package com.driot.bookplayer.utils;

import static com.driot.bookplayer.utils.Utils.recursiveRemove;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class KanFiles {

    public static void copyFile(File source, File dest) throws IOException {
        copyFileUsingStream(source, dest);
    }

    private static void copyFileUsingStream(File source, File dest) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(source);
            os = new FileOutputStream(dest);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            is.close();
            os.close();
        }
    }


    public static boolean deleteFolderRecursive(String strPath) {
        String starter = "file:///";
        if (strPath.length()>5) {
            strPath = strPath.replace(starter,"");
            try {
                File zikFileToDelete = new File(strPath);
                if(zikFileToDelete.exists()) {
                    if (recursiveRemove(zikFileToDelete)) {
                        myLog("Deleted from Disk : [" + strPath + "]");
                        return true;
                    } else {
                        myLog("NOT Deleted from Disk : [" + strPath + "]");
                        return false;
                    }
                } else {
                    myLogE("file does not exist : [" + strPath + "]");
                    return false;
                }
            } catch (Exception e) {
                myLogE("Error remove ZikFile from Disk : [" + strPath + "] - " + e.getMessage());
                return false;
            }
        } else {
            myLogE("should not happen uri less than 5 chars for path [" + strPath + "]");
            return false;
        }
    }

    ////////////////////////////////////////////////////////
    private static final String TAG = "KanFiles";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }


}
