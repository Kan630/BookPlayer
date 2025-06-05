// ffmpeg-jni.c
// This file provides JNI wrappers for FFmpeg CLI using ffmpeg_main and ffprobe_main

#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>

#define LOG_TAG "FFmpegJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Declare ffmpeg and ffprobe main functions from FFmpeg sources
extern int ffmpeg_main(int argc, char** argv);
extern int ffprobe_main(int argc, char** argv);

// Helper to convert Java string array to C string array
char** convertJavaStringArray(JNIEnv* env, jobjectArray javaArgs, int* argcOut) {
    jsize argc = (*env)->GetArrayLength(env, javaArgs);
    char** argv = (char**)malloc(sizeof(char*) * argc);

    for (int i = 0; i < argc; ++i) {
        jstring javaString = (jstring)(*env)->GetObjectArrayElement(env, javaArgs, i);
        const char* utfString = (*env)->GetStringUTFChars(env, javaString, 0);
        argv[i] = strdup(utfString);
        (*env)->ReleaseStringUTFChars(env, javaString, utfString);
        (*env)->DeleteLocalRef(env, javaString);
    }
    *argcOut = argc;
    return argv;
}

void freeArgv(char** argv, int argc) {
    for (int i = 0; i < argc; ++i) {
        free(argv[i]);
    }
    free(argv);
}

JNIEXPORT jint JNICALL Java_com_driot_bookplayer_bridge_FFmpegBridge_runFFmpeg(JNIEnv* env, jclass clazz, jobjectArray javaArgs) {
    int argc;
    char** argv = convertJavaStringArray(env, javaArgs, &argc);
    LOGI("Running ffmpeg with %d arguments", argc);
    int result = ffmpeg_main(argc, argv);
    freeArgv(argv, argc);
    return result;
}

JNIEXPORT jint JNICALL Java_com_driot_bookplayer_bridge_FFmpegBridge_runFFprobe(JNIEnv* env, jclass clazz, jobjectArray javaArgs) {
    int argc;
    char** argv = convertJavaStringArray(env, javaArgs, &argc);
    LOGI("Running ffprobe with %d arguments", argc);
    int result = ffprobe_main(argc, argv);
    freeArgv(argv, argc);
    return result;
}
