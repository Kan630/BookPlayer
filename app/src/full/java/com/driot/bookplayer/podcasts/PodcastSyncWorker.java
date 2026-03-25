package com.driot.bookplayer.podcasts;

import static com.driot.bookplayer.global.Var.SOURCE_LOCATION_PODCAST;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.WorkerParameters;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Episode;
import com.driot.bookplayer.db.EpisodeDao;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.FolderDao;
import com.driot.bookplayer.db.PodcastDao;
import com.driot.bookplayer.db.Sql;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.db.CommonZikFileDao;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.objects.AudioInfo;
import com.driot.bookplayer.objects.AudioProber;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.player.MediaService;
import android.content.Intent;
import com.driot.bookplayer.utils.log.LoggingWorker;

import java.io.File;

public class PodcastSyncWorker extends LoggingWorker {


    public PodcastSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWorkBody() {
        String path = getInputData().getString(FinalizeDownloadWorker.KEY_FOLDER_PATH);
        String name = getInputData().getString(FinalizeDownloadWorker.KEY_FOLDER_NAME);
        long feedId = getInputData().getLong(FinalizeDownloadWorker.KEY_FEED_ID,0);
        Long podcastId = null;
        Long newZikFileId = null;
        File folder = new File(path);
        if (!folder.exists() || !folder.isDirectory()) return Result.failure();

        AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
        FolderDao folderDao = db.folderDao();
        CommonZikFileDao zikFileDao = db.zikFileDao();
        PodcastDao podcastDao = db.podcastDao();
        EpisodeDao episodeDao = db.episodeDao();

        // Optionally enter foreground:
        // setForegroundEarly(buildForegroundInfo());

        // 1. Ensure folder is registered
        Folder folderDb = folderDao.getByName(name);
        long idFolder = -1;
        if (folderDb != null) {
            idFolder = folderDb.getId();
        } else {
            folderDb = new Folder();

            folderDb.setName(name);
            folderDb.setPath(path);
            folderDb.setUri(path); //2023-10-22 deprecated
            folderDb.setPercentdone(0.0);
            folderDb.setFinished(false);
            folderDb.setIszipfile(false); //2023-10-22 deprecated (live zip reading - code has been removed)
            folderDb.setOriginalHash("");
            folderDb.setSourceLocation(SOURCE_LOCATION_PODCAST);
            folderDb.date_added = System.currentTimeMillis();
            folderDb.date_last_zikfile_added = System.currentTimeMillis();
            try {
                folderDb.image = podcastDao.getPodcastByFeedId(feedId).image;
            } catch (Exception e) {
                myLogE("copy podcast image in folder failed");
            }

            long newId = folderDao.insert(folderDb); // Room returns the new ID
            idFolder = (int) newId; // safely cast to int

            //update Podcast with folderId
            podcastDao.updateFolderIdByFeedId(feedId, newId);

            myLog("New Podcast folder added to DB : [" + name + "] - FolderId=[" + idFolder + "] - feedId=[" + feedId + "]");

        }
        podcastId = podcastDao.getIdByFeedId(feedId);

        // 2. Scan files
        File[] files = folder.listFiles((dir, filename) -> filename.endsWith(".mp3"));
        if (files == null) {
            myLogW("folder null " + path + "-" + name);
            return Result.success();
        }
        if (files.length == 0) {
            myLogW("files.length == 0 " + path + "-" + name);
            return Result.success();
        }

        int newFilesCount = 0;
        for (File file : files) {
            myLogD("file : [" +  file.getName() + ']');
            int idFile = zikFileDao.getId(idFolder, file.getName());

            long episodeId = PodcastHelper.getEpisodeIdFromName(file.getName());
            if (episodeId < 1) {
                myLogEE(null, "could not get episode Id from name " + file.getName());
            } else {
                Episode episode = episodeDao.getByEpisodeId(episodeId);

                if (idFile < 1) { // not in DB
                    double zeOrder = zikFileDao.getMaxOrder(idFolder) + 1;

                    String trackTitle = null;
                    try {
                        trackTitle = (Option.getPodcastAddDateToEpisodeName()
                                ? "[" + Tonio.formatDateForDisplay(Long.parseLong(episode.datePublished)*1000) + "]"
                                : "");
                        trackTitle = trackTitle + " " + Tonio.formatNameForDisplay(episode.title, false);
                    } catch (Exception ignored) {}
                    if (trackTitle==null) {
                        trackTitle = file.getName();
                    }
                    myLogD("trackTitle : << " + trackTitle + " >>");

                    myLogD("getting duration for file : [" + file.getAbsolutePath() + ']');
                    long duration = 0;
                    AudioInfo audioInfo = AudioProber.probe(this.getApplicationContext(), Uri.fromFile(file), false);
                    if (audioInfo != null) duration = audioInfo.durationMs;

                    if (duration > 0) {
                        ZikFile zikFile = new ZikFile();
                        zikFile.setIdFolder(idFolder);
                        zikFile.setName(file.getName());
                        zikFile.setPath(folder.getAbsolutePath() + "/" + file.getName());
                        zikFile.setDisplayName(trackTitle); // TO CHANGE
                        zikFile.setZeorder(zeOrder);
                        zikFile.setFolderName(folderDb.getName());
                        zikFile.setPercentdone(0.0);
                        zikFile.setPosition(0);
                        zikFile.setIszipfile(false);
                        zikFile.setFinished(false);
                        zikFile.setDuration(duration);
                        zikFile.date_added = System.currentTimeMillis();
                        newZikFileId = zikFileDao.insert(zikFile);
                        myLogD("ZikFile inserted with ID: " + newZikFileId + " - [" + trackTitle + "]");
                        newFilesCount++;

                        if (podcastId != null) {
                            episode.idZikFile = newZikFileId;
                            episode.date_import = System.currentTimeMillis();
                            int updateResult = episodeDao.update(episode);
                            myLog("[" + updateResult + "] - episode updated for zikFile link " + newZikFileId + " and podcast " + podcastId);
                            if (updateResult != 1) {
                                myLogEE(null, "[" + updateResult + "] - episode updated for zikFile link " + newZikFileId + " and podcast " + podcastId);
                            }
                        } else {
                            myLogEE(null, "could not find podcastId");
                        }
                        
                        // Notify MediaService that this episode finished downloading so it can seamlessly switch to the local file
                        Intent downloadIntent = new Intent(getApplicationContext(), MediaService.class)
                                .setAction(Intents.ACTION_PODCAST_DOWNLOAD_COMPLETED)
                                .putExtra(Intents.EXTRA_EPISODE_ID, episode.id)
                                .putExtra(Intents.EXTRA_ZIKFILE_ID, newZikFileId)
                                .putExtra(Intents.EXTRA_FOREGROUND, true)
                                .putExtra(Intents.EXTRA_CALLER, "PodcastSyncWorker");
                        ContextCompat.startForegroundService(getApplicationContext(), downloadIntent);
                    } else {
                        myLogEE(null, "duration == 0 " + file.getName());
                    }
                } else {
                    myLogW("already in DB : [" + file.getName() + "]");
                }
            }
        }

        // 3. Notify user
        if (newFilesCount > 0) {
            Sql.updateFolderTable(getApplicationContext(), idFolder);
            ImageHelper.processPendingImages(getApplicationContext(), System.currentTimeMillis(), "podcast sync worker");
            if (Option.getPodcastAutoDownloadedAtTheTop()) {
                folderDao.updateLastAccess(idFolder, System.currentTimeMillis()); //triggers livedata update and reload of Book list
            }
            Handler handler = new Handler(Looper.getMainLooper());
            int finalNewFilesCount = newFilesCount;
            //TODO handler.post(() -> myToast(finalNewFilesCount + " " + getString(getApplicationContext(), R.string.podcast_new_episodes) + " for " + name));
            handler.post(() -> myToast(finalNewFilesCount + " new episodes for " + name));
        }

        return Result.success();
    }

}
