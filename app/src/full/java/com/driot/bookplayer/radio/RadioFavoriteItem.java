package com.driot.bookplayer.radio;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.driot.bookplayer.db.RadioStation;
import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** Snapshot of a station when it is favorited (so favorites screen is stable). */
@Keep
public class RadioFavoriteItem implements Serializable {

    @SerializedName("stationuuid") public String stationuuid;
    @SerializedName("name")        public String name;
    @SerializedName("favicon")     public String favicon;
    @SerializedName("codec")       public String codec;
    @SerializedName("bitrate")     public int bitrate;
    @SerializedName("country")     public String country;
    @SerializedName("language")    public String language;
    @SerializedName("tags")        public String tags;
    @SerializedName("last_url")    public String last_url;
    @SerializedName("url")         public String url;           // original (may redirect)
    @SerializedName("url_resolved")public String url_resolved;  // best direct URL known
    @SerializedName("hls")         public int hls;              // 1 if HLS
    @SerializedName("countrycode") public String countrycode;
    @SerializedName("clickcount")  public int clickcount;
    @SerializedName("lastcheckok") public int lastcheckok;      // 1 if last check OK

    public static RadioFavoriteItem fromStation(@NonNull ApiStation s) {
        RadioFavoriteItem f = new RadioFavoriteItem();
        f.stationuuid = s.stationuuid;
        f.name        = s.name;
        f.favicon     = s.favicon;
        f.codec       = s.codec;
        f.bitrate     = s.bitrate;
        f.country     = s.country;
        f.language    = s.language;
        f.tags        = s.tags;
        f.countrycode = s.countrycode;
        f.clickcount  = s.clickcount;
        f.lastcheckok = s.lastcheckok;
        f.url         = s.url;
        f.url_resolved= s.url_resolved;

        f.last_url    = (s.url_resolved != null && !s.url_resolved.isEmpty()) ? s.url_resolved : s.url;
        return f;
    }

    public static RadioFavoriteItem fromRadioStation(@NonNull RadioStation r) {
        RadioFavoriteItem f = new RadioFavoriteItem();
        f.stationuuid = r.stationuuid;
        f.name        = r.name;
        f.favicon     = r.favicon;
        f.codec       = r.codec;
        f.bitrate     = r.bitrate;
        f.country     = r.country;
        f.language    = r.language;
        f.tags        = r.tags;
        f.countrycode = r.countrycode;
        f.clickcount  = r.clickcount;
        f.lastcheckok = r.lastcheckok;
        f.url         = r.url;
        f.url_resolved= r.url_resolved;

        f.last_url    = (r.url_resolved != null && !r.url_resolved.isEmpty()) ? r.url_resolved : r.url;
        return f;
    }
}
