package com.driot.bookplayer.objects;


//POJO (Plain Old Java Object) => DB projection ---
//
// ---- WARNING ---- should have same fields that in FolderDao getFoldersForCleaning

public class FolderSummary {
    public String path;
    public String name;
    public int id;
    public double percentDone;
    public String sourceLocation;
    public String playType;
    public String image;


    public FolderSummary(String path, String name, int id, double percentDone, String sourceLocation, String playType, String image) {
        this.path = path;
        this.name = name;
        this.id = id;
        this.percentDone = percentDone;
        this.sourceLocation = sourceLocation;
        this.playType = playType;
        this.image = image;

    }
}