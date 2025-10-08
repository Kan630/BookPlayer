package com.driot.bookplayer.objects;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import androidx.annotation.Nullable;

import com.driot.bookplayer.objects.AudioInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class AudioProber {

    private AudioProber() {}

    /**
     * Probe a Uri robustly and return a single AudioInfo object.
     * Will try: direct URI -> FD -> temp copy fallback.
     */
    @Nullable
    public static AudioInfo probe(Context context, Uri uri) {
        if (uri == null) {
            myLogW("probe: null uri");
            return null;
        }

        String display = bestDisplayName(context.getContentResolver(), uri);
        if (display == null) display = safeLastSegment(uri);

        // 1) Try direct setDataSource(context, uri)
        AudioInfo info = tryWithRetriever(context, uri, /*sourceHint*/hintFromUri(uri));
        if (info != null) return info;

        // 2) Try with FD
        info = tryWithFd(context, uri);
        if (info != null) return info;

        // 3) Last resort: copy to temp and probe from file path (KEEP RARE)
        /*
        File tmp = copyToTemp(context, uri, display);
        if (tmp != null) {
            try {
                AudioInfo fromFile = tryWithFile(tmp, uri, "temp-copy");
                if (fromFile != null) return fromFile;
            } finally {
                // You can decide to keep it for caching; for now we remove.
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        }

         */

        myLogW("probe: failed for " + uri);
        return new AudioInfo(uri, display, 0L, null, null, null, null, "unreadable");
    }

    // --------------------- Strategies ---------------------

    @Nullable
    private static AudioInfo tryWithRetriever(Context context, Uri uri, String sourceHint) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(context, uri);
            return buildInfoFromRetriever(context, uri, mmr, sourceHint);
        } catch (Exception e) {
            myLogW("tryWithRetriever failed for " + uri + " : " + e.getMessage());
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
            myLogW("tryWithFd failed for " + uri + " : " + e.getMessage());
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
            myLogW("tryWithFile failed for " + file + " : " + e.getMessage());
            return null;
        } finally {
            try { mmr.release(); } catch (Throwable ignore) {}
        }
    }

    // --------------------- Builders & helpers ---------------------

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

        // pick a good display name
        String display = (context != null)
                ? bestDisplayName(context.getContentResolver(), uri)
                : null;
        if (display == null) display = safeLastSegment(uri);

        return new AudioInfo(uri, display != null ? display : "",
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
            myLogW("copyToTemp failed for " + uri + " : " + e.getMessage());
            return null;
        }
    }

    // Display name for any content:// Uri; falls back to last segment.
    @Nullable
    public static String bestDisplayName(ContentResolver cr, Uri uri) {
        if (uri == null) return null;
        // OpenableColumns works for SAF + most providers
        try (Cursor c = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = c.getString(idx);
                    if (name != null && !name.isEmpty()) return name;
                }
            }
        } catch (Exception ignored) {}

        // Some DocumentsProvider expose documentId that includes filename
        try {
            if (isDocumentsProviderUri(uri)) {
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

    private static boolean isDocumentsProviderUri(Uri uri) {
        try { return "content".equalsIgnoreCase(uri.getScheme())
                && DocumentsContract.isDocumentUri(null, uri); }
        catch (Throwable t) { return uri.toString().contains("/document/"); }
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
