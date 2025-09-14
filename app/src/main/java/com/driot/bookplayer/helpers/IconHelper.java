package com.driot.bookplayer.helpers;

import static com.driot.bookplayer.global.Var.SOURCE_LOCATION_LIBRIVOX;
import static com.driot.bookplayer.global.Var.SOURCE_LOCATION_PODCAST;
import static com.driot.bookplayer.utils.KanLogger.myLog;

import android.view.View;
import android.widget.ImageView;

import com.driot.bookplayer.R;

public class IconHelper {


    public static void setSourceIcon(ImageView ivSource, String sourceLocation, String fileExtension) {
        //myLog("setSourceIcon " + sourceLocation + " " + fileExtension);
        if (sourceLocation.equals(SOURCE_LOCATION_PODCAST)) {
            ivSource.setVisibility(View.VISIBLE);
            ivSource.setImageResource(R.drawable.ic_podcast_24);
        } else if (sourceLocation.equals(SOURCE_LOCATION_LIBRIVOX)) {
            ivSource.setVisibility(View.VISIBLE);
            ivSource.setImageResource(R.drawable.ic_librivox_24);
        } else if (fileExtension.equalsIgnoreCase("EPUB")) {
            ivSource.setVisibility(View.VISIBLE);
            ivSource.setImageResource(R.drawable.ic_tts_24);
        } else {
            ivSource.setVisibility(View.GONE);
        }
    }

}
