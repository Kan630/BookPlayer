package com.driot.bookplayer.librivox;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import com.driot.bookplayer.BuildConfig;

public class LibrivoxServiceFactory {

    private static OkHttpClient buildClient(HttpLoggingInterceptor.Level lvl) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(Logger::log);
        logging.setLevel(lvl);

        return new OkHttpClient.Builder()
                // you can add a user-agent interceptor here if you want, like for Radio
                .addInterceptor(logging)
                // ⭐ Timeouts for slow API servers like LibriVox
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)

                // Optional: avoid global hard timeout, rely on readTimeout instead
                .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    /** Direct calls to archive.org (no cache proxy). */
    public static Retrofit createDirectInternetArchiveRetrofit(HttpLoggingInterceptor.Level logLevel) {
        String base = "https://archive.org/";
        myLogD("Internet Archive (direct) base = " + base);

        return new Retrofit.Builder()
                .baseUrl(base)
                .client(buildClient(logLevel))
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    public static Retrofit createDirectLibrivoxRetrofit(HttpLoggingInterceptor.Level logLevel) {
        String base = BuildConfig.LIBRIVOX_PROXY_BASE_URL;
        //String base = "https://librivox.org/";
        myLogD("Librivox (direct) base = " + base);

        return new Retrofit.Builder()
                .baseUrl(base)
                .client(buildClient(logLevel))
                .addConverterFactory(GsonConverterFactory.create())
                .build();
}


/** Cloudflare / proxy front for cached lists (most downloaded, etc.). */
    public static Retrofit createCloudflareRetrofit(HttpLoggingInterceptor.Level logLevel) {
        // e.g. https://books.driot.com/ or ${your CF worker}/
        String base = BuildConfig.LIBRIVOX_PROXY_BASE_URL;
        myLogD("Librivox (Cloudflare) base = " + base);

        return new Retrofit.Builder()
                .baseUrl(base)
                .client(buildClient(logLevel))
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    private static class Logger {
        static void log(String s) { myLog(s); }
    }
}
