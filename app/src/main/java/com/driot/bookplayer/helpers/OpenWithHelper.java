package com.driot.bookplayer.helpers;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.player.StartPlayHelper;
import com.driot.bookplayer.utils.log.BaseActivity;

/**
 * Shared logic for OpenWithProxyActivity / OpenWithProxyActivityAll: when the app is invoked via
 * Android's "Open With" on a single audio file, check whether that exact path is already a
 * registered ZikFile.
 * - If it is: resume its owning book from that track, at its saved position, exactly as if the
 * user had tapped it in the app - do NOT open the import screen.
 * - If it isn't: keep opening the classic import screen as before, and (if the "live preview"
 * setting is on) also start playing the raw file immediately in the MiniPlayUnregisteredFragment
 * bottom bar, so the user can listen before deciding whether to import it.
 */
public class OpenWithHelper {

    public interface ImportLauncher {
        void launchImport(Uri uri, boolean forceCopy);
    }

    public static void handle(BaseActivity activity, Uri uri, boolean persistPermission, ImportLauncher importLauncher) {
        String mime = SupportedFilesHelper.getMimeType(activity, uri);
        boolean isAudio = mime != null && mime.startsWith("audio/");

        if (!isAudio) {
            // Not an audio file (e.g. zip/epub reaching the catch-all proxy) - unchanged behavior.
            importLauncher.launchImport(uri, !persistPermission);
            return;
        }

        String uriString = uri.toString();
        String barePath = "file".equals(uri.getScheme()) ? uri.getPath() : null;

        AppDatabase.databaseReadExecutor.execute(() -> {
            ZikFile found = AppDatabase.getDatabase(activity.getApplicationContext()).zikFileDao().getByPath(uriString);
            if (found == null && barePath != null) {
                found = AppDatabase.getDatabase(activity.getApplicationContext()).zikFileDao().getByPath(barePath);
            }
            final ZikFile finalFound = found;

            activity.runOnUiThread(() -> {
                if (activity.isFinishing()) {
                    return;
                }
                if (finalFound != null) {
                    myLogI("OpenWith: [" + uriString + "] already registered (zikFileId=" + finalFound.getId()
                            + ") - resuming its book instead of importing");
                    StartPlayHelper.onZikFileClick(activity, finalFound, "OpenWithProxyActivity");
                    activity.finish();
                } else {
                    importLauncher.launchImport(uri, !persistPermission);
                    if (Option.getOpenWithLivePreview()) {
                        String displayName = resolveDisplayName(activity, uri);
                        StartPlayHelper.playPreview(activity, uriString, displayName, "OpenWithProxyActivity");
                    }
                }
            });
        });
    }

    private static String resolveDisplayName(BaseActivity activity, Uri uri) {
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = activity.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        String name = cursor.getString(nameIndex);
                        if (name != null && !name.isEmpty()) {
                            return name;
                        }
                    }
                }
            } catch (Exception e) {
                myLogEE(e, "OpenWithHelper.resolveDisplayName: content query failed for " + uri);
            }
        }
        String last = uri.getLastPathSegment();
        return (last != null) ? last : uri.toString();
    }
}
