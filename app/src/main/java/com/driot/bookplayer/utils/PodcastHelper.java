package com.driot.bookplayer.utils;

import static com.driot.bookplayer.global.Var.FOLDER_UNZIPPED;
import static com.driot.bookplayer.global.Var.PODCASTINDEXORG_API_KEY;
import static com.driot.bookplayer.global.Var.PODCASTINDEXORG_API_SECRET;
import static com.driot.bookplayer.global.Var.PODCASTINDEXORG_MAX_EPISODE_AUTO_DOWNLOAD;
import static com.driot.bookplayer.global.Var.PODCASTINDEXORG_API_MAX_RESULTS;
import static com.driot.bookplayer.global.Var.PODCASTINDEXORG_MAX_PODCAST_AUTO_DOWNLOAD;
import static com.driot.bookplayer.utils.KanFiles.sanitizeFilename;
import static com.driot.bookplayer.utils.StorageHelper.getPreferredFilesDirs;
import static com.driot.bookplayer.utils.StorageHelper.getSdCardFilesDirs;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.objects.PodcastEpisode;
import com.driot.bookplayer.objects.PodcastEpisodeResponse;
import com.driot.bookplayer.objects.PodcastIndexApi;
import com.driot.bookplayer.objects.PodcastFeed;
import com.driot.bookplayer.objects.PodcastIndexResponse;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import android.content.Context;

public class PodcastHelper {

    public interface Callback {
        void onSuccess(List<PodcastFeed> feeds);
        void onError(Exception e);
    }

    public static File buildPodcastPath(Context context, Podcast podcast) {
        return buildPodcastPath(context, podcast.title);
    }

    public static File buildPodcastPath(Context context, String podcastTitle, boolean forceSdCard) {
        String sanitizedPodcastTitle = sanitizeFilename(podcastTitle);
        File baseFolder;
        if (forceSdCard) {
            baseFolder = new File(getSdCardFilesDirs(context), FOLDER_UNZIPPED);
        } else {
            baseFolder = new File(context.getFilesDir(), FOLDER_UNZIPPED);
        }
        return new File(baseFolder, sanitizedPodcastTitle);
    }

    public static File buildPodcastPath(Context context, String podcastTitle) {
        String sanitizedPodcastTitle = sanitizeFilename(podcastTitle);
        File baseFolder = new File(getPreferredFilesDirs(context), FOLDER_UNZIPPED);
        return new File(baseFolder, sanitizedPodcastTitle);
    }

    public static String buildPodcastEpisodeName(PodcastEpisode episode) {
        String safeTitle = sanitizeFilename(episode.title);
        String safeDate = sanitizeFilename(episode.datePublishedPretty.replace(":","h"));
        return safeTitle + " - [" + safeDate + "].mp3";
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
        api.searchPodcasts(query, PODCASTINDEXORG_API_MAX_RESULTS, lang).enqueue(new retrofit2.Callback<PodcastIndexResponse>() {
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
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Podcast> autoList = AppDatabase.getDatabase(context).PodcastDao().getAutoDownloads();
            int i=0;
            for (Podcast podcast : autoList) {
                i=i+1;
                myLogD("checking new episodes for podcast " + i + " [" + podcast.title + "]");
                if (i > PODCASTINDEXORG_MAX_PODCAST_AUTO_DOWNLOAD) {
                    myLogW("Max number of podcasts to auto download reached, bypassing...");
                } else {
                    checkForNewEpisodesToAutoDownload(context, podcast, since);
                }
            }
        });
    }
    public static void checkForNewEpisodesToAutoDownload(Context context, Podcast podcast, long since) {
        getEpisodesByFeedId(podcast.feedId, since, new EpisodeCallback() {
            @Override
            public void onSuccess(List<PodcastEpisode> episodes) {
                File podcastFolder = buildPodcastPath(context, podcast);
                if (!podcastFolder.exists()) podcastFolder.mkdirs();

                List<PodcastEpisode> newEpisodes = new ArrayList<>();
                int i = 0;

                for (PodcastEpisode episode : episodes) {
                    /// EPISODES LOOP ////////////////////////////////////////////////////////
                    i++;
                    if (i > PODCASTINDEXORG_MAX_EPISODE_AUTO_DOWNLOAD) break;

                    String baseName = buildPodcastEpisodeName(episode);
                    File destFile = new File(podcastFolder, baseName);

                    if (!destFile.exists()) {
                        myLogD("Auto-download episode " + i + " - [" + baseName + "]");
                        newEpisodes.add(episode);
                    } else {
                        myLogD("episode " + i + " - [" + baseName + "] already exists");
                    }
                    /// EPISODES LOOP ////////////////////////////////////////////////////////
                }

                if (!newEpisodes.isEmpty()) {
                    AppDatabase.databaseWriteExecutor.execute(() -> {  //maybe Executors.newSingleThreadExecutor() will be better, or some background thread
                        PodcastDownloadManager.enqueueDownloads(context, podcast.feedId, newEpisodes, podcastFolder, null);
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                myLogEE(e, "Auto-download error for feedId " + podcast.feedId);
            }
        });
    }


    public static void cancelAutoDownload(Context c, int folderId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase.getDatabase(c).PodcastDao().updateAutoDownloadStatus_fromFolderId(folderId, false);
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
