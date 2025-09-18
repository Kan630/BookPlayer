package com.driot.bookplayer.objects;

import static com.driot.bookplayer.global.Var.ONLY_MIME_AUDIO;
import static com.driot.bookplayer.global.Var.ONLY_MIME_EBOOK;
import static com.driot.bookplayer.global.Var.ONLY_MIME_VIDEO;
import static com.driot.bookplayer.global.Var.SUPPORTED_AUDIO_EXTENSIONS;

import static com.driot.bookplayer.global.Var.SUPPORTED_EBOOK_EXTENSIONS;
import static com.driot.bookplayer.global.Var.SUPPORTED_TEXTUAL_MIMES;
import static com.driot.bookplayer.global.Var.SUPPORTED_VIDEO_EXTENSIONS;
import static com.driot.bookplayer.utils.Tonio.formatNameForDisplay;
import static com.driot.bookplayer.utils.Tonio.getExtension;
import static com.driot.bookplayer.utils.Tonio.getFileNameFromUri;
import static com.driot.bookplayer.utils.Tonio.getMimeType;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.utils.KanLogger;
import com.driot.bookplayer.utils.Tonio;

import java.io.File;
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
            mimeType = Objects.toString(Tonio.getMimeType(appContext, uri),"");
            String fileName = getFileNameFromUri(appContext, uri);
            fileExtension = getExtension(fileName);

            if (Objects.toString(fileExtension,"").isEmpty()) {
                myLogEE(null, "file extension not found");
                this.isBroken = true;
            }

            // specific workers....
            if (fileExtension.equalsIgnoreCase("zip")) {
                this.type = "ZIP";
            } else if (fileExtension.equalsIgnoreCase("m4b")) {
                this.type = "M4B";
            } else if (fileExtension.equalsIgnoreCase("odt")) {
                this.type = "ODT";
            } else if (fileExtension.equalsIgnoreCase("fb2")) {
                this.type = "FB2";
            } else if (fileExtension.equalsIgnoreCase("epub")) {
                this.type = "EPUB";
            }

            this.infoMimeExtension = "[" + type + "] :    [" + mimeType + "] - [." + fileExtension + "]";
            this.infoMimeExtensionSmall = "[" + mimeType + "] - [." + fileExtension + "]";

            if (this.type.equals("ZIP")
                || mimeType.startsWith(ONLY_MIME_AUDIO) || SUPPORTED_AUDIO_EXTENSIONS.contains(fileExtension)
                || mimeType.startsWith(ONLY_MIME_VIDEO) || SUPPORTED_VIDEO_EXTENSIONS.contains(fileExtension)
                || SUPPORTED_TEXTUAL_MIMES.contains(mimeType) || SUPPORTED_EBOOK_EXTENSIONS.contains(fileExtension)
            ) {
                myLogD("Mime/Extension supported - " + infoMimeExtension);
            } else {
                this.isMimeSupported = false;
                myLogEE(null,"Mime/Extension not supported - " + infoMimeExtension);
                return;
            }

            if (ONLY_MIME_EBOOK.contains(mimeType) || SUPPORTED_EBOOK_EXTENSIONS.contains(fileExtension)) {
                this.playType = Var.PLAY_TYPE_TEXT;
            } else {
                this.playType = Var.PLAY_TYPE_AUDIO;
            }

            String fileName2 = getFileName();
            this.audioBookName = formatNameForDisplay(fileName2);
            this.originalFile = fileName2;

            if (!Objects.equals(fileName, fileName2)) {
                myLogE("-------------------------------------------------");
                myLogE("CHECK THAT " + fileName + "/" + fileName2);
            }

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





    private String getFileName() {
        myLogD("getFileName() start: uri = " + uri.toString());
        String name = null;

        // 1. Try OpenableColumns (most reliable for content://)
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = appContext.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        name = cursor.getString(index);
                        myLogD("getFileName - OpenableColumns: [" + name + "]");
                        return name;
                    }
                }
            } catch (Exception e) {
                myLogEE(e, "getFileName - OpenableColumns failed");
            }
        }

        // 2. Try resolving via MediaStore
        if (name == null && "content".equalsIgnoreCase(uri.getScheme())) {
            try {
                String[] projection = { MediaStore.MediaColumns.DATA };
                try (Cursor cursor = appContext.getContentResolver().query(uri, projection, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                        String filePath = cursor.getString(index);
                        if (filePath != null) {
                            name = new File(filePath).getName();
                            myLog("getFileName - MediaStore: [" + name + "]");
                            return name;
                        }
                    }
                }
            } catch (Exception e) {
                myLogEE(e, "getFileName - MediaStore failed");
            }
        }

        // 3. Fallback: parse from path manually
        if (name == null) {
            String path = uri.getPath();
            if (path != null) {
                if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
                int cut = path.lastIndexOf('/');
                if (cut != -1) {
                    name = path.substring(cut + 1);
                    myLog("getFileName - path fallback: " + name);
                    return name;
                }
            }
        }

        // 4. Last fallback: last path segment
        if (name == null) {
            name = uri.getLastPathSegment();
            myLog("getFileName - lastPathSegment fallback: " + name);
        }

        if (name == null) {
            myLogE("getFileName failed completely for uri: [" + uri.toString() + "]");
            this.isBroken = true;
        }

        return name;
    }

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
                zeReturn = formatNameForDisplay(str.substring(pos2 + 1), stripExtension);
            } else {
                zeReturn = formatNameForDisplay(str.substring(pos1 + 1), stripExtension);
            }
        } else {
            // especially when foldername is just a string without slash (Android 11 zip local copy)
            zeReturn = formatNameForDisplay(str, stripExtension);
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
                "\ninfoSourceLocation='" + infoSourceLocation + '\'';
    }

    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogI(String str) { KanLogger.myLogI(this.getClass().getName(), str); }
    private void myLogW(String str) { KanLogger.myLogW(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    private void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, this.getClass().getName(), str); }
    private void myToastEE(Throwable t, String str) { KanLogger.myToastEE(t, this.getClass().getName(), str); }

}
