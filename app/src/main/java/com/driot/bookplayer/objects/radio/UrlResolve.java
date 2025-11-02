package com.driot.bookplayer.objects.radio;

import com.google.gson.annotations.SerializedName;

public class UrlResolve {
    // Radio Browser returns a tiny array; each item has "url" (and sometimes "ok").
    @SerializedName("url") public String url;
    @SerializedName("ok")  public String ok;
}
