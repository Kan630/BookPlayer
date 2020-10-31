package com.driot.bookplayer;

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

    @ColumnInfo(name = "position")
    private double position;

    @ColumnInfo(name = "length")
    private int length;

    @ColumnInfo(name = "percentdone")
    private Double percentdone;

    @ColumnInfo(name = "firstaccess")
    private Time firstaccess;

    @ColumnInfo(name = "lastaccess")
    private Time lastaccess;

    @ColumnInfo(name = "finished")
    private boolean finished;

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

    public double getPosition() {
        return position;
    }

    public void setPosition(double position) {
        this.position = position;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public Double getPercentdone() {
        return percentdone;
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

    public Time getLastaccess() {
        return lastaccess;
    }

    public void setLastaccess(Time lastaccess) {
        this.lastaccess = lastaccess;
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

}
