package com.driot.bookplayer.utils.log;

import com.driot.bookplayer.utils.KanLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static logging helper with the same API as LoggerHelper,
 * but usable from static contexts (utilities) without inheritance.
 *
 * Usage (no instance needed):
 *   LoggerStaticHelper.myLogD("Hello");
 *   LoggerStaticHelper.myLogEE(e, "Something failed");
 */
public final class LoggerStaticHelper {

    private LoggerStaticHelper() {} // no instances

    // Cache to avoid repeated stack walking for the same caller class
    private static final Map<String, String> TAG_CACHE = new ConcurrentHashMap<>();
    private static final String LIFECYCLE_LOG_TAG = "Lifecycle";

    // ---------------- Public logging wrappers (same names/signatures) ----------------

    public static void myLog(String str)           { KanLogger.myLog(getCallerTag(), str); }
    public static void myLogD(String str)          { KanLogger.myLogD(getCallerTag(), str); }
    public static void myLogI(String str)          { KanLogger.myLogI(getCallerTag(), str); }
    public static void myLogW(String str)          { KanLogger.myLogW(getCallerTag(), str); }
    public static void myLogE(String str)          { KanLogger.myLogE(getCallerTag(), str); }
    public static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, getCallerTag(), str); }
    public static void myLogInFile(String str)     { KanLogger.myLogInFile(getCallerTag(), str); }

    public static void myToast(String str)         { KanLogger.myToast(getCallerTag(), str); }
    public static void myToastE(String str)        { KanLogger.myToastE(getCallerTag(), str); }
    public static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, getCallerTag(), str); }
    public static void myToastLong(String str)     { KanLogger.myToastLong(getCallerTag(), str); }

    //if big loop and find clazz takes time
    public static void myLogD(Class<?> clazz, String msg) {
        KanLogger.myLogD("." + clazz.getSimpleName(), msg);
    }

    // ---------------- Lifecycle-specific wrappers (same names) ----------------

    public static void myLifecycleLog(String str) {
        if (KanLogger.LOG_LIFECYCLE_TRACE) {
            KanLogger.myLogD(LIFECYCLE_LOG_TAG, getCallerBracket() + str);
        }
    }

    public static void myLifecycleLogE(String str) {
        if (KanLogger.LOG_LIFECYCLE_TRACE) {
            KanLogger.myLogE(LIFECYCLE_LOG_TAG, getCallerBracket() + str);
        }
    }

    public static void myLifecycleLogEE(Throwable t, String str) {
        if (KanLogger.LOG_LIFECYCLE_TRACE) {
            KanLogger.myLogEE(t, LIFECYCLE_LOG_TAG, getCallerBracket() + str);
        }
    }

    // ---------------- Internals: caller detection + caching ----------------

    private static String getCallerTag() {
        StackTraceElement caller = findCaller();
        if (caller == null) return "BookPlayer";
        String fqcn = caller.getClassName();
        // cache ".SimpleName" to match your original format
        return TAG_CACHE.computeIfAbsent(fqcn, k -> "." + simpleNameOf(k));
    }

    private static String getCallerBracket() {
        StackTraceElement caller = findCaller();
        String simple = (caller == null) ? "Unknown" : simpleNameOf(caller.getClassName());
        return "[" + simple + "]: ";
    }

    /**
     * Walk the stack to the frame that called the public log method.
     * Stack layout usually:
     * 0 getStackTrace
     * 1 getCaller*
     * 2 our public log method (myLogD/myLogEE/…)
     * 3 actual caller (wanted)
     */
    private static StackTraceElement findCaller() {
        StackTraceElement[] s = Thread.currentThread().getStackTrace();
        // Defensive search: find the first frame *after* our class
        boolean seenThis = false;
        for (StackTraceElement e : s) {
            String cn = e.getClassName();
            if (cn.equals(LoggerStaticHelper.class.getName())) {
                seenThis = true;
                continue;
            }
            if (seenThis) {
                // skip java.lang.Thread and sun/… frames
                if (!cn.startsWith("java.lang.") && !cn.startsWith("sun.") ) {
                    return e;
                }
            }
        }
        return (s.length > 4) ? s[4] : null; // fallback heuristic
    }

    private static String simpleNameOf(String fqcn) {
        int idx = fqcn.lastIndexOf('.');
        return (idx >= 0 && idx < fqcn.length() - 1) ? fqcn.substring(idx + 1) : fqcn;
    }
}
