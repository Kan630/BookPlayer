package com.driot.bookplayer.helpers;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import com.driot.bookplayer.BuildConfig;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.imports.ImportBookTaskState;
import com.driot.bookplayer.imports.ImportJob;
import com.driot.bookplayer.utils.Tonio;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.util.Locale;

public final class FirebaseAnalyticsHelper {

    private static final int MAX_FA_PARAM = 100;

    private static Context appContext;

    private static String appVersion;
    private static String aaa;
    private static String bbb;

    public static void init(Context context) {
        try {
            appContext = context.getApplicationContext();
            appVersion = String.valueOf(BuildConfig.VERSION_CODE);

            String sdkVersion = String.valueOf(Build.VERSION.SDK_INT);
            String androidVersion = Build.VERSION.RELEASE;
            String countryFromLocale = Locale.getDefault().getCountry();
            long installTimestamp = Pref.getFirstOpenTimeStamp(appContext);
            long days = installTimestamp > 0
                    ? (System.currentTimeMillis() - installTimestamp) / (24 * 60 * 60 * 1000L)
                    : 0;
            String daysSinceInstall = String.valueOf(days);
            aaa = androidVersion + "-" + appVersion + "-" + countryFromLocale;
            bbb = sdkVersion + "-" + appVersion + "-" + daysSinceInstall + "-" + countryFromLocale;

            setCustomKeyCrashlytics("app_version", appVersion);
            setCustomKeyCrashlytics("sdk_version", sdkVersion);
            setCustomKeyCrashlytics("first_open_date", Pref.getFirstOpenDate());
            setCustomKeyCrashlytics("total_ms_played", Tonio.formatTime(Pref.getTotalMsPlayed(), false));
        } catch (Exception e) {
            myLogE("FirebaseAnalyticsHelper init crash : " + e.getMessage());
        }
    }

//CRASHLYTICS

    public static void setCustomKeyCrashlytics(String strKey, String strValue) {
        myLogD("setCustomKeyCrashlytics : " + strKey + " = " + strValue);
        FirebaseCrashlytics.getInstance().setCustomKey(strKey, trimFA(strValue));
    }

    public static void logCrashlytics(String strLog) {
        myLogD("logCrashlytics : " + strLog);
        FirebaseCrashlytics.getInstance().log(trimFA(strLog));
    }

//ANALYTICS

    public static void logEvent(String event) {
        try {
            myLogD("Analytics logging - " + event);
            FirebaseAnalytics firebaseAnalytics = FirebaseAnalytics.getInstance(appContext);
            firebaseAnalytics.logEvent(trimFA(event), null);
        } catch (Exception e) {
            myLogEE(e, "Analytics logging - " + event);
        }
    }

    // PLAY 1min

    public static void tellPlayFor1min(String elapsed_category, String playMode, String extension) {
        Bundle bundle = new Bundle();
        bundle.putString("elapsed_category", trimFA(elapsed_category));
        bundle.putString("extension", trimFA(extension));
        logBundleEvent( playMode + "_for_1min", bundle);
        bundle.putString("play_mode", trimFA(playMode));
        logBundleEvent("play_for_1min", bundle);
    }

    public static void tellDbKo(String err_type, int nbFatalError, int nbInvalid, int nbZikFiles, int nbRewritten, int nbStillBad, String masterMsg) {
        Bundle bundle = new Bundle();
        bundle.putString("err_type", trimFA(err_type));
        bundle.putString("ei_invalid", nbInvalid + "/" + nbZikFiles);
        bundle.putString("ei_fatal", nbFatalError + "/" + nbZikFiles);
        bundle.putString("ei_rewritten_inv", nbRewritten + "/" + nbInvalid + "/" + nbZikFiles);
        bundle.putString("ei_still_bad_inv", nbStillBad + "/" + nbInvalid + "/" + nbZikFiles);
        bundle.putString("err_master_msg", trimFA(masterMsg));
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
        bundle.putString("originalUri", trimFA(j.originalUri));
        bundle.putString("taskName", trimFA(j.currentOperation));
        bundle.putString("progressText", trimFA(j.progressText));
        bundle.putBoolean("doDownload", j.doDownload);
        bundle.putString("extension", trimFA(j.fileExtension));
        bundle.putString("folderName", trimFA(j.futureFolderName));
        bundle.putString("errorText", trimFA(j.errorTextDev));
        bundle.putString("warningText", trimFA(j.warningText));
        return bundle;
    }

