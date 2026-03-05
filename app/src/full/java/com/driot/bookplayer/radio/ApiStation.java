package com.driot.bookplayer.radio;

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;


// Retrofit / RadioBrowser API JSON
// Raw live data returned by search/topvote/etc

@Keep
public class ApiStation {
    @SerializedName("stationuuid") public String stationuuid;
    @SerializedName("name")        public String name;
    @SerializedName("url")         public String url;           // original (may redirect)
    @SerializedName("url_resolved")public String url_resolved;  // best direct URL known
    @SerializedName("codec")       public String codec;         // "MP3","AAC","OGG", etc.
    @SerializedName("bitrate")     public int bitrate;
    @SerializedName("hls")         public int hls;              // 1 if HLS
    @SerializedName("favicon")     public String favicon;
    @SerializedName("country")     public String country;
    @SerializedName("countrycode") public String countrycode;
    @SerializedName("language")    public String language;
    @SerializedName("tags")        public String tags;          // comma list
    @SerializedName("clickcount")  public int clickcount;
    @SerializedName("lastcheckok") public int lastcheckok;      // 1 if last check OK

    @SerializedName("state") public String state;
    @SerializedName("iso_3166_2") public String iso_3166_2;
    @SerializedName("votes") public String votes;
    @SerializedName("homepage") public String homepage;


    @Override
    public String toString() {
        return "ApiStation{" +
                "stationuuid='" + stationuuid + '\'' +
                ", name='" + name + '\'' +
                ", url='" + url + '\'' +
                ", url_resolved='" + url_resolved + '\'' +
                ", codec='" + codec + '\'' +
                ", bitrate=" + bitrate +
                ", hls=" + hls +
                ", favicon='" + favicon + '\'' +
                ", country='" + country + '\'' +
                ", countrycode='" + countrycode + '\'' +
                ", language='" + language + '\'' +
                ", tags='" + tags + '\'' +
                ", clickcount=" + clickcount +
                ", lastcheckok=" + lastcheckok +
                ", state='" + state + '\'' +
                ", iso_3166_2='" + iso_3166_2 + '\'' +
                ", votes='" + votes + '\'' +
                ", homepage='" + homepage + '\'' +
                '}';
    }
}
