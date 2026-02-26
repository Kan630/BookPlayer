package com.driot.bookplayer.radio;

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;

@Keep
public class UrlResolve {
    @SerializedName("url") public String url;
    @SerializedName("ok")  public String ok;
    @SerializedName("message")  public String message;
    @SerializedName("name")  public String name;
    @SerializedName("stationuuid")  public String stationuuid;
}
