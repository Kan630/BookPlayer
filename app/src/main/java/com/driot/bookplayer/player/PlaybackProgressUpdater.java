package com.driot.bookplayer.player;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.player.heatmaps.PlayTick;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.db.ZikFileDao;
import com.driot.bookplayer.db.Sql;
import com.driot.bookplayer.utils.log.LoggerHelper;

import java.text.DecimalFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PlaybackProgressUpdater extends LoggerHelper {

    private final Context app;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final DecimalFormat df = new DecimalFormat("#,###.");

    private volatile long suspendUntil = 0;

    public PlaybackProgressUpdater(@NonNull Context ctx) {
        super(PlaybackProgressUpdater.class);
        this.app = ctx.getApplicationContext();
    }

    public void update(@Nullable ZikFile zf, boolean finished, long pos, long dur, String playMode, long timestamp) {
        if (System.currentTimeMillis() < suspendUntil) {
            myLog("update() skipped (suspended)");
            return;
        }
        io.submit(() -> {
            //Stats
            if (!finished) {
                try {
                    Pref.addToTotalMsPlayed(playMode, MediaService.DELAY_CHECK_TIMER_SLEEP);
                } catch (Throwable t) {
                    myLogEE(t, "Pref.addToTotalMsPlayed exception");
                }
            }
            if (zf == null) return;

                // Legacy zikFile position
            try {
                if (zf.lFirstAccess == null || zf.lFirstAccess == 0) {
                    zf.lFirstAccess = timestamp;
                }
                zf.lLastAccess = timestamp;

                if (finished) {
                    zf.setPosition(zf.getDuration());
                    zf.setPercentdone(100);
                    zf.setFinished(true);
                } else {
                    if (pos <= 0 || dur <= 0) return;
                    zf.setPosition(pos);
                    zf.setPercentdone(Math.round((10000.0 * pos / dur)) / 100.0); // like 47.56%
                }

                AppDatabase db = AppDatabase.getDatabase(app);
                ZikFileDao dao = db.zikFileDao();
                int r = dao.update(zf);
                if (r > 0) {
                    myLogD("zik updated (" + zf.getName() + ") pos=" + df.format(zf.getPosition()) + "/" + df.format(zf.getDuration()) + " - " + zf.getPercentdone() + "%");
                    Sql.calculateFolderProgress(app, zf.getIdFolder());
                } else {
                    myLogEE(null,"update failed for " + zf.getName());
                }
            } catch (Throwable t) {
                myLogEE(t, "update exception");
            }

            //PlayTick
            if (!finished) {
                try {
                    PlayTick tick = new PlayTick(timestamp, zf.getId(), pos);
                    AppDatabase.getDatabase(app).playTickDao().insert(tick);
                } catch (Throwable t) {
                    myLogEE(t, "playTick insert exception");
                }

            }

        });
    }
    /** Temporarily suspend DB updates for a few milliseconds (e.g., after a seek). */
    public void suspendOnce(long millis) {
        suspendUntil = System.currentTimeMillis() + millis;
        myLogD("suspending updates for " + millis + "ms");
    }
}
