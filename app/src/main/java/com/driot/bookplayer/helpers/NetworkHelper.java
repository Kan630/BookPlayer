package com.driot.bookplayer.helpers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.security.NetworkSecurityPolicy;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import com.driot.bookplayer.global.Option;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownServiceException;
import java.util.Locale;

public class NetworkHelper {

    // ---------- Logging ----------

    public static void logCurrentNetworkState(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            myLogW("ConnectivityManager is null");
            return;
        }

        try {
            Network active = cm.getActiveNetwork();
            if (active == null) {
                myLogI("No active network");
                return;
            }

            NetworkCapabilities caps = cm.getNetworkCapabilities(active);
            if (caps == null) {
                myLogI("No network capabilities");
                return;
            }

            boolean wifi      = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            boolean cellular  = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
            boolean ethernet  = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
            boolean vpn       = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);

            boolean hasInternet  = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            boolean validated    = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            boolean unmetered    = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);

            // Best-effort roaming (legacy API)
            boolean roaming = false;
            @SuppressLint("MissingPermission")
            NetworkInfo info = cm.getActiveNetworkInfo();
            if (info != null) roaming = info.isRoaming();

            myLogI("---- Network Debug ----");
            myLog("Wi-Fi: " + wifi + " | Cellular: " + cellular + " | Ethernet: " + ethernet + " | VPN: " + vpn);
            myLog("HasInternet: " + hasInternet + " | Validated: " + validated + " | Unmetered: " + unmetered);
            myLog("Roaming: " + roaming);
            myLogI("------------------------");
            myLog("Option manual download : " + Option.getNetworkPolicyManualDownload().toString());
            myLog("Option auto download : " + Option.getNetworkPolicyAutoDownload().toString());
            myLogI("------------------------");


        } catch (Exception e) {
            myLogEE(e, "Error checking network state");
        }
    }

    // ---------- Policies (placeholders if you need them here) ----------

    public enum NetworkPolicyManual {
         NETWORK_POLICY_NEVER_ASK
        ,NETWORK_POLICY_UNMETERED
    }

    public enum NetworkPolicyAuto {
         NETWORK_POLICY_NEVER_ASK
        ,NETWORK_POLICY_UNMETERED
    }

    // ---------- Connectivity helpers ----------

    /** Quick connected check using modern APIs; no legacy NetworkInfo reliance. */
    public static boolean isConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network active = cm.getActiveNetwork();
        if (active == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(active);
        return caps != null
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    /** Kept for compatibility; consider using isConnected() instead. */
    @SuppressLint("MissingPermission")
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo info = cm.getActiveNetworkInfo(); // deprecated but still functional
        return info != null && info.isConnected();
    }

    /** True if active network is unmetered (Wi-Fi/Ethernet/etc.). Conservative default = false. */
    public static boolean isUnmeteredConnected(Context context) {
        return false;
        /*
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network active = cm.getActiveNetwork();
        if (active == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(active);
        return caps != null
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);

         */
    }

    /** True if active transport is Wi-Fi (does not imply unmetered). */
    public static boolean isWifiConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network active = cm.getActiveNetwork();
        if (active == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(active);
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    /** Walks cause chain to detect UnknownHostException. */
    public static boolean isUnknownHost(Throwable e) {
        while (e != null) {
            if (e instanceof java.net.UnknownHostException) return true;
            e = e.getCause();
        }
        return false;
    }

    // ---------- Reachability checks (don’t call on main thread) ----------

    /** Simple GET 200 check; closes connection; 5s connect timeout. */
    public static boolean canReachUrl(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int code = conn.getResponseCode();
            return code == HttpURLConnection.HTTP_OK;
        } catch (IOException ignored) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** HEAD with short timeouts; treats 2xx–3xx as reachable. */
    public static boolean isUrlReachable(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            int code = conn.getResponseCode();
            return code >= 200 && code < 400;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Returns true if this exception (or any cause) indicates cleartext is blocked by policy. */
    public static boolean isCleartextNotPermitted(Throwable t) {
        while (t != null) {
            String msg = t.getMessage();
            if (t instanceof UnknownServiceException && msg != null &&
                    msg.toLowerCase(Locale.US).contains("cleartext")) {
                myLog("isCleartextNotPermitted, case 1");
                return true;
            }
            if (msg != null && msg.toLowerCase(Locale.US).contains("cleartext")
                    && msg.toLowerCase(Locale.US).contains("not permitted")) {
                myLog("isCleartextNotPermitted, case 2");
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    /** True if URL is http:// and app policy blocks cleartext for that host. */
    public static boolean isCleartextBlockedForUrl(Context ctx, String urlStr) {
        try {
            URL u = new URL(urlStr);
            if (!"http".equalsIgnoreCase(u.getProtocol())) return false;
            return !NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(u.getHost());
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Safely convert http://… to https://… (keeps path/query/fragment). Returns null if not http. */
    public static String upgradeToHttps(String urlStr) {
        try {
            URL u = new URL(urlStr);
            if (!"http".equalsIgnoreCase(u.getProtocol())) return null;
            // Simple & robust: replace only the scheme.
            return "https://" + u.getHost() + (u.getPort() > 0 ? (":" + u.getPort()) : "") + u.getFile();
        } catch (Exception e) {
            return null;
        }
    }
}
