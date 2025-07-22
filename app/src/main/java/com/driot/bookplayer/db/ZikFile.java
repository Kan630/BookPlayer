package com.driot.bookplayer.db;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 30/10/20
 */

import static com.driot.bookplayer.utils.Tonio.getExtension;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;
import java.sql.Date;
import java.sql.Time;

@Entity
public class ZikFile implements Serializable {

// TODO : best practice for intent passing .. to test
//public class ZikFile implements Parcelable {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "idFolder")
    private int idFolder;

    @ColumnInfo(name = "displayName")
    private String displayName;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "path")
    private String path;

    @ColumnInfo(name = "folderName")
    private String folderName;

    @ColumnInfo(name = "position")
    private double position;

    @ColumnInfo(name = "duration")
    private double duration;

    @ColumnInfo(name = "size")
    private double size;

    @ColumnInfo(name = "percentdone")
    private double percentdone;

    @ColumnInfo(name = "firstaccess")
    private Date firstaccess;

    @ColumnInfo(name = "lastaccess")
    private Date lastaccess;

    @ColumnInfo(name = "lastaccessTime")
    private Time lastaccessTime;

    @ColumnInfo(name = "iszipfile")
    private boolean iszipfile;

    @ColumnInfo(name = "finished")
    private boolean finished;

    @ColumnInfo(name = "zeorder")
    private double zeorder;

    public long date_added;



    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }

    public String getFolderName() {
        return folderName;
    }
    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public String getDisplayName() {
        return displayName;
    }
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
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

    public double getPosition() {
        return position;
    }
    public void setPosition(double position) {
        this.position = position;
    }

    public double getDuration() {
        return duration;
    }
    public void setDuration(double duration) {
        this.duration = duration;
    }

    public double getPercentdone() {
        return percentdone;
    }
    public void setPercentdone(double percentdone) {
        this.percentdone = percentdone;
    }

    public Date getFirstaccess() {
        return firstaccess;
    }
    public void setFirstaccess(Date firstaccess) {
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

    public boolean isFinished() {
        return finished;
    }
    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public int getIdFolder() {
        return idFolder;
    }
    public void setIdFolder(int idFolder) {
        this.idFolder = idFolder;
    }

    public double getSize() {
        return size;
    }
    public void setSize(double size) {
        this.size = size;
    }

    public boolean isIszipfile() {
        return iszipfile;
    }
    public void setIszipfile(boolean iszipfile) {
        this.iszipfile = iszipfile;
    }

    public boolean isM4b() {
        return getExtension(name).equals("m4b");
    }


    public double getZeorder() { return zeorder; }
    public void setZeorder(double zeorder) {
        this.zeorder = zeorder;
    }

    @Override
    public String toString() {
        return "ZikFile{" +
                "id=" + id +
                ", idFolder=" + idFolder +
                ", name='" + name + '\'' +
                ", zeorder='" + zeorder + '\'' +
                ", path='" + path + '\'' +
                ", folderName='" + folderName + '\'' +
                ", position=" + position +
                ", duration=" + duration +
                ", size=" + size +
                ", percentdone=" + percentdone +
                ", firstaccess=" + firstaccess +
                ", lastaccess=" + lastaccess +
                ", lastaccessTime=" + lastaccessTime +
                ", iszipfile=" + iszipfile +
                ", finished=" + finished +
                '}';
    }
}
