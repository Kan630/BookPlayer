package com.driot.bookplayer.helpers;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.driot.bookplayer.global.Var;
import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.io.File;
import java.net.URLConnection;
import java.util.Locale;

public class SupportedFilesHelper {

    // --- File type constants ---
    public static final String FILE_TYPE_AUDIO = "audio";
    public static final String FILE_TYPE_VIDEO = "video";
    public static final String FILE_TYPE_IMAGE = "image";
    public static final String FILE_TYPE_EBOOK = "ebook";
    public static final String FILE_TYPE_BUNDLE = "bundle";

    // --- Special type constants ---
    public static final String SPECIAL_TYPE_EPUB = "EPUB";
    public static final String SPECIAL_TYPE_FB2 = "FB2";
    public static final String SPECIAL_TYPE_ODT = "ODT";
    public static final String SPECIAL_TYPE_DOCX = "DOCX";
    public static final String SPECIAL_TYPE_M4B = "M4B";
    public static final String SPECIAL_TYPE_ZIP = "ZIP";
    public static final String SPECIAL_TYPE_7Z = "7Z";
    public static final String SPECIAL_TYPE_TAR = "TAR";
    public static final String SPECIAL_TYPE_TXT = "TXT";
    public static final String SPECIAL_TYPE_HTML = "HTML";

    public static boolean isAudio(DocumentFile docFile) {
        String type = getType(docFile);
        return FILE_TYPE_AUDIO.equals(type);
    }

    public static boolean isVideo(DocumentFile docFile) {
        String type = getType(docFile);
        return FILE_TYPE_VIDEO.equals(type);
    }

    public static boolean isText(DocumentFile docFile) {
        String type = getSpecialType(docFile);
        return SPECIAL_TYPE_TXT.equals(type);
    }

    public static boolean isImage(DocumentFile docFile) {
        String type = getType(docFile);
        return FILE_TYPE_IMAGE.equals(type);
    }

    // ----------------------- SAFE UTILITIES -----------------------
    private static String safeString(String s) {
        return s == null ? "" : s;
    }

    private static String extractExtension(String fileName) {
        if (fileName == null)
            return "";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1)
            return "";
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    // ----------------------- PUBLIC GETTERS -----------------------
    /** Never null. */
    public static String getFileName(DocumentFile docFile) {
        return docFile == null ? "" : safeString(docFile.getName());
    }

    /** Without dot; never null. */
    public static String getFileExtension(DocumentFile docFile) {
        return extractExtension(getFileName(docFile));
    }

    /** May be "", never null. */
    public static String getMimeType(DocumentFile docFile) {
        return docFile == null ? "" : safeString(docFile.getType());
    }

