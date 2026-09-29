package com.driot.bookplayer.podcasts;

import android.content.Context;

import java.util.Collections;
import java.util.List;

/** Pure flavor has no podcasts. */
public final class PodcastRedownload {

    private PodcastRedownload() {
    }

    public static List<Long> folderIds(Context context) {
        return Collections.emptyList();
    }

    public static boolean isRedownloadable(Context context, long folderId) {
        return false;
    }

    public static void enqueue(Context context, long folderId) {
    }
}
