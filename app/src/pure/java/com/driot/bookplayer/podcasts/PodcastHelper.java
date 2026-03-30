package com.driot.bookplayer.podcasts;

import android.content.Context;
import android.content.Intent;

import static com.driot.bookplayer.global.Var.PODCAST_INDEX_ORG_SINCE;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.BackupManager;
import com.driot.bookplayer.db.BackupManager;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.NetworkHelper;

import java.util.List;

public class PodcastHelper {

    public static void cancelAutoDownload(Context c, long folderId) {
        myLog("stub!");
    }

    public static void deleteEpisode(long id, Context context) {
        myLog("stub!");
    }

    @androidx.annotation.Nullable
    public static String getPodcastOriginalCoverPath(Context context, long folderId) {
        return null;
    }

    @androidx.annotation.Nullable
    public static String getPodcastOriginalCoverUrl(Context context, long folderId) {
        return null;
    }

    public static void handlePodcastImages(Context context, long currentTime) {
        myLog("stub!");
    }

    public static void startPlayOpenPodcast(Folder folder, Context context) {
        myLog("stub!");
    }

    public static List<ZikFile> getPodcastZikFiles(Folder folder, Context context, boolean bool) {
        return null;
    }

    public static boolean playStreamIfKnownPodcast(Context context, String url) {
        return false;
    }

    public static void deletePodcastFolder(long folderId, Context context) {
        myLog("stub!");
    }

    public static void openPodcastEpisodeActivityFromActivity(Folder folder, Context context) {
        myLog("stub!");
    }

    public static void doAutoDownloadAndDelete(Context context) {
        myLog("stub!");
    }

    public static boolean backupDataHasPodcasts(BackupManager.BackupData data) {
        return false;
    }

    public static void updateImage(long folderId, String imagePath, Context context) {
        myLog("stub!");
    }

    public static void addSecondToTimeListened(Context context, long trackId)  {
        myLogE("should never happen");
    }

    public static Intent getSectionRootIntent(Context context) {
        return null;
    }

    public static Intent getFavoritesSectionIntent(Context context) {
        return null;
    }

}
