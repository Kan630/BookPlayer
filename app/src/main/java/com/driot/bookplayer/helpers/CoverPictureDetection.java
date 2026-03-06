package com.driot.bookplayer.helpers;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.driot.bookplayer.ebooks.EpubCommonHelper;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.utils.log.KanLogger;

import java.io.File;
import java.util.Map;

/**
 * Centralized helper class for cover picture detection across all import types.
 * 
 * This class consolidates cover detection logic that was previously scattered
 * across:
 * - FinalParseFolderWorker (folder image scanning)
 * - AudioProber (embedded cover extraction)
 * - EpubCommonHelper (EPUB cover extraction)
 * - ImageHelper (fallback cover generation)
 * 
 * Usage:
 * - For folder imports: detectCoverFromFolder()
 * - For audio metadata: extractEmbeddedCover()
 * - For EPUB files: detectCoverFromEpub()
 * - For fallback: createFallbackCover()
 */
public class CoverPictureDetection {

    /**
     * Result object containing cover detection information
     */
    public static class CoverDetectionResult {
        @Nullable
        public final String imagePath; // URI string or file path
        @Nullable
        public final Bitmap bitmap; // In-memory bitmap (for embedded covers)
        public final CoverSource source;

        public enum CoverSource {
            EMBEDDED_METADATA, // From audio file metadata
            FOLDER_IMAGE, // From image file in folder
            EPUB_STRUCTURE, // From EPUB cover
            FALLBACK_GENERATED, // Generated pastel cover
            NONE // No cover found
        }

        public CoverDetectionResult(@Nullable String imagePath, @Nullable Bitmap bitmap, CoverSource source) {
            this.imagePath = imagePath;
            this.bitmap = bitmap;
            this.source = source;
        }

        public boolean hasCover() {
            return imagePath != null || bitmap != null;
        }
    }

    /**
     * Detects cover from a folder by scanning for image files.
     * Selects the largest image file found.
     * 
     * @param context          Android context
     * @param folder           DocumentFile representing the folder to scan
     * @param currentImagePath Current image path (if any) to compare against
     * @return CoverDetectionResult with the best cover found, or NONE if no images
     */
    @Nullable
    public static CoverDetectionResult detectCoverFromFolder(Context context, DocumentFile folder,
            @Nullable String currentImagePath) {
        if (folder == null || !folder.isDirectory()) {
            return new CoverDetectionResult(null, null, CoverDetectionResult.CoverSource.NONE);
        }

        String bestImagePath = currentImagePath;
        long bestImageSize = 0;

        // Get current image size if we have one
        if (currentImagePath != null) {
            try {
                bestImageSize = UriHelper.getSize(context, Uri.parse(currentImagePath));
            } catch (Exception e) {
                myLogE("Error getting current image size: " + e.getMessage());
            }
        }

        // Scan folder for images
        for (DocumentFile file : folder.listFiles()) {
            myLogD("scanning for cover image : " + file.getName());
            if (file.isFile() && isCoverImage(file)) {
                long imageSize = file.length();
                if (bestImagePath == null || imageSize > bestImageSize) {
                    myLogD("New biggest Picture Found, size = [" + imageSize + "] - [" + file.getUri() + "]");
                    bestImagePath = file.getUri().toString();
                    bestImageSize = imageSize;
                }
            }
        }

        if (bestImagePath != null && !bestImagePath.equals(currentImagePath)) {
            return new CoverDetectionResult(bestImagePath, null, CoverDetectionResult.CoverSource.FOLDER_IMAGE);
        }

        return new CoverDetectionResult(currentImagePath, null,
                currentImagePath != null ? CoverDetectionResult.CoverSource.FOLDER_IMAGE
                        : CoverDetectionResult.CoverSource.NONE);
    }

    /**
     * Extracts embedded cover from audio file metadata using
     * MediaMetadataRetriever.
     * 
     * @param mmr MediaMetadataRetriever already set to the audio file
     * @return CoverDetectionResult with bitmap if found, or NONE
     */
    @Nullable
    public static CoverDetectionResult extractEmbeddedCover(MediaMetadataRetriever mmr) {
        if (mmr == null) {
            return new CoverDetectionResult(null, null, CoverDetectionResult.CoverSource.NONE);
        }

        try {
            byte[] art = mmr.getEmbeddedPicture();
            if (art != null) {
                Bitmap cover = android.graphics.BitmapFactory.decodeByteArray(art, 0, art.length);
                if (cover != null) {
                    return new CoverDetectionResult(null, cover, CoverDetectionResult.CoverSource.EMBEDDED_METADATA);
                }
            }
        } catch (Throwable e) {
            myLogE("Error extracting embedded cover: " + e.getMessage());
        }

        return new CoverDetectionResult(null, null, CoverDetectionResult.CoverSource.NONE);
    }

