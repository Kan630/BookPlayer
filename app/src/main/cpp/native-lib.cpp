#include <jni.h>
#include <string>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_driot_bookplayer_activities_MainActivity_stringFromJNI(JNIEnv* env, jobject /* this */) {
    std::string hello = "Hello from JNI -- Kan est un grand maitre de l'IA!";
    return env->NewStringUTF(hello.c_str());
}
