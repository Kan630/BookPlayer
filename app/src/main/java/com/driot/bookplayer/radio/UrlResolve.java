package com.driot.bookplayer.radio;

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;

@Keep
public class UrlResolve {
    // Radio Browser returns a tiny array; each item has "url" (and sometimes "ok").
    @SerializedName("url") public String url;
    @SerializedName("ok")  public String ok;
}
