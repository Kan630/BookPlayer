package com.driot.bookplayer.utils.log;

import com.driot.bookplayer.utils.KanLogger;

/**
 * not used right now, because I would need to add this line at the top od each activity/service/adapter
 *    private final LoggerHelper log = new LoggerHelper(this);
 * and furthermore prefix all my call to myLog => logger.myLog
 * ..
 * so for now using inheritance with Services and Activities, and old school hardcoding with others
 * and furthermore prefix all my call to myLog => logger.myLog
 */

public class LoggerHelper {
/*
    private final String tag;
    private final String tagBracket;
    private final String logTag = "Lifecycle";

    public LoggerHelper(Class<?> clazz) {
        this.tag = "." + clazz.getSimpleName();
        this.tagBracket = "[" + clazz.getSimpleName() + "]: ";
    }

    // Public logging wrappers
    public void myLog(String str) { KanLogger.myLog(tag, str); }
    public void myLogD(String str) { KanLogger.myLogD(tag, str); }
    public void myLogI(String str) { KanLogger.myLogI(tag, str); }
    public void myLogW(String str) { KanLogger.myLogW(tag, str); }
    public void myLogE(String str) { KanLogger.myLogE(tag, str); }
    public void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, tag, str); }
    public void myLogInFile(String str) { KanLogger.myLogInFile(tag, str); }

    public void myToast(String str) { KanLogger.myToast(tag, str); }
    public void myToastE(String str) { KanLogger.myToastE(tag, str); }
    public void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, tag, str); }
    public void myLongToast(String str) { KanLogger.myLongToast(tag, str); }

    // Lifecycle-specific internal log (optional)
    public void myLifecycleLog(String str) {
        if (KanLogger.LOG_LIFECYCLE_TRACE) KanLogger.myLogD(logTag, tagBracket + str);
    }

    public void myLifecycleLogE(String str) {
        if (KanLogger.LOG_LIFECYCLE_TRACE) KanLogger.myLogE(logTag, tagBracket + str);
    }

    public void myLifecycleLogEE(Throwable t, String str) {
        if (KanLogger.LOG_LIFECYCLE_TRACE) KanLogger.myLogEE(t, logTag, tagBracket + str);
    }

 */
}
