package com.driot.bookplayer.podcasts;

import com.driot.bookplayer.BuildConfig;
import static com.driot.bookplayer.helpers.FileHelper.sanitizeFilename;
import static com.driot.bookplayer.helpers.StorageHelper.getUnzipFolder;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.BackupManager;
import com.driot.bookplayer.db.Episode;
import com.driot.bookplayer.db.EpisodeDao;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.db.PodcastDao;
import com.driot.bookplayer.db.Sql;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.db.CommonZikFileDao;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.player.PlayList;
import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.NetworkHelper;
import com.driot.bookplayer.helpers.ShareHelper;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import com.driot.bookplayer.player.StartPlayHelper;
import com.driot.bookplayer.utils.log.LoggerStaticHelper;

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
import android.net.Uri;
import android.text.TextUtils;

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
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(LoggerStaticHelper::myLog);
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
          synchronized (EPISODE_ROWS_LOCK) {
            AppDatabase db = AppDatabase.getDatabase(context);
            CommonZikFileDao zikFileDao = db.zikFileDao();
            EpisodeDao episodeDao = db.episodeDao();

            List<ZikFile> filesToDelete = zikFileDao.getListenedPodcastEpisodesToDelete(percent, thresholdTime);
            long deleteListSize = filesToDelete.size();
            myLogD("AutoDelete : " + deleteListSize + " Episodes to delete ... (thresholdTime=" + thresholdTime
                    + " from " + days + " days) + " + percent + "% completion");

            int fsDeleted = 0;
            int dbDeleted = 0;

            Set<Long> foldersToUpdate = new HashSet<>();

            for (ZikFile listed : filesToDelete) {
                int outcome = deleteDownloadedEpisode(db, listed.getId(), "AutoDelete");
                if (outcome == EPISODE_KEPT)
                    continue;
                if (outcome == EPISODE_FILE_AND_ROW_DELETED)
                    fsDeleted++;
                dbDeleted++;
                foldersToUpdate.add((long) listed.getIdFolder());
            }

            if (dbDeleted != 0) {
                myLogI("AutoDelete => " + fsDeleted + " file(s)/" + dbDeleted + " row(s)"
                        + " old listened podcast episodes were deleted (thresholdTime=" + thresholdTime + " from "
                        + days + " days) + " + percent + "% completion");
                for (Long idFolder : foldersToUpdate) {
                    if (idFolder != null) {
                        Sql.updateFolderTable(context, idFolder.intValue());
                    }
                }
            }
          }
        });
    }

    /** One category of the manual episode cleanup: the downloaded episodes it would remove. */
    public static final class EpisodeCleanupGroup {
        public final List<Long> zikFileIds = new java.util.ArrayList<>();
        public long bytes;
    }

    /** What each cleanup choice would remove in one podcast folder (PodcastEpisodeCleanupSheet). */
    public static final class EpisodeCleanupPreview {
        public final EpisodeCleanupGroup listened = new EpisodeCleanupGroup();
        public final EpisodeCleanupGroup neverPlayed = new EpisodeCleanupGroup();
        public final EpisodeCleanupGroup untouched = new EpisodeCleanupGroup();
        public int episodeCount;
        public long totalBytes;
        public int listenedPercent;
    }

    /**
     * Sorts a podcast folder's downloaded episodes into the manual cleanup choices:
     * listened (finished, or played past the auto-delete completion %), never played (no progress,
     * no listening time), and untouched for untouchedMonths (neither downloaded nor played since).
     * The episode currently loaded in the player is never included. Reads the disk (sizes): call
     * off the main thread.
     */
    public static EpisodeCleanupPreview previewEpisodeCleanup(Context context, long folderId, int untouchedMonths) {
        int percent = Option.getPodcastAutoDeleteCompletionPercentage();
        return previewEpisodeCleanup(context, folderId, untouchedMonths, (percent >= 10 && percent <= 100) ? percent : 95);
    }

    /** Same, with an explicit "listened" threshold (radio recordings: no auto-delete setting). */
    public static EpisodeCleanupPreview previewEpisodeCleanup(Context context, long folderId, int untouchedMonths,
            int listenedPercent) {
        EpisodeCleanupPreview preview = new EpisodeCleanupPreview();
        preview.listenedPercent = listenedPercent;

        java.util.Calendar cutoff = java.util.Calendar.getInstance();
        cutoff.add(java.util.Calendar.MONTH, -untouchedMonths);
        long untouchedBefore = cutoff.getTimeInMillis();

        long playingId = -1;
        PlayList pl = PlayList.getInstance();
        if (pl != null && !pl.isStream() && pl.getZikFile() != null)
            playingId = pl.getZikFile().getId();

        for (ZikFile z : AppDatabase.getDatabase(context.getApplicationContext()).zikFileDao().getZikFiles(folderId)) {
            File f = resolveEpisodeFile(z);
            long size = f != null ? f.length() : 0;
            preview.episodeCount++;
            preview.totalBytes += size;
            if (z.getId() == playingId)
                continue;

            if (z.isFinished() || z.getPercentdone() >= preview.listenedPercent)
                add(preview.listened, z, size);
            if (!z.isFinished() && z.getPercentdone() <= 0 && z.timeListened <= 0)
                add(preview.neverPlayed, z, size);
            long lastTouched = Math.max(z.date_added, z.lLastAccess != null ? z.lLastAccess : 0);
            if (lastTouched > 0 && lastTouched < untouchedBefore)
                add(preview.untouched, z, size);
        }
        return preview;
    }

    private static void add(EpisodeCleanupGroup group, ZikFile z, long size) {
        group.zikFileIds.add((long) z.getId());
        group.bytes += size;
    }

    /** Outcome of deleteDownloadedEpisodes(). */
    public static final class EpisodeCleanupResult {
        public int removed;
        public long freedBytes;
    }

    /**
     * Deletes the given downloaded episodes of one podcast folder, each exactly like AutoDelete
     * does (deleteDownloadedEpisode: listening time kept, shared files kept, unreachable storage
     * left alone). Call off the main thread.
     */
    public static EpisodeCleanupResult deleteDownloadedEpisodes(Context context, long folderId, List<Long> zikFileIds) {
        EpisodeCleanupResult result = new EpisodeCleanupResult();
        synchronized (EPISODE_ROWS_LOCK) {
            AppDatabase db = AppDatabase.getDatabase(context.getApplicationContext());
            for (long id : zikFileIds) {
                ZikFile z = db.zikFileDao().getById(id);
                File f = z != null ? resolveEpisodeFile(z) : null;
                long size = f != null ? f.length() : 0;
                int outcome = deleteDownloadedEpisode(db, id, "Cleanup");
                if (outcome == EPISODE_KEPT)
                    continue;
                result.removed++;
                if (outcome == EPISODE_FILE_AND_ROW_DELETED)
                    result.freedBytes += size;
            }
        }
        if (result.removed > 0)
            Sql.updateFolderTable(context, folderId);
        myLogI("Cleanup => " + result.removed + "/" + zikFileIds.size() + " episode(s) removed from folder "
                + folderId + ", " + result.freedBytes + " bytes freed");
        return result;
    }

    /** Fragment result posted by the episode cleanup sheet after a deletion (host refreshes). */
    public static final String EPISODE_CLEANUP_RESULT_KEY = "podcast_episode_cleanup_done";

    /** Long press on a podcast in the Clean screen: opens the episode cleanup sheet. */
    public static void showEpisodeCleanup(androidx.fragment.app.Fragment host, long folderId, String podcastName,
            @androidx.annotation.Nullable String image) {
        PodcastEpisodeCleanupSheet.newInstance(folderId, podcastName, image, false)
                .show(host.getChildFragmentManager(), PodcastEpisodeCleanupSheet.TAG);
    }

    /** Long press on a radio recordings folder in the Clean screen: same sheet, recordings wording. */
    public static void showRecordingCleanup(androidx.fragment.app.Fragment host, long folderId, String name,
            @androidx.annotation.Nullable String image) {
        PodcastEpisodeCleanupSheet.newInstance(folderId, name, image, true)
                .show(host.getChildFragmentManager(), PodcastEpisodeCleanupSheet.TAG);
    }

    // What deleteDownloadedEpisode() did with one row.
    private static final int EPISODE_KEPT = 0;                 // nothing done (see log)
    private static final int EPISODE_MERGED = 1;               // duplicate row merged, file kept
    private static final int EPISODE_ROW_DELETED = 2;          // file was already gone, row removed
    private static final int EPISODE_FILE_AND_ROW_DELETED = 3;

    /**
     * Removes one downloaded episode: its file, then its row (listening time kept on the Episode,
     * which shows as not downloaded again - removeEpisodeRow). When other rows use the same file
     * (duplicate rows left by concurrent syncs, see FinalizeDownloadWorker) only this row goes,
     * merged into one of them, and the file stays: deleting it would leave them pointing at
     * nothing ("could not find the file"). A missing file whose folder is unreachable (unmounted
     * SD card) keeps its row. Shared by AutoDelete and the manual cleanup
     * (PodcastEpisodeCleanupSheet). Caller holds EPISODE_ROWS_LOCK, off the main thread.
     */
    private static int deleteDownloadedEpisode(AppDatabase db, long zikFileId, String logPrefix) {
        // Fresh read: an earlier merge in the same pass may have changed this row.
        ZikFile zikFile = db.zikFileDao().getById(zikFileId);
        if (zikFile == null || zikFile.getPath() == null)
            return EPISODE_KEPT;
        String path = zikFile.getPath();

        File file = resolveEpisodeFile(zikFile);
        if (file == null) {
            if (!isEpisodeFolderReachable(zikFile)) {
                myLogW(logPrefix + " => folder not reachable (storage unmounted?), row kept: " + path);
                return EPISODE_KEPT;
            }
            myLogE(logPrefix + " => file already missing on disk, cleaning up DB only: " + path);
        }

        File sameFile = (file != null) ? file : new File(path);
        List<ZikFile> others = db.zikFileDao().getReferencing(sameFile.getAbsolutePath(),
                sameFile.getParent(), sameFile.getName(), zikFileId);
        if (!others.isEmpty()) {
            ZikFile survivor = pickDuplicateSurvivor(others, db.episodeDao());
            db.runInTransaction(() -> mergeDuplicateInto(db, zikFile, survivor));
            myLogW(logPrefix + " => duplicate row " + zikFileId + " merged into " + survivor.getId()
                    + " (" + others.size() + " other row(s) use the file, kept): " + path);
            return EPISODE_MERGED;
        }

        if (file != null) {
            if (!file.delete()) {
                myLogE(logPrefix + " => Failed to delete file: " + path);
                return EPISODE_KEPT;
            }
            myLogD(logPrefix + " => Deleted file: " + path);
        }
        if (db.runInTransaction(() -> removeEpisodeRow(db, zikFile)) < 0)
            return EPISODE_KEPT;
        return file != null ? EPISODE_FILE_AND_ROW_DELETED : EPISODE_ROW_DELETED;
    }

    // AutoDelete and the one-time repair both merge/delete episode rows: never both at once
    // (databaseWriteExecutor has several threads).
    private static final Object EPISODE_ROWS_LOCK = new Object();

    // The file a podcast row plays: its full path, or (legacy rows) folder path + name. Null if
    // neither is on disk.
    @androidx.annotation.Nullable
    private static File resolveEpisodeFile(ZikFile z) {
        if (z.getPath() == null)
            return null;
        File f = new File(z.getPath());
        if (f.isFile())
            return f;
        File legacy = new File(f, z.getName());
        return legacy.isFile() ? legacy : null;
    }

    // A missing file only counts as deleted if its folder is still there: an unmounted SD card makes
    // every file on it look missing, and those rows must survive until it comes back.
    private static boolean isEpisodeFolderReachable(ZikFile z) {
        File f = new File(z.getPath());
        File dir = f.getParentFile();
        return f.isDirectory() || (dir != null && dir.isDirectory());
    }

    // Removes a podcast row whose file is gone (or just deleted). Its listening time moves to the
    // Episode first (which outlives the row), so the podcast's "listened" total doesn't drop -
    // found by the link, else by the id in the file name. Then the Episode is marked deleted
    // (update before delete: the link is SET NULL with the row) and the row goes. A row no Episode
    // links to (an unlinked duplicate) is still deleted: skipping it left a row whose file was gone.
    // Returns the number of Episodes marked deleted (0 or 1), -1 if the row could not be deleted.
    // Run in a transaction.
    private static int removeEpisodeRow(AppDatabase db, ZikFile z) {
        EpisodeDao episodeDao = db.episodeDao();
        long id = z.getId();
        if (z.timeListened > 0 && episodeDao.addTimeListenedForZikFileId(id, z.timeListened) == 0) {
            long idEpisode = getEpisodeIdFromName(z.getName());
            if (idEpisode > 0)
                episodeDao.addTimeListenedForEpisodeId(idEpisode, z.timeListened);
        }
        int updated = episodeDao.updateDateDeleteForZikFileId(id, System.currentTimeMillis());
        if (updated == 0)
            myLogW("no Episode linked to ZikFile " + id + ", deleting the row anyway");
        if (db.zikFileDao().deleteById(id) == 0) {
            myLogE("Failed to delete in DB ZikFile: " + id);
            return -1;
        }
        return updated;
    }

    /**
     * One-time repair (AppUpgrade) of the rows left by concurrent podcast syncs, see
     * FinalizeDownloadWorker: several rows for one file, and rows whose file AutoDelete removed
     * while a twin row stayed ("could not find the file"). Per file of a podcast folder:
     * duplicates are merged into one row (mergeDuplicateInto: nothing the user sees is lost); if
     * the file is gone, that row is then removed like AutoDelete does (removeEpisodeRow: listening
     * time kept on the Episode, which shows as not downloaded again). Also clears the "deleted"
     * mark of episodes downloaded again after being deleted. Files on disk are never touched.
     * Call off the main thread.
     */
    public static void repairDuplicateAndGhostEpisodeRows(Context context) {
        synchronized (EPISODE_ROWS_LOCK) {
            AppDatabase db = AppDatabase.getDatabase(context.getApplicationContext());
            EpisodeDao episodeDao = db.episodeDao();

            // Same folder + same file: rows in different folders are different books, left alone.
            java.util.Map<String, List<ZikFile>> byFile = new java.util.LinkedHashMap<>();
            for (ZikFile z : db.zikFileDao().getAllPodcastZikFiles()) {
                if (z.getPath() == null)
                    continue;
                File f = resolveEpisodeFile(z);
                String key = z.getIdFolder() + "|" + (f != null ? f.getAbsolutePath()
                        : new File(z.getPath()).isDirectory() ? z.getPath() + "/" + z.getName() : z.getPath());
                byFile.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(z);
            }

            int merged = 0, removed = 0, skipped = 0;
            Set<Long> folders = new HashSet<>();
            for (List<ZikFile> rows : byFile.values()) {
                boolean exists = resolveEpisodeFile(rows.get(0)) != null;
                if (exists && rows.size() == 1)
                    continue;
                if (!exists && !isEpisodeFolderReachable(rows.get(0))) {
                    myLogW("repair => folder not reachable (storage unmounted?), kept: " + rows.get(0).getPath());
                    skipped++;
                    continue;
                }
                ZikFile survivor = pickDuplicateSurvivor(rows, episodeDao);
                for (ZikFile dup : rows) {
                    if (dup.getId() == survivor.getId())
                        continue;
                    db.runInTransaction(() -> mergeDuplicateInto(db, dup, survivor));
                    merged++;
                    myLogW("repair => duplicate row " + dup.getId() + " (" + Math.round(dup.getPercentdone())
                            + "%) merged into " + survivor.getId() + ": " + dup.getPath());
                }
                if (!exists) {
                    ZikFile fresh = db.zikFileDao().getById(survivor.getId());
                    if (fresh != null && db.runInTransaction(() -> removeEpisodeRow(db, fresh)) >= 0) {
                        removed++;
                        myLogW("repair => row " + fresh.getId() + " removed, file gone (" + fresh.timeListened
                                + "s listened kept on the episode): " + fresh.getPath());
                    }
                }
                folders.add((long) rows.get(0).getIdFolder());
            }

            // Episodes downloaded again after an auto-delete kept their old "deleted" mark.
            int cleared = episodeDao.clearDateDeleteOfDownloadedEpisodes();

            for (Long idFolder : folders)
                Sql.updateFolderTable(context, idFolder);
            myLogI("repair => " + merged + " duplicate row(s) merged, " + removed + " row(s) of missing files removed, "
                    + skipped + " file(s) skipped (folder not reachable), " + cleared + " stale episode delete mark(s) cleared");
        }
    }

    // Among rows sharing one file, the one to keep: the row the Episode links to, else the most
    // listened one.
    private static ZikFile pickDuplicateSurvivor(List<ZikFile> candidates, EpisodeDao episodeDao) {
        ZikFile best = candidates.get(0);
        for (ZikFile z : candidates) {
            if (episodeDao.getByZikFileId(z.getId()) != null)
                return z;
            if (z.getPercentdone() > best.getPercentdone())
                best = z;
        }
        return best;
    }

    // Folds a duplicate row into the survivor, then deletes it. Nothing the user sees is lost: the
    // furthest progress wins, listening time adds up, play sessions/ticks (heatmaps) and the
    // episode link move over. Run in a transaction.
    private static void mergeDuplicateInto(AppDatabase db, ZikFile dup, ZikFile survivor) {
        long from = dup.getId(), to = survivor.getId();
        ZikFile s = db.zikFileDao().getById(to); // fresh copy, not a caller's snapshot
        if (s == null)
            return; // survivor gone meanwhile: leave the duplicate as it is
        db.playSessionDao().moveToZikFile(from, to);
        db.playTickDao().moveToZikFile(from, to);
        if (db.episodeDao().getByZikFileId(to) == null)
            db.episodeDao().moveToZikFile(from, to); // unique idZikFile: only when the survivor has none

        if (dup.getPercentdone() > s.getPercentdone()) {
            s.setPercentdone(dup.getPercentdone());
            s.setPosition(dup.getPosition());
            s.setFinished(dup.isFinished());
        }
        s.timeListened += dup.timeListened;
        if (dup.lLastAccess != null && (s.lLastAccess == null || dup.lLastAccess > s.lLastAccess))
            s.lLastAccess = dup.lLastAccess;
        if (dup.lFirstAccess != null && (s.lFirstAccess == null || dup.lFirstAccess < s.lFirstAccess))
            s.lFirstAccess = dup.lFirstAccess;
        db.zikFileDao().update(s);
        db.zikFileDao().deleteById(from);
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

                // Everything below reads/writes the DB (dedup lookup + insert), so it all
                // runs on the DB executor rather than whatever thread the network callback fires on.
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    EpisodeDao episodeDao = AppDatabase.getDatabase(context).episodeDao();
                    List<Episode> existingEpisodesForPodcast = episodeDao.getByPodcastId(podcast.getId());

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

                        if (destFile.exists()) {
                            myLogD("episode already exists - n°" + i + "/" + maxEpisode + " for [" + podcast.title
                                    + "] - [" + episodeLabel + "] - [" + fileName + "]");
                            continue;
                        }

                        // PodcastIndex sometimes reassigns a new episode id to the same actual
                        // episode on re-crawl (e.g. unstable feed guids). That defeats every check
                        // above, which is keyed on episode.id, and silently re-downloads a duplicate.
                        // Catch it here by comparing title + declared enclosure size against episodes
                        // of this podcast that are already downloaded.
                        Episode duplicate = findDuplicateByTitleAndSize(existingEpisodesForPodcast, episode.title,
                                episode.enclosureLength);
                        if (duplicate != null) {
                            myLogE("Auto-download SKIPPED - duplicate of already-downloaded episode idEpisode="
                                    + duplicate.idEpisode + " idZikFile=" + duplicate.idZikFile
                                    + " (title+size match) for [" + podcast.title + "] - [" + episodeLabel
                                    + "] - new idEpisode=" + episode.id);
                            continue;
                        }

                        myLogD("Auto-download episode n°" + i + "/" + maxEpisode + " for [" + podcast.title + "] - ["
                                + episodeLabel + "] - [" + fileName + "]");
                        newEpisodes.add(episode);
                        /// EPISODES LOOP ////////////////////////////////////////////////////////
                    }

                    if (!newEpisodes.isEmpty()) {
                        List<Episode> toSave = PodcastHelper.convertToEpisodes(podcastEpisodes, podcast.getId());
                        episodeDao.insertAll(toSave);
                        PodcastDownloadManager.enqueueDownloads(context, podcast.feedId, newEpisodes, podcastFolder,
                                null);
                    }
                });
                updateLastCheck(context, podcast.feedId);

            }

            @Override
            public void onError(Exception e) {
                myLogEE(e, "Auto-download error for feedId " + podcast.feedId);
            }
        });
    }

    // Matches on normalized title + declared enclosure byte size against episodes of the same
    // podcast that are already linked to a downloaded ZikFile (idZikFile != null). Requires a
    // positive size on both sides so two episodes with unknown/zero declared length never match
    // on title alone.
    private static Episode findDuplicateByTitleAndSize(List<Episode> existingEpisodes, String title,
            long enclosureLength) {
        if (title == null || enclosureLength <= 0)
            return null;
        String normalizedTitle = title.trim();
        for (Episode existing : existingEpisodes) {
            if (existing.idZikFile == null)
                continue;
            if (existing.enclosureLength != enclosureLength)
                continue;
            if (existing.title != null && normalizedTitle.equalsIgnoreCase(existing.title.trim())) {
                return existing;
            }
        }
        return null;
    }

    public static void cancelAutoDownload(Context c, long folderId) {
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

    public static void deleteEpisode(long id, Context context) {
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
    public static String getPodcastOriginalCoverPath(Context context, long folderId) {
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
                myLogD("opening podcast episode via MainActivity for podcast : " + podcast.title);
                activity.startActivity(buildOpenPodcastIntent(activity, podcast));
            } else {
                myLogI("No podcast linked to folder " + folder.getId());
            }
        });
    }

    @androidx.annotation.Nullable
    public static String getPodcastOriginalCoverUrl(Context context, long folderId) {
        Podcast podcast = AppDatabase.getDatabase(context.getApplicationContext()).podcastDao()
                .getPodcastByFolderId(folderId);
        if (podcast != null) {
            return podcast.imageOriginalUrl;
        }
        return null;
    }

    // Lets MediaService (shared/"main" source set, no Episode/EpisodeDao access) show the
    // episode-specific cover - when it has one - instead of the podcast/folder cover, without
    // MediaService needing to know Episode exists at all. Blocking DB call - call off the main thread.
    @androidx.annotation.Nullable
    public static String getEpisodeCoverForZikFile(Context context, long zikFileId) {
        Episode episode = AppDatabase.getDatabase(context.getApplicationContext()).episodeDao()
                .getByZikFileId(zikFileId);
        if (episode != null && episode.image != null && !episode.image.isEmpty()) {
            return episode.image;
        }
        return null;
    }

    // A Folder cover file was moved (cached_images -> images, see ImageHelper.processPendingImages()).
    // A podcast's folder shares its cover file with the Podcast row (PodcastSyncWorker), so follow it.
    public static void onImageFileMoved(Context context, String oldPath, String newPath) {
        int n = AppDatabase.getDatabase(context.getApplicationContext()).podcastDao().replaceImagePath(oldPath, newPath);
        if (n > 0)
            myLogD("onImageFileMoved: " + n + " podcast image path(s) updated => " + newPath);
    }

    // Podcast rows whose local cover file is gone (moved without updating the row, cleaned, restored
    // from another device...): point them at the moved file if any, else back to the original URL
    // so they get re-cached below. Call off the main thread.
    private static void healMissingPodcastImages(Context context) {
        PodcastDao dao = AppDatabase.getDatabase(context.getApplicationContext()).podcastDao();
        for (Podcast podcast : dao.getAllWithLocalImages()) {
            File f = new File(podcast.image);
            if (f.exists() && f.length() > 0)
                continue;
            File moved = new File(com.driot.bookplayer.helpers.StorageHelper.getImageFolder(context, false), f.getName());
            if (moved.exists() && moved.length() > 0) {
                myLogW("healMissingPodcastImages: [" + podcast.title + "] => " + moved.getAbsolutePath());
                podcast.image = moved.getAbsolutePath();
            } else if (podcast.imageOriginalUrl != null && podcast.imageOriginalUrl.startsWith("http")) {
                myLogW("healMissingPodcastImages: [" + podcast.title + "] => back to " + podcast.imageOriginalUrl);
                podcast.image = podcast.imageOriginalUrl;
                podcast.date_maj = 0; // re-cache on this pass
            } else {
                continue;
            }
            dao.update(podcast);
        }
    }

    public static void handlePodcastImages(Context context, long currentTime) {
        try {
            healMissingPodcastImages(context);
        } catch (Exception e) {
            myLogEE(e, "healMissingPodcastImages");
        }
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
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                context.startActivity(buildOpenPodcastIntent(context, p)));
    }

    /** Intent that shows the given podcast's episode screen inside MainActivity's Podcast tab. */
    private static Intent buildOpenPodcastIntent(Context context, Podcast podcast) {
        android.os.Bundle args = new android.os.Bundle();
        args.putParcelable("podcast", podcast);
        return new Intent(context, com.driot.bookplayer.activities.MainActivity.class)
                .putExtra(com.driot.bookplayer.activities.MainActivity.EXTRA_NAV_TAB_ID, R.id.nav_podcast)
                .putExtra(com.driot.bookplayer.activities.MainActivity.EXTRA_NAV_DEST_ID, R.id.podcastEpisodeFragment)
                .putExtra(com.driot.bookplayer.activities.MainActivity.EXTRA_NAV_ARGS, args)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    // ---- Sharing an episode (mirror of RadioHelper.shareRadioStation / handleDeepLink) ----

    /** Shares a link (plus the podcast cover when available) that opens this podcast on the
     * receiver's app and plays the episode. The link carries everything needed to do that without
     * any lookup: the podcast index feed id (episode list), the audio url, and display titles. */
    public static void shareEpisode(Context context, Podcast podcast, DisplayableEpisode episode) {
        if (episode.enclosureUrl == null || episode.enclosureUrl.isEmpty()) {
            myLogE("shareEpisode: episode has no audio url - nothing to share");
            return;
        }
        Context appCtx = context.getApplicationContext();

        Uri.Builder link = new Uri.Builder()
                .scheme("https")
                .authority("bookplayer.driot.com")
                .appendPath("share")
                .appendPath("podcast")
                .appendQueryParameter("feed", String.valueOf(podcast.feedId))
                .appendQueryParameter("episode", String.valueOf(episode.idEpisode))
                .appendQueryParameter("url", episode.enclosureUrl)
                .appendQueryParameter("ptitle", podcast.title);
        if (episode.title != null) {
            link.appendQueryParameter("title", episode.title);
        }
        // The receiver can only fetch a remote cover; podcast.image is a local path once cached.
        String remoteImage = podcast.imageOriginalUrl != null && podcast.imageOriginalUrl.startsWith("http")
                ? podcast.imageOriginalUrl
                : (podcast.image != null && podcast.image.startsWith("http") ? podcast.image : null);
        if (remoteImage != null) {
            link.appendQueryParameter("image", remoteImage);
        }

        String body = appCtx.getString(R.string.share_podcast_body) + ": \n\n"
                + (episode.title != null ? episode.title + "\n" : "") + podcast.title + "\n\n" + link.build();
        String head = appCtx.getString(R.string.share_podcast_head);

        AppDatabase.databaseReadExecutor.execute(() -> {
            Uri imageUri = ShareHelper.resolveShareImageUri(appCtx, podcast.image,
                    "share_podcast_" + podcast.feedId + ".jpg");
            ShareHelper.shareContent(context, body, head, imageUri);
        });
    }

    /** Opens a shared episode's podcast and starts playing that episode, exactly as tapping it in
     * the podcast's episode list would. The podcast is saved locally if it's new to this device
     * (same as opening one from the search results). */
    public static void handleDeepLink(Context context, Uri data) {
        String url = data.getQueryParameter("url");
        String episodeTitle = data.getQueryParameter("title");
        String podcastTitle = data.getQueryParameter("ptitle");
        String image = data.getQueryParameter("image");
        long feedId = parseLongOrDefault(data.getQueryParameter("feed"), -1);
        long episodeId = parseLongOrDefault(data.getQueryParameter("episode"), -1);
        myLog("feed=[" + feedId + "] - episode=[" + episodeId + "] - url=[" + url + "]");

        if (feedId <= 0 || url == null || url.isEmpty()) {
            myLogEE(null, "handle deepLink podcast, missing feed or url");
            return;
        }

        Context appCtx = context.getApplicationContext();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            PodcastDao dao = AppDatabase.getDatabase(appCtx).podcastDao();
            Podcast podcast = dao.getPodcastByFeedId(feedId);
            if (podcast == null) {
                String title = !TextUtils.isEmpty(podcastTitle) ? podcastTitle
                        : (!TextUtils.isEmpty(episodeTitle) ? episodeTitle : url);
                podcast = fromPodcastFeed(new PodcastFeed(feedId, title, image, ""));
                dao.insert(podcast);
            }
            context.startActivity(buildOpenPodcastIntent(context, podcast));

            DisplayableEpisode episode = new DisplayableEpisode();
            episode.idEpisode = episodeId;
            episode.title = !TextUtils.isEmpty(episodeTitle) ? episodeTitle : podcast.title;
            episode.enclosureUrl = url;
            onPodcastClick(appCtx, episode, podcast, "DeepLink");
        });
    }

    private static long parseLongOrDefault(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    public static void onPodcastClick(Context context, DisplayableEpisode ep, Podcast podcast, String caller) {
        String cover = ep.image == null || ep.image.isEmpty() ? podcast.image : ep.image;
        long trackId = (ep.id != null) ? ep.id : -1;
        StartPlayHelper.playStream(context, Var.PLAY_MODE_PODCAST, ep.enclosureUrl, trackId, ep.title,
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
            // TODO => we need a position, or it will start the episode from the
            // beggining....
            StartPlayHelper.playStream(context, Var.PLAY_MODE_PODCAST, url,
                    (int) episode.id, episode.title, episode.image, null);
            return true;
        } else {
            return false;
        }
    }

    public static void deletePodcastFolder(long folderId, Context context) {
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
            if (!NetworkHelper.hasInternet(context)) {
                myLogD("no internet => bypassing podcast auto-download");
            } else if (isStorageTooLowForDownload(context)) {
                myLogW("Podcast auto-download skipped - low storage");
                myToast(context.getString(R.string.podcast_autodownload_skipped_low_storage));
            } else {
                PodcastHelper.checkForNewEpisodesToAutoDownload(context, Var.PODCAST_INDEX_ORG_SINCE);
            }
        }
        /// Podcasts AutoDelete
        PodcastHelper.checkForEpisodesToAutoDelete(context);
    }

    /** Same app-controlled threshold used for book downloads (Option.getMinFreeStorageMbForDownload,
     *  Settings > Download) - checked against wherever podcasts actually land (internal or SD
     *  card, per Option.getUseSdCard()), not the OS's own often-inaccurate "storage low" signal. */
    private static boolean isStorageTooLowForDownload(Context context) {
        File dir = getUnzipFolder(context, Option.getUseSdCard());
        long freeBytes = com.driot.bookplayer.helpers.StorageHelper.getUsableSpaceForPath(dir.getPath());
        long minFreeBytes = com.driot.bookplayer.helpers.StorageHelper
                .getMinFreeStorageMbForPath(context, dir.getPath()) * 1024L * 1024L;
        return freeBytes > 0 && freeBytes < minFreeBytes;
    }

    public static boolean backupDataHasPodcasts(BackupManager.BackupData data) {
        return (data.podcasts != null && !data.podcasts.isEmpty());
    }

    public static boolean backupDataHasEpisodeHistory(BackupManager.BackupData data) {
        return data.episodeHistory != null && !data.episodeHistory.isEmpty();
    }

    public static int backupDataPodcastCount(BackupManager.BackupData data) {
        return data.podcasts != null ? data.podcasts.size() : 0;
    }

    public static int backupDataEpisodeHistoryCount(BackupManager.BackupData data) {
        return data.episodeHistory != null ? data.episodeHistory.size() : 0;
    }

    public static void updateImage(long folderId, String imagePath, Context context) {
        AppDatabase.getDatabase(context.getApplicationContext()).podcastDao().updateImageForFolderId(folderId,
                imagePath);
    }

    // Listening time of a podcast folder that its remaining tracks no longer carry: streamed
    // episodes, and downloaded ones since deleted (AutoDelete moves their time to the Episode).
    // Same total as the podcast screen (PodcastDao.getTotalTimeListenedForPodcast). Seconds.
    public static long getEpisodeTimeListenedForFolder(Context context, long folderId) {
        return AppDatabase.getDatabase(context.getApplicationContext()).episodeDao()
                .getEpisodeTimeListenedForFolder(folderId);
    }

    public static void addSecondToTimeListened(Context context, long trackId) {
        AppDatabase db = AppDatabase.getDatabase(context.getApplicationContext());
        db.episodeDao().addSecondToTimeListened(trackId);
    }

}
