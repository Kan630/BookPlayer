package com.driot.bookplayer.utils;

import static com.driot.bookplayer.global.Var.MAX_IMAGE_SIZE_KB;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.db.Folder;
import com.driot.bookplayer.db.Podcast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class ImageHelper {

    public static final int MAX_IMAGE_WIDTH = 1280;
    public static final int MAX_IMAGE_HEIGHT = 1280;

    public static final String PODCAST_IMAGE_PREFIX = "podcast_feed_";
    public static final String FOLDER_IMAGE_PREFIX = "folder_id_";
    public static final String LIBRIVOX_IMAGE_PREFIX = "librivox_img_";

    public static File getImageFile(Context context, long id, boolean isFolder) {
        File dir = StorageHelper.getImageFolder(context);
        if (!dir.exists()) dir.mkdirs();
        String prefix = isFolder ? FOLDER_IMAGE_PREFIX : PODCAST_IMAGE_PREFIX;
        return new File(dir, prefix + id + ".jpg");
    }

    private static String downloadAndMaybeCompressImage(Context context, String imageUrl, String imagePath) {
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
            return compressAndSaveImage(context, imageBytes, imagePath);

        } catch (IOException e) {
            myLogEE(e, "downloadAndMaybeCompressImage() failed for: " + imageUrl);
            return null;
        }
    }


    private static String saveBytesToFile(Context context, byte[] data, String imagePath) throws IOException {
        File dir = StorageHelper.getImageFolder(context);
        if (!dir.exists()) dir.mkdirs();
        File imageFile = new File(dir, imagePath);
        FileOutputStream fos = new FileOutputStream(imageFile);
        fos.write(data);
        fos.close();
        myLogD("image saved [" + imagePath + "] - " + (imageFile.length() / 1024) + "KB");
        return imageFile.getAbsolutePath();
    }


    public static void processPendingImages(Context context) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(context);

// --- Handle Podcast images ---
            List<Podcast> pendingPodcasts = db.PodcastDao().getAllWithRemoteImage();
            for (Podcast podcast : pendingPodcasts) {
                String url = podcast.image;
                if (url == null || !url.startsWith("http")) continue;

                String imagePath = PODCAST_IMAGE_PREFIX + podcast.feedId + ".jpg";
                String localPath = downloadAndMaybeCompressImage(context, url, imagePath);
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
                String imagePath = FOLDER_IMAGE_PREFIX + folder.getId() + ".jpg";

                if (url.startsWith("http")) {
                    localPath = downloadAndMaybeCompressImage(context, url, imagePath);
                } else if (isContentUri(url)) {
                    localPath = copyContentUriToImageFile(context, url, imagePath);
                }

                if (localPath != null) {
                    folder.image = localPath;
                    db.FolderDao().update(folder);
                }
            }
        });
    }

    public static String getOrDownloadLibrivoxImage(Context context, String identifier, String imageUrl, boolean forceDownload) {
        String imagePath = LIBRIVOX_IMAGE_PREFIX + identifier + ".jpg";
        File imageFile = new File(StorageHelper.getImageFolder(context), imagePath);

        if (imageFile.exists() && !forceDownload) {
            myLogD("Librivox image already exists: " + imageFile.getAbsolutePath());
            return imageFile.getAbsolutePath();
        }

        myLogI("Downloading Librivox image for: " + identifier);
        return downloadAndMaybeCompressImage(context, imageUrl, imagePath);
    }

    public static File getLibrivoxImageFile(Context context, String identifier) {
        File dir = StorageHelper.getImageFolder(context);
        return new File(dir, LIBRIVOX_IMAGE_PREFIX + identifier + ".jpg");
    }

    public static String copyContentUriToImageFile(Context context, String uriOrPath, String outputFileName) {
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
            return compressAndSaveImage(context, imageBytes, outputFileName);

        } catch (Exception e) {
            myLogEE(e, "copyContentUriToImageFile() failed for: " + uriOrPath);
            return null;
        }
    }



    private static boolean isContentUri(String s) {
        return s != null && s.startsWith("content://");
    }

    private static String compressAndSaveImage(Context context, byte[] imageBytes, String outputFileName) throws IOException {
        if (imageBytes.length / 1024 <= MAX_IMAGE_SIZE_KB) {
            return saveBytesToFile(context, imageBytes, outputFileName);
        }

        myLogD("Image too big (" + imageBytes.length / 1024 + "KB), compressing...");

        Bitmap originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        Bitmap resizedBitmap = resizeIfNeeded(originalBitmap);

        int quality = 75;
        byte[] compressedBytes;

        do {
            ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, compressedOut);
            compressedBytes = compressedOut.toByteArray();
            myLogD("pic compressed to " + compressedBytes.length / 1024 + "KB, quality: " + quality + "%");
            quality -= 15;
        } while (compressedBytes.length / 1024 > MAX_IMAGE_SIZE_KB && quality >= 40);

        return saveBytesToFile(context, compressedBytes, outputFileName);
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

    // ----------------------- LOG -----------------------
    private static final String TAG = "ImageHelper";
    private static void myLog(String str) { KanLogger.myLog(TAG, str); }
    private static void myLogD(String str) { KanLogger.myLogD(TAG, str); }
    private static void myLogW(String str) { KanLogger.myLogW(TAG, str); }
    private static void myLogI(String str) { KanLogger.myLogI(TAG, str); }
    private static void myLogE(String str) { KanLogger.myLogE(TAG, str); }
    private static void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, TAG, str); }
    private static void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, TAG, str); }
}
