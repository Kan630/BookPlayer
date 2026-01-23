package com.driot.bookplayer.helpers;

import static com.driot.bookplayer.global.Var.MAX_IMAGE_SIZE_KB;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.Podcast;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.radio.RadioStation;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;

public class ImageHelper {

    public static final int MAX_IMAGE_WIDTH = 1280;
    public static final int MAX_IMAGE_HEIGHT = 1280;

    public static final String IMAGE_PREFIX_FOR_PODCAST_COVERS = "podcast_feed_";
    public static final String IMAGE_PREFIX_FOR_RADIO_COVERS = "radio_station_";
    public static final String IMAGE_PREFIX_FOR_LIBRIVOX_COVERS = "librivox_img_";
    public static final String IMAGE_PREFIX_FOR_SAVED_BOOK = "folder_id_";
    public static final String IMAGE_PREFIX_FOR_TEMP_FILE = "tmp_img";

    // TODO ASYNC...
    private static String downloadAndMaybeCompressImage(Context context, String imageUrl, String imagePath,
            boolean isCached) {
        try {
            byte[] imageBytes = NetworkHelper.fetchBytesWithHttpsFallbackForImage(imageUrl);
            if (imageBytes == null)
                return null;

            // Optional but recommended: ensure it’s actually an image (prevents saving HTML
            // error pages)
            if (!isLikelyImage(imageBytes)) {
                myLogE("Not an image (decode failed): " + imageUrl);
                return null;
            }

            return compressAndSaveImage(context, imageBytes, imagePath, isCached);

        } catch (Throwable t) {
            myLogEE(t, "downloadAndMaybeCompressImage() failed for: " + imageUrl);
            return null;
        }
    }

    private static String saveBytesToFile(Context context, byte[] data, String imagePath, boolean isCached)
            throws IOException {
        File dir = StorageHelper.getImageFolder(context, isCached);
        if (!dir.exists())
            dir.mkdirs();
        File imageFile = new File(dir, imagePath);
        FileOutputStream fos = new FileOutputStream(imageFile);
        fos.write(data);
        fos.close();
        myLogD("image saved [" + imagePath + "] - " + (imageFile.length() / 1024) + "KB");
        return imageFile.getAbsolutePath();
    }

