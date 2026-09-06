package com.driot.bookplayer.player;

import android.content.Context;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.podcasts.PodcastHelper;

/**
 * When set (a podcast episode with its own distinct cover), takes priority over the
 * folder/podcast cover everywhere a "current track" cover is shown: notification, PlayActivity,
 * mini-player. Resolved async per-track since it needs a DB lookup
 * (PodcastHelper.getEpisodeCoverForZikFile() keeps flavor-agnostic, see there for why).
 */
public class EpisodeCoverOverride {

    public interface Listener {
        void onCoverResolved();
    }

    private final Context appContext;
    private final Listener listener;

    private volatile String override = null;

    public EpisodeCoverOverride(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
    }

    public String resolve(Folder f) {
        return (override != null) ? override : (f != null ? f.image : null);
    }

    public void resolveForZikFile(ZikFile zf) {
        override = null; // reset so a previous track's episode cover never lingers
        if (zf == null)
            return;
        final long zikFileId = zf.getId();
        AppDatabase.databaseReadExecutor.execute(() -> {
            String img = PodcastHelper.getEpisodeCoverForZikFile(appContext, zikFileId);
            if (img == null)
                return;
            // Guard: a newer track may have already loaded while this lookup was in flight.
            PlayList pl = PlayList.getInstance();
            ZikFile current = (pl != null) ? pl.getZikFile() : null;
            if (current != null && current.getId() == zikFileId) {
                override = img;
                if (listener != null) {
                    listener.onCoverResolved();
                }
            }
        });
    }
}
