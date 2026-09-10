package com.driot.bookplayer.helpers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.global.Var;

/**
 * Single source of truth for bucketing a Folder's local (ZikFile-based) playback into the same
 * listening-time-stats categories used by both the aggregate totals (StatsActivity, accumulated
 * live tick-by-tick in PlaybackProgressUpdater) and the per-folder breakdown (DetailedStatsActivity,
 * read from Folder.timeListened at rest) - so the two screens always agree on what counts as what.
 */
public final class StatsCategoryHelper {

    private StatsCategoryHelper() {
    }

    @NonNull
    public static String category(@Nullable Folder folder) {
        if (folder == null) {
            return Var.PLAY_MODE_BOOK;
        }
        if (Var.PLAY_TYPE_TEXT.equals(folder.playType)) {
            return Var.PLAY_MODE_TTS;
        }
        if (Var.PLAY_TYPE_MUSIC.equals(folder.playType)) {
            return Var.STATS_CATEGORY_MUSIC;
        }
        if (Var.SOURCE_LOCATION_PODCAST.equals(folder.getSourceLocation())) {
            return Var.STATS_CATEGORY_PODCAST_DOWNLOADED;
        }
        if (Var.SOURCE_LOCATION_RADIO_RECORDING.equals(folder.getSourceLocation())) {
            return Var.STATS_CATEGORY_RADIO_RECORDING;
        }
        return Var.PLAY_MODE_BOOK;
    }

    @StringRes
    public static int categoryLabelRes(@NonNull String category) {
        if (Var.PLAY_MODE_TTS.equals(category)) {
            return R.string.stats_category_tts;
        }
        if (Var.STATS_CATEGORY_MUSIC.equals(category)) {
            return R.string.stats_category_music;
        }
        if (Var.STATS_CATEGORY_PODCAST_DOWNLOADED.equals(category)) {
            return R.string.stats_category_podcast_downloaded;
        }
        if (Var.STATS_CATEGORY_RADIO_RECORDING.equals(category)) {
            return R.string.stats_category_radio_recording;
        }
        return R.string.stats_category_book;
    }
}
