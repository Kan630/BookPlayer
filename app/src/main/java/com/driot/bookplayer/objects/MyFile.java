package com.driot.bookplayer.objects;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.util.Arrays;

import static com.driot.bookplayer.utils.TonioCommonStuff.deleteExtension;

import androidx.core.content.FileProvider;

import com.driot.bookplayer.utils.log.LoggerHelper;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 21/08/21
 */


//   log/kanlog_2021-08-21.txt


public class MyFile extends LoggerHelper {

    private final String fileName;
    private final String date;
    private final String title;

    public MyFile(String fileName) {
        super(MyFile.class);

        this.fileName = fileName;
        String str;
        String[] separated = new String[4];
        Arrays.fill(separated, "");
        try {
            str = deleteExtension(fileName);
            separated = str.split("_");
        } catch (Exception e) {
            myLogEE(e,"error constructeur MyFile");
        }
        this.date = separated[1];
        this.title = separated[0];
    }

    public String getDate() {
        return date;
    }

    public String getTitle() {
        return title;
    }

    public String getFileName() {
        return fileName;
    }

    public static Uri getUriFromMyFile(Context context, MyFile myFile) {
        File file = new File(context.getFilesDir(), "log/" + myFile.getFileName());

        if (!file.exists()) {
            com.driot.bookplayer.utils.log.LoggerStaticHelper.myLogE("getUriFromMyFile: File does not exist -> " + file.getAbsolutePath());
            return null;
        }

        return FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".FileProvider",
                file
        );
    }

}
