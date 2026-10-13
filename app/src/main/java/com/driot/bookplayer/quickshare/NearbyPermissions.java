package com.driot.bookplayer.quickshare;

import android.os.Build;

/**
 * When Quick Share (Nearby Connections) still needs a location permission.
 *
 * <p>Nearby Connections requires ACCESS_FINE_LOCATION only up to Android 12L (API 32); from
 * API 33 it discovers over NEARBY_WIFI_DEVICES plus the BLUETOOTH_* permissions instead. The
 * manifest caps both location permissions at {@code maxSdkVersion="32"} for that reason - Play
 * reviews every release that asks for location, which is what kept BookPlayer updates in review
 * for days.
 *
 * <p>So this must agree with the manifest: requesting a permission the manifest does not declare
 * is denied immediately and without a dialog, which would leave the share screen waiting for a
 * permission it can never get. Every caller asks here rather than testing the SDK itself.
 */
public final class NearbyPermissions {

    /** Last Android version whose Nearby Connections needs a location permission. */
    public static final int LAST_SDK_NEEDING_LOCATION = 32;

    public static boolean needsLocationPermission() {
        return Build.VERSION.SDK_INT <= LAST_SDK_NEEDING_LOCATION;
    }

    private NearbyPermissions() {
    }
}
