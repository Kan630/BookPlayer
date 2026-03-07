package com.driot.bookplayer.player;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public final class CarSignals {

    private static volatile long lastCarConnectElapsed = 0L;

    public static void markCarConnected() {
        lastCarConnectElapsed = android.os.SystemClock.elapsedRealtime();
        myLogI("car connected");
    }

    public static boolean withinCarConnectGrace(long ms) {
        long dt = android.os.SystemClock.elapsedRealtime() - lastCarConnectElapsed;
        return lastCarConnectElapsed != 0 && dt >= 0 && dt <= ms;
    }

}