    /** Context+Uri variants (robust) */
    public static String getFileName(@NonNull Context ctx, @NonNull Uri uri) {
        // myLogD("getFileName(ctx, uri) start: uri = " + uri);
        String name = null;

        // 1) Try OpenableColumns (most reliable for content://)
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = ctx.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        name = cursor.getString(index);
                        if (name != null) {
                            myLogD("getFileNameFromUri - OpenableColumns: [" + name + "]");
                            return name;
                        }
                    }
                }
            } catch (Exception e) {
                myLogEE(e, "getFileNameFromUri - OpenableColumns failed");
            }
        }

        // 2) Try resolving via MediaStore (DATA column)
        if (name == null && "content".equalsIgnoreCase(uri.getScheme())) {
            try {
                String[] projection = { MediaStore.MediaColumns.DATA };
                try (Cursor cursor = ctx.getContentResolver().query(uri, projection, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                        String filePath = cursor.getString(index);
                        if (filePath != null) {
                            name = new java.io.File(filePath).getName();
                            myLog("getFileNameFromUri - MediaStore: [" + name + "]");
                            return name;
                        }
                    }
                }
            } catch (Exception e) {
                myLogEE(e, "getFileNameFromUri - MediaStore failed");
            }
        }

        // 3) Fallback: parse from path manually (works for file:// and some content://)
        if (name == null) {
            String path = uri.getPath();
            if (path != null) {
                if (path.endsWith("/"))
                    path = path.substring(0, path.length() - 1);
                int cut = path.lastIndexOf('/');
                if (cut != -1 && cut + 1 < path.length()) {
                    name = path.substring(cut + 1);
                    myLog("getFileNameFromUri - path fallback: [" + name + "]");
                    return name;
                }
            }
        }

        // 4) Last fallback: last path segment
        if (name == null) {
            name = uri.getLastPathSegment();
            myLog("getFileNameFromUri - lastPathSegment fallback: [" + name + "]");
        }

        if (name == null) {
            myLogE("getFileName failed completely for uri: [" + uri + "]");
            return ""; // helper can't flag isBroken; return safe empty string
        }

        return name;
    }

    /**
     * Best-effort robust MIME from Context+Uri (includes m4b fallback and multiple
     * strategies).
     */
    public static String getMimeType(@NonNull Context context, @NonNull Uri uri) {
        // 1) ContentResolver
        String mime = null;
        try {
            mime = context.getContentResolver().getType(uri);
        } catch (Exception ignored) {
        }

        // 2) If null, fallback using extension extracted from DISPLAY_NAME or file path
        if (mime == null) {
            String extension = null;
            try {
                if ("content".equals(uri.getScheme())) {
                    try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                        if (cursor != null) {
                            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                            if (nameIndex != -1 && cursor.moveToFirst()) {
                                String fileName = cursor.getString(nameIndex);
                                int dotIndex = fileName != null ? fileName.lastIndexOf('.') : -1;
                                if (fileName != null && dotIndex >= 0) {
                                    extension = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
                                }
                            }
                        }
                    }
                } else if ("file".equals(uri.getScheme())) {
                    String path = uri.getPath();
                    if (path != null) {
                        int dotIndex = path.lastIndexOf('.');
                        if (dotIndex >= 0) {
                            extension = path.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
                        }
                    }
                }

                if (extension != null) {
                    mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                    // Custom fallback for m4b
                    if (mime == null && "m4b".equals(extension)) {
                        mime = "audio/m4b";
                    }
                }
            } catch (Exception e) {
                myLogEE(e, "getMimeType(ctx, uri) - extension fallback");
            }
        }

        // 3) Guess from name (cheap heuristic)
        if (mime == null) {
            try {
                String guess = URLConnection.guessContentTypeFromName(uri.toString());
                if (guess != null)
                    mime = guess;
            } catch (Exception ignored) {
            }
        }

        // 4) Unknown → ""
        return mime == null ? "" : mime;
    }

    public static String getFileExtension(String fileName) {
        return extractExtension(fileName);
    }

    // ----------------------- TYPE RESOLUTION -----------------------
    /** Hybrid: check supported MIME sets, then prefix fallback for robustness. */
    private static String typeFromMime(String mime) {
        if (mime == null || mime.isEmpty())
            return null;

        String lower = mime.toLowerCase(Locale.ROOT);

        if (Var.SUPPORTED_AUDIO_MIMES.contains(lower) || lower.startsWith("audio/"))
            return FILE_TYPE_AUDIO;
        if (Var.SUPPORTED_VIDEO_MIMES.contains(lower) || lower.startsWith("video/"))
            return FILE_TYPE_VIDEO;
        if (Var.SUPPORTED_IMAGE_MIMES.contains(lower) || lower.startsWith("image/"))
            return FILE_TYPE_IMAGE;
        if (Var.SUPPORTED_EBOOK_MIMES.contains(lower))
            return FILE_TYPE_EBOOK;

        return null;
    }

    private static String typeFromExtension(String extLower) {
        if (extLower == null || extLower.isEmpty())
            return null;
        if (Var.SUPPORTED_AUDIO_EXTENSIONS.contains(extLower))
            return FILE_TYPE_AUDIO;
        if (Var.SUPPORTED_VIDEO_EXTENSIONS.contains(extLower))
            return FILE_TYPE_VIDEO;
        if (Var.SUPPORTED_IMAGE_EXTENSIONS.contains(extLower))
            return FILE_TYPE_IMAGE;
        if (Var.SUPPORTED_EBOOK_EXTENSIONS.contains(extLower))
            return FILE_TYPE_EBOOK;
        if (Var.SUPPORTED_COMPRESSED_FILE_EXTENSIONS.contains(extLower))
            return FILE_TYPE_BUNDLE;
        return null;
    }

    /**
     * Determine type from DocumentFile. Warn if MIME and extension conflict.
     */
    public static String getType(DocumentFile docFile) {
        if (docFile == null)
            return null;

        String name = getFileName(docFile);
        String ext = extractExtension(name);
        String mime = getMimeType(docFile);

        String extType = typeFromExtension(ext);
        String mimeType = typeFromMime(mime);

        if (extType != null && mimeType != null && !extType.equals(mimeType)) {
            myLogW("(WARNING Mismatch) [" + extType + "] . [" + name + "] - mime = [" + mime + "]");
        }

        // Prefer MIME when available (SAF), else extension
        return mimeType != null ? mimeType : extType;
    }

    /**
     * Robust overload using Context+Uri (uses the robust getMimeType(ctx, uri)).
     */
    public static String getType(@NonNull Context ctx, @NonNull Uri uri) {
        String name = getFileName(ctx, uri);
        String ext = extractExtension(name);
        String mime = getMimeType(ctx, uri);

        String extType = typeFromExtension(ext);
        String mimeType = typeFromMime(mime);

        if (extType != null && mimeType != null && !extType.equals(mimeType)) {
            myLogW("(WARNING Mismatch) [" + extType + "] . [" + name + "] - mime = [" + mime + "]");
        }

        return mimeType != null ? mimeType : extType;
    }

    /** Extension-only fallback (no MIME guess). */
    public static String getType(String fileName) {
        if (fileName == null)
            return null;
        String ext = extractExtension(fileName);
        return typeFromExtension(ext);
    }

    // ----------------------- SPECIAL TYPE -----------------------

    public static String getSpecialTypeFromExt(String ext) {
        switch (ext) {
            case "zip":
                return SPECIAL_TYPE_ZIP;
            case "7z":
                return SPECIAL_TYPE_7Z;
            case "tar":
            case "tgz":
            case "tbz2":
            case "txz":
            case "tar.bz2":
            case "tar.xz":
                return SPECIAL_TYPE_TAR;
            case "m4b":
                return SPECIAL_TYPE_M4B;
            case "odt":
                return SPECIAL_TYPE_ODT;
            case "docx":
                return SPECIAL_TYPE_DOCX;
            case "fb2":
                return SPECIAL_TYPE_FB2;
            case "epub":
                return SPECIAL_TYPE_EPUB;
            case "txt":
                return SPECIAL_TYPE_TXT;
            case "html":
            case "htm":
                return SPECIAL_TYPE_HTML;
            default:
                return null;
        }
    }

    public static String getSpecialType(DocumentFile docFile) {
        if (docFile == null)
            return null;
        String name = getFileName(docFile);
        String ext = extractExtension(name);
        String mime = getMimeType(docFile);

        // TXT (by mime or ext)
        if ((mime.startsWith("text/")) || "txt".equalsIgnoreCase(ext))
            return SPECIAL_TYPE_TXT;

        return getSpecialTypeFromExt(ext);
    }

    public static String getSpecialType(String fileName) {
        if (fileName == null)
            return null;
        String ext = extractExtension(fileName).toLowerCase(Locale.ROOT);
        return getSpecialTypeFromExt(ext);
    }

    // ----------------------- SUPPORT CHECKS -----------------------
    private static boolean isBookSupportedFromType(String type) {
        return FILE_TYPE_AUDIO.equals(type) || FILE_TYPE_VIDEO.equals(type) || FILE_TYPE_EBOOK.equals(type)
                || FILE_TYPE_BUNDLE.equals(type);
    }

    public static boolean isBookSupported(DocumentFile docFile) {
        return isBookSupportedFromType(getType(docFile));
    }

    public static boolean isBookSupported(String fileName) {
        return isBookSupportedFromType(getType(fileName));
    }

    public static String getPlayType(String fileName) {
        String type = getType(fileName);
        if (type == null)
            return null;
        switch (type) {
            case FILE_TYPE_EBOOK:
                return Var.PLAY_TYPE_TEXT;
            case FILE_TYPE_AUDIO:
            case FILE_TYPE_VIDEO:
            case FILE_TYPE_BUNDLE:
                return Var.PLAY_TYPE_AUDIO;
            default:
                return null;
        }
    }

    // Group helpers for special types
    public static boolean isBundleSpecial(String specialType) {
        return SPECIAL_TYPE_ZIP.equals(specialType)
                || SPECIAL_TYPE_7Z.equals(specialType)
                || SPECIAL_TYPE_TAR.equals(specialType);
    }

    public static boolean isEbookSpecial(String specialType) {
        return SPECIAL_TYPE_EPUB.equals(specialType)
                || SPECIAL_TYPE_FB2.equals(specialType)
                || SPECIAL_TYPE_ODT.equals(specialType)
                || SPECIAL_TYPE_DOCX.equals(specialType)
                || SPECIAL_TYPE_HTML.equals(specialType)
                || SPECIAL_TYPE_TXT.equals(specialType);
    }

    /** epub, fb2, odt – need EbookSplitWorker to extract .txt before TTS. */
    public static boolean isSplittableEbookSpecial(String specialType) {
        return SPECIAL_TYPE_EPUB.equals(specialType)
                || SPECIAL_TYPE_FB2.equals(specialType)
                || SPECIAL_TYPE_ODT.equals(specialType)
                || SPECIAL_TYPE_HTML.equals(specialType)
                || SPECIAL_TYPE_DOCX.equals(specialType);
    }

    public static boolean isM4bSpecial(String specialType) {
        return SPECIAL_TYPE_M4B.equals(specialType);
    }

}
