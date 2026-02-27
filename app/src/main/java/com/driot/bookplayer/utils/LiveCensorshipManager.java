package com.driot.bookplayer.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.TrafficStats;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.myLogD;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.myLogE;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.myLogEE;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.myLogI;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.myLogW;

public final class LiveCensorshipManager {

    public static final String ENDPOINT = "https://bookplayer.driot.com/app_live_data/censored_items.json";

    private static final String PERIODIC_WORK_NAME = "LiveCensorshipPeriodicFetch";
    private static final long PERIODIC_HOURS = 24;
    private static final int RETRY_DELAY_IN_SEC = 30;

    private static final String KEY_JSON = "censorship_json";
    private static final String KEY_ETAG = "censorship_etag";
    private static final String KEY_FETCHED_AT = "censorship_fetched_at";

    // In-memory cache
    private static Set<String> cachedRadios;
    private static Set<String> cachedPodcasts;
    private static Set<String> cachedEbooks;
    private static Set<String> cachedLibrivox;

    private LiveCensorshipManager() {
    }

    public static void schedule(Context context) {
        myLogD("LiveCensorshipManager schedule(): enqueue one-shot + periodic fetch");
        Constraints net = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest now = new OneTimeWorkRequest.Builder(FetchWorker.class)
                .setConstraints(net)
                .setBackoffCriteria(
                        androidx.work.BackoffPolicy.EXPONENTIAL,
                        RETRY_DELAY_IN_SEC, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                "LiveCensorshipOneShot", ExistingWorkPolicy.REPLACE, now);

        PeriodicWorkRequest periodic = new PeriodicWorkRequest.Builder(
                FetchWorker.class, PERIODIC_HOURS, TimeUnit.HOURS)
                .setConstraints(net)
                .setBackoffCriteria(
                        androidx.work.BackoffPolicy.EXPONENTIAL,
                        RETRY_DELAY_IN_SEC, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, periodic);
    }

    @NonNull
    public static Set<String> getCensoredRadios(Context context) {
        ensureLoaded(context);
        Set<String> allRadios = new HashSet<>(Var.RADIO_STATION_CENSORED_LOWERCASE);
        if (cachedRadios != null) {
            allRadios.addAll(cachedRadios);
        }
        return allRadios;
    }

    @NonNull
    public static Set<String> getCensoredPodcasts(Context context) {
        ensureLoaded(context);
        return cachedPodcasts != null ? cachedPodcasts : Collections.emptySet();
    }

    @NonNull
    public static Set<String> getCensoredEbooks(Context context) {
        ensureLoaded(context);
        return cachedEbooks != null ? cachedEbooks : Collections.emptySet();
    }

    @NonNull
    public static Set<String> getCensoredLibrivox(Context context) {
        ensureLoaded(context);
        return cachedLibrivox != null ? cachedLibrivox : Collections.emptySet();
    }

    public static boolean isCensored(String name, Set<String> censoredList) {
        if (name == null || name.isEmpty() || censoredList == null || censoredList.isEmpty()) {
            return false;
        }
        String lowerName = name.toLowerCase(Locale.ROOT);
        for (String censored : censoredList) {
            if (lowerName.contains(censored)) {
                return true;
            }
        }
        return false;
    }

    private static synchronized void ensureLoaded(Context ctx) {
        if (cachedRadios != null)
            return;

        SharedPreferences prefs = Pref.getCensorshipPrefs();
        String json = prefs.getString(KEY_JSON, null);
        parseJsonToCache(json);
    }

    private static synchronized void parseJsonToCache(@Nullable String json) {
        cachedRadios = new HashSet<>();
        cachedPodcasts = new HashSet<>();
        cachedEbooks = new HashSet<>();
        cachedLibrivox = new HashSet<>();

        if (json == null || json.trim().isEmpty()) {
            return;
        }

        try {
            CensoredItems data = new Gson().fromJson(json, CensoredItems.class);
            if (data != null) {
                if (data.radios != null) {
                    for (String s : data.radios) {
                        if (s != null)
                            cachedRadios.add(s.toLowerCase(Locale.ROOT));
                    }
                }
                if (data.podcasts != null) {
                    for (String s : data.podcasts) {
                        if (s != null)
                            cachedPodcasts.add(s.toLowerCase(Locale.ROOT));
                    }
                }
                if (data.ebooks != null) {
                    for (String s : data.ebooks) {
                        if (s != null)
                            cachedEbooks.add(s.toLowerCase(Locale.ROOT));
                    }
                }
                if (data.librivox_books != null) {
                    for (String s : data.librivox_books) {
                        if (s != null)
                            cachedLibrivox.add(s.toLowerCase(Locale.ROOT));
                    }
                }
            }
        } catch (Exception e) {
            myLogEE(e, "LiveCensorshipManager failed to parse JSON cache");
        }
    }

