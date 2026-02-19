package com.driot.bookplayer.helpers;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import android.content.Context;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

/**
 * Helper class for Google Play Services information
 */
public class GoogleServicesHelper {

    /**
     * Checks if Google Play Services are available
     * 
     * @return true if available, false otherwise
     */
    public static boolean isGooglePlayServicesAvailable(Context context) {
        try {
            GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
            int resultCode = apiAvailability.isGooglePlayServicesAvailable(context);
            return resultCode == ConnectionResult.SUCCESS;
        } catch (Exception e) {
            myLogE("Error checking Play Services availability: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets a string describing the Google Play Services status
     */
    public static String getPlayServicesStatus(Context context) {
        try {
            GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
            int resultCode = apiAvailability.isGooglePlayServicesAvailable(context);
            if (resultCode == ConnectionResult.SUCCESS) {
                return "present";
            } else {
                return "Issue: " + apiAvailability.getErrorString(resultCode);
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Gets the Google Play Services version code
     * 
     * @return version code as string, or "Not present" if not found
     */
    public static String getPlayServicesVersion(Context context) {
        try {
            int versionCode = context.getPackageManager()
                    .getPackageInfo("com.google.android.gms", 0).versionCode;
            return String.valueOf(versionCode);
        } catch (Exception e) {
            return "Not present";
        }
    }

    /**
     * Logs Google Play Services information for debugging
     */
    public static void logPlayServicesInfo(Context context) {
        myLogD("Google Play Services: " + getPlayServicesStatus(context));
        myLogD("Google Play Services version: " + getPlayServicesVersion(context));
    }
}