    /**
     * Detects cover from EPUB file structure.
     * 
     * @param zip Map of EPUB file contents (path -> bytes)
     * @param opf OPF metadata containing cover information
     * @return CoverDetectionResult with bitmap if found, or NONE
     */
    @Nullable
    public static CoverDetectionResult detectCoverFromEpub(Map<String, byte[]> zip,
            EpubCommonHelper.OpfInfoForCover opf) {
        if (zip == null || opf == null) {
            return new CoverDetectionResult(null, null, CoverDetectionResult.CoverSource.NONE);
        }

        try {
            Bitmap cover = EpubCommonHelper.extractCoverBitmap(zip, opf);
            if (cover != null) {
                return new CoverDetectionResult(null, cover, CoverDetectionResult.CoverSource.EPUB_STRUCTURE);
            }
        } catch (Exception e) {
            myLogE("Error extracting EPUB cover: " + e.getMessage());
        }

        return new CoverDetectionResult(null, null, CoverDetectionResult.CoverSource.NONE);
    }

    /**
     * Saves a cover bitmap to temporary storage.
     * Used for embedded covers that need to be saved before folder creation.
     * 
     * @param context Android context
     * @param bitmap  Cover bitmap to save
     * @return Path to saved temp file, or null on error
     */
    @Nullable
    public static String saveCoverToTemp(Context context, Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }

        try {
            return ImageHelper.saveTempBitmap(context, bitmap);
        } catch (Exception e) {
            myLogE("Error saving cover to temp: " + e.getMessage());
            return null;
        }
    }

    /**
     * Finalizes a temporary cover by moving it to the permanent location.
     * 
     * @param context  Android context
     * @param folderId Database ID of the folder
     */
    public static void finalizeCover(Context context, long folderId) {
        try {
            ImageHelper.finalizeTempFolderImage(context, folderId);
        } catch (Exception e) {
            myLogE("Error finalizing cover: " + e.getMessage());
        }
    }

    /**
     * Creates a fallback cover with pastel background and title initials.
     * Only creates if user preference is enabled.
     * 
     * @param context    Android context
     * @param title      Book title for generating initials
     * @param folderPath Path where the cover will be saved
     * @param sizePx     Size of the cover in pixels
     * @return Path to created cover, or null if not created
     */
    @Nullable
    public static String createFallbackCover(Context context, String title, String folderPath, int sizePx) {
        if (!shouldCreateFallbackCover()) {
            return null;
        }

        try {
            return ImageHelper.createFallbackManualFolderImagePreInsert(context, title, folderPath, sizePx);
        } catch (Exception e) {
            myLogE("Error creating fallback cover: " + e.getMessage());
            return null;
        }
    }

    /**
     * Checks if a DocumentFile is a valid cover image.
     * 
     * @param file DocumentFile to check
     * @return true if file is a supported image format
     */
    public static boolean isCoverImage(DocumentFile file) {
        if (file == null || !file.isFile()) {
            return false;
        }

        return SupportedFilesHelper.isImage(file);
    }

    /**
     * Selects the best cover between current and new option based on file size.
     * 
     * @param context     Android context
     * @param currentPath Current cover path (may be null)
     * @param newImage    New image file to consider
     * @return URI string of the best cover, or null if none
     */
    @Nullable
    public static String selectBestCover(Context context, @Nullable String currentPath, DocumentFile newImage) {
        if (newImage == null || !isCoverImage(newImage)) {
            return currentPath;
        }

        long newSize = newImage.length();

        if (currentPath == null) {
            return newImage.getUri().toString();
        }

        try {
            long currentSize = UriHelper.getSize(context, Uri.parse(currentPath));
            if (newSize > currentSize) {
                return newImage.getUri().toString();
            }
        } catch (Exception e) {
            myLogE("Error comparing cover sizes: " + e.getMessage());
            // If we can't get current size, prefer the new one
            return newImage.getUri().toString();
        }

        return currentPath;
    }

    /**
     * Checks user preference for creating fallback covers.
     * 
     * @return true if fallback covers should be created
     */
    public static boolean shouldCreateFallbackCover() {
        return Option.getCreateCover();
    }

    // --- LOG --------------------------
    private static void myLog(String str) {
        KanLogger.myLog(CoverPictureDetection.class.getName(), str);
    }

    private static void myLogD(String str) {
        KanLogger.myLogD(CoverPictureDetection.class.getName(), str);
    }

    private static void myLogE(String str) {
        KanLogger.myLogE(CoverPictureDetection.class.getName(), str);
    }
}
