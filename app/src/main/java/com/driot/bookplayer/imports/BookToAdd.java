package com.driot.bookplayer.imports;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.driot.bookplayer.utils.log.LoggerHelper;

public class BookToAdd extends LoggerHelper {

    private static Context appContext;

    private final BookCandidate candidate;
    private final String pickedType;
    private DocumentFile df; // Kept for interface compatibility if needed, though likely unused or can be
                             // derived

    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////
    /// CONSTRUCTOR
    /// ////////////////////////////////////////////////////////////////////////////////////////
    public BookToAdd(Uri pickedUri, String pickedType) {
        super(BookToAdd.class);
        this.pickedType = pickedType;

        // Delegate everything to BookCandidate
        // note: name is not strictly needed for enrichment, passing null or derived
        // name
        this.candidate = new BookCandidate(appContext, pickedUri, null, pickedType);
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////
    /// GETTERS
    /// ////////////////////////////////////////////////////////////////////////////////////////

    public Uri getUri() {
        return candidate.uri;
    }

    public String getType() {
        return pickedType;
    }

    public boolean isBroken() {
        return candidate.isBroken;
    }

    public boolean isMimeSupported() {
        return candidate.isMimeSupported;
    }

    public String getAudioBookName() {
        return candidate.audioBookName;
    }

    public DocumentFile getDf() {
        // If df was used externally, we might need to expose it, but BookCandidate uses
        // it internally.
        // For now returning null or we could expose candidate.getDocumentFile if we
        // stored it.
        // Looking at original code, 'df' field was never actually assigned in
        // constructor!
        // It was defined but not set. So returning null is faithful to original
        // behavior unless I missed something.
        return df;
    }

    public String getSourceLocation() {
        return candidate.sourceLocation;
    }

    public String getInfoMimeExtension() {
        return candidate.infoMimeExtension;
    }

    public String getInfoMimeExtensionSmall() {
        return candidate.infoMimeExtensionSmall;
    }

    public String getInfoSourceLocation() {
        return candidate.infoSourceLocation;
    }

    public String getOriginalFile() {
        return candidate.originalFile;
    }

    public String getOriginalType() {
        return candidate.originalType;
    }

    public String getFileExtension() {
        return candidate.fileExtension;
    }

    public String getMimeType() {
        return candidate.mimeType;
    }

    public String getPlayType() {
        return candidate.playType;
    }

    public String getSpecialType() {
        return candidate.specialType;
    }

    /**
     * True when folder contains 2+ books (epub/fb2/odt/zip) or only zip files →
     * user should use Mass Import.
     */
    public boolean hasMultipleBooksInFolder() {
        return candidate.hasMultipleBooksInFolder();
    }
}
