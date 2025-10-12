package com.driot.bookplayer.player;

import com.driot.bookplayer.utils.log.KanLogger;

public final class CarSignals {

    private static volatile long lastCarConnectElapsed = 0L;

    public static void markCarConnected() {
        lastCarConnectElapsed = android.os.SystemClock.elapsedRealtime();
        myLog("car connect");
    }

    public static boolean withinCarConnectGrace(long ms) {
        long dt = android.os.SystemClock.elapsedRealtime() - lastCarConnectElapsed;
        return lastCarConnectElapsed != 0 && dt >= 0 && dt <= ms;
    }

    ////////////////////////////////////////////////////////
    private static final String TAG = "CarSignals";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }
}
