// com.driot.bookplayer.net.NetUtils.java
package com.driot.bookplayer.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.utils.log.LoggerStaticHelper;

public final class NetUtils {
    private NetUtils() {}

    private static volatile OkHttpClient sClient;

    /** Shared OkHttpClient with logging — reuse across all cover providers. */
    public static OkHttpClient sharedClient() {
        if (sClient == null) {
            synchronized (NetUtils.class) {
                if (sClient == null) {
                    // Collapse pretty-printed JSON bodies to a single line
                    HttpLoggingInterceptor logging = new HttpLoggingInterceptor(
                            msg -> LoggerStaticHelper.myLog(msg.replace("\n", "").replace("\r", "")));
                    logging.setLevel(Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL);
                    sClient = new OkHttpClient.Builder()
                            .addInterceptor(logging)
                            .connectTimeout(8, TimeUnit.SECONDS)
                            .readTimeout(20, TimeUnit.SECONDS) // OpenLibrary can be slow
                            .build();
                }
            }
        }
        return sClient;
    }

    public static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }

    public static String readUtf8(InputStream in) throws IOException {
        return new String(readAllBytes(in), StandardCharsets.UTF_8);
    }
}
