package com.driot.bookplayer.utils;

public class FFmpegWrapper {
    static {
        System.loadLibrary("avformat");
        System.loadLibrary("avcodec");
        System.loadLibrary("avutil");
        System.loadLibrary("swresample");
        System.loadLibrary("swscale");
        System.loadLibrary("avfilter");
        System.loadLibrary("avdevice");

        System.loadLibrary("native-lib"); // your own JNI wrapper
    }

    public native void initFFmpeg();
}