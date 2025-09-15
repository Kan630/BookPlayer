package com.driot.bookplayer.objects;


//POJO (Plain Old Java Object) => DB projection ---
//
// ---- WARNING ---- should have same fields that in ZikFileDao getZikFileDistinctLocations

public class ZikFileSummary {
    public String path;                 //ZikFile
    public String folderName;           //ZikFile
    public int idFolder;                //ZikFile
    public double percentDone;          //Folder
    public String sourceLocation;       //Folder
    public String playType;             //Folder


    public ZikFileSummary(String path, String folderName, int idFolder, double percentDone, String sourceLocation, String playType) {
        this.path = path;
        this.folderName = folderName;
        this.idFolder = idFolder;
        this.percentDone = percentDone;
        this.sourceLocation = sourceLocation;
        this.playType = playType;
    }
}