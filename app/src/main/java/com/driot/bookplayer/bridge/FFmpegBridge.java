package com.driot.bookplayer.bridge;

public class FFmpegBridge {
    static {
        System.loadLibrary("ffmpeg-jni");
    }

    public static native int runFFmpeg(String[] args);
    public static native int runFFprobe(String[] args);
}
