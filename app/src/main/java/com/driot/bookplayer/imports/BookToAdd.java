package com.driot.bookplayer.imports;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.SupportedFilesHelper;
import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.utils.Tonio;
import com.driot.bookplayer.utils.log.LoggerHelper;

import java.util.Objects;

public class BookToAdd extends LoggerHelper {

    private static Context appContext;

    private Uri uri;
    private String pickedType;

    private boolean isBroken;
    private boolean isMimeSupported;
    private String sourceLocation = "init...";
    private String audioBookName = "init...";
    private DocumentFile df;
    private String originalFile;
    private String originalType;
    private String fileExtension;
    private String mimeType;
    private String playType;
    private String specialType;

    /** When Folder: count of epub/fb2/odt files (each is typically one book). >= 2 means "multiple books" → use Mass Import. */
    private int multipleBooksCount = 0;
    private boolean hasOnlyZipFilesInFolder = false;

    private String infoMimeExtension = "init...";
    private String infoMimeExtensionSmall = "init...";
    private String infoSourceLocation = "init...";
    private String extractedfileName = "...";

    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }
    /// ////////////////////////////////////////////////////////////////////////////////////////
    ///    CONSTRUCTOR
    /// ////////////////////////////////////////////////////////////////////////////////////////
    public BookToAdd(Uri pickedUri, String pickedType) {
        super(BookToAdd.class);

        this.pickedType = pickedType;
        this.originalType = pickedType;
        this.uri = pickedUri;
        this.isMimeSupported = true;

        this.sourceLocation = Tonio.getSourceLocation(appContext, pickedUri);
        this.infoSourceLocation = "[" + this.sourceLocation + "]";

        if (pickedType.equals("File")) {
            //TODO check that 3 methods (and they are also in in Librivox, somewhere else in the code is use Tonio.get...)
            mimeType = SupportedFilesHelper.getMimeType(appContext, pickedUri);
            extractedfileName = SupportedFilesHelper.getFileName(appContext, pickedUri) ;
            fileExtension = SupportedFilesHelper.getFileExtension(extractedfileName);

            if (Objects.toString(fileExtension,"").isEmpty()) {
                myLogEE(null, "file extension not found");
                this.isBroken = true;
            }

            // specific workers....
            specialType = SupportedFilesHelper.getSpecialType(extractedfileName);
            //if (specialType != null && !specialType.isEmpty()) { this.pickedType = specialType; }

            this.infoMimeExtension = "[" + specialType + "] :    [" + mimeType + "] - [." + fileExtension + "]";
            this.infoMimeExtensionSmall = "[" + mimeType + "] - [." + fileExtension + "]";

            if (SupportedFilesHelper.isBookSupported(extractedfileName)
            ) {
                myLogD("Mime/Extension supported - " + infoMimeExtension);
            } else {
                this.isMimeSupported = false;
                myLogEE(null,"Mime/Extension not supported - " + infoMimeExtension);
                return;
            }

            this.playType = SupportedFilesHelper.getPlayType(extractedfileName);

            this.audioBookName = Tonio.formatNameForDisplay(extractedfileName);
            this.originalFile = extractedfileName;

        } else if (pickedType.equals("Folder")) {

            this.infoMimeExtension = "[" + pickedType + "]";
            this.audioBookName = getBookName_with2folders(pickedUri.getPath(), false);
            this.playType = inferPlayTypeFromFolder(pickedUri);
            this.multipleBooksCount = countRealEbookFilesRecursive(pickedUri);
            this.hasOnlyZipFilesInFolder = checkHasOnlyZipFilesInFolder(pickedUri);

        }
        trimAudioBookPrefix();

    }

    /// ////////////////////////////////////////////////////////////////////////////////////////
    ///     GETTERS
    /// ////////////////////////////////////////////////////////////////////////////////////////

    public Uri getUri() {
        return uri;
    }

    public String getType() {
        return pickedType;
    }

    public boolean isBroken() {
        return isBroken;
    }

    public boolean isMimeSupported() {
        return isMimeSupported;
    }

    public String getAudioBookName() {
        return audioBookName;
    }

    public DocumentFile getDf() {
        return df;
    }

    public String getSourceLocation() {
        return sourceLocation;
    }

    public String getInfoMimeExtension() {
        return infoMimeExtension;
    }
    public String getInfoMimeExtensionSmall() {
        return infoMimeExtensionSmall;
    }

    public String getInfoSourceLocation() {
        return infoSourceLocation;
    }

    public String getOriginalFile() {
        return originalFile;
    }

    public String getOriginalType() {
        return originalType;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getPlayType() { return playType; }
    public String getSpecialType() { return specialType; }

    /** True when folder contains 2+ books (epub/fb2/odt/zip) or only zip files → user should use Mass Import. */
    public boolean hasMultipleBooksInFolder() {
        return multipleBooksCount >= 2 || hasOnlyZipFilesInFolder;
    }



    /// ////////////////////////////////////////////////////////////////////////////////////////
    /// HELPERS
    /// ////////////////////////////////////////////////////////////////////////////////////////

    private String getBookName_with2folders(String sFolderPath, boolean stripExtension) {
        // nom par défaut = les deux derniers folders :
        // ex  : "S3 - Finances publiques/Audios"
        String str = sFolderPath;
        String zeReturn;
        if (str == null) {str = "";}
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
            // especially when foldername is just a string without slash (Android 11 zip local copy)
            zeReturn = Tonio.formatNameForDisplay(str, stripExtension);
        }
        myLog("getBookName_with2folders : [" + zeReturn + "]\nFrom : [" + sFolderPath + "]");
        return zeReturn;
    }

    /**
     * Infer playType for a folder by scanning its contents.
     * - "text" (TTS): only when folder contains plain .txt files and NO audio, video, epub, fb2, odt, m4b, zip, etc.
     * - "audio": when folder contains any "real" content (audio, video, epub, real ebooks, zip...).
     * We never import .txt as ebook tracks when the folder is an "audio" folder.
     */
    private String inferPlayTypeFromFolder(Uri folderUri) {
        try {
            DocumentFile root = UriHelper.getDocumentFileFromAnyUri(appContext, folderUri);
            if (root == null || !root.exists() || !root.isDirectory()) {
                return null;
            }
            int[] counts = countMediaTypesRecursive(root);
            boolean hasRealContent = counts[0] > 0;  // audio, video, epub, fb2, odt, m4b, zip...
            boolean hasPlainTextOnly = counts[1] > 0; // .txt only
            if (hasRealContent) {
                return Var.PLAY_TYPE_AUDIO;
            }
            if (hasPlainTextOnly) {
                return Var.PLAY_TYPE_TEXT;
            }
        } catch (Exception e) {
            myLogEE(e, "inferPlayTypeFromFolder");
        }
        return null;
    }

    /** Returns [realContentCount, plainTextCount]. Real = audio/video/epub/fb2/odt/m4b/zip. Plain = .txt only. */
    private int[] countMediaTypesRecursive(DocumentFile dir) {
        int realContent = 0;
        int plainText = 0;
        DocumentFile[] children = dir.listFiles();
        if (children == null) return new int[] { 0, 0 };
        for (DocumentFile child : children) {
            if (child.isDirectory()) {
                int[] sub = countMediaTypesRecursive(child);
                realContent += sub[0];
                plainText += sub[1];
            } else if (child.isFile()) {
                if (isRealBookOrAudioContent(child)) {
                    realContent++;
                } else if (SupportedFilesHelper.isText(child)) {
                    plainText++;
                }
            }
        }
        return new int[] { realContent, plainText };
    }

    /** Count epub, fb2, odt files (each is typically one book). Used to detect "multiple books" folder. */
    private int countRealEbookFilesRecursive(Uri folderUri) {
        try {
            DocumentFile root = UriHelper.getDocumentFileFromAnyUri(appContext, folderUri);
            if (root == null || !root.exists() || !root.isDirectory()) return 0;
            return countRealEbookFilesRecursive(root);
        } catch (Exception e) {
            myLogEE(e, "countRealEbookFilesRecursive");
            return 0;
        }
    }

    /** Count epub, fb2, odt, zip, 7z – each is one book → "multiple books" when >= 2. */
    private int countRealEbookFilesRecursive(DocumentFile dir) {
        int count = 0;
        DocumentFile[] children = dir.listFiles();
        if (children == null) return 0;
        for (DocumentFile child : children) {
            if (child.isDirectory()) {
                count += countRealEbookFilesRecursive(child);
            } else if (child.isFile()) {
                String special = SupportedFilesHelper.getSpecialType(child);
                if (SupportedFilesHelper.isSplittableEbookSpecial(special)
                        || SupportedFilesHelper.isBundleSpecial(special)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** True when folder has 1+ zip/7z and no audio (Folder import can't unzip; use Mass Import). */
    private boolean checkHasOnlyZipFilesInFolder(Uri folderUri) {
        try {
            DocumentFile root = UriHelper.getDocumentFileFromAnyUri(appContext, folderUri);
            if (root == null || !root.exists() || !root.isDirectory()) return false;
            int[] ab = countAudioAndBundleRecursive(root);
            return ab[1] >= 1 && ab[0] == 0; // has bundles, no audio
        } catch (Exception e) {
            myLogEE(e, "checkHasOnlyZipFilesInFolder");
            return false;
        }
    }

    /** Returns [audioCount, bundleCount]. */
    private int[] countAudioAndBundleRecursive(DocumentFile dir) {
        int audio = 0, bundle = 0;
        DocumentFile[] children = dir.listFiles();
        if (children == null) return new int[] { 0, 0 };
        for (DocumentFile child : children) {
            if (child.isDirectory()) {
                int[] sub = countAudioAndBundleRecursive(child);
                audio += sub[0];
                bundle += sub[1];
            } else if (child.isFile()) {
                if (SupportedFilesHelper.isAudio(child) || SupportedFilesHelper.isVideo(child)) {
                    audio++;
                } else if (SupportedFilesHelper.isBundleSpecial(SupportedFilesHelper.getSpecialType(child))) {
                    bundle++;
                }
            }
        }
        return new int[] { audio, bundle };
    }

    /** Real content that blocks "text folder" mode: audio, video, epub, fb2, odt, m4b, zip... */
    private boolean isRealBookOrAudioContent(DocumentFile child) {
        if (SupportedFilesHelper.isAudio(child) || SupportedFilesHelper.isVideo(child)) {
            return true;
        }
        String special = SupportedFilesHelper.getSpecialType(child);
        return SupportedFilesHelper.isSplittableEbookSpecial(special)
                || SupportedFilesHelper.isBundleSpecial(special)
                || SupportedFilesHelper.isM4bSpecial(special);
    }

    private void trimAudioBookPrefix() {
        String[] prefixes = { "download/", "audiobooks/", "unzipped/" };
        for (String prefix : prefixes) {
            if (audioBookName.toLowerCase().startsWith(prefix)) {
                audioBookName = audioBookName.substring(prefix.length());
                break; // Stop after the first match
            }
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "uri=" + uri +
                "\npickedType='" + pickedType + '\'' +
                "\noriginalType='" + originalType + '\'' +
                "\nspecialType='" + specialType + '\'' +
                "\nisBroken=" + isBroken +
                "\nsourceLocation='" + sourceLocation + '\'' +
                "\naudioBookName='" + audioBookName + '\'' +
                "\ndf=" + df +
                "\noriginalFile='" + originalFile + '\'' +
                "\nfileExtension='" + fileExtension + '\'' +
                "\nmimeType='" + mimeType + '\'' +
                "\ninfoMimeExtension='" + infoMimeExtension + '\'' +
                "\ninfoSourceLocation='" + infoSourceLocation + '\'' +
                "\nplayType='" + playType + '\'' +
                '}';
    }
}
