package com.driot.bookplayer.utils;

public class LoggerHelper {

    private final String tag;
    private final String tagBracket;
    private final String logTag = "Lifecycle";

    public LoggerHelper(Class<?> clazz) {
        this.tag = "." + clazz.getSimpleName();
        this.tagBracket = "[" + clazz.getSimpleName() + "]: ";
    }

    // Public logging wrappers
    public void log(String str) { KanLogger.myLog(tag, str); }
    public void logD(String str) { KanLogger.myLogD(tag, str); }
    public void logI(String str) { KanLogger.myLogI(tag, str); }
    public void logW(String str) { KanLogger.myLogW(tag, str); }
    public void logE(String str) { KanLogger.myLogE(tag, str); }
    public void logEE(Throwable t, String str) { KanLogger.myLogEE(t, tag, str); }
    public void logInFile(String str) { KanLogger.myLogInFile(tag, str); }

    public void toast(String str) { KanLogger.myToast(tag, str); }
    public void toastE(String str) { KanLogger.myToastE(tag, str); }
    public void toastEE(Throwable t, String str) { KanLogger.myToastEE(t, tag, str); }
    public void longToast(String str) { KanLogger.myLongToast(tag, str); }

    // Lifecycle-specific internal log (optional)
    public void lifecycleLog(String str) {
        if (KanLogger.LOG_LIFECYCLE_TRACE) KanLogger.myLogD(logTag, tagBracket + str);
    }

    public void lifecycleLogE(String str) {
        if (KanLogger.LOG_LIFECYCLE_TRACE) KanLogger.myLogE(logTag, tagBracket + str);
    }

    public void lifecycleLogEE(Throwable t, String str) {
        if (KanLogger.LOG_LIFECYCLE_TRACE) KanLogger.myLogEE(t, logTag, tagBracket + str);
    }
}
