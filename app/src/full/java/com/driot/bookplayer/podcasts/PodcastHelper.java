package com.driot.bookplayer.podcasts;

import com.driot.bookplayer.BuildConfig;
import static com.driot.bookplayer.helpers.FileHelper.sanitizeFilename;
import static com.driot.bookplayer.helpers.StorageHelper.getUnzipFolder;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.BackupManager;
import com.driot.bookplayer.db.Episode;
import com.driot.bookplayer.db.EpisodeDao;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.Sql;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.db.CommonZikFileDao;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.NetworkHelper;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import com.driot.bookplayer.player.StartPlayHelper;

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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

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
        String safeDate = sanitizeFilename(episode.datePublishedPretty.replace(":", "h"));
        return safeTitle + " - [" + safeDate + "].mp3";
    }

    public static String buildPodcastEpisodeName(DisplayableEpisode episode) {
        String safeTitle = sanitizeFilename(episode.title);
        String safeDate = sanitizeFilename(episode.datePublishedPretty.replace(":", "h"));
        return safeTitle + " - [" + safeDate + "].mp3";
    }

    public static String buildPodcastEpisodeFileName(PodcastEpisode episode) {
        return Var.PODCAST_SOURCE + "_" + episode.id + ".mp3";
    }

    public static String buildPodcastEpisodeFileName(DisplayableEpisode episode) {
        return Var.PODCAST_SOURCE + "_" + episode.idEpisode + ".mp3";
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
        if (file.exists())
            return file;

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
                // .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                // .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL) // e.g. https://<worker>.workers.dev/podcastindex/
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        return retrofit.create(PodcastIndexApi.class);
    }

    public static void searchPodcasts(String query, String lang, Callback callback) {
        PodcastIndexApi api = buildApi();
        api.searchPodcasts(query, Option.getPodcastIndexOrgApiNbResults(), lang)
                .enqueue(new retrofit2.Callback<PodcastIndexResponse>() {
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
                    } catch (Exception ignored) {
                    }
                    callback.onError(new Exception("HTTP " + response.code() + ": " + error));
                }
            }

            @Override
            public void onFailure(Call<PodcastIndexResponse> call, Throwable t) {
                callback.onError(new Exception(t));
            }
        });
    }

    public static void getEpisodesByFeedId(Context context, long feedId, long since, int max, boolean fullText,
            EpisodeCallback callback) {
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
            // myLogD("AutoDelete On");
        } else {
            // myLogD("AutoDelete Off");
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
            CommonZikFileDao zikFileDao = db.zikFileDao();
            EpisodeDao episodeDao = db.episodeDao();

            List<ZikFile> filesToDelete = zikFileDao.getListenedPodcastEpisodesToDelete(percent, thresholdTime);
            long deleteListSize = filesToDelete.size();
            myLogD("AutoDelete : " + deleteListSize + " Episodes to delete ... (thresholdTime=" + thresholdTime
                    + " from " + days + " days) + " + percent + "% completion");

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
                    // legacy
                    file = new File(path + "/" + zikFile.getName());
                    if (!file.exists() || !file.isFile()) {
                        myLogE("AutoDelete => Failed to locate file: " + path);
                        continue;
                    } else {
                        myLogW("legacy paths : path/name"); // 2025-10-09 (some 2 months old podcasts stays in my phone)
                    }
                }

                if (!file.delete()) {
                    myLogE("AutoDelete => Failed to delete file " + fsDeleted + 1 + "/" + deleteListSize + ": " + path);
                    continue;
                }

                // At this point, file was deleted
                myLogD("AutoDelete => Deleted file: " + path);
                fsDeleted++;

                long fileId = zikFile.getId();

                // update before delete because onCascade null
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

            if (dbDeleted != 0) {
                myLogI("AutoDelete => " + fsDeleted + "/" + dbDeleted + "/" + dbUpdated
                        + " old listened podcast episodes were deleted (thresholdTime=" + thresholdTime + " from "
                        + days + " days) + " + percent + "% completion");
                for (Long idFolder : foldersToUpdate) {
                    if (idFolder != null) {
                        Sql.updateFolderTable(context, idFolder.intValue());
                    }
                }
            }

        });
    }

    public static void checkForNewEpisodesToAutoDownload(Context context, long since) {
        if (Option.getNetworkPolicyAutoDownload().equals(NetworkHelper.NetworkPolicyAuto.NETWORK_POLICY_UNMETERED)
                && !NetworkHelper.isUnmeteredConnected(context)) {
            myLogD("Network policy prevents auto-download (Unmetered)");
            return;
        }
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Podcast> autoList = AppDatabase.getDatabase(context).podcastDao().getAutoDownloads();
            int i = 0;
            for (Podcast podcast : autoList) {
                i = i + 1;
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
                if (!podcastFolder.exists())
                    podcastFolder.mkdirs();

                List<PodcastEpisode> newEpisodes = new ArrayList<>();
                int i = 0;

                for (PodcastEpisode episode : podcastEpisodes) {
                    /// EPISODES LOOP ////////////////////////////////////////////////////////
                    i++;
                    if (i > maxEpisode)
                        break;

                    String episodeLabel = buildPodcastEpisodeName(episode);
                    String fileName = buildPodcastEpisodeFileName(episode);
                    File destFile = new File(podcastFolder, fileName);

                    if (!destFile.exists()) {
                        myLogD("Auto-download episode n°" + i + "/" + maxEpisode + " for [" + podcast.title + "] - ["
                                + episodeLabel + "] - [" + fileName + "]");
                        newEpisodes.add(episode);
                    } else {
                        myLogD("episode already exists - n°" + i + "/" + maxEpisode + " for [" + podcast.title + "] - ["
                                + episodeLabel + "] - [" + fileName + "]");
                    }
                    /// EPISODES LOOP ////////////////////////////////////////////////////////
                }

                if (!newEpisodes.isEmpty()) {
                    AppDatabase.databaseWriteExecutor.execute(() -> { // maybe Executors.newSingleThreadExecutor() will
                                                                      // be better, or some background thread
                        List<Episode> toSave = PodcastHelper.convertToEpisodes(podcastEpisodes, podcast.getId());
                        AppDatabase.getDatabase(context).episodeDao().insertAll(toSave);
                        PodcastDownloadManager.enqueueDownloads(context, podcast.feedId, newEpisodes, podcastFolder,
                                null);
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
            if (podcastFeed.image != null && podcastFeed.image.startsWith("http")) {
                podcast.imageOriginalUrl = podcastFeed.image;
            }
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
        if (feed.image != null && feed.image.startsWith("http")) {
            p.imageOriginalUrl = feed.image;
        }
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
            if (pe.image != null && pe.image.startsWith("http")) {
                ep.imageOriginalUrl = pe.image;
            }
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
                    System.currentTimeMillis());
        });
    }

    public static void deleteEpisode(int id, Context context) {
        Episode episode = AppDatabase.getDatabase(context.getApplicationContext()).episodeDao().getByZikFileId(id);
        if (episode != null) {
            episode.date_delete = System.currentTimeMillis();
            AppDatabase.getDatabase(context.getApplicationContext()).episodeDao().update(episode);
        }
    }

    /**
     * Gets the path to the original cover for a podcast folder.
     * Podcast covers are saved as podcast_feed_{feedId}.jpg
     * 
     * @param context  Android context
     * @param folderId Database ID of the folder
     * @return Absolute path to original cover, or null if not found
     */
    @androidx.annotation.Nullable
    public static String getPodcastOriginalCoverPath(Context context, int folderId) {
        // LEGACY
        Podcast podcast = AppDatabase.getDatabase(context.getApplicationContext()).podcastDao()
                .getPodcastByFolderId(folderId);
        if (podcast == null)
            return null;

        File dir = com.driot.bookplayer.helpers.StorageHelper.getImageFolder(context, true);
        File jpgFile = new File(dir, ImageHelper.IMAGE_PREFIX_FOR_PODCAST_COVERS + podcast.feedId + ".jpg");

        if (jpgFile.exists()) {
            return jpgFile.getAbsolutePath();
        }

        dir = com.driot.bookplayer.helpers.StorageHelper.getImageFolder(context, false);
        jpgFile = new File(dir, ImageHelper.IMAGE_PREFIX_FOR_PODCAST_COVERS + podcast.feedId + ".jpg");

        if (jpgFile.exists()) {
            return jpgFile.getAbsolutePath();
        }

        return null;
    }

    public static void openPodcastEpisodeActivityFromActivity(Folder folder, Activity activity) {
        AppDatabase.databaseReadExecutor.execute(() -> {
            Podcast podcast = AppDatabase.getDatabase(activity.getApplicationContext()).podcastDao()
                    .getPodcastByFolderId(folder.getId());
            if (podcast != null) {
                myLogD("opening PodcastEpisodeActivity for podcast : " + podcast.title);
                activity.startActivity(new Intent(activity, PodcastEpisodeActivity.class).putExtra("podcast", podcast));
            } else {
                myLogI("No podcast linked to folder " + folder.getId());
            }
        });
    }

    @androidx.annotation.Nullable
    public static String getPodcastOriginalCoverUrl(Context context, int folderId) {
        Podcast podcast = AppDatabase.getDatabase(context.getApplicationContext()).podcastDao()
                .getPodcastByFolderId(folderId);
        if (podcast != null) {
            return podcast.imageOriginalUrl;
        }
        return null;
    }

    public static void handlePodcastImages(Context context, long currentTime) {
        if (NetworkHelper.hasInternet(context)) {
            AppDatabase db = AppDatabase.getDatabase(context.getApplicationContext());
            List<Podcast> pendingPodcasts = db.podcastDao()
                    .getAllWithExternalImagesUnchangedSince24h(currentTime);
            for (Podcast podcast : pendingPodcasts) {
                myLog("caching podcast image for: " + podcast.title);
                String url = podcast.image;
                if (url == null || !url.startsWith("http")) {
                    myLogE("caching podcast image for: " + podcast.title + " => bad URL");
                    podcast.date_maj = System.currentTimeMillis();
                    db.podcastDao().update(podcast);
                    continue;
                }
                String imagePath = ImageHelper.IMAGE_PREFIX_FOR_PODCAST_COVERS + podcast.feedId + ".jpg";
                String localPath = ImageHelper.downloadAndVerifyImage(context, url, imagePath, true);
                if (localPath != null) {
                    podcast.image = localPath;
                } else {
                    myLogW("caching podcast image for: " + podcast.title + " => failed or invalid");
                }
                podcast.date_maj = System.currentTimeMillis();
                db.podcastDao().update(podcast);
            }
        }
    }

    public static void startPlayOpenPodcast(Folder folder, Context context) {
        Podcast p = AppDatabase.getDatabase(context).podcastDao()
                .getPodcastByFolderId(folder.getId());
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            context.startActivity(new Intent(context, PodcastEpisodeActivity.class).putExtra("podcast", p));
        });
    }

    public static void onPodcastClick(Context context, DisplayableEpisode ep, Podcast podcast, String caller) {
        String cover = ep.image == null || ep.image.isEmpty() ? podcast.image : ep.image;
        StartPlayHelper.playStream(context, Var.PLAY_MODE_PODCAST, ep.enclosureUrl, podcast.feedId, null, ep.title,
                cover, caller);
    }

    public static List<ZikFile> getPodcastZikFiles(Folder folder, Context context, boolean newestFirst) {
        if (newestFirst) {
            return AppDatabase.getDatabase(context.getApplicationContext()).zikFileDao()
                    .getPodcastZikFilesDesc(folder.getId());
        } else {
            return AppDatabase.getDatabase(context.getApplicationContext()).zikFileDao()
                    .getPodcastZikFilesAsc(folder.getId());
        }
    }

    public static boolean playStreamIfKnownPodcast(Context context, String url) {
        Episode episode = AppDatabase.getDatabase(context.getApplicationContext()).episodeDao().getFromUrl(url);
        if (episode != null) {
            String title = episode.title;
            String imageUrl = episode.image;
            // TODO => we need a position, or it will start the episode from the
            // beggining....
            // broadcastUiState("loadAndPlay");
            // main.post(() -> {
            StartPlayHelper.playStream(context, Var.PLAY_MODE_RADIO, url, -1, null, title, imageUrl, null);
            /*
             * boolean ok = playStream(Var.PLAY_MODE_PODCAST, url, title, imageUrl);
             * if (!ok) {
             * myLogEE(null, "loadAndPlayFromStorage(): playback failed - podcast");
             * }
             * 
             */
            // });

            return true;
        } else {
            return false;
        }
    }

    public static void deletePodcastFolder(int folderId, Context context) {
        AppDatabase db = AppDatabase.getDatabase(context.getApplicationContext());
        Podcast podcast = db.podcastDao().getPodcastByFolderId(folderId);
        if (podcast == null) {
            Folder f = db.folderDao().getById(folderId); // ensure DAO exists
            if (f != null)
                ImageHelper.deleteImage(context.getApplicationContext(), f);
        }

        List<ZikFile> zikFileList = db.zikFileDao().getZikFiles(folderId);
        for (ZikFile zikFile : zikFileList) {
            Episode episode = db.episodeDao().getByZikFileId(zikFile.getId());
            if (episode != null) {
                episode.date_delete = System.currentTimeMillis();
                db.episodeDao().update(episode);
                myLogD("Podcast Episode date deleted set for " + episode.title);
            }
        }
    }

    public static void doAutoDownloadAndDelete(Context context) {
        final int nbPodcastAutoDownload = AppDatabase.getDatabase(context).podcastDao().getNbAutoDownload();
        /// Podcasts AutoDownload
        if (nbPodcastAutoDownload > 0 && (Pref.doCheckForPodcastAutoDownload() || Var.FORCE_AUTO_DOWNLOAD_NO_DELAY)) {
            if (NetworkHelper.hasInternet(context)) {
                PodcastHelper.checkForNewEpisodesToAutoDownload(context, Var.PODCAST_INDEX_ORG_SINCE);
            } else {
                myLogD("no internet => bypassing podcast auto-download");
            }
        }
        /// Podcasts AutoDelete
        PodcastHelper.checkForEpisodesToAutoDelete(context);
    }

    public static boolean backupDataHasPodcasts(BackupManager.BackupData data) {
        return (data.podcasts != null && !data.podcasts.isEmpty());
    }

    public static void updateImage(int folderId, String imagePath, Context context) {
        AppDatabase.getDatabase(context.getApplicationContext()).podcastDao().updateImageForFolderId(folderId, imagePath);
    }

    public static void addSecondToTimeListened(Context context, int trackId) {
        AppDatabase db = AppDatabase.getDatabase(context.getApplicationContext());
        db.episodeDao().addSecondToTimeListened(trackId);
    }

}
