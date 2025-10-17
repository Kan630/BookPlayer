package com.driot.bookplayer.objects;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class AudioProber {

    private AudioProber() {}

    @Nullable
    public static AudioInfo probe(Context context, Uri uri, boolean doExtractCover) {
        if (uri == null) {
            myLogD("probe: null uri");
            return null;
        }

        final String display = bestDisplayName(context, uri);
        final String shown = (display != null ? display : safeLastSegment(uri));

        // 0) Quick hint
        final String hint = hintFromUri(uri);

        // 1) MMR: context+uri
        AudioInfo info = tryWithRetriever(context, uri, hint, doExtractCover);
        if (isValid(info)) return info;

        // 2) file:// → MMR with plain path (OEMs sometimes only accept String path)
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            AudioInfo byPath = tryWithPath(uri, doExtractCover);
            if (isValid(byPath)) return byPath;
        }

        // 3) MMR: FD
        info = tryWithFd(context, uri, doExtractCover);
        if (isValid(info)) return info;

        // 4) MediaExtractor fallback (dur only; no tags/cover)
        info = tryWithMediaExtractor(context, uri, shown, hint + "+ex");
        if (isValid(info)) return info;

        // 5) Final fallback: unreadable
        myLogW("probe: failed for " + uri);
        return new AudioInfo(
                uri,
                (shown != null ? shown : ""),
                0L,
                null,
                "unreadable",
                /* metadata */ new HashMap<>()
        );
    }

    private static boolean isValid(@Nullable AudioInfo info) {
        return info != null && info.durationMs > 0;
    }

    // --------------------- MMR strategies ---------------------

    @Nullable
    private static AudioInfo tryWithRetriever(Context context, Uri uri, String sourceHint, boolean doExtractCover) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(context, uri);
            return buildInfoFromRetriever(context, uri, mmr, sourceHint, doExtractCover);
        } catch (Exception e) {
            myLogD("tryWithRetriever failed for " + uri + " : " + e.getMessage());
            return null;
        } finally {
            try { mmr.release(); } catch (Throwable ignore) {}
        }
    }

    @Nullable
    private static AudioInfo tryWithFd(Context context, Uri uri, boolean doExtractCover) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r")) {
            if (pfd == null) return null;
            mmr.setDataSource(pfd.getFileDescriptor());
            return buildInfoFromRetriever(context, uri, mmr, hintFromUri(uri) + "+fd", doExtractCover);
        } catch (Exception e) {
            myLogD("tryWithFd failed for " + uri + " : " + e.getMessage());
            return null;
        } finally {
            try { mmr.release(); } catch (Throwable ignore) {}
        }
    }

    // For file:// only — some OEMs accept only String paths.
    @Nullable
    private static AudioInfo tryWithPath(Uri fileUri, boolean doExtractCover) {
        if (!"file".equalsIgnoreCase(fileUri.getScheme())) return null;
        String path = fileUri.getPath();
        if (path == null) return null;
        File f = new File(path);
        if (!f.exists() || f.length() <= 0) return null;

        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(path); // String path variant
            return buildInfoFromRetriever(null, fileUri, mmr, "file-path", doExtractCover);
        } catch (Exception e) {
            myLogD("tryWithPath failed for " + path + " : " + e.getMessage());
            return null;
        } finally {
            try { mmr.release(); } catch (Throwable ignore) {}
        }
    }

    @Nullable
    private static AudioInfo tryWithFile(File file, Uri originalUri, String sourceHint, boolean doExtractCover) {
        if (file == null || !file.exists() || file.length() <= 0) return null;
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try (FileInputStream fis = new FileInputStream(file)) {
            mmr.setDataSource(fis.getFD());
            return buildInfoFromRetriever(null, originalUri, mmr, sourceHint, doExtractCover);
        } catch (Exception e) {
            myLogD("tryWithFile failed for " + file + " : " + e.getMessage());
            return null;
        } finally {
            try { mmr.release(); } catch (Throwable ignore) {}
        }
    }

    // --------------------- MediaExtractor strategies ---------------------

    @Nullable
    private static AudioInfo tryWithMediaExtractor(Context context, Uri uri, String display, String sourceHint) {
        MediaExtractor ex = new MediaExtractor();
        try {
            // (API 23+ path; if you support <23, add the AFD fallback again)
            ex.setDataSource(context, uri, null);

            long durUs = extractMaxDurationUs(ex);
            long durMs = (durUs > 0 ? durUs / 1000 : 0);
            if (durMs <= 0) return null;

            return new AudioInfo(
                    uri,
                    (display != null ? display : ""),
                    durMs,
                    null,
                    sourceHint,
                    /* metadata */ new HashMap<>()
            );
        } catch (Exception e) {
            myLogD("tryWithMediaExtractor failed for " + uri + " : " + e.getMessage());
            return null;
        } finally {
            try { ex.release(); } catch (Throwable ignore) {}
        }
    }

    @Nullable
    private static AudioInfo tryWithMediaExtractorOnFile(File file, Uri originalUri, String display, String sourceHint) {
        if (file == null || !file.exists() || file.length() <= 0) return null;
        MediaExtractor ex = new MediaExtractor();
        try {
            ex.setDataSource(file.getAbsolutePath());
            long durUs = extractMaxDurationUs(ex);
            long durMs = (durUs > 0 ? durUs / 1000 : 0);
            if (durMs <= 0) return null;

            return new AudioInfo(
                    originalUri,
                    (display != null ? display : ""),
                    durMs,
                    null,
                    sourceHint,
                    /* metadata */ new HashMap<>()
            );
        } catch (Exception e) {
            myLogD("tryWithMediaExtractorOnFile failed for " + file + " : " + e.getMessage());
            return null;
        } finally {
            try { ex.release(); } catch (Throwable ignore) {}
        }
    }

    private static long extractMaxDurationUs(MediaExtractor ex) {
        long max = 0;
        try {
            final int tracks = ex.getTrackCount();
            for (int i = 0; i < tracks; i++) {
                MediaFormat fmt = ex.getTrackFormat(i);
                if (fmt != null && fmt.containsKey(MediaFormat.KEY_DURATION)) {
                    long d = fmt.getLong(MediaFormat.KEY_DURATION);
                    if (d > max) max = d;
                }
            }
        } catch (Throwable ignored) {}
        return max;
    }

    // --------------------- Shared builders & utils ---------------------

    @Nullable
    private static AudioInfo buildInfoFromRetriever(@Nullable Context context,
                                                    Uri uri,
                                                    MediaMetadataRetriever mmr,
                                                    String sourceHint,
                                                    boolean doExtractCover) {

        //TODO check the use of MediaExtractor extractor = new MediaExtractor();
        // extractor.setDataSource(filePath); MediaFormat format = extractor.getTrackFormat(i); format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)

        // Common tags
        String title   = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        String artist  = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
        String album   = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
        String composer= mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER);
        String writer  = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER);
        String compilation = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPILATION);
        String location = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION);
        String genre   = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
        String year    = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR);
        String track   = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER);
        String disc    = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER);

        String mime    = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
        String bitrate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
        String srate   = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            srate   = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE);
        }

        long duration = 0L;
        String durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        if (durStr != null) {
            try { duration = Long.parseLong(durStr); } catch (NumberFormatException ignore) {}
        }

        Bitmap cover = null;
        if (doExtractCover) {
            try {
                byte[] art = mmr.getEmbeddedPicture();
                if (art != null) cover = BitmapFactory.decodeByteArray(art, 0, art.length);
            } catch (Throwable ignored) {}
        }

        String display = (context != null)
                ? bestDisplayName(context, uri)
                : null;
        if (display == null) display = safeLastSegment(uri);

        // Build metadata map
        Map<String,String> meta = new HashMap<>();
        putIfNotEmpty(meta, "mime",    mime);
        putIfNotEmpty(meta, AudioInfo.K_TITLE,  title);
        putIfNotEmpty(meta, AudioInfo.K_ARTIST, artist);
        putIfNotEmpty(meta, "composer",  composer);
        putIfNotEmpty(meta, AudioInfo.K_ALBUM,  album);
        putIfNotEmpty(meta, AudioInfo.K_GENRE,  genre);
        putIfNotEmpty(meta, AudioInfo.K_YEAR,   year);
        putIfNotEmpty(meta, "track",   track);
        putIfNotEmpty(meta, "disc",    disc);
        putIfNotEmpty(meta, "bitrate", prettyBitrate(bitrate));
        putIfNotEmpty(meta, "samplerate", prettyBitrate(srate));
        putIfNotEmpty(meta, "location", location);
        putIfNotEmpty(meta, "writer",  writer);
        putIfNotEmpty(meta, "compilation", compilation);


        return new AudioInfo(
                uri,
                (display != null ? display : ""),
                duration,
                cover,
                sourceHint,
                meta
        );
    }

    private static String prettyBitrate(String bitrate) {
        if (bitrate == null || bitrate.trim().isEmpty()) return null;
        try {
            return (Long.parseLong(bitrate)/1000 + " kHz");
        } catch (Exception e) {
            myLogEE(e, "pretty bitrate error");
            return bitrate;
        }
    }

    private static void putIfNotEmpty(Map<String,String> m, String k, @Nullable String v) {
        if (v != null) {
            String t = v.trim();
            if (!t.isEmpty()) m.put(k, t);
        }
    }

    @Nullable
    private static File copyToTemp(Context context, Uri uri, String displayName) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            String base = (displayName == null || displayName.isEmpty()) ? "audio" : displayName;
            File out = File.createTempFile(sanitize(base), null, context.getCacheDir());
            try (FileOutputStream fos = new FileOutputStream(out)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
            }
            return out;
        } catch (Exception e) {
            myLogD("copyToTemp failed for " + uri + " : " + e.getMessage());
            return null;
        }
    }

    @Nullable
    public static String bestDisplayName(Context context, Uri uri) {
        if (uri == null) return null;

        ContentResolver cr = context.getContentResolver();

        try (Cursor c = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = c.getString(idx);
                    if (name != null && !name.isEmpty()) return name;
                }
            }
        } catch (Exception ignored) {}

        try {
            if ("content".equalsIgnoreCase(uri.getScheme()) && DocumentsContract.isDocumentUri(context, uri)) {
                String docId = DocumentsContract.getDocumentId(uri);
                if (docId != null) {
                    int slash = docId.lastIndexOf('/');
                    if (slash >= 0 && slash + 1 < docId.length()) {
                        String tail = docId.substring(slash + 1);
                        if (!tail.isEmpty()) return tail;
                    }
                }
            }
        } catch (Exception ignored) {}

        return safeLastSegment(uri);
    }

    @Nullable
    private static String safeLastSegment(Uri uri) {
        try { return uri.getLastPathSegment(); } catch (Throwable t) { return null; }
    }

    private static String sanitize(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }

    private static String hintFromUri(Uri uri) {
        String scheme = uri.getScheme();
        String auth = uri.getAuthority();
        return (scheme == null ? "raw" : scheme) + "://" + (auth == null ? "" : auth);
    }
}
