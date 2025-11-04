package com.driot.bookplayer.radio;

import androidx.annotation.NonNull;
import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** Snapshot of a station when it is favorited (so favorites screen is stable). */
public class RadioFavoriteItem implements Serializable {

    @SerializedName("stationuuid") public String stationuuid;
    @SerializedName("name")        public String name;
    @SerializedName("favicon")     public String favicon;
    @SerializedName("codec")       public String codec;
    @SerializedName("bitrate")     public int bitrate;
    @SerializedName("country")     public String country;
    @SerializedName("language")    public String language;
    @SerializedName("tags")        public String tags;

    public static RadioFavoriteItem fromStation(@NonNull Station s) {
        RadioFavoriteItem f = new RadioFavoriteItem();
        f.stationuuid = s.stationuuid;
        f.name        = s.name;
        f.favicon     = s.favicon;
        f.codec       = s.codec;
        f.bitrate     = s.bitrate;
        f.country     = s.country;
        f.language    = s.language;
        f.tags        = s.tags;
        return f;
    }
}
