package com.driot.bookplayer.db;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 30/10/20
 */

import android.os.Parcel;
import android.os.Parcelable;

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
    private long idFolder;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "path")
    private String path;

    @ColumnInfo(name = "folderName")
    private String folderName;

    @ColumnInfo(name = "position")
    private double position;

    @ColumnInfo(name = "length")
    private double length;

    @ColumnInfo(name = "size")
    private double size;

    @ColumnInfo(name = "percentdone")
    private Double percentdone;

    @ColumnInfo(name = "firstaccess")
    private Date firstaccess;

    @ColumnInfo(name = "lastaccess")
    private Date lastaccess;

    @ColumnInfo(name = "lastaccessTime")
    private Time lastaccessTime;

    @ColumnInfo(name = "finished")
    private boolean finished;

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

    public double getLength() {
        return length;
    }

    public void setLength(double length) {
        this.length = length;
    }

    public Double getPercentdone() {
        return percentdone;
    }

    public void setPercentdone(Double percentdone) {
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

    public long getIdFolder() {
        return idFolder;
    }

    public void setIdFolder(long idFolder) {
        this.idFolder = idFolder;
    }

    public double getSize() {
        return size;
    }

    public void setSize(double size) {
        this.size = size;
    }

    @Override
    public String toString() {
        return "ZikFile{" +
                "id=" + id +
                ", idFolder=" + idFolder +
                ", name='" + name + '\'' +
                ", path='" + path + '\'' +
                ", folderName='" + folderName + '\'' +
                ", position=" + position +
                ", length=" + length +
                ", size=" + size +
                ", percentdone=" + percentdone +
                ", firstaccess=" + firstaccess +
                ", lastaccess=" + lastaccess +
                ", lastaccessTime=" + lastaccessTime +
                ", finished=" + finished +
                '}';
    }
}
