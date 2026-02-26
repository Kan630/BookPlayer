package com.driot.bookplayer.db;

import android.content.Context;

public class BackupManager extends BaseBackupManager {

    public BackupManager(Context context) {
        super(context);
    }

    public static class BackupData extends BaseBackupData {
        // No extra fields for Pure flavor
    }

    @Override
    public String exportToJson(boolean includePreferences, boolean includeRadios, boolean includePodcasts,
            boolean includeLibrivox) {
        BackupData data = new BackupData();
        exportBaseData(data, includePreferences, includeLibrivox);
        return gson.toJson(data);
    }

    @Override
    public BackupData inspectJson(String json) {
        return gson.fromJson(json, BackupData.class);
    }

    @Override
    public void importFromJson(String json, boolean includePreferences, boolean includeRadios, boolean includePodcasts,
            boolean includeLibrivox) {
        BackupData data = inspectJson(json);
        if (data == null)
            return;

        importBaseData(data, includePreferences, includeLibrivox);
    }
}
