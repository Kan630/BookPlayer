package com.driot.bookplayer.imports;

import android.content.Context;
import android.net.Uri;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.driot.bookplayer.R;
import com.driot.bookplayer.db.AppDatabase;
import com.driot.bookplayer.ebooks.EpubCommonHelper;
import com.driot.bookplayer.ebooks.EpubLowLevelHelper;
import com.driot.bookplayer.ebooks.Fb2LowLevelHelper;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.CoverPictureDetection;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.SupportedFilesHelper;
import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.utils.HashWorker;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.KanLogger;
import com.googlecode.mp4parser.DataSource;
import com.googlecode.mp4parser.FileDataSourceViaHeapImpl;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;

import java.util.Locale;
import java.util.Objects;

import android.os.Parcel;
import android.os.Parcelable;

public class BookCandidate implements Parcelable {
    private boolean LOG_DEBUG = true;

    public Uri uri;
    public String name;
    public String sourceType; // Folder, Archive, M4B, EPUB, Audio File
    public String path; // For display
    public long size;
    public int tracksCount;
    public String originalHash; // Computed during scanning
    public String existingBookName; // Name of book if hash already exists in DB (null if not imported)
    public String coverImagePath; // Path to detected cover image (null if none)
    private boolean selected; // User selection for mass import (false if already imported)

    // New fields from BookToAdd
    public String sourceLocation = "sourceLocation...";
    public String playType;
    public String infoMimeExtension = "init...";
    public String infoMimeExtensionSmall = "init...";
    public String infoSourceLocation = "infoSourceLocation...";
    public String infoLine1 = "init...";

    // New fields from BookToAdd (Phase 2)
    public boolean isBroken = false;
    public boolean isMimeSupported = true;
    public boolean isHeavyLoaded = false;
    public boolean isCalculating = false; // UI state for heavy load progress
    public String audioBookName = "init..."; // formatted for display
    public int multipleBooksCount = 0;
    public boolean hasOnlyZipFilesInFolder = false;
    public String originalFile;
    public String fileExtension;
    public String mimeType;
    public String specialType;

    public final java.util.List<String> trackList = new java.util.ArrayList<>();

    public interface OnMetadataListener {
        void onTrackFound(String name);

        void onCoverFound(String imagePath);
    }

