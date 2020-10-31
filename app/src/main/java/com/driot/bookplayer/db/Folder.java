package com.driot.bookplayer.db;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 28/10/20
 */

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

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
}