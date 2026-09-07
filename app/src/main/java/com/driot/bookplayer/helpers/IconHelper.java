package com.driot.bookplayer.helpers;

import android.view.View;
import android.widget.ImageView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Var;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public class IconHelper {


    public static void setSourceIcon(ImageView ivSource, String sourceLocation, String playType) {
        if (isSpecificBookType(sourceLocation, playType) || Var.PLAY_TYPE_MUSIC.equals(playType)) {
            ivSource.setVisibility(View.VISIBLE);
            ivSource.setImageResource(getBookTypeIcon(sourceLocation, playType));
        } else {
            ivSource.setVisibility(View.GONE);
        }
    }

    /** True if the folder's type was determined automatically at import time (podcast, LibriVox,
     * text-to-speech) and can't be changed by the user via the "Book Type" switch. */
    public static boolean isSpecificBookType(String sourceLocation, String playType) {
        if (Var.SOURCE_LOCATION_PODCAST.equals(sourceLocation)) return true;
        if (Var.SOURCE_LOCATION_LIBRIVOX.equals(sourceLocation)) return true;
        return Var.PLAY_TYPE_TEXT.equals(playType);
    }

    /** Icon for the folder's book type. Always resolves to something, defaulting to the plain
     * audiobook icon when no specific type applies (not "music" either). */
    public static int getBookTypeIcon(String sourceLocation, String playType) {
        if (Var.SOURCE_LOCATION_PODCAST.equals(sourceLocation)) return R.drawable.ic_podcast_24;
        if (Var.SOURCE_LOCATION_LIBRIVOX.equals(sourceLocation)) return R.drawable.ic_librivox_24;
        if (Var.PLAY_TYPE_TEXT.equals(playType)) return R.drawable.ic_tts_24;
        if (Var.PLAY_TYPE_MUSIC.equals(playType)) return R.drawable.ic_music_note_24px;
        return R.drawable.ic_menu_book_24;
    }

    /** Denomination string resource matching {@link #getBookTypeIcon}. */
    public static int getBookTypeLabel(String sourceLocation, String playType) {
        if (Var.SOURCE_LOCATION_PODCAST.equals(sourceLocation)) return R.string.book_type_podcast;
        if (Var.SOURCE_LOCATION_LIBRIVOX.equals(sourceLocation)) return R.string.book_type_librivox;
        if (Var.PLAY_TYPE_TEXT.equals(playType)) return R.string.book_type_tts;
        if (Var.PLAY_TYPE_MUSIC.equals(playType)) return R.string.book_type_music;
        return R.string.book_type_audiobook;
    }

}