    public static void processPendingImages(Context context) {
        // myLogD("processPendingImages");
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(context);

            // --- 0) Migrate folder images from cached_images -> images ---
            try {
                List<Folder> allFolders = db.folderDao().getAll(); // you already use this elsewhere
                for (Folder f : allFolders) {
                    String path = f.image;
                    if (path == null || path.isEmpty())
                        continue;

                    // Only local absolute files (skip URIs)
                    if (path.startsWith("content://") || path.startsWith("file://"))
                        continue;

                    // If in cached_images, move it
                    String moved = moveCachedImageToPermanent(context, path);
                    if (moved != null && !moved.equals(path)) {
                        try {
                            db.folderDao().updateImage(f.getId(), moved);
                            myLogD("Folder image path updated (cache->images): id=" + f.getId() + "  " + moved);
                        } catch (Exception e) {
                            myLogEE(e, "DB update after moving cached image (folderId=" + f.getId() + ")");
                        }
                    }
                }
            } catch (Exception e) {
                myLogEE(e, "processPendingImages: cached->images migration block");
            }

            // --- Handle Podcast images ---
            List<Podcast> pendingPodcasts = db.podcastDao().getAllWithRemoteImage();
            for (Podcast podcast : pendingPodcasts) {
                String url = podcast.image;
                if (url == null || !url.startsWith("http"))
                    continue;

                String imagePath = IMAGE_PREFIX_FOR_PODCAST_COVERS + podcast.feedId + ".jpg";
                String localPath = downloadAndMaybeCompressImage(context, url, imagePath, true);
                if (localPath != null) {
                    podcast.image = localPath;
                    db.podcastDao().update(podcast);
                }
            }

            // Move Folder cover if on SD card
            try {
                List<Folder> allFolders = db.folderDao().getAll();
                for (Folder f : allFolders) {
                    String path = f.image;
                    if (path == null || path.isEmpty())
                        continue;

                    // Check if it's on SD card (absolute path or content URI)
                    if (path.startsWith("/storage/") ||
                            path.startsWith("/sdcard/") ||
                            path.startsWith("content://com.android.externalstorage")) {

                        String localPath = checkAndCopySdCardCoverToLocal(context, path, f.getId());
                        myLog("Folder image to move from SD card: [" + path + "] => [" + localPath + "]");

                        if (localPath != null && !localPath.equals(path)) {
                            try {
                                db.folderDao().updateImage(f.getId(), localPath);
                                myLogD("Folder cover migrated from SD to local: id=" + f.getId());
                            } catch (Exception e) {
                                myLogEE(e, "DB update after SD->local migration (folderId=" + f.getId() + ")");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                myLogEE(e, "processPendingImages: SD->local migration block");
            }

            // --- Handle Folder images ---
            List<Folder> pendingFolders = db.folderDao().getAllWithRemoteImage();
            for (Folder folder : pendingFolders) {
                String url = folder.image;

                if (url == null)
                    continue;

                String localPath = null;
                String imagePath = IMAGE_PREFIX_FOR_SAVED_BOOK + folder.getId() + ".jpg";

                if (url.startsWith("http")) {
                    localPath = downloadAndMaybeCompressImage(context, url, imagePath, false);
                } else if (isContentUri(url)) {
                    localPath = copyContentUriToImageFile(context, url, imagePath, false);
                }

                if (localPath != null) {
                    folder.image = localPath;
                    db.folderDao().update(folder);
                }
            }

            // --- Handle Radio images ---
            if (NetworkHelper.hasInternet(context)) {
                List<RadioStation> radioStations = db.radioStationDao().getAllWithExternalImages();
                for (RadioStation radioStation : radioStations) {
                    String url = radioStation.favicon;
                    String imagePath = IMAGE_PREFIX_FOR_RADIO_COVERS + radioStation.stationuuid + ".jpg";
                    String localPath = null;
                    localPath = downloadAndMaybeCompressImage(context, url, imagePath, false);
                    if (localPath != null) {
                        File f = new File(localPath);
                        if (f.exists() && f.length() > 0L) {
                            // OK, non-empty file → persist local path
                            radioStation.favicon = localPath;
                            db.radioStationDao().update(radioStation);
                        } else {
                            // 0 KB or missing → treat as failure, clean up
                            myLogW("Radio favicon download failed or empty (" + f.length() + " bytes) for " + url);
                            if (f.exists() && f.length() == 0L) {
                                try {
                                    myLog("deleting bad file, success=" + f.delete());
                                } catch (Exception ignored) {
                                }
                            }
                            // keep old favicon URL in DB so Glide can still try remote
                        }
                    }
                }
            }

        });
    }

    public static String getOrDownloadLibrivoxImage(Context context, String identifier, String imageUrl,
            boolean forceDownload) {
        String imagePath = IMAGE_PREFIX_FOR_LIBRIVOX_COVERS + identifier + ".jpg";
        File imageFile = new File(StorageHelper.getImageFolder(context, true), imagePath);

        if (imageFile.exists() && !forceDownload) {
            myLogD("Librivox image already exists: " + imageFile.getAbsolutePath());
            return imageFile.getAbsolutePath();
        }

        myLogI("Downloading Librivox image for: " + identifier);
        return downloadAndMaybeCompressImage(context, imageUrl, imagePath, true);
    }

    public static File getLibrivoxImageFile(Context context, String identifier) {
        File dir = StorageHelper.getImageFolder(context, true);
        return new File(dir, IMAGE_PREFIX_FOR_LIBRIVOX_COVERS + identifier + ".jpg");
    }

    public static String copyContentUriToImageFile(Context context, String uriOrPath, String outputFileName,
            boolean isCached) {
        try {
            InputStream in;
            Uri uri;

            if (uriOrPath.startsWith("content://") || uriOrPath.startsWith("file://")) {
                uri = Uri.parse(uriOrPath);
            } else {
                File file = new File(uriOrPath);
                uri = FileProvider.getUriForFile(context, context.getPackageName() + ".FileProvider", file);
            }

            in = context.getContentResolver().openInputStream(uri);
            if (in == null)
                return null;

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            in.close();

            byte[] imageBytes = out.toByteArray();
            return compressAndSaveImage(context, imageBytes, outputFileName, isCached);

        } catch (Exception e) {
            myLogEE(e, "copyContentUriToImageFile() failed for: " + uriOrPath);
            return null;
        }
    }

    public static String saveTempBitmap(Context context, Bitmap bitmap) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            // Use PNG to keep original quality before compressing later
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            byte[] imageBytes = out.toByteArray();
            return compressAndSaveImage(context, imageBytes, IMAGE_PREFIX_FOR_TEMP_FILE + ".jpg", true);
        } catch (Exception e) {
            myLogEE(e, "saveTempBitmap");
            return null;
        }
    }

