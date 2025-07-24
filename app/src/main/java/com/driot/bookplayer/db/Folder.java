package com.driot.bookplayer.db;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 28/10/20
 */

import static com.driot.bookplayer.global.Var.FOLDER_UNZIPPED;

import android.content.Context;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.driot.bookplayer.R;
import com.driot.bookplayer.utils.KanLogger;
import com.driot.bookplayer.utils.StorageHelper;

import org.jspecify.annotations.NonNull;

import java.io.Serializable;
import java.sql.Date;
import java.sql.Time;

@Entity
public class Folder implements Serializable {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "path")
    private String path;

    @ColumnInfo(name = "uri")
    private String uri;

    @ColumnInfo(name = "hash")
    private String hash;

    @ColumnInfo(name = "position")
    private int position;

    @ColumnInfo(name = "duration")
    private double duration;

    @ColumnInfo(name = "percentdone")
    private Double percentdone;

    @ColumnInfo(name = "firstaccess")
    private Time firstaccess;

    @ColumnInfo(name = "lastaccess")
    private Date lastaccess;

    @ColumnInfo(name = "lastaccessTime")
    private Time lastaccessTime;

    @ColumnInfo(name = "iszipfile")
    private boolean iszipfile;

    @ColumnInfo(name = "finished")
    private boolean finished;

    @ColumnInfo
    private String originalType;

    @ColumnInfo
    private String originalFile;

    @ColumnInfo
    private String originalHash;

    @ColumnInfo
    private String sourceLocation;

    @ColumnInfo
    private long listeningDuration;

    @ColumnInfo
    private long listeningPlayCount;

    public long nbZikFile;

    public long date_added;

    public long date_last_zikfile_added;

    public String image;

    public Long lFirstAccess;

    public long lLastAccess;



    /*
     * Getters and Setters
     * */

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public Double getPercentdone() {
        if (percentdone==null) {
            return 0.0;
        } else {
            return percentdone;
        }
    }

    public void setPercentdone(Double percentdone) {
        this.percentdone = percentdone;
    }

    public Time getFirstaccess() {
        return firstaccess;
    }

    public void setFirstaccess(Time firstaccess) {
        this.firstaccess = firstaccess;
    }

    public Date getLastaccess() {
        return lastaccess;
    }

    public void setLastaccess(Date lastaccess) {
        this.lastaccess = lastaccess;
    }

    public Time getLastaccessTime() {
        return lastaccessTime;
    }

    public void setLastaccessTime(Time lastaccessTime) {
        this.lastaccessTime = lastaccessTime;
    }

    public boolean isIszipfile() {
        return iszipfile;
    }

    public void setIszipfile(boolean iszipfile) {
        this.iszipfile = iszipfile;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }


    public int getMemoryLocationIcon(Context context) {
        try {
            if (path.startsWith(context.getFilesDir().getAbsolutePath())) {
                return R.drawable.ic_memory_general_smartphone_R; // Reserved internal
            } else if (path.startsWith(StorageHelper.getSdCardFilesDirs(context).getAbsolutePath())) {
                if (path.contains("/Android/data/" + context.getPackageName())) {
                    return R.drawable.ic_memory_sdcard_R; // Reserved SD
                } else {
                    return R.drawable.ic_memory_sdcard; // Shared SD
                }
            } else {
                return R.drawable.ic_memory_general_smartphone; // Shared phone storage
            }
        } catch (Exception e) {
            myLogEE(e, "getMemoryLocationIcon()");
            return R.drawable.ic_memory_notfound;
        }
    }

    public String getMemoryLocationText(Context context) {
        try {
            if (path.startsWith(context.getFilesDir().getAbsolutePath())) {
                return context.getString(R.string.audio_location_bookplayer_reserved_storage);
            } else if (path.startsWith(StorageHelper.getSdCardFilesDirs(context).getAbsolutePath())) {
                if (path.contains("/Android/data/" + context.getPackageName())) {
                    return context.getString(R.string.audio_location_sdcard_reserved_storage);
                } else {
                    return context.getString(R.string.audio_location_sdcard);
                }
            } else {
                return context.getString(R.string.audio_location_smartphone_shared_storage);
            }
        } catch (Exception e) {
            myLogEE(e, "getMemoryLocationIcon()");
            return context.getString(R.string.audio_location_audiobook_not_found);
        }
    }

    public void setLastAccessToNow() {
        this.lastaccess = new Date(System.currentTimeMillis());
        this.lastaccessTime = new Time(System.currentTimeMillis());
    }


    public String getOriginalType() {
        return originalType;
    }

    public String getOriginalFile() {
        return originalFile;
    }

    public String getOriginalHash() {
        return originalHash;
    }

    public String getSourceLocation() {
        return sourceLocation;
    }


    public void setOriginalType(String originalType) {
        this.originalType = originalType;
    }

    public void setOriginalFile(String originalFile) {
        this.originalFile = originalFile;
    }

    public void setOriginalHash(String originalHash) {
        this.originalHash = originalHash;
    }

    public void setSourceLocation(String sourceLocation) {
        this.sourceLocation = sourceLocation;
    }
    public long getListeningDuration() {
        return listeningDuration;
    }

    public long getListeningPlayCount() {
        return listeningPlayCount;
    }
    public void setListeningDuration(long listeningDuration) {
        this.listeningDuration = listeningDuration;
    }

    public void setListeningPlayCount(long listeningPlayCount) {
        this.listeningPlayCount = listeningPlayCount;
    }


    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogInFile(String str) { KanLogger.myLogInFile(this.getClass().getName(), str); }
    private void myLogD(String str) { KanLogger.myLogD(this.getClass().getName(), str); }
    private void myLogI(String str) { KanLogger.myLogI(this.getClass().getName(), str); }
    private void myLogW(String str) { KanLogger.myLogW(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    private void myLogEE(Throwable t, String str) { KanLogger.myLogEE(t, this.getClass().getName(), str); }
    private void myToast(String str) { KanLogger.myToast(this.getClass().getName(), str); }
    private void myToastE(String str) { KanLogger.myToastE(this.getClass().getName(), str); }
    private void myKeyFirebase(String strKey, String strValue) {KanLogger.myKeyFirebase(strKey, strValue);}
    private void myLogFirebase(String strLog) {KanLogger.myLogFirebase(strLog);}
}