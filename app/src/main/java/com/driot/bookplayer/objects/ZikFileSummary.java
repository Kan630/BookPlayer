package com.driot.bookplayer.objects;


//POJO (Plain Old Java Object) => interface projection

public class ZikFileSummary {
    public String path;
    public String folderName;
    public Double percentDone;
    public String sourceLocation;
    public String originalFile;
    public int idFolder;


    public ZikFileSummary(String path, String folderName, Double percentDone, String sourceLocation, String originalFile, int idFolder) {
        this.path = path;
        this.folderName = folderName;
        this.percentDone = percentDone;
        this.sourceLocation = sourceLocation;
        this.originalFile = originalFile;
        this.idFolder = idFolder;
    }
}