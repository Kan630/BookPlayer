package com.driot.bookplayer.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class NetworkUtils {

    // Check if the device is connected to the internet
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }

    // Check if the device can reach the specified URL
    public static boolean canReachUrl(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setConnectTimeout(5000); // 5 seconds timeout
            urlConnection.connect();

            int responseCode = urlConnection.getResponseCode();
            return responseCode == 200; // HTTP 200 means OK
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
