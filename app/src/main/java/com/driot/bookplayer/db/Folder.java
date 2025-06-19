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


    public static final int MEMORY_ICON_BOOKPLAYER_INTERNAL = R.drawable.ic_memory_internal_bookplayer;
    public static final int MEMORY_ICON_SMARTPHONE_GENERAL = R.drawable.ic_memory_general_smartphone;
    public static final int MEMORY_ICON_SMARTPHONE_NOTFOUND = R.drawable.ic_memory_general_notfound;



    public int getMemoryLocationIcon(Context c) {
        try {
            if (path.contains(FOLDER_UNZIPPED)) {
                return R.drawable.ic_memory_internal_bookplayer;
            } else {
                return R.drawable.ic_memory_general_smartphone;
            }
        } catch (Exception e) {
            myLogE("getMemoryLocationIcon() - error : " + e.getMessage());
            return R.drawable.ic_memory_general_notfound;
        }
    }

    // TODO : create a new column to store the location in DB
    // and use StorageManager (API 24+) to check if storage is removable (aka SD card)

    public String getMemoryLocationText(Context c) {
        try {
            if (path.contains(FOLDER_UNZIPPED)) {
                return c.getString(R.string.audio_location_bookplayer_reserved_storage);
            } else {
                return c.getString(R.string.audio_location_smartphone_shared_storage);
            }
        } catch (Exception e) {
            myLogE("getMemoryLocationText() - error : " + e.getMessage());
            return c.getString(R.string.audio_location_audiobook_not_found);
        }
    }

    public void setLastAccessToNow() {
        this.lastaccess = new Date(System.currentTimeMillis());
        this.lastaccessTime = new Time(System.currentTimeMillis());
    }
    //--- LOG --------------------------
    private void myLog(String str) { KanLogger.myLog(this.getClass().getName(), str); }
    private void myLogInFile(String str) { KanLogger.myLogInFile(this.getClass().getName(), str); }
    private void myLogE(String str) { KanLogger.myLogE(this.getClass().getName(), str); }
    private void myToast(String str) { KanLogger.myToast(this.getClass().getName(), str); }
    private void myToastE(String str) { KanLogger.myToastE(this.getClass().getName(), str); }

}