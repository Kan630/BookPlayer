package com.driot.bookplayer.imports;

import android.content.Context;
import android.net.Uri;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.driot.bookplayer.R;
import com.driot.bookplayer.ebooks.DocxLowLevelHelper;
import com.driot.bookplayer.ebooks.EpubCommonHelper;
import com.driot.bookplayer.ebooks.EpubLowLevelHelper;
import com.driot.bookplayer.ebooks.Fb2LowLevelHelper;
import com.driot.bookplayer.ebooks.HtmlLowLevelHelper;
import com.driot.bookplayer.ebooks.OdtLowLevelHelper;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.CoverPictureDetection;
import com.driot.bookplayer.helpers.ImageHelper;
import com.driot.bookplayer.helpers.SupportedFilesHelper;
import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.utils.HashWorker;
import com.driot.bookplayer.utils.Tonio;
import com.googlecode.mp4parser.DataSource;
import com.googlecode.mp4parser.FileDataSourceImpl;
import com.googlecode.mp4parser.FileDataSourceViaHeapImpl;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Sample;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;

import com.driot.bookplayer.objects.AudioFileInfo;
import com.driot.bookplayer.objects.AudioInfo;
import com.driot.bookplayer.objects.AudioProber;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

public class BookCandidate implements Parcelable {
    private boolean VERBOSE_DEBUG = false;

    public Uri uri;
    public String name;
    public String sourceType; // Folder, Archive, M4B, EPUB, Audio File
    public String path; // For display
    public long size;
    public String originalFile;
    public String fileExtension;
    public String playType;
    public String specialType;

    public int multipleBooksCount = 0;
    public boolean hasOnlyZipFilesInFolder = false;
    public String originalHash; // Computed during scanning
    public String existingBookName; // Name of book if hash already exists in DB (null if not imported)

    public String audioBookName = "init..."; // formatted for display
    public int tracksCount;
    // public final List<String> trackList = new ArrayList<>();
    private ArrayList<AudioFileInfo> audioFileInfoArrayList = new ArrayList<>();
    public String coverImagePath; // Path to detected cover image (null if none)

    public String sourceLocation = "sourceLocation...";
    public String mimeType;
    public String infoMimeExtension = "init...";
    public String infoMimeExtensionSmall = "init...";
    public String infoSourceLocation = "infoSourceLocation...";
    public String infoLine1 = "init...";

    private boolean selected; // User selection for mass import (false if already imported)

    public boolean isBroken = false;
    public boolean isMimeSupported = true;
    public boolean isHeavyLoaded = false;
    public boolean isCalculating = false; // UI state for heavy load progress
    public boolean isEasyLoaded = false;
    public boolean isSizeCalculated = false;
    public boolean isEasyCalculating = false; // UI state for easy load progress

    public interface OnMetadataListener {
        void onTrackFound(AudioFileInfo info);

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
        isEasyLoaded = in.readByte() != 0;
        isSizeCalculated = in.readByte() != 0;
        isEasyCalculating = in.readByte() != 0;
        audioBookName = in.readString();
        multipleBooksCount = in.readInt();
        hasOnlyZipFilesInFolder = in.readByte() != 0;
        originalFile = in.readString();
        fileExtension = in.readString();
        mimeType = in.readString();
        specialType = in.readString();
        // in.readStringList(trackList);
        audioFileInfoArrayList = in.createTypedArrayList(AudioFileInfo.CREATOR);
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
        dest.writeByte((byte) (isEasyLoaded ? 1 : 0));
        dest.writeByte((byte) (isSizeCalculated ? 1 : 0));
        dest.writeByte((byte) (isEasyCalculating ? 1 : 0));
        dest.writeString(audioBookName);
        dest.writeInt(multipleBooksCount);
        dest.writeByte((byte) (hasOnlyZipFilesInFolder ? 1 : 0));
        dest.writeString(originalFile);
        dest.writeString(fileExtension);
        dest.writeString(mimeType);
        dest.writeString(specialType);
        // dest.writeStringList(trackList);
        dest.writeTypedList(audioFileInfoArrayList);
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

    public ArrayList<AudioFileInfo> getAudioFileInfoArrayList() {
        return audioFileInfoArrayList;
    }

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

        initializeTypes();

        this.path = this.name; // Default path
        this.selected = true;

        if (this.sourceType == null) {
            // throw new RuntimeException("constructor : sourceType is null");
            myLogW("constructor : sourceType is null for " + this.name);
            this.isMimeSupported = false;
        }

