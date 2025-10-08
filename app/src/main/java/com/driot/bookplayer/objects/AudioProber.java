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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class AudioProber {

    private AudioProber() {}

    @Nullable
    public static AudioInfo probe(Context context, Uri uri) {
        if (uri == null) {
            myLogD("probe: null uri");
            return null;
        }

        final String display = bestDisplayName(context.getContentResolver(), uri);
        final String shown = (display != null ? display : safeLastSegment(uri));

        // 0) Quick hint
        final String hint = hintFromUri(uri);

        // 1) MMR: context+uri
        AudioInfo info = tryWithRetriever(context, uri, hint);
        if (isValid(info)) return info;

        // 2) file:// → MMR with plain path (OEMs sometimes only accept String path)
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            AudioInfo byPath = tryWithPath(uri);
            if (isValid(byPath)) return byPath;
        }

        // 3) MMR: FD
        info = tryWithFd(context, uri);
        if (isValid(info)) return info;

        // 4) MediaExtractor fallback (dur only; no tags/cover)
        info = tryWithMediaExtractor(context, uri, shown, hint + "+ex");
        if (isValid(info)) return info;

        /*
        // 5) Last resort: temp copy + re-try (both MMR + MediaExtractor)
        File tmp = copyToTemp(context, uri, shown);
        if (tmp != null) {
            try {
                // 5a) MMR on temp file
                AudioInfo fromFile = tryWithFile(tmp, uri, "temp-copy");
                if (isValid(fromFile)) return fromFile;

                // 5b) EX on temp file (file path variant)
                AudioInfo exFile = tryWithMediaExtractorOnFile(tmp, uri, shown, "temp-copy+ex");
                if (isValid(exFile)) return exFile;

            } finally {
                // remove or keep as cache depending on your policy
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        }
         */

        myLogW("probe: failed for " + uri);
        return new AudioInfo(uri, (shown != null ? shown : ""),
                0L, null, null, null, null, "unreadable");
    }

    private static boolean isValid(@Nullable AudioInfo info) {
        return info != null && info.durationMs > 0;
    }

    // --------------------- MMR strategies ---------------------

    @Nullable
    private static AudioInfo tryWithRetriever(Context context, Uri uri, String sourceHint) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(context, uri);
            return buildInfoFromRetriever(context, uri, mmr, sourceHint);
        } catch (Exception e) {
            myLogD("tryWithRetriever failed for " + uri + " : " + e.getMessage());
            return null;
        } finally {
            try { mmr.release(); } catch (Throwable ignore) {}
        }
    }

    @Nullable
    private static AudioInfo tryWithFd(Context context, Uri uri) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r")) {
            if (pfd == null) return null;
            mmr.setDataSource(pfd.getFileDescriptor());
            return buildInfoFromRetriever(context, uri, mmr, hintFromUri(uri) + "+fd");
        } catch (Exception e) {
            myLogD("tryWithFd failed for " + uri + " : " + e.getMessage());
            return null;
        } finally {
            try { mmr.release(); } catch (Throwable ignore) {}
        }
    }

    // For file:// only — some OEMs accept only String paths.
    @Nullable
    private static AudioInfo tryWithPath(Uri fileUri) {
        if (!"file".equalsIgnoreCase(fileUri.getScheme())) return null;
        String path = fileUri.getPath();
        if (path == null) return null;
        File f = new File(path);
        if (!f.exists() || f.length() <= 0) return null;

        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(path); // String path variant
            return buildInfoFromRetriever(null, fileUri, mmr, "file-path");
        } catch (Exception e) {
            myLogD("tryWithPath failed for " + path + " : " + e.getMessage());
            return null;
        } finally {
            try { mmr.release(); } catch (Throwable ignore) {}
        }
    }

    @Nullable
    private static AudioInfo tryWithFile(File file, Uri originalUri, String sourceHint) {
        if (file == null || !file.exists() || file.length() <= 0) return null;
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try (FileInputStream fis = new FileInputStream(file)) {
            mmr.setDataSource(fis.getFD());
            return buildInfoFromRetriever(null, originalUri, mmr, sourceHint);
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
            if (Build.VERSION.SDK_INT >= 23) {
                ex.setDataSource(context, uri, null);
            } else if ("file".equalsIgnoreCase(uri.getScheme())) {
                String path = uri.getPath();
                if (path == null) return null;
                ex.setDataSource(path);
            } else {
                try (AssetFileDescriptor afd = context.getContentResolver().openAssetFileDescriptor(uri, "r")) {
                    if (afd == null) return null;
                    ex.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                }
            }

            long durUs = extractMaxDurationUs(ex);
            long durMs = (durUs > 0 ? durUs / 1000 : 0);
            if (durMs <= 0) return null;

            return new AudioInfo(uri, (display != null ? display : ""),
                    durMs, null, null, null, null, sourceHint);
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

            return new AudioInfo(originalUri, (display != null ? display : ""),
                    durMs, null, null, null, null, sourceHint);
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
                                                    String sourceHint) {
        String title  = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        String artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
        String album  = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);

        long duration = 0L;
        String durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        if (durStr != null) {
            try { duration = Long.parseLong(durStr); } catch (NumberFormatException ignore) {}
        }

        Bitmap cover = null;
        try {
            byte[] art = mmr.getEmbeddedPicture();
            if (art != null) cover = BitmapFactory.decodeByteArray(art, 0, art.length);
        } catch (Throwable ignored) {}

        String display = (context != null)
                ? bestDisplayName(context.getContentResolver(), uri)
                : null;
        if (display == null) display = safeLastSegment(uri);

        return new AudioInfo(uri, (display != null ? display : ""),
                duration, title, artist, album, cover, sourceHint);
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
    public static String bestDisplayName(ContentResolver cr, Uri uri) {
        if (uri == null) return null;
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
            if ("content".equalsIgnoreCase(uri.getScheme()) && DocumentsContract.isDocumentUri(null, uri)) {
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
