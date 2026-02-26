package com.driot.bookplayer.podcasts;

import android.content.Context;
import android.content.Intent;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;

public class PodcastHelper {

    public static void cancelAutoDownload(Context c, int folderId) {
		myLog("stub!");
    }

    public static void deleteEpisode(int id) {
        myLog("stub!");
    }

    public static void goUserClickHeader(Folder folder, Context c) {
        myLog("stub!");
    }

    public static void handlePodcastImages(Context context) {
        myLog("stub!");
    }

    public static void playActivityOnDoubleTap(Folder folder, Context c) {
        myLog("stub!");
    }

    public static void startPlayOpenPodcast(Folder folder, Context c) {
        myLog("stub!");
    }

	public static List<ZikFile> getPodcastZikFiles(Folder folder, Context context) {
		return null;
    }
		
}
