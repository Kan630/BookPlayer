package com.driot.bookplayer.player;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.player.heatmaps.PlayTick;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.db.CommonZikFileDao;
import com.driot.bookplayer.db.Sql;
import com.driot.bookplayer.podcasts.PodcastHelper;
import com.driot.bookplayer.radio.RadioHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggerHelper;

import java.text.DecimalFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PlaybackProgressUpdater extends LoggerHelper {

    private final Context app;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

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
            // Stats
            if (!finished) {
                try {
                    Pref.addToTotalMsPlayed(playMode, MediaService.DELAY_CHECK_TIMER_SLEEP);
                } catch (Throwable t) {
                    myLogEE(t, "Pref.addToTotalMsPlayed exception");
                }
            }
            if (zf == null) {
                //TODO update if radio
                return;
            }


            // Legacy zikFile position
            try {
                if (zf.lFirstAccess == null || zf.lFirstAccess == 0) {
                    zf.lFirstAccess = timestamp;
                }
                zf.lLastAccess = timestamp;

                // Keep ZikFile.duration in sync with engine (TTS/text files often have 0 or
                // wrong duration in DB)
                if (dur > 0) {
                    zf.setDuration(dur);
                }

                if (finished) {
                    zf.setPosition(zf.getDuration());
                    zf.setPercentdone(100);
                    zf.setFinished(true);
                } else {
                    if (pos <= 0 || dur <= 0)
                        return;
                    zf.setPosition(pos);
                    zf.setPercentdone(Math.round((10000.0 * pos / dur)) / 100.0); // like 47.56%
                }

                zf.timeListened = zf.timeListened + 1;

                AppDatabase db = AppDatabase.getDatabase(app);
                CommonZikFileDao dao = db.zikFileDao();
                int r = dao.update(zf);
                if (r > 0) {
                    myLogD("zik updated " + String.valueOf(timestamp).substring(8) + " (" + zf.getName() + ") pos="
                            + Tonio.getReadablePosition(zf.getPosition()) + "/" + Tonio.getReadablePosition(zf.getDuration()) + " - "
                            + zf.getPercentdone() + "%");
                    // PlayTick so heatmap has data (including final position when finished)
                    try {
                        long tickPos = finished ? (long) zf.getDuration() : pos;
                        PlayTick tick = new PlayTick(timestamp, zf.getId(), tickPos);
                        AppDatabase.getDatabase(app).playTickDao().insert(tick);
                    } catch (android.database.sqlite.SQLiteConstraintException e) {
                        try {
                            ZikFile check = AppDatabase.getDatabase(app).zikFileDao().getById(zf.getId());
                            if (check == null) {
                                myLogW("playTick insert ignored: ZikFile " + zf.getId()
                                        + " was deleted (race condition).");
                            } else {
                                myLogEE(e, "playTick insert failed BUT ZikFile " + zf.getId()
                                        + " EXISTS. This is unexpected.");
                            }
                        } catch (Throwable t2) {
                            myLogEE(e,
                                    "playTick insert ignored (ConstraintViolation) - also failed to check existence: "
                                            + t2.getMessage());
                        }
                    } catch (Throwable t) {
                        myLogEE(t, "playTick insert exception for [" + zf.getDisplayName() + "] - pos=" + pos);
                    }
                    Sql.calculateFolderProgress(app, zf.getIdFolder());
                } else {
                    myLogEE(null, "update failed for " + zf.getName());
                }
            } catch (Throwable t) {
                myLogEE(t, "update exception");
            }

        });
    }

    public void updateStream(String playMode, long trackId) {
        if (System.currentTimeMillis() < suspendUntil) {
            myLog("update() skipped (suspended)");
            return;
        }
        io.submit(() -> {
            try {
                Pref.addToTotalMsPlayed(playMode, MediaService.DELAY_CHECK_TIMER_SLEEP);
            } catch (Throwable t) {
                myLogEE(t, "Pref.addToTotalMsPlayed exception");
            }

            if (Var.PLAY_MODE_RADIO.equals(playMode)) {
                RadioHelper.addSecondToTimeListened(app, trackId);
            } else if (Var.PLAY_MODE_PODCAST.equals(playMode)) {
                PodcastHelper.addSecondToTimeListened(app, trackId);
            }
        });
    }

    /**
     * Temporarily suspend DB updates for a few milliseconds (e.g., after a seek).
     */
    public void suspendOnce(long millis) {
        suspendUntil = System.currentTimeMillis() + millis;
        myLogD("suspending updates for " + millis + "ms");
    }
}