    // PLAY PROBLEM

    public static void tellAnalyticsLoadFileKO(String filePath, String playMode) {
        Bundle bundle = new Bundle();
        bundle.putString("playMode", trimFA(playMode));
        bundle.putString("filePath", trimFA(filePath));
        bundle.putString("fileName", Tonio.getFileNameFromPath(trimFA(filePath)));
        bundle.putString("extension", Tonio.getExtension(trimFA(filePath)));
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
        bundle.putString("action", trimFA(action));
        logBundleEvent("car_send_cmd", bundle);
    }


    public static void tellAnalyticsPlaylistLoadFromStorage(Context context, String storageType, String playlist) {
        Bundle bundle = new Bundle();
        bundle.putString("context", context.getClass().getSimpleName());
        bundle.putString("storageType", trimFA(storageType));
        bundle.putString("playlist", trimFA(playlist));
        logBundleEvent("playlist_load_from_storage", bundle);
    }

    public static void tellAnalyticsWork(ImportBookTaskState s, boolean doDownload) {
        Bundle bundle = new Bundle();
        bundle.putString("originalUri", trimFA(String.valueOf(s.originalUri)));
        bundle.putString("originalFile", trimFA(s.originalFile));
        bundle.putString("extension", trimFA(s.fileExtension));
        bundle.putString("addToExistingFolder", String.valueOf (s.addToExistingFolderId>0));
        bundle.putString("folderName", trimFA (s.futureFolderName));
        bundle.putString("playType", trimFA (s.playType));
        bundle.putString("sourceLocation", trimFA(s.sourceLocation));
        bundle.putString("title", trimFA(s.title));
        bundle.putBoolean("doDownload", doDownload);
        logBundleEvent("worker_start", bundle);
    }
    public static void tellAnalyticsManualLoad(String type, String extension, String sourceLocation, String originalFile) {
        Bundle bundle = new Bundle();
        bundle.putString("type", trimFA(type));
        bundle.putString("extension", trimFA(extension));
        bundle.putString("sourceLocation", trimFA(sourceLocation));
        bundle.putString("originalFile", trimFA(originalFile));
        logBundleEvent("manual_load", bundle);
    }
    public static void tellAnalyticsProxyLoad(String originalUri, String mode, boolean persistedPermission) {
        Bundle bundle = new Bundle();
        bundle.putString("originalUri", trimFA(originalUri));
        bundle.putString("mode", trimFA(mode));
        bundle.putString("persistedPermission", String.valueOf(persistedPermission));
        logBundleEvent("proxy_load", bundle);
    }
    public static void tellAnalyticsManualDownload(String fileUrl, String destinationFolder) {
        Bundle bundle = new Bundle();
        bundle.putString("fileUrl", trimFA(fileUrl));
        bundle.putString("fileName", trimFA(Tonio.getFileNameFromUrl(String.valueOf(fileUrl))));
        bundle.putString("destinationFolder", String.valueOf(destinationFolder));
        logBundleEvent("just_get_it", bundle);
    }

    public static void tellEbookDownloadFromGutendex(String title) {
        Bundle bundle = new Bundle();
        bundle.putString("title", trimFA(title));
        logBundleEvent("Gutendex_download", bundle);
    }

    public static void tellLibrivoxDownload(String title) {
        Bundle bundle = new Bundle();
        bundle.putString("title", trimFA(title));
        logBundleEvent("librivox_download", bundle);
    }
    public static void tellLibrivoxSuccess(String title) {
        Bundle bundle = new Bundle();
        bundle.putString("title", trimFA(title));
        logBundleEvent("librivox_success", bundle);
    }

    public static void tellAnalyticsLibrivoxSearch(String query, String lang) {
        Bundle bundle = new Bundle();
        bundle.putString("query", trimFA(query));
        bundle.putString("language", trimFA(lang));
        logBundleEvent("librivox_search", bundle);
    }
    public static void tellAnalyticsGutendexSearch(String query, String lang) {
        Bundle bundle = new Bundle();
        bundle.putString("query", trimFA(query));
        bundle.putString("language", trimFA(lang));
        logBundleEvent("Gutendex_search", bundle);
    }

    public static void tellAnalyticsLibrivoxQuickList(String query, String lang, String type) {
        Bundle bundle = new Bundle();
        bundle.putString("query", trimFA(query));
        bundle.putString("language", trimFA(lang));
        bundle.putString("type", trimFA(type));
        logBundleEvent("librivox_quick_list", bundle);
    }

