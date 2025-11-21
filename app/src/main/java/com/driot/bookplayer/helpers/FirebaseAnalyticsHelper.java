package com.driot.bookplayer.helpers;

import android.content.Context;
import android.os.Bundle;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import com.driot.bookplayer.BuildConfig;
import com.driot.bookplayer.imports.ImportJob;
import com.driot.bookplayer.objects.LoadBookTaskState;
import com.driot.bookplayer.utils.Tonio;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

public final class FirebaseAnalyticsHelper {

    private static final int MAX_FA_PARAM = 100;

    private static Context appContext;
    private static String appVersion;

    public static void init(Context context) {
        appVersion = BuildConfig.VERSION_NAME;
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

    // PLAY 1min

    public static void tellPlayFor1min(String elapsed_category, String playMode) {
        Bundle bundle = new Bundle();
        bundle.putString("elapsed_category", String.valueOf(elapsed_category));
        logBundleEvent( playMode + "_for_1min", bundle);
        bundle.putString("play_mode", String.valueOf(playMode));
        logBundleEvent("play_for_1min", bundle);
    }

    public static void tellDbKo(String err_type, int nbFatalError, int nbInvalid, int nbZikFiles, int nbRewritten, int nbStillBad, String masterMsg) {
        Bundle bundle = new Bundle();
        bundle.putString("err_type", String.valueOf(err_type));
        bundle.putString("ei_invalid", nbInvalid + "/" + nbZikFiles);
        bundle.putString("ei_fatal", nbFatalError + "/" + nbZikFiles);
        bundle.putString("ei_rewritten_inv", nbRewritten + "/" + nbInvalid + "/" + nbZikFiles);
        bundle.putString("ei_still_bad_inv", nbStillBad + "/" + nbInvalid + "/" + nbZikFiles);
        bundle.putString("err_master_msg", trimFA(String.valueOf(masterMsg)));
        logBundleEvent( "db_ko_" + err_type, bundle);
    }

    // IMPORT JOB

    public static void tellLoadBookFailed(ImportJob j) {
        logBundleEvent("load_book_failed", getImportJobBundle(j));
    }
    public static void tellLoadBookCancelled(ImportJob j) {
        logBundleEvent("load_book_cancelled", getImportJobBundle(j));
    }
    public static void tellLoadBookSuccess(ImportJob j) {
        logBundleEvent("load_book_success", getImportJobBundle(j));
    }
    private static Bundle getImportJobBundle(ImportJob j) {
        Bundle bundle = new Bundle();
        bundle.putString("originalUri", String.valueOf(j.originalUri));
        bundle.putString("taskName", String.valueOf(j.currentOperation));
        bundle.putString("progressText", String.valueOf(j.progressText));
        bundle.putBoolean("doDownload", j.doDownload);
        bundle.putString("extension", String.valueOf(j.fileExtension));
        bundle.putString("folderName", String.valueOf(j.futureFolderName));
        bundle.putString("errorText", String.valueOf(j.errorTextDev));
        bundle.putString("warningText", String.valueOf(j.warningText));
        return bundle;
    }

    // PLAY PROBLEM

    public static void tellAnalyticsLoadFileKO(String filePath, String playMode) {
        Bundle bundle = new Bundle();
        bundle.putString("playMode", String.valueOf(playMode));
        bundle.putString("filePath", String.valueOf(filePath));
        bundle.putString("fileName", Tonio.getFileNameFromPath(String.valueOf(filePath)));
        bundle.putString("extension", Tonio.getExtension(String.valueOf(filePath)));
        logBundleEvent("load_file_KO", bundle);
    }

    // AUTOMOTIVE

    public static void tellCarAutoPlay() {
        logEvent("car_auto_play");
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


    public static void tellAnalyticsPlaylistLoadFromStorage(Context context, String storageType, String playlist) {
        Bundle bundle = new Bundle();
        bundle.putString("context", context.getClass().getSimpleName());
        bundle.putString("storageType", String.valueOf(storageType));
        bundle.putString("playlist", String.valueOf(playlist));
        logBundleEvent("playlist_load_from_storage", bundle);
    }

    public static void tellAnalyticsWork(LoadBookTaskState s, boolean doDownload) {
        Bundle bundle = new Bundle();
        bundle.putString("originalUri", String.valueOf(s.originalUri));
        bundle.putString("originalFile", String.valueOf(s.originalFile));
        bundle.putString("extension", String.valueOf(s.fileExtension));
        bundle.putString("addToExistingFolder", String.valueOf (s.addToExistingFolderId>0));
        bundle.putString("folderName", String.valueOf (s.futureFolderName));
        bundle.putString("playType", String.valueOf (s.playType));
        bundle.putString("sourceLocation", String.valueOf(s.sourceLocation));
        bundle.putString("title", String.valueOf(s.title));
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
    public static void tellAnalyticsProxyLoad(String originalUri, String mode, boolean persistedPermission) {
        Bundle bundle = new Bundle();
        bundle.putString("originalUri", String.valueOf(originalUri));
        bundle.putString("mode", String.valueOf(mode));
        bundle.putString("persistedPermission", String.valueOf(persistedPermission));
        logBundleEvent("proxy_load", bundle);
    }
    public static void tellAnalyticsManualDownload(String fileUrl, String destinationFolder) {
        Bundle bundle = new Bundle();
        bundle.putString("fileUrl", String.valueOf(fileUrl));
        bundle.putString("fileName", Tonio.getFileNameFromUrl(String.valueOf(fileUrl)));
        bundle.putString("destinationFolder", String.valueOf(destinationFolder));
        logBundleEvent("just_get_it", bundle);
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

    public static void tellAnalyticsPlayAction(String actionName, String folderName) {
        Bundle bundle = new Bundle();
        bundle.putString("actionName", String.valueOf(actionName));
        bundle.putString("folderName", String.valueOf(folderName));
        logBundleEvent("play_action", bundle);
    }
    public static void tellAnalyticsEbookWorker(String extension) {
        Bundle bundle = new Bundle();
        bundle.putString("extension", String.valueOf(extension));
        logBundleEvent("ebook_worker", bundle);
    }
    public static void tellAnalyticsStartStreaming(String podcastName, String stream_url, String play_mode) {
        Bundle bundle = new Bundle();
        bundle.putString("podcastName", String.valueOf(podcastName));
        bundle.putString("stream_url", String.valueOf(stream_url));
        bundle.putString("play_mode", String.valueOf(play_mode));
        logBundleEvent("start_streaming", bundle);
    }

    public static void tellAnalyticsRadioTrending(String query, String lang, String country, String tag) {
        Bundle bundle = new Bundle();
        bundle.putString("query", String.valueOf(query));
        bundle.putString("language", String.valueOf(lang));
        bundle.putString("country", String.valueOf(country));
        bundle.putString("tag", String.valueOf(tag));
        logBundleEvent("radio_trending", bundle);
    }
    public static void tellAnalyticsRadioBy(String byWhat) {
        Bundle bundle = new Bundle();
        bundle.putString(byWhat, String.valueOf(byWhat));
        logBundleEvent("radio_by_" + byWhat, bundle);
    }

    public static void tellAnalyticsRadioSearch(String query, String lang, String country, String tag) {
        Bundle bundle = new Bundle();
        bundle.putString("query", String.valueOf(query));
        bundle.putString("language", String.valueOf(lang));
        bundle.putString("country", String.valueOf(country));
        bundle.putString("tag", String.valueOf(tag));
        logBundleEvent("radio_search", bundle);
    }

    public static void tellAnalyticsLogee(String customErrorTxt, String androidErrorText) {
        Bundle bundle = new Bundle();
        bundle.putString("customErrorTxt", String.valueOf(customErrorTxt));
        bundle.putString("androidErrorText", String.valueOf(androidErrorText));
        logBundleEvent("log_ee", bundle);
    }

    private static void logBundleEvent(String logName, Bundle bundle) {
        try {
            myLogD("Analytics logging - " + logName + " - " + bundle.toString());
            FirebaseAnalytics firebaseAnalytics = FirebaseAnalytics.getInstance(appContext);
            bundle.putString("app_version", appVersion);
            firebaseAnalytics.logEvent(logName, bundle);
        } catch (Exception e) {
            myLogEE(e, "Analytics logging - " + logName);
        }
    }

    static String trimFA(String s) {
        if (s == null) return null;
        if (s.length() <= MAX_FA_PARAM) return s;
        return s.substring(0, MAX_FA_PARAM - 1) + "…";
    }
}
