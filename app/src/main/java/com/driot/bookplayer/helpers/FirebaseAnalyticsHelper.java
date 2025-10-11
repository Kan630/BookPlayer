package com.driot.bookplayer.helpers;

import android.content.Context;
import android.os.Bundle;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

public final class FirebaseAnalyticsHelper {
    private static Context appContext;

    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

//CRASHLYTICS

    public static void setCustomKeyCrashlytics(String strKey, String strValue) {
        myLogD("setCustomKeyCrashlytics : " + strKey + " = " + strValue);
        FirebaseCrashlytics.getInstance().setCustomKey(strKey, String.valueOf(strValue));
    }

    public static void logCrashlytics(String strLog) {
        myLogD("logCrashlytics : " + strLog);
        FirebaseCrashlytics.getInstance().log(String.valueOf(strLog));
    }

//ANALYTICS

    public static void logEvent(String event) {
        try {
            myLogD("Analytics logging - " + event);
            FirebaseAnalytics firebaseAnalytics = FirebaseAnalytics.getInstance(appContext);
            firebaseAnalytics.logEvent(String.valueOf(event), null);
        } catch (Exception e) {
            myLogEE(e, "Analytics logging - " + event);
        }
    }

    public static void tellPlayFor1min(String elapsed_category) {
        Bundle bundle = new Bundle();
        bundle.putString("elapsed_category", String.valueOf(elapsed_category));
        logBundleEvent("play_for_1min", bundle);
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
        bundle.putString("action", String.valueOf(action));
        logBundleEvent("car_send_cmd", bundle);
    }

    public static void tellLoadBookFailed(String originalUri, String taskName, String errorText, String extension, boolean doDownload) {
        Bundle bundle = new Bundle();
        bundle.putString("originalUri", String.valueOf(originalUri));
        bundle.putString("taskName", String.valueOf(taskName));
        bundle.putString("errorText", String.valueOf(errorText));
        bundle.putString("extension", String.valueOf(extension));
        bundle.putBoolean("doDownload", doDownload);
        logBundleEvent("load_book_failed", bundle);
    }

    public static void tellLoadBookCancelled(String originalUri, String taskName, String extension, boolean doDownload) {
        Bundle bundle = new Bundle();
        bundle.putString("originalUri", String.valueOf(originalUri));
        bundle.putString("taskName", String.valueOf(taskName));
        bundle.putString("extension", String.valueOf(extension));
        bundle.putBoolean("doDownload", doDownload);
        logBundleEvent("load_book_cancelled", bundle);
    }

    public static void tellLoadBookSuccess(String originalUri, String extension, boolean doDownload) {
        Bundle bundle = new Bundle();
        bundle.putString("originalUri", String.valueOf(originalUri));
        bundle.putString("extension", String.valueOf(extension));
        bundle.putBoolean("doDownload", doDownload);
        logBundleEvent("load_book_success", bundle);
    }

    public static void tellAnalyticsPlaylistLoadFromStorage(Context context) {
        Bundle bundle = new Bundle();
        bundle.putString("context", context.getClass().getSimpleName());
        logBundleEvent("playlist_load_from_storage", bundle);
    }

    public static void tellAnalyticsWork(String originalUri, String extension, boolean doDownload) {
        Bundle bundle = new Bundle();
        bundle.putString("originalUri", originalUri);
        bundle.putString("extension", String.valueOf(extension));
        bundle.putBoolean("doDownload", doDownload);
        logBundleEvent("worker_start", bundle);
    }
    public static void tellAnalyticsManualLoad(String type, String extension, String sourceLocation, String originalFile) {
        Bundle bundle = new Bundle();
        bundle.putString("type", String.valueOf(type));
        bundle.putString("extension", String.valueOf(extension));
        bundle.putString("sourceLocation", String.valueOf(sourceLocation));
        bundle.putString("originalFile", String.valueOf(originalFile));
        logBundleEvent("manual_load", bundle);
    }
    public static void tellAnalyticsProxyLoad(String originalUri) {
        Bundle bundle = new Bundle();
        bundle.putString("originalUri", String.valueOf(originalUri));
        logBundleEvent("proxy_load", bundle);
    }
    public static void tellAnalyticsManualDownload(String fileUrl, String destinationFolder) {
        Bundle bundle = new Bundle();
        bundle.putString("fileUrl", String.valueOf(fileUrl));
        bundle.putString("destinationFolder", String.valueOf(destinationFolder));
        logBundleEvent("manual_download", bundle);
    }

