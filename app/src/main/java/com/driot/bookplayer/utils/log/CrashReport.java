package com.driot.bookplayer.utils.log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.InterruptedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Helpers for what KanLogger sends to Crashlytics. Analytics keeps its own 100-char trimFA (only redact() is shared).
 * - callerStack(): stack of a synthetic LoggedError without the logger frames, so each myLogEE call site
 *   becomes its own Crashlytics issue instead of all of them grouping under KanLogger.myLogEE.
 * - allow(): drops identical reports repeated within DEDUP_WINDOW_MS (Crashlytics keeps only 8 non-fatals
 *   per session, a per-second repeat would evict the useful ones).
 * - redact(): removes e-mail addresses (Gmail attachment URIs contain the account) before anything leaves the device.
 */
public final class CrashReport {

    private CrashReport() {
    }

    /** Crashlytics custom key values are capped at 1024 chars by the SDK, keep a margin. */
    static final int MAX_CRASHLYTICS_VALUE = 1000;

    private static final String LOG_PACKAGE = CrashReport.class.getPackage().getName() + ".";
    private static final long DEDUP_WINDOW_MS = 60_000;
    private static final int DEDUP_MAX_KEYS = 300;

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._+-]+(@|%40)[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /** key -> {lastReportedAt, suppressedSinceThen} */
    private static final Map<String, long[]> recent = new ConcurrentHashMap<>();

    /** Thrown-for-reporting only: gives myLogEE(null, ...) reports a recognizable type. */
    static final class LoggedError extends RuntimeException {
        LoggedError(String message) {
            super(message);
        }
    }

    @NonNull
    public static String redact(@Nullable String s) {
        if (s == null)
            return "null";
        if (s.indexOf('@') < 0 && !s.contains("%40"))
            return s;
        return EMAIL.matcher(s).replaceAll("<email>");
    }

    @NonNull
    public static String clip(@Nullable String s) {
        String r = redact(s);
        return r.length() <= MAX_CRASHLYTICS_VALUE ? r : r.substring(0, MAX_CRASHLYTICS_VALUE - 1) + "…";
    }

    /**
     * @return -1 if this key was already reported less than DEDUP_WINDOW_MS ago (skip it), otherwise the number
     *         of identical reports suppressed since the last one (0 usually).
     */
    static long allow(@NonNull String key) {
        long now = System.currentTimeMillis();
        long[] entry = recent.get(key);
        if (entry != null && now - entry[0] < DEDUP_WINDOW_MS) {
            entry[1]++;
            return -1;
        }
        if (recent.size() > DEDUP_MAX_KEYS)
            recent.clear();
        long suppressed = entry != null ? entry[1] : 0;
        recent.put(key, new long[] { now, 0 });
        return suppressed;
    }

    /** User/system cancellations: expected, not bugs (thread interrupted by a cancel, closed channel...). */
    static boolean isInterruption(@Nullable Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof InterruptedException || c instanceof InterruptedIOException
                    && !(c instanceof java.net.SocketTimeoutException)
                    || c instanceof ClosedByInterruptException)
                return true;
            if (c.getCause() == c)
                break;
        }
        return false;
    }

    /** Current stack minus VM frames and the logger's own frames: the first frame is the real caller. */
    @NonNull
    static StackTraceElement[] callerStack() {
        StackTraceElement[] all = Thread.currentThread().getStackTrace();
        int i = 0;
        while (i < all.length && isNoiseFrame(all[i]))
            i++;
        List<StackTraceElement> out = new ArrayList<>(all.length - i);
        for (; i < all.length; i++)
            out.add(all[i]);
        return out.toArray(new StackTraceElement[0]);
    }

    private static boolean isNoiseFrame(StackTraceElement e) {
        String cls = e.getClassName();
        if (cls.startsWith("dalvik.system.VMStack") || cls.equals("java.lang.Thread"))
            return true;
        if (!cls.startsWith(LOG_PACKAGE))
            return false;
        // Logger plumbing: KanLogger/CrashReport entirely, and the myLogEE/myToastEE-style wrappers of the
        // Logging* base classes (BaseActivity.myLogEE, LoggerStaticHelper.myLogEE...). A BaseActivity.onCreate
        // frame is a real caller and is kept.
        return isClass(cls, KanLogger.class) || isClass(cls, CrashReport.class) || e.getMethodName().startsWith("my");
    }

    /** Exact class or one of its inner/lambda classes (not a mere name prefix like CrashReportTest). */
    private static boolean isClass(String cls, Class<?> c) {
        return cls.equals(c.getName()) || cls.startsWith(c.getName() + "$");
    }
}
