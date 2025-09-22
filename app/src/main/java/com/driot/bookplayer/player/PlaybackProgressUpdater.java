package com.driot.bookplayer.player;

import android.content.Context;

import androidx.annotation.NonNull;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.db.ZikFileDao;
import com.driot.bookplayer.db.Sql;

import java.text.DecimalFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PlaybackProgressUpdater {

    private final Context app;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final DecimalFormat df = new DecimalFormat("#,###.");

    public interface Logger {
        void d(String msg);
        void e(String msg);
        void ee(Throwable t, String msg);
    }

    private final Logger log;

    public PlaybackProgressUpdater(@NonNull Context ctx, @NonNull Logger logger) {
        this.app = ctx.getApplicationContext();
        this.log = logger;
    }

    public void update(@NonNull ZikFile zf, boolean finished, int pos, int dur) {
        io.submit(() -> {
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
                    zf.setPercentdone((int) Math.round(100.0 * pos / dur));
                }

                AppDatabase db = AppDatabase.getDatabase(app);
                ZikFileDao dao = db.ZikFileDao();
                int r = dao.update(zf);
                if (r > 0) {
                    log.d("zik updated (" + zf.getName() + ") pos=" + df.format(zf.getPosition()));
                    Sql.calculateFolderProgress(app, zf.getIdFolder());
                } else {
                    log.e("update failed for " + zf.getName());
                }
            } catch (Throwable t) {
                log.ee(t, "update exception");
            }
        });
    }
}
