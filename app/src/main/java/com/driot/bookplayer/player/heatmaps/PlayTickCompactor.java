package com.driot.bookplayer.player.heatmaps;

import java.util.ArrayList;
import java.util.List;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.utils.Tonio;

public final class PlayTickCompactor {

    private static final long MAX_TS_GAP_MS = 1500;   // tick continuity
    private static final long MAX_POS_GAP_MS = 2500; //TODO check, maybe speed will put it off.   increased from 1500 to 2500 because of tts...
    private static final long MIN_SESSION_MS = 2000; // ignore noise

    private PlayTickCompactor() {}

    public static void compact(
            AppDatabase db,
            long zikFileId
    ) {
        myLogD("compacting playTicks for zikFileId " + zikFileId);
        List<PlayTick> ticks =
                db.playTickDao().getAllForFile(zikFileId);

        if (ticks == null || ticks.size() < 2) {
            myLogD("nothing to do");
            return;
        }

        List<PlaySession> sessions = new ArrayList<>();

        PlayTick first = ticks.get(0);
        long tsStart = first.timestamp;
        long posStart = first.position;
        PlayTick prev = first;

        for (int i = 1; i < ticks.size(); i++) {
            PlayTick cur = ticks.get(i);

            boolean contiguous =
                    (cur.timestamp - prev.timestamp) <= MAX_TS_GAP_MS &&
                            Math.abs(cur.position - prev.position) <= MAX_POS_GAP_MS;

            if (!contiguous) {
                maybeAddSession(
                        sessions,
                        zikFileId,
                        tsStart,
                        prev.timestamp,
                        posStart,
                        prev.position
                );

                tsStart = cur.timestamp;
                posStart = cur.position;
            }

            prev = cur;
        }

        // close last
        maybeAddSession(
                sessions,
                zikFileId,
                tsStart,
                prev.timestamp,
                posStart,
                prev.position
        );

        if (!sessions.isEmpty()) {
            db.playSessionDao().insertAll(sessions);
            db.playTickDao().deleteUpTo(zikFileId, prev.timestamp);
        }
    }

    private static void maybeAddSession(
            List<PlaySession> out,
            long zikFileId,
            long tsStart,
            long tsEnd,
            long posStart,
            long posEnd
    ) {
        if (posEnd - posStart >= MIN_SESSION_MS) {
            myLogD("adding session - TS=" + Tonio.formatMmSsMs(tsEnd-tsStart) + " - pos=" + Tonio.formatMmSsMs(tsEnd-tsStart));
            out.add(new PlaySession(
                    zikFileId,
                    tsStart,
                    tsEnd,
                    posStart,
                    posEnd
            ));
        } else {
            myLogD("NOT adding session - TS=" + Tonio.formatMmSsMs(tsEnd-tsStart) + " - pos=" + Tonio.formatMmSsMs(tsEnd-tsStart));
        }
    }
}
