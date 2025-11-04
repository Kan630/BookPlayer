package com.driot.bookplayer.radio;

import com.google.gson.annotations.SerializedName;

public class ServerInfo {
    @SerializedName("name")     public String name;     // e.g. "fr1.api.radio-browser.info"
    @SerializedName("ip")       public String ip;
    @SerializedName("country")  public String country;
    @SerializedName("url")      public String url;      // e.g. "https://fr1.api.radio-browser.info/"
    @SerializedName("status")   public String status;   // "ok"
    @SerializedName("ssl")      public int ssl;         // 1 if https
    @SerializedName("proto")    public String proto;    // "https"/"http"
    @SerializedName("load")     public double load;     // lower is better
    @SerializedName("supported_version") public String supported_version;
}
