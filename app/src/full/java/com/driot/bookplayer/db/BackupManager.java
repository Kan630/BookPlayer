package com.driot.bookplayer.db;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

public class BackupManager extends BaseBackupManager {

    public BackupManager(Context context) {
        super(context);
    }

    public static class BackupData extends BaseBackupData {
        public List<RadioStation> radioStations = new ArrayList<>();
        public List<Podcast> podcasts = new ArrayList<>();
        // "Podcast history" category - only episodes with real user data (downloaded or
        // listened to), never the full catalog. See PendingEpisodeHistory.
        public List<PendingEpisodeHistory> episodeHistory = new ArrayList<>();
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

        importBaseData(data, includePreferences, includeLibrivox, includeBookProgress);

        // Restore Database (flavor-specific)
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(context);
            db.runInTransaction(() -> {
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
            });
        });
    }
}
