package com.driot.bookplayer.utils;

import static com.driot.bookplayer.global.Var.MAX_IMAGE_SIZE_KB;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

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
    public static final String PODCAST_IMAGE_PREFIX = "podcast_feed_";
    public static final String FOLDER_IMAGE_PREFIX = "folder_id_";
    public static final String LIBRIVOX_IMAGE_PREFIX = "librivox_img_";
    public static final String IMAGE_FOLDER = "images";

    public static File getImageFile(Context context, long id, boolean isFolder) {
        File dir = new File(context.getFilesDir(), IMAGE_FOLDER);
        if (!dir.exists()) dir.mkdirs();
        String prefix = isFolder ? FOLDER_IMAGE_PREFIX : PODCAST_IMAGE_PREFIX;
        return new File(dir, prefix + id + ".jpg");
    }

    private static String downloadAndMaybeCompressImage(Context context, String imageUrl, String imagePath) {
        try {
            // Download
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.connect();
            InputStream in = connection.getInputStream();

            // Read into byte[]
            ByteArrayOutputStream originalOut = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                originalOut.write(buffer, 0, len);
            }
            in.close();
            myLogI("Saved image to: " + imagePath + " - " + (new File(new File(context.getFilesDir(), IMAGE_FOLDER), imagePath).length() / 1024) + "KB");

            byte[] originalBytes = originalOut.toByteArray();
            if (originalBytes.length / 1024 <= MAX_IMAGE_SIZE_KB) {
                return saveBytesToFile(context, originalBytes, imagePath);
            } else {
                myLogI("Image too big " + originalBytes.length / 1024  + "KB , compressing...");
                Bitmap bitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.length);
                ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, compressedOut);
                return saveBytesToFile(context, compressedOut.toByteArray(), imagePath);
            }

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }


    private static String saveBytesToFile(Context context, byte[] data, String imagePath) throws IOException {
        File dir = new File(context.getFilesDir(), IMAGE_FOLDER);
        if (!dir.exists()) dir.mkdirs();

        File imageFile = new File(dir, imagePath);
        FileOutputStream fos = new FileOutputStream(imageFile);
        fos.write(data);
        fos.close();
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
                if (url == null || !url.startsWith("http")) continue;

                // Use Folder ID in file name
                String imagePath = FOLDER_IMAGE_PREFIX + folder.getId() + ".jpg";
                String localPath = downloadAndMaybeCompressImage(context, url, imagePath);
                if (localPath != null) {
                    folder.image = localPath;
                    db.FolderDao().update(folder);
                }
            }
        });
    }

    public static String getOrDownloadLibrivoxImage(Context context, String identifier, String imageUrl, boolean forceDownload) {
        String imagePath = LIBRIVOX_IMAGE_PREFIX + identifier + ".jpg";
        File imageFile = new File(context.getFilesDir(), IMAGE_FOLDER + "/" + imagePath);

        if (imageFile.exists() && !forceDownload) {
            myLogD("Librivox image already exists: " + imageFile.getAbsolutePath());
            return imageFile.getAbsolutePath();
        }

        myLogI("Downloading Librivox image for: " + identifier);
        return downloadAndMaybeCompressImage(context, imageUrl, imagePath);
    }

    public static File getLibrivoxImageFile(Context context, String identifier) {
        File dir = new File(context.getFilesDir(), IMAGE_FOLDER);
        return new File(dir, LIBRIVOX_IMAGE_PREFIX + identifier + ".jpg");
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
