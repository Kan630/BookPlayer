package com.driot.bookplayer.player;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.MediaSessionManager;

public final class MediaCallerHelper {

    private static final long CAR_GRACE_MS = 10_000L;

    private MediaCallerHelper() {}

    @Nullable
    public static MediaSessionManager.RemoteUserInfo getCallerInfo(MediaBrowserServiceCompat service) {
        try {
            return service.getCurrentBrowserInfo();
        } catch (Throwable t) {
            return null; // safe fallback for pre-P / compat libs
        }
    }

    public static boolean isAndroidAuto(@Nullable MediaSessionManager.RemoteUserInfo info) {
        return isPkg(info, "com.google.android.projection.gearhead")      // projected AA
                || isPkg(info, "com.android.car.media")                        // AAOS (AOSP)
                || isPkg(info, "com.google.android.apps.automotive.media")     // AAOS (GAS)
                || CarSignals.withinCarConnectGrace(CAR_GRACE_MS);
    }

    public static boolean isOwnApp(Context ctx, @Nullable MediaSessionManager.RemoteUserInfo info) {
        String me = ctx.getPackageName();
        return isPkg(info, me) || isPkg(info, me + ".debug");
    }

    public static String describeCaller(Context ctx, @Nullable MediaSessionManager.RemoteUserInfo info) {
        if (isAndroidAuto(info)) return "AndroidAuto";
        if (isOwnApp(ctx, info)) return "AppUI";
        if (isPkg(info, "com.google.android.googlequicksearchbox")) return "Assistant";
        if (isPkg(info, "com.android.systemui") || isPkg(info, "android")) return "System";
        return info != null
                ? info.getPackageName()
                : (CarSignals.withinCarConnectGrace(CAR_GRACE_MS) ? "AndroidAuto?" : "unknown");
    }

    private static boolean isPkg(@Nullable MediaSessionManager.RemoteUserInfo info, String pkg) {
        return info != null && pkg.equals(info.getPackageName());
    }
}