    public static String saveTempBitmap(Context context, Bitmap bitmap, String suffix) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            // Use PNG to keep original quality before compressing later
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            byte[] imageBytes = out.toByteArray();
            return compressAndSaveImage(context, imageBytes, IMAGE_PREFIX_FOR_TEMP_FILE + suffix + ".jpg", true);
        } catch (Exception e) {
            myLogEE(e, "saveTempBitmap with suffix");
            return null;
        }
    }

    public static void deleteTempImportImage(Context context) {
        File imageDir = StorageHelper.getImageFolder(context, true);
        File tmpFile = new File(imageDir, IMAGE_PREFIX_FOR_TEMP_FILE + ".jpg");

        if (tmpFile.exists()) {
            boolean deleted = tmpFile.delete();
            if (deleted) {
                myLog("Temp import image deleted: " + tmpFile.getAbsolutePath());
            } else {
                myLogE("Failed to delete temp import image: " + tmpFile.getAbsolutePath());
            }
        } else {
            myLogD("No temp import image to delete at: " + tmpFile.getAbsolutePath());
        }
    }

    public static void deleteTempImportImage(Context context, String suffix) {
        File imageDir = StorageHelper.getImageFolder(context, true);
        File tmpFile = new File(imageDir, IMAGE_PREFIX_FOR_TEMP_FILE + suffix + ".jpg");

        if (tmpFile.exists()) {
            boolean deleted = tmpFile.delete();
            if (deleted) {
                myLog("Temp import image deleted: " + tmpFile.getAbsolutePath());
            } else {
                myLogE("Failed to delete temp import image: " + tmpFile.getAbsolutePath());
            }
        }
    }

    public static void finalizeTempFolderImage(Context context, int folderId) {
        finalizeTempFolderImage(context, folderId, "");
    }

    public static void finalizeTempFolderImage(Context context, int folderId, String suffix) {
        String safeSuffix = (suffix == null) ? "" : suffix;
        File tmpFile = new File(StorageHelper.getImageFolder(context, true),
                IMAGE_PREFIX_FOR_TEMP_FILE + safeSuffix + ".jpg");
        File newFile = new File(StorageHelper.getImageFolder(context, false),
                IMAGE_PREFIX_FOR_SAVED_BOOK + folderId + ".jpg");

        if (!tmpFile.exists()) {
            myLogD("Temp image not found: " + tmpFile.getAbsolutePath());
            return;
        }

        boolean renamed = tmpFile.renameTo(newFile);
        if (!renamed) {
            myLogE("Failed to rename temp image to: " + newFile.getAbsolutePath());
            return;
        }

        myLog("Temp image renamed to: " + newFile.getAbsolutePath());

        // Update folder in DB
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Folder folder = AppDatabase.getDatabase(context).folderDao().getById(folderId);
            if (folder != null) {
                folder.image = newFile.getAbsolutePath();
                AppDatabase.getDatabase(context).folderDao().update(folder);
                myLog("Folder DB updated with new image path");
            } else {
                myLogE("Folder not found for ID: " + folderId);
            }
        });
    }

    private static boolean isContentUri(String s) {
        return s != null && s.startsWith("content://");
    }

    private static String compressAndSaveImage(Context context, byte[] imageBytes, String outputFileName,
            boolean isCached) throws IOException {
        if (imageBytes.length / 1024 <= MAX_IMAGE_SIZE_KB) {
            return saveBytesToFile(context, imageBytes, outputFileName, isCached);
        }

        myLogD("Image too big (" + imageBytes.length / 1024 + "KB), compressing...");

        Bitmap originalBitmap;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, bounds);

            int inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_IMAGE_WIDTH,
                    MAX_IMAGE_HEIGHT);

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = inSampleSize;
            originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);
        } catch (OutOfMemoryError oom) {
            myLogE("OOM while decoding large image");
            return null;
        }

        if (originalBitmap == null) {
            myLogE("Failed to decode image");
            return null;
        }
        Bitmap resizedBitmap = resizeIfNeeded(originalBitmap);

        if (resizedBitmap != originalBitmap && !originalBitmap.isRecycled()) {
            originalBitmap.recycle();
        }

        int quality = 75;
        byte[] compressedBytes;

        do {
            ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, compressedOut);
            compressedBytes = compressedOut.toByteArray();
            myLogD("pic compressed to " + compressedBytes.length / 1024 + "KB, quality: " + quality + "%");
            quality -= 15;
        } while (compressedBytes.length / 1024 > MAX_IMAGE_SIZE_KB && quality >= 40);

        if (!resizedBitmap.isRecycled()) {
            resizedBitmap.recycle();
        }

        return saveBytesToFile(context, compressedBytes, outputFileName, isCached);
    }

    private static Bitmap resizeIfNeeded(Bitmap original) {
        int width = original.getWidth();
        int height = original.getHeight();

        if (width <= MAX_IMAGE_WIDTH && height <= MAX_IMAGE_HEIGHT) {
            return original; // No need to resize
        }

        float widthRatio = (float) MAX_IMAGE_WIDTH / width;
        float heightRatio = (float) MAX_IMAGE_HEIGHT / height;
        float scaleRatio = Math.min(widthRatio, heightRatio); // preserve aspect ratio

        int newWidth = Math.round(width * scaleRatio);
        int newHeight = Math.round(height * scaleRatio);

        myLogD("Resizing image from " + width + "x" + height + " to " + newWidth + "x" + newHeight);

        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
    }

    private static int calculateInSampleSize(int width, int height, int reqWidth, int reqHeight) { // used for old
                                                                                                   // device with low
                                                                                                   // memory...
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    public static void deleteImage(Context context, Folder folder) {
        if (folder.image != null) {
            try {
                Uri uri = Uri.parse(folder.image);
                DocumentFile file = UriHelper.getDocumentFileFromAnyUri(context, uri);
                if (file != null && file.exists()) {
                    if (!file.delete()) {
                        myLogEE(null, "Error deleting image file from content URI: " + folder.image);
                    } else {
                        myLogD("Image deleted successfully: " + folder.image);
                    }
                } else {
                    myLogE("deleteImage: URI points to non-existing file: " + folder.image);
                }
            } catch (Exception e) {
                myLogEE(e, "deleteImage: exception when trying to delete image");
            }
        } else {
            myLogD("deleteImage: no image in folder");
        }
    }

    // === Fallback cover generation (initials over colored background) ===

    private static String createAndSaveFallbackImage(Context context, String fileName, String title, int sizePx) {
        try {
            Bitmap bmp = createInitialsBitmap(title, sizePx, /* rounded= */true);
            // Encode once to JPEG, then let your existing compressor enforce
            // MAX_IMAGE_SIZE_KB
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out);
            bmp.recycle();
            byte[] bytes = out.toByteArray();
            return compressAndSaveImage(context, bytes, fileName, false);
        } catch (Exception e) {
            myLogEE(e, "createAndSaveFallbackImage failed");
            return null;
        }
    }

    /** Build a bitmap with pastel background + centered initials. */
    public static Bitmap createInitialsBitmap(String title, int sizePx, boolean rounded) {
        String initials = getInitials(title);
        int bg = getColorFromTitle(title);
        return createInitialsBitmapCustom(initials, bg, sizePx, rounded);
    }

    /** Same rendering but with explicit initials & color for the generator UI. */
    public static Bitmap createInitialsBitmapCustom(String initials, int bgColor, int sizePx, boolean rounded) {
        if (initials == null)
            initials = "";
        initials = initials.trim();
        if (initials.length() > 5)
            initials = initials.substring(0, 5); // hard cap

        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(bgColor);

        if (rounded) {
            float r = sizePx * 0.12f;
            c.drawRoundRect(new RectF(0, 0, sizePx, sizePx), r, r, p);
        } else {
            c.drawRect(0, 0, sizePx, sizePx, p);
        }

        // Draw initials
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextSize(sizePx * (initials.length() <= 1 ? 0.55f : 0.42f));

        Paint.FontMetrics fm = p.getFontMetrics();
        float x = sizePx / 2f;
        float y = sizePx / 2f - (fm.ascent + fm.descent) / 2f;
        c.drawText(initials, x, y, p);

        return bmp;
    }

    private static String getInitials(String title) {
        if (title == null)
            return "?";
        String t = title.trim();
        if (t.isEmpty())
            return "?";

        // For CJK scripts (Chinese, Japanese, Korean), just take the first 2 chars
        int firstCodePoint = t.codePointAt(0);
        if (isCJK(firstCodePoint)) {
            // Defensive: if string shorter than 2 codepoints, fallback to 1
            int count = Math.min(2, t.codePointCount(0, t.length()));
            return new String(t.codePoints().limit(count).toArray(), 0, count);
        }

        // Otherwise, use first letter of first two words
        String[] w = t.split("\\s+");
        String a = safeFirstLetter(w[0]);
        String b = (w.length > 1) ? safeFirstLetter(w[1]) : "";
        String res = a + b;

        return res.isEmpty() ? "?" : res;
    }

    private static String safeFirstLetter(String s) {
        if (s.isEmpty())
            return "";
        int cp = s.codePointAt(0);
        return Character.isLetterOrDigit(cp) ? new String(Character.toChars(cp)).toUpperCase() : "";
    }

    private static boolean isCJK(int codePoint) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES;
    }

    public static int getColorFromTitle(String title) {
        int h = (title == null ? 0 : title.hashCode());
        float hue = (h % 360 + 360) % 360;
        // Pastel-ish: low saturation, high value
        return Color.HSVToColor(new float[] { hue, 0.35f, 0.92f });
    }

    public static String saveGeneratedInitialsCover(Context context, int folderId, Bitmap bmp) throws IOException {
        // Reuse your JPEG + size cap pipeline
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 92, out);
        byte[] bytes = out.toByteArray();
        // non-cached destination, consistent with saved book images
        String fileName = IMAGE_PREFIX_FOR_SAVED_BOOK + folderId + ".jpg";
        return compressAndSaveImage(context, bytes, fileName, false);
    }

    public static String buildManualFolderImageFileName(String title, String futureFolderPath) {
        String key = (title == null ? "" : title.trim()) + "|"
                + (futureFolderPath == null ? "" : futureFolderPath.trim());
        String hash = md5Hex(key);
        return IMAGE_PREFIX_FOR_SAVED_BOOK + "manual_" + hash + ".jpg";
    }

    private static String md5Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] b = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(b.length * 2);
            for (byte x : b)
                sb.append(String.format(Locale.US, "%02x", x));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback: simple hash if MD5 not available (very unlikely)
            return Integer.toHexString(s.hashCode());
        }
    }

    public static String saveGeneratedInitialsCoverVersioned(
            Context context, long folderId,
            String initials, int color, boolean rounded, Bitmap bmp) throws IOException {

        // Build a short, stable suffix for current settings
        String signature = initials + "|" + color + "|" + (rounded ? 1 : 0);
        String hash = shortHash(signature); // 6–8 hex chars is enough

        String fileName = IMAGE_PREFIX_FOR_SAVED_BOOK + folderId + "_" + hash + ".jpg";

        // Encode once (same as your existing saver)
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 92, out);
        byte[] bytes = out.toByteArray();

        String absPath = compressAndSaveImage(context, bytes, fileName, /* isCached= */false);

        // Delete older versions for this folder to avoid accumulation
        File dir = StorageHelper.getImageFolder(context, false);
        File[] old = dir.listFiles((d, name) -> name.startsWith(IMAGE_PREFIX_FOR_SAVED_BOOK + folderId + "_")
                && !name.equals(fileName));
        if (old != null) {
            for (File o : old) {
                try {
                    /* ignore result */ o.delete();
                } catch (Throwable ignored) {
                }
            }
        }

        return absPath;
    }

    private static String shortHash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] b = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            // 8 hex chars is plenty
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 4; i++)
                sb.append(String.format(Locale.US, "%02x", b[i]));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    /**
     * Create fallback cover for manual folder BEFORE insert, returns absolute path
     */
    public static @Nullable String createFallbackManualFolderImagePreInsert(Context ctx, String title,
            String futureFolderPath, int sizePx) {
        String fileName = buildManualFolderImageFileName(title, futureFolderPath);
        return createAndSaveFallbackImage(ctx, fileName, title, sizePx); // uses the helper we added earlier
    }

    private static @Nullable String moveCachedImageToPermanent(Context context, String currentAbsPath) {
        if (currentAbsPath == null || currentAbsPath.isEmpty())
            return null;

        // Only handle plain file paths (skip content:// or file://)
        if (currentAbsPath.startsWith("content://") || currentAbsPath.startsWith("file://"))
            return null;

        File cachedDir = StorageHelper.getImageFolder(context, /* isCached= */true);
        File imagesDir = StorageHelper.getImageFolder(context, /* isCached= */false);

        // Robust check: path starts with cached dir OR contains "/cached_images/"
        String cachedDirPath = cachedDir.getAbsolutePath();
        boolean isInCached = currentAbsPath.startsWith(cachedDirPath)
                || currentAbsPath.contains(File.separator + Var.FOLDER_CACHED_IMAGE + File.separator);
        if (!isInCached)
            return null;

        File src = new File(currentAbsPath);
        if (!src.exists()) {
            // check if it was not already in the permanent folder, but DB was not updated
            File dst = new File(imagesDir, src.getName());
            if (dst.exists()) {
                myLogW("image was already moved but DB was not updated: " + dst.getAbsolutePath());
                return dst.getAbsolutePath();
            } else {
                myLogE("moveCachedImageToPermanent: source not found: " + currentAbsPath);
                return null;
            }
        }

        if (!imagesDir.exists() && !imagesDir.mkdirs()) {
            myLogE("moveCachedImageToPermanent: could not create images dir: " + imagesDir.getAbsolutePath());
            return null;
        }

        File dst = new File(imagesDir, src.getName());
        if (dst.equals(src))
            return src.getAbsolutePath(); // already correct

        // If target exists, delete it to allow rename
        if (dst.exists() && !dst.delete()) {
            myLogE("moveCachedImageToPermanent: target exists and cannot delete: " + dst.getAbsolutePath());
            return null;
        }

        boolean renamed = src.renameTo(dst);
        if (!renamed) {
            // Fallback: copy -> delete
            myLogD("renameTo failed, will copy: " + src.getAbsolutePath() + " -> " + dst.getAbsolutePath());
            try (java.io.FileInputStream in = new java.io.FileInputStream(src);
                    java.io.FileOutputStream out = new java.io.FileOutputStream(dst)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1)
                    out.write(buf, 0, n);
            } catch (IOException io) {
                myLogEE(io, "moveCachedImageToPermanent: copy failed");
                // Clean up partial file
                try {
                    if (dst.exists())
                        dst.delete();
                } catch (Throwable ignore) {
                }
                return null;
            }
            // try to delete src; if it fails, we still proceed (we “copied” instead of
            // move)
            try {
                if (!src.delete())
                    myLogD("moveCachedImageToPermanent: could not delete source (copied): " + src.getAbsolutePath());
            } catch (Throwable ignore) {
            }
        }

        myLog("Moved cached image to permanent: " + dst.getAbsolutePath());
        return dst.getAbsolutePath();
    }

    private static boolean isLikelyImage(byte[] bytes) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            return o.outWidth > 0 && o.outHeight > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String downloadRemoteToBookCoverVersioned(Context context, long folderId, String imageUrl) {
        try {
            byte[] imageBytes = NetworkHelper.fetchBytesWithHttpsFallbackForImage(imageUrl);
            if (imageBytes == null || !isLikelyImage(imageBytes))
                return null;

            String hash = shortHash(imageBytes); // first 8 hex chars of MD5, for example
            String fileName = IMAGE_PREFIX_FOR_SAVED_BOOK + folderId + "_" + hash + ".jpg";

            String abs = compressAndSaveImage(context, imageBytes, fileName, /* isCached= */false);

            // delete older versions
            File dir = StorageHelper.getImageFolder(context, false);
            File[] old = dir.listFiles((d, name) -> name.startsWith(IMAGE_PREFIX_FOR_SAVED_BOOK + folderId + "_")
                    && !name.equals(fileName));
            if (old != null)
                for (File o : old)
                    try {
                        o.delete();
                    } catch (Throwable ignore) {
                    }

            return abs;
        } catch (Throwable t) {
            myLogEE(t, "downloadRemoteToBookCoverVersioned");
            return null;
        }
    }

    public static String shortHash(byte[] data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] b = md.digest(data);
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 4; i++)
                sb.append(String.format(java.util.Locale.US, "%02x", b[i]));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(java.util.Arrays.hashCode(data));
        }
    }

    public static String saveUserSelectedImageToBookCoverVersioned(Context context, long folderId, String uriOrPath) {
        try (InputStream in = context.getContentResolver().openInputStream(Uri.parse(uriOrPath))) {
            if (in == null)
                return null;
            // read all bytes
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1)
                out.write(buf, 0, n);
            byte[] imageBytes = out.toByteArray();

            if (!isLikelyImage(imageBytes))
                return null;

            // build short hash from content
            String hash = shortHash(imageBytes);
            String fileName = IMAGE_PREFIX_FOR_SAVED_BOOK + folderId + "_" + hash + ".jpg";

            String abs = compressAndSaveImage(context, imageBytes, fileName, /* isCached= */false);

            // remove older versions for this folder
            File dir = StorageHelper.getImageFolder(context, false);
            File[] old = dir.listFiles((d, name) -> name.startsWith(IMAGE_PREFIX_FOR_SAVED_BOOK + folderId + "_")
                    && !name.equals(fileName));
            if (old != null)
                for (File o : old)
                    try {
                        o.delete();
                    } catch (Throwable ignored) {
                    }

            return abs;
        } catch (Throwable t) {
            myLogEE(t, "saveUserSelectedImageToBookCoverVersioned");
            return null;
        }
    }

    @Nullable
    public static android.graphics.Bitmap decodeBitmapFromStringUri(Context context, String uriString, int maxSidePx) {
        // myLog("decodeBitmapFromStringUri : " + uriString + " - " + maxSidePx);
        if (uriString == null)
            return null;
        try {
            android.net.Uri uri = android.net.Uri.parse(uriString);

            // File path support (if your DB sometimes stores plain paths)
            // myLog("decodeBitmapFromStringUri by file");
            if ("file".equalsIgnoreCase(uri.getScheme()) || uriString.startsWith("/")) {
                String path = "file".equalsIgnoreCase(uri.getScheme()) ? uri.getPath() : uriString;
                if (path == null)
                    return null;
                android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                android.graphics.BitmapFactory.decodeFile(path, o);
                int sample = 1;
                while (Math.max(o.outWidth / sample, o.outHeight / sample) > maxSidePx)
                    sample *= 2;
                android.graphics.BitmapFactory.Options o2 = new android.graphics.BitmapFactory.Options();
                o2.inSampleSize = sample;
                o2.inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888;
                return android.graphics.BitmapFactory.decodeFile(path, o2);
            }

            // Content:// (SAF) — decode via stream (we are the same app → we can read it)
            try (java.io.InputStream is = context.getContentResolver().openInputStream(uri)) {
                myLogW("decodeBitmapFromStringUri by stream");
                if (is == null)
                    return null;
                byte[] all = readAll(is);
                android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                android.graphics.BitmapFactory.decodeByteArray(all, 0, all.length, o);
                int sample = 1;
                while (Math.max(o.outWidth / sample, o.outHeight / sample) > maxSidePx)
                    sample *= 2;
                android.graphics.BitmapFactory.Options o2 = new android.graphics.BitmapFactory.Options();
                o2.inSampleSize = sample;
                o2.inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888;
                return android.graphics.BitmapFactory.decodeByteArray(all, 0, all.length, o2);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static byte[] readAll(java.io.InputStream is) throws java.io.IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int r;
        while ((r = is.read(buf)) != -1)
            bos.write(buf, 0, r);
        return bos.toByteArray();
    }

    /**
     * Checks if a cover image is stored on SD card and copies/moves it to local
     * storage
     * to improve app loading performance.
     *
     * @param context          The application context
     * @param currentImagePath The current image path from the database
     * @param bookId           The book/folder ID to use for naming the local cover
     *                         file
     * @return The new local path if copied/moved, or the original path if already
     *         local, null on failure
     */
    public static String checkAndCopySdCardCoverToLocal(Context context, String currentImagePath, long bookId) {
        if (currentImagePath == null || currentImagePath.isEmpty()) {
            return null;
        }

        // Skip if already a local path (relative path in our files dir)
        if (!currentImagePath.startsWith("/storage/") &&
                !currentImagePath.startsWith("/sdcard/") &&
                !currentImagePath.startsWith("content://")) {
            // Already local relative path
            return currentImagePath;
        }

        try {
            File sourceFile = null;
            File destFile = new File(StorageHelper.getImageFolder(context, false),
                    IMAGE_PREFIX_FOR_SAVED_BOOK + bookId + ".jpg");
            boolean shouldMove = false; // move if in our app folder, copy otherwise

            // Handle content:// URIs
            if (currentImagePath.startsWith("content://")) {
                // For content URIs, we'll copy (can't move)
                return copyContentUriToImageFile(context, currentImagePath, destFile.getName(), false);
            }

            // Handle absolute file paths on SD card
            sourceFile = new File(currentImagePath);
            if (!sourceFile.exists() || !sourceFile.canRead()) {
                myLogW("SD card cover not accessible: " + currentImagePath);
                return null;
            }

            // Check if it's in our app's reserved SD card folder
            File[] externalFilesDirs = context.getExternalFilesDirs(null);
            for (File appDir : externalFilesDirs) {
                if (appDir != null && currentImagePath.startsWith(appDir.getAbsolutePath())) {
                    shouldMove = true;
                    break;
                }
            }

            // Create parent directories if needed
            File parentDir = destFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // Copy the file
            try (FileInputStream fis = new FileInputStream(sourceFile);
                    FileOutputStream fos = new FileOutputStream(destFile)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                fos.flush();
            }

            // If we should move (app folder), delete the source
            shouldMove = false; // TODO, to check, maybe ok, but afraid to break export or something else...
            if (shouldMove) {
                try {
                    if (sourceFile.delete()) {
                        myLog("cover on sd_card MOVED to local: " + destFile.getPath());
                    } else {
                        myLogW("Could not delete source file after copy: " + currentImagePath);
                    }
                } catch (Exception e) {
                    myLogEE(e, "Error deleting source file after move");
                }
            } else {
                myLog("cover on sd_card COPIED to local: " + destFile.getPath());
            }

            return destFile.getPath();

        } catch (Exception e) {
            myLogEE(e, "checkAndCopySdCardCoverToLocal failed for: " + currentImagePath);
        }

        return null;
    }

}
