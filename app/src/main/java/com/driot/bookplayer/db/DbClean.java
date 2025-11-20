package com.driot.bookplayer.db;

import android.content.Context;
import android.net.Uri;

import com.driot.bookplayer.global.Pref;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.FirebaseAnalyticsHelper;
import com.driot.bookplayer.helpers.StorageHelper;
import com.driot.bookplayer.helpers.UriHelper;
import com.driot.bookplayer.player.ErrorUi;
import com.driot.bookplayer.utils.Tonio;

import static com.driot.bookplayer.utils.log.LoggerStaticHelper.*;

import java.util.List;

public class DbClean {

    private static final boolean DO_REWRITE = false;
    private static final int DEBUG_LOG_SKIP_LOOP = 20;
    private static final int INTERVAL_BETWEEN_CHECKS_IN_MIN = 60*24;

    public static void doClean(Context context, boolean cleanZikFilePaths, boolean cleanImagesPaths) {

        long lastDbClean = Pref.getLastDbClean();
        long now = System.currentTimeMillis();
        boolean doClean = (now - lastDbClean > INTERVAL_BETWEEN_CHECKS_IN_MIN * 60 * 1000);
        myLog("doClean = [" + doClean +  "].. - last Clean was " + Tonio.formatTime(now - lastDbClean) + " ago. -  Interval = " + Tonio.formatTime(INTERVAL_BETWEEN_CHECKS_IN_MIN * 60 * 1000));
        if (!doClean) return;
        Pref.setLastDbClean();

// CLEAN ZIKFILE PATHS
        if (cleanZikFilePaths) {
            AppDatabase.databaseReadExecutor.execute(() -> {
                Context ctx = context.getApplicationContext();
                String masterMsg = "";
                Uri src;
                String path;

                int i = 0;
                int nbInvalid = 0;
                int nbRewritten = 0;
                int nbStillBad = 0;
                int nbFatalError = 0;

                List<ZikFile> list = AppDatabase.getDatabase(ctx).zikFileDao().getAll();
                int nbZikFiles = list.size();

                for (ZikFile zikFile : list) {
                    i = i + 1;
                    if (i % DEBUG_LOG_SKIP_LOOP == 0) {
                        myLogD("checking zikFiles paths : " + i + "/" + nbZikFiles + " zikfiles");
                    }

                    path = zikFile.getPath();
                    String pathType = (path.startsWith("content://") ? "[CONTENT] " : "");
                    String logStrPrefix = "zikFile " + i + "/" + nbZikFiles
                            + " [" + zikFile.getFolderName() + "] - [" + zikFile.getDisplayName() + "]";

                    src = UriHelper.resolveUriFromPath(ctx, path);
                    if (src == null) {
                        nbInvalid = nbInvalid + 1;

                        String errMessage = ErrorUi.getErrorMessage(ctx, path);
                        String fullError = logStrPrefix
                                + "\n" + errMessage
                                + "\npath = [" + pathType + path + "]";

                        // Try fallback path
                        String newPath = zikFile.getPath() + "/" + zikFile.getName();
                        Uri newSrc = UriHelper.resolveUriFromPath(ctx, newPath);

                        if (newSrc == null) {
                            nbStillBad = nbStillBad + 1;
                            fullError = fullError
                                    + "\n--> rewrite FAILED, still invalid"
                                    + "\nnewPath = [" + newPath + "]";
                        } else {
                            nbRewritten = nbRewritten + 1;
                            if (DO_REWRITE) {
                                zikFile.setPath(newPath);
                                AppDatabase.getDatabase(ctx).zikFileDao().update(zikFile);
                            }
                            fullError = fullError
                                    + "\n****************************************************************************************"
                                    + "\n****************************************************************************************"
                                    + "\n--> path REWRITTEN" + (DO_REWRITE ? "" : "--- FAKE REWRITE (debug DO_REWRITE=false) ---")
                                    + "\n****************************************************************************************"
                                    + "\nnewPath = [" + newPath + "]"
                                    + "\n****************************************************************************************"
                                    + "\n****************************************************************************************"
                            ;
                        }

                        masterMsg = masterMsg + "\n\n" + fullError;
                        nbFatalError = masterMsg.split(Var.SHOULD_NOT_HAPPEN, -1).length - 1;
                    }
                }

                if (nbInvalid == 0) {
                    myLogI("CLEAN ZIKFILE PATHS : all " + nbZikFiles + " zikFiles have valid paths.");
                } else {
                    String header = "CLEAN ZIKFILE PATHS : "
                            + "\n" + nbInvalid + "/" + nbZikFiles + " invalid paths; "
                            + "\n" + nbFatalError + " - " + Var.SHOULD_NOT_HAPPEN + " -"
                            + "\n" + nbRewritten + " rewritten; "
                            + "\n" + nbStillBad + " still invalid.";
                    myLogW(header + "\n\nDetails:" + masterMsg);
                    FirebaseAnalyticsHelper.tellDbKo("audio", nbFatalError, nbInvalid, nbZikFiles, nbRewritten, nbStillBad, masterMsg);
                }
            });
        }


        //CLEAN IMAGES PATHS
        if (cleanImagesPaths) {
            AppDatabase.databaseReadExecutor.execute(() -> {
                Context ctx = context.getApplicationContext();
                String masterMsg = "";
                String path;
                String newPath;

                int i = 0;
                int nbWithImage = 0;
                int nbInvalid = 0;
                int nbRewritten = 0;
                int nbStillBad = 0;
                int nbFatalError = 0;

                List<Folder> list = AppDatabase.getDatabase(ctx).folderDao().getAll();
                int nbFolders = list.size();

                for (Folder folder : list) {
                    i = i + 1;
                    //if (i % DEBUG_LOG_SKIP_LOOP == 0) {
                        myLogD("checking image paths : " + i + "/" + nbFolders + " folders");
                    //}

                    path = folder.image;
                    if (path == null || path.isEmpty()) {
                        // no image assigned, not an error
                        continue;
                    }

                    nbWithImage = nbWithImage + 1;

                    String pathType = (path.startsWith("content://") ? "[CONTENT] " : "");
                    String logStrPrefix = "img " + nbWithImage + "/" + nbFolders
                            + " [" + folder.getName() + "]";

                    newPath = StorageHelper.checkAndCleanImagePath(ctx, path);

                    if (newPath == null) {
                        // original path invalid and no fixed path found
                        nbInvalid = nbInvalid + 1;
                        nbStillBad = nbStillBad + 1;

                        String fullError = logStrPrefix
                                + "\ncould not find image"
                                + "\npath = [" + pathType + path + "]";

                        masterMsg = masterMsg + "\n\n" + fullError;
                    } else if (!newPath.equals(path)) {
                        // original path invalid but we found a replacement
                        nbInvalid = nbInvalid + 1;
                        nbRewritten = nbRewritten + 1;

                        String fullError = logStrPrefix
                                + "\n****************************************************************************************"
                                + "\n****************************************************************************************"
                                + "\n--> path REWRITTEN" + (DO_REWRITE ? "" : "--- FAKE REWRITE (debug DO_REWRITE=false) ---")
                                + "\n****************************************************************************************"
                                + "\nfrom [" + pathType + path + "]"
                                + "\nto [" + newPath + "]"
                                + "\n****************************************************************************************"
                                + "\n****************************************************************************************";

                        masterMsg = masterMsg + "\n\n" + fullError;

                        if (DO_REWRITE) {
                            folder.image = newPath;
                            AppDatabase.getDatabase(ctx).folderDao().update(folder);
                        }
                    }

                    // count "should not happen" occurrences in the accumulated message
                    nbFatalError = masterMsg.split(Var.SHOULD_NOT_HAPPEN, -1).length - 1;
                }

                if (nbInvalid == 0) {
                    myLogI("CLEAN IMAGE PATHS : all " + nbWithImage
                            + " image paths are valid (" + nbFolders + " folders total).");
                } else {
                    String header = "CLEAN IMAGE PATHS : "
                            + "\n" + nbInvalid + "/" + nbWithImage + " invalid image paths; "
                            + "\n" + nbFatalError + " - " + Var.SHOULD_NOT_HAPPEN + " -"
                            + "\n" + nbRewritten + " rewritten; "
                            + "\n" + nbStillBad + " still invalid.";

                    myLogW(header + "\n\nDetails:" + masterMsg);
                    FirebaseAnalyticsHelper.tellDbKo("image", nbFatalError, nbInvalid, nbWithImage, nbRewritten, nbStillBad, masterMsg);
                }
            });
        }
    }
}
