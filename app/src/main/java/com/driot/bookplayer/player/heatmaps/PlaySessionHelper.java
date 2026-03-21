package com.driot.bookplayer.player.heatmaps;

import com.driot.bookplayer.db.AppDatabase;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import android.content.Context;

public class PlaySessionHelper {

    private static final int MIN_SESSION_DURATION_MS = 2000;

    public static void registerSession(Context context, long zikFileId, long startTimestamp, long startPosition, long endTimestamp, long endPosition) {

        if (startTimestamp <= 0) {
            myLogE("bad StartTimestamp : " + startTimestamp);
            return;
        }

        if (startPosition < 0) {
            myLogE("bad startPosition : " + startPosition);
            return;
        }

        long duration = endPosition - startPosition;

        // same as PlayTickCompactor.MIN_SESSION_MS
        if (duration < MIN_SESSION_DURATION_MS) {
            myLogD("TTS Session too short: " + duration + "ms - discarding");
            return;
        }

        Context appCtx = context.getApplicationContext();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            PlaySession s = new PlaySession(
                    zikFileId,
                    startTimestamp,
                    endTimestamp,
                    startPosition,
                    endPosition
            );

            AppDatabase db = AppDatabase.getInstance(appCtx);
            db.playSessionDao().insert(s);
            db.playTickDao().deleteRange(zikFileId, startTimestamp, endTimestamp);
            myLogI("TTS Session registered: " + duration + "ms (" + startPosition + " -> " + endPosition + ")");
        });
    }

}
