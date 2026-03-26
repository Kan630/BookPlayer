package com.driot.bookplayer.helpers;

import static com.driot.bookplayer.global.Var.MAX_IMAGE_SIZE_KB;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import android.os.Looper;
import android.os.Handler;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.BookSource;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.RadioStation;
import com.driot.bookplayer.db.RadioStationDao;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.podcasts.PodcastHelper;
import com.driot.bookplayer.radio.RadioHelper;
import com.driot.bookplayer.utils.Tonio;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class ImageHelper {

    public static final int MAX_IMAGE_WIDTH = 1280;
    public static final int MAX_IMAGE_HEIGHT = 1280;

    public static final String IMAGE_PREFIX_FOR_PODCAST_COVERS = "podcast_feed_";
    public static final String IMAGE_PREFIX_FOR_RADIO_COVERS = "radio_station_";
    public static final String IMAGE_PREFIX_FOR_LIBRIVOX_COVERS = "librivox_img_";
    public static final String IMAGE_PREFIX_FOR_SAVED_BOOK = "folder_id_";
    public static final String IMAGE_PREFIX_FOR_SAVED_COPY_OF_ORIGINAL_COVER = "saved_";
    public static final String IMAGE_PREFIX_FOR_TEMP_FILE = "tmp_img";

    public static final boolean VERBOSE_DEBUG = false;
    private static void myLogDD(String txt) { if (VERBOSE_DEBUG) myLogD(txt); }

    // TODO ASYNC...
    public static String downloadAndMaybeCompressImage(Context context, String imageUrl, String imagePath,
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
            myLogE("downloadAndMaybeCompressImage() failed for: " + imageUrl);
            return null;
        }
    }

    /**
     * Common function to download, compress, and verify an image.
     * Returns absolute path if successful (non-empty file exists), null otherwise.
     */
    public static @Nullable String downloadAndVerifyImage(Context context, String imageUrl, String imageFileName,
            boolean isCached) {
        String absPath = downloadAndMaybeCompressImage(context, imageUrl, imageFileName, isCached);
        if (absPath == null)
            return null;

        File f = new File(absPath);
        if (f.exists() && f.length() > 0L) {
            return absPath;
        } else {
            // Clean up potentially corrupted/empty file
            if (f.exists()) {
                try {
                    f.delete();
                } catch (Exception ignored) {
                }
            }
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

    public static void processPendingImages(Context context, long currentTime, String from) {
         myLogD("processPendingImages - from " + from);
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
            PodcastHelper.handlePodcastImages(context, currentTime);

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
            List<Folder> pendingFolders = db.folderDao()
                    .getAllWithExternalImagesUnchangedSince24h(currentTime);
            for (Folder folder : pendingFolders) {
                String url = folder.image;
                if (url == null)
                    continue;

                myLog("caching folder image for: " + folder.getName());
                String localPath = null;
                String imagePath = IMAGE_PREFIX_FOR_SAVED_BOOK + folder.getId() + ".jpg";

                if (url.startsWith("http")) {
                    localPath = downloadAndVerifyImage(context, url, imagePath, false);
                } else if (isContentUri(url)) {
                    localPath = copyContentUriToImageFile(context, url, imagePath, false);
                }

                if (localPath != null) {
                    folder.image = localPath;
                }
                folder.date_maj = System.currentTimeMillis();
                db.folderDao().update(folder);
            }

            // --- Handle Radio images ---
            RadioHelper.handleRadioImages(context, currentTime);

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

    public static void deleteAllTempImages(Context context) {
        File imageDir = StorageHelper.getImageFolder(context, true);
        File[] tempFiles = imageDir.listFiles((dir, name) -> name.startsWith(IMAGE_PREFIX_FOR_TEMP_FILE));

        if (tempFiles != null) {
            for (File file : tempFiles) {
                try {
                    if (file.delete()) {
                        myLog("Deleted temp image: " + file.getName());
                    }
                } catch (Exception e) {
                    myLogE("Failed to delete temp image: " + file.getName());
                }
            }
        }
    }

    public static void finalizeTempFolderImage(Context context, long folderId) {
        finalizeTempFolderImage(context, folderId, "");
    }

    private static File getOriginalImageSavedCopy(Context context, long folderId) {
        return new File(StorageHelper.getImageFolder(context, false), IMAGE_PREFIX_FOR_SAVED_COPY_OF_ORIGINAL_COVER + folderId + ".jpg");
    }

    public static void finalizeTempFolderImage(Context context, long folderId, String suffix) {
        String safeSuffix = (suffix == null) ? "" : suffix;
        File tmpFile = new File(StorageHelper.getImageFolder(context, true),
                IMAGE_PREFIX_FOR_TEMP_FILE + safeSuffix + ".jpg");
        File newFile = new File(StorageHelper.getImageFolder(context, false),
                IMAGE_PREFIX_FOR_SAVED_BOOK + folderId + ".jpg");

        if (!tmpFile.exists()) {
            myLogD("Temp image not found: " + tmpFile.getAbsolutePath());
            return;
        }

        // First, save a copy as "original" (saved_XX.jpg)
        File originalSavedCopyFile = getOriginalImageSavedCopy(context, folderId);
        try {
            copyFile(tmpFile, originalSavedCopyFile);
            myLog("Original cover preserved at: " + originalSavedCopyFile.getAbsolutePath());
        } catch (IOException e) {
            myLogE("Failed to preserve original cover: " + e.getMessage());
        }

        // Then, renamed the tmp file to a proper name
        boolean renamed = tmpFile.renameTo(newFile);
        if (!renamed) {
            myLogE("Failed to rename temp image to: " + newFile.getAbsolutePath());
            return;
        }
        myLog("Temp image renamed to: " + newFile.getAbsolutePath());

        // And persist its new name in DB
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

            // Use PNG to preserve transparency in rounded corners
            // This is the same approach as saveGeneratedInitialsCoverVersioned
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            bmp.recycle();
            byte[] bytes = out.toByteArray();

            // If small enough, save directly as PNG (bypass JPEG compression)
            // Generated covers are typically small (vector-like), so this should work
            if (bytes.length / 1024 <= MAX_IMAGE_SIZE_KB) {
                return saveBytesToFile(context, bytes, fileName, false);
            } else {
                // If somehow too large, we still need to save it
                // Log warning but save anyway - better to have a large PNG than broken JPEG
                myLogW("Fallback cover is large (" + bytes.length / 1024
                        + "KB), saving anyway to preserve transparency");
                return saveBytesToFile(context, bytes, fileName, false);
            }
        } catch (Exception e) {
            myLogEE(e, "createAndSaveFallbackImage failed");
            return null;
        }
    }

    /** Build a bitmap with pastel background + centered initials. */
    public static Bitmap createInitialsBitmap(String title, int sizePx, boolean rounded) {
        String initials = getInitials(title);
        int bg = getColorFromTitle(title);
        return createInitialsBitmapCustom(initials, bg, sizePx, rounded, 16);
    }

    /** Same rendering but with explicit initials & color for the generator UI. */
    public static Bitmap createInitialsBitmapCustom(String initials, int bgColor, int sizePx, boolean rounded,
            int textSizeVal) {
        if (initials == null)
            initials = "";
        initials = initials.trim();
        // REMOVED 5 chars limit

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

        // TEXT SIZE logic
        // base logic was: sizePx * (initials.length() <= 1 ? 0.55f : 0.42f)
        // New logic: map textSizeVal (8..30) to a scale factor.
        // Let's say 16 is "standard" -> 0.45f
        // 8 -> 0.20f
        // 30 -> 0.80f
        // Linear mapping:
        // min=8, max=30, range=22.
        // scaleMin=0.20f, scaleMax=0.80f, range=0.60f.
        // factor = 0.20f + ((val - 8) / 22.0f) * 0.60f
        float factor = 0.20f + ((textSizeVal - 8) / 22.0f) * 0.60f;
        p.setTextSize(sizePx * factor);

        // MULTI-LINE LOGIC
        // We respect the actual newlines in 'initials' first.
        // If 'nbLines' implies we should force split, we could, but better to trust the
        // input string now that it is multi-line.
        // "I mean how many line user can write" -> handled by UI.

        String[] lines = initials.split("\n");
        // Center the block of lines vertically
        Paint.FontMetrics fm = p.getFontMetrics();
        float lineHeight = fm.descent - fm.ascent;
        float totalHeight = lines.length * lineHeight;

        float x = sizePx / 2f;

        // Start Y: center - half total height + ascent correction for first line?
        // Usually drawText y is baseline.
        // Top of block is (sizePx - totalHeight) / 2
        // First baseline = Top + (-fm.ascent)

        float startY = (sizePx - totalHeight) / 2f + (-fm.ascent);

        for (int i = 0; i < lines.length; i++) {
            c.drawText(lines[i], x, startY + (i * lineHeight), p);
        }

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

    public static String saveGeneratedInitialsCover(Context context, long folderId, Bitmap bmp) throws IOException {
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
            String initials, int color, boolean rounded, int textSize, Bitmap bmp) throws IOException {

        // Build a short, stable suffix for current settings
        String signature = initials + "|" + color + "|" + (rounded ? 1 : 0) + "|" + textSize;
        String hash = shortHash(signature); // 6–8 hex chars is enough

        String fileName = IMAGE_PREFIX_FOR_SAVED_BOOK + folderId + "_" + hash + ".png";

        // Encode as PNG to preserve transparency
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        byte[] bytes = out.toByteArray();

        String absPath;
        // If small enough, save directly as PNG logic (bypass JPEG compression)
        if (bytes.length / 1024 <= MAX_IMAGE_SIZE_KB) {
            absPath = saveBytesToFile(context, bytes, fileName, /* isCached= */false);
        } else {
            // Fallback to compression if too huge (should be rare for vector-like covers)
            absPath = compressAndSaveImage(context, bytes, fileName, /* isCached= */false);
        }

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

    // ===== Original Cover Preservation Helpers =====

    /**
     * Gets the path to the original cover for a folder.
     * for "reset to original" button
     *
     * @param context  Android context
     * @param folderId Database ID of the folder
     * @return Absolute path to original cover, or null if not found
     */
    @Nullable
    public static String getOriginalCoverPath(Context context, long folderId) {
        // 1) Check for preserved original image (saved_XX.jpg)
        File dir = StorageHelper.getImageFolder(context, false);
        File originalSavedCopyFile = getOriginalImageSavedCopy(context, folderId);
        if (originalSavedCopyFile.exists() && originalSavedCopyFile.isFile()) {
            return originalSavedCopyFile.getAbsolutePath();
        }

        // --- Backwards compatibility / Legacy paths ---
        // Try Podcast first
        String podcastPath = PodcastHelper.getPodcastOriginalCoverPath(context, folderId);
        if (podcastPath != null) {
            return podcastPath;
        }

        AppDatabase db = AppDatabase.getDatabase(context.getApplicationContext());
        Folder folder = db.folderDao().getById(folderId);
        if (folder == null)
            return null;

        // Try Librivox legacy
        if (Var.SOURCE_LOCATION_LIBRIVOX.equals(folder.getSourceLocation())) {
            String identifier = Tonio.getFileNameFromPath(folder.getPath());
            File cachedDir = StorageHelper.getImageFolder(context, true);
            File librivoxFile = new File(cachedDir, IMAGE_PREFIX_FOR_LIBRIVOX_COVERS + identifier + ".jpg");
            if (librivoxFile.exists()) {
                return librivoxFile.getAbsolutePath();
            }
        }

        // Check for PNG first (preferred format for transparency)
        File pngFile = new File(dir, IMAGE_PREFIX_FOR_SAVED_BOOK + folderId + ".png");
        if (pngFile.exists()) {
            return pngFile.getAbsolutePath();
        }

        // Fallback to JPG
        File jpgFile = new File(dir, IMAGE_PREFIX_FOR_SAVED_BOOK + folderId + ".jpg");
        if (jpgFile.exists()) {
            return jpgFile.getAbsolutePath();
        }

        // artillerie lourde
        try {
            myLog("trying CoverPictureDetection");
            Uri uri = Uri.parse(folder.getUri());
            if (uri == null) {
                myLog("Cannot parse Uri");
            }
            DocumentFile docFolder = UriHelper.getDocumentFileFromAnyUri(context, uri);
            if (docFolder == null || !docFolder.exists()) {
                myToast("Cannot access folder to detect cover");
            }

            // Step 1: Try to detect cover from folder images
            CoverPictureDetection.CoverDetectionResult result = CoverPictureDetection.detectCoverFromFolder(context,
                    docFolder, null);
            if (result!=null && result.imagePath != null) {
                return result.imagePath;
            }
        } catch (Exception e) {
            myLogEE(e, "CoverPictureDetection - getOriginalCoverPath");
        }

        // --- Step 2: External Recovery (Re-download) ---
        String externalUrl = null;

        // --- 2a: Check BookSource first ---
        try {
            BookSource bs = db.bookSourceDao().getByFolderId(folderId);
            if (bs != null && bs.imageRemote != null && !bs.imageRemote.isEmpty()) {
                externalUrl = bs.imageRemote;
                myLog("Found remote cover URL in BookSource: " + externalUrl);
            }
        } catch (Exception e) {
            myLogEE(e, "Error checking BookSource for cover");
        }

        // --- 2b: Fallback to specialized logic if no BookSource URL found ---
        if (externalUrl == null) {
            if (Var.SOURCE_LOCATION_PODCAST.equals(folder.getSourceLocation())) {
                externalUrl = PodcastHelper.getPodcastOriginalCoverUrl(context, folderId);
            } else if (Var.SOURCE_LOCATION_LIBRIVOX.equals(folder.getSourceLocation())) {
                String identifier = Tonio.getFileNameFromPath(folder.getPath());
                if (identifier != null && !identifier.isEmpty()) {
                    externalUrl = "https://archive.org/services/img/" + identifier;
                }
            } else if (Var.SOURCE_LOCATION_EBOOK_GUTENDEX.equals(folder.getSourceLocation())) {
                String identifier = Tonio.getFileNameFromPath(folder.getPath());
                if (identifier != null && identifier.startsWith("gutendex_")) {
                    String id = identifier.substring("gutendex_".length());
                    externalUrl = "https://www.gutenberg.org/cache/epub/" + id + "/pg" + id + ".cover.medium.jpg";
                }
            }
        }

        if (externalUrl != null) {
            String fileName = IMAGE_PREFIX_FOR_SAVED_COPY_OF_ORIGINAL_COVER + folderId + ".jpg";
            myLog("Attempting to redownload missing original cover: " + externalUrl);
            if (NetworkHelper.isConnected(context)) {
                return downloadAndVerifyImage(context, externalUrl, fileName, false);
            } else {
                myToast("no internet connection");
            }
        }

        return null;
    }

    /**
     * Saves a modified cover (user changed via UI) with a versioned filename.
     * Preserves the original cover file (folder_id_{id}.png/jpg).
     * Creates new file: folder_id_{id}_{timestamp}.png
     * 
     * @param context  Android context
     * @param folderId Database ID of the folder
     * @param bitmap   Cover bitmap to save
     * @return Absolute path to saved modified cover, or null on error
     */
    @Nullable
    public static String saveModifiedCover(Context context, long folderId, Bitmap bitmap) {
        try {
            // Generate versioned filename with timestamp
            long timestamp = System.currentTimeMillis();
            String fileName = IMAGE_PREFIX_FOR_SAVED_BOOK + folderId + "_" + timestamp + ".png";

            // Encode as PNG to preserve quality/transparency
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            byte[] bytes = out.toByteArray();

            String absPath;
            if (bytes.length / 1024 <= MAX_IMAGE_SIZE_KB) {
                absPath = saveBytesToFile(context, bytes, fileName, false);
            } else {
                // Compress if too large
                absPath = compressAndSaveImage(context, bytes, fileName, false);
            }

            // Clean up old modified covers (but NOT the original)
            cleanupOldModifiedCovers(context, folderId, fileName);

            return absPath;
        } catch (Exception e) {
            myLogEE(e, "saveModifiedCover failed");
            return null;
        }
    }

    /**
     * Deletes old modified cover files for a folder, preserving the original.
     * Original format: folder_id_{id}.png or folder_id_{id}.jpg
     * Modified format: folder_id_{id}_{hash/timestamp}.png
     * 
     * @param context         Android context
     * @param folderId        Database ID of the folder
     * @param currentFileName Current modified cover filename to keep
     */
    private static void cleanupOldModifiedCovers(Context context, long folderId, String currentFileName) {
        try {
            File dir = StorageHelper.getImageFolder(context, false);
            String prefix = IMAGE_PREFIX_FOR_SAVED_BOOK + folderId + "_";

            File[] oldFiles = dir.listFiles((d, name) -> {
                // Match files like: folder_id_{id}_{something}.png/jpg
                // But NOT: folder_id_{id}.png or folder_id_{id}.jpg (original)
                if (!name.startsWith(prefix)) {
                    return false;
                }

                // Skip the current file
                if (name.equals(currentFileName)) {
                    return false;
                }

                // Extract the part after prefix
                String suffix = name.substring(prefix.length());

                // If suffix is just ".png" or ".jpg", it's the original - don't delete
                if (suffix.equals(".png") || suffix.equals(".jpg")) {
                    return false;
                }

                // Otherwise it's a modified version - mark for deletion
                return true;
            });

            if (oldFiles != null) {
                for (File old : oldFiles) {
                    try {
                        if (old.delete()) {
                            myLogD("Deleted old modified cover: " + old.getName());
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Exception e) {
            myLogEE(e, "cleanupOldModifiedCovers failed");
        }
    }

    private static void copyFile(File source, File dest) throws IOException {
        try (java.io.FileInputStream is = new java.io.FileInputStream(source);
                java.io.FileOutputStream os = new java.io.FileOutputStream(dest)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        }
    }

    public static void loadRadioFavicon(RadioStation s, ImageView favicon, int replacementResource,
                                        Map<String, String> faviconCache) {
        favicon.setTag(s.stationuuid);

        // If we already resolved a URL for this station, load it directly
        if (faviconCache.containsKey(s.stationuuid)) {
            String cachedUrl = faviconCache.get(s.stationuuid);
            if (cachedUrl != null) {
                Glide.with(favicon)
                        .load(cachedUrl)
                        .placeholder(getDefaultFaviconDrawable(favicon.getContext()))
                        .error(getErrorDrawable(favicon.getContext(), replacementResource))
                        .into(favicon);
            } else {
                Glide.with(favicon).clear(favicon);
                favicon.setImageDrawable(getErrorDrawable(favicon.getContext(), replacementResource));
            }
            return;
        }

        // Not yet resolved — clear immediately to avoid showing stale image from recycled view
        Glide.with(favicon).clear(favicon);
        favicon.setImageDrawable(getErrorDrawable(favicon.getContext(), replacementResource));

        if (TextUtils.isEmpty(s.favicon)) {
            if (!TextUtils.isEmpty(s.homepage)) {
                fetchOgImage(s.homepage, Var.RADIO_OG_IMAGE_TIMEOUT_MS, url -> {
                    if (!favicon.getTag().equals(s.stationuuid)) return;
                    if (url != null) {
                        myLogDD("[" + s.name + "] => no favicon, using og image");
                        faviconCache.put(s.stationuuid, url);
                        Glide.with(favicon).load(url)
                                .placeholder(getDefaultFaviconDrawable(favicon.getContext()))
                                .error(getErrorDrawable(favicon.getContext(), replacementResource))
                                .into(favicon);
                    } else {
                        myLogDD("[" + s.name + "] => no favicon, falling back to google favicon");
                        String googleUrl = "https://www.google.com/s2/favicons?sz=256&domain=" + s.homepage;
                        faviconCache.put(s.stationuuid, googleUrl);
                        Glide.with(favicon).load(googleUrl)
                                .placeholder(getDefaultFaviconDrawable(favicon.getContext()))
                                .error(getErrorDrawable(favicon.getContext(), replacementResource))
                                .into(favicon);
                    }
                });
            } else {
                myLogDD("[" + s.name + "] => no favicon, no homepage, trying DuckDuckGo fallback");
                fetchFallbackBySearch(s.name, s.country, url -> {
                    if (!favicon.getTag().equals(s.stationuuid)) return;
                    if (url != null) {
                        myLogDD("[" + s.name + "] => DuckDuckGo fallback found");
                        faviconCache.put(s.stationuuid, url);
                        Glide.with(favicon).load(url)
                                .placeholder(getDefaultFaviconDrawable(favicon.getContext()))
                                .error(getErrorDrawable(favicon.getContext(), replacementResource))
                                .into(favicon);
                    } else {
                        myLogDD("[" + s.name + "] => DuckDuckGo failed, trying iTunes fallback");
                        fetchFallbackImage(s.name, s.country, url2 -> {
                            if (!favicon.getTag().equals(s.stationuuid)) return;
                            if (url2 != null) {
                                myLogDD("[" + s.name + "] => iTunes fallback found");
                                faviconCache.put(s.stationuuid, url2);
                                Glide.with(favicon).load(url2)
                                        .placeholder(getDefaultFaviconDrawable(favicon.getContext()))
                                        .error(getErrorDrawable(favicon.getContext(), replacementResource))
                                        .into(favicon);
                            } else {
                                myLogDD("[" + s.name + "] => no image found at all, using default");
                                faviconCache.put(s.stationuuid, null);
                            }
                        });
                    }
                });
            }
        } else {
            Glide.with(favicon)
                    .load(s.favicon)
                    .placeholder(getDefaultFaviconDrawable(favicon.getContext()))
                    .error(getErrorDrawable(favicon.getContext(), replacementResource))
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                    Target<Drawable> target, boolean isFirstResource) {
                            if (!TextUtils.isEmpty(s.homepage)) {
                                fetchOgImage(s.homepage, Var.RADIO_OG_IMAGE_TIMEOUT_MS, url -> {
                                    if (!favicon.getTag().equals(s.stationuuid)) return;
                                    String loadUrl = url != null ? url
                                            : "https://www.google.com/s2/favicons?sz=256&domain=" + s.homepage;
                                    faviconCache.put(s.stationuuid, loadUrl);
                                    Glide.with(favicon).load(loadUrl)
                                            .placeholder(getDefaultFaviconDrawable(favicon.getContext()))
                                            .error(getErrorDrawable(favicon.getContext(), replacementResource))
                                            .into(favicon);
                                });
                            } else {
                                faviconCache.put(s.stationuuid, null);
                            }
                            return true;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model,
                                                       Target<Drawable> target, DataSource dataSource,
                                                       boolean isFirstResource) {
                            faviconCache.put(s.stationuuid, s.favicon);
                            return false;
                        }
                    })
                    .into(favicon);
        }
    }
    //RADIO favicon fetcher
    private static void fetchOgImage(String homeUrl, int timeout_ms, Consumer<String> callback) {
        new Thread(() -> {
            try {
                org.jsoup.nodes.Document doc = org.jsoup.Jsoup.connect(homeUrl).timeout(timeout_ms).get();
                String img = doc.select("meta[property=og:image]").attr("content");
                new Handler(Looper.getMainLooper()).post(() ->
                        callback.accept(img.isEmpty() ? null : img)
                );
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() ->
                        callback.accept(null)
                );
            }
        }).start();
    }
    private static Drawable getErrorDrawable(Context context, int replacementResource) {
        if (replacementResource != 0) {
            return AppCompatResources.getDrawable(context, replacementResource);
        }
        return getDefaultFaviconDrawable(context);
    }
    public static ColorDrawable getDefaultFaviconDrawable(Context context) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.windowBackground, typedValue, true);
        // Check if dark theme
        int nightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        int color = (nightMode == Configuration.UI_MODE_NIGHT_YES)
                ? Color.BLACK
                : Color.WHITE;
        return new ColorDrawable(color);
    }

    private static void fetchFallbackImage(String stationName, String country, Consumer<String> callback) {
        new Thread(() -> {
            try {
                String query = URLEncoder.encode("radio " + stationName + " " + country, "UTF-8");
                String apiUrl = "https://itunes.apple.com/search?term=" + query + "&media=music&limit=1";
                HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                if (json.getInt("resultCount") > 0) {
                    // artworkUrl100 → replace with higher res
                    String url = json.getJSONArray("results")
                            .getJSONObject(0)
                            .getString("artworkUrl100")
                            .replace("100x100", "600x600");
                    new Handler(Looper.getMainLooper()).post(() -> callback.accept(url));
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> callback.accept(null));
                }
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.accept(null));
            }
        }).start();
    }
    private static void fetchFallbackBySearch(String stationName, String country, Consumer<String> callback) {
        new Thread(() -> {
            try {
                // Strip leading "radio" prefix if present — Wikipedia titles don't have it
                String cleanName = stationName.replaceAll("(?i)^radio\\s+", "").trim();

                // Try 1: exact name only (e.g. "France Inter")
                String url1 = "https://en.wikipedia.org/w/api.php?action=query&titles="
                        + URLEncoder.encode(cleanName, "UTF-8")
                        + "&prop=pageimages&format=json&pithumbsize=300";

                String imageUrl = tryWikipediaQuery(url1);

                // Try 2: name + country if first failed (e.g. "France Musique France")
                if (imageUrl == null) {
                    String url2 = "https://en.wikipedia.org/w/api.php?action=query&titles="
                            + URLEncoder.encode(cleanName + " " + country, "UTF-8")
                            + "&prop=pageimages&format=json&pithumbsize=300";
                    imageUrl = tryWikipediaQuery(url2);
                }

                final String result = imageUrl;
                new Handler(Looper.getMainLooper()).post(() -> callback.accept(result));

            } catch (Exception e) {
                myLogDD("Wikipedia exception: " + e.getMessage());
                new Handler(Looper.getMainLooper()).post(() -> callback.accept(null));
            }
        }).start();
    }

    private static String tryWikipediaQuery(String apiUrl) {
        try {
            myLogDD("Wikipedia query: " + apiUrl);
            HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "RadioApp/1.0");
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            String raw = sb.toString();
            myLogDD("Wikipedia response: " + raw);

            JSONObject pages = new JSONObject(raw)
                    .getJSONObject("query")
                    .getJSONObject("pages");

            JSONObject page = pages.getJSONObject(pages.keys().next());

            // missing or invalid = not found
            if (page.has("missing") || page.has("invalid")) return null;

            JSONObject thumbnail = page.optJSONObject("thumbnail");
            if (thumbnail != null) {
                String imageUrl = thumbnail.optString("source", "");
                myLogDD("Wikipedia imageUrl: " + imageUrl);
                return imageUrl.isEmpty() ? null : imageUrl;
            }
        } catch (Exception e) {
            myLogDD("Wikipedia tryQuery exception: " + e.getMessage());
        }
        return null;
    }

    public static void resolveAndPersistFavicon(Context context, RadioStation s) {
        // Already has a valid remote URL → nothing to do
        if (!TextUtils.isEmpty(s.favicon) && s.favicon.startsWith("http")) return;

        // Already has a valid local file → nothing to do
        if (!TextUtils.isEmpty(s.favicon) && !s.favicon.startsWith("http")) {
            File f = new File(s.favicon);
            if (f.exists() && f.length() > 0) return;
        }

        // No favicon, or local file is missing/broken → resolve
        if (!TextUtils.isEmpty(s.homepage)) {
            fetchOgImage(s.homepage, Var.RADIO_OG_IMAGE_TIMEOUT_MS, url -> {
                if (url != null) {
                    myLogDD("[" + s.name + "] => persisting og image: " + url);
                    persistFaviconUrl(context, s, url);
                } else {
                    String googleUrl = "https://www.google.com/s2/favicons?sz=256&domain=" + s.homepage;
                    myLogDD("[" + s.name + "] => persisting google favicon: " + googleUrl);
                    persistFaviconUrl(context, s, googleUrl);
                }
            });
        } else {
            fetchFallbackBySearch(s.name, s.country, url -> {
                if (url != null) {
                    myLogDD("[" + s.name + "] => persisting Wikipedia url: " + url);
                    persistFaviconUrl(context, s, url);
                } else {
                    fetchFallbackImage(s.name, s.country, url2 -> {
                        if (url2 != null) {
                            myLogDD("[" + s.name + "] => persisting iTunes url: " + url2);
                            persistFaviconUrl(context, s, url2);
                        }
                    });
                }
            });
        }
    }

    private static void persistFaviconUrl(Context context, RadioStation s, String url) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                AppDatabase.getDatabase(context.getApplicationContext())
                        .radioStationDao()
                        .updateFavicon(s.stationuuid, url);
                s.favicon = url; // keep in-memory object in sync
            } catch (Exception e) {
                myLogW("persistFaviconUrl failed: " + e.getMessage());
            }
        });
    }

}