    protected BookCandidate(Parcel in) {
        uri = in.readParcelable(Uri.class.getClassLoader());
        name = in.readString();
        sourceType = in.readString();
        path = in.readString();
        size = in.readLong();
        tracksCount = in.readInt();
        originalHash = in.readString();
        existingBookName = in.readString();
        coverImagePath = in.readString();
        selected = in.readByte() != 0;
        sourceLocation = in.readString();
        playType = in.readString();
        infoMimeExtension = in.readString();
        infoMimeExtensionSmall = in.readString();
        infoSourceLocation = in.readString();
        infoLine1 = in.readString();
        isBroken = in.readByte() != 0;
        isMimeSupported = in.readByte() != 0;
        isHeavyLoaded = in.readByte() != 0;
        audioBookName = in.readString();
        multipleBooksCount = in.readInt();
        hasOnlyZipFilesInFolder = in.readByte() != 0;
        originalFile = in.readString();
        fileExtension = in.readString();
        mimeType = in.readString();
        specialType = in.readString();
        in.readStringList(trackList);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(uri, flags);
        dest.writeString(name);
        dest.writeString(sourceType);
        dest.writeString(path);
        dest.writeLong(size);
        dest.writeInt(tracksCount);
        dest.writeString(originalHash);
        dest.writeString(existingBookName);
        dest.writeString(coverImagePath);
        dest.writeByte((byte) (selected ? 1 : 0));
        dest.writeString(sourceLocation);
        dest.writeString(playType);
        dest.writeString(infoMimeExtension);
        dest.writeString(infoMimeExtensionSmall);
        dest.writeString(infoSourceLocation);
        dest.writeString(infoLine1);
        dest.writeByte((byte) (isBroken ? 1 : 0));
        dest.writeByte((byte) (isMimeSupported ? 1 : 0));
        dest.writeByte((byte) (isHeavyLoaded ? 1 : 0));
        dest.writeString(audioBookName);
        dest.writeInt(multipleBooksCount);
        dest.writeByte((byte) (hasOnlyZipFilesInFolder ? 1 : 0));
        dest.writeString(originalFile);
        dest.writeString(fileExtension);
        dest.writeString(mimeType);
        dest.writeString(specialType);
        dest.writeStringList(trackList);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<BookCandidate> CREATOR = new Creator<BookCandidate>() {
        @Override
        public BookCandidate createFromParcel(Parcel in) {
            return new BookCandidate(in);
        }

        @Override
        public BookCandidate[] newArray(int size) {
            return new BookCandidate[size];
        }
    };

    public BookCandidate(Context context, Uri uri) {
        this.uri = uri;
        if (this.uri == null)
            throw new RuntimeException("constructor : uri is null");

        // Cache filename to avoid redundant calls
        this.name = SupportedFilesHelper.getFileName(context, uri);
        // Use filename-based getType to avoid calling getFileName again
        this.sourceType = SupportedFilesHelper.getType(this.name);

        // Special handling for Folder type which might return null or generic type
        if (this.sourceType == null) {
            DocumentFile file = UriHelper.getDocumentFileFromAnyUri(context, uri);
            if (file != null && file.isDirectory()) {
                this.sourceType = "Folder";
            }
        }

        // Map SupportedFilesHelper types to BookCandidate legacy types
        // ("audio" → "Audio File", "bundle" → "Archive", etc.)
        if (!"Folder".equals(this.sourceType)) {
            String specialType = SupportedFilesHelper.getSpecialType(this.name);

            if (SupportedFilesHelper.SPECIAL_TYPE_M4B.equals(specialType)) {
                this.sourceType = "M4B";
            } else if (SupportedFilesHelper.isBundleSpecial(specialType)) {
                this.sourceType = "Archive";
            } else if (SupportedFilesHelper.isEbookSpecial(specialType)) {
                this.sourceType = "Ebook";
            } else if (SupportedFilesHelper.FILE_TYPE_AUDIO.equals(this.sourceType)) {
                this.sourceType = "Audio File";
            }
        }

        this.path = this.name; // Default path
        this.selected = true;

        if (this.sourceType == null)
            throw new RuntimeException("constructor : sourceType is null");

        enrich(context, false); // Default to fast enrichment
    }

    /**
     * Secondary initialization for heavy operations (zip scanning).
     * Call this from a background thread after the initial fast load.
     */
    public void loadHeavyMetadata(Context context) {
        enrich(context, true);
    }

    private void enrich(Context context, boolean heavyEnrich) {
        long startTime = System.currentTimeMillis();
        myLogD("enrich() START (heavy=" + heavyEnrich + ") for: " + name);

        DocumentFile file = UriHelper.getDocumentFileFromAnyUri(context, uri);
        if (file == null)
            return;

        if (!heavyEnrich) {
            // FAST PHASE
            myLogD("document file ok");
            // Calculate fields
            // Calculate fields
            // this.size = calculateSize(file); // Moved to individual blocks (deferred for
            // Folder)
            myLogD("calculateSize ok");
            this.originalHash = computeHash(context, uri);
            myLogD("Hash ok");

            if ("Folder".equals(sourceType)) {
                this.size = -1; // Defer calculation to heavy phase
                this.existingBookName = checkFolderAlreadyImported(context, uri.toString(), originalHash);
                myLogD("checkFolderAlreadyImported ok");
                // Skipping heavy folder calcs for now or keeping them if they are fast enough?
                // Folder scanning can be slow too, but let's stick to the plan for Zip first.
                // For now, let's keep Folder logic as is or maybe split it too if needed.
                // The user specifically mentioned Zip files.
                // Let's keep existing synchronous logic for Folder for now to check regression
                // risks,
                // but we might want to move recursive counts to heavy.

                // For safety and complying with "non-blocking" request, let's move heavy
                // recursive counts to heavy phase for Folder too.

                this.audioBookName = getBookName_with2folders(uri.getPath(), false);
                myLogD("getBookName_with2folders ok");
            } else {
                // Calculate fields (files are fast)
                this.size = file.length();
                myLogD("calculateSize ok");

                this.existingBookName = checkHashExists(context, originalHash);
                myLogD("checkHashExists ok");

                // M4B and Audio File cover detection is usually fast (header read), but better
                // safe than sorry.
                // Ebook cover detection involves zip reading too (epub).

                // BookToAdd specifics
                // Reuse this.name instead of calling getFileName again
                this.originalFile = this.name;
                this.fileExtension = SupportedFilesHelper.getFileExtension(originalFile);
                this.mimeType = SupportedFilesHelper.getMimeType(context, uri);
                this.specialType = SupportedFilesHelper.getSpecialType(originalFile);

                if (Objects.toString(fileExtension, "").isEmpty()) {
                    this.isBroken = true;
                }

                this.audioBookName = Tonio.formatNameForDisplay(originalFile);

                if (!SupportedFilesHelper.isBookSupported(originalFile)) {
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
            this.infoSourceLocation = context.getString(R.string.Location) + ": [" + this.sourceLocation + "]";
            this.infoLine1 = this.sourceType + " - " + this.infoSourceLocation;

            if ("Folder".equals(sourceType)) {
                // this.playType = inferPlayTypeFromFolder(context, uri); // Moved to heavy
                // phase
                this.infoMimeExtension = "[" + "Folder" + "]";
                this.infoMimeExtensionSmall = "init...";
            } else {
                this.playType = SupportedFilesHelper.getPlayType(name);
                this.infoMimeExtension = "[" + specialType + "] :    [" + mimeType + "] - [." + fileExtension + "]";
                this.infoMimeExtensionSmall = "[" + mimeType + "] - [." + fileExtension + "]";
            }
        } else {
            // HEAVY PHASE
            if (Thread.currentThread().isInterrupted())
                return;
            loadHeavyMetadata(context, null);
        }

        myLogD("enrich() TOTAL (heavy=" + heavyEnrich + "): " + (System.currentTimeMillis() - startTime) + "ms for: "
                + name);
    }

    /**
     * Heavy initialization: scans zip for covers, counts tracks.
     * This is blocking and should be called in background.
     */
    public void loadHeavyMetadata(Context context, OnMetadataListener listener) {
        long startTime = System.currentTimeMillis();
        myLogD("loadHeavyMetadata() START for: " + name);

        DocumentFile file = UriHelper.getDocumentFileFromAnyUri(context, uri);
        if (file == null)
            return;

        if ("Folder".equals(sourceType)) {
            // HEAVY PHASE
            if (Thread.currentThread().isInterrupted())
                return;

            scanFolderCombined(context, file, listener);
        } else {
            // HEAVY PHASE
            if (Thread.currentThread().isInterrupted())
                return;

            if ("M4B".equals(sourceType)) {
                scanM4BCombined(context, file, listener);
            } else if ("Archive".equals(sourceType)) {
                scanArchiveCombined(context, file, listener);
            } else {
                this.tracksCount = 1;
                this.coverImagePath = detectCoverForFile(context, file, sourceType);
                myLogD("detectCoverForFile ok");
                if (this.coverImagePath != null && listener != null) {
                    listener.onCoverFound(this.coverImagePath);
                }
            }
        }

        this.isHeavyLoaded = true;

        myLogD("loadHeavyMetadata() DONE: " + (System.currentTimeMillis() - startTime) + "ms for: "
                + name);
    }

    private void scanM4BCombined(Context context, DocumentFile file, OnMetadataListener listener) {
        long startTime = System.currentTimeMillis();
        myLogD("scanM4BCombined() START for: " + name);

        try (android.os.ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(file.getUri(),
                "r")) {
            if (pfd != null) {
                java.io.FileDescriptor fd = pfd.getFileDescriptor();

                // 1. Cover Detection
                android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
                try {
                    mmr.setDataSource(fd);
                    com.driot.bookplayer.helpers.CoverPictureDetection.CoverDetectionResult result = com.driot.bookplayer.helpers.CoverPictureDetection
                            .extractEmbeddedCover(mmr);

                    if (result != null && result.bitmap != null) {
                        String suffix = "_" + file.getUri().hashCode();
                        this.coverImagePath = ImageHelper.saveTempBitmap(context, result.bitmap, suffix);
                        if (this.coverImagePath != null && listener != null) {
                            listener.onCoverFound(this.coverImagePath);
                        }
                    }
                } finally {
                    try {
                        mmr.release();
                    } catch (Exception ignored) {
                    }
                }

                // IMPORTANT: MediaMetadataRetriever might have moved the FD position.
                // Reset it to 0 before giving it to mp4parser, otherwise it reads garbage and
                // crashes (OOM/Large allocation).
                try {
                    android.system.Os.lseek(fd, 0, android.system.OsConstants.SEEK_SET);
                } catch (Exception e) {
                    myLogW("Could not seek FD back to 0: " + e.getMessage());
                }

                // 2. Track Count
                try (java.nio.channels.FileChannel channel = new java.io.FileInputStream(fd).getChannel()) {
                    // Try refreshing path if possible to use FileDataSourceViaHeapImpl as it was
                    // working before
                    String directPath = UriHelper.getPathFromUri(context, file.getUri());
                    if (directPath != null && new java.io.File(directPath).exists()) {
                        dataSource = new com.googlecode.mp4parser.FileDataSourceViaHeapImpl(directPath);
                    } else {
                        // Fallback to channel based datasource (with offset 0)
                        dataSource = new com.googlecode.mp4parser.FileDataSourceImpl(channel);
                    }

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
                        java.util.List<com.googlecode.mp4parser.authoring.Sample> samples = chapterTrack.getSamples();
                        int chapterCount = samples.size();
                        this.tracksCount = chapterCount > 0 ? chapterCount : 1;

                        for (int i = 0; i < chapterCount; i++) {
                            String title = extractCleanChapterTitle(samples.get(i));
                            String tName = (i + 1) + ". " + title;
                            trackList.add(tName);
                            if (listener != null) {
                                listener.onTrackFound(tName);
                            }
                        }
                    } else {
                        this.tracksCount = 1;
                    }
                } finally {
                    if (dataSource != null) {
                        try {
                            dataSource.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception e) {
            myLogEE(e, "Error during combined M4B scan");
            this.tracksCount = 1; // Fallback
        }

        myLogD("scanM4BCombined() DONE in " + (System.currentTimeMillis() - startTime) + "ms. tracks=" + tracksCount);
    }

    private com.googlecode.mp4parser.DataSource dataSource; // Temporary helper for scanM4BCombined

    private void scanArchiveCombined(Context context, DocumentFile archiveFile, OnMetadataListener listener) {
        long startTime = System.currentTimeMillis();
        myLogD("scanArchiveCombined() START for: " + name);

        String fileName = safeName(archiveFile).toLowerCase();
        String ext = getExt(fileName);

        if (ext.equals("7z")) {
            scan7ZCombined(context, archiveFile, listener);
        } else if (ext.equals("tar") || fileName.endsWith(".tgz") || fileName.endsWith(".tar.gz")
                || fileName.endsWith(".tbz2") || fileName.endsWith(".tar.bz2")
                || fileName.endsWith(".txz") || fileName.endsWith(".tar.xz")) {
            scanTarCombined(context, archiveFile, listener);
        } else {
            // Real Zip Optimization (already combined)
            scanZipCombined(context, archiveFile, listener);
        }

        myLogD("scanArchiveCombined() DONE in " + (System.currentTimeMillis() - startTime) + "ms. tracks="
                + this.tracksCount);
    }

    private void scan7ZCombined(Context context, DocumentFile archiveFile, OnMetadataListener listener) {
        int count = 0;
        byte[] largestImage = null;
        long largestSize = 0;

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
                                if (Thread.currentThread().isInterrupted())
                                    return;
                                String entryName = entry.getName();
                                if (entry.isDirectory())
                                    continue;

                                // 1. Track count
                                if (isAudioFileName(entryName)) {
                                    count++;
                                    String fileNameOnly = new java.io.File(entryName).getName();
                                    String displayName = com.driot.bookplayer.utils.Tonio
                                            .formatNameForDisplay(fileNameOnly);
                                    String tName = count + ". " + displayName;
                                    trackList.add(tName);
                                    if (listener != null) {
                                        listener.onTrackFound(tName);
                                    }
                                }

                                // 2. Cover detection
                                String lowName = entryName.toLowerCase();
                                if (lowName.endsWith(".jpg") || lowName.endsWith(".jpeg") ||
                                        lowName.endsWith(".png") || lowName.endsWith(".webp")) {
                                    long size = entry.getSize();
                                    if (size > largestSize || (size == -1 && largestImage == null)) {
                                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                                        byte[] buffer = new byte[8192];
                                        int len;
                                        // SevenZFile.read() reads the current entry
                                        while ((len = sevenZFile.read(buffer)) > 0) {
                                            baos.write(buffer, 0, len);
                                        }
                                        byte[] imageBytes = baos.toByteArray();
                                        if (imageBytes.length > largestSize) {
                                            largestImage = imageBytes;
                                            largestSize = imageBytes.length;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        this.tracksCount = count;

        if (largestImage != null) {
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(
                    largestImage, 0, largestImage.length);
            if (bitmap != null) {
                String suffix = "_" + archiveFile.getUri().hashCode();
                this.coverImagePath = ImageHelper.saveTempBitmap(context, bitmap, suffix);
                if (this.coverImagePath != null && listener != null) {
                    listener.onCoverFound(this.coverImagePath);
                }
            }
        }
    }

    private void scanTarCombined(Context context, DocumentFile archiveFile, OnMetadataListener listener) {
        int count = 0;
        byte[] largestImage = null;
        long largestSize = 0;

        try {
            java.io.InputStream inputStream = context.getContentResolver().openInputStream(archiveFile.getUri());
            if (inputStream != null) {
                try (java.io.InputStream bis = new java.io.BufferedInputStream(inputStream);
                        java.io.InputStream cis = maybeWrapCompressor(bis);
                        org.apache.commons.compress.archivers.tar.TarArchiveInputStream tis = new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(
                                cis)) {
                    org.apache.commons.compress.archivers.tar.TarArchiveEntry entry;
                    while ((entry = tis.getNextTarEntry()) != null) {
                        if (Thread.currentThread().isInterrupted())
                            return;
                        String entryName = entry.getName();
                        if (entry.isDirectory())
                            continue;

                        // 1. Track counting
                        if (isAudioFileName(entryName)) {
                            count++;
                            String fileNameOnly = new java.io.File(entryName).getName();
                            String displayName = com.driot.bookplayer.utils.Tonio.formatNameForDisplay(fileNameOnly);
                            String tName = count + ". " + displayName;
                            trackList.add(tName);
                            if (listener != null) {
                                listener.onTrackFound(tName);
                            }
                        }

                        // 2. Cover detection
                        String lowName = entryName.toLowerCase();
                        if (lowName.endsWith(".jpg") || lowName.endsWith(".jpeg") ||
                                lowName.endsWith(".png") || lowName.endsWith(".webp")) {
                            long size = entry.getSize();
                            if (size > largestSize || (size == -1 && largestImage == null)) {
                                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                                byte[] buffer = new byte[8192];
                                int len;
                                while ((len = tis.read(buffer)) > 0) {
                                    baos.write(buffer, 0, len);
                                }
                                byte[] imageBytes = baos.toByteArray();
                                if (imageBytes.length > largestSize) {
                                    largestImage = imageBytes;
                                    largestSize = imageBytes.length;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        this.tracksCount = count;

        if (largestImage != null) {
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(
                    largestImage, 0, largestImage.length);
            if (bitmap != null) {
                String suffix = "_" + archiveFile.getUri().hashCode();
                this.coverImagePath = ImageHelper.saveTempBitmap(context, bitmap, suffix);
                if (this.coverImagePath != null && listener != null) {
                    listener.onCoverFound(this.coverImagePath);
                }
            }
        }
    }

    private void scanZipCombined(Context context, DocumentFile file, OnMetadataListener listener) {
        int trackCount = 0;
        byte[] largestImage = null;
        long largestSize = 0;

        try {
            java.io.InputStream inputStream = context.getContentResolver().openInputStream(file.getUri());
            if (inputStream != null) {
                try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(inputStream)) {
                    java.util.zip.ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (Thread.currentThread().isInterrupted()) {
                            myLogD("scanZipCombined interrupted");
                            return;
                        }
                        if (!entry.isDirectory()) {
                            String entryName = entry.getName();
                            String lowName = entryName.toLowerCase();

                            // 1. Track Count
                            if (isAudioFileName(entry.getName())) { // isAudioFileName uses getExt
                                trackCount++;
                                String entryNameFull = entry.getName();
                                String fileName = new java.io.File(entryNameFull).getName();
                                String displayName = com.driot.bookplayer.utils.Tonio.formatNameForDisplay(fileName);
                                String tName = trackCount + ". " + displayName;
                                trackList.add(tName);
                                if (listener != null)
                                    listener.onTrackFound(tName);
                            }

                            // 2. Cover Detection
                            if (lowName.endsWith(".jpg") || lowName.endsWith(".jpeg") ||
                                    lowName.endsWith(".png") || lowName.endsWith(".webp")) {

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
                                }
                            }
                        }
                        zis.closeEntry();
                    }
                }
            }

            this.tracksCount = trackCount;

            if (largestImage != null) {
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(
                        largestImage, 0, largestImage.length);
                if (bitmap != null) {
                    String suffix = "_" + file.getUri().hashCode();
                    this.coverImagePath = ImageHelper.saveTempBitmap(context, bitmap, suffix);
                    if (this.coverImagePath != null && listener != null) {
                        listener.onCoverFound(this.coverImagePath);
                    }
                }
            }

        } catch (Exception ignored) {
            myLogEE(ignored, "Error scanning zip");
        }
    }

    private void scanFolderCombined(Context context, DocumentFile rootDir, OnMetadataListener listener) {
        long startTime = System.currentTimeMillis();
        myLogD("scanFolderCombined() START for: " + name);

        // 1. Initial Cover Detection (Root only, external images) - this is fast
        this.coverImagePath = detectCoverForFolderExternally(context, rootDir);
        if (this.coverImagePath != null && listener != null) {
            listener.onCoverFound(this.coverImagePath);
        }

        // 2. Recursive Scan
        trackList.clear();
        FolderScanState state = new FolderScanState();
        scanFolderRecursive(context, rootDir, listener, state);

        this.size = state.totalSize;
        this.tracksCount = state.audioCount;
        this.multipleBooksCount = state.ebookCount + state.bundleCount;
        this.hasOnlyZipFilesInFolder = state.bundleCount >= 1 && state.audioCount == 0;

        // Infer playType
        boolean hasRealContent = state.audioCount > 0 || state.ebookCount > 0 || state.bundleCount > 0;
        if (hasRealContent) {
            this.playType = Var.PLAY_TYPE_AUDIO;
        } else if (state.plainTextCount > 0) {
            this.playType = Var.PLAY_TYPE_TEXT;
        }

        // If still no cover, we might have found one embedded during the recursive scan
        if (this.coverImagePath == null && state.embeddedCoverPath != null) {
            this.coverImagePath = state.embeddedCoverPath;
            if (listener != null)
                listener.onCoverFound(this.coverImagePath);
        }

        myLogD("scanFolderCombined() DONE in " + (System.currentTimeMillis() - startTime) + "ms. " +
                "tracks=" + tracksCount + ", size=" + size + ", multipleBooks=" + multipleBooksCount);
    }

    private void scanFolderRecursive(Context context, DocumentFile dir, OnMetadataListener listener,
            FolderScanState state) {
        DocumentFile[] files = dir.listFiles();
        if (files == null)
            return;

        // Sort files to ensure deterministic track order (matching original logic)
        java.util.Arrays.sort(files, (f1, f2) -> {
            String n1 = f1 != null ? f1.getName() : "";
            String n2 = f2 != null ? f2.getName() : "";
            return String.CASE_INSENSITIVE_ORDER.compare(n1 != null ? n1 : "", n2 != null ? n2 : "");
        });

        for (DocumentFile child : files) {
            if (Thread.currentThread().isInterrupted())
                return;

            if (child.isDirectory()) {
                scanFolderRecursive(context, child, listener, state);
            } else {
                state.totalSize += child.length();

                String special = SupportedFilesHelper.getSpecialType(child);
                if (SupportedFilesHelper.isAudio(child) || SupportedFilesHelper.isVideo(child)) {
                    state.audioCount++;

                    // Track List for display
                    String displayName = com.driot.bookplayer.utils.Tonio.formatNameForDisplay(safeName(child));
                    String tName = state.audioCount + ". " + displayName;
                    trackList.add(tName);
                    if (listener != null)
                        listener.onTrackFound(tName);

                    // Embedded Cover Detection (if not found yet)
                    if (this.coverImagePath == null && state.embeddedCoverPath == null) {
                        state.embeddedCoverPath = extractEmbeddedCoverFromFile(context, child);
                        if (state.embeddedCoverPath != null && listener != null) {
                            listener.onCoverFound(state.embeddedCoverPath);
                        }
                    }
                } else if (SupportedFilesHelper.isSplittableEbookSpecial(special)) {
                    state.ebookCount++;
                } else if (SupportedFilesHelper.isBundleSpecial(special)) {
                    state.bundleCount++;
                } else if (SupportedFilesHelper.isText(child)) {
                    state.plainTextCount++;
                }
            }
        }
    }

    private static class FolderScanState {
        long totalSize = 0;
        int audioCount = 0;
        int ebookCount = 0;
        int bundleCount = 0;
        int plainTextCount = 0;
        String embeddedCoverPath = null;
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

    private String detectCoverForArchive(Context context, DocumentFile file) {
        try {
            java.io.InputStream inputStream = context.getContentResolver().openInputStream(file.getUri());
            if (inputStream != null) {
                java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(inputStream);
                java.util.zip.ZipEntry entry;

                byte[] largestImage = null;
                long largestSize = 0;

                while ((entry = zis.getNextEntry()) != null) {
                    if (!entry.isDirectory()) {
                        String entryName = entry.getName().toLowerCase();
                        myLogD("detectCoverForZip : " + entryName);
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
                        return ImageHelper.saveTempBitmap(context,
                                bitmap, suffix);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
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

    private String detectCoverForFolderExternally(Context context, DocumentFile folder) {
        try {
            CoverPictureDetection.CoverDetectionResult result = CoverPictureDetection.detectCoverFromFolder(context,
                    folder, null);

            if (result != null && result.imagePath != null) {
                return result.imagePath;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String extractEmbeddedCoverFromFile(Context context, DocumentFile file) {
        try {
            android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
            try {
                mmr.setDataSource(context, file.getUri());
                CoverPictureDetection.CoverDetectionResult result = CoverPictureDetection.extractEmbeddedCover(mmr);

                if (result != null && result.bitmap != null) {
                    String suffix = "_" + file.getUri().hashCode();
                    return ImageHelper.saveTempBitmap(context,
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
                        return ImageHelper.saveTempBitmap(context,
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
                    java.util.Map<String, byte[]> zip = EpubCommonHelper.readZip(
                            file.getUri(), context);
                    byte[] containerXml = zip.get("META-INF/container.xml");
                    if (containerXml != null) {
                        String opfPath = EpubCommonHelper.findOpfPath(containerXml);
                        byte[] opfBytes = zip.get(opfPath);
                        if (opfBytes != null) {
                            EpubLowLevelHelper.OpfInfo opf = EpubLowLevelHelper
                                    .parseOpf(opfBytes);
                            opf.opfPath = opfPath;
                            CoverPictureDetection.CoverDetectionResult result = CoverPictureDetection
                                    .detectCoverFromEpub(zip, opf);
                            if (result != null && result.bitmap != null) {
                                String suffix = "_" + file.getUri().hashCode();
                                return ImageHelper.saveTempBitmap(context,
                                        result.bitmap, suffix);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            } else if (fileName.endsWith(".fb2")) {
                try {
                    String xml = Fb2LowLevelHelper.readAllText(context, file.getUri());
                    Fb2LowLevelHelper.Meta meta = Fb2LowLevelHelper
                            .parseMetaAndBinaries(xml);

                    myLogD("FB2 cover detection - coverImageId: [" + meta.coverImageId
                            + "], binaries count: " + meta.binaries.size());

                    byte[] imageBytes = null;

                    if (meta.coverImageId != null && !meta.coverImageId.isEmpty()) {
                        // Important: binaries are stored with lowercase keys
                        String lookupKey = meta.coverImageId.toLowerCase(java.util.Locale.ROOT);
                        imageBytes = meta.binaries.get(lookupKey);

                        myLogD("FB2 - Looking up cover with key: [" + lookupKey + "], found: " + (imageBytes != null));
                    } else if (!meta.binaries.isEmpty()) {
                        // Fallback: No explicit cover defined
                        // 1. Try to find image with 'cover' in the name
                        String coverKey = null;
                        for (String key : meta.binaries.keySet()) {
                            if (key.toLowerCase().contains("cover")) {
                                coverKey = key;
                                myLogD("FB2 - Found image with 'cover' in name: " + key);
                                break;
                            }
                        }

                        if (coverKey != null) {
                            imageBytes = meta.binaries.get(coverKey);
                        } else {
                            // 2. Use the first valid image (size > 2KB) as fallback
                            myLogD("FB2 - No cover-named image, scanning for first valid image (>2KB)");
                            for (java.util.Map.Entry<String, byte[]> entry : meta.binaries.entrySet()) {
                                int size = entry.getValue().length;
                                // Filter out tiny images (icons, spacers) - 2KB threshold
                                if (size > 2048) {
                                    imageBytes = entry.getValue();
                                    myLogD("FB2 - Found candidate image: " + entry.getKey() + " (" + size + " bytes)");
                                    break;
                                }
                            }

                            // 3. Last resort: just take the very first image if nothing else matched
                            if (imageBytes == null && !meta.binaries.isEmpty()) {
                                imageBytes = meta.binaries.values().iterator().next();
                                myLogD("FB2 - No image > 2KB found, taking first available image");
                            }
                        }
                    }

                    if (imageBytes != null) {
                        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(
                                imageBytes, 0, imageBytes.length);
                        if (bitmap != null) {
                            myLogD("FB2 - Successfully decoded cover bitmap: " + bitmap.getWidth() + "x"
                                    + bitmap.getHeight());
                            String suffix = "_" + file.getUri().hashCode();
                            return ImageHelper.saveTempBitmap(context,
                                    bitmap, suffix);
                        } else {
                            myLogD("FB2 - Failed to decode bitmap from bytes");
                        }
                    }
                } catch (Exception e) {
                    myLogEE(e, "FB2 cover detection error");
                }
            }
        }

        if ("Archive".equals(type)) {
            try {
                return detectCoverForArchive(context, file);
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    public boolean hasMultipleBooksInFolder() {
        return multipleBooksCount >= 2 || hasOnlyZipFilesInFolder;
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
        return "[" + sourceType + "] " + name;
    }

    private void myLogD(String txt) {
        if (LOG_DEBUG)
            KanLogger.myLogD(txt);
    }

    private String extractCleanChapterTitle(com.googlecode.mp4parser.authoring.Sample sample) {
        java.nio.ByteBuffer buffer = sample.asByteBuffer();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        if (data.length < 2)
            return "chapter";
        String raw = new String(java.util.Arrays.copyOfRange(data, 2, data.length),
                java.nio.charset.StandardCharsets.UTF_8);
        raw = raw.replaceAll("encd.*$", "")
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")
                .replace("\uFEFF", "")
                .trim();
        return raw.isEmpty() ? "chapter" : raw;
    }
}
