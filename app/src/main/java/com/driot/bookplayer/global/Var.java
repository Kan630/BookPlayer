package com.driot.bookplayer.global;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 15/09/21
 */
public class Var {

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    public static final boolean LOG_LIFECYCLE_TRACE = true;
    //------------------------------------------------------------------------
    //------------------------------------------------------------------------

    public static final String ONLY_MIME = "audio/";

    public static final String  FOLDER_UNZIPPED = "unzipped";
    public static final String  FOLDER_ZIPPED = "zipped";
    public static final String  FOLDER_MP4 = "mp4_split";

    public static final int  ZIP_SIZE_MAX_COEF = 4;

    public static final String  FOLDER_DOWNLOAD = "download";
    public static final String  PATH_CHECK_AUTOTEST = "bookplayer/files/download";
    public static final String  PATH_CHECK_APPLICATION = "bookplayer/files/";

    public static final String  PATH_AUTOTEST_URL = "https://bookplayer.driot.com/autotest/";

    public static final String  AUTOTEST_FILE_01 = PATH_AUTOTEST_URL + "file_01.zip";
    public static final String  AUTOTEST_FILE_0100 = "https://archive.org/compress/sonnetsandsongs_2405_librivox/formats=64KBPS%20MP3&file=/sonnetsandsongs_2405_librivox.zip";

    public static final String  AUTOTEST_FILE_02 = PATH_AUTOTEST_URL + "file_02.zip";
    public static final String  AUTOTEST_FILE_03 = PATH_AUTOTEST_URL + "file_03.zip";

}
