package com.driot.bookplayer.helpers;

import android.content.Context;
import android.os.Bundle;

import com.driot.bookplayer.utils.KanLogger;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

public final class FirebaseAnalyticsHelper {
    private static Context appContext;

    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

//CRASHLYTICS

    public static void setCustomKeyCrashlytics(String strKey, String strValue) {
        KanLogger.myLogD("setCustomKeyCrashlytics : " + strKey + " = " + strValue);
        FirebaseCrashlytics.getInstance().setCustomKey(strKey, strValue);
    }

    public static void logCrashlytics(String strLog) {
        KanLogger.myLogD("logCrashlytics : " + strLog);
        FirebaseCrashlytics.getInstance().log(strLog);
    }

//ANALYTICS

    public static void logEvent(String event) {
        try {
            myLogD("Analytics logging - " + event);
            FirebaseAnalytics firebaseAnalytics = FirebaseAnalytics.getInstance(appContext);
            firebaseAnalytics.logEvent(event, null);
        } catch (Exception e) {
            myLogEE(e, "Analytics logging - " + event);
        }
    }

    public static void tellPlayFor1min() {
        logEvent("play_for_1min");
    }

    public static void tellCarOnChildren() {
        logEvent("car_on_children");
    }

    public static void tellCarOnRoot() {
        logEvent("car_on_root");
    }

    public static void tellCarOnPlayFromMediaId() {
        logEvent("car_on_play_from_media_id");
    }

    public static void tellCarSendCmd(String action) {
        Bundle bundle = new Bundle();
        bundle.putString("action", action);
        logBundleEvent("car_send_cmd", bundle);
    }

    public static void tellLoadBookFailed(String originalUri, String taskName, String errorText) {
        Bundle bundle = new Bundle();
        bundle.putString("originalUri", originalUri);
        bundle.putString("taskName", taskName);
        bundle.putString("errorText", errorText);
        logBundleEvent("load_book_failed", bundle);
    }

    public static void tellLoadBookCancelled(String originalUri, String taskName) {
        Bundle bundle = new Bundle();
        bundle.putString("originalUri", originalUri);
        bundle.putString("taskName", taskName);
        logBundleEvent("load_book_cancelled", bundle);
    }

    public static void tellLoadBookSuccess(String originalUri) {
        Bundle bundle = new Bundle();
        bundle.putString("originalUri", originalUri);
        logBundleEvent("load_book_success", bundle);
    }

    public static void tellAnalyticsPlaylistLoadFromStorage(Context context) {
        Bundle bundle = new Bundle();
        bundle.putString("context", context.getClass().getSimpleName());
        logBundleEvent("playlist_load_from_storage", bundle);
    }

    public static void tellAnalyticsWork(String originalUri) {
        Bundle bundle = new Bundle();
        bundle.putString("originalUri", originalUri);
        logBundleEvent("worker_start", bundle);
    }
    public static void tellAnalyticsManualLoad(String type, String extension, String sourceLocation, String originalFile) {
        Bundle bundle = new Bundle();
        bundle.putString("type", type);
        bundle.putString("extension", extension);
        bundle.putString("sourceLocation", sourceLocation);
        bundle.putString("originalFile", originalFile);
        logBundleEvent("manual_load", bundle);
    }
    public static void tellAnalyticsProxyLoad(String originalUri) {
        Bundle bundle = new Bundle();
        bundle.putString("originalUri", originalUri);
        logBundleEvent("proxy_load", bundle);
    }
    public static void tellAnalyticsManualDownload(String fileUrl, String destinationFolder) {
        Bundle bundle = new Bundle();
        bundle.putString("fileUrl", fileUrl);
        bundle.putString("destinationFolder", destinationFolder);
        logBundleEvent("manual_download", bundle);
    }

    public static void tellLibrivoxDownload(String title) {
        Bundle bundle = new Bundle();
        bundle.putString("title", title);
        logBundleEvent("librivox_download", bundle);
    }
    public static void tellLibrivoxSuccess(String title) {
        Bundle bundle = new Bundle();
        bundle.putString("title", title);
        logBundleEvent("librivox_success", bundle);
    }

    public static void tellAnalyticsLibrivoxSearch(String query, String lang) {
        Bundle bundle = new Bundle();
        bundle.putString("query", query);
        bundle.putString("language", lang);
        logBundleEvent("librivox_search", bundle);
    }
    public static void  tellAnalyticsLibrivoxTrending(String query, String lang) {
        Bundle bundle = new Bundle();
        bundle.putString("query", query);
        bundle.putString("language", lang);
        logBundleEvent("librivox_trending", bundle);
    }

    public static void tellAnalyticsPodcastSearch(String query, String lang) {
        Bundle bundle = new Bundle();
        bundle.putString("query", query);
        bundle.putString("language", lang);
        logBundleEvent("podcast_search", bundle);
    }
    public static void tellAnalyticsPodcastTrending(String query, String lang) {
        Bundle bundle = new Bundle();
        bundle.putString("query", query);
        bundle.putString("language", lang);
        logBundleEvent("podcast_trending", bundle);
    }
    public static void tellAnalyticsPodcastFavorite(String podcastName, String podcastLang) {
        Bundle bundle = new Bundle();
        bundle.putString("podcastName", podcastName);
        bundle.putString("language", podcastLang);
        logBundleEvent("podcast_favorite", bundle);
    }
    public static void tellAnalyticsPodcastAutoDownload(String podcastName, String podcastLang) {
        Bundle bundle = new Bundle();
        bundle.putString("podcastName", podcastName);
        bundle.putString("language", podcastLang);
        logBundleEvent("podcast_autodownload", bundle);
    }
    public static void tellAnalyticsPodcastRefresh(String podcast_title) {
        Bundle bundle = new Bundle();
        bundle.putString("podcast_title", podcast_title);
        logBundleEvent("podcast_refresh", bundle);
    }

    public static void tellAnalyticsPressPlay(String folderName) {
        Bundle bundle = new Bundle();
        bundle.putString("folderName", folderName);
        logBundleEvent("press_play", bundle);
    }
    public static void tellAnalyticsEbookWorker(String extension) {
        Bundle bundle = new Bundle();
        bundle.putString("extension", extension);
        logBundleEvent("ebook_worker", bundle);
    }
    public static void tellAnalyticsStartStreaming(String podcastName) {
        Bundle bundle = new Bundle();
        bundle.putString("folderName", podcastName);
        logBundleEvent("start_streaming", bundle);
    }



    public static void tellAnalyticsLogee(String customErrorTxt, String androidErrorText) {
        Bundle bundle = new Bundle();
        bundle.putString("customErrorTxt", customErrorTxt);
        bundle.putString("androidErrorText", androidErrorText);
        logBundleEvent("log_ee", bundle);
    }
    private static void logBundleEvent(String logName, Bundle bundle) {
        try {
            myLogD("Analytics logging - " + logName);
            FirebaseAnalytics firebaseAnalytics = FirebaseAnalytics.getInstance(appContext);
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
