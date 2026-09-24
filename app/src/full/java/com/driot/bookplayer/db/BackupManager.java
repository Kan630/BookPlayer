package com.driot.bookplayer.db;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

public class BackupManager extends BaseBackupManager {

    public BackupManager(Context context) {
        super(context);
    }

    @Override
    public List<Long> getRedownloadablePodcastFolderIds() {
        return AppDatabase.getDatabase(context).episodeDao().getFoldersWhollyRedownloadable();
    }

    public static class BackupData extends BaseBackupData {
        public List<RadioStation> radioStations = new ArrayList<>();
        public List<Podcast> podcasts = new ArrayList<>();
        // "Podcast history" category - only episodes with real user data (downloaded or
        // listened to), never the full catalog. See PendingEpisodeHistory.
        public List<PendingEpisodeHistory> episodeHistory = new ArrayList<>();
        // Episodes that have a downloaded file, whole: they are what remembers each one's
        // download address, so a book left out of a backup can be fetched again after a restore.
        public List<Episode> downloadedEpisodes = new ArrayList<>();
    }

    @Override
    public String exportToJson(boolean includePreferences, boolean includeRadios, boolean includePodcasts,
            boolean includeLibrivox, boolean includeBookProgress, boolean includePodcastHistory) {
        BackupData data = new BackupData();
        exportBaseData(data, includePreferences, includeLibrivox, includeBookProgress);

        // Database
        AppDatabase db = AppDatabase.getDatabase(context);
        if (includeRadios) {
            data.radioStations = db.radioStationDao().getAll();
        }
        if (includePodcasts) {
            data.podcasts = db.podcastDao().getAll();
        }
        if (includePodcastHistory) {
            data.episodeHistory = db.episodeDao().getEngagedEpisodesForBackup();
            data.downloadedEpisodes = db.episodeDao().getDownloadedEpisodes();
        }

        return gson.toJson(data);
    }

    @Override
    public BackupData inspectJson(String json) {
        return gson.fromJson(json, BackupData.class);
    }

    @Override
    public void importFromJson(String json, boolean includePreferences, boolean includeRadios, boolean includePodcasts,
            boolean includeLibrivox, boolean includeBookProgress, boolean includePodcastHistory) {
        BackupData data = inspectJson(json);
        if (data == null)
            return;

        Runnable work = () -> {
            AppDatabase db = AppDatabase.getDatabase(context);
            db.runInTransaction(() -> {
                // Replacing podcasts (and the tracks, above) cascades into Episode: set the rows
                // aside first and put back what still has its podcast and track.
                List<Episode> kept = includePodcasts || includeBookProgress ? db.episodeDao().getAll()
                        : new ArrayList<>();

                importBaseData(data, includePreferences, includeLibrivox, includeBookProgress);

                if (includeRadios && data.radioStations != null) {
                    db.radioStationDao().deleteAll();
                    db.radioStationDao().insertAll(data.radioStations);
                }
                if (includePodcasts) {
                    if (data.podcasts != null) {
                        db.podcastDao().insertAll(data.podcasts);
                    }
                }
                // Held as pending - not inserted into Episode directly, since the podcast's
                // catalog isn't restored (deliberately) and idPodcast local ids won't match
                // post-restore. Reconciled by feedId+idEpisode once each podcast's episodes
                // are fetched again normally - see PodcastEpisodeViewModel.
                if (includePodcastHistory && data.episodeHistory != null) {
                    db.pendingEpisodeHistoryDao().insertAll(data.episodeHistory);
                }

                java.util.Set<Long> podcastIds = new java.util.HashSet<>();
                for (Podcast p : db.podcastDao().getAll()) {
                    podcastIds.add((long) p.getId());
                }
                java.util.Set<Long> zikIds = new java.util.HashSet<>();
                for (com.driot.bookplayer.db.ZikFile z : db.zikFileDao().getAll()) {
                    zikIds.add(z.getId());
                }
                List<Episode> back = new ArrayList<>();
                back.addAll(kept);
                if (includePodcastHistory && includeBookProgress && data.downloadedEpisodes != null) {
                    back.addAll(data.downloadedEpisodes);
                }
                List<Episode> valid = new ArrayList<>();
                for (Episode e : back) {
                    if (!podcastIds.contains(e.idPodcast)) {
                        continue;
                    }
                    if (e.idZikFile != null && !zikIds.contains(e.idZikFile)) {
                        e.idZikFile = null;
                    }
                    valid.add(e);
                }
                // IGNORE on conflict: a row already put back wins over the backup's copy.
                if (!valid.isEmpty()) {
                    db.episodeDao().insertAll(valid);
                }
            });
        };
        // Off the main thread run it here, so a caller that continues afterwards sees the result.
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            AppDatabase.databaseWriteExecutor.execute(work);
        } else {
            work.run();
        }
    }
}
