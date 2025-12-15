package com.driot.bookplayer.utils;

public class NetworkStatus {

    public final boolean hasInternet;
    public final long offlineSinceMillis;

    public NetworkStatus(boolean hasInternet, long offlineSinceMillis) {
        this.hasInternet = hasInternet;
        this.offlineSinceMillis = offlineSinceMillis;
    }
}