    public static void tellLibrivoxDownload(String title) {
        Bundle bundle = new Bundle();
        bundle.putString("title", String.valueOf(title));
        logBundleEvent("librivox_download", bundle);
    }
    public static void tellLibrivoxSuccess(String title) {
        Bundle bundle = new Bundle();
        bundle.putString("title", String.valueOf(title));
        logBundleEvent("librivox_success", bundle);
    }

    public static void tellAnalyticsLibrivoxSearch(String query, String lang) {
        Bundle bundle = new Bundle();
        bundle.putString("query", String.valueOf(query));
        bundle.putString("language", String.valueOf(lang));
        logBundleEvent("librivox_search", bundle);
    }
    public static void  tellAnalyticsLibrivoxTrending(String query, String lang) {
        Bundle bundle = new Bundle();
        bundle.putString("query", String.valueOf(query));
        bundle.putString("language", String.valueOf(lang));
        logBundleEvent("librivox_trending", bundle);
    }

    public static void tellAnalyticsPodcastSearch(String query, String lang) {
        Bundle bundle = new Bundle();
        bundle.putString("query", String.valueOf(query));
        bundle.putString("language", String.valueOf(lang));
        logBundleEvent("podcast_search", bundle);
    }
    public static void tellAnalyticsPodcastTrending(String query, String lang) {
        Bundle bundle = new Bundle();
        bundle.putString("query", String.valueOf(query));
        bundle.putString("language", String.valueOf(lang));
        logBundleEvent("podcast_trending", bundle);
    }
    public static void tellAnalyticsPodcastFavorite(String podcastName, String lang) {
        Bundle bundle = new Bundle();
        bundle.putString("podcastName", String.valueOf(podcastName));
        bundle.putString("language", String.valueOf(lang));
        logBundleEvent("podcast_favorite", bundle);
    }
    public static void tellAnalyticsPodcastAutoDownload(String podcastName, String lang) {
        Bundle bundle = new Bundle();
        bundle.putString("podcastName", String.valueOf(podcastName));
        bundle.putString("language", String.valueOf(lang));
        logBundleEvent("podcast_autodownload", bundle);
    }
    public static void tellAnalyticsPodcastRefresh(String podcast_title) {
        Bundle bundle = new Bundle();
        bundle.putString("podcast_title", String.valueOf(podcast_title));
        logBundleEvent("podcast_refresh", bundle);
    }

    public static void tellAnalyticsPressPlay(String folderName) {
        Bundle bundle = new Bundle();
        bundle.putString("folderName", String.valueOf(folderName));
        logBundleEvent("press_play", bundle);
    }
    public static void tellAnalyticsEbookWorker(String extension) {
        Bundle bundle = new Bundle();
        bundle.putString("extension", String.valueOf(extension));
        logBundleEvent("ebook_worker", bundle);
    }
    public static void tellAnalyticsStartStreaming(String podcastName) {
        Bundle bundle = new Bundle();
        bundle.putString("podcastName", String.valueOf(podcastName));
        logBundleEvent("start_streaming", bundle);
    }



    public static void tellAnalyticsLogee(String customErrorTxt, String androidErrorText) {
        Bundle bundle = new Bundle();
        bundle.putString("customErrorTxt", String.valueOf(customErrorTxt));
        bundle.putString("androidErrorText", String.valueOf(androidErrorText));
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

}
