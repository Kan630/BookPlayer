package com.driot.bookplayer.helpers;

import android.content.Context;
import android.os.Bundle;

import com.driot.bookplayer.utils.KanLogger;
import com.google.firebase.analytics.FirebaseAnalytics;

public class FirebaseAnalyticsHelper {


    public static void tellAnalyticsWork(Context context, String originalUri) {
        Bundle bundle = new Bundle();
        bundle.putString("originalUri", originalUri);
        logThat(context, "worker_start", bundle);
    }

    public static void tellAnalyticsManualLoad(Context context, String type, String extension, String sourceLocation, String originalFile) {
        Bundle bundle = new Bundle();
        bundle.putString("type", type);
        bundle.putString("extension", extension);
        bundle.putString("sourceLocation", sourceLocation);
        bundle.putString("originalFile", originalFile);
        logThat(context, "manual_load", bundle);
    }
    public static void tellAnalyticsManualDownload(Context context, String fileUrl, String destinationFolder, long alreadyDownloaded) {
        Bundle bundle = new Bundle();
        bundle.putString("fileUrl", fileUrl);
        bundle.putString("destinationFolder", destinationFolder);
        bundle.putLong("alreadyDownloaded", alreadyDownloaded);
        logThat(context, "manual_load", bundle);
    }
    public static void tellAnalyticsLibrivoxSearch(Context context, String query, String lang) {
        Bundle bundle = new Bundle();
        bundle.putString("query", query);
        bundle.putString("language", lang);
        logThat(context, "librivox_search", bundle);
    }
    public static void  tellAnalyticsLibrivoxTrending(Context context, String query, String lang) {
        Bundle bundle = new Bundle();
        bundle.putString("query", query);
        bundle.putString("language", lang);
        logThat(context, "librivox_trending", bundle);
    }
    public static void tellAnalyticsPodcastSearch(Context context, String query, String lang) {
        Bundle bundle = new Bundle();
        bundle.putString("query", query);
        bundle.putString("language", lang);
        logThat(context, "podcast_search", bundle);
    }
    public static void tellAnalyticsPodcastFavorite(Context context, String podcastName, String podcastLang) {
        Bundle bundle = new Bundle();
        bundle.putString("podcastName", podcastName);
        bundle.putString("language", podcastLang);
        logThat(context, "podcast_favorite", bundle);
    }

    private static void logThat(Context context, String logName, Bundle bundle) {
        try {
            myLogD("Analytics logging - " + logName);
            FirebaseAnalytics firebaseAnalytics = FirebaseAnalytics.getInstance(context);
            firebaseAnalytics.logEvent(logName, bundle);
        } catch (Exception e) {
            myLogEE(e, "Analytics logging - " + logName);
        }
    }

    ////////////////////////////////////////////////////////
    private static final String TAG = "AnalyticsHelper";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }
}
