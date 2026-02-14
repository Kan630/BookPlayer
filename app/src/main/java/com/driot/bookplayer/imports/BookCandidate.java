package com.driot.bookplayer.imports;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.CoverPictureDetection;
import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.utils.HashWorker;
import com.driot.bookplayer.utils.Tonio;
import com.googlecode.mp4parser.DataSource;
import com.googlecode.mp4parser.FileDataSourceViaHeapImpl;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;

import java.util.Locale;
import java.util.Objects;

public class BookCandidate {
    public Uri uri;
    public String name;
    public String type; // Folder, ZIP, M4B, EPUB.
    public String path; // For display
    public long size;
    public int tracksCount;
    public String originalHash; // Computed during scanning
    public String existingBookName; // Name of book if hash already exists in DB (null if not imported)
    public String coverImagePath; // Path to detected cover image (null if none)
    private boolean selected; // User selection for mass import (false if already imported)

    // New fields from BookToAdd
    public String sourceLocation = "init...";
    public String playType;
    public String infoMimeExtension = "init...";
    public String infoMimeExtensionSmall = "init...";
    public String infoSourceLocation = "init...";

    // New fields from BookToAdd (Phase 2)
    public boolean isBroken = false;
    public boolean isMimeSupported = true;
    public String audioBookName = "init..."; // formatted for display
    public int multipleBooksCount = 0;
    public boolean hasOnlyZipFilesInFolder = false;
    public String originalFile;
    public String originalType;
    public String fileExtension;
    public String mimeType;
    public String specialType;

    public BookCandidate(Context context, Uri uri, String name, String type) {
        this.uri = uri;
        this.name = name;
        this.type = type;
        this.path = name; // Default path
        this.selected = true;

        enrich(context);
    }

    private void enrich(Context context) {
        DocumentFile file = UriHelper.getDocumentFileFromAnyUri(context, uri);
        if (file == null)
            return;

        // Calculate fields
        this.size = calculateSize(file);
        this.originalHash = computeHash(context, uri);

        if ("Folder".equals(type)) {
            this.existingBookName = checkFolderAlreadyImported(context, uri.toString(), originalHash);
            this.tracksCount = calculateTrackCount(file);
            this.coverImagePath = detectCoverForFolder(context, file);

            // BookToAdd specifics
            this.audioBookName = getBookName_with2folders(uri.getPath(), false);
            this.multipleBooksCount = countRealEbookFilesRecursive(context, uri); // Using context overload
            this.hasOnlyZipFilesInFolder = checkHasOnlyZipFilesInFolder(context, uri);
        } else {
            this.existingBookName = checkHashExists(context, originalHash);
            this.coverImagePath = detectCoverForFile(context, file, type);
            this.tracksCount = 1;
            if ("M4B".equals(type)) {
                this.tracksCount = calculateTrackCountForM4B(context, file);
            } else if ("ZIP".equals(type)) {
                this.tracksCount = calculateTrackCountForArchive(context, file);
            }

            // BookToAdd specifics
            this.originalFile = com.driot.bookplayer.helpers.SupportedFilesHelper.getFileName(context, uri);
            this.fileExtension = com.driot.bookplayer.helpers.SupportedFilesHelper.getFileExtension(originalFile);
            this.mimeType = com.driot.bookplayer.helpers.SupportedFilesHelper.getMimeType(context, uri);
            this.specialType = com.driot.bookplayer.helpers.SupportedFilesHelper.getSpecialType(originalFile);

            if (Objects.toString(fileExtension, "").isEmpty()) {
                this.isBroken = true;
            }

            this.audioBookName = Tonio.formatNameForDisplay(originalFile);

            if (!com.driot.bookplayer.helpers.SupportedFilesHelper.isBookSupported(originalFile)) {
                this.isMimeSupported = false;
            }
        }

        trimAudioBookPrefix();

        // Update selected state based on import status
        if (this.existingBookName != null && !this.existingBookName.isEmpty()) {
            this.selected = false;
        }

        // Extra info fields
        this.sourceLocation = Tonio.getSourceLocation(context, uri);

        if ("Folder".equals(type)) {
            this.playType = inferPlayTypeFromFolder(context, uri);
            this.infoMimeExtension = "[" + "Folder" + "]";
            this.infoMimeExtensionSmall = "init...";
        } else {
            this.playType = com.driot.bookplayer.helpers.SupportedFilesHelper.getPlayType(name);

            // Already calculated above in 'else' block
            // String mimeType =
            // com.driot.bookplayer.helpers.SupportedFilesHelper.getMimeType(context, uri);
            // String fileExtension =
            // com.driot.bookplayer.helpers.SupportedFilesHelper.getFileExtension(name);
            // String specialType =
            // com.driot.bookplayer.helpers.SupportedFilesHelper.getSpecialType(name);

            this.infoMimeExtension = "[" + specialType + "] :    [" + mimeType + "] - [." + fileExtension + "]";
            this.infoMimeExtensionSmall = "[" + mimeType + "] - [." + fileExtension + "]";
        }
    }

