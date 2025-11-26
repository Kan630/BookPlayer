package com.driot.bookplayer.radio;

import android.content.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.NetworkHelper;


public class RadioBrowserServiceFactory {

    // Fallback list (can be expanded later)
    private static final String[] FALLBACKS = new String[]{
            "https://fi1.api.radio-browser.info/"
            ,"https://de2.api.radio-browser.info/"
            ,"https://de1.api.radio-browser.info/"
            ,"https://fr1.api.radio-browser.info/"
            ,"https://nl1.api.radio-browser.info/"
    };

    public static void init(Context context) {
        if (NetworkHelper.isNetworkAvailable(context)) {
            Executors.newSingleThreadExecutor().execute(() -> {
                RadioBrowserServiceFactory.createRetrofit(
                        context,
                        /* tryDiscoverMirrors = */ true,
                        Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL
                );
            });
        } else {
            myLogD("no internet => no radio init");
        }
    }

    public static Retrofit createRetrofit(Context ctx, boolean tryDiscoverMirrors, HttpLoggingInterceptor.Level logLevel) {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(userAgentInterceptor())
                .addInterceptor(httpLogging(logLevel))
                .build();

        String base = Pref.get_radio_mirror();

        myLogD("tryDiscoverMirrors = " + tryDiscoverMirrors);

        if (tryDiscoverMirrors) {
            Set<String> failedBases = new java.util.HashSet<>();
            String discovered = discoverBestMirror(client, failedBases);
            if (discovered != null) {
                base = discovered;
                Pref.set_radio_mirror(base);
            } else {
                // try fallbacks quickly (first one that responds)
                for (String fb : FALLBACKS) {
                    if (failedBases.contains(fb)) {
                        myLogD("Skipping fallback (already failed): " + fb);
                        continue;
                    }
                    if (probeMirror(client, fb)) {
                        base = fb;
                        Pref.set_radio_mirror(base);
                        break;
                    } else {
                        failedBases.add(fb);
                    }
                }
            }
        }

        myLogD("RadioBrowser base = " + base);

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
                    .header("User-Agent", Var.USER_AGENT_BOOKPLAYER)
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

    /** Discover a good mirror via /json/servers + probing. */
    private static String discoverBestMirror(OkHttpClient client, Set<String> failedBases) {
        myLogD("discoverBestMirror");

        try {
            Retrofit rootRetrofit = new Retrofit.Builder()
                    .baseUrl(Pref.get_radio_mirror())
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            RadioBrowserApi root = rootRetrofit.create(RadioBrowserApi.class);
            Response<List<ServerInfo>> resp = root.getServers().execute();

            if (!resp.isSuccessful() || resp.body() == null || resp.body().isEmpty()) {
                myLogW("discoverBestMirror: /json/servers failed: " + resp.code());
                return null;
            }

            List<ServerInfo> servers = new ArrayList<>(resp.body());
            // Keep only entries with a hostname
            servers.removeIf(s -> s == null || s.name == null || s.name.isEmpty());

            if (servers.isEmpty()) {
                myLogW("discoverBestMirror: servers list empty after filtering");
                return null;
            }
            myLogD(servers.size() + " servers returned.");

            // Shuffle to distribute load randomly
            java.util.Collections.shuffle(servers);

            for (ServerInfo s : servers) {
                String base = "https://" + s.name + "/";
                myLogD("server discovery candidate: name=" + s.name
                        + " ip=" + s.ip + " base=" + base);

                if (failedBases.contains(base)) {
                    myLogD("Skipping candidate (already failed): " + base);
                    continue;
                }
                if (probeMirror(client, base)) {
                    myLog("discoverBestMirror: selected " + base);
                    return base;
                }
            }

            myLogW("discoverBestMirror: no candidate server passed probe");
        } catch (Exception e) {
            myLogW("Mirror discovery failed: " + e);
        }
        return null;
    }

    /** Make a tiny blocking call to check the mirror is alive. */
    private static boolean probeMirror(OkHttpClient client, String base) {
        myLog("probeMirror : " + base);
        try {
            Retrofit r = new Retrofit.Builder()
                    .baseUrl(base)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            RadioBrowserApi api = r.create(RadioBrowserApi.class);
            retrofit2.Response<List<Station>> resp = api.topVoted(1, false).execute();
            return resp.isSuccessful();
        } catch (javax.net.ssl.SSLHandshakeException e) {
            myLogW("probeMirror SSLHandshakeException for " + base + " : " + e);
            return false;
        } catch (IOException e) {
            myLogW("probeMirror IOException for " + base + " : " + e);
            return false;
        } catch (Exception e) {
            myLogW("probeMirror generic error for " + base + " : " + e);
            return false;
        }
    }
}
