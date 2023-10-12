package com.driot.tonylib;

import java.nio.charset.StandardCharsets;

import static com.driot.tonylib.KanLogger.myLogE;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 10/12/20
 */

public class TonioCommonStuff {


    public static String deleteExtension(String fileName) {
        if (fileName.lastIndexOf(".") > 0) {
            return fileName.substring(0,fileName.lastIndexOf("."));
        } else {
            return fileName;
        }
    }

    public static String extractPath(String fullFilePath) {
        if (fullFilePath.lastIndexOf("/") > 0) {
            return fullFilePath.substring(0,fullFilePath.lastIndexOf("/"));
        } else {
            return fullFilePath;
        }
    }

    public static String extractName(String fullFilePath) {
        if (fullFilePath.lastIndexOf("/") > 0) {
            return fullFilePath.substring(fullFilePath.lastIndexOf("/")+1);
        } else {
            return fullFilePath;
        }
    }

    public static String getExtension(String fileName) {
        if (fileName.lastIndexOf(".") > 0) {
            return fileName.substring(fileName.lastIndexOf(".") + 1);
        } else {
            return "";
        }
    }

    public static String MD5(String md5) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            StringBuilder sb = new StringBuilder();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {   // > 16
                byte[] array;
                array = md.digest(md5.getBytes(StandardCharsets.UTF_8));
                for (byte b : array) {
                    sb.append(Integer.toHexString((b & 0xFF) | 0x100).substring(1, 3));
                }
            } else {
                return null;
            }
            return sb.toString();
        } catch (Exception e) {
            myLogE("MD5 failed");
        }
        return null;
    }
}
