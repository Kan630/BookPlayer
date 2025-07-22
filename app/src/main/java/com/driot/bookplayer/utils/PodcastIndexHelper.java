package com.driot.bookplayer.utils;

import static com.driot.bookplayer.global.Var.FOLDER_UNZIPPED;
import static com.driot.bookplayer.global.Var.PODCASTINDEXORG_API_KEY;
import static com.driot.bookplayer.global.Var.PODCASTINDEXORG_API_SECRET;
import static com.driot.bookplayer.global.Var.PODCASTINDEXORG_MAX_DOWNLOAD;
import static com.driot.bookplayer.global.Var.PODCASTINDEXORG_MAX_RESULTS;
import static com.driot.bookplayer.utils.KanFiles.sanitizeFilename;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.objects.PodcastEpisode;
import com.driot.bookplayer.objects.PodcastEpisodeResponse;
import com.driot.bookplayer.objects.PodcastIndexApi;
import com.driot.bookplayer.objects.PodcastFeed;
import com.driot.bookplayer.objects.PodcastIndexResponse;

import java.io.File;
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

import android.content.Context;
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
        api.searchPodcasts(query, PODCASTINDEXORG_MAX_RESULTS, lang).enqueue(new retrofit2.Callback<PodcastIndexResponse>() {
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

    public static void getTrendingPodcasts(String lang, int max, Callback callback) {
        PodcastIndexApi api = buildApi();
        api.getTrendingPodcasts(lang, max).enqueue(new retrofit2.Callback<PodcastIndexResponse>() {
            @Override
            public void onResponse(Call<PodcastIndexResponse> call, Response<PodcastIndexResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body().feeds);
                } else {
                    String error = "Unknown error";
                    try {
                        if (response.errorBody() != null) {
                            error = response.errorBody().string();
                        }
                    } catch (Exception ignored) {}
                    callback.onError(new Exception("HTTP " + response.code() + ": " + error));
                }
            }

            @Override
            public void onFailure(Call<PodcastIndexResponse> call, Throwable t) {
                callback.onError(new Exception(t));
            }
        });
    }

    public static void getEpisodesByFeedId(long feedId, long since, EpisodeCallback callback) {
        PodcastIndexApi api = buildApi();
        api.getEpisodesByFeedId(feedId, since).enqueue(new retrofit2.Callback<PodcastEpisodeResponse>() {
            @Override
            public void onResponse(Call<PodcastEpisodeResponse> call, Response<PodcastEpisodeResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body().items);
                } else {
                    callback.onError(new Exception("HTTP " + response.code()));
                }
            }

            @Override
            public void onFailure(Call<PodcastEpisodeResponse> call, Throwable t) {
                callback.onError(new Exception(t));
            }
        });
    }

    public interface EpisodeCallback {
        void onSuccess(List<PodcastEpisode> episodes);
        void onError(Exception e);
    }

    public static void checkForNewEpisodesToAutoDownload(Context context, long since) {
        myLogD("checking for new episodes");
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Podcast> autoList = AppDatabase.getDatabase(context).PodcastDao().getAutoDownloads();

            for (Podcast podcast : autoList) {
                String podcastTitle = sanitizeFilename(podcast.title);
                myLogD("checking for new podcast episodes - " + podcastTitle);
                getEpisodesByFeedId(podcast.feedId, since, new EpisodeCallback() {
                    @Override
                    public void onSuccess(List<PodcastEpisode> episodes) {
                        File baseFolder = new File(context.getFilesDir(), FOLDER_UNZIPPED);
                        File podcastFolder = new File(baseFolder, podcastTitle);
                        if (!podcastFolder.exists()) podcastFolder.mkdirs();
                        int i = 0;
                        for (PodcastEpisode episode : episodes) {
                            i = i + 1;
                            String safeTitle = sanitizeFilename(episode.title);
                            String safeDate = sanitizeFilename(episode.datePublishedPretty);
                            String baseName = safeTitle + " - " + safeDate + ".mp3";
                            if (i <= PODCASTINDEXORG_MAX_DOWNLOAD) {
                                myLogD("Auto-download episode " + i + "[" + safeDate + "] - [" + baseName + "]");
                                File destFile = new File(podcastFolder, baseName);
                                if (!destFile.exists()) {
                                    PodcastDownloadManager.enqueuePodcastDownload(context, episode, destFile.getAbsolutePath());
                                } else {
                                    myLogD("episode " + i + " already exists");
                                }
                            }
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        myLogEE(e, "Auto-download error for feedId " + podcast.feedId);
                    }
                });
            }
        });
    }

    ////////////////////////////////////////////////////////
    private static final String TAG = "PodcastIndexHelper";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }

}
