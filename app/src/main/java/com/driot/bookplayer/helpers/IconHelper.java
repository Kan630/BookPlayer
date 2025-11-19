package com.driot.bookplayer.helpers;

import android.view.View;
import android.widget.ImageView;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Var;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

public class IconHelper {


    public static void setSourceIcon(ImageView ivSource, String sourceLocation, String playType) {
        if (sourceLocation==null) {
            //myLogD("bad param for setSourceIcon - sourceLocation = " + sourceLocation);
            sourceLocation="***";
        }
        if (playType==null) {
            //myLogD("bad param for setSourceIcon - playType = " + playType);
            playType="***";
        }

        if (sourceLocation.equals(Var.SOURCE_LOCATION_PODCAST)) {
            ivSource.setVisibility(View.VISIBLE);
            ivSource.setImageResource(R.drawable.ic_podcast_24);
        } else if (sourceLocation.equals(Var.SOURCE_LOCATION_LIBRIVOX)) {
            ivSource.setVisibility(View.VISIBLE);
            ivSource.setImageResource(R.drawable.ic_librivox_24);
        } else if (playType.equals(Var.PLAY_TYPE_TEXT)) {
            ivSource.setVisibility(View.VISIBLE);
            ivSource.setImageResource(R.drawable.ic_tts_24);
        } else {
            ivSource.setVisibility(View.GONE);
        }
    }

}


