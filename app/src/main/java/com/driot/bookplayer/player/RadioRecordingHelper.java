package com.driot.bookplayer.player;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import android.content.Context;
import android.net.Uri;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.CommonZikFileDao;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.FolderDao;
import com.driot.bookplayer.db.Sql;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FileHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.objects.AudioInfo;
import com.driot.bookplayer.objects.AudioProber;
import com.driot.bookplayer.utils.Tonio;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Builds the on-disk layout for radio recordings and turns a finished recording into a
 * book/folder + track, mirroring how PodcastSyncWorker registers a downloaded episode. Also
 * stashes the station's stream URL/id in the folder's jsonData so its cover (in ZikFileActivity)
 * can relaunch live playback of that station. */
public final class RadioRecordingHelper {

    private static final String JSON_KEY_RADIO = "radio";
    private static final String JSON_KEY_STREAM_URL = "streamUrl";
    private static final String JSON_KEY_STATION_ID = "stationId";

    private RadioRecordingHelper() {
    }

    static File buildRecordingFolder(Context context, String stationName) {
        File root = StorageHelper.getUnzipFolder(context, Option.getUseSdCard());
        return new File(root, FileHelper.sanitizeFilename(folderName(stationName)));
    }

    static String buildRecordingFileName() {
        // NOTE: 'ss' (quoted) is literal text "ss", not the seconds pattern - only unquoted ss is.
        // Getting this wrong once meant every recording started within the same minute produced
        // the exact same file name, silently overwriting the previous recording and leaving two
        // ZikFile rows pointing at one (identical) file. SSS (millis) added as extra insurance.
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH'h'mm'm'ss's'-SSS", Locale.US).format(new Date());
        // Most Icecast/Shoutcast progressive streams are MP3; a wrong extension doesn't break
        // playback (BookPlayer's own player probes/decodes by content, not by extension) but
        // keeps the file recognizable to other apps/file explorers.
        return "Recording " + stamp + ".mp3";
    }

    private static String folderName(String stationName) {
        return (stationName != null && !stationName.isEmpty()) ? stationName : "Radio";
    }

    /** @return the live stream URL to replay this folder's station, or null if this isn't a radio
     * recording folder (or the info wasn't captured). */
    @androidx.annotation.Nullable
    public static String getRadioStreamUrl(Folder folder) {
        JSONObject radio = getRadioJson(folder);
        return (radio != null) ? radio.optString(JSON_KEY_STREAM_URL, null) : null;
    }

    /** @return the RadioStation DB id this folder's recordings came from, or 0 if unknown/not a
     * radio recording folder. Not guaranteed to still exist (station may have been removed). */
    public static long getRadioStationId(Folder folder) {
        JSONObject radio = getRadioJson(folder);
        return (radio != null) ? radio.optLong(JSON_KEY_STATION_ID, 0) : 0;
    }

