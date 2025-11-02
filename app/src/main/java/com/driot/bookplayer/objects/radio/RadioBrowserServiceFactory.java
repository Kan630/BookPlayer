package com.driot.bookplayer.objects.radio;

import android.content.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;


public class RadioBrowserServiceFactory {

    private static final String DEFAULT_BASE = "https://de1.api.radio-browser.info/";
    // Fallback list (can be expanded later)
    private static final String[] FALLBACKS = new String[]{
            "https://fr1.api.radio-browser.info/",
            "https://de1.api.radio-browser.info/",
            "https://nl1.api.radio-browser.info/",
            "https://at1.api.radio-browser.info/"
    };

    public static Retrofit createRetrofit(Context ctx, boolean tryDiscoverMirrors, HttpLoggingInterceptor.Level logLevel) {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(userAgentInterceptor())
                .addInterceptor(httpLogging(logLevel))
                .build();

        String base = DEFAULT_BASE;

        if (tryDiscoverMirrors) {
            String discovered = discoverBestMirror(client);
            if (discovered != null) {
                base = discovered;
            } else {
                // try fallbacks quickly (first one that responds)
                for (String fb : FALLBACKS) {
                    if (probeMirror(client, fb)) {
                        base = fb;
                        break;
                    }
                }
            }
        }

        myLog("RadioBrowser base = " + base);

        return new Retrofit.Builder()
                .baseUrl(base)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    private static Interceptor userAgentInterceptor() {
        return chain -> {
            Request req = chain.request().newBuilder()
                    // Be descriptive: your app + contact or site
                    .header("User-Agent", "BookPlayer/1.0 (com.driot.bookplayer) Android")
                    .build();
            return chain.proceed(req);
        };
    }

    private static HttpLoggingInterceptor httpLogging(HttpLoggingInterceptor.Level lvl) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(Logger::log);
        logging.setLevel(lvl);
        return logging;
    }

    private static class Logger {
        static void log(String s) { myLog(s); }
    }

    /** Ask the root endpoint for servers and pick a good one (https + lowest load). */
    private static String discoverBestMirror(OkHttpClient client) {
        try {
            Retrofit rootRetrofit = new Retrofit.Builder()
                    .baseUrl("https://api.radio-browser.info/") // aggregator
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            RadioBrowserApi root = rootRetrofit.create(RadioBrowserApi.class);
            Response<List<ServerInfo>> resp = root.getServers().execute();
            if (!resp.isSuccessful() || resp.body() == null || resp.body().isEmpty()) return null;

            List<ServerInfo> servers = new ArrayList<>(resp.body());
            servers.removeIf(s -> s == null || s.url == null || s.status == null || !"ok".equalsIgnoreCase(s.status));
            servers.sort(Comparator.comparingDouble(s -> s.load)); // ascending load

            for (ServerInfo s : servers) {
                // prefer https, OK status, and reachable
                if (s.ssl == 1 && probeMirror(client, s.url)) {
                    return s.url.endsWith("/") ? s.url : (s.url + "/");
                }
            }
            // fallback to first OK server if no https passes probe
            if (!servers.isEmpty()) {
                String url = servers.get(0).url;
                if (probeMirror(client, url)) return url.endsWith("/") ? url : (url + "/");
            }
        } catch (Exception e) {
            myLogW("Mirror discovery failed: " + e);
        }
        return null;
    }

    /** Make a tiny blocking call to check the mirror is alive. */
    private static boolean probeMirror(OkHttpClient client, String base) {
        try {
            Retrofit r = new Retrofit.Builder()
                    .baseUrl(base)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            RadioBrowserApi api = r.create(RadioBrowserApi.class);
            retrofit2.Response<List<Station>> resp = api.topVoted(1).execute();
            return resp.isSuccessful();
        } catch (IOException e) {
            return false;
        }
    }
}
