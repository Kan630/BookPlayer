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
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;

public class ImageHelper {

    public static final int MAX_IMAGE_WIDTH = 1280;
    public static final int MAX_IMAGE_HEIGHT = 1280;

    public static final String IMAGE_PREFIX_FOR_PODCAST_COVERS = "podcast_feed_";
    public static final String IMAGE_PREFIX_FOR_LIBRIVOX_COVERS = "librivox_img_";
    public static final String IMAGE_PREFIX_FOR_SAVED_BOOK = "folder_id_";
    public static final String IMAGE_PREFIX_FOR_TEMP_FILE = "tmp_img";


    //TODO ASYNC...
    private static String downloadAndMaybeCompressImage(Context context, String imageUrl, String imagePath, boolean isCached) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            InputStream in = connection.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            in.close();

            byte[] imageBytes = out.toByteArray();
            return compressAndSaveImage(context, imageBytes, imagePath, isCached);

        } catch (Throwable t) {
            myLogEE(t, "downloadAndMaybeCompressImage() failed for: " + imageUrl);
            return null;
        }
    }


    private static String saveBytesToFile(Context context, byte[] data, String imagePath, boolean isCached) throws IOException {
        File dir = StorageHelper.getImageFolder(context, isCached);
        if (!dir.exists()) dir.mkdirs();
        File imageFile = new File(dir, imagePath);
        FileOutputStream fos = new FileOutputStream(imageFile);
        fos.write(data);
        fos.close();
        myLogD("image saved [" + imagePath + "] - " + (imageFile.length() / 1024) + "KB");
        return imageFile.getAbsolutePath();
    }

    public static void processPendingImages(Context context) {
        myLogD("processPendingImages");
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(context);

// --- Handle Podcast images ---
            List<Podcast> pendingPodcasts = db.PodcastDao().getAllWithRemoteImage();
            for (Podcast podcast : pendingPodcasts) {
                String url = podcast.image;
                if (url == null || !url.startsWith("http")) continue;

                String imagePath = IMAGE_PREFIX_FOR_PODCAST_COVERS + podcast.feedId + ".jpg";
                String localPath = downloadAndMaybeCompressImage(context, url, imagePath, true);
                if (localPath != null) {
                    podcast.image = localPath;
                    db.PodcastDao().update(podcast);
                }
            }

// --- Handle Folder images ---
            List<Folder> pendingFolders = db.FolderDao().getAllWithRemoteImage();
            for (Folder folder : pendingFolders) {
                String url = folder.image;

                if (url == null) continue;

                String localPath = null;
                String imagePath = IMAGE_PREFIX_FOR_SAVED_BOOK + folder.getId() + ".jpg";

                if (url.startsWith("http")) {
                    localPath = downloadAndMaybeCompressImage(context, url, imagePath, false);
                } else if (isContentUri(url)) {
                    localPath = copyContentUriToImageFile(context, url, imagePath, false);
                }

                if (localPath != null) {
                    folder.image = localPath;
                    db.FolderDao().update(folder);
                }
            }
        });
    }

    public static String getOrDownloadLibrivoxImage(Context context, String identifier, String imageUrl, boolean forceDownload) {
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

    public static String copyContentUriToImageFile(Context context, String uriOrPath, String outputFileName, boolean isCached) {
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
            if (in == null) return null;

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
    public static void finalizeTempFolderImage(Context context, int folderId) {
        File tmpFile = new File(StorageHelper.getImageFolder(context, true), IMAGE_PREFIX_FOR_TEMP_FILE + ".jpg");
        File newFile = new File(StorageHelper.getImageFolder(context, false), IMAGE_PREFIX_FOR_SAVED_BOOK + folderId + ".jpg");

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
            Folder folder = AppDatabase.getDatabase(context).FolderDao().getById(folderId);
            if (folder != null) {
                folder.image = newFile.getAbsolutePath();
                AppDatabase.getDatabase(context).FolderDao().update(folder);
                myLog("Folder DB updated with new image path");
            } else {
                myLogE("Folder not found for ID: " + folderId);
            }
        });
    }


    private static boolean isContentUri(String s) {
        return s != null && s.startsWith("content://");
    }

    private static String compressAndSaveImage(Context context, byte[] imageBytes, String outputFileName, boolean isCached) throws IOException {
        if (imageBytes.length / 1024 <= MAX_IMAGE_SIZE_KB) {
            return saveBytesToFile(context, imageBytes, outputFileName, isCached);
        }

        myLogD("Image too big (" + imageBytes.length / 1024 + "KB), compressing...");

        Bitmap originalBitmap;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, bounds);

            int inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT);

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

    private static int calculateInSampleSize(int width, int height, int reqWidth, int reqHeight) { //used for old device with low memory...
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
                if (file!=null && file.exists()) {
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
            Bitmap bmp = createInitialsBitmap(title, sizePx, /*rounded=*/true);
            // Encode once to JPEG, then let your existing compressor enforce MAX_IMAGE_SIZE_KB
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
    private static Bitmap createInitialsBitmap(String title, int sizePx, boolean rounded) {
        String initials = getInitials(title);
        int bg = getColorFromTitle(title);

        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(bg);

        if (rounded) {
            float r = sizePx * 0.12f; // corner radius
            c.drawRoundRect(new RectF(0, 0, sizePx, sizePx), r, r, p);
        } else {
            c.drawRect(0, 0, sizePx, sizePx, p);
        }

        // Draw initials
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextSize(sizePx * (initials.length() == 1 ? 0.55f : 0.42f));

        Paint.FontMetrics fm = p.getFontMetrics();
        float x = sizePx / 2f;
        float y = sizePx / 2f - (fm.ascent + fm.descent) / 2f;
        c.drawText(initials, x, y, p);

        return bmp;
    }

    private static String getInitials(String title) {
        if (title == null) return "?";
        String t = title.trim();
        if (t.isEmpty()) return "?";

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
        if (s.isEmpty()) return "";
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


    private static int getColorFromTitle(String title) {
        int h = (title == null ? 0 : title.hashCode());
        float hue = (h % 360 + 360) % 360;
        // Pastel-ish: low saturation, high value
        return Color.HSVToColor(new float[]{hue, 0.35f, 0.92f});
    }

    public static String buildManualFolderImageFileName(String title, String futureFolderPath) {
        String key = (title == null ? "" : title.trim()) + "|" + (futureFolderPath == null ? "" : futureFolderPath.trim());
        String hash = md5Hex(key);
        return IMAGE_PREFIX_FOR_SAVED_BOOK + "manual_" + hash + ".jpg";
    }

    private static String md5Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] b = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(b.length * 2);
            for (byte x : b) sb.append(String.format(Locale.US, "%02x", x));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback: simple hash if MD5 not available (very unlikely)
            return Integer.toHexString(s.hashCode());
        }
    }

    /** Create fallback cover for manual folder BEFORE insert, returns absolute path */
    public static @Nullable String createFallbackManualFolderImagePreInsert(Context ctx, String title, String futureFolderPath, int sizePx) {
        String fileName = buildManualFolderImageFileName(title, futureFolderPath);
        return createAndSaveFallbackImage(ctx, fileName, title, sizePx); // uses the helper we added earlier
    }

}