        // Legacy: synchronous load for backward compatibility?
        // Let's keep it but ideally we should move away from it.
        loadEasyMetadata(context);
    }

    /**
     * Lightweight constructor for MassImportScanner.
     * Identification phase only.
     */
    public BookCandidate(Context context, Uri uri, String name, String sourceType, long size) {
        this.uri = uri;
        this.name = name;
        this.sourceType = sourceType;
        this.size = size;
        if (size >= 0) {
            this.isSizeCalculated = true;
        }

        initializeTypes();

        this.path = this.name;
        this.selected = true;
    }

    private void initializeTypes() {
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
    }

    /**
     * Secondary initialization for heavy operations (zip scanning).
     * Call this from a background thread after the initial fast load.
     */
    public void loadHeavyMetadata(Context context) {
        loadHeavyMetadata(context, null);
    }

    public void loadEasyMetadata(Context context) {
        if (isEasyLoaded)
            return;

        long startTime = System.currentTimeMillis();
        myLogD("loadEasyMetadata() START for: " + name);

        DocumentFile file = UriHelper.getDocumentFileFromAnyUri(context, uri);
        if (file == null) {
            String scheme = uri != null ? uri.getScheme() : null;
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                return;
            }
        }

        myLogD("document file ok (or web link)");
        // Calculate fields
        this.originalHash = computeHash(context, uri);
        myLogD("Hash ok");

        if ("Folder".equals(sourceType)) {
            if (!isSizeCalculated) {
                this.size = -1; // Defer calculation to heavy phase
            }
            this.existingBookName = checkFolderAlreadyImported(context, uri.toString(), originalHash);
            myLogD("checkFolderAlreadyImported ok");

            this.audioBookName = getBookName_with2folders(uri.getPath(), false);
            myLogD("getBookName_with2folders ok");
        } else {
            // Calculate fields (files are fast)
            if (!isSizeCalculated) {
                this.size = file != null ? file.length() : -1;
                this.isSizeCalculated = true;
            }
            myLogD("calculateSize ok");

            this.existingBookName = checkHashExists(context, originalHash);
            myLogD("checkHashExists ok");

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
            } else {
                this.isMimeSupported = true; // explicitly set if supported
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
            this.infoMimeExtension = "[" + "Folder" + "]";
            this.infoMimeExtensionSmall = "init...";
        } else {
            this.playType = SupportedFilesHelper.getPlayType(name);
            this.infoMimeExtension = "[" + specialType + "] :    [" + mimeType + "] - [." + fileExtension + "]";
            this.infoMimeExtensionSmall = "[" + mimeType + "] - [." + fileExtension + "]";
        }

