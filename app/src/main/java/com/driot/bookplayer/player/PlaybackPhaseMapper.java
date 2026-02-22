package com.driot.bookplayer.player;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Intents;

import static com.driot.bookplayer.global.Intents.*;

/**
 * Utility to centralize mapping of playback phases to localized strings.
 */
public class PlaybackPhaseMapper {

    @Nullable
    public static String getPhaseMessage(@NonNull Context context, @NonNull String phaseId) {
        switch (phaseId) {
            case PHASE_LOADING_TEXT:
                return context.getString(R.string.tts_phase_loading_text);
            case PHASE_WARMING_UP:
                return context.getString(R.string.tts_phase_warming_up);
            case PHASE_STARTING:
                return context.getString(R.string.tts_phase_starting);
            case PHASE_READY:
                return context.getString(R.string.Ready);
            case PHASE_SPEAKING:
                return context.getString(R.string.Speaking);
            case PHASE_ERROR:
                return context.getString(R.string.tts_phase_error);
            case PHASE_BUFFERING:
                return context.getString(R.string.Scanning_3dots); // Or something else for podcasts?
            default:
                return null;
        }
    }
}
