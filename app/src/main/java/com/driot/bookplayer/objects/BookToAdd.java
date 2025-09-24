package com.driot.bookplayer.objects;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.driot.bookplayer.helpers.SupportedFilesHelper;
import com.driot.bookplayer.utils.KanLogger;
import com.driot.bookplayer.utils.Tonio;

import java.util.Objects;

public class BookToAdd {

    private static Context appContext;

    private Uri uri;
    private String type;

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
    public BookToAdd(Uri uri, String type) {

        this.type = type;
        this.uri = uri;
        this.originalType = type;
        this.isMimeSupported = true;

        if (isUriDirectory() && !Objects.equals(type, "Folder")) {
            myLogW("Side Check if it is a Folder : " + isUriDirectory() + " - but type = " + type);
        }

        this.sourceLocation = Tonio.getSourceLocation(appContext, uri);
        this.infoSourceLocation = "[" + this.sourceLocation + "]";

        //TODO : is this really usefull ?
        if (type.equals("File")) {
            try {
                this.df = DocumentFile.fromSingleUri(appContext, uri);
            } catch (Exception e) {
                myLogEE(e,"Error reading picked File.... DocumentFile.fromSingleUri");
                this.isBroken = true;
            }
        } else if (type.equals("Folder")) {
            try {
                this.df = DocumentFile.fromTreeUri(appContext, uri);
            } catch (Exception e) {
                myLogEE(e,"Error reading picked Folder.... DocumentFile.fromTreeUri");
                this.isBroken = true;
            }
        } else {
            myLogEE(null, "Very bad type");
        }

        if (type.equals("File")) {
            //TODO check that 3 methods
            mimeType = SupportedFilesHelper.getMimeType(appContext, uri);
            extractedfileName = SupportedFilesHelper.getFileName(appContext, uri) ;
            fileExtension = SupportedFilesHelper.getFileExtension(extractedfileName);

            if (Objects.toString(fileExtension,"").isEmpty()) {
                myLogEE(null, "file extension not found");
                this.isBroken = true;
            }

            // specific workers....
            String specialType = SupportedFilesHelper.getSpecialType(extractedfileName);
            if (specialType != null && !specialType.isEmpty()) { this.type = specialType; }

            this.infoMimeExtension = "[" + type + "] :    [" + mimeType + "] - [." + fileExtension + "]";
            this.infoMimeExtensionSmall = "[" + mimeType + "] - [." + fileExtension + "]";

            if (this.type.equals(SupportedFilesHelper.SPECIAL_TYPE_ZIP) || SupportedFilesHelper.isBookSupported(extractedfileName)
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

        } else if (type.equals("Folder")) {

            this.infoMimeExtension = "[" + type + "]";

            if (!isUriDirectory()) {
                myLogEE(null,"is not a Directory ?");
            }

            this.audioBookName = getBookName_with2folders(uri.getPath(), false);

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
        return type;
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

    public String getPlayType() {
        return playType;
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////
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
    private boolean isUriDirectory() {
        try {
            DocumentFile doc = DocumentFile.fromTreeUri(appContext, uri);
            return doc != null && doc.isDirectory();
        } catch (Exception e) {
            return false;
        }
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
                "\ntype='" + type + '\'' +
                "\nisBroken=" + isBroken +
                "\nsourceLocation='" + sourceLocation + '\'' +
                "\naudioBookName='" + audioBookName + '\'' +
                "\ndf=" + df +
                "\noriginalFile='" + originalFile + '\'' +
                "\noriginalType='" + originalType + '\'' +
                "\nfileExtension='" + fileExtension + '\'' +
                "\nmimeType='" + mimeType + '\'' +
                "\ninfoMimeExtension='" + infoMimeExtension + '\'' +
                "\ninfoSourceLocation='" + infoSourceLocation + '\'' +
                "\nplayType='" + playType + '\'' +
                '}';
    }

    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogI(String str) { KanLogger.myLogI(this.getClass().getName(), str); }
    private void myLogW(String str) { KanLogger.myLogW(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    private void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, this.getClass().getName(), str); }
    private void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, this.getClass().getName(), str); }

}