        isEasyLoaded = true;
        myLog("loadEasyMetadata() TOTAL: " + (System.currentTimeMillis() - startTime) + "ms for: " + name);
        myLog("--------------------");

    }

    /**
     * Heavy initialization: scans zip for covers, counts tracks.
     * This is blocking and should be called in background.
     */
    public void loadHeavyMetadata(Context context, OnMetadataListener listener) {
        long startTime = System.currentTimeMillis();
        myLogD("loadHeavyMetadata() START for: " + name);

        DocumentFile file = UriHelper.getDocumentFileFromAnyUri(context, uri);
        if (file == null) {
            String scheme = uri != null ? uri.getScheme() : null;
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                return;
            }
        }

        if ("Folder".equals(sourceType)) {
            // HEAVY PHASE
            if (Thread.currentThread().isInterrupted())
                return;

            if (file != null) {
                scanFolderCombined(context, file, listener);
            }
        } else {
            // HEAVY PHASE
            if (Thread.currentThread().isInterrupted())
                return;

            if (file != null) {
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
            } else {
                this.tracksCount = 1;
            }
        }

        this.isHeavyLoaded = true;

        myLog("loadHeavyMetadata() DONE: " + (System.currentTimeMillis() - startTime) + "ms for: "
                + name);
        myLogDD("--------------------");
        for (AudioFileInfo afi : audioFileInfoArrayList) {
            myLogDD(afi.toString());
        }
        myLog("--------------------");
    }

    private void scanM4BCombined(Context context, DocumentFile file, OnMetadataListener listener) {
        long startTime = System.currentTimeMillis();
        myLogD("scanM4BCombined() START for: " + name);

        try (android.content.res.AssetFileDescriptor afd = context.getContentResolver()
                .openAssetFileDescriptor(file.getUri(), "r")) {
            if (afd != null) {
                java.io.FileDescriptor fd = afd.getFileDescriptor();
                long offset = afd.getStartOffset();
                long length = afd.getLength();

                // 1. Cover Detection
                android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
                try {
                    mmr.setDataSource(fd, offset, length);
                    CoverPictureDetection.CoverDetectionResult result = CoverPictureDetection
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
                // Reset it to the starting offset before giving it to mp4parser.
                try {
                    android.system.Os.lseek(fd, offset, android.system.OsConstants.SEEK_SET);
                } catch (Exception e) {
                    myLogW("Could not seek FD back to offset " + offset + " : " + e.getMessage());
                }

                // 2. Track Count
                try (java.nio.channels.FileChannel channel = new java.io.FileInputStream(fd).getChannel()) {
                    // Try refreshing path if possible to use FileDataSourceViaHeapImpl as it was
                    // working before
                    String directPath = UriHelper.getPathFromUri(context, file.getUri());
                    if (directPath != null && new java.io.File(directPath).exists()) {
                        dataSource = new FileDataSourceViaHeapImpl(directPath);
                    } else {
                        // Fallback to channel based datasource (with offset 0)
                        dataSource = new FileDataSourceImpl(channel);
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
                        List<Sample> samples = chapterTrack.getSamples();
                        long[] sampleDurations = chapterTrack.getSampleDurations();
                        long timescale = chapterTrack.getTrackMetaData().getTimescale();

                        int chapterCount = samples.size();
                        this.tracksCount = chapterCount > 0 ? chapterCount : 1;

                        for (int i = 0; i < chapterCount; i++) {
                            long durationMs = 0;
                            if (sampleDurations != null && i < sampleDurations.length && timescale != 0) {
                                durationMs = (sampleDurations[i] * 1000) / timescale;
                            }

                            String title = extractCleanChapterTitle(samples.get(i));
                            String tName = (i + 1) + ". " + title;
                            // trackList.add(tName);
                            AudioFileInfo afi = new AudioFileInfo(tName, tName, durationMs, 0, file.getUri().toString(), null);
                            audioFileInfoArrayList.add(afi);
                            if (listener != null) {
                                listener.onTrackFound(afi);
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

    private DataSource dataSource; // Temporary helper for scanM4BCombined

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
            scanZipCombined(context, archiveFile, listener);
        }

        myLogD("scanArchiveCombined() DONE in " + (System.currentTimeMillis() - startTime) + "ms. tracks="
                + this.tracksCount);
    }

    private void scan7ZCombined(Context context, DocumentFile archiveFile, OnMetadataListener listener) {
        int audioCount = 0;
        int txtCount = 0;
        int bigEbookCount = 0;
        String firstBigEbookName = null;
        List<AudioFileInfo> ebookFileInfos = new java.util.ArrayList<>();

        byte[] largestImage = null;
        long largestSize = 0;

        try {
            try (android.os.ParcelFileDescriptor pfd = context.getContentResolver()
                    .openFileDescriptor(archiveFile.getUri(), "r")) {
                if (pfd != null) {
                    try (FileChannel channel = new FileInputStream(
                            pfd.getFileDescriptor()).getChannel()) {
                        try (SevenZFile sevenZFile = new SevenZFile(
                                channel)) {
                            SevenZArchiveEntry entry;
                            while ((entry = sevenZFile.getNextEntry()) != null) {
                                if (Thread.currentThread().isInterrupted())
                                    return;
                                String entryName = entry.getName();
                                if (entry.isDirectory())
                                    continue;

                                String lowName = entryName.toLowerCase(Locale.ROOT);
                                String ext = getExt(lowName);

                                // 1. Track counting
                                if (isAudioFileName(entryName)) {
                                    audioCount++;
                                    String fileNameOnly = new File(entryName).getName();
                                    String displayName = Tonio
                                            .formatNameForDisplay(fileNameOnly);
                                    String tName = audioCount + ". " + displayName;
                                    long entrySize = entry.getSize();
                                    AudioFileInfo afi = new AudioFileInfo(fileNameOnly, tName, 0,
                                            entrySize,
                                            archiveFile.getUri().toString(), null);
                                    audioFileInfoArrayList.add(afi);
                                    if (listener != null) {
                                        listener.onTrackFound(afi);
                                    }
                                } else if (Var.SUPPORTED_EBOOK_EXTENSIONS.contains(ext)) {
                                    String fileName = new java.io.File(entryName).getName();
                                    String displayName = Tonio.formatNameForDisplay(fileName);
                                    AudioFileInfo afi = new AudioFileInfo(fileName, displayName, 0, entry.getSize(), archiveFile.getUri().toString(), null);
                                    ebookFileInfos.add(afi);

                                    if (SupportedFilesHelper.isSplittableEbookSpecial(SupportedFilesHelper.getSpecialTypeFromExt(ext))) {
                                        bigEbookCount++;
                                        if (firstBigEbookName == null) firstBigEbookName = entryName;
                                    } else {
                                        txtCount++;
                                    }
                                }

                                // 2. Cover detection
                                if (Var.SUPPORTED_IMAGE_EXTENSIONS.contains(ext)) {
                                    long size = entry.getSize();
                                    if (size > largestSize || (size == -1 && largestImage == null)) {
                                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                                        byte[] buffer = new byte[8192];
                                        int len;
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

                            handleArchiveContents(context, audioCount, txtCount, bigEbookCount, firstBigEbookName, ebookFileInfos, archiveFile, listener);

                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (largestImage != null && this.coverImagePath == null) {
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
        int audioCount = 0;
        int txtCount = 0;
        int bigEbookCount = 0;
        String firstBigEbookName = null;
        List<AudioFileInfo> ebookFileInfos = new java.util.ArrayList<>();

        byte[] largestImage = null;
        long largestSize = 0;

        try {
            java.io.InputStream inputStream = context.getContentResolver().openInputStream(archiveFile.getUri());
            if (inputStream != null) {
                try (java.io.InputStream bis = new java.io.BufferedInputStream(inputStream);
                        java.io.InputStream cis = maybeWrapCompressor(bis);
                        TarArchiveInputStream tis = new TarArchiveInputStream(
                                cis)) {
                    TarArchiveEntry entry;
                    while ((entry = tis.getNextTarEntry()) != null) {
                        if (Thread.currentThread().isInterrupted())
                            return;
                        String entryName = entry.getName();
                        if (entry.isDirectory())
                            continue;

                        String lowName = entryName.toLowerCase(Locale.ROOT);
                        String ext = getExt(lowName);

                        // 1. Track counting
                        if (isAudioFileName(entryName)) {
                            audioCount++;
                            String fileNameOnly = new java.io.File(entryName).getName();
                            String displayName = Tonio.formatNameForDisplay(fileNameOnly);
                            String tName = audioCount + ". " + displayName;
                            long entrySize = entry.getSize();
                            AudioFileInfo afi = new AudioFileInfo(fileNameOnly, tName, 0, entrySize,
                                    archiveFile.getUri().toString(), null);
                            audioFileInfoArrayList.add(afi);
                            if (listener != null) {
                                listener.onTrackFound(afi);
                            }
                        } else if (Var.SUPPORTED_EBOOK_EXTENSIONS.contains(ext)) {
                            String fileName = new java.io.File(entryName).getName();
                            String displayName = Tonio.formatNameForDisplay(fileName);
                            AudioFileInfo afi = new AudioFileInfo(fileName, displayName, 0, entry.getSize(), archiveFile.getUri().toString(), null);
                            ebookFileInfos.add(afi);

                            if (SupportedFilesHelper.isSplittableEbookSpecial(SupportedFilesHelper.getSpecialTypeFromExt(ext))) {
                                bigEbookCount++;
                                if (firstBigEbookName == null) firstBigEbookName = entryName;
                            } else {
                                txtCount++;
                            }
                        }

                        // 2. Cover detection
                        if (Var.SUPPORTED_IMAGE_EXTENSIONS.contains(ext)) {
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

                    handleArchiveContents(context, audioCount, txtCount, bigEbookCount, firstBigEbookName, ebookFileInfos, archiveFile, listener);
                }
            }
        } catch (Exception ignored) {
        }

        if (largestImage != null && this.coverImagePath == null) {
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
        int audioCount = 0;
        int txtCount = 0;
        int pureEbookCount = 0;
        String firstPureEbookName = null;
        List<AudioFileInfo> ebookFileInfos = new java.util.ArrayList<>();

        byte[] largestImage = null;
        long largestSize = 0;

        try {
            try (ParcelFileDescriptor pfd = context.getContentResolver()
                    .openFileDescriptor(file.getUri(), "r")) {
                if (pfd != null) {
                    try (FileChannel channel = new FileInputStream(
                            pfd.getFileDescriptor()).getChannel()) {
                        try (ZipFile zipFile = new ZipFile(
                                channel)) {
                            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
                            while (entries.hasMoreElements()) {
                                if (Thread.currentThread().isInterrupted()) {
                                    myLogD("scanZipCombined interrupted");
                                    return;
                                }
                                ZipArchiveEntry entry = entries.nextElement();
                                if (entry.isDirectory())
                                    continue;

                                String entryName = entry.getName();
                                String lowName = entryName.toLowerCase(Locale.ROOT);
                                String ext = getExt(lowName);

                                // 1. Track Count
                                if (Var.SUPPORTED_AUDIO_EXTENSIONS.contains(ext)) {
                                    audioCount++;
                                    long size = entry.getSize();
                                    String fileName = new File(entryName).getName();
                                    String displayName = Tonio.formatNameForDisplay(fileName);
                                    String tName = audioCount + ". " + displayName;
                                    AudioFileInfo afi = new AudioFileInfo(fileName, tName, 0, size,
                                            file.getUri().toString(), null);
                                    audioFileInfoArrayList.add(afi);
                                    if (listener != null)
                                        listener.onTrackFound(afi);

                                } else if (Var.SUPPORTED_EBOOK_EXTENSIONS.contains(ext)) {
                                    String fileName = new File(entryName).getName();
                                    String displayName = Tonio.formatNameForDisplay(fileName);
                                    AudioFileInfo afi = new AudioFileInfo(fileName, displayName, 0, entry.getSize(), file.getUri().toString(), null);
                                    ebookFileInfos.add(afi);

                                    if (SupportedFilesHelper.isPureEbook(ext)) {
                                        pureEbookCount++;
                                        if (firstPureEbookName == null) firstPureEbookName = entryName;
                                    } else {
                                        txtCount++;
                                    }
                                } else if (Var.SUPPORTED_IMAGE_EXTENSIONS.contains(ext)) {
                                    long size = entry.getSize();
                                    if (size > largestSize || (size == -1 && largestImage == null)) {
                                        try (InputStream eis = zipFile.getInputStream(entry)) {
                                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                            byte[] buffer = new byte[8192];
                                            int len;
                                            while ((len = eis.read(buffer)) > 0) {
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

                            handleArchiveContents(context, audioCount, txtCount, pureEbookCount, firstPureEbookName, ebookFileInfos, file, listener);
                        }
                    }
                }
            }

            if (largestImage != null && this.coverImagePath == null) {
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

    private void handleArchiveContents(Context context, int audioCount, int txtCount, int bigEbookCount, String firstBigEbookName, List<AudioFileInfo> ebookFileInfos, DocumentFile archiveFile, OnMetadataListener listener) {
        //If any audio, don't bother about potential ebooks
        if (audioCount > 0) {
            this.tracksCount = audioCount;
            return;
        }

        if (bigEbookCount == 1) {
            // Check first ebook to get its chapters
            myLogD("Archive contains exactly one big ebook: " + firstBigEbookName);

            //Here need to replace the zip bookCandidate by this ebook bookCandidate
            //meaning, changing the pointer....

            myLogW("-------------------------------------");
            myLogW("Here need to replace the zip bookCandidate by this ebook bookCandidate...");
            myLogW("meaning, changing the pointer....");
            myLogW("-------------------------------------");


            //this.specialType = "Ebook Archive";
            extractAndScanSingleEbook(context, firstBigEbookName, archiveFile, listener);

        } else if (bigEbookCount > 1) {
            this.multipleBooksCount = bigEbookCount;
            //.....toremove...this.tracksCount = bigEbookCount;
            //.....toremove...this.specialType = "Multiple Ebooks";
            myLogW("-------------------------------------");
            myLogW("Here we should someway manage to split into multiple bookCandidate...   -  its mass import...");
            myLogW("-------------------------------------");

        } else if (txtCount > 0) {
            // Multiple simple txt documents => "1 item = 1 chapter"
            myLogW("Archive of multiple simple txt documents: " + (txtCount + bigEbookCount));
            boolean isComplex = false;
            for (int i = 0; i < ebookFileInfos.size(); i++) {
                AudioFileInfo afi = ebookFileInfos.get(i);
                String ext = Tonio.getExtension(afi.getFileName());
                if (!ext.equalsIgnoreCase("txt")) {
                    isComplex = true;
                    break;
                }
            }
            if (isComplex) {
                myLogW("-------------------------------------");
                myLogW("Here a parsing will be needed to turn txt document (like docx or odt) into .txt");
                myLogW("-------------------------------------");
            }
            this.tracksCount = ebookFileInfos.size();
            Collections.sort(ebookFileInfos, (a, b) -> a.getDisplayPath().compareToIgnoreCase(b.getDisplayPath()));
            for (int i = 0; i < ebookFileInfos.size(); i++) {
                AudioFileInfo afi = ebookFileInfos.get(i);
                String numberedName = (i + 1) + ". " + afi.getDisplayPath();
                AudioFileInfo newAfi = new AudioFileInfo(afi.getFileName(), numberedName, afi.getDuration(), afi.getSize(), afi.getContentUri(), afi.getMeta());
                audioFileInfoArrayList.add(newAfi);
                if (listener != null) listener.onTrackFound(newAfi);
            }


            //.....toremove...this.specialType = "Text Archive";
            
            // Sort them by name to have a consistent order
        } else {
            this.tracksCount = 0;
        }
    }

    private void extractAndScanSingleEbook(Context context, String entryName, DocumentFile archiveFile, OnMetadataListener listener) {
        String ext = getExt(entryName.toLowerCase(Locale.ROOT));
        String special = SupportedFilesHelper.getSpecialTypeFromExt(ext);

        File tempFile = new File(context.getCacheDir(), "scan_temp_" + System.currentTimeMillis() + "." + ext);
        try {
            // 1. Extract to temp
            if (extractFileFromArchive(context, archiveFile, entryName, tempFile)) {
                Uri tempUri = Uri.fromFile(tempFile);

                // 2. Scan chapters based on type
                if (SupportedFilesHelper.SPECIAL_TYPE_EPUB.equals(special)) {
                    EpubLowLevelHelper.ExtractResult res = EpubLowLevelHelper.extractAll(context, tempUri);
                    populateFromEbookResult(res, listener);
                } else if (SupportedFilesHelper.SPECIAL_TYPE_FB2.equals(special)) {
                    Fb2LowLevelHelper.ExtractResult res = Fb2LowLevelHelper.extractAll(context, tempUri);
                    populateFromEbookResult(res, listener);
                } else if (SupportedFilesHelper.SPECIAL_TYPE_ODT.equals(special)) {
                    OdtLowLevelHelper.ExtractResult res = OdtLowLevelHelper.extractAll(context, tempUri);
                    populateFromEbookResult(res, listener);
                } else if (SupportedFilesHelper.SPECIAL_TYPE_DOCX.equals(special)) {
                    DocxLowLevelHelper.ExtractResult res = DocxLowLevelHelper.extractAll(context, tempUri, false);
                    populateFromEbookResult(res, listener);
                } else if (SupportedFilesHelper.SPECIAL_TYPE_HTML.equals(special)) {
                    HtmlLowLevelHelper.ExtractResult res = HtmlLowLevelHelper.extractAll(context, tempUri);
                    populateFromEbookResult(res, listener);
                }
            }
        } catch (Exception e) {
            myLogEE(e, "Error scanning ebook inside archive: " + entryName);
        } finally {
            if (tempFile.exists())
                tempFile.delete();
        }
    }

    private void populateFromEbookResult(Object result, OnMetadataListener listener) {
        if (result == null)
            return;

        List<File> chapters = null;
        Map<String, String> titles = null;
        String bookTitle = null;

        try {
            if (result instanceof EpubLowLevelHelper.ExtractResult) {
                EpubLowLevelHelper.ExtractResult r = (EpubLowLevelHelper.ExtractResult) result;
                chapters = r.chapterFiles;
                titles = r.trackTitles;
                bookTitle = r.bookTitle;
            } else if (result instanceof Fb2LowLevelHelper.ExtractResult) {
                Fb2LowLevelHelper.ExtractResult r = (Fb2LowLevelHelper.ExtractResult) result;
                chapters = r.chapterFiles;
                titles = r.trackTitles;
                bookTitle = r.bookTitle;
            } else if (result instanceof OdtLowLevelHelper.ExtractResult) {
                OdtLowLevelHelper.ExtractResult r = (OdtLowLevelHelper.ExtractResult) result;
                chapters = r.chapterFiles;
                titles = r.trackTitles;
                bookTitle = r.bookTitle;
            } else if (result instanceof DocxLowLevelHelper.ExtractResult) {
                DocxLowLevelHelper.ExtractResult r = (DocxLowLevelHelper.ExtractResult) result;
                chapters = r.chapterFiles;
                titles = r.trackTitles;
                bookTitle = r.bookTitle;
            } else if (result instanceof HtmlLowLevelHelper.ExtractResult) {
                HtmlLowLevelHelper.ExtractResult r = (HtmlLowLevelHelper.ExtractResult) result;
                chapters = r.chapterFiles;
                titles = r.trackTitles;
                bookTitle = r.bookTitle;
            }
        } catch (Exception ignored) {
        }

        if (chapters != null) {
            this.tracksCount = chapters.size();
            this.audioBookName = Tonio.formatNameForDisplay(bookTitle != null ? bookTitle : this.name);
            this.specialType = "Ebook Archive";

            for (int i = 0; i < chapters.size(); i++) {
                File f = chapters.get(i);
                String title = titles != null ? titles.get(f.getName()) : f.getName();
                String tName = (i + 1) + ". " + title;
                AudioFileInfo afi = new AudioFileInfo(f.getName(), tName, 0, f.length(), this.uri.toString(), null);
                audioFileInfoArrayList.add(afi);
                if (listener != null)
                    listener.onTrackFound(afi);
            }
        }
    }

    private boolean extractFileFromArchive(Context context, DocumentFile archiveFile, String entryName, File destFile) {
        String ext = getExt(archiveFile.getName().toLowerCase(Locale.ROOT));
        try {
            try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(archiveFile.getUri(), "r")) {
                if (pfd == null)
                    return false;
                try (FileChannel channel = new FileInputStream(pfd.getFileDescriptor()).getChannel()) {
                    if (ext.equals("7z")) {
                        try (SevenZFile archive = new SevenZFile(channel)) {
                            SevenZArchiveEntry entry;
                            while ((entry = archive.getNextEntry()) != null) {
                                if (entry.getName().equals(entryName)) {
                                    try (FileOutputStream fos = new FileOutputStream(destFile)) {
                                        byte[] buffer = new byte[8192];
                                        int len;
                                        while ((len = archive.read(buffer)) > 0)
                                            fos.write(buffer, 0, len);
                                    }
                                    return true;
                                }
                            }
                        }
                    } else if (ext.equals("zip")) {
                        try (ZipFile archive = new ZipFile(channel)) {
                            ZipArchiveEntry entry = archive.getEntry(entryName);
                            if (entry != null) {
                                try (InputStream is = archive.getInputStream(entry);
                                        FileOutputStream fos = new FileOutputStream(destFile)) {
                                    byte[] buffer = new byte[8192];
                                    int len;
                                    while ((len = is.read(buffer)) > 0)
                                        fos.write(buffer, 0, len);
                                }
                                return true;
                            }
                        }
                    } else if (SupportedFilesHelper.SPECIAL_TYPE_TAR.equals(SupportedFilesHelper.getSpecialTypeFromExt(ext))) {
                        try (InputStream is = context.getContentResolver().openInputStream(archiveFile.getUri());
                                java.io.BufferedInputStream bis = new java.io.BufferedInputStream(is);
                                java.io.InputStream cis = maybeWrapCompressor(bis);
                                TarArchiveInputStream tis = new TarArchiveInputStream(cis)) {
                            TarArchiveEntry entry;
                            while ((entry = tis.getNextTarEntry()) != null) {
                                if (entry.getName().equals(entryName)) {
                                    try (FileOutputStream fos = new FileOutputStream(destFile)) {
                                        byte[] buffer = new byte[8192];
                                        int len;
                                        while ((len = tis.read(buffer)) > 0)
                                            fos.write(buffer, 0, len);
                                    }
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            myLogEE(e, "Error extracting " + entryName + " from " + archiveFile.getName());
        }
        return false;
    }

    private void scanFolderCombined(Context context, DocumentFile rootDir, OnMetadataListener listener) {
        long startTime = System.currentTimeMillis();
        myLogD("scanFolderCombined() START for: " + name);

        // 1. Initial Cover Detection (Root only, external images) - this is fast
        this.coverImagePath = detectCoverForFolderExternally(context, rootDir);
        if (this.coverImagePath != null && listener != null) {
            listener.onCoverFound(this.coverImagePath);
        }
        myLogD("cover done for: " + name);

        // 2. Recursive Scan
        audioFileInfoArrayList.clear();
        FolderScanState state = new FolderScanState();
        scanFolderRecursive(context, rootDir, listener, state, 0, "");

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
            FolderScanState state, int level, String currentPath) {
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

            String fileName = safeName(child);
            String childPath = currentPath.isEmpty() ? fileName : currentPath + "/" + fileName;
            myLogD(Tonio.lpad(level, (level + 1) * 3) + " | scanFolderRecursive: " + childPath);

            if (child.isDirectory()) {
                scanFolderRecursive(context, child, listener, state, level + 1, childPath);
            } else {
                state.totalSize += child.length();

                String special = SupportedFilesHelper.getSpecialType(child);
                if (SupportedFilesHelper.isAudio(child) || SupportedFilesHelper.isVideo(child)) {
                    state.audioCount++;

                    // Track List for display
                    String displayName = Tonio.formatNameForDisplay(safeName(child));
                    String tName = state.audioCount + ". " + displayName;
                    // trackList.add(tName);

                    AudioInfo audioInfo = AudioProber.probe(context, child.getUri(), false);
                    if (audioInfo != null && audioInfo.durationMs > 0) {
                        AudioFileInfo afi = AudioFileInfo.fromProbe(audioInfo, tName);
                        audioFileInfoArrayList.add(afi);
                        if (listener != null) {
                            listener.onTrackFound(afi);
                        }
                    } else {
                        AudioFileInfo afi = new AudioFileInfo(child.getName(), tName, 0, child.length(),
                                child.getUri().toString(), null);
                        audioFileInfoArrayList.add(afi);
                        if (listener != null) {
                            listener.onTrackFound(afi);
                        }
                    }

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
        return ImportValidator.checkHashExists(context, hash);
    }

    private String checkFolderAlreadyImported(Context context, String folderPath, String hash) {
        if (folderPath == null || folderPath.isEmpty())
            return checkHashExists(context, hash);

        String existingByPath = ImportValidator.checkPathExists(context, folderPath);
        if (existingByPath != null && !existingByPath.isEmpty()) {
            return existingByPath;
        }

        if (hash != null && !hash.isEmpty()) {
            String existingByHash = ImportValidator.checkHashExists(context, hash);
            if (existingByHash != null && !existingByHash.isEmpty()) {
                return existingByHash;
            }
        }
        return null;
    }

    // --- Media Capabilities ---

    public boolean requiresForcedCopy() {
        return playType != null && !playType.isEmpty() && (SupportedFilesHelper.isBundleSpecial(specialType) ||
                SupportedFilesHelper.isEbookSpecial(specialType) ||
                "cloud".equals(sourceLocation) ||
                "web".equals(sourceLocation));
    }

    public boolean supportsSplit() {
        return SupportedFilesHelper.isM4bSpecial(specialType);
    }

    public boolean requiresForcedSplitCopy(boolean isSplitChecked) {
        return supportsSplit() && isSplitChecked;
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
        myLogD("using general helper to find a cover at the root level");
        try {
            CoverPictureDetection.CoverDetectionResult result = CoverPictureDetection.detectCoverFromFolder(context,
                    folder, null);

            if (result != null && result.imagePath != null) {
                myLog("image found through external helper");
                return result.imagePath;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String extractEmbeddedCoverFromFile(Context context, DocumentFile file) {
        if (Tonio.getExtension(file.getName()).equalsIgnoreCase("mp3")) {
            myLogDD("bypassing extract embedded cover for mp3 - 2");
            myLogD("bypassing extract embedded cover for mp3 - 2");
            return null;
        }
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
            } else if (fileName.endsWith(".docx")) {
                try {
                    return detectCoverForArchive(context, file);
                } catch (Exception ignored) {
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

    private void myLogDD(String txt) {
        if (VERBOSE_DEBUG)
            myLogD(txt);
    }

    private String extractCleanChapterTitle(Sample sample) {
        java.nio.ByteBuffer buffer = sample.asByteBuffer();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        if (data.length < 2)
            return "chapter";
        String raw = new String(Arrays.copyOfRange(data, 2, data.length),
                java.nio.charset.StandardCharsets.UTF_8);
        raw = raw.replaceAll("encd.*$", "")
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")
                .replace("\uFEFF", "")
                .trim();
        return raw.isEmpty() ? "chapter" : raw;
    }
}
