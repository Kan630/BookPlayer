package com.driot.bookplayer.db;

import android.content.Context;
import android.net.Uri;

import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.player.ErrorUi;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.util.List;

public class DbClean {

    private static final boolean DO_REWRITE = false;

    public static void doClean(Context context, boolean cleanZikFilePaths, boolean cleanImagesPaths) {

        //CLEAN ZIKFILE PATHS
        if (cleanZikFilePaths) {
            AppDatabase.databaseReadExecutor.execute(() -> {
                String path;
                Uri src;
                int i = 1;
                List<ZikFile> list = AppDatabase.getDatabase(context).zikFileDao().getAll();
                int nbZikFiles = list.size();
                for (ZikFile zikFile : list) {
                    path = zikFile.getPath();
                    String pathType = (path.startsWith("content://") ? "[CONTENT] " : "");
                    String logStrPrefix = "zikFile " + i + "/" + nbZikFiles + ". " + pathType;
                    src = UriHelper.resolveUriFromPath(context, path);
                    if (src == null) {
                        String errMessage = ErrorUi.getErrorMessage(context, path);
                        myLogEE(null, logStrPrefix + "bad path for zikFile [" + zikFile.getFolderName() + "] - [" + zikFile.getDisplayName() + "]" +
                                "\npath = [" + path + "]" +
                                "\n" + errMessage
                        );
                        path = zikFile.getPath() + "/" + zikFile.getName();
                        src = UriHelper.resolveUriFromPath(context, path);
                        if (src==null) {
                            myLogW(logStrPrefix + "bad path for zikFile - Even new Path not working - [" + path + "]");
                        } else {
                            if (DO_REWRITE) {
                                zikFile.setPath(path);
                                AppDatabase.getDatabase(context).zikFileDao().update(zikFile);
                            }
                            myLogW(logStrPrefix + "path REWRITTEN : [" + path + "]");
                        }
                        //} else {
                        //myLogD(logStrPrefix + "path OK for zikFile [" + zikFile.getFolderName() + "] - [" + zikFile.getDisplayName() + "] - path = [" + path + "]");
                    }
                    i = i + 1 ;
                }
            });
        }

        //CLEAN IMAGES PATHS
        if (cleanImagesPaths) {
            AppDatabase.databaseReadExecutor.execute(() -> {
                String path;
                String newPath;
                int i = 1;
                List<Folder> list = AppDatabase.getDatabase(context).folderDao().getAll();
                int nbFolder = list.size();
                for (Folder folder : list) {
                    String logStrPrefix = "img " + i + "/" + nbFolder + ". ";
                    path = folder.image;
                    if (path == null) {
                        myLogD( logStrPrefix + "no image for [" + folder.getName() + "] - path = [" + path + "]");
                    } else {
                        newPath = StorageHelper.checkAndCleanImagePath(context, path);
                        if (newPath == null) {
                            myLogEE(null, logStrPrefix + "could not find image [" + folder.getName() + "] - path = [" + path + "]");
                        } else if (newPath.equals(path)) {
                            myLogD(logStrPrefix + "path OK for image [" + folder.getName() + "] - path = [" + path + "]");
                        } else {
                            myLogW(logStrPrefix + "path REWRITTEN for image [" + folder.getName() + "]\n[" + path + "] => [" + newPath + "]");
                            if (DO_REWRITE) {
                                folder.image = newPath;
                                AppDatabase.getDatabase(context).folderDao().update(folder);
                            }
                        }
                    }
                    i = i + 1 ;
                }
            });
        }
    }

}