    // --- BookToAdd specific helpers (ported) ---

    private String getBookName_with2folders(String sFolderPath, boolean stripExtension) {
        // nom par défaut = les deux derniers folders :
        // ex : "S3 - Finances publiques/Audios"
        String str = sFolderPath;
        String zeReturn;
        if (str == null) {
            str = "";
        }
        str = str.replace(":", "/");
        int pos1 = str.lastIndexOf("/");
        if (pos1 > -1) {
            int pos2 = str.substring(0, pos1).lastIndexOf("/", pos1);
            if (pos2 > -1) {
                zeReturn = Tonio.formatNameForDisplay(str.substring(pos2 + 1), stripExtension);
            } else {
                zeReturn = Tonio.formatNameForDisplay(str.substring(pos1 + 1), stripExtension);
            }
        } else {
            // especially when foldername is just a string without slash (Android 11 zip
            // local copy)
            zeReturn = Tonio.formatNameForDisplay(str, stripExtension);
        }
        return zeReturn;
    }

    private void trimAudioBookPrefix() {
        if (audioBookName == null)
            return;
        String[] prefixes = { "download/", "audiobooks/", "unzipped/" };
        for (String prefix : prefixes) {
            if (audioBookName.toLowerCase().startsWith(prefix)) {
                audioBookName = audioBookName.substring(prefix.length());
                break; // Stop after the first match
            }
        }
    }

    private int countRealEbookFilesRecursive(Context context, Uri folderUri) {
        try {
            DocumentFile root = UriHelper.getDocumentFileFromAnyUri(context, folderUri);
            if (root == null || !root.exists() || !root.isDirectory())
                return 0;
            return countRealEbookFilesRecursive(root);
        } catch (Exception e) {
            return 0;
        }
    }