    @androidx.annotation.Nullable
    private static JSONObject getRadioJson(Folder folder) {
        if (folder == null || folder.jsonData == null || folder.jsonData.isEmpty())
            return null;
        try {
            JSONObject root = new JSONObject(folder.jsonData);
            return root.has(JSON_KEY_RADIO) ? root.getJSONObject(JSON_KEY_RADIO) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String withRadioStreamInfo(String existingJsonData, String streamUrl, long stationId) {
        JSONObject root = new JSONObject();
        if (existingJsonData != null && !existingJsonData.isEmpty()) {
            try {
                root = new JSONObject(existingJsonData);
            } catch (Exception ignored) {
            }
        }
        try {
            JSONObject radio = new JSONObject();
            if (streamUrl != null && !streamUrl.isEmpty())
                radio.put(JSON_KEY_STREAM_URL, streamUrl);
            if (stationId > 0)
                radio.put(JSON_KEY_STATION_ID, stationId);
            root.put(JSON_KEY_RADIO, radio);
        } catch (Exception e) {
            myLogEE(e, "withRadioStreamInfo: failed to build jsonData");
        }
        return root.toString();
    }

    /** Runs on the calling (background) thread's own executor submission - callers already run
     * this off the capture thread. `elapsedMs` (wall-clock time between start/stop) is only a
     * fallback: most Icecast/Shoutcast servers "burst" several already-buffered seconds of audio
     * as fast as the socket allows right when the connection opens, so the file's actual audio
     * duration can be noticeably longer than the wall-clock recording time. We probe the real
     * file instead so the saved track's duration matches what actually plays back. */
    static void finalizeRecording(Context context, File file, String stationName, String coverUrl,
            String streamUrl, long stationId, long elapsedMs) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long durationMs = elapsedMs;
            try {
                AudioInfo info = AudioProber.probe(context, Uri.fromFile(file), false);
                if (info != null && info.durationMs > 0) {
                    durationMs = info.durationMs;
                }
            } catch (Exception e) {
                myLogEE(e, "RadioRecordingHelper: failed to probe recorded file duration, falling back to elapsed time");
            }

            AppDatabase db = AppDatabase.getDatabase(context);
            FolderDao folderDao = db.folderDao();
            CommonZikFileDao zikFileDao = db.zikFileDao();

            String name = folderName(stationName);
            Folder folder = folderDao.getByName(name);
            long idFolder;
            if (folder != null) {
                idFolder = folder.getId();
                if ((folder.image == null || folder.image.isEmpty()) && coverUrl != null && !coverUrl.isEmpty()) {
                    folderDao.updateImage(idFolder, coverUrl);
                }
                String updatedJson = withRadioStreamInfo(folder.jsonData, streamUrl, stationId);
                if (!updatedJson.equals(folder.jsonData)) {
                    folder.jsonData = updatedJson;
                    folderDao.update(folder);
                }
            } else {
                folder = new Folder();
                folder.setName(name);
                folder.setPath(file.getParent());
                folder.setUri(file.getParent());
                folder.setPercentdone(0.0);
                folder.setFinished(false);
                folder.setIszipfile(false);
                folder.setOriginalHash("");
                folder.setSourceLocation(Var.SOURCE_LOCATION_RADIO_RECORDING);
                folder.playType = Option.getRadioRecordingAsMusic() ? Var.PLAY_TYPE_MUSIC : Var.PLAY_TYPE_AUDIO;
                folder.jsonData = withRadioStreamInfo(null, streamUrl, stationId);
                folder.image = coverUrl;
                folder.date_added = System.currentTimeMillis();
                folder.date_last_zikfile_added = System.currentTimeMillis();
                idFolder = folderDao.insert(folder);
                myLog("New radio recording folder created: [" + name + "] - FolderId=[" + idFolder + "]");
            }

            double zeOrder = zikFileDao.getMaxOrder(idFolder) + 1;
            String trackTitle = Tonio.formatDateForDisplay(System.currentTimeMillis());

            ZikFile zikFile = new ZikFile();
            zikFile.setIdFolder(idFolder);
            zikFile.setName(file.getName());
            zikFile.setPath(file.getAbsolutePath());
            zikFile.setDisplayName(trackTitle);
            zikFile.setZeorder(zeOrder);
            zikFile.setFolderName(name);
            zikFile.setPercentdone(0.0);
            zikFile.setPosition(0);
            zikFile.setIszipfile(false);
            zikFile.setFinished(false);
            zikFile.setDuration(durationMs);
            zikFile.setSize(file.length());
            zikFile.date_added = System.currentTimeMillis();
            long newZikFileId = zikFileDao.insert(zikFile);
            myLog("Radio recording ZikFile inserted with ID: " + newZikFileId + " - [" + trackTitle + "]");

            Sql.updateFolderTable(context, idFolder);
            folderDao.updateLastAccess(idFolder, System.currentTimeMillis());
        });
    }
}
