package com.driot.bookplayer.utils;

import static com.driot.bookplayer.global.Var.PODCASTINDEXORG_API_KEY;
import static com.driot.bookplayer.global.Var.PODCASTINDEXORG_API_SECRET;

import com.driot.bookplayer.objects.PodcastIndexApi;
import com.driot.bookplayer.objects.PodcastFeed;
import com.driot.bookplayer.objects.PodcastIndexResponse;

import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import android.util.Base64;

public class PodcastIndexHelper {

    public interface Callback {
        void onSuccess(List<PodcastFeed> feeds);
        void onError(Exception e);
    }

    private static final String BASE_URL = "https://api.podcastindex.org/api/1.0/";

    public static PodcastIndexApi buildApi() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        Interceptor buildAuthInterceptor = chain -> {
            String key = PODCASTINDEXORG_API_KEY;
            String secret = PODCASTINDEXORG_API_SECRET;

            long epochSeconds = System.currentTimeMillis() / 1000;
            String authDate = String.valueOf(epochSeconds);

            // ✅ PodcastIndex requires SHA1(key + secret + time)
            String toHash = key + secret + authDate;
            MessageDigest digest = null;
            try {
                digest = MessageDigest.getInstance("SHA-1");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
            byte[] hashBytes = digest.digest(toHash.getBytes("UTF-8"));

            StringBuilder hexHash = new StringBuilder();
            for (byte b : hashBytes) {
                hexHash.append(String.format("%02x", b));
            }

            Request newRequest = chain.request().newBuilder()
                    .header("User-Agent", "BookPlayer/1.0")
                    .header("X-Auth-Date", authDate)
                    .header("X-Auth-Key", key)
                    .header("Authorization", hexHash.toString())
                    .build();

            return chain.proceed(newRequest);
        };

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor(buildAuthInterceptor)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        return retrofit.create(PodcastIndexApi.class);
    }


    public static void searchPodcasts(String query, String lang, Callback callback) {
        PodcastIndexApi api = buildApi();
        api.searchPodcasts(query, 100, lang).enqueue(new retrofit2.Callback<PodcastIndexResponse>() {
        //api.searchByTerm(query).enqueue(new retrofit2.Callback<PodcastSearchResponse>() {
        @Override
        public void onResponse(Call<PodcastIndexResponse> call, Response<PodcastIndexResponse> response) {
            if (response.isSuccessful() && response.body() != null) {
                callback.onSuccess(response.body().feeds);
            } else {
                String errorBody = "Unknown error";
                try {
                    if (response.errorBody() != null) {
                        errorBody = response.errorBody().string();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

                String message = "HTTP " + response.code() + ": " + errorBody;
                callback.onError(new Exception(message));
            }
        }


            @Override
            public void onFailure(Call<PodcastIndexResponse> call, Throwable t) {
                callback.onError(new Exception(t));
            }
        });
    }
}