    private int countRealEbookFilesRecursive(DocumentFile dir) {
        int count = 0;
        DocumentFile[] children = dir.listFiles();
        if (children == null)
            return 0;
        for (DocumentFile child : children) {
            if (child.isDirectory()) {
                count += countRealEbookFilesRecursive(child);
            } else if (child.isFile()) {
                String special = com.driot.bookplayer.helpers.SupportedFilesHelper.getSpecialType(child);
                if (com.driot.bookplayer.helpers.SupportedFilesHelper.isSplittableEbookSpecial(special)
                        || com.driot.bookplayer.helpers.SupportedFilesHelper.isBundleSpecial(special)) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean checkHasOnlyZipFilesInFolder(Context context, Uri folderUri) {
        try {
            DocumentFile root = UriHelper.getDocumentFileFromAnyUri(context, folderUri);
            if (root == null || !root.exists() || !root.isDirectory())
                return false;
            int[] ab = countAudioAndBundleRecursive(root);
            return ab[1] >= 1 && ab[0] == 0; // has bundles, no audio
        } catch (Exception e) {
            return false;
        }
    }

    private int[] countAudioAndBundleRecursive(DocumentFile dir) {
        int audio = 0, bundle = 0;
        DocumentFile[] children = dir.listFiles();
        if (children == null)
            return new int[] { 0, 0 };
        for (DocumentFile child : children) {
            if (child.isDirectory()) {
                int[] sub = countAudioAndBundleRecursive(child);
                audio += sub[0];
                bundle += sub[1];
            } else if (child.isFile()) {
                if (com.driot.bookplayer.helpers.SupportedFilesHelper.isAudio(child)
                        || com.driot.bookplayer.helpers.SupportedFilesHelper.isVideo(child)) {
                    audio++;
                } else if (com.driot.bookplayer.helpers.SupportedFilesHelper
                        .isBundleSpecial(com.driot.bookplayer.helpers.SupportedFilesHelper.getSpecialType(child))) {
                    bundle++;
                }
            }
        }
        return new int[] { audio, bundle };
    }

    public boolean hasMultipleBooksInFolder() {
        return multipleBooksCount >= 2 || hasOnlyZipFilesInFolder;
    }

    // --- Helper Methods ---

    private String safeName(DocumentFile f) {
        String n = f.getName();
        return n == null ? "Untitled" : n;
    }

    private String getExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isAudio(DocumentFile f) {
        String name = safeName(f);
        String ext = getExt(name);
        String mime = Objects.toString(f.getType(), "");
        if (mime != null && mime.startsWith(Var.ONLY_MIME_AUDIO))
            return true;
        return Var.SUPPORTED_AUDIO_EXTENSIONS.contains(ext);
    }

    private long calculateSize(DocumentFile file) {
        if (!file.isDirectory()) {
            return file.length();
        }
        long size = 0;
        DocumentFile[] children = file.listFiles();
        if (children != null) {
            for (DocumentFile child : children) {
                size += calculateSize(child);
            }
        }
        return size;
    }

    private String computeHash(Context context, Uri uri) {
        try {
            return HashWorker.computeHashFromUri(context, uri);
        } catch (Exception e) {
            return null;
        }
    }

    private String checkHashExists(Context context, String hash) {
        if (hash == null || hash.isEmpty()) {
            return null;
        }
        try {
            return AppDatabase.getDatabase(context).folderDao().originalHashAlreadyExist_getBookName(hash);
        } catch (Exception e) {
            return null;
        }
    }

    private String checkFolderAlreadyImported(Context context, String folderPath, String hash) {
        if (folderPath == null || folderPath.isEmpty())
            return checkHashExists(context, hash);

        try {
            AppDatabase db = AppDatabase.getDatabase(context);
            String existingByPath = db.folderDao().folderAlreadyExist_checkFolderPath_getBookName(folderPath);
            if (existingByPath != null && !existingByPath.isEmpty()) {
                return existingByPath;
            }

            if (hash != null && !hash.isEmpty()) {
                String existingByHash = db.folderDao().originalHashAlreadyExist_getBookName(hash);
                if (existingByHash != null && !existingByHash.isEmpty()) {
                    return existingByHash;
                }
            }
            return null;
        } catch (Exception e) {
            return checkHashExists(context, hash);
        }
    }

    private int calculateTrackCount(DocumentFile file) {
        if (!file.isDirectory()) {
            return isAudio(file) ? 1 : 0;
        }
        int count = 0;
        DocumentFile[] files = file.listFiles();
        if (files != null) {
            for (DocumentFile child : files) {
                count += calculateTrackCount(child);
            }
        }
        return count;
    }

    private int calculateTrackCountForM4B(Context context, DocumentFile m4bFile) {
        java.io.File tempFile = null;
        try {
            String filePath = UriHelper.getPathFromUri(context, m4bFile.getUri());
            if (filePath != null) {
                java.io.File directFile = new java.io.File(filePath);
                if (directFile.exists() && directFile.isFile()) {
                    return countM4BChapters(directFile.getAbsolutePath());
                }
            }
            tempFile = UriHelper.getFileFromUri(context, m4bFile.getUri());
            if (tempFile != null && tempFile.exists()) {
                int count = countM4BChapters(tempFile.getAbsolutePath());
                if (tempFile.getParentFile() != null
                        && tempFile.getParentFile().equals(context.getCacheDir())
                        && (tempFile.getName().startsWith("uri_tmp_") || tempFile.getName().startsWith("uri_temp_"))) {
                    try {
                        tempFile.delete();
                    } catch (Exception ignored) {
                    }
                }
                return count;
            }
        } catch (Exception e) {
            if (tempFile != null && tempFile.exists()) {
                try {
                    tempFile.delete();
                } catch (Exception ignored) {
                }
            }
        }
        return 1;
    }

    private int countM4BChapters(String m4bFilePath) {
        DataSource dataSource = null;
        try {
            dataSource = new FileDataSourceViaHeapImpl(m4bFilePath);
            Movie movie = MovieCreator.build(dataSource);
            Track chapterTrack = null;
            for (Track track : movie.getTracks()) {
                String handler = track.getHandler();
                if ("text".equals(handler) || "sbtl".equals(handler)) {
                    chapterTrack = track;
                    break;
                }
            }
            if (chapterTrack != null) {
                int chapterCount = chapterTrack.getSamples().size();
                return chapterCount > 0 ? chapterCount : 1;
            }
            return 1;
        } catch (Exception e) {
            return 1;
        } finally {
            if (dataSource != null) {
                try {
                    dataSource.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private int calculateTrackCountForArchive(Context context, DocumentFile archiveFile) {
        String fileName = safeName(archiveFile).toLowerCase();
        String ext = getExt(fileName);
        try {
            if (ext.equals("7z")) {
                return calculateTrackCountFor7Z(context, archiveFile);
            } else if (ext.equals("tar") || fileName.endsWith(".tgz") || fileName.endsWith(".tar.gz")
                    || fileName.endsWith(".tbz2") || fileName.endsWith(".tar.bz2")
                    || fileName.endsWith(".txz") || fileName.endsWith(".tar.xz")) {
                return calculateTrackCountForTar(context, archiveFile);
            } else {
                return calculateTrackCountForZip(context, archiveFile);
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private int calculateTrackCountForZip(Context context, DocumentFile archiveFile) {
        int count = 0;
        try {
            java.io.InputStream inputStream = context.getContentResolver().openInputStream(archiveFile.getUri());
            if (inputStream != null) {
                try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(inputStream)) {
                    java.util.zip.ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (!entry.isDirectory() && isAudioFileName(entry.getName())) {
                            count++;
                        }
                        zis.closeEntry();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return count;
    }

    private int calculateTrackCountFor7Z(Context context, DocumentFile archiveFile) {
        int count = 0;
        try {
            try (android.os.ParcelFileDescriptor pfd = context.getContentResolver()
                    .openFileDescriptor(archiveFile.getUri(), "r")) {
                if (pfd != null) {
                    try (java.nio.channels.FileChannel channel = new java.io.FileInputStream(
                            pfd.getFileDescriptor()).getChannel()) {
                        try (org.apache.commons.compress.archivers.sevenz.SevenZFile sevenZFile = new org.apache.commons.compress.archivers.sevenz.SevenZFile(
                                channel)) {
                            org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry entry;
                            while ((entry = sevenZFile.getNextEntry()) != null) {
                                if (!entry.isDirectory() && isAudioFileName(entry.getName())) {
                                    count++;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return count;
    }

    private int calculateTrackCountForTar(Context context, DocumentFile archiveFile) {
        int count = 0;
        try {
            java.io.InputStream inputStream = context.getContentResolver().openInputStream(archiveFile.getUri());
            if (inputStream != null) {
                try (java.io.InputStream bis = new java.io.BufferedInputStream(inputStream);
                        java.io.InputStream cis = maybeWrapCompressor(bis);
                        org.apache.commons.compress.archivers.tar.TarArchiveInputStream tis = new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(
                                cis)) {
                    org.apache.commons.compress.archivers.tar.TarArchiveEntry entry;
                    while ((entry = tis.getNextTarEntry()) != null) {
                        if (!entry.isDirectory() && isAudioFileName(entry.getName())) {
                            count++;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return count;
    }

    private static java.io.InputStream maybeWrapCompressor(java.io.InputStream in)
            throws org.apache.commons.compress.compressors.CompressorException {
        org.apache.commons.compress.compressors.CompressorStreamFactory f = new org.apache.commons.compress.compressors.CompressorStreamFactory(
                true);
        try {
            return f.createCompressorInputStream(in);
        } catch (org.apache.commons.compress.compressors.CompressorException notCompressed) {
            return in;
        }
    }

    private boolean isAudioFileName(String fileName) {
        String ext = getExt(fileName);
        return Var.SUPPORTED_AUDIO_EXTENSIONS.contains(ext);
    }

    // --- Cover Detection Helpers ---

    private String detectCoverForFolder(Context context, DocumentFile folder) {
        try {
            CoverPictureDetection.CoverDetectionResult result = CoverPictureDetection.detectCoverFromFolder(context,
                    folder, null);

            if (result != null && result.imagePath != null) {
                return result.imagePath;
            }

            return extractEmbeddedCoverFromFolder(context, folder);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractEmbeddedCoverFromFolder(Context context, DocumentFile folder) {
        try {
            DocumentFile[] files = folder.listFiles();
            if (files != null) {
                for (DocumentFile file : files) {
                    if (file.isFile() && isAudio(file)) {
                        android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
                        try {
                            mmr.setDataSource(context, file.getUri());
                            CoverPictureDetection.CoverDetectionResult result = CoverPictureDetection
                                    .extractEmbeddedCover(mmr);

                            if (result != null && result.bitmap != null) {
                                String suffix = "_" + file.getUri().hashCode();
                                return com.driot.bookplayer.helpers.ImageHelper.saveTempBitmap(context,
                                        result.bitmap, suffix);
                            }
                        } finally {
                            try {
                                mmr.release();
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String detectCoverForFile(Context context, DocumentFile file, String type) {
        if ("M4B".equals(type) || "Audio File".equals(type)) {
            try {
                android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
                try {
                    mmr.setDataSource(context, file.getUri());
                    CoverPictureDetection.CoverDetectionResult result = CoverPictureDetection.extractEmbeddedCover(mmr);

                    if (result != null && result.bitmap != null) {
                        String suffix = "_" + file.getUri().hashCode();
                        return com.driot.bookplayer.helpers.ImageHelper.saveTempBitmap(context,
                                result.bitmap, suffix);
                    }
                } finally {
                    try {
                        mmr.release();
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if ("Ebook".equals(type)) {
            String fileName = safeName(file).toLowerCase();
            if (fileName.endsWith(".epub")) {
                try {
                    java.util.Map<String, byte[]> zip = com.driot.bookplayer.ebooks.EpubCommonHelper.readZip(
                            file.getUri(), context);
                    byte[] containerXml = zip.get("META-INF/container.xml");
                    if (containerXml != null) {
                        String opfPath = com.driot.bookplayer.ebooks.EpubCommonHelper.findOpfPath(containerXml);
                        byte[] opfBytes = zip.get(opfPath);
                        if (opfBytes != null) {
                            com.driot.bookplayer.ebooks.EpubLowLevelHelper.OpfInfo opf = com.driot.bookplayer.ebooks.EpubLowLevelHelper
                                    .parseOpf(opfBytes);
                            opf.opfPath = opfPath;
                            CoverPictureDetection.CoverDetectionResult result = CoverPictureDetection
                                    .detectCoverFromEpub(zip, opf);
                            if (result != null && result.bitmap != null) {
                                String suffix = "_" + file.getUri().hashCode();
                                return com.driot.bookplayer.helpers.ImageHelper.saveTempBitmap(context,
                                        result.bitmap, suffix);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            } else if (fileName.endsWith(".fb2")) {
                try {
                    String xml = com.driot.bookplayer.ebooks.Fb2LowLevelHelper.readAllText(context, file.getUri());
                    com.driot.bookplayer.ebooks.Fb2LowLevelHelper.Meta meta = com.driot.bookplayer.ebooks.Fb2LowLevelHelper
                            .parseMetaAndBinaries(xml);

                    if (meta.coverImageId != null && !meta.coverImageId.isEmpty()) {
                        byte[] imageBytes = meta.binaries.get(meta.coverImageId);
                        if (imageBytes != null) {
                            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(
                                    imageBytes, 0, imageBytes.length);
                            if (bitmap != null) {
                                String suffix = "_" + file.getUri().hashCode();
                                return com.driot.bookplayer.helpers.ImageHelper.saveTempBitmap(context,
                                        bitmap, suffix);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if ("ZIP".equals(type)) {
            try {
                return detectCoverForZip(context, file);
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private String detectCoverForZip(Context context, DocumentFile file) {
        try {
            java.io.InputStream inputStream = context.getContentResolver().openInputStream(file.getUri());
            if (inputStream != null) {
                java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(inputStream);
                java.util.zip.ZipEntry entry;

                byte[] largestImage = null;
                long largestSize = 0;
                String largestName = null;

                while ((entry = zis.getNextEntry()) != null) {
                    if (!entry.isDirectory()) {
                        String entryName = entry.getName().toLowerCase();
                        if (entryName.endsWith(".jpg") || entryName.endsWith(".jpeg") ||
                                entryName.endsWith(".png") || entryName.endsWith(".webp")) {

                            long size = entry.getSize();
                            if (size > largestSize || (size == -1 && largestImage == null)) {
                                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                                byte[] buffer = new byte[8192];
                                int len;
                                while ((len = zis.read(buffer)) > 0) {
                                    baos.write(buffer, 0, len);
                                }
                                byte[] imageBytes = baos.toByteArray();
                                if (imageBytes.length > largestSize) {
                                    largestImage = imageBytes;
                                    largestSize = imageBytes.length;
                                    largestName = entry.getName();
                                }
                            }
                            zis.closeEntry();
                        }
                    }
                }
                zis.close();

                if (largestImage != null) {
                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(
                            largestImage, 0, largestImage.length);
                    if (bitmap != null) {
                        String suffix = "_" + file.getUri().hashCode();
                        return com.driot.bookplayer.helpers.ImageHelper.saveTempBitmap(context,
                                bitmap, suffix);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String inferPlayTypeFromFolder(android.content.Context context, Uri folderUri) {
        try {
            androidx.documentfile.provider.DocumentFile root = com.driot.bookplayer.helpers.UriHelper
                    .getDocumentFileFromAnyUri(context, folderUri);
            if (root == null || !root.exists() || !root.isDirectory()) {
                return null;
            }
            int[] counts = countMediaTypesRecursive(root);
            boolean hasRealContent = counts[0] > 0;
            boolean hasPlainTextOnly = counts[1] > 0;
            if (hasRealContent) {
                return com.driot.bookplayer.global.Var.PLAY_TYPE_AUDIO;
            }
            if (hasPlainTextOnly) {
                return com.driot.bookplayer.global.Var.PLAY_TYPE_TEXT;
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private int[] countMediaTypesRecursive(androidx.documentfile.provider.DocumentFile dir) {
        int realContent = 0;
        int plainText = 0;
        androidx.documentfile.provider.DocumentFile[] children = dir.listFiles();
        if (children == null)
            return new int[] { 0, 0 };
        for (androidx.documentfile.provider.DocumentFile child : children) {
            if (child.isDirectory()) {
                int[] sub = countMediaTypesRecursive(child);
                realContent += sub[0];
                plainText += sub[1];
            } else if (child.isFile()) {
                if (isRealBookOrAudioContent(child)) {
                    realContent++;
                } else if (com.driot.bookplayer.helpers.SupportedFilesHelper.isText(child)) {
                    plainText++;
                }
            }
        }
        return new int[] { realContent, plainText };
    }

    private boolean isRealBookOrAudioContent(androidx.documentfile.provider.DocumentFile child) {
        if (com.driot.bookplayer.helpers.SupportedFilesHelper.isAudio(child)
                || com.driot.bookplayer.helpers.SupportedFilesHelper.isVideo(child)) {
            return true;
        }
        String special = com.driot.bookplayer.helpers.SupportedFilesHelper.getSpecialType(child);
        return com.driot.bookplayer.helpers.SupportedFilesHelper.isSplittableEbookSpecial(special)
                || com.driot.bookplayer.helpers.SupportedFilesHelper.isBundleSpecial(special)
                || com.driot.bookplayer.helpers.SupportedFilesHelper.isM4bSpecial(special);
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isAlreadyImported() {
        return existingBookName != null && !existingBookName.isEmpty();
    }

    @NonNull
    @Override
    public String toString() {
        return "[" + type + "] " + name;
    }
}
