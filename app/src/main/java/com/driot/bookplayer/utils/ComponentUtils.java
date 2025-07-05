package com.driot.bookplayer.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import com.driot.bookplayer.activities.OpenWithProxyActivity;
import com.driot.bookplayer.activities.OpenWithProxyActivityAll;

public class ComponentUtils {
    public static void setOpenWithProxyEnabled(Context context, boolean enabled) {
        PackageManager pm = context.getPackageManager();
        ComponentName component = new ComponentName(context, OpenWithProxyActivity.class);
        int newState = enabled ?
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

        pm.setComponentEnabledSetting(
                component,
                newState,
                PackageManager.DONT_KILL_APP
        );
    }
    public static void setOpenWithProxyEnabled_all(Context context, boolean enabled) {
        PackageManager pm = context.getPackageManager();
        ComponentName component = new ComponentName(context, OpenWithProxyActivityAll.class);
        int newState = enabled ?
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

        pm.setComponentEnabledSetting(
                component,
                newState,
                PackageManager.DONT_KILL_APP
        );
    }

}
