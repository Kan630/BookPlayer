package com.driot.bookplayer.helpers;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.driot.bookplayer.activities.ZikFileActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
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
            AppDatabase db = AppDatabase.getDatabase(activity.getApplicationContext());
            ZikFile found = db.zikFileDao().getByPath(uriString);
            if (found == null && barePath != null) {
                found = db.zikFileDao().getByPath(barePath);
            }

            NameAndSize nameAndSize = null;
            if (found == null && !"file".equals(uri.getScheme())) {
                // A content:// Uri from a real file manager's "Open With" is often re-generated
                // per pick and won't equal the file:// path a prior whole-folder import stored
                // for this same on-disk file (see FinalParseFolderWorker/BookCandidate, which
                // scan via DocumentFile.fromFile() - always file://, never the picker's own
                // content:// string). Resolve down to the real filesystem path the same way the
                // rest of the app already does (FileHelper.processUri - the side-effect-free core
                // of getRealPathFromURI, safe to call speculatively) and retry both the file://
                // form (matches an un-copied import) and the bare form (matches a "copy to
                // internal storage" destination, or defensively either way).
                String realPath = null;
                try {
                    realPath = FileHelper.processUri(activity, uri);
                } catch (Exception e) {
                    myLogEE(e, "OpenWithHelper: FileHelper.processUri failed for " + uri);
                }
                if (realPath != null && !realPath.isEmpty()) {
                    found = db.zikFileDao().getByPath("file://" + realPath);
                    if (found == null) {
                        found = db.zikFileDao().getByPath(realPath);
                    }
                }

                if (found == null) {
                    // Some vendor content providers (seen: Samsung My Files' own FileProvider)
                    // expose neither a resolvable real path nor a DocumentsContract document id -
                    // just OpenableColumns. That's still enough to recognize a re-opened sibling
                    // file: match it against a registered ZikFile by exact name + byte size.
                    nameAndSize = queryNameAndSize(activity, uri);
                    if (nameAndSize.name != null && nameAndSize.size != null) {
                        found = db.zikFileDao().getByNameAndSize(nameAndSize.name, (double) nameAndSize.size);
                    }
                }
            }
            final ZikFile finalFound = found;
            final NameAndSize finalNameAndSize = nameAndSize;

            activity.runOnUiThread(() -> {
                if (activity.isFinishing()) {
                    return;
                }
                if (finalFound != null) {
                    myLogI("OpenWith: [" + uriString + "] already registered (zikFileId=" + finalFound.getId()
                            + ") - resuming its book instead of importing");
                    StartPlayHelper.onZikFileClick(activity, finalFound, "OpenWithProxyActivity");
                    // onZikFileClick() only opens PlayActivity in some cases (see its own
                    // sameTrack/Option.getOpenPlayActivity() logic) - here there is no other app
                    // UI in this task (launched externally via "Open with"), so without this the
                    // app would otherwise just start playback silently behind whatever app the
                    // user was already in, with only the notification to show for it.
                    activity.startActivity(new Intent(activity, ZikFileActivity.class)
                            .putExtra(Intents.EXTRA_FOLDER_ID, finalFound.getIdFolder())
                            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK));
                    activity.finish();
                } else {
                    String action = Option.getOpenWithAction();
                    boolean wantImport = !Var.OPEN_WITH_ACTION_PLAY.equals(action);
                    boolean wantPlay = !Var.OPEN_WITH_ACTION_IMPORT.equals(action);

                    if (wantPlay) {
                        String displayName = (finalNameAndSize != null && finalNameAndSize.name != null)
                                ? finalNameAndSize.name
                                : resolveDisplayNameFallback(uri);
                        StartPlayHelper.playPreview(activity, uriString, displayName, "OpenWithProxyActivity");
                    }
                    if (wantImport) {
                        importLauncher.launchImport(uri, !persistPermission);
                    } else {
                        // Play-only: nothing will start an activity-for-result to trigger
                        // finish() later (see onActivityResult in the two proxy activities), so
                        // close the invisible proxy ourselves once the preview has started.
                        activity.finish();
                    }
                }
            });
        });
    }

    private static class NameAndSize {
        final String name;
        final Long size;

        NameAndSize(String name, Long size) {
            this.name = name;
            this.size = size;
        }
    }

    /** Background-thread only (does a ContentResolver query). Returns (null, null) fields for a
     * non-content Uri or on any resolution failure - callers must null-check both fields. */
    private static NameAndSize queryNameAndSize(BaseActivity activity, Uri uri) {
        if (!"content".equals(uri.getScheme())) {
            return new NameAndSize(null, null);
        }
        try (Cursor cursor = activity.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = null;
                Long size = null;
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    name = cursor.getString(nameIndex);
                }
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex);
                }
                return new NameAndSize(name, size);
            }
        } catch (Exception e) {
            myLogEE(e, "OpenWithHelper.queryNameAndSize: content query failed for " + uri);
        }
        return new NameAndSize(null, null);
    }

    private static String resolveDisplayNameFallback(Uri uri) {
        String last = uri.getLastPathSegment();
        return (last != null) ? last : uri.toString();
    }
}
