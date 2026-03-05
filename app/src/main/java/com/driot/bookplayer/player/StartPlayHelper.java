// Antoine Driot, 2025-11-11
package com.driot.bookplayer.player;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.media.MediaBrowserServiceCompat;

import com.driot.bookplayer.R;
import com.driot.bookplayer.activities.ZikFileActivity;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.ZikFile;
import com.driot.bookplayer.global.Intents;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.podcasts.PodcastHelper;
import com.driot.bookplayer.radio.RadioHelper;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class StartPlayHelper {

    private static final String ROOT_ID = "root";
    private static final String PREFIX_FOLDER = "folder:";
    private static final String PREFIX_TRACK = "track:";

    private static final int ART_MAX_PX = 512; // big artwork
    private static final int ICON_MAX_PX = 158; // was 128 158=hints fron AndroidAuto // list thumbnails

    public static void onFolderClick(Context context, Folder clickedFolder, String caller) {
        // DB work off main; UI nav back on main
        AppDatabase.databaseReadExecutor.execute(() ->

        {
            try {
                List<ZikFile> zikFilesList = AppDatabase.getDatabase(context).zikFileDao()
                        .getZikFiles(clickedFolder.getId());
                if (zikFilesList.isEmpty()) {
                    if (Var.SOURCE_LOCATION_PODCAST.equals(clickedFolder.getSourceLocation())) {
                        if (!Option.getPodcastOpenSpecificView()) {
                            myToast(context.getString(R.string.no_episode_all_deleted));
                            // lets open the podcast specific view anyway
                        }
                    } else {
                        myToastE(context.getString(R.string.ErrorCouldNotLoadAudios_emptyfolder));
                        return;
                    }
                }

                if (Var.SOURCE_LOCATION_PODCAST.equals(clickedFolder.getSourceLocation())
                        && (Option.getPodcastOpenSpecificView()
                                || (!Option.getPodcastOpenSpecificView() && zikFilesList.isEmpty()))) {
                    PodcastHelper.startPlayOpenPodcast(clickedFolder, context);
                } else {
                    if (zikFilesList.size() > 1) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            context.startActivity(new Intent(context, ZikFileActivity.class)
                                    .putExtra(Intents.EXTRA_FOLDER, clickedFolder));
                        });
                    } else {
                        myLogD("Single file");
                        // SINGLE FILE: only reload if it's a different clickedFolder than what's
                        // playing
                        PlaybackUiState lastUiState = PlaybackUiBus.get().state().getValue();
                        PlayList pl = PlayList.getInstance();
                        boolean sameTrack = (pl != null && pl.getFolder() != null
                                && pl.getFolder().getId() == clickedFolder.getId()); // keep getId() => needed !
                        boolean isTTS = (pl != null && pl.getFolder() != null
                                && Objects.equals(pl.getFolder().playType, Var.PLAY_TYPE_TEXT)); // keep getId() =>
                                                                                                 // needed !
                        myLogI("Book with only 1 track...     - sameTrack=" + sameTrack + " - lastUiState = "
                                + lastUiState);

                        if (!isTTS) {
                            stopTtsIfPlaying(context, lastUiState);
                        }
                        PlaybackUiBus.get().setLoadPhase(Intents.PHASE_TRACK_CLICK);

                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            // open play screen ?
                            if (Option.getOpenPlayActivity()
                                    || isTTS
                                    || sameTrack) {
                                context.startActivity(new Intent(context, PlayActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                            }

                            // start foreground audio service ?
                            if (lastUiState == null
                                    || !lastUiState.playing
                                    || !sameTrack
                            // || isTTS //TODO remove : TTS not perfect yet, so we force reload...
                            ) {
                                ContextCompat.startForegroundService(
                                        context.getApplicationContext(),
                                        new Intent(context.getApplicationContext(), MediaService.class)
                                                .setAction(Intents.ACTION_PLAY_FROM_FOLDER)
                                                .putExtra(Intents.EXTRA_FOLDER_ID, clickedFolder.getId())
                                                .putExtra(Intents.EXTRA_CALLER, caller)
                                                .putExtra(Intents.EXTRA_FOREGROUND, true));
                            }
                        });

                    }
                }
            } catch (Exception e) {
                myToastEE(e, "error getting nb of ZikFiles");
            }
        });
    }

    public static void onZikFileClick(Context context, ZikFile clickedZikFile, String caller) {
        AppDatabase.databaseReadExecutor.execute(() -> {
            // TTS ?
            final boolean isTTS;
            Folder folder = AppDatabase.getDatabase(context).folderDao().getById(clickedZikFile.getIdFolder());
            isTTS = Objects.equals(folder.playType, Var.PLAY_TYPE_TEXT);

            // was something playing ?
            PlaybackUiState lastUiState = PlaybackUiBus.get().state().getValue();
            if (!isTTS) {
                stopTtsIfPlaying(context, lastUiState);
            }
            PlaybackUiBus.get().setLoadPhase(Intents.PHASE_TRACK_CLICK);

            // is same track clicked ?
            PlayList pl = PlayList.getInstance();
            boolean sameTrack = (pl != null && pl.getZikFile() != null
                    && pl.getZikFile().getId() == clickedZikFile.getId()); // keep getId() => needed !

            myLogI("USER CLICKS ZIKFILE : [" + clickedZikFile.getName() + "] - sameTrack=" + sameTrack + " - TTS="
                    + isTTS + " - lastUiState = " + lastUiState);

            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                PlaybackCommands.resetLastUserAction(context);

                // start audio service
                if (lastUiState == null
                        || !lastUiState.playing
                        || !sameTrack) {
                    playZikFile(context, clickedZikFile.getId(), caller, false, false);
                }

                // open PlayActivity
                if (sameTrack || Option.getOpenPlayActivity() || isTTS) {
                    startActivityBecauseSameTrack(context);
                }

            });
        });
    }

    public static void onZikFileFromPodcast(Context activityContext, ZikFile zikFile, String caller,
            boolean sortNewestFirst) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (MediaService.isRunning && PlaybackUiBus.get().state().getValue() != null) {
                if (PlaybackUiBus.get().state().getValue().trackId == zikFile.getId()) {
                    myLog("already playing that track - [" + zikFile.getDisplayName() + "]");
                    startActivityBecauseSameTrack(activityContext);
                    return;
                }
            }

            try {
                myLog("onOpenLocalEpisode : " + zikFile.getDisplayName() + " - " + zikFile.getId() + " - "
                        + zikFile.getName());
                playZikFile(activityContext, zikFile.getId(), caller, true, sortNewestFirst);
            } catch (Exception e) {
                myLogEE(e, "clickOnEpisode - playThatShit");
            }
        });
    }

    private static void playZikFile(Context context, int zikFileId, String caller, boolean isPodcast,
            boolean sortNewestFirst) {
        ContextCompat.startForegroundService(
                context.getApplicationContext(),
                new Intent(context.getApplicationContext(), MediaService.class)
                        .setAction(Intents.ACTION_PLAY_FROM_TRACK)
                        .putExtra(Intents.EXTRA_TRACK_ID, zikFileId)
                        .putExtra(Intents.EXTRA_TRACK_ORDER_NEWEST_FIRST, sortNewestFirst)
                        .putExtra(Intents.EXTRA_IS_PODCAST, isPodcast)
                        .putExtra(Intents.EXTRA_CALLER, caller)
                        .putExtra(Intents.EXTRA_FOREGROUND, true));
    }

    private static void startActivityBecauseSameTrack(Context context) {
        context.startActivity(new Intent(context, PlayActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }

    public static void carOnPlay(Context context) {
        if (!Option.getAutomotiveLetCarAutoplay()) {
            myLogW("Android Auto not authorized to start audio on its own (from Bookplayer settings)");
            return;
        }

        // TODO if (Option.getAutomotiveAutoResumeOnCarConnect()) {
        // myLogW("Android Auto not authorized to resume playback (from Bookplayer
        // settings)");

        PlayList pl = PlayList.getInstance();
        if (pl == null) {
            // NO PLAYLIST
            myLogI("Car onPlay but Playlist is null");
            FirebaseAnalyticsHelper.tellCarAutoPlay();
            AppDatabase.databaseReadExecutor.execute(() -> {
                ZikFile zikFile = AppDatabase.getDatabase(context.getApplicationContext())
                        .zikFileDao().getLastListenedZikFile();
                if (zikFile == null) {
                    myLogW("no last played zikfile !, must be pretty new");
                } else {
                    myLog("go for last played zikfile : [" + zikFile.getDisplayName() + "], starting FOREGROUND");
                    ContextCompat.startForegroundService(
                            context,
                            new Intent(context, MediaService.class)
                                    .setAction(Intents.ACTION_PLAY_FROM_TRACK)
                                    .putExtra(Intents.EXTRA_TRACK_ID, zikFile.getId())
                                    .putExtra(Intents.EXTRA_CALLER, "carOnPlay()")
                                    .putExtra(Intents.EXTRA_FOREGROUND, true));
                    // Optional: show buffering right away in AA
                    // pushPlaybackState(PlaybackStateCompat.STATE_BUFFERING, 0);
                }
            });
        } else {
            // PLAYLIST EXISTS
            String playMode = pl.getPlayMode();
            if (Var.PLAY_MODE_RADIO.equals(playMode) || Var.PLAY_MODE_PODCAST.equals(playMode)) {
                myLog("Car onPlay, resuming... send play stream");
                playStream(context, playMode, pl.getUrl(), pl.getTrackId(), pl.getTitle(),
                        pl.getImageUrl(), "carOnPlay()");
            } else {
                ZikFile zikFile = pl.getZikFile();
                myLog("Car onPlay, resuming... send CMD play");
                sendCmdPlay(context);
            }
        }
    }

    public static void carOnPlayFromMediaId(Context context, String mediaId, Bundle extras) {
        myLogI("---- AUTOMOTIVE user click Play -----");
        myLog("media Id = [" + mediaId + "]   - extras : " + getBundleString(extras));
        FirebaseAnalyticsHelper.tellCarOnPlayFromMediaId();
        if (mediaId == null)
            return;

        if (mediaId.startsWith(PREFIX_TRACK)) {
            int trackId = safeParseInt(mediaId.substring(PREFIX_TRACK.length()), -1);
            if (trackId > 0) {
                ContextCompat.startForegroundService(
                        context,
                        new Intent(context, MediaService.class)
                                .setAction(Intents.ACTION_PLAY_FROM_TRACK)
                                .putExtra(Intents.EXTRA_TRACK_ID, trackId)
                                .putExtra(Intents.EXTRA_CALLER,
                                        context.getClass().getSimpleName() + ".onPlayFromMediaId() track")
                                .putExtra(Intents.EXTRA_FOREGROUND, true));
                // Optional: show buffering right away in AA
                // pushPlaybackState(PlaybackStateCompat.STATE_BUFFERING, 0);
            }
            return;
        }

        if (mediaId.startsWith(PREFIX_FOLDER)) {
            int folderId = safeParseInt(mediaId.substring(PREFIX_FOLDER.length()), -1);
            if (folderId > 0) {
                // If you want to play index 0 immediately (single-track or your UX choice):
                ContextCompat.startForegroundService(
                        context,
                        new Intent(context, MediaService.class)
                                .setAction(Intents.ACTION_PLAY_FROM_FOLDER)
                                .putExtra(Intents.EXTRA_FOLDER_ID, folderId)
                                .putExtra(Intents.EXTRA_INDEX, 0)
                                .putExtra(Intents.EXTRA_CALLER,
                                        context.getClass().getSimpleName() + ".onPlayFromMediaId() folder")
                                .putExtra(Intents.EXTRA_FOREGROUND, true));
                // pushPlaybackState(PlaybackStateCompat.STATE_BUFFERING, 0);
            }
        }
    }

    public static void loadChildrenImpl(Context context,
            @NonNull String parentId,
            @Nullable Bundle options,
            @NonNull MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> result) {
        long startTime = System.currentTimeMillis();
        FirebaseAnalyticsHelper.tellCarOnChildren();
        // Chargements DB → thread bg
        result.detach();

        // Optional: honor paging if host asks
        // int page = options != null ? options.getInt(MediaBrowserCompat.EXTRA_PAGE,
        // -1) : -1;
        // int pageSize = options != null ?
        // options.getInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, -1) :

        AppDatabase.databaseReadExecutor.execute(() -> {
            List<MediaBrowserCompat.MediaItem> out = new ArrayList<>();

            if (ROOT_ID.equals(parentId)) {
                List<Folder> folders = AppDatabase.getDatabase(context.getApplicationContext())
                        .folderDao().getAll();

                if (folders == null || folders.isEmpty()) {
                    out.add(browsable("hint", context.getString(R.string.automotive_no_item_in_bookplayer)));
                    result.sendResult(out);
                    return;
                }

                for (Folder f : folders) {
                    MediaDescriptionCompat.Builder b = new MediaDescriptionCompat.Builder()
                            .setMediaId(PREFIX_FOLDER + f.getId())
                            .setTitle(f.getName());

                    // Small icon
                    Bitmap icon = null;
                    if (f.image != null) {
                        icon = MediaService.iconCache.get(f.image);
                        if (icon == null) {
                            icon = ImageHelper.decodeBitmapFromStringUri(context.getApplicationContext(), f.image,
                                    ICON_MAX_PX);
                            if (icon != null)
                                MediaService.iconCache.put(f.image, icon);
                        }
                    }
                    if (icon != null)
                        b.setIconBitmap(icon);

                    // If only 1 track => Make the "folder" tap play directly
                    int count = AppDatabase.getDatabase(context.getApplicationContext())
                            .zikFileDao().countTracks(f.getId());
                    if (count == 1) {
                        ZikFile only = AppDatabase.getDatabase(context.getApplicationContext()).zikFileDao()
                                .getFirstInFolder(f.getId());
                        if (only != null) {
                            b.setSubtitle(only.getDisplayName()); // track label
                            out.add(new MediaBrowserCompat.MediaItem(b.build(),
                                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
                        }
                    } else {
                        out.add(new MediaBrowserCompat.MediaItem(b.build(),
                                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
                    }
                }

                result.sendResult(out);
                myLog("onChildren() : " + out.size() + " results sent in " + (System.currentTimeMillis() - startTime)
                        + "ms. for ROOT (parentId=" + parentId + ")");
                return;
            }

            if (parentId.startsWith(PREFIX_FOLDER)) {
                int folderId = safeParseInt(parentId.substring(PREFIX_FOLDER.length()), -1);
                if (folderId > 0) {
                    List<ZikFile> tracks = AppDatabase.getDatabase(context.getApplicationContext())
                            .zikFileDao().getZikFiles(folderId);

                    if (tracks == null || tracks.isEmpty()) {
                        out.add(browsable("hint", context.getString(R.string.automotive_empty_book)));
                        result.sendResult(out);
                        return;
                    }

                    // Put a "Resume" item first
                    ZikFile resume = AppDatabase.getDatabase(context.getApplicationContext()).zikFileDao()
                            .getLastListenedZikFileOfFolder(folderId);
                    if (resume != null) {
                        MediaDescriptionCompat.Builder rb = new MediaDescriptionCompat.Builder()
                                .setMediaId(PREFIX_TRACK + resume.getId())
                                .setTitle("▶ " + context.getString(R.string.automotive_resume_play) + " : \n"
                                        + resume.getDisplayName())
                                .setSubtitle(resume.getFolderName());
                        // optional icon from folder cover (reuse your icon code)
                        Folder f = AppDatabase.getDatabase(context.getApplicationContext()).folderDao()
                                .getById(folderId);
                        Bitmap icon = null;
                        if (f != null && f.image != null) {
                            icon = MediaService.iconCache.get(f.image);
                            if (icon == null) {
                                icon = ImageHelper.decodeBitmapFromStringUri(context.getApplicationContext(), f.image,
                                        ICON_MAX_PX);
                                if (icon != null)
                                    MediaService.iconCache.put(f.image, icon);
                            }
                        }
                        if (icon != null)
                            rb.setIconBitmap(icon);

                        Bundle rExtras = new Bundle();
                        if (resume.getDuration() > 0)
                            rExtras.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, (long) resume.getDuration());
                        rb.setExtras(rExtras);

                        out.add(new MediaBrowserCompat.MediaItem(rb.build(),
                                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
                    }

                    // Fetch folder (for its image)
                    Folder f = AppDatabase.getDatabase(context.getApplicationContext())
                            .folderDao().getById(folderId); // add DAO method if missing
                    android.graphics.Bitmap icon = null;
                    if (f != null && f.image != null) {
                        icon = MediaService.iconCache.get(f.image);
                        if (icon == null) {
                            icon = ImageHelper.decodeBitmapFromStringUri(context.getApplicationContext(), f.image,
                                    ICON_MAX_PX);
                            if (icon != null)
                                MediaService.iconCache.put(f.image, icon);
                        }
                    }
                    for (ZikFile z : tracks) {
                        MediaDescriptionCompat.Builder b = new MediaDescriptionCompat.Builder()
                                .setMediaId(PREFIX_TRACK + z.getId()) // or getIdZikFile()
                                .setTitle(z.getDisplayName())
                                .setSubtitle(z.getFolderName());
                        if (icon != null)
                            b.setIconBitmap(icon);

                        Bundle extras = new Bundle();
                        if (z.getDuration() > 0) {
                            extras.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, (long) z.getDuration());
                        }
                        b.setExtras(extras);

                        out.add(new MediaBrowserCompat.MediaItem(
                                b.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
                    }

                }
                myLogD("onChildren(" + parentId + ") " + out.size() + " results sent in "
                        + (System.currentTimeMillis() - startTime) + "ms.");
                result.sendResult(out);
                return;
            }

            myLogW("onChildren() sendResult emptyList for parentId=" + parentId);
            result.sendResult(Collections.emptyList());
        });
    }

    public static MediaBrowserServiceCompat.BrowserRoot onGetRoot(String clientPackageName, String callerInfo) {

        // Filtre au cas ou je ne set pas la permission dans le manifest pour le service
        // : android:permission="android.permission.BIND_MEDIA_BROWSER_SERVICE"

        if ("com.google.android.projection.gearhead".equals(clientPackageName)
                || "com.google.android.apps.automotive.inputmethod".equals(clientPackageName)
                || "AndroidAuto".equals(callerInfo)) {
            CarSignals.markCarConnected();
            FirebaseAnalyticsHelper.tellCarOnRoot();
        }

        Bundle extras = new Bundle();
        if ("AppUI".equals(callerInfo)) {
            extras.putBoolean(MediaBrowserServiceCompat.BrowserRoot.EXTRA_OFFLINE, true);
        } else {
            // Tell host we support styled lists (same keys AA passed you)
            extras.putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true);

            // 1 = list, 2 = grid
            extras.putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1); // Folders
            extras.putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1); // ZikFiles

            // These androidx flags matter for some AA versions:
            extras.putInt("androidx.media.MediaBrowserCompat.Extras.KEY_ROOT_CHILDREN_SUPPORTED_FLAGS", 1);
            extras.putInt("androidx.media.MediaBrowserCompat.Extras.KEY_ROOT_CHILDREN_LIMIT", 1000);

            // If you’re okay to be searchable:
            extras.putBoolean("android.media.browse.SEARCH_SUPPORTED", true);

            myLog("-----------");
            myLog("extras=" + "\n" + extras.toString().replace(",", "\n"));
        }

        return new MediaBrowserServiceCompat.BrowserRoot(ROOT_ID, extras);
    }

    public static void doSearch(Context context,
            @NonNull String query, Bundle extras,
            @NonNull MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> result) {
        myLogI("onSearch q=" + query + " extras=" + extras);
        result.detach();

        AppDatabase.databaseReadExecutor.execute(() -> {
            List<MediaBrowserCompat.MediaItem> out = new ArrayList<>();

            // naive search: match folder or track names containing the query
            String q = query.toLowerCase(Locale.US);
            for (Folder f : AppDatabase.getDatabase(context.getApplicationContext()).folderDao().getAll()) {
                if (f.getName().toLowerCase(Locale.US).contains(q)) {
                    MediaDescriptionCompat desc = new MediaDescriptionCompat.Builder()
                            .setMediaId(PREFIX_FOLDER + f.getId())
                            .setTitle(f.getName())
                            .build();
                    out.add(new MediaBrowserCompat.MediaItem(desc,
                            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
                }
            }
            for (ZikFile z : AppDatabase.getDatabase(context.getApplicationContext()).zikFileDao().getAll()) {
                if (z.getDisplayName().toLowerCase(Locale.US).contains(q)) {
                    MediaDescriptionCompat desc = new MediaDescriptionCompat.Builder()
                            .setMediaId(PREFIX_TRACK + z.getId())
                            .setTitle(z.getDisplayName())
                            .setSubtitle(z.getFolderName())
                            .build();
                    out.add(new MediaBrowserCompat.MediaItem(desc,
                            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
                }
            }

            result.sendResult(out);
        });

    }

    private static void sendCmdPlay(Context context) {
        FirebaseAnalyticsHelper.tellCarSendCmd("CMD_PLAY");
        ContextCompat.startForegroundService(
                context,
                new Intent(context, MediaService.class)
                        .setAction("CMD_PLAY")
                        .putExtra(Intents.EXTRA_CALLER, context.getClass().getSimpleName() + ".sendCmd " + "CMD_PLAY")
                        .putExtra(Intents.EXTRA_FOREGROUND, true));
    }

    public static void playStream(Context context, String playMode, String streamUrl, int trackId
            ,String title, String cover, String caller) {
        stopTtsIfPlaying(context, PlaybackUiBus.get().state().getValue());
        PlayList.createFromStream(context, playMode, streamUrl, trackId, title, cover);
        FirebaseAnalyticsHelper.tellAnalyticsStartStreaming(title, streamUrl, playMode);
        androidx.core.content.ContextCompat.startForegroundService(
                context,
                new Intent(context, MediaService.class)
                        .setAction(Intents.ACTION_PLAY_STREAM)
                        .putExtra(Intents.EXTRA_PLAY_MODE, playMode)
                        .putExtra(Intents.EXTRA_STREAM_URL, streamUrl)
                        .putExtra(Intents.EXTRA_STREAM_TRACK_ID, trackId)
                        //.putExtra(Intents.EXTRA_RADIO_STATION_UUID, uuid)
                        .putExtra(Intents.EXTRA_TITLE, title)
                        .putExtra(Intents.EXTRA_IMAGE_URL, cover)
                        .putExtra(Intents.EXTRA_CALLER, caller)
                        .putExtra(Intents.EXTRA_FOREGROUND, true));
    }

    public static void playUndefinedStream(Context context, String url) {
        AppDatabase.databaseReadExecutor.execute(() -> {
            boolean playStreamIfKnownRadio = RadioHelper.playStreamIfKnownRadio(context, url);
            if (playStreamIfKnownRadio) {
                return;
            }
            boolean playStreamIfKnownPodcast = PodcastHelper.playStreamIfKnownPodcast(context, url);
            if (playStreamIfKnownPodcast) {
                myLogEE(null, "could not play undefined stream : [" + url + "]");
            }
        });
    }

    // private Helpers
    private static MediaBrowserCompat.MediaItem browsable(String id, String title) {
        MediaDescriptionCompat desc = new MediaDescriptionCompat.Builder()
                .setMediaId(id)
                .setTitle(title)
                .build();
        return new MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);
    }

    private static void stopTtsIfPlaying(Context context, PlaybackUiState state) {
        if (state != null && Var.PLAY_MODE_TTS.equals(state.playMode) && state.playing) {
            myLogI("stopTtsIfPlaying: switching to non-TTS mode, killing TTS engine.");
            PlaybackCommands.stop(context.getApplicationContext());
        }
    }

    private static int safeParseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

}
