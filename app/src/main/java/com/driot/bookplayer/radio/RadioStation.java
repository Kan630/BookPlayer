package com.driot.bookplayer.radio;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "RadioStation")
public class RadioStation {

    @PrimaryKey(autoGenerate = true)
    public long id;

    // ---- API fields ----

    @NonNull
    public String stationuuid;         // NOT NULL in DB

    @Nullable public String name;
    @Nullable public String url;
    @Nullable public String url_resolved;
    @Nullable public String codec;

    @ColumnInfo(defaultValue = "0")
    public int bitrate;

    @ColumnInfo(defaultValue = "0")
    public int hls;

    @Nullable public String favicon;
    @Nullable public String country;
    @Nullable public String countrycode;
    @Nullable public String language;
    @Nullable public String tags;

    @ColumnInfo(defaultValue = "0")
    public int clickcount;             // INTEGER NOT NULL DEFAULT 0

    @ColumnInfo(defaultValue = "0")
    public int lastcheckok;            // INTEGER NOT NULL DEFAULT 0

    // ---- App fields ----

    @ColumnInfo(defaultValue = "0")
    public int display_order;          // INTEGER NOT NULL DEFAULT 0

    @ColumnInfo(defaultValue = "0")
    public boolean isFavorite;         // INTEGER NOT NULL DEFAULT 0 (0/1)

    @Nullable
    public Long date_last_played;      // INTEGER, nullable (no default)

    @ColumnInfo(defaultValue = "0")
    public long date_added;            // INTEGER NOT NULL DEFAULT 0

    @ColumnInfo(defaultValue = "0")
    public long date_maj;              // INTEGER NOT NULL DEFAULT 0

    @Nullable public String state;
    @Nullable public String iso_3166_2;
    @Nullable public String votes;
    @Nullable public String homepage;


    public static RadioStation fromStation(@NonNull Station s,
                                           @Nullable String streamUrl) {
        long now = System.currentTimeMillis();

        RadioStation r = new RadioStation();
        r.stationuuid      = s.stationuuid;
        r.name             = s.name;
        r.url              = s.url;
        r.url_resolved     = (streamUrl != null && !streamUrl.isEmpty())
                ? streamUrl
                : s.url_resolved;
        r.codec            = s.codec;
        r.bitrate          = s.bitrate;
        r.hls              = s.hls;
        r.favicon          = s.favicon;
        r.country          = s.country;
        r.countrycode      = s.countrycode;
        r.language         = s.language;
        r.tags             = s.tags;
        r.clickcount       = s.clickcount;
        r.lastcheckok      = s.lastcheckok;

        r.display_order    = 0;          // non-favorite by default, last
        r.isFavorite       = false;
        r.date_last_played = null;
        r.date_added       = now;
        r.date_maj         = now;

        r.state       = s.state;
        r.iso_3166_2  = s.iso_3166_2;
        r.votes       = s.votes;
        r.homepage    = s.homepage;

        return r;
    }
}