    public static void tellAnalyticsLibrivoxBy(String byWhat, String lang) {
        Bundle bundle = new Bundle();
        bundle.putString("by", trimFA(byWhat));
        bundle.putString("language", trimFA(lang));
        logBundleEvent("librivox_by_" + byWhat, bundle);
    }

    public static void tellAnalyticsPodcastSearch(String query, String lang) {
        Bundle bundle = new Bundle();
        bundle.putString("query", trimFA(query));
        bundle.putString("language", trimFA(lang));
        logBundleEvent("podcast_search", bundle);
    }
    public static void tellAnalyticsPodcastTrending(String query, String lang) {
        Bundle bundle = new Bundle();
        bundle.putString("query", trimFA(query));
        bundle.putString("language", trimFA(lang));
        logBundleEvent("podcast_trending", bundle);
    }
    public static void tellAnalyticsPodcastFavorite(String podcastName, String lang) {
        Bundle bundle = new Bundle();
        bundle.putString("podcastName", trimFA(podcastName));
        bundle.putString("language", trimFA(lang));
        logBundleEvent("podcast_favorite", bundle);
    }
    public static void tellAnalyticsPodcastAutoDownload(String podcastName, String lang) {
        Bundle bundle = new Bundle();
        bundle.putString("podcastName", trimFA(podcastName));
        bundle.putString("language", trimFA(lang));
        logBundleEvent("podcast_autodownload", bundle);
    }
    public static void tellAnalyticsPodcastRefresh(String podcast_title) {
        Bundle bundle = new Bundle();
        bundle.putString("podcast_title", trimFA(podcast_title));
        logBundleEvent("podcast_refresh", bundle);
    }

    public static void tellAnalyticsPlayAction(String actionName, String folderName) {
        Bundle bundle = new Bundle();
        bundle.putString("actionName", trimFA(actionName));
        bundle.putString("folderName", trimFA(folderName));
        logBundleEvent("play_action", bundle);
    }
    public static void tellAnalyticsEbookWorker(String extension, String sourceLocation) {
        Bundle bundle = new Bundle();
        bundle.putString("extension", trimFA(extension));
        bundle.putString("sourceLocation", trimFA(sourceLocation));
        logBundleEvent("ebook_worker", bundle);
    }
    public static void tellAnalyticsStartStreaming(String stream_name, String stream_url, String play_mode) {
        Bundle bundle = new Bundle();
        bundle.putString("stream_name", trimFA(stream_name));
        bundle.putString("stream_url", trimFA(stream_url));
        bundle.putString("play_mode", trimFA(play_mode));
        logBundleEvent("start_streaming", bundle);
    }

    public static void tellAnalyticsRadioBy(String byWhat) {
        Bundle bundle = new Bundle();
        logBundleEvent("radio_by_" + byWhat, bundle);
    }

    public static void tellAnalyticsRadioSearch(String query, String lang, String country, String tag) {
        Bundle bundle = new Bundle();
        bundle.putString("query", trimFA(query));
        bundle.putString("language", trimFA(lang));
        bundle.putString("country", trimFA(country));
        bundle.putString("tag", trimFA(tag));
        logBundleEvent("radio_search", bundle);
    }

    public static void tellAnalyticsLogee(String customErrorTxt, String androidErrorText, String from) {
        Bundle bundle = new Bundle();
        bundle.putString("from", trimFA(from));
        bundle.putString("customErrorTxt", trimFA(customErrorTxt));
        bundle.putString("androidErrorText", trimFA(androidErrorText));
        logBundleEvent("log_ee", bundle);
    }

    private static void logBundleEvent(String logName, Bundle bundle) {
        try {
            myLogD("Analytics logging - " + logName + " - " + bundle.toString());
            FirebaseAnalytics firebaseAnalytics = FirebaseAnalytics.getInstance(appContext);
            bundle.putString("app_version", appVersion);
            bundle.putString("aaa", aaa);
            bundle.putString("bbb", bbb);
            firebaseAnalytics.logEvent(logName, bundle);
        } catch (Exception e) {
            myLogE("Analytics logging - " + logName);
        }
    }

    static String trimFA(String s) {
        s = String.valueOf(s);
        if (s.length() <= MAX_FA_PARAM) return s;
        return s.substring(0, MAX_FA_PARAM - 1) + "…";
    }
}
