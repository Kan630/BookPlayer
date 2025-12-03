package com.driot.bookplayer.helpers;

import com.driot.bookplayer.BuildConfig;
import static com.driot.bookplayer.helpers.FileHelper.sanitizeFilename;
import static com.driot.bookplayer.helpers.StorageHelper.getUnzipFolder;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Episode;
import com.driot.bookplayer.db.EpisodeDao;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.Sql;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.db.ZikFileDao;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.objects.DisplayableEpisode;
import com.driot.bookplayer.objects.PodcastEpisode;
import com.driot.bookplayer.objects.PodcastEpisodeResponse;
import com.driot.bookplayer.objects.PodcastIndexApi;
import com.driot.bookplayer.objects.PodcastFeed;
import com.driot.bookplayer.objects.PodcastIndexResponse;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;
import com.driot.bookplayer.utils.PodcastDownloadManager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    private static final String BASE_URL = BuildConfig.PODCASTINDEX_BASE_URL;

    public interface Callback {
        void onSuccess(List<PodcastFeed> feeds);
        void onError(Exception e);
    }

    public static File buildPodcastPath(Context context, Podcast podcast) {
        return buildPodcastPath(context, podcast.title);
    }

    public static File buildPodcastPath(Context context, String podcastTitle) {
        return buildPodcastPath(context, podcastTitle, Option.getUseSdCard());
    }

    public static File buildPodcastPath(Context context, String podcastTitle, boolean forceSdCard) {
        String sanitizedTitle = sanitizeFilename(podcastTitle);
        File unzipFolder = getUnzipFolder(context, forceSdCard);
        return new File(unzipFolder, sanitizedTitle);
    }

    public static String buildPodcastEpisodeName(PodcastEpisode episode) {
        String safeTitle = sanitizeFilename(episode.title);
        String safeDate = sanitizeFilename(episode.datePublishedPretty.replace(":","h"));
        return safeTitle + " - [" + safeDate + "].mp3";
    }

    public static String buildPodcastEpisodeName(DisplayableEpisode episode) {
        String safeTitle = sanitizeFilename(episode.title);
        String safeDate = sanitizeFilename(episode.datePublishedPretty.replace(":","h"));
        return safeTitle + " - [" + safeDate + "].mp3";
    }

    public static String buildPodcastEpisodeFileName(PodcastEpisode episode) {
        return Var.PODCAST_SOURCE + "_" +  episode.id + ".mp3";
    }

    public static String buildPodcastEpisodeFileName(DisplayableEpisode episode) {
        return Var.PODCAST_SOURCE + "_" +  episode.idEpisode + ".mp3";
    }

    public static long getEpisodeIdFromName(String fileName) {
        try {
            if (fileName == null || !fileName.startsWith(Var.PODCAST_SOURCE + "_") || !fileName.endsWith(".mp3")) {
                return -1;
            }

            String idPart = fileName
                    .substring((Var.PODCAST_SOURCE + "_").length(), fileName.length() - ".mp3".length());

            return Long.parseLong(idPart);
        } catch (Exception e) {
            return -1; // or throw if you prefer
        }
    }


    public static File findPodcastEpisodeFileIfExists(Context context, String podcastTitle, String episodeFileName) {
        // Try internal storage first
        File file = new File(buildPodcastPath(context, podcastTitle, false), episodeFileName);
        if (file.exists()) return file;

        // Then try SD card
        file = new File(buildPodcastPath(context, podcastTitle, true), episodeFileName);
        return file.exists() ? file : null;
    }

    public static PodcastIndexApi buildApi() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(Var.HTTP_LOGGING_INTERCEPTOR_LOG_LEVEL);

        // Optional: add the shared token header so your Worker accepts the call
        Interceptor appTokenInterceptor = chain -> {
            Request.Builder b = chain.request().newBuilder()
                    .header("User-Agent", Var.USER_AGENT_BOOKPLAYER);
            String tok = BuildConfig.APP_TOKEN;
            if (tok != null && !tok.isEmpty()) {
                b.header("x-app-auth", tok);
            }
            return chain.proceed(b.build());
        };

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor(appTokenInterceptor)
                // (optional) timeouts if you want:
                //.connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                //.readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)   // e.g. https://<worker>.workers.dev/podcastindex/
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        return retrofit.create(PodcastIndexApi.class);
    }


    public static void searchPodcasts(String query, String lang, Callback callback) {
        PodcastIndexApi api = buildApi();
        api.searchPodcasts(query, Option.getPodcastIndexOrgApiNbResults(), lang).enqueue(new retrofit2.Callback<PodcastIndexResponse>() {
        @Override
        public void onResponse(Call<PodcastIndexResponse> call, Response<PodcastIndexResponse> response) {
            if (response.isSuccessful() && response.body() != null) {
                myLogD("response.isSuccessful() && response.body() != null");
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

    public static void getEpisodesByFeedId(Context context, long feedId, long since, int max, boolean fullText, EpisodeCallback callback) {
        PodcastIndexApi api = buildApi();
        myLog("API call");
        api.getEpisodesByFeedId(feedId, since, max, fullText).enqueue(new retrofit2.Callback<PodcastEpisodeResponse>() {
            @Override
            public void onResponse(Call<PodcastEpisodeResponse> call, Response<PodcastEpisodeResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body().items);
                    updateLastCheck(context, feedId);
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

    public static void checkForEpisodesToAutoDelete(Context context) {
        if (Option.getPodcastAutoDelete()) {
            myLogD("AutoDelete On");
        } else {
            myLogD("AutoDelete Off");
            return;
        }

        long days = Option.getPodcastAutoDeleteDelay(); // e.g. 7
        int percent = Option.getPodcastAutoDeleteCompletionPercentage(); // e.g. 95

        if (days < 0 || percent < 10) {
            myLogE("AutoDelete bad values : days=" + days + " percent=" + percent);
            return;
        }

        long now = System.currentTimeMillis();
        long thresholdTime = now - days * 24L * 60 * 60 * 1000;

        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(context);
            ZikFileDao zikFileDao = db.zikFileDao();
            EpisodeDao episodeDao = db.episodeDao();

            List<ZikFile> filesToDelete = zikFileDao.getListenedPodcastEpisodesToDelete(percent, thresholdTime);
            long deleteListSize = filesToDelete.size();
            myLogD("AutoDelete : " + deleteListSize + " Episodes to delete ... (thresholdTime=" + thresholdTime + " from " + days + " days) + " + percent + "% completion");

            int fsDeleted = 0;
            int dbDeleted = 0;
            int dbUpdated = 0;

            Set<Long> foldersToUpdate = new HashSet<>();

            for (ZikFile zikFile : filesToDelete) {
                String path = zikFile.getPath();
                if (path == null) {
                    myLogE("AutoDelete => path is null");
                    continue;
                }

                File file = new File(path);
                if (!file.exists() || !file.isFile()) {
                    //legacy
                    file = new File(path + "/" + zikFile.getName());
                    if (!file.exists() || !file.isFile()) {
                        myLogE("AutoDelete => Failed to locate file: " + path);
                        continue;
                    } else {
                        myLogW("legacy paths : path/name"); //2025-10-09 (some 2 months old podcasts stays in my phone)
                    }
                }

                if (!file.delete()) {
                    myLogE("AutoDelete => Failed to delete file " + fsDeleted+1 + "/" + deleteListSize + ": " + path);
                    continue;
                }

                // At this point, file was deleted
                myLogD("AutoDelete => Deleted file: " + path);
                fsDeleted++;

                long fileId = zikFile.getId();

                //update before delete because onCascade null
                int updated = episodeDao.updateDateDeleteForZikFileId(fileId, System.currentTimeMillis());
                if (updated == 0) {
                    myLogE("AutoDelete => Failed to update in DB Episode: " + fileId);
                    continue;
                }
                dbUpdated++;

                int deletedZik = zikFileDao.deleteById(fileId);
                if (deletedZik == 0) {
                    myLogE("AutoDelete => Failed to delete in DB ZikFile: " + fileId);
                    continue;
                }
                dbDeleted++;
                foldersToUpdate.add((long) zikFile.getIdFolder());
            }

            if (dbDeleted!=0) {
                myLogI("AutoDelete => " + fsDeleted + "/" + dbDeleted + "/" + dbUpdated + " old listened podcast episodes were deleted (thresholdTime=" + thresholdTime + " from " + days + " days) + " + percent + "% completion");
                for (Long idFolder : foldersToUpdate) {
                    if (idFolder != null) {
                        Sql.updateFolderTable(context, idFolder.intValue());
                    }
                }
            }

        });
    }

    public static void checkForNewEpisodesToAutoDownload(Context context, long since) {
        if (Option.getNetworkPolicyAutoDownload().equals(NetworkHelper.NetworkPolicyAuto.NETWORK_POLICY_UNMETERED) && !NetworkHelper.isUnmeteredConnected(context)) {
            myLogD("Network policy prevents auto-download (Unmetered)");
            return;
        }
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Podcast> autoList = AppDatabase.getDatabase(context).podcastDao().getAutoDownloads();
            int i=0;
            for (Podcast podcast : autoList) {
                i=i+1;
                myLogD("checking new episodes for podcast " + i + " [" + podcast.title + "]");
                if (i > Option.getPodcastAutoDownloadMaxNbPodcast()) {
                    myLogW("Max number of podcasts to auto download reached, bypassing...");
                } else {
                    checkForNewEpisodesToAutoDownloadForPodcast(context, podcast, since);
                }
            }
        });
    }
    public static void checkForNewEpisodesToAutoDownloadForPodcast(Context context, Podcast podcast, long since) {
        int maxEpisode = Option.getPodcastAutoDownloadLastNbEpisode();
        getEpisodesByFeedId(context, podcast.feedId, since, maxEpisode, true, new EpisodeCallback() {
            @Override
            public void onSuccess(List<PodcastEpisode> podcastEpisodes) {

                File podcastFolder = buildPodcastPath(context, podcast);
                if (!podcastFolder.exists()) podcastFolder.mkdirs();

                List<PodcastEpisode> newEpisodes = new ArrayList<>();
                int i = 0;

                for (PodcastEpisode episode : podcastEpisodes) {
                    /// EPISODES LOOP ////////////////////////////////////////////////////////
                    i++;
                    if (i > maxEpisode) break;

                    String episodeLabel = buildPodcastEpisodeName(episode);
                    String fileName = buildPodcastEpisodeFileName(episode);
                    File destFile = new File(podcastFolder, fileName);

                    if (!destFile.exists()) {
                        myLogD("Auto-download episode n°" + i + "/" + maxEpisode + " for [" + podcast.title + "] - [" + episodeLabel + "] - [" + fileName + "]");
                        newEpisodes.add(episode);
                    } else {
                        myLogD("episode already exists - n°" + i + "/" + maxEpisode + " for [" + podcast.title + "] - [" + episodeLabel + "] - [" + fileName + "]");
                    }
                    /// EPISODES LOOP ////////////////////////////////////////////////////////
                }

                if (!newEpisodes.isEmpty()) {
                    AppDatabase.databaseWriteExecutor.execute(() -> {  //maybe Executors.newSingleThreadExecutor() will be better, or some background thread
                        List<Episode> toSave = PodcastHelper.convertToEpisodes(podcastEpisodes, podcast.getId());
                        AppDatabase.getDatabase(context).episodeDao().insertAll(toSave);
                        PodcastDownloadManager.enqueueDownloads(context, podcast.feedId, newEpisodes, podcastFolder, null);
                    });
                }
                updateLastCheck(context, podcast.feedId);

            }

            @Override
            public void onError(Exception e) {
                myLogEE(e, "Auto-download error for feedId " + podcast.feedId);
            }
        });
    }


    public static void cancelAutoDownload(Context c, int folderId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase.getDatabase(c).podcastDao().updateAutoDownloadStatus_fromFolderId(folderId, false);
        });
    }

    public static void addPodcastToDB(Context context, PodcastFeed podcastFeed) {
        Podcast podcast = AppDatabase.getDatabase(context).podcastDao().getPodcastByFeedId(podcastFeed.id);
        if (podcast == null) {
            podcast = new Podcast();
            podcast.source = Var.PODCAST_SOURCE;
            podcast.feedId = podcastFeed.id;
            podcast.title = podcastFeed.title;
            podcast.image = podcastFeed.image;
            podcast.imageOriginalUrl = podcastFeed.image;
            podcast.description = podcastFeed.description;
            podcast.isFavorite = false;
            podcast.autoDownload = false;
            AppDatabase.getDatabase(context).podcastDao().insert(podcast);
            myLogD("Podcast added to DB: " + podcast.feedId + " " + podcast.title);
        }
    }
    public static Podcast fromPodcastFeed(PodcastFeed feed) {
        Podcast p = new Podcast();
        p.feedId = feed.id;
        p.title = feed.title;
        p.image = feed.image;
        p.imageOriginalUrl = feed.image;
        p.description = feed.description;
        p.language = feed.language;
        p.source = Var.PODCAST_SOURCE;
        p.date_added = System.currentTimeMillis();
        return p;
    }

    // FOR INSERT IN DB
    public static List<Episode> convertToEpisodes(List<PodcastEpisode> podcastEpisodes, long idPodcast) {
        long now = System.currentTimeMillis();
        List<Episode> result = new ArrayList<>();
        for (PodcastEpisode pe : podcastEpisodes) {
            Episode ep = new Episode();
            ep.idPodcast = idPodcast;
            ep.date_add = now;
            ep.idEpisode = pe.id;
            ep.description = pe.description;
            ep.title = pe.title;
            ep.image = pe.image;
            ep.guid = pe.guid;
            ep.enclosureUrl = pe.enclosureUrl;
            ep.datePublished = pe.datePublished;
            ep.duration = pe.duration;
            ep.enclosureLength = pe.enclosureLength;
            result.add(ep);
        }
        return result;
    }

    private static void updateLastCheck(Context context, long feedId) {
        // update lastCheck in table for that podcast
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase.getDatabase(context).podcastDao().updateLastCheck(
                    feedId,
                    System.currentTimeMillis()
            );
        });
    }

}