    private static void saveToCache(Context ctx, String json, @Nullable String etag) {
        long now = System.currentTimeMillis();
        Pref.getCensorshipPrefs().edit()
                .putString(KEY_JSON, json)
                .putString(KEY_ETAG, etag)
                .putLong(KEY_FETCHED_AT, now)
                .apply();

        parseJsonToCache(json);
        myLogD("LiveCensorshipManager Cache saved. etag=" + etag);
    }

    static String fetchJson(String urlString, @Nullable String etag, Context context) throws Exception {
        // We can reuse the same traffic tag or use REACHABILITY_CHECK
        TrafficStats.setThreadStatsTag(Var.TRAFFIC_TAG_REACHABILITY_CHECK);
        HttpURLConnection c = (HttpURLConnection) new URL(urlString).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(7000);
        if (etag != null && !etag.isEmpty()) {
            c.setRequestProperty("If-None-Match", etag);
        }
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Accept-Encoding", "gzip");

        int code = c.getResponseCode();
        String enc = c.getHeaderField("Content-Encoding");
        String newEtag = c.getHeaderField("ETag");

        myLogD("LiveCensorshipManager fetchJson: HTTP " + code + ", ETag=" + newEtag);

        if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
            c.disconnect();
            throw new NotModifiedException();
        }

        if (code == HttpURLConnection.HTTP_OK) {
            // Read body
        } else if (code == HttpURLConnection.HTTP_NOT_FOUND || code == HttpURLConnection.HTTP_GONE
                || (code >= 400 && code < 500 && code != 429)) {
            c.disconnect();
            throw new PermanentHttpException(code, "Permanent HTTP " + code);
        } else {
            c.disconnect();
            throw new RetryableHttpException(code, "Retryable HTTP " + code);
        }

        try (InputStream rawIn = c.getInputStream();
                InputStream in = (enc != null && enc.toLowerCase(Locale.ROOT).contains("gzip"))
                        ? new java.util.zip.GZIPInputStream(rawIn)
                        : rawIn;
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            String json = out.toString("UTF-8");

            saveToCache(context, json, newEtag);
            return json;
        } finally {
            c.disconnect();
        }
    }

    public static class FetchWorker extends Worker {
        public FetchWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @NonNull
        @Override
        public Result doWork() {
            SharedPreferences p = Pref.getCensorshipPrefs();
            String etag = p.getString(KEY_ETAG, null);
            myLogD("LiveCensorshipManager Worker: start, etag=" + etag);

            try {
                fetchJson(ENDPOINT, etag, getApplicationContext());
                return Result.success();
            } catch (NotModifiedException ignore) {
                myLogD("LiveCensorshipManager Worker: HTTP 304 Not Modified");
                return Result.success();
            } catch (PermanentHttpException e) {
                myLogW("LiveCensorshipManager Worker: " + e.getMessage() + " — not retrying");
                return Result.success();
            } catch (RetryableHttpException | java.io.IOException e) {
                myLogI("LiveCensorshipManager Worker: Network/Retryable error (" + e.getMessage() + ") — will retry");
                return Result.retry();
            } catch (Throwable t) {
                myLogEE(t, "LiveCensorshipManager Worker error");
                return Result.retry();
            }
        }
    }

    static class NotModifiedException extends Exception {
    }

    static class PermanentHttpException extends Exception {
        final int code;

        PermanentHttpException(int code, String msg) {
            super(msg);
            this.code = code;
        }
    }

    static class RetryableHttpException extends Exception {
        final int code;

        RetryableHttpException(int code, String msg) {
            super(msg);
            this.code = code;
        }
    }

    @Keep
    public static class CensoredItems {
        public List<String> radios;
        public List<String> podcasts;
        public List<String> ebooks;
        public List<String> librivox_books;
    }
}
