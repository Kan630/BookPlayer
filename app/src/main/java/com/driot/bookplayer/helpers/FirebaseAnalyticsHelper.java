package com.driot.bookplayer.helpers;

import android.content.Context;
import android.os.Bundle;

import com.driot.bookplayer.utils.KanLogger;
import com.google.firebase.analytics.FirebaseAnalytics;

public final class FirebaseAnalyticsHelper {
    private static Context appContext;

    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    public static void sendEvent(String event) {
        try {
            myLogD("Analytics logging - " + event);
            FirebaseAnalytics firebaseAnalytics = FirebaseAnalytics.getInstance(appContext);
            firebaseAnalytics.logEvent(event, null);
        } catch (Exception e) {
            myLogEE(e, "Analytics logging - " + event);
        }
    }

    public static void tellPlayFor1min() {
        sendEvent("play_for_1min");
    }

    public static void tellLoadBookFailed(String originalUri, String taskName, String errorText) {
        Bundle bundle = new Bundle();
        bundle.putString("originalUri", originalUri);
        bundle.putString("taskName", taskName);
        bundle.putString("errorText", errorText);
        logThat("load_book_failed", bundle);
    }

    public static void tellLoadBookSuccess(String originalUri) {
        Bundle bundle = new Bundle();
        bundle.putString("originalUri", originalUri);
        logThat("load_book_success", bundle);
    }

    public static void tellAnalyticsPlaylistLoadFromStorage(Context context) {
        Bundle bundle = new Bundle();
        bundle.putString("context", context.getClass().getSimpleName());
        logThat("playlist_load_from_storage", bundle);
    }

    public static void tellAnalyticsWork(String originalUri) {
        Bundle bundle = new Bundle();
        bundle.putString("originalUri", originalUri);
        logThat("worker_start", bundle);
    }
    public static void tellAnalyticsManualLoad(String type, String extension, String sourceLocation, String originalFile) {
        Bundle bundle = new Bundle();
        bundle.putString("type", type);
        bundle.putString("extension", extension);
        bundle.putString("sourceLocation", sourceLocation);
        bundle.putString("originalFile", originalFile);
        logThat("manual_load", bundle);
    }
    public static void tellAnalyticsProxyLoad(String originalUri) {
        Bundle bundle = new Bundle();
        bundle.putString("originalUri", originalUri);
        logThat("proxy_load", bundle);
    }
    public static void tellAnalyticsManualDownload(String fileUrl, String destinationFolder) {
        Bundle bundle = new Bundle();
        bundle.putString("fileUrl", fileUrl);
        bundle.putString("destinationFolder", destinationFolder);
        logThat("manual_download", bundle);
    }

    public static void tellLibrivoxDownload(String title) {
        Bundle bundle = new Bundle();
        bundle.putString("title", title);
        logThat("librivox_download", bundle);
    }
    public static void tellAnalyticsLibrivoxSearch(String query, String lang) {
        Bundle bundle = new Bundle();
        bundle.putString("query", query);
        bundle.putString("language", lang);
        logThat("librivox_search", bundle);
    }
    public static void  tellAnalyticsLibrivoxTrending(String query, String lang) {
        Bundle bundle = new Bundle();
        bundle.putString("query", query);
        bundle.putString("language", lang);
        logThat("librivox_trending", bundle);
    }

    public static void tellAnalyticsPodcastSearch(String query, String lang) {
        Bundle bundle = new Bundle();
        bundle.putString("query", query);
        bundle.putString("language", lang);
        logThat("podcast_search", bundle);
    }
    public static void tellAnalyticsPodcastTrending(String query, String lang) {
        Bundle bundle = new Bundle();
        bundle.putString("query", query);
        bundle.putString("language", lang);
        logThat("podcast_trending", bundle);
    }
    public static void tellAnalyticsPodcastFavorite(String podcastName, String podcastLang) {
        Bundle bundle = new Bundle();
        bundle.putString("podcastName", podcastName);
        bundle.putString("language", podcastLang);
        logThat("podcast_favorite", bundle);
    }
    public static void tellAnalyticsPodcastAutoDownload(String podcastName, String podcastLang) {
        Bundle bundle = new Bundle();
        bundle.putString("podcastName", podcastName);
        bundle.putString("language", podcastLang);
        logThat("podcast_autodownload", bundle);
    }
    public static void tellAnalyticsPodcastRefresh(String podcast_title) {
        Bundle bundle = new Bundle();
        bundle.putString("podcast_title", podcast_title);
        logThat("podcast_refresh", bundle);
    }

    public static void tellAnalyticsPressPlay(String folderName) {
        Bundle bundle = new Bundle();
        bundle.putString("folderName", folderName);
        logThat("press_play", bundle);
    }
    public static void tellAnalyticsEbookWorker(String extension) {
        Bundle bundle = new Bundle();
        bundle.putString("extension", extension);
        logThat("ebook_worker", bundle);
    }
    public static void tellAnalyticsStartStreaming(String podcastName) {
        Bundle bundle = new Bundle();
        bundle.putString("folderName", podcastName);
        logThat("start_streaming", bundle);
    }



    public static void tellAnalyticsLogee(String customErrorTxt, String androidErrorText) {
        Bundle bundle = new Bundle();
        bundle.putString("customErrorTxt", customErrorTxt);
        bundle.putString("androidErrorText", androidErrorText);
        logThat("log_ee", bundle);
    }
    private static void logThat(String logName, Bundle bundle) {
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
