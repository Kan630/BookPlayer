package com.driot.bookplayer.player;

import android.content.Context;

import androidx.annotation.NonNull;

import com.driot.bookplayer.db.AppDatabase;
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

    public void update(@NonNull ZikFile zf, boolean finished, long pos, long dur) {
        if (System.currentTimeMillis() < suspendUntil) {
            myLog("update() skipped (suspended)");
            return;
        }
        io.submit(() -> {
            // Legacy zikFile position
            try {
                if (zf.lFirstAccess == null || zf.lFirstAccess == 0) {
                    zf.lFirstAccess = System.currentTimeMillis();
                }
                zf.lLastAccess = System.currentTimeMillis();

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

            // new Heatmap tick save
            try {
                PlayTick tick = new PlayTick(System.currentTimeMillis(), zf.getId(), pos);
                AppDatabase db = AppDatabase.getDatabase(app);
                db.playTickDao().insert(tick);
            } catch (Exception e) {
                myLogEE(e, "updatePlayTick");
            }

        });
    }
    /** Temporarily suspend DB updates for a few milliseconds (e.g., after a seek). */
    public void suspendOnce(long millis) {
        suspendUntil = System.currentTimeMillis() + millis;
        myLogD("suspending updates for " + millis + "ms");
    }
}
